package com.go;

import com.go.api.dto.CreateRoomRequest;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages in-memory game rooms and persists undo/redo stacks to the database.
 */
@Service
public class RoomService {

    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final BoardStateStackRepository stackRepository;
    private static final String ROOM_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int ROOM_CODE_LENGTH = 8;
    private final SecureRandom random = new SecureRandom();

    public RoomService(BoardStateStackRepository stackRepository) {
        this.stackRepository = stackRepository;
    }

    /**
     * Generates a unique room ID.
     */
    private String generateRoomId() {
        String id;
        do {
            StringBuilder sb = new StringBuilder(ROOM_CODE_LENGTH);
            for (int i = 0; i < ROOM_CODE_LENGTH; i++) {
                sb.append(ROOM_CODE_CHARS.charAt(random.nextInt(ROOM_CODE_CHARS.length())));
            }
            id = sb.toString();
        } while (rooms.containsKey(id));
        return id;
    }

    /**
     * Creates a new room with an empty board.
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
        Room room = new Room(roomId, boardSize);
        rooms.put(roomId, room);
        return room;
    }

    /** Creates a new room with default settings. */
    public Room createRoom() {
        return createRoom(null);
    }

    /**
     * Looks up a room by its ID.
     *
     * @return the Room, or null if not found
     */
    public Room getRoom(String roomId) {
        return rooms.get(roomId.toUpperCase());
    }

    /**
     * Applies a move and persists the saved state to the undo stack in the database.
     */
    public Room.MoveResult applyMove(String roomId, int x, int y) {
        Room room = getRoom(roomId);
        if (room == null) return new Room.MoveResult(false, "Room not found", null);
        Room.MoveResult result = room.applyMove(x, y);
        if (result.success() && result.statePushedForUndo() != null) {
            stackRepository.push("room", roomId.toUpperCase(), "undo", result.statePushedForUndo());
            stackRepository.clear("room", roomId.toUpperCase(), "redo");
        }
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
        }
        return did;
    }
}
