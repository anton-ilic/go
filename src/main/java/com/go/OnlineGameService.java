package com.go;

import com.go.OnlineGameSession.GameStatus;
import com.go.OnlineGameSession.Turn;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages in-memory online games for two remote players.
 */
@Service
public class OnlineGameService {

    private final Map<UUID, OnlineGameSession> games = new ConcurrentHashMap<>();

    public OnlineGameSession createGame(String creatorName) {
        Board board = new Board();
        board.restart();
        OnlineGameSession session = new OnlineGameSession(board, creatorName);
        games.put(session.getGameId(), session);
        return session;
    }

    public OnlineGameSession getGame(UUID gameId) {
        OnlineGameSession session = games.get(gameId);
        if (session == null) {
            throw new IllegalArgumentException("Game not found: " + gameId);
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

