package com.go;

import com.go.OnlineGameSession.GameStatus;
import com.go.OnlineGameSession.Turn;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages in-memory online games for two remote players.
 */
@Service
public class OnlineGameService {

    private final Map<UUID, OnlineGameSession> games = new ConcurrentHashMap<>();
    private final Map<String, OnlineGameSession> gamesByRoomCode = new ConcurrentHashMap<>();
    private static final String ROOM_CODE_CHARS = "ABCDEFGHJKLMNOPQRSTUVWXYZ23456789"; // Exclude I, O, 0, 1 for clarity
    private static final int ROOM_CODE_LENGTH = 6;
    private final SecureRandom random = new SecureRandom();

    /**
     * Generates a unique 6-character room code.
     */
    private String generateRoomCode() {
        StringBuilder code = new StringBuilder(ROOM_CODE_LENGTH);
        for (int i = 0; i < ROOM_CODE_LENGTH; i++) {
            code.append(ROOM_CODE_CHARS.charAt(random.nextInt(ROOM_CODE_CHARS.length())));
        }
        String roomCode = code.toString();
        // Ensure uniqueness (very unlikely collision, but check anyway)
        while (gamesByRoomCode.containsKey(roomCode)) {
            code = new StringBuilder(ROOM_CODE_LENGTH);
            for (int i = 0; i < ROOM_CODE_LENGTH; i++) {
                code.append(ROOM_CODE_CHARS.charAt(random.nextInt(ROOM_CODE_CHARS.length())));
            }
            roomCode = code.toString();
        }
        return roomCode;
    }

    public OnlineGameSession createGame(String creatorName) {
        Board board = new Board();
        board.restart();
        String roomCode = generateRoomCode();
        OnlineGameSession session = new OnlineGameSession(board, creatorName, roomCode);
        games.put(session.getGameId(), session);
        gamesByRoomCode.put(roomCode, session);
        return session;
    }

    public OnlineGameSession getGame(UUID gameId) {
        OnlineGameSession session = games.get(gameId);
        if (session == null) {
            throw new IllegalArgumentException("Game not found: " + gameId);
        }
        return session;
    }

    public OnlineGameSession getGameByRoomCode(String roomCode) {
        OnlineGameSession session = gamesByRoomCode.get(roomCode.toUpperCase());
        if (session == null) {
            throw new IllegalArgumentException("Game not found for room code: " + roomCode);
        }
        return session;
    }

    public UUID joinGame(UUID gameId, String playerName) {
        OnlineGameSession session = getGame(gameId);
        if (session.isFull()) {
            throw new IllegalStateException("Game already has two players");
        }
        return session.joinAsWhite(playerName);
    }

    public OnlineGameSession.GameStatus getStatus(UUID gameId) {
        return getGame(gameId).getStatus();
    }

    /**
     * Attempts to apply a move for the given player.
     *
     * @return MoveResult describing outcome.
     */
    public MoveResult playMove(UUID gameId, UUID playerId, int x, int y) {
        OnlineGameSession session = getGame(gameId);

        if (session.getStatus() != GameStatus.IN_PROGRESS) {
            return new MoveResult(MoveStatus.GAME_NOT_IN_PROGRESS, "Game is not in progress", session);
        }

        if (!session.isPlayersTurn(playerId)) {
            return new MoveResult(MoveStatus.NOT_YOUR_TURN, "It is not your turn", session);
        }

        boolean isWhite = session.getCurrentTurn() == Turn.WHITE;

        // bounds check before Board.play to avoid IndexOutOfBounds
        if (x < 0 || x >= Board.BOARD_SIZE || y < 0 || y >= Board.BOARD_SIZE) {
            return new MoveResult(MoveStatus.ILLEGAL_MOVE, "Coordinates out of bounds", session);
        }

        boolean ok = session.getBoard().play(x, y, isWhite);
        if (!ok) {
            return new MoveResult(MoveStatus.ILLEGAL_MOVE, "Illegal move", session);
        }

        session.switchTurn();
        return new MoveResult(MoveStatus.OK, "Move accepted", session);
    }

    public enum MoveStatus {
        OK,
        ILLEGAL_MOVE,
        NOT_YOUR_TURN,
        GAME_NOT_IN_PROGRESS
    }

    public record MoveResult(MoveStatus status, String message, OnlineGameSession session) {}
}

