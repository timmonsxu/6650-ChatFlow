package com.chatflow.client.sender;

import com.chatflow.client.connection.ConnectionManager;
import com.chatflow.client.connection.ConnectionManager.ChatWebSocketClient;
import com.chatflow.client.metrics.MetricsCollector;
import com.chatflow.client.model.ChatMessage;
import com.chatflow.client.model.LatencyRecord;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class SenderThread implements Runnable {

    private static final int MAX_RETRIES = 5;
    private static final long RESPONSE_TIMEOUT_MS = 5000;

    private final BlockingQueue<ChatMessage> queue;
    private final ConnectionManager connectionManager;
    private final MetricsCollector metrics;
    private final int maxMessages;
    private final AtomicInteger sharedCounter;

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
            int connRoom = java.util.concurrent.ThreadLocalRandom.current().nextInt(1, 21);
            client = connectionManager.createConnection(connRoom);

            while (shouldContinue(sent)) {
                ChatMessage msg = queue.poll(2, TimeUnit.SECONDS);
                if (msg == null) break;

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

    private int sendWithRetry(ChatWebSocketClient client, ChatMessage msg) {
        String json = msg.toJson();

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                if (!client.isOpen()) {
                    client = connectionManager.reconnect(client);
                }

                // Record timestamp before send
                long sendTime = System.currentTimeMillis();

                String response = client.sendAndWait(json, RESPONSE_TIMEOUT_MS);

                // Record timestamp after ACK
                long ackTime = System.currentTimeMillis();
                long latency = ackTime - sendTime;

                if (response != null && response.contains("\"OK\"")) {
                    metrics.recordSuccess();
                    metrics.recordLatency(new LatencyRecord(
                            sendTime, msg.getMessageType(), latency, "OK", msg.getRoomId()));
                    if (sharedCounter != null) sharedCounter.decrementAndGet();
                    return 1;
                }

                if (response != null) {
                    System.err.println("[Sender] Server error: " + response);
                }

            } catch (Exception e) {
                // Connection issue, will retry
            }

            if (attempt < MAX_RETRIES) {
                try {
                    Thread.sleep((long) (10 * Math.pow(2, attempt)));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        // Record failed attempt with latency = -1
        metrics.recordFailure();
        metrics.recordLatency(new LatencyRecord(
                System.currentTimeMillis(), msg.getMessageType(), -1, "FAIL", msg.getRoomId()));
        return 0;
    }
}
