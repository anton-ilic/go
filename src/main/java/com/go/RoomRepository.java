package com.go;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.*;

/**
 * Persists game rooms to the database.
 * Schema is SQLite-friendly; for Postgres use SERIAL where applicable and BOOLEAN for game_ended.
 */
@Repository
public class RoomRepository {

    private final String jdbcUrl;

    public RoomRepository(@Value("${puzzles.db.path}") String dbPath) {
        Path path = Paths.get(dbPath);
        if (!path.isAbsolute()) {
            path = Paths.get(System.getProperty("user.dir")).resolve(path);
        }
        this.jdbcUrl = "jdbc:sqlite:" + path.normalize().toAbsolutePath();
        ensureTable();
    }

    private void ensureTable() {
        // SQLite: INTEGER for boolean (0/1). For Postgres you'd use BOOLEAN.
        String sql = """
            CREATE TABLE IF NOT EXISTS rooms (
                room_id TEXT PRIMARY KEY,
                board_size INTEGER NOT NULL,
                komi REAL NOT NULL,
                turn TEXT NOT NULL,
                move_number INTEGER NOT NULL,
                consecutive_passes INTEGER NOT NULL,
                game_ended INTEGER NOT NULL,
                score_black REAL NOT NULL,
                score_white REAL NOT NULL,
                resigned_by TEXT,
                winner TEXT,
                territory_marks_json TEXT,
                dead_stones_json TEXT,
                board_state_json TEXT NOT NULL,
                updated_at TEXT
            )
            """;
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to ensure rooms table exists", e);
        }
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }

    public void save(Room room) {
        String boardStateJson = room.getBoard().createStateSnapshot().toJson();
        String territoryMarksJson = jsonFromMap(room.getTerritoryMarks());
        String deadStonesJson = jsonFromSet(room.getDeadStones());
        String sql = """
            INSERT OR REPLACE INTO rooms (
                room_id, board_size, komi, turn, move_number, consecutive_passes,
                game_ended, score_black, score_white, resigned_by, winner,
                territory_marks_json, dead_stones_json, board_state_json, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 0;
            ps.setString(++i, room.getRoomId());
            ps.setInt(++i, room.getBoardSize());
            ps.setDouble(++i, room.getKomi());
            ps.setString(++i, room.getTurn());
            ps.setInt(++i, room.getMoveNumber());
            ps.setInt(++i, room.getConsecutivePasses());
            ps.setInt(++i, room.isGameEnded() ? 1 : 0);
            ps.setDouble(++i, room.getScoreBlack());
            ps.setDouble(++i, room.getScoreWhite());
            ps.setString(++i, room.getResignedBy());
            ps.setString(++i, room.getWinner());
            ps.setString(++i, territoryMarksJson);
            ps.setString(++i, deadStonesJson);
            ps.setString(++i, boardStateJson);
            ps.setString(++i, room.getUpdatedAt() != null ? room.getUpdatedAt().toString() : null);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save room " + room.getRoomId(), e);
        }
    }

    public Optional<Room> findByRoomId(String roomId) {
        if (roomId == null) return Optional.empty();
        String sql = "SELECT * FROM rooms WHERE room_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, roomId.toUpperCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find room " + roomId, e);
        }
    }

    public boolean existsByRoomId(String roomId) {
        return findByRoomId(roomId).isPresent();
    }

    private Room mapRow(ResultSet rs) throws SQLException {
        String roomId = rs.getString("room_id");
        int boardSize = rs.getInt("board_size");
        double komi = rs.getDouble("komi");
        String boardStateJson = rs.getString("board_state_json");
        String turn = rs.getString("turn");
        int moveNumber = rs.getInt("move_number");
        int consecutivePasses = rs.getInt("consecutive_passes");
        boolean gameEnded = rs.getInt("game_ended") != 0;
        double scoreBlack = rs.getDouble("score_black");
        double scoreWhite = rs.getDouble("score_white");
        String resignedBy = rs.getString("resigned_by");
        String winner = rs.getString("winner");
        Map<String, String> territoryMarks = mapFromJson(rs.getString("territory_marks_json"));
        Set<String> deadStones = setFromJson(rs.getString("dead_stones_json"));
        return new Room(roomId, boardSize, komi, boardStateJson, turn, moveNumber,
                consecutivePasses, gameEnded, scoreBlack, scoreWhite, resignedBy, winner,
                territoryMarks, deadStones);
    }

    private static String jsonFromMap(Map<String, String> map) {
        if (map == null || map.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{");
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (sb.length() > 1) sb.append(",");
            sb.append("\"").append(escapeJson(e.getKey())).append("\":\"").append(escapeJson(e.getValue())).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    private static String jsonFromSet(Set<String> set) {
        if (set == null || set.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (String s : set) {
            if (sb.length() > 1) sb.append(",");
            sb.append("\"").append(escapeJson(s)).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static Map<String, String> mapFromJson(String json) {
        if (json == null || json.isBlank()) return Collections.emptyMap();
        try {
            com.google.gson.Gson gson = new com.google.gson.Gson();
            @SuppressWarnings("unchecked")
            Map<String, String> map = gson.fromJson(json, Map.class);
            return map != null ? map : Collections.emptyMap();
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private static Set<String> setFromJson(String json) {
        if (json == null || json.isBlank()) return Collections.emptySet();
        try {
            com.google.gson.Gson gson = new com.google.gson.Gson();
            String[] arr = gson.fromJson(json, String[].class);
            if (arr == null) return Collections.emptySet();
            return new LinkedHashSet<>(Arrays.asList(arr));
        } catch (Exception e) {
            return Collections.emptySet();
        }
    }
}
