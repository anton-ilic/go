package com.go;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.Optional;

/**
 * Persists board state stacks (undo/redo) to the database.
 * entity_type = "room", entity_id = roomId. stack_type = "undo" or "redo".
 */
@Repository
public class BoardStateStackRepository {

    private final String jdbcUrl;

    public BoardStateStackRepository(@Value("${puzzles.db.path}") String dbPath) {
        Path path = Paths.get(dbPath);
        if (!path.isAbsolute()) {
            path = Paths.get(System.getProperty("user.dir")).resolve(path);
        }
        this.jdbcUrl = "jdbc:sqlite:" + path.normalize().toAbsolutePath();
        ensureTable();
    }

    private void ensureTable() {
        String sql = "CREATE TABLE IF NOT EXISTS board_state_stack (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "entity_type TEXT NOT NULL," +
                "entity_id TEXT NOT NULL," +
                "stack_type TEXT NOT NULL," +
                "state_json TEXT NOT NULL)";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to ensure board_state_stack table exists", e);
        }
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }

    public void push(String entityType, String entityId, String stackType, BoardStateSnapshot state) {
        String sql = "INSERT INTO board_state_stack (entity_type, entity_id, stack_type, state_json) VALUES (?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entityType);
            ps.setString(2, entityId);
            ps.setString(3, stackType);
            ps.setString(4, state.toJson());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to push board state to stack", e);
        }
    }

    public Optional<BoardStateSnapshot> pop(String entityType, String entityId, String stackType) {
        String selectSql = "SELECT id, state_json FROM board_state_stack WHERE entity_type = ? AND entity_id = ? AND stack_type = ? ORDER BY id DESC LIMIT 1";
        String deleteSql = "DELETE FROM board_state_stack WHERE id = ?";
        try (Connection conn = getConnection()) {
            try (PreparedStatement select = conn.prepareStatement(selectSql)) {
                select.setString(1, entityType);
                select.setString(2, entityId);
                select.setString(3, stackType);
                try (ResultSet rs = select.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    long id = rs.getLong("id");
                    String stateJson = rs.getString("state_json");
                    try (PreparedStatement delete = conn.prepareStatement(deleteSql)) {
                        delete.setLong(1, id);
                        delete.executeUpdate();
                    }
                    return Optional.of(BoardStateSnapshot.fromJson(stateJson));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to pop board state from stack", e);
        }
    }

    public void clear(String entityType, String entityId, String stackType) {
        String sql = "DELETE FROM board_state_stack WHERE entity_type = ? AND entity_id = ? AND stack_type = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entityType);
            ps.setString(2, entityId);
            ps.setString(3, stackType);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to clear board state stack", e);
        }
    }
}
