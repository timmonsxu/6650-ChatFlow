package com.chatflow.client.connection;

import com.chatflow.client.metrics.MetricsCollector;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ConnectionManager {

    private final String serverBaseUrl;
    private final MetricsCollector metrics;

    public ConnectionManager(String serverBaseUrl, MetricsCollector metrics) {
        this.serverBaseUrl = serverBaseUrl;
        this.metrics = metrics;
    }

    /**
     * Creates a WebSocket connection to a specific room.
     * Blocks until connected or timeout.
     */
    public ChatWebSocketClient createConnection(int roomId) throws Exception {
        String url = serverBaseUrl + "/chat/" + roomId;
        ChatWebSocketClient client = new ChatWebSocketClient(new URI(url));
        client.connectBlocking(10, TimeUnit.SECONDS);

        if (!client.isOpen()) {
            throw new RuntimeException("Failed to connect to " + url);
        }

        metrics.recordConnection();
        return client;
    }

    /**
     * Reconnects an existing client to its original URI.
     * Returns a new client instance since Java-WebSocket doesn't support reconnecting.
     */
    public ChatWebSocketClient reconnect(ChatWebSocketClient oldClient) throws Exception {
        URI uri = oldClient.getURI();
        oldClient.closeBlocking();

        ChatWebSocketClient newClient = new ChatWebSocketClient(uri);
        newClient.connectBlocking(10, TimeUnit.SECONDS);

        if (!newClient.isOpen()) {
            throw new RuntimeException("Failed to reconnect to " + uri);
        }

        metrics.recordReconnection();
        metrics.recordConnection();
        return newClient;
    }

    /**
     * Custom WebSocket client that supports synchronous send-and-wait-for-response.
     */
    public static class ChatWebSocketClient extends WebSocketClient {

        private volatile String lastResponse;
        private volatile CountDownLatch responseLatch;

        public ChatWebSocketClient(URI serverUri) {
            super(serverUri);
        }

        @Override
        public void onOpen(ServerHandshake handshake) {}

        @Override
        public void onMessage(String message) {
            this.lastResponse = message;
            CountDownLatch latch = this.responseLatch;
            if (latch != null) {
                latch.countDown();
            }
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {}

        @Override
        public void onError(Exception ex) {
            System.err.println("[WS Error] " + ex.getMessage());
        }

        /**
         * Send a message and wait for the server's response synchronously.
         * Returns the server response, or null on timeout.
         */
        public String sendAndWait(String message, long timeoutMs) throws InterruptedException {
            this.responseLatch = new CountDownLatch(1);
            this.lastResponse = null;
            send(message);
            boolean received = responseLatch.await(timeoutMs, TimeUnit.MILLISECONDS);
            return received ? lastResponse : null;
        }
    }
}
