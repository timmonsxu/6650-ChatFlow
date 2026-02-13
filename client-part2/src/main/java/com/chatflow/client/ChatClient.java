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
    private static final int WARMUP_TOTAL = WARMUP_THREADS * WARMUP_MESSAGES_PER_THREAD;

    // Main phase config - tuned via benchmarking (512 = server saturation sweet spot on t3.micro)
    private static final int MAIN_THREADS = 512;

    // Output files (../results/ is sibling to client-part2/)
    private static final String CSV_FILE = "../results/latency.csv";
    private static final String THROUGHPUT_CSV = "../results/throughput.csv";

    public static void main(String[] args) throws Exception {
        String serverUrl = args.length > 0 ? args[0] : SERVER_URL;

        System.out.println("============================================");
        System.out.println("  ChatFlow Load Test Client - Part 2");
        System.out.println("  Server: " + serverUrl);
        System.out.println("  Total messages: " + TOTAL_MESSAGES);
        System.out.println("  Warmup: " + WARMUP_THREADS + " threads × " + WARMUP_MESSAGES_PER_THREAD + " msgs");
        System.out.println("  Main:   " + MAIN_THREADS + " threads");
        System.out.println("============================================");

        // Create results directory (sibling to client-part2/)
        new java.io.File("../results").mkdirs();

        BlockingQueue<ChatMessage> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
        // Use a single MetricsCollector for all phases to get combined latency data
        MetricsCollector allMetrics = new MetricsCollector();

        // Start message generator thread
        Thread generatorThread = new Thread(
                new MessageGenerator(queue, TOTAL_MESSAGES), "msg-generator");
        generatorThread.start();

        Thread.sleep(500);

        long testStartTime = System.currentTimeMillis();

        // ============ Warmup Phase ============
        System.out.println("\n>>> Warmup Phase starting...");

        ConnectionManager warmupConnMgr = new ConnectionManager(serverUrl, allMetrics);
        AtomicInteger warmupCounter = new AtomicInteger(WARMUP_TOTAL);

        // Snapshot before warmup
        long warmupSuccessBefore = allMetrics.getSuccessCount();

        ExecutorService warmupExecutor = Executors.newFixedThreadPool(WARMUP_THREADS);
        long warmupStart = System.currentTimeMillis();

        for (int i = 0; i < WARMUP_THREADS; i++) {
            warmupExecutor.submit(new SenderThread(
                    queue, warmupConnMgr, allMetrics,
                    WARMUP_MESSAGES_PER_THREAD, warmupCounter));
        }

        warmupExecutor.shutdown();
        warmupExecutor.awaitTermination(10, TimeUnit.MINUTES);

        long warmupEnd = System.currentTimeMillis();
        long warmupSuccess = allMetrics.getSuccessCount() - warmupSuccessBefore;
        double warmupSec = (warmupEnd - warmupStart) / 1000.0;
        System.out.println();
        System.out.println("========================================");
        System.out.println("  Warmup Phase Results");
        System.out.println("========================================");
        System.out.printf("  Successful messages : %,d%n", warmupSuccess);
        System.out.printf("  Total runtime       : %.2f seconds%n", warmupSec);
        System.out.printf("  Throughput          : %,.0f msg/s%n", warmupSuccess / warmupSec);
        System.out.println("========================================");

        // ============ Main Phase ============
        int remaining = TOTAL_MESSAGES - (int) allMetrics.getSuccessCount();
        System.out.println("\n>>> Main Phase: " + MAIN_THREADS + " threads, "
                + remaining + " remaining messages");

        ConnectionManager mainConnMgr = new ConnectionManager(serverUrl, allMetrics);

        ExecutorService mainExecutor = Executors.newFixedThreadPool(MAIN_THREADS);
        long mainStart = System.currentTimeMillis();

        for (int i = 0; i < MAIN_THREADS; i++) {
            mainExecutor.submit(new SenderThread(
                    queue, mainConnMgr, allMetrics,
                    -1, null));
        }

        mainExecutor.shutdown();
        mainExecutor.awaitTermination(30, TimeUnit.MINUTES);

        long mainEnd = System.currentTimeMillis();

        // Wait for generator
        generatorThread.join();

        // ============ Overall Results ============
        long totalSuccess = allMetrics.getSuccessCount();
        long totalFail = allMetrics.getFailCount();
        double totalTimeSec = (mainEnd - testStartTime) / 1000.0;

        System.out.println();
        System.out.println("========================================");
        System.out.println("  Overall Summary");
        System.out.println("========================================");
        System.out.printf("  Total successful    : %,d%n", totalSuccess);
        System.out.printf("  Total failed        : %,d%n", totalFail);
        System.out.printf("  Total wall time     : %.2f seconds%n", totalTimeSec);
        System.out.printf("  Overall throughput  : %,.0f msg/s%n", totalSuccess / totalTimeSec);
        System.out.println("========================================");

        // ============ Detailed Statistics ============
        allMetrics.printStatistics();
        allMetrics.printThroughputOverTime(testStartTime);

        // ============ Write CSV Files ============
        allMetrics.writeCsv(CSV_FILE);
        allMetrics.writeThroughputCsv(THROUGHPUT_CSV, testStartTime);

        System.out.println("\n>>> Done! Check results/ directory for CSV files.");
    }
}
