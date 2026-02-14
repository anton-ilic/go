package com.go;

import java.time.Instant;

/**
 * A multiplayer Go room. No player identity — anyone with the link can play.
 * Turn order and move numbering are the only enforcement.
 */
public class Room {

    private final String roomId;
    private final Board board;
    private String turn;       // "BLACK" or "WHITE"
    private int moveNumber;
    private Instant updatedAt;

    public Room(String roomId) {
        this.roomId = roomId;
        this.board = new Board();
        this.board.restart();
        this.turn = "BLACK";
        this.moveNumber = 0;
        this.updatedAt = Instant.now();
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

    /**
     * Attempts to apply a move. Thread-safe.
     * Only validates that the move is legal according to Go rules.
     *
     * @param x board x coordinate
     * @param y board y coordinate
     * @return result describing success or failure
     */
    public synchronized MoveResult applyMove(int x, int y) {
        if (x < 0 || x >= Board.BOARD_SIZE || y < 0 || y >= Board.BOARD_SIZE) {
            return new MoveResult(false, "Coordinates out of bounds");
        }

        boolean isWhite = "WHITE".equals(this.turn);
        boolean ok = board.play(x, y, isWhite);
        if (!ok) {
            return new MoveResult(false, "Illegal move");
        }

        this.turn = isWhite ? "BLACK" : "WHITE";
        this.moveNumber++;
        this.updatedAt = Instant.now();

        return new MoveResult(true, "Move accepted");
    }

    /**
     * Pass turn without placing a stone.
     */
    public synchronized MoveResult pass() {
        this.turn = "WHITE".equals(this.turn) ? "BLACK" : "WHITE";
        this.moveNumber++;
        this.updatedAt = Instant.now();

        return new MoveResult(true, "Pass accepted");
    }

    public record MoveResult(boolean success, String message) {}
}
