package com.go.api;

import com.go.Board;
import com.go.GameService;
import com.go.GameSession;
import com.go.Level;
import com.go.api.dto.BoardStateDto;
import com.go.api.dto.CreateGameRequest;
import com.go.api.dto.CreateGameResponse;
import com.go.api.dto.GameStateDto;
import com.go.api.dto.MoveRequest;
import com.go.api.dto.MoveResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/games")
public class GameController {

    private final GameService gameService;

    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    @PostMapping
    public ResponseEntity<CreateGameResponse> createGame(@RequestBody CreateGameRequest request) {
        try {
            GameSession session = gameService.createSession(request.puzzleId());
            GameStateDto state = toGameState(session.getLevel());
            String playerColor = session.isPlayerWhite() ? "WHITE" : "BLACK";
            return ResponseEntity.ok(new CreateGameResponse(
                    session.getId().toString(),
                    playerColor,
                    state
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @PostMapping("/{gameId}/moves")
    public ResponseEntity<MoveResponse> playMove(@PathVariable String gameId,
                                                 @RequestBody MoveRequest request) {
        UUID id;
        try {
            id = UUID.fromString(gameId);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new MoveResponse(gameId, "INVALID_GAME_ID", "Invalid game id", null));
        }

        try {
            boolean correct = gameService.playMove(id, request.x(), request.y());
            GameSession session = gameService.getSession(id);
            Level level = session.getLevel();
            GameStateDto state = toGameState(level);

            String status;
            String message;
            if (!correct) {
                status = "INCORRECT_MOVE";
                message = "Incorrect move. Puzzle has been reset.";
            } else if (level.isSolved()) {
                status = "SOLVED";
                message = "Puzzle solved!";
            } else {
                status = "IN_PROGRESS";
                message = "Move accepted.";
            }

            return ResponseEntity.ok(new MoveResponse(
                    gameId,
                    status,
                    message,
                    state
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new MoveResponse(gameId, "GAME_NOT_FOUND", "Game not found", null));
        }
    }

    private GameStateDto toGameState(Level level) {
        int size = Board.BOARD_SIZE;
        List<BoardStateDto.StoneDto> stones = new ArrayList<>();

        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                int stone = level.getStoneAt(x, y);
                if (stone == Board.WHITE) {
                    stones.add(new BoardStateDto.StoneDto(x, y, "WHITE"));
                } else if (stone == Board.BLACK) {
                    stones.add(new BoardStateDto.StoneDto(x, y, "BLACK"));
                }
            }
        }

        BoardStateDto boardState = new BoardStateDto(size, stones);
        return new GameStateDto(boardState, level.isSolved());
    }
}

