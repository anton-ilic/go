package com.go;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A multiplayer Go room. No player identity — anyone with the link can play.
 * Turn order and move numbering are the only enforcement.
 */
public class Room {

    private final String roomId;
    private final Board board;
    private final double komi;
    private String turn;       // "BLACK" or "WHITE"
    private int moveNumber;
    private Instant updatedAt;
    /** Number of consecutive passes (0, 1, or 2). Game ends when this reaches 2. */
    private int consecutivePasses;
    private boolean gameEnded;
    private double scoreBlack;
    private double scoreWhite;
    /** If non-null, game ended by resignation; winner is the opposite color. */
    private String resignedBy;
    private String winner;
    /** Manual territory marks for scoring: key "x,y" -> "BLACK" or "WHITE". Only used when game ended by double-pass. */
    private final Map<String, String> territoryMarks = new ConcurrentHashMap<>();
    /** Stones marked as dead for scoring: keys "x,y". Only used when game ended by double-pass. */
    private final Set<String> deadStones = ConcurrentHashMap.newKeySet();
    /** Linear history of board states for replay (initial position + after each move/pass). */
    private final java.util.List<BoardStateSnapshot> history = new java.util.ArrayList<>();
    /** Coordinates of the last stone played (-1,-1 if none or after a pass). */
    private int lastMoveX = -1;
    private int lastMoveY = -1;

    public Room(String roomId) {
        this(roomId, Board.DEFAULT_BOARD_SIZE, 6.5);
    }

    /** Creates a room with the given board size (9, 11, or 19) and komi. */
    public Room(String roomId, int boardSize) {
        this(roomId, boardSize, 6.5);
    }

    /** Creates a room with board size and komi (e.g. 6.5 for White). */
    public Room(String roomId, int boardSize, double komi) {
        this.roomId = roomId;
        this.komi = komi;
        this.board = new Board(boardSize);
        this.board.restart();
        this.turn = "BLACK";
        this.moveNumber = 0;
        this.consecutivePasses = 0;
        this.gameEnded = false;
        this.scoreBlack = 0;
        this.scoreWhite = 0;
        this.updatedAt = Instant.now();
        // Record initial position for replay
        this.history.add(this.board.createStateSnapshot());
    }

    /**
     * Creates a room from persisted state (used when loading from DB).
     * Undo/redo stacks are not restored here; they remain in board_state_stack.
     */
    public Room(String roomId, int boardSize, double komi, String boardStateJson,
                String turn, int moveNumber, int consecutivePasses, boolean gameEnded,
                double scoreBlack, double scoreWhite, String resignedBy, String winner,
                Map<String, String> territoryMarks, Set<String> deadStones) {
        this.roomId = roomId;
        this.komi = komi;
        this.board = new Board(boardSize);
        this.board.restoreState(BoardStateSnapshot.fromJson(boardStateJson));
        this.turn = turn != null ? turn : "BLACK";
        this.moveNumber = moveNumber;
        this.consecutivePasses = consecutivePasses;
        this.gameEnded = gameEnded;
        this.scoreBlack = scoreBlack;
        this.scoreWhite = scoreWhite;
        this.resignedBy = resignedBy;
        this.winner = winner;
        this.updatedAt = Instant.now();
        if (territoryMarks != null) this.territoryMarks.putAll(territoryMarks);
        if (deadStones != null) this.deadStones.addAll(deadStones);
        if (gameEnded && resignedBy == null && this.territoryMarks.isEmpty()) {
            fillTerritoryMarksFromScoring();
        }
    }

    public int getBoardSize() {
        return board.getBoardSize();
    }

    public String getRoomId() {
        return roomId;
    }

    public Board getBoard() {
        return board;
    }

    public String getTurn() {
        return turn;
    }

    public int getMoveNumber() {
        return moveNumber;
    }

