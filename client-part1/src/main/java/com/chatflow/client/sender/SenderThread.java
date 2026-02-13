package com.chatflow.client.sender;

import com.chatflow.client.connection.ConnectionManager;
import com.chatflow.client.connection.ConnectionManager.ChatWebSocketClient;
import com.chatflow.client.metrics.MetricsCollector;
import com.chatflow.client.model.ChatMessage;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class SenderThread implements Runnable {

    private static final int MAX_RETRIES = 5;
    private static final long RESPONSE_TIMEOUT_MS = 5000;

    private final BlockingQueue<ChatMessage> queue;
    private final ConnectionManager connectionManager;
    private final MetricsCollector metrics;
    private final int maxMessages; // -1 means unlimited (until queue empty)
    private final AtomicInteger sharedCounter; // shared counter for warmup phase

    /**
     * @param maxMessages   max messages this thread will send. -1 = until queue is empty.
     * @param sharedCounter if non-null, used as shared counter across threads (for warmup).
     */
    public SenderThread(BlockingQueue<ChatMessage> queue,
                        ConnectionManager connectionManager,
                        MetricsCollector metrics,
                        int maxMessages,
                        AtomicInteger sharedCounter) {
        this.queue = queue;
        this.connectionManager = connectionManager;
        this.metrics = metrics;
        this.maxMessages = maxMessages;
        this.sharedCounter = sharedCounter;
    }

    @Override
    public void run() {
        ChatWebSocketClient client = null;
        int sent = 0;

        try {
            // Connect to a random room (1-20). Since A1 server is echo-only,
            // the room in the URL doesn't need to match the message's roomId.
            // This avoids reconnecting for every message with a different roomId.
            int connRoom = java.util.concurrent.ThreadLocalRandom.current().nextInt(1, 21);
            client = connectionManager.createConnection(connRoom);

            while (shouldContinue(sent)) {
                ChatMessage msg = queue.poll(2, TimeUnit.SECONDS);
                if (msg == null) break; // queue empty and generator done

                sent += sendWithRetry(client, msg);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            System.err.println("[Sender] Error: " + e.getMessage());
        } finally {
            if (client != null && client.isOpen()) {
                client.close();
            }
        }
    }

    private boolean shouldContinue(int localSent) {
        if (maxMessages > 0 && localSent >= maxMessages) return false;
        if (sharedCounter != null && sharedCounter.get() <= 0) return false;
        return true;
    }

    /**
     * Send a message with up to MAX_RETRIES retries using exponential backoff.
     * Returns 1 if successful, 0 if failed after all retries.
     */
    private int sendWithRetry(ChatWebSocketClient client, ChatMessage msg) {
        String json = msg.toJson();

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                if (!client.isOpen()) {
                    client = connectionManager.reconnect(client);
                }

                String response = client.sendAndWait(json, RESPONSE_TIMEOUT_MS);
                if (response != null && response.contains("\"OK\"")) {
                    metrics.recordSuccess();
                    if (sharedCounter != null) sharedCounter.decrementAndGet();
                    return 1;
                }

                // Server returned error — still counts as received, retry
                if (response != null) {
                    System.err.println("[Sender] Server error: " + response);
                }

            } catch (Exception e) {
                // Connection issue, will retry
            }

            // Exponential backoff: 10ms, 20ms, 40ms, 80ms, 160ms
            if (attempt < MAX_RETRIES) {
                try {
                    Thread.sleep((long) (10 * Math.pow(2, attempt)));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        metrics.recordFailure();
        return 0;
    }
}
