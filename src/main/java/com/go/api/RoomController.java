package com.go.api;

import com.go.Board;
import com.go.Room;
import com.go.RoomService;
import com.go.api.dto.BoardStateDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    /**
     * POST /api/rooms — Create a new room.
     * Returns { roomId }.
     */
    @PostMapping
    public ResponseEntity<Map<String, String>> createRoom() {
        Room room = roomService.createRoom();
        return ResponseEntity.ok(Map.of("roomId", room.getRoomId()));
    }

    /**
     * GET /api/rooms/{roomId} — Get current room state (for initial page load before WS connects).
     */
    @GetMapping("/{roomId}")
    public ResponseEntity<?> getRoom(@PathVariable("roomId") String roomId) {
        Room room = roomService.getRoom(roomId);
        if (room == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Room not found"));
        }

        return ResponseEntity.ok(Map.of(
                "roomId", room.getRoomId(),
                "turn", room.getTurn(),
                "moveNumber", room.getMoveNumber(),
                "board", toBoardDto(room)
        ));
    }

    /**
     * POST /api/rooms/{roomId}/moves — Make a move.
     * Body: { "x": int, "y": int }
     */
    @PostMapping("/{roomId}/moves")
    public ResponseEntity<?> makeMove(
            @PathVariable("roomId") String roomId,
            @RequestBody(required = false) Map<String, Object> request) {
        Room room = roomService.getRoom(roomId);
        if (room == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", "Room not found"));
        }

        if (request == null || request.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "message", "Request body is empty"));
        }

        // Simple: just get x and y, place the stone
        Object xObj = request.get("x");
        Object yObj = request.get("y");
        
        if (xObj == null || yObj == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "message", "Missing x or y", "request", request.toString()));
        }
        
        int x = ((Number) xObj).intValue();
        int y = ((Number) yObj).intValue();

        Room.MoveResult result = room.applyMove(x, y);

        if (result.success()) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", result.message(),
                    "roomId", room.getRoomId(),
                    "turn", room.getTurn(),
                    "moveNumber", room.getMoveNumber(),
                    "board", toBoardDto(room)
            ));
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                            "success", false,
                            "message", result.message(),
                            "roomId", room.getRoomId(),
                            "turn", room.getTurn(),
                            "moveNumber", room.getMoveNumber(),
                            "board", toBoardDto(room)
                    ));
        }
    }

    /**
     * POST /api/rooms/{roomId}/pass — Pass turn (REST fallback).
     * No body required.
     */
    @PostMapping("/{roomId}/pass")
    public ResponseEntity<?> passTurn(@PathVariable("roomId") String roomId) {
        Room room = roomService.getRoom(roomId);
        if (room == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Room not found"));
        }

        try {
            Room.MoveResult result = room.pass();

            if (result.success()) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", result.message(),
                        "roomId", room.getRoomId(),
                        "turn", room.getTurn(),
                        "moveNumber", room.getMoveNumber(),
                        "board", toBoardDto(room)
                ));
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of(
                                "success", false,
                                "message", result.message(),
                                "roomId", room.getRoomId(),
                                "turn", room.getTurn(),
                                "moveNumber", room.getMoveNumber(),
                                "board", toBoardDto(room)
                        ));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                            "success", false,
                            "message", "Error: " + e.getMessage()
                    ));
        }
    }

    private BoardStateDto toBoardDto(Room room) {
        int size = Board.BOARD_SIZE;
        List<BoardStateDto.StoneDto> stones = new ArrayList<>();
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                int stone = room.getBoard().getStoneAt(x, y);
                if (stone == Board.WHITE) {
                    stones.add(new BoardStateDto.StoneDto(x, y, "WHITE"));
                } else if (stone == Board.BLACK) {
                    stones.add(new BoardStateDto.StoneDto(x, y, "BLACK"));
                }
            }
        }
        return new BoardStateDto(size, stones);
    }
}
