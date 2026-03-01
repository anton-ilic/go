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

        return ResponseEntity.ok(Map.of(
                "roomId", room.getRoomId(),
                "boardSize", room.getBoardSize(),
                "turn", room.getTurn(),
                "moveNumber", room.getMoveNumber(),
                "prisoners", toPrisonersDto(room),
                "board", toBoardDto(room),
                "canUndo", room.canUndo(),
                "canRedo", room.canRedo()
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

        Room.MoveResult result = roomService.applyMove(roomId, x, y);
        Room updatedRoom = roomService.getRoom(roomId);
        if (updatedRoom == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", "Room not found"));
        }

        if (result.success()) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", result.message(),
                    "roomId", updatedRoom.getRoomId(),
                    "turn", updatedRoom.getTurn(),
                    "moveNumber", updatedRoom.getMoveNumber(),
                    "prisoners", toPrisonersDto(updatedRoom),
                    "board", toBoardDto(updatedRoom),
                    "canUndo", updatedRoom.canUndo(),
                    "canRedo", updatedRoom.canRedo()
            ));
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                            "success", false,
                            "message", result.message(),
                            "roomId", updatedRoom.getRoomId(),
                            "turn", updatedRoom.getTurn(),
                            "moveNumber", updatedRoom.getMoveNumber(),
                            "prisoners", toPrisonersDto(updatedRoom),
                            "board", toBoardDto(updatedRoom),
                            "canUndo", updatedRoom.canUndo(),
                            "canRedo", updatedRoom.canRedo()
                    ));
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
                    .body(Map.of(
                            "success", false,
                            "message", "Nothing to undo",
                            "roomId", room.getRoomId(),
                            "turn", room.getTurn(),
                            "moveNumber", room.getMoveNumber(),
                            "prisoners", toPrisonersDto(room),
                            "board", toBoardDto(room)
                    ));
        }
        Room updated = roomService.getRoom(roomId);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Move undone",
                "roomId", updated.getRoomId(),
                "turn", updated.getTurn(),
                "moveNumber", updated.getMoveNumber(),
                "prisoners", toPrisonersDto(updated),
                "board", toBoardDto(updated),
                "canUndo", updated.canUndo(),
                "canRedo", updated.canRedo()
        ));
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
                    .body(Map.of(
                            "success", false,
                            "message", "Nothing to redo",
                            "roomId", room.getRoomId(),
                            "turn", room.getTurn(),
                            "moveNumber", room.getMoveNumber(),
                            "prisoners", toPrisonersDto(room),
                            "board", toBoardDto(room)
                    ));
        }
        Room updated = roomService.getRoom(roomId);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Move redone",
                "roomId", updated.getRoomId(),
                "turn", updated.getTurn(),
                "moveNumber", updated.getMoveNumber(),
                "prisoners", toPrisonersDto(updated),
                "board", toBoardDto(updated),
                "canUndo", updated.canUndo(),
                "canRedo", updated.canRedo()
        ));
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
                        "prisoners", toPrisonersDto(room),
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
                                "prisoners", toPrisonersDto(room),
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
}
