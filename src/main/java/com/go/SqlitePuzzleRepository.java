package com.go;

import com.google.gson.Gson;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Repository
public class SqlitePuzzleRepository implements PuzzleRepository {

    /** Resolved absolute path so init and runtime use the same DB file. */
    private final Path dbFile;
    /** JDBC URL used for all connections. */
    private final String jdbcUrl;
    private static final Gson gson = new Gson();

    public SqlitePuzzleRepository(@Value("${puzzles.db.path}") String dbPath) {
        Path path = Paths.get(dbPath);
        if (!path.isAbsolute()) {
            path = Paths.get(System.getProperty("user.dir")).resolve(path);
        }
        this.dbFile = path.normalize().toAbsolutePath();
        this.jdbcUrl = "jdbc:sqlite:" + this.dbFile;
        ensureDatabaseInitialized();
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }

    private void ensureDatabaseInitialized() {
        Path dbDir = dbFile.getParent();
        if (dbDir == null) {
            throw new RuntimeException("Invalid puzzles database path: " + dbFile);
        }

        try {
            Files.createDirectories(dbDir);
        } catch (IOException e) {
            throw new RuntimeException("Unable to create database directory: " + dbDir, e);
        }

        String schemaSql = readResource("/database/schema.sql");
        String seedSql = readResource("/database/puzzles.sql");

        try (Connection conn = DriverManager.getConnection(jdbcUrl)) {
            if (hasPuzzlesTable(conn)) {
                if (puzzleCount(conn) == 0) {
                    runSeed(conn, seedSql);
                }
                return;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Unable to initialize puzzles database", e);
        }

        // No table (or corrupt/empty DB): remove file and create fresh so we never reuse a bad DB
        try {
            Files.deleteIfExists(dbFile);
        } catch (IOException e) {
            throw new RuntimeException("Unable to remove existing database file: " + dbFile, e);
        }

        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("PRAGMA foreign_keys = ON");
            stmt.executeUpdate("PRAGMA busy_timeout = 3000");
            conn.setAutoCommit(false);
            try {
                stmt.execute(schemaSql);
                executeScript(stmt, seedSql);
                conn.commit();
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Unable to create and seed puzzles database", e);
        }
    }

    private void runSeed(Connection conn, String seedSql) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            conn.setAutoCommit(false);
            try {
                executeScript(stmt, seedSql);
                conn.commit();
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    private int puzzleCount(Connection conn) throws SQLException {
        String sql = "SELECT COUNT(*) FROM puzzles";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static String readResource(String name) {
        try (InputStream in = SqlitePuzzleRepository.class.getResourceAsStream(name)) {
            if (in == null) {
                throw new RuntimeException("Missing classpath resource: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + name, e);
        }
    }

    /** Executes a SQL script containing multiple statements separated by semicolons. */
    private void executeScript(Statement stmt, String script) throws SQLException {
        for (String statement : script.split(";")) {
            String trimmed = statement.trim();
            if (!trimmed.isEmpty()) {
                stmt.execute(trimmed);
            }
        }
    }

    private boolean hasPuzzlesTable(Connection conn) throws SQLException {
        String sql = "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'puzzles'";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next();
        }
    }

    @Override
    public List<PuzzleSummary> findAll() {
        List<PuzzleSummary> summaries = new ArrayList<>();
        String sql = "SELECT id, name, difficulty FROM puzzles ORDER BY id";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                long id = rs.getLong("id");
                String name = rs.getString("name");
                int difficulty = rs.getInt("difficulty");
                summaries.add(new PuzzleSummary(id, name, difficulty));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error loading puzzle summaries", e);
        }

        return summaries;
    }

    @Override
    public Optional<PuzzleData> findById(long id) {
        String sql = "SELECT * FROM puzzles WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }

                String name = rs.getString("name");
                int difficulty = rs.getInt("difficulty");
                String initialWhiteJson = rs.getString("initial_white");
                String initialBlackJson = rs.getString("initial_black");
                String solutionJson = rs.getString("solution");
                boolean playerToMove = rs.getBoolean("player_to_move");
                String notes = rs.getString("notes");

                List<int[]> initialWhite = Arrays.asList(gson.fromJson(initialWhiteJson, int[][].class));
                List<int[]> initialBlack = Arrays.asList(gson.fromJson(initialBlackJson, int[][].class));
                List<int[]> solution = Arrays.asList(gson.fromJson(solutionJson, int[][].class));

                PuzzleData data = new PuzzleData(
                        id,
                        name,
                        difficulty,
                        initialWhite,
                        initialBlack,
                        solution,
                        playerToMove,
                        notes
                );
                return Optional.of(data);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error loading puzzle id=" + id, e);
        }
    }
}

