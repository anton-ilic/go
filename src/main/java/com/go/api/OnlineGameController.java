package com.go.api;

import com.go.Board;
import com.go.OnlineGameService;
import com.go.OnlineGameSession;
import com.go.api.dto.BoardStateDto;
import com.go.api.dto.CreateOnlineGameRequest;
import com.go.api.dto.CreateOnlineGameResponse;
import com.go.api.dto.JoinOnlineGameRequest;
import com.go.api.dto.JoinOnlineGameResponse;
import com.go.api.dto.OnlineGameStateDto;
import com.go.api.dto.OnlineMoveRequest;
import com.go.api.dto.OnlineMoveResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/online-games")
public class OnlineGameController {

    private final OnlineGameService onlineGameService;

    public OnlineGameController(OnlineGameService onlineGameService) {
        this.onlineGameService = onlineGameService;
    }

    @PostMapping
    public ResponseEntity<CreateOnlineGameResponse> create(@RequestBody CreateOnlineGameRequest request) {
        String playerName = request.playerName() != null && !request.playerName().isBlank()
                ? request.playerName()
                : "Player 1";

        OnlineGameSession session = onlineGameService.createGame(playerName);

        OnlineGameStateDto stateDto = toStateDto(session);
        return ResponseEntity.ok(new CreateOnlineGameResponse(
                session.getGameId().toString(),
                session.getRoomCode(),
                session.getBlackPlayerId().toString(),
                "BLACK",
                stateDto
        ));
    }

    @PostMapping("/{gameId}/join")
    public ResponseEntity<JoinOnlineGameResponse> join(@PathVariable String gameId,
                                                       @RequestBody JoinOnlineGameRequest request) {
        OnlineGameSession session;
        try {
            // Try as UUID first, then as room code
            try {
                UUID gameUuid = UUID.fromString(gameId);
                session = onlineGameService.getGame(gameUuid);
            } catch (IllegalArgumentException ex) {
                // Not a UUID, try as room code
                session = onlineGameService.getGameByRoomCode(gameId);
            }
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        String playerName = request.playerName() != null && !request.playerName().isBlank()
                ? request.playerName()
                : "Player 2";

        try {
            UUID playerId = onlineGameService.joinGame(session.getGameId(), playerName);
            OnlineGameSession updatedSession = onlineGameService.getGame(session.getGameId());
            OnlineGameStateDto stateDto = toStateDto(updatedSession);
            return ResponseEntity.ok(new JoinOnlineGameResponse(
                    updatedSession.getGameId().toString(),
                    updatedSession.getRoomCode(),
                    playerId.toString(),
                    "WHITE",
                    stateDto
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    @GetMapping("/{gameId}")
    public ResponseEntity<OnlineGameStateDto> getState(@PathVariable String gameId,
                                                       @RequestParam("playerId") String playerId) {
        OnlineGameSession session;
        UUID playerUuid;
        try {
            playerUuid = UUID.fromString(playerId);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        try {
            // Try as UUID first, then as room code
            try {
                UUID gameUuid = UUID.fromString(gameId);
                session = onlineGameService.getGame(gameUuid);
            } catch (IllegalArgumentException ex) {
                // Not a UUID, try as room code
                session = onlineGameService.getGameByRoomCode(gameId);
            }
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        // For now we only check that the playerId matches one of the players if game is not waiting
        if (session.isFull()
                && !(playerUuid.equals(session.getBlackPlayerId())
                || playerUuid.equals(session.getWhitePlayerId()))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return ResponseEntity.ok(toStateDto(session));
    }

    @PostMapping("/{gameId}/moves")
    public ResponseEntity<OnlineMoveResponse> move(@PathVariable String gameId,
                                                   @RequestBody OnlineMoveRequest request) {
        OnlineGameSession session;
        UUID playerUuid;
        try {
            playerUuid = UUID.fromString(request.playerId());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    new OnlineMoveResponse("BAD_REQUEST", "Invalid playerId", null)
            );
        }

        try {
            // Try as UUID first, then as room code
            try {
                UUID gameUuid = UUID.fromString(gameId);
                session = onlineGameService.getGame(gameUuid);
            } catch (IllegalArgumentException ex) {
                // Not a UUID, try as room code
                session = onlineGameService.getGameByRoomCode(gameId);
            }
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new OnlineMoveResponse("GAME_NOT_FOUND", "Game not found", null));
        }

        try {
            OnlineGameService.MoveResult result =
                    onlineGameService.playMove(session.getGameId(), playerUuid, request.x(), request.y());

            OnlineGameStateDto stateDto = toStateDto(result.session());
            return ResponseEntity.ok(new OnlineMoveResponse(
                    result.status().name(),
                    result.message(),
                    stateDto
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new OnlineMoveResponse("GAME_NOT_FOUND", "Game not found", null));
        }
    }

    private OnlineGameStateDto toStateDto(OnlineGameSession session) {
        int size = Board.BOARD_SIZE;
        List<BoardStateDto.StoneDto> stones = new ArrayList<>();
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                int stone = session.getBoard().getStoneAt(x, y);
                if (stone == Board.WHITE) {
                    stones.add(new BoardStateDto.StoneDto(x, y, "WHITE"));
                } else if (stone == Board.BLACK) {
                    stones.add(new BoardStateDto.StoneDto(x, y, "BLACK"));
                }
            }
        }

        BoardStateDto boardState = new BoardStateDto(size, stones);
        return new OnlineGameStateDto(
                boardState,
                session.getCurrentTurn().name(),
                session.getStatus().name(),
                session.getBlackPlayerName(),
                session.getWhitePlayerName()
        );
    }
}

