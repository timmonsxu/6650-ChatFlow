package com.chatflow.client;

import com.chatflow.client.connection.ConnectionManager;
import com.chatflow.client.generator.MessageGenerator;
import com.chatflow.client.metrics.MetricsCollector;
import com.chatflow.client.model.ChatMessage;
import com.chatflow.client.sender.SenderThread;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ChatClient {

    // ============ Configuration ============
    private static final String SERVER_URL = "ws://54.184.109.66:8080";
    private static final int TOTAL_MESSAGES = 500_000;
    private static final int QUEUE_CAPACITY = 10_000;

    // Warmup config (assignment requirement: 32 threads × 1000 messages)
    private static final int WARMUP_THREADS = 32;
    private static final int WARMUP_MESSAGES_PER_THREAD = 1000;
    private static final int WARMUP_TOTAL = WARMUP_THREADS * WARMUP_MESSAGES_PER_THREAD; // 32,000

    // Main phase config - tuned via benchmarking (512 = server saturation sweet spot on t3.micro)
    private static final int MAIN_THREADS = 512;

    public static void main(String[] args) throws Exception {
        // Allow overriding server URL via command line
        String serverUrl = args.length > 0 ? args[0] : SERVER_URL;

        System.out.println("============================================");
        System.out.println("  ChatFlow Load Test Client - Part 1");
        System.out.println("  Server: " + serverUrl);
        System.out.println("  Total messages: " + TOTAL_MESSAGES);
        System.out.println("  Warmup: " + WARMUP_THREADS + " threads × " + WARMUP_MESSAGES_PER_THREAD + " msgs");
        System.out.println("  Main:   " + MAIN_THREADS + " threads");
        System.out.println("============================================");

        BlockingQueue<ChatMessage> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
        MetricsCollector warmupMetrics = new MetricsCollector();
        MetricsCollector mainMetrics = new MetricsCollector();

        // Start message generator thread
        Thread generatorThread = new Thread(
                new MessageGenerator(queue, TOTAL_MESSAGES), "msg-generator");
        generatorThread.start();

        // Give generator a head start to fill queue
        Thread.sleep(500);

        // ============ Warmup Phase ============
        System.out.println("\n>>> Warmup Phase starting...");

        ConnectionManager warmupConnMgr = new ConnectionManager(serverUrl, warmupMetrics);
        AtomicInteger warmupCounter = new AtomicInteger(WARMUP_TOTAL);

        ExecutorService warmupExecutor = Executors.newFixedThreadPool(WARMUP_THREADS);
        long warmupStart = System.currentTimeMillis();

        for (int i = 0; i < WARMUP_THREADS; i++) {
            warmupExecutor.submit(new SenderThread(
                    queue, warmupConnMgr, warmupMetrics,
                    WARMUP_MESSAGES_PER_THREAD, warmupCounter));
        }

        warmupExecutor.shutdown();
        warmupExecutor.awaitTermination(10, TimeUnit.MINUTES);

        long warmupEnd = System.currentTimeMillis();
        warmupMetrics.printReport("Warmup Phase", warmupStart, warmupEnd);

        // ============ Main Phase ============
        int remaining = TOTAL_MESSAGES - (int) warmupMetrics.getSuccessCount();
        System.out.println("\n>>> Main Phase: " + MAIN_THREADS + " threads, "
                + remaining + " remaining messages");

        ConnectionManager mainConnMgr = new ConnectionManager(serverUrl, mainMetrics);

        ExecutorService mainExecutor = Executors.newFixedThreadPool(MAIN_THREADS);
        long mainStart = System.currentTimeMillis();

        for (int i = 0; i < MAIN_THREADS; i++) {
            mainExecutor.submit(new SenderThread(
                    queue, mainConnMgr, mainMetrics,
                    -1, null)); // -1 = send until queue empty
        }

        mainExecutor.shutdown();
        mainExecutor.awaitTermination(30, TimeUnit.MINUTES);

        long mainEnd = System.currentTimeMillis();
        mainMetrics.printReport("Main Phase", mainStart, mainEnd);

        // Wait for generator to finish
        generatorThread.join();

        // ============ Overall Summary ============
        long totalSuccess = warmupMetrics.getSuccessCount() + mainMetrics.getSuccessCount();
        long totalFail = warmupMetrics.getFailCount() + mainMetrics.getFailCount();
        double totalTimeSec = (mainEnd - warmupStart) / 1000.0;

        System.out.println();
        System.out.println("========================================");
        System.out.println("  Overall Summary");
        System.out.println("========================================");
        System.out.printf("  Total successful    : %,d%n", totalSuccess);
        System.out.printf("  Total failed        : %,d%n", totalFail);
        System.out.printf("  Total wall time     : %.2f seconds%n", totalTimeSec);
        System.out.printf("  Overall throughput  : %,.0f msg/s%n", totalSuccess / totalTimeSec);
        System.out.println("========================================");
    }
}
