package com.chatflow.server.handler;

import com.chatflow.server.model.ChatMessage;
import com.chatflow.server.model.ChatResponse;
import com.chatflow.server.model.MessageType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);
    private final ObjectMapper mapper;
    // Track sessions per room for future use (A2+)
    private final ConcurrentHashMap<String, List<WebSocketSession>> rooms = new ConcurrentHashMap<>();

    public ChatWebSocketHandler() {
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
        this.mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String roomId = extractRoomId(session);
        rooms.computeIfAbsent(roomId, k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(session);
        log.info("Connection opened: session={}, room={}", session.getId(), roomId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String roomId = extractRoomId(session);
        List<WebSocketSession> roomSessions = rooms.get(roomId);
        if (roomSessions != null) {
            roomSessions.remove(session);
        }
        log.info("Connection closed: session={}, room={}, status={}", session.getId(), roomId, status);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();

        // Parse
        ChatMessage chatMsg;
        try {
            chatMsg = mapper.readValue(payload, ChatMessage.class);
        } catch (Exception e) {
            sendError(session, "Invalid JSON format: " + e.getMessage());
            return;
        }

        // Validate
        List<String> errors = validate(chatMsg);
        if (!errors.isEmpty()) {
            sendError(session, "Validation failed: " + String.join("; ", errors));
            return;
        }

        // Echo back with server timestamp and status
        ChatResponse response = new ChatResponse(chatMsg, "OK");
        String responseJson = mapper.writeValueAsString(response);
        session.sendMessage(new TextMessage(responseJson));
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("Transport error: session={}, error={}", session.getId(), exception.getMessage());
    }

    private List<String> validate(ChatMessage msg) {
        List<String> errors = new ArrayList<>();

        // userId: must be string representing number between 1 and 100,000
        if (msg.getUserId() == null || msg.getUserId().isBlank()) {
            errors.add("userId is required");
        } else {
            try {
                int id = Integer.parseInt(msg.getUserId());
                if (id < 1 || id > 100000) {
                    errors.add("userId must be between 1 and 100000");
                }
            } catch (NumberFormatException e) {
                errors.add("userId must be a numeric string");
            }
        }

        // username: 3-20 alphanumeric characters
        if (msg.getUsername() == null || !msg.getUsername().matches("^[a-zA-Z0-9]{3,20}$")) {
            errors.add("username must be 3-20 alphanumeric characters");
        }

        // message: 1-500 characters
        if (msg.getMessage() == null || msg.getMessage().isEmpty() || msg.getMessage().length() > 500) {
            errors.add("message must be 1-500 characters");
        }

        // timestamp: must be present (Jackson would fail to parse invalid ISO-8601)
        if (msg.getTimestamp() == null) {
            errors.add("timestamp is required and must be valid ISO-8601");
        }

        // messageType: must be one of TEXT, JOIN, LEAVE
        if (msg.getMessageType() == null) {
            errors.add("messageType must be one of: TEXT, JOIN, LEAVE");
        }

        return errors;
    }

    private void sendError(WebSocketSession session, String errorMsg) throws Exception {
        String json = mapper.writeValueAsString(
                java.util.Map.of("status", "ERROR", "error", errorMsg)
        );
        session.sendMessage(new TextMessage(json));
    }

    private String extractRoomId(WebSocketSession session) {
        // URI is like /chat/5 -> extract "5"
        String path = session.getUri().getPath();
        String[] parts = path.split("/");
        return parts.length >= 3 ? parts[2] : "default";
    }
}
