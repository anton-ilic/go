package com.go;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private final RoomService roomService;
    private final Gson gson = new Gson();

    /** roomId → set of connected sessions */
    private final Map<String, Set<WebSocketSession>> roomSessions = new ConcurrentHashMap<>();

    public GameWebSocketHandler(RoomService roomService) {
        this.roomService = roomService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String roomId = extractRoomId(session);
        if (roomId == null) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        Room room = roomService.getRoom(roomId);
        if (room == null) {
            sendError(session, "Room not found: " + roomId);
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        roomSessions.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(session);

        // Send current state to the newly connected client
        sendState(session, room);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String roomId = extractRoomId(session);
        if (roomId == null) return;

        Room room = roomService.getRoom(roomId);
        if (room == null) {
            sendError(session, "Room not found");
            return;
        }

        JsonObject json;
        try {
            json = JsonParser.parseString(message.getPayload()).getAsJsonObject();
        } catch (Exception e) {
            sendError(session, "Invalid JSON");
            return;
        }

        String type = json.has("type") ? json.get("type").getAsString() : "";

        switch (type) {
            case "move" -> handleMove(session, room, json);
            case "pass" -> handlePass(session, room, json);
            default -> sendError(session, "Unknown message type: " + type);
        }
    }

    private void handleMove(WebSocketSession session, Room room, JsonObject json) throws IOException {
        int x = json.get("x").getAsInt();
        int y = json.get("y").getAsInt();

        Room.MoveResult result = roomService.applyMove(room.getRoomId(), x, y);

        if (result.success()) {
            broadcastState(room);
        } else {
            sendErrorWithState(session, result.message(), room);
        }
    }

    private void handlePass(WebSocketSession session, Room room, JsonObject json) throws IOException {
        Room.MoveResult result = room.pass();

        if (result.success()) {
            broadcastState(room);
        } else {
            sendErrorWithState(session, result.message(), room);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String roomId = extractRoomId(session);
        if (roomId != null) {
            Set<WebSocketSession> sessions = roomSessions.get(roomId);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    roomSessions.remove(roomId);
                }
            }
        }
    }

    private void broadcastState(Room room) {
        Set<WebSocketSession> sessions = roomSessions.get(room.getRoomId());
        if (sessions == null) return;

        String payload = buildStateJson(room);
        TextMessage msg = new TextMessage(payload);

        for (WebSocketSession s : sessions) {
            if (s.isOpen()) {
                try {
                    s.sendMessage(msg);
                } catch (IOException e) {
                    // Session probably closed; ignore
                }
            }
        }
    }

    private void sendState(WebSocketSession session, Room room) throws IOException {
        session.sendMessage(new TextMessage(buildStateJson(room)));
    }

    private void sendError(WebSocketSession session, String message) throws IOException {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "error");
        obj.addProperty("message", message);
        session.sendMessage(new TextMessage(gson.toJson(obj)));
    }

    private void sendErrorWithState(WebSocketSession session, String message, Room room) throws IOException {
        JsonObject obj = JsonParser.parseString(buildStateJson(room)).getAsJsonObject();
        obj.addProperty("type", "error");
        obj.addProperty("message", message);
        session.sendMessage(new TextMessage(gson.toJson(obj)));
    }

    private String buildStateJson(Room room) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "state");
        obj.addProperty("roomId", room.getRoomId());
        obj.addProperty("turn", room.getTurn());
        obj.addProperty("moveNumber", room.getMoveNumber());
        obj.addProperty("komi", room.getKomi());
        obj.addProperty("gameEnded", room.isGameEnded());
        obj.addProperty("scoreBlack", room.getScoreBlack());
        obj.addProperty("scoreWhite", room.getScoreWhite());
        obj.addProperty("canUndo", room.canUndo());
        obj.addProperty("canRedo", room.canRedo());
        JsonObject prisonersObj = new JsonObject();
        prisonersObj.addProperty("black", room.getBlackPrisoners());
        prisonersObj.addProperty("white", room.getWhitePrisoners());
        obj.add("prisoners", prisonersObj);

        // Build board
        int size = room.getBoard().getBoardSize();
        JsonObject boardObj = new JsonObject();
        boardObj.addProperty("boardSize", size);

        List<JsonObject> stones = new ArrayList<>();
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                int stone = room.getBoard().getStoneAt(x, y);
                if (stone != Board.EMPTY) {
                    JsonObject s = new JsonObject();
                    s.addProperty("x", x);
                    s.addProperty("y", y);
                    s.addProperty("color", stone == Board.WHITE ? "WHITE" : "BLACK");
                    stones.add(s);
                }
            }
        }

        boardObj.add("stones", gson.toJsonTree(stones));
        obj.add("board", boardObj);

        return gson.toJson(obj);
    }

    private String extractRoomId(WebSocketSession session) {
        if (session.getUri() == null) return null;
        String path = session.getUri().getPath();
        // Path is like /ws/game/ABC123XY
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash < 0 || lastSlash == path.length() - 1) return null;
        return path.substring(lastSlash + 1).toUpperCase();
    }
}
