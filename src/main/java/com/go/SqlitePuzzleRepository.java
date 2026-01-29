package com.go;

import com.google.gson.Gson;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

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

    private final String dbPath;
    private static final Gson gson = new Gson();

    public SqlitePuzzleRepository(@Value("${puzzles.db.path}") String dbPath) {
        this.dbPath = dbPath;
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath);
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

