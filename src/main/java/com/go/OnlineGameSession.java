package com.go;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a two-player online Go game backed by a Board.
 */
public class OnlineGameSession {

    public enum GameStatus {
        WAITING_FOR_OPPONENT,
        IN_PROGRESS,
        FINISHED
    }

    public enum Turn {
        BLACK,
        WHITE
    }

    private final UUID gameId;
    private final String roomCode;
    private final Board board;

    private UUID blackPlayerId;
    private UUID whitePlayerId;
    private String blackPlayerName;
    private String whitePlayerName;

    private Turn currentTurn;
    private GameStatus status;
    private final Instant createdAt;

    public OnlineGameSession(Board board, String creatorName, String roomCode) {
        this.gameId = UUID.randomUUID();
        this.roomCode = roomCode;
        this.board = board;
        this.blackPlayerId = UUID.randomUUID();
        this.blackPlayerName = creatorName;
        this.currentTurn = Turn.BLACK;
        this.status = GameStatus.WAITING_FOR_OPPONENT;
        this.createdAt = Instant.now();
    }

    public String getRoomCode() {
        return roomCode;
    }

    public UUID getGameId() {
        return gameId;
    }

    public Board getBoard() {
        return board;
    }

    public UUID getBlackPlayerId() {
        return blackPlayerId;
    }

    public UUID getWhitePlayerId() {
        return whitePlayerId;
    }

    public String getBlackPlayerName() {
        return blackPlayerName;
    }

    public String getWhitePlayerName() {
        return whitePlayerName;
    }

    public Turn getCurrentTurn() {
        return currentTurn;
    }

    public GameStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isFull() {
        return whitePlayerId != null;
    }

    public UUID joinAsWhite(String playerName) {
        if (isFull()) {
            throw new IllegalStateException("Game already has two players");
        }
        this.whitePlayerId = UUID.randomUUID();
        this.whitePlayerName = playerName;
        this.status = GameStatus.IN_PROGRESS;
        return whitePlayerId;
    }

    public boolean isPlayersTurn(UUID playerId) {
        if (status != GameStatus.IN_PROGRESS) {
            return false;
        }
        if (currentTurn == Turn.BLACK) {
            return playerId.equals(blackPlayerId);
        }
        return playerId.equals(whitePlayerId);
    }

    public boolean isBlack(UUID playerId) {
        return playerId.equals(blackPlayerId);
    }

    public boolean isWhite(UUID playerId) {
        return playerId.equals(whitePlayerId);
    }

    public void switchTurn() {
        this.currentTurn = (this.currentTurn == Turn.BLACK) ? Turn.WHITE : Turn.BLACK;
    }
}

