package com.go;

import java.time.Instant;

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
        this.updatedAt = Instant.now();

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
        this.updatedAt = Instant.now();

        if (consecutivePasses >= 2) {
            gameEnded = true;
            ChineseScoring.ScoreResult score = ChineseScoring.compute(board, komi);
            this.scoreBlack = score.black();
            this.scoreWhite = score.white();
        }

        return new MoveResult(true, consecutivePasses >= 2 ? "Game over. Both players passed." : "Pass accepted");
    }

    /**
     * Undo the last move. Returns true if undo was performed.
     */
    public synchronized boolean undo() {
        if (!board.canUndo()) return false;
        board.undo();
        moveNumber--;
        this.turn = "WHITE".equals(this.turn) ? "BLACK" : "WHITE";
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
