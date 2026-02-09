package com.chatflow.server.model;

import java.time.Instant;

public class ChatResponse {
    private String userId;
    private String username;
    private String message;
    private Instant timestamp;
    private MessageType messageType;
    private Instant serverTimestamp;
    private String status;

    public ChatResponse() {}

    public ChatResponse(ChatMessage msg, String status) {
        this.userId = msg.getUserId();
        this.username = msg.getUsername();
        this.message = msg.getMessage();
        this.timestamp = msg.getTimestamp();
        this.messageType = msg.getMessageType();
        this.serverTimestamp = Instant.now();
        this.status = status;
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public MessageType getMessageType() { return messageType; }
    public void setMessageType(MessageType messageType) { this.messageType = messageType; }

    public Instant getServerTimestamp() { return serverTimestamp; }
    public void setServerTimestamp(Instant serverTimestamp) { this.serverTimestamp = serverTimestamp; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