    public int getConsecutivePasses() {
        return consecutivePasses;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public int getBlackPrisoners() {
        return board.getBlackPrisoners();
    }

    public int getWhitePrisoners() {
        return board.getWhitePrisoners();
    }

    public double getKomi() { return komi; }
    public boolean isGameEnded() { return gameEnded; }
    public double getScoreBlack() { return scoreBlack; }
    public double getScoreWhite() { return scoreWhite; }
    public String getResignedBy() { return resignedBy; }
    public String getWinner() { return winner; }
    public Map<String, String> getTerritoryMarks() { return Map.copyOf(territoryMarks); }
    public Set<String> getDeadStones() { return Set.copyOf(deadStones); }
    /** True if game ended by double-pass (so territory/dead marking is allowed). */
    public boolean isScoringPhase() { return gameEnded && resignedBy == null; }

    /** Last stone played: x (or -1 if none/pass). */
    public int getLastMoveX() { return lastMoveX; }
    /** Last stone played: y (or -1 if none/pass). */
    public int getLastMoveY() { return lastMoveY; }

    /** Immutable view of the replay history (initial position + after each move/pass). */
    public java.util.List<BoardStateSnapshot> getHistory() {
        return java.util.List.copyOf(history);
    }

    /**
     * Attempts to apply a move. Thread-safe.
     * Saves current state to undo stack before moving; clears redo stack on success.
     */
    public synchronized MoveResult applyMove(int x, int y) {
        if (gameEnded) {
            return new MoveResult(false, "Game has ended", null);
        }
        if (x < 0 || x >= board.getBoardSize() || y < 0 || y >= board.getBoardSize()) {
            return new MoveResult(false, "Coordinates out of bounds", null);
        }

        this.consecutivePasses = 0;
        BoardStateSnapshot saved = board.saveState();
        boolean isWhite = "WHITE".equals(this.turn);
        boolean ok = board.play(x, y, isWhite);
        if (!ok) {
            board.popUndoStack(); // remove the state we just saved
            return new MoveResult(false, "Illegal move", null);
        }

        board.clearRedo();
        this.turn = isWhite ? "BLACK" : "WHITE";
        this.moveNumber++;
        this.lastMoveX = x;
        this.lastMoveY = y;
        this.updatedAt = Instant.now();

        // Record the new position for replay (after the move).
        history.add(board.createStateSnapshot());

        return new MoveResult(true, "Move accepted", saved);
    }

    /**
     * Pass turn without placing a stone. If both players pass consecutively, the game ends and scores are computed (Chinese + komi).
     */
    public synchronized MoveResult pass() {
        if (gameEnded) {
            return new MoveResult(false, "Game has ended", null);
        }
        this.turn = "WHITE".equals(this.turn) ? "BLACK" : "WHITE";
        this.moveNumber++;
        this.consecutivePasses++;
        this.lastMoveX = -1;
        this.lastMoveY = -1;
        this.updatedAt = Instant.now();

        if (consecutivePasses >= 2) {
            gameEnded = true;
            fillTerritoryMarksFromScoring();
        }

        // Record the position after the pass for replay as well.
        history.add(board.createStateSnapshot());

        return new MoveResult(true, consecutivePasses >= 2 ? "Game over. Both players passed." : "Pass accepted");
    }

    /**
     * Current player resigns. Game ends; winner is the opposite color.
     */
    public synchronized boolean resign() {
        if (gameEnded) return false;
        gameEnded = true;
        resignedBy = turn;
        winner = "BLACK".equals(turn) ? "WHITE" : "BLACK";
        updatedAt = Instant.now();
        return true;
    }

    /**
     * Set or clear territory mark at (x,y). Only allowed in scoring phase (game ended by double-pass).
     * color: "BLACK", "WHITE", or null to clear.
     */
    public synchronized boolean setTerritoryMark(int x, int y, String color) {
        if (!isScoringPhase()) return false;
        int size = board.getBoardSize();
        if (x < 0 || x >= size || y < 0 || y >= size) return false;

        // Only operate on empty points (not occupied by stones).
        if (board.getStoneAt(x, y) != Board.EMPTY) {
            return false;
        }

        // Flood-fill the connected empty region starting from (x,y).
        java.util.Set<String> region = floodFillEmptyRegion(x, y);

        if (color == null || color.isEmpty()) {
            // Clear marks for the whole region.
            for (String key : region) {
                territoryMarks.remove(key);
            }
        } else if ("BLACK".equals(color) || "WHITE".equals(color)) {
            // Set the same color for the whole region.
            for (String key : region) {
                territoryMarks.put(key, color);
            }
        } else {
            return false;
        }
        recomputeScoresFromMarks();
        updatedAt = Instant.now();
        return true;
    }

    /**
     * Toggle dead-stone mark at (x,y). Only allowed in scoring phase.
     * Point must have a stone (marking it as dead removes it from count and treats as empty for territory).
     */
    public synchronized boolean toggleDeadStone(int x, int y) {
        if (!isScoringPhase()) return false;
        int size = board.getBoardSize();
        if (x < 0 || x >= size || y < 0 || y >= size) return false;
        if (board.getStoneAt(x, y) == Board.EMPTY) return false;
        // Toggle the entire connected group of stones (same color) as dead/alive.
        java.util.Set<String> group = floodFillStoneGroup(x, y);
        boolean anyDead = group.stream().anyMatch(deadStones::contains);
        if (anyDead) {
            // If any stone in the group is already marked dead, unmark the whole group.
            for (String key : group) {
                deadStones.remove(key);
            }
        } else {
            // Otherwise mark the whole group as dead.
            deadStones.addAll(group);
        }
        recomputeScoresFromMarks();
        updatedAt = Instant.now();
        return true;
    }

    private void recomputeScoresFromMarks() {
        ChineseScoring.ScoreResult score = ChineseScoring.compute(board, komi, territoryMarks, deadStones);
        this.scoreBlack = score.black();
        this.scoreWhite = score.white();
    }

    /**
     * Flood-fill the connected empty region (no stones) starting from (startX, startY).
     * Returns a set of "x,y" keys for all points in the region.
     */
    private java.util.Set<String> floodFillEmptyRegion(int startX, int startY) {
        java.util.Set<String> visited = new java.util.HashSet<>();
        java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();

        int size = board.getBoardSize();
        if (startX < 0 || startX >= size || startY < 0 || startY >= size) {
            return visited;
        }
        if (board.getStoneAt(startX, startY) != Board.EMPTY) {
            return visited;
        }

        queue.add(new int[]{startX, startY});
        visited.add(startX + "," + startY);

        int[][] directions = new int[][]{{1,0},{-1,0},{0,1},{0,-1}};

        while (!queue.isEmpty()) {
            int[] pos = queue.pollFirst();
            int x = pos[0];
            int y = pos[1];

            for (int[] d : directions) {
                int nx = x + d[0];
                int ny = y + d[1];
                if (nx < 0 || nx >= size || ny < 0 || ny >= size) continue;
                if (board.getStoneAt(nx, ny) != Board.EMPTY) continue;
                String key = nx + "," + ny;
                if (visited.contains(key)) continue;
                visited.add(key);
                queue.add(new int[]{nx, ny});
            }
        }

        return visited;
    }

    /**
     * Flood-fill a connected group of stones of the same color starting from (startX, startY).
     * Returns a set of "x,y" keys for all stones in the group.
     */
    private java.util.Set<String> floodFillStoneGroup(int startX, int startY) {
        java.util.Set<String> visited = new java.util.HashSet<>();
        java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();

        int size = board.getBoardSize();
        if (startX < 0 || startX >= size || startY < 0 || startY >= size) {
            return visited;
        }
        int color = board.getStoneAt(startX, startY);
        if (color == Board.EMPTY) {
            return visited;
        }

        queue.add(new int[]{startX, startY});
        visited.add(startX + "," + startY);

        int[][] directions = new int[][]{{1,0},{-1,0},{0,1},{0,-1}};

        while (!queue.isEmpty()) {
            int[] pos = queue.pollFirst();
            int x = pos[0];
            int y = pos[1];

            for (int[] d : directions) {
                int nx = x + d[0];
                int ny = y + d[1];
                if (nx < 0 || nx >= size || ny < 0 || ny >= size) continue;
                if (board.getStoneAt(nx, ny) != color) continue;
                String key = nx + "," + ny;
                if (visited.contains(key)) continue;
                visited.add(key);
                queue.add(new int[]{nx, ny});
            }
        }

        return visited;
    }

    /** At the start of scoring phase, fill territory marks so the board shows Black/White territory visually. */
    private void fillTerritoryMarksFromScoring() {
        territoryMarks.clear();
        territoryMarks.putAll(ChineseScoring.computeTerritoryMarks(board, deadStones));
        recomputeScoresFromMarks();
    }

    /**
     * Undo the last move. Returns true if undo was performed.
     */
    public synchronized boolean undo() {
        if (!board.canUndo()) return false;
        board.undo();
        moveNumber--;
        this.turn = "WHITE".equals(this.turn) ? "BLACK" : "WHITE";
        this.lastMoveX = -1;
        this.lastMoveY = -1;
        this.updatedAt = Instant.now();
        return true;
    }

    /**
     * Redo a previously undone move. Returns true if redo was performed.
     */
    public synchronized boolean redo() {
        if (!board.canRedo()) return false;
        board.redo();
        moveNumber++;
        this.turn = "WHITE".equals(this.turn) ? "BLACK" : "WHITE";
        this.lastMoveX = -1;
        this.lastMoveY = -1;
        this.updatedAt = Instant.now();
        return true;
    }

    public boolean canUndo() {
        return !gameEnded && board.canUndo();
    }

    public boolean canRedo() {
        return !gameEnded && board.canRedo();
    }

    public record MoveResult(boolean success, String message, BoardStateSnapshot statePushedForUndo) {
        public MoveResult(boolean success, String message) {
            this(success, message, null);
        }
    }
}
