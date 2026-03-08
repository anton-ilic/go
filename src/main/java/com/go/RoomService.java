package com.go;

import com.go.api.dto.CreateRoomRequest;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages game rooms: in-memory cache with persistence to the database.
 * Rooms survive server restarts; undo/redo stacks are also persisted separately.
 */
@Service
public class RoomService {

    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final BoardStateStackRepository stackRepository;
    private final RoomRepository roomRepository;
    private static final String ROOM_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int ROOM_CODE_LENGTH = 8;
    private final SecureRandom random = new SecureRandom();

    public RoomService(BoardStateStackRepository stackRepository, RoomRepository roomRepository) {
        this.stackRepository = stackRepository;
        this.roomRepository = roomRepository;
    }

    /**
     * Generates a unique room ID (checks DB and cache).
     */
    private String generateRoomId() {
        String id;
        do {
            StringBuilder sb = new StringBuilder(ROOM_CODE_LENGTH);
            for (int i = 0; i < ROOM_CODE_LENGTH; i++) {
                sb.append(ROOM_CODE_CHARS.charAt(random.nextInt(ROOM_CODE_CHARS.length())));
            }
            id = sb.toString();
        } while (rooms.containsKey(id) || roomRepository.existsByRoomId(id));
        return id;
    }

    /**
     * Creates a new room with an empty board and persists it.
     * @param request optional settings (boardSize 9/11/19, komi, startingColor); null safe.
     */
    public Room createRoom(CreateRoomRequest request) {
        String roomId = generateRoomId();
        int boardSize = Board.DEFAULT_BOARD_SIZE;
        if (request != null && request.boardSize() != null) {
            int s = request.boardSize();
            if (s == 9 || s == 11 || s == 19) {
                boardSize = s;
            }
        }
        double komi = 6.5;
        if (request != null && request.komi() != null) {
            komi = request.komi();
        }
        Room room = new Room(roomId, boardSize, komi);
        roomRepository.save(room);
        rooms.put(roomId, room);
        return room;
    }

    /** Creates a new room with default settings. */
    public Room createRoom() {
        return createRoom(null);
    }

    /**
     * Looks up a room by its ID (cache first, then DB).
     *
     * @return the Room, or null if not found
     */
    public Room getRoom(String roomId) {
        String key = roomId.toUpperCase();
        Room cached = rooms.get(key);
        if (cached != null) return cached;
        return roomRepository.findByRoomId(key)
                .map(room -> {
                    // Restore undo/redo stacks from DB so undo/redo works after load/restart
                    var undoStates = stackRepository.listAll("room", key, "undo");
                    var redoStates = stackRepository.listAll("room", key, "redo");
                    room.getBoard().loadUndoStack(undoStates);
                    room.getBoard().loadRedoStack(redoStates);
                    rooms.put(key, room);
                    return room;
                })
                .orElse(null);
    }

    private void saveRoom(Room room) {
        if (room != null) roomRepository.save(room);
    }

    /**
     * Applies a move and persists the saved state to the undo stack in the database.
     */
    public Room.MoveResult applyMove(String roomId, int x, int y) {
        Room room = getRoom(roomId);
        if (room == null) return new Room.MoveResult(false, "Room not found", null);
        Room.MoveResult result = room.applyMove(x, y);
        if (result.success()) {
            if (result.statePushedForUndo() != null) {
                stackRepository.push("room", roomId.toUpperCase(), "undo", result.statePushedForUndo());
                stackRepository.clear("room", roomId.toUpperCase(), "redo");
            }
            saveRoom(room);
        }
        return result;
    }

    /**
     * Pass turn. Persists room state after pass (including game-over + scores).
     */
    public Room.MoveResult pass(String roomId) {
        Room room = getRoom(roomId);
        if (room == null) return new Room.MoveResult(false, "Room not found", null);
        Room.MoveResult result = room.pass();
        if (result.success()) saveRoom(room);
        return result;
    }

    /**
     * Undoes the last move and syncs undo/redo stacks with the database.
     */
    public boolean undo(String roomId) {
        Room room = getRoom(roomId);
        if (room == null || !room.canUndo()) return false;
        BoardStateSnapshot beforeUndo = room.getBoard().createStateSnapshot();
        boolean did = room.undo();
        if (did) {
            stackRepository.pop("room", roomId.toUpperCase(), "undo");
            stackRepository.push("room", roomId.toUpperCase(), "redo", beforeUndo);
            saveRoom(room);
        }
        return did;
    }

    /**
     * Redoes a previously undone move and syncs with the database.
     */
    public boolean redo(String roomId) {
        Room room = getRoom(roomId);
        if (room == null || !room.canRedo()) return false;
        boolean did = room.redo();
        if (did) {
            stackRepository.pop("room", roomId.toUpperCase(), "redo");
            saveRoom(room);
        }
        return did;
    }

    /**
     * Current player resigns. Game ends; winner is the opposite color.
     */
    public boolean resign(String roomId) {
        Room room = getRoom(roomId);
        if (room == null) return false;
        boolean did = room.resign();
        if (did) saveRoom(room);
        return did;
    }

    /**
     * Set or clear territory mark. Only in scoring phase (game ended by double-pass).
     */
    public boolean setTerritoryMark(String roomId, int x, int y, String color) {
        Room room = getRoom(roomId);
        if (room == null) return false;
        boolean did = room.setTerritoryMark(x, y, color);
        if (did) saveRoom(room);
        return did;
    }

    /**
     * Toggle dead-stone mark at (x,y). Only in scoring phase.
     */
    public boolean toggleDeadStone(String roomId, int x, int y) {
        Room room = getRoom(roomId);
        if (room == null) return false;
        boolean did = room.toggleDeadStone(x, y);
        if (did) saveRoom(room);
        return did;
    }
}
