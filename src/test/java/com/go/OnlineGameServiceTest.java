package com.go;

import com.go.OnlineGameSession.GameStatus;
import com.go.OnlineGameService.MoveResult;
import com.go.OnlineGameService.MoveStatus;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OnlineGameServiceTest {

    @Test
    void createGameInitializesBoardAndBlackPlayer() {
        OnlineGameService service = new OnlineGameService();

        OnlineGameSession session = service.createGame("Alice");

        assertNotNull(session.getGameId());
        assertNotNull(session.getBlackPlayerId());
        assertEquals("Alice", session.getBlackPlayerName());
        assertEquals(GameStatus.WAITING_FOR_OPPONENT, session.getStatus());
    }

    @Test
    void joinGameAssignsWhiteAndStartsGame() {
        OnlineGameService service = new OnlineGameService();

        OnlineGameSession session = service.createGame("Alice");
        UUID gameId = session.getGameId();

        UUID whiteId = service.joinGame(gameId, "Bob");

        OnlineGameSession updated = service.getGame(gameId);
        assertEquals(whiteId, updated.getWhitePlayerId());
        assertEquals("Bob", updated.getWhitePlayerName());
        assertEquals(GameStatus.IN_PROGRESS, updated.getStatus());
    }

    @Test
    void secondJoinFailsWhenGameFull() {
        OnlineGameService service = new OnlineGameService();

        OnlineGameSession session = service.createGame("Alice");
        UUID gameId = session.getGameId();
        service.joinGame(gameId, "Bob");

        assertThrows(IllegalStateException.class, () -> service.joinGame(gameId, "Charlie"));
    }

    @Test
    void playMoveEnforcesTurn() {
        OnlineGameService service = new OnlineGameService();

        OnlineGameSession session = service.createGame("Alice");
        UUID gameId = session.getGameId();
        UUID blackId = session.getBlackPlayerId();
        UUID whiteId = service.joinGame(gameId, "Bob");

        // Black to move first
        MoveResult wrongTurnResult = service.playMove(gameId, whiteId, 0, 0);
        assertEquals(MoveStatus.NOT_YOUR_TURN, wrongTurnResult.status());

        MoveResult okResult = service.playMove(gameId, blackId, 0, 0);
        assertEquals(MoveStatus.OK, okResult.status());
    }
}

