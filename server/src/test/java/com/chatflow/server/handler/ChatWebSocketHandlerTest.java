package com.chatflow.server.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;

class ChatWebSocketHandlerTest {

    private ChatWebSocketHandler handler;
    private WebSocketSession session;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        handler = new ChatWebSocketHandler();
        session = mock(WebSocketSession.class);
        mapper = new ObjectMapper();
        when(session.getId()).thenReturn("test-session");
        when(session.getUri()).thenReturn(URI.create("ws://localhost:8080/chat/1"));
    }

    private String captureResponse() throws Exception {
        var captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());
        return captor.getValue().getPayload();
    }

    // ===== Valid message tests =====

    @Test
    void validTextMessage_returnsOK() throws Exception {
        String msg = """
                {"userId":"123","username":"user123","message":"hello","timestamp":"2026-02-07T12:00:00Z","messageType":"TEXT"}""";

        handler.handleTextMessage(session, new TextMessage(msg));

        String response = captureResponse();
        JsonNode node = mapper.readTree(response);
        assertEquals("OK", node.get("status").asText());
        assertEquals("123", node.get("userId").asText());
        assertEquals("user123", node.get("username").asText());
        assertEquals("hello", node.get("message").asText());
        assertNotNull(node.get("serverTimestamp"));
    }

    @Test
    void validJoinMessage_returnsOK() throws Exception {
        String msg = """
                {"userId":"1","username":"abc","message":"joining","timestamp":"2026-02-07T12:00:00Z","messageType":"JOIN"}""";

        handler.handleTextMessage(session, new TextMessage(msg));

        String response = captureResponse();
        JsonNode node = mapper.readTree(response);
        assertEquals("OK", node.get("status").asText());
        assertEquals("JOIN", node.get("messageType").asText());
    }

    @Test
    void validLeaveMessage_returnsOK() throws Exception {
        String msg = """
                {"userId":"100000","username":"user100000","message":"bye","timestamp":"2026-02-07T12:00:00Z","messageType":"LEAVE"}""";

        handler.handleTextMessage(session, new TextMessage(msg));

        String response = captureResponse();
        JsonNode node = mapper.readTree(response);
        assertEquals("OK", node.get("status").asText());
        assertEquals("LEAVE", node.get("messageType").asText());
    }

    // ===== Invalid JSON =====

    @Test
    void invalidJson_returnsError() throws Exception {
        handler.handleTextMessage(session, new TextMessage("not json"));

        String response = captureResponse();
        JsonNode node = mapper.readTree(response);
        assertEquals("ERROR", node.get("status").asText());
        assertTrue(node.get("error").asText().contains("Invalid JSON"));
    }

    // ===== userId validation =====

    @Test
    void userId_zero_returnsError() throws Exception {
        String msg = """
                {"userId":"0","username":"user123","message":"hi","timestamp":"2026-02-07T12:00:00Z","messageType":"TEXT"}""";

        handler.handleTextMessage(session, new TextMessage(msg));

        String response = captureResponse();
        assertTrue(response.contains("userId must be between"));
    }

    @Test
    void userId_tooLarge_returnsError() throws Exception {
        String msg = """
                {"userId":"100001","username":"user123","message":"hi","timestamp":"2026-02-07T12:00:00Z","messageType":"TEXT"}""";

        handler.handleTextMessage(session, new TextMessage(msg));

        String response = captureResponse();
        assertTrue(response.contains("userId must be between"));
    }

    @Test
    void userId_missing_returnsError() throws Exception {
        String msg = """
                {"username":"user123","message":"hi","timestamp":"2026-02-07T12:00:00Z","messageType":"TEXT"}""";

        handler.handleTextMessage(session, new TextMessage(msg));

        String response = captureResponse();
        assertTrue(response.contains("userId"));
    }

    @Test
    void userId_nonNumeric_returnsError() throws Exception {
        String msg = """
                {"userId":"abc","username":"user123","message":"hi","timestamp":"2026-02-07T12:00:00Z","messageType":"TEXT"}""";

        handler.handleTextMessage(session, new TextMessage(msg));

        String response = captureResponse();
        assertTrue(response.contains("userId must be a numeric"));
    }

    // ===== username validation =====

    @Test
    void username_tooShort_returnsError() throws Exception {
        String msg = """
                {"userId":"1","username":"ab","message":"hi","timestamp":"2026-02-07T12:00:00Z","messageType":"TEXT"}""";

        handler.handleTextMessage(session, new TextMessage(msg));

        String response = captureResponse();
        assertTrue(response.contains("username"));
    }

    @Test
    void username_tooLong_returnsError() throws Exception {
        String msg = """
                {"userId":"1","username":"abcdefghijklmnopqrstu","message":"hi","timestamp":"2026-02-07T12:00:00Z","messageType":"TEXT"}""";

        handler.handleTextMessage(session, new TextMessage(msg));

        String response = captureResponse();
        assertTrue(response.contains("username"));
    }

    @Test
    void username_specialChars_returnsError() throws Exception {
        String msg = """
                {"userId":"1","username":"user@123","message":"hi","timestamp":"2026-02-07T12:00:00Z","messageType":"TEXT"}""";

        handler.handleTextMessage(session, new TextMessage(msg));

        String response = captureResponse();
        assertTrue(response.contains("username"));
    }

    // ===== message validation =====

    @Test
    void message_empty_returnsError() throws Exception {
        String msg = """
                {"userId":"1","username":"user123","message":"","timestamp":"2026-02-07T12:00:00Z","messageType":"TEXT"}""";

        handler.handleTextMessage(session, new TextMessage(msg));

        String response = captureResponse();
        assertTrue(response.contains("message must be"));
    }

    @Test
    void message_tooLong_returnsError() throws Exception {
        String longMsg = "x".repeat(501);
        String msg = String.format(
                """
                {"userId":"1","username":"user123","message":"%s","timestamp":"2026-02-07T12:00:00Z","messageType":"TEXT"}""",
                longMsg);

        handler.handleTextMessage(session, new TextMessage(msg));

        String response = captureResponse();
        assertTrue(response.contains("message must be"));
    }

    // ===== timestamp validation =====

    @Test
    void timestamp_missing_returnsError() throws Exception {
        String msg = """
                {"userId":"1","username":"user123","message":"hi","messageType":"TEXT"}""";

        handler.handleTextMessage(session, new TextMessage(msg));

        String response = captureResponse();
        assertTrue(response.contains("timestamp"));
    }

    // ===== messageType validation =====

    @Test
    void messageType_invalid_returnsError() throws Exception {
        String msg = """
                {"userId":"1","username":"user123","message":"hi","timestamp":"2026-02-07T12:00:00Z","messageType":"INVALID"}""";

        handler.handleTextMessage(session, new TextMessage(msg));

        String response = captureResponse();
        assertTrue(response.contains("ERROR"));
    }

    // ===== Boundary tests =====

    @Test
    void userId_boundaryMin_returnsOK() throws Exception {
        String msg = """
                {"userId":"1","username":"abc","message":"x","timestamp":"2026-02-07T12:00:00Z","messageType":"TEXT"}""";

        handler.handleTextMessage(session, new TextMessage(msg));

        String response = captureResponse();
        assertTrue(response.contains("OK"));
    }

    @Test
    void userId_boundaryMax_returnsOK() throws Exception {
        String msg = """
                {"userId":"100000","username":"abc","message":"x","timestamp":"2026-02-07T12:00:00Z","messageType":"TEXT"}""";

        handler.handleTextMessage(session, new TextMessage(msg));

        String response = captureResponse();
        assertTrue(response.contains("OK"));
    }

    @Test
    void username_boundary3chars_returnsOK() throws Exception {
        String msg = """
                {"userId":"1","username":"abc","message":"x","timestamp":"2026-02-07T12:00:00Z","messageType":"TEXT"}""";

        handler.handleTextMessage(session, new TextMessage(msg));

        String response = captureResponse();
        assertTrue(response.contains("OK"));
    }

    @Test
    void username_boundary20chars_returnsOK() throws Exception {
        String msg = """
                {"userId":"1","username":"abcdefghijklmnopqrst","message":"x","timestamp":"2026-02-07T12:00:00Z","messageType":"TEXT"}""";

        handler.handleTextMessage(session, new TextMessage(msg));

        String response = captureResponse();
        assertTrue(response.contains("OK"));
    }

    @Test
    void message_boundary500chars_returnsOK() throws Exception {
        String longMsg = "x".repeat(500);
        String msg = String.format(
                """
                {"userId":"1","username":"abc","message":"%s","timestamp":"2026-02-07T12:00:00Z","messageType":"TEXT"}""",
                longMsg);

        handler.handleTextMessage(session, new TextMessage(msg));

        String response = captureResponse();
        assertTrue(response.contains("OK"));
    }
}
