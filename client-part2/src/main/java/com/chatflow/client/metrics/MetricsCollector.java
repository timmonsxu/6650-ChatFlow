package com.chatflow.client.metrics;

import com.chatflow.client.model.LatencyRecord;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class MetricsCollector {

    private final AtomicLong successCount = new AtomicLong(0);
    private final AtomicLong failCount = new AtomicLong(0);
    private final AtomicInteger totalConnections = new AtomicInteger(0);
    private final AtomicInteger reconnections = new AtomicInteger(0);

    // Per-message latency records (lock-free, thread-safe)
    private final ConcurrentLinkedQueue<LatencyRecord> latencyRecords = new ConcurrentLinkedQueue<>();

    public void recordSuccess() { successCount.incrementAndGet(); }
    public void recordFailure() { failCount.incrementAndGet(); }
    public void recordConnection() { totalConnections.incrementAndGet(); }
    public void recordReconnection() { reconnections.incrementAndGet(); }
    public void recordLatency(LatencyRecord record) { latencyRecords.add(record); }

    public long getSuccessCount() { return successCount.get(); }
    public long getFailCount() { return failCount.get(); }
    public List<LatencyRecord> getLatencyRecords() { return new ArrayList<>(latencyRecords); }

    public void printReport(String phase, long startTime, long endTime) {
        long success = successCount.get();
        long fail = failCount.get();
        double durationSec = (endTime - startTime) / 1000.0;
        double throughput = success / durationSec;

        System.out.println();
        System.out.println("========================================");
        System.out.println("  " + phase + " Results");
        System.out.println("========================================");
        System.out.printf("  Successful messages : %,d%n", success);
        System.out.printf("  Failed messages     : %,d%n", fail);
        System.out.printf("  Total runtime       : %.2f seconds%n", durationSec);
        System.out.printf("  Throughput          : %,.0f msg/s%n", throughput);
        System.out.printf("  Total connections   : %d%n", totalConnections.get());
        System.out.printf("  Reconnections       : %d%n", reconnections.get());
        System.out.println("========================================");
    }

    /**
     * Write all latency records to CSV file.
     */
    public void writeCsv(String filename) throws IOException {
        List<LatencyRecord> records = getLatencyRecords();
        try (PrintWriter pw = new PrintWriter(new FileWriter(filename))) {
            pw.println("timestamp,messageType,latency,statusCode,roomId");
            for (LatencyRecord r : records) {
                pw.println(r.toCsvLine());
            }
        }
        System.out.println("  CSV written: " + filename + " (" + records.size() + " records)");
    }

    /**
     * Print detailed statistical analysis of latency data.
     */
    public void printStatistics() {
        List<LatencyRecord> records = getLatencyRecords();
        if (records.isEmpty()) {
            System.out.println("  No latency records to analyze.");
            return;
        }

        // Extract latencies and sort
        long[] latencies = records.stream().mapToLong(LatencyRecord::getLatencyMs).toArray();
        Arrays.sort(latencies);

        int n = latencies.length;
        long sum = 0;
        for (long l : latencies) sum += l;

        double mean = (double) sum / n;
        long median = latencies[n / 2];
        long p95 = latencies[(int) (n * 0.95)];
        long p99 = latencies[(int) (n * 0.99)];
        long min = latencies[0];
        long max = latencies[n - 1];

        System.out.println();
        System.out.println("========================================");
        System.out.println("  Latency Statistics");
        System.out.println("========================================");
        System.out.printf("  Total records : %,d%n", n);
        System.out.printf("  Mean          : %.2f ms%n", mean);
        System.out.printf("  Median        : %d ms%n", median);
        System.out.printf("  P95           : %d ms%n", p95);
        System.out.printf("  P99           : %d ms%n", p99);
        System.out.printf("  Min           : %d ms%n", min);
        System.out.printf("  Max           : %d ms%n", max);
        System.out.println("========================================");

        // Throughput per room
        Map<Integer, Integer> roomCounts = new HashMap<>();
        Map<String, Integer> typeCounts = new HashMap<>();
        for (LatencyRecord r : records) {
            roomCounts.merge(r.getRoomId(), 1, Integer::sum);
            typeCounts.merge(r.getMessageType(), 1, Integer::sum);
        }

        System.out.println();
        System.out.println("========================================");
        System.out.println("  Throughput Per Room");
        System.out.println("========================================");
        roomCounts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> System.out.printf("  Room %2d : %,d messages%n", e.getKey(), e.getValue()));
        System.out.println("========================================");

        System.out.println();
        System.out.println("========================================");
        System.out.println("  Message Type Distribution");
        System.out.println("========================================");
        typeCounts.forEach((type, count) ->
                System.out.printf("  %-6s : %,d (%.1f%%)%n", type, count, 100.0 * count / n));
        System.out.println("========================================");
    }

    /**
     * Print throughput over time in 10-second buckets (for visualization data).
     */
    public void printThroughputOverTime(long testStartTime) {
        List<LatencyRecord> records = getLatencyRecords();
        if (records.isEmpty()) return;

        // Group by 10-second bucket
        Map<Long, Integer> buckets = new TreeMap<>();
        for (LatencyRecord r : records) {
            long bucketKey = ((r.getSendTimestamp() - testStartTime) / 10_000) * 10; // seconds
            buckets.merge(bucketKey, 1, Integer::sum);
        }

        System.out.println();
        System.out.println("========================================");
        System.out.println("  Throughput Over Time (10s buckets)");
        System.out.println("========================================");
        System.out.println("  Time(s)  | Messages | Throughput(msg/s)");
        System.out.println("  ---------+----------+-----------------");
        buckets.forEach((timeSec, count) ->
                System.out.printf("  %4d-%4d | %,8d | %,.0f msg/s%n",
                        timeSec, timeSec + 10, count, count / 10.0));
        System.out.println("========================================");
    }

    /**
     * Write throughput-over-time data to CSV for chart generation.
     */
    public void writeThroughputCsv(String filename, long testStartTime) throws IOException {
        List<LatencyRecord> records = getLatencyRecords();
        Map<Long, Integer> buckets = new TreeMap<>();
        for (LatencyRecord r : records) {
            long bucketKey = ((r.getSendTimestamp() - testStartTime) / 10_000) * 10;
            buckets.merge(bucketKey, 1, Integer::sum);
        }

        try (PrintWriter pw = new PrintWriter(new FileWriter(filename))) {
            pw.println("time_seconds,messages,throughput_per_second");
            buckets.forEach((timeSec, count) ->
                    pw.println(timeSec + "," + count + "," + String.format("%.1f", count / 10.0)));
        }
        System.out.println("  Throughput CSV written: " + filename);
    }

    public void reset() {
        successCount.set(0);
        failCount.set(0);
        totalConnections.set(0);
        reconnections.set(0);
        latencyRecords.clear();
    }
}
