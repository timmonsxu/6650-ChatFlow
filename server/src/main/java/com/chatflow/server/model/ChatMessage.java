package com.chatflow.server.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;

public class ChatMessage {
    private String userId;
    private String username;
    private String message;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant timestamp;
    private MessageType messageType;

    public ChatMessage() {}

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
}
