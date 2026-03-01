package com.go;

import java.sql.*;
import java.util.*;
import com.google.gson.Gson;

public class PuzzleLoader {
    private static final String DB_PATH = "database/puzzles.db";
    private static final Gson gson = new Gson();

    public static class PuzzleData {
        public final Level level;
        public final boolean playerIsWhite;

        public PuzzleData(Level level, boolean playerIsWhite) {
            this.level = level;
            this.playerIsWhite = playerIsWhite;
        }
    }

    public static List<PuzzleData> loadAllPuzzles() {
        List<PuzzleData> puzzles = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH)) { //connect to DB and extract puzzles
            String sql = "SELECT * FROM puzzles";
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql);

            while (rs.next()) {
                String initialWhiteJson = rs.getString("initial_white");
                String initialBlackJson = rs.getString("initial_black");
                String solutionJson = rs.getString("solution");
                boolean playerToMove = rs.getBoolean("player_to_move"); // defaults to white if not specified

                //TODO: Extract name, notes, difficulty and render as needed (may want to allow randomization by difficulty)

                List<int[]> initialWhite = Arrays.asList(gson.fromJson(initialWhiteJson, int[][].class));
                List<int[]> initialBlack = Arrays.asList(gson.fromJson(initialBlackJson, int[][].class));
                List<SolutionStep> solution = SolutionParser.parse(solutionJson);

                Board board = new Board();
                board.setIntialStones(initialWhite, initialBlack);
                Level level = new Level(board, solution, initialWhite, initialBlack);
                puzzles.add(new PuzzleData(level, playerToMove));
            }

        } catch (SQLException e) {
            System.err.println("Error loading puzzles from database:");
            e.printStackTrace();
            System.exit(1);
        }

        return puzzles;
    }
}
