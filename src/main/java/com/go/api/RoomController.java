package com.go.api;

import com.go.Board;
import com.go.Room;
import com.go.RoomService;
import com.go.api.dto.BoardStateDto;
import com.go.api.dto.CreateRoomRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
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
     * Optional body: { "boardSize": 9|11|19, "komi": number, "startingColor": "BLACK"|"WHITE" }.
     * Returns { roomId, boardSize }.
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createRoom(@RequestBody(required = false) CreateRoomRequest request) {
        Room room = roomService.createRoom(request);
        return ResponseEntity.ok(Map.<String, Object>of(
                "roomId", room.getRoomId(),
                "boardSize", room.getBoardSize()
        ));
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

        Map<String, Object> body = new HashMap<>();
        body.put("roomId", room.getRoomId());
        body.put("boardSize", room.getBoardSize());
        body.put("turn", room.getTurn());
        body.put("moveNumber", room.getMoveNumber());
        body.put("komi", room.getKomi());
        body.put("gameEnded", room.isGameEnded());
        body.put("scoreBlack", room.getScoreBlack());
        body.put("scoreWhite", room.getScoreWhite());
        body.put("prisoners", toPrisonersDto(room));
        body.put("board", toBoardDto(room));
        body.put("canUndo", room.canUndo());
        body.put("canRedo", room.canRedo());
        return ResponseEntity.ok(body);
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

        Room.MoveResult result = roomService.applyMove(roomId, x, y);
        Room updatedRoom = roomService.getRoom(roomId);
        if (updatedRoom == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", "Room not found"));
        }

        if (result.success()) {
            return ResponseEntity.ok(toRoomStateMap(updatedRoom, true, result.message()));
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(toRoomStateMap(updatedRoom, false, result.message()));
        }
    }

    /**
     * POST /api/rooms/{roomId}/undo — Undo the last move.
     */
    @PostMapping("/{roomId}/undo")
    public ResponseEntity<?> undo(@PathVariable("roomId") String roomId) {
        Room room = roomService.getRoom(roomId);
        if (room == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", "Room not found"));
        }
        boolean did = roomService.undo(roomId);
        if (!did) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(toRoomStateMap(room, false, "Nothing to undo"));
        }
        Room updated = roomService.getRoom(roomId);
        return ResponseEntity.ok(toRoomStateMap(updated, true, "Move undone"));
    }

    /**
     * POST /api/rooms/{roomId}/redo — Redo a previously undone move.
     */
    @PostMapping("/{roomId}/redo")
    public ResponseEntity<?> redo(@PathVariable("roomId") String roomId) {
        Room room = roomService.getRoom(roomId);
        if (room == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", "Room not found"));
        }
        boolean did = roomService.redo(roomId);
        if (!did) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(toRoomStateMap(room, false, "Nothing to redo"));
        }
        Room updated = roomService.getRoom(roomId);
        return ResponseEntity.ok(toRoomStateMap(updated, true, "Move redone"));
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
                return ResponseEntity.ok(toRoomStateMap(room, true, result.message()));
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(toRoomStateMap(room, false, result.message()));
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
        int size = room.getBoard().getBoardSize();
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

    private Map<String, Integer> toPrisonersDto(Room room) {
        return Map.of(
                "black", room.getBlackPrisoners(),
                "white", room.getWhitePrisoners()
        );
    }

    private Map<String, Object> toRoomStateMap(Room room, boolean success, String message) {
        Map<String, Object> m = new HashMap<>();
        m.put("success", success);
        if (message != null) m.put("message", message);
        m.put("roomId", room.getRoomId());
        m.put("turn", room.getTurn());
        m.put("moveNumber", room.getMoveNumber());
        m.put("komi", room.getKomi());
        m.put("gameEnded", room.isGameEnded());
        m.put("scoreBlack", room.getScoreBlack());
        m.put("scoreWhite", room.getScoreWhite());
        m.put("prisoners", toPrisonersDto(room));
        m.put("board", toBoardDto(room));
        m.put("canUndo", room.canUndo());
        m.put("canRedo", room.canRedo());
        return m;
    }
}
