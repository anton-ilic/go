package com.go;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages in-memory game rooms.
 */
@Service
public class RoomService {

    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private static final String ROOM_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int ROOM_CODE_LENGTH = 8;
    private final SecureRandom random = new SecureRandom();

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
     */
    public Room createRoom() {
        String roomId = generateRoomId();
        Room room = new Room(roomId);
        rooms.put(roomId, room);
        return room;
    }

    /**
     * Looks up a room by its ID.
     *
     * @return the Room, or null if not found
     */
    public Room getRoom(String roomId) {
        return rooms.get(roomId.toUpperCase());
    }
}
