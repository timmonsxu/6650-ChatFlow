# Assignment 1 - Performance Analysis & Little's Law

## Test Environment

| Component | Spec |
|-----------|------|
| Server | Spring Boot WebSocket, EC2 t3.micro (2 vCPU, 1GB RAM), us-west-2 |
| Client | Pure Java + Java-WebSocket 1.5.4, local machine (Bellevue, WA) |
| Network | Client (Bellevue) → Server (Oregon), ~20ms RTT |
| Messages | 500,000 total, 20 rooms, 90% TEXT / 5% JOIN / 5% LEAVE |

---

## Little's Law Analysis

### Theory

Little's Law: **L = λ × W**

In our context:
- **L** = number of concurrent threads (each thread has 1 in-flight message at a time)
- **λ** = throughput (messages/second)
- **W** = average response time per message (RTT)

Rearranged: **λ = L / W**

### RTT Measurement

Measured from warmup phase (32 threads, 32,000 messages):
- Total time: 20.04 seconds
- Per-thread throughput: 1,597 / 32 ≈ 49.9 msg/s
- **RTT ≈ 1 / 49.9 ≈ 20ms**

This includes: network latency (client → EC2 Oregon) + server processing + network latency (EC2 → client)

### Predictions vs Actual Results

| Threads | Predicted λ (msg/s) | Actual λ (msg/s) | Actual / Predicted | Analysis |
|---------|--------------------|--------------------|-------------------|----------|
| 32 (warmup) | 1,600 | 1,597 | 99.8% | Near-perfect match |
| 128 | 6,400 | 6,291 | 98.3% | Near-perfect match |
| 256 | 12,800 | 11,801 | 92.2% | Slight server overhead |
| 384 | 19,200 | 14,734 | 76.7% | Server saturation begins |
| 512 | 25,600 | 19,823 | 77.4% | Server CPU bottleneck |
| 640 | 32,000 | 20,435 | 63.9% | Diminishing returns |
| 768 | 38,400 | N/A | — | Server refused connections |

### Key Observations

1. **Little's Law is highly accurate at low concurrency.** At 32-128 threads, actual throughput matches prediction within 2%. This confirms our RTT measurement is correct and the system behaves linearly.

2. **Server saturation starts around 384 threads.** The t3.micro (2 vCPU) can handle ~500 concurrent WebSocket connections before Tomcat's thread pool and CPU become bottlenecks. Beyond this point, adding more client threads increases contention without proportional throughput gains.

3. **Optimal configuration: 512 threads.** This achieves ~20K msg/s — the practical throughput ceiling for this server instance. Further scaling would require a larger EC2 instance or horizontal scaling.

4. **The gap between prediction and reality at high concurrency** is caused by:
   - Server-side thread context switching overhead
   - Tomcat thread pool contention
   - CPU saturation on 2 vCPUs handling 500+ concurrent connections
   - Increased GC pressure from many concurrent WebSocket sessions

---

## Client Part 1 - Final EC2 Test Results (512 threads)

```
============================================
  ChatFlow Load Test Client - Part 1
  Server: ws://54.184.109.66:8080
  Total messages: 500000
  Warmup: 32 threads × 1000 msgs
  Main:   512 threads
============================================

Warmup Phase Results:
  Successful messages : 32,000
  Failed messages     : 0
  Total runtime       : 20.04 seconds
  Throughput          : 1,597 msg/s
  Total connections   : 32
  Reconnections       : 0

Main Phase Results:
  Successful messages : 468,000
  Failed messages     : 0
  Total runtime       : 23.61 seconds
  Throughput          : 19,823 msg/s
  Total connections   : 512
  Reconnections       : 0

Overall Summary:
  Total successful    : 500,000
  Total failed        : 0
  Total wall time     : ~44 seconds
  Overall throughput  : ~11,400 msg/s
```

---

## Local Test Results (for comparison)

```
Server: ws://localhost:8080 (no network latency)
Total messages: 500,000
Warmup: 32 threads × 1000 msgs
Main:   128 threads

Warmup Throughput:  40,973 msg/s
Main Throughput:    90,417 msg/s
Overall Throughput: 83,640 msg/s
Total wall time:    5.98 seconds
```

Local throughput is ~15x higher than EC2 because RTT is <1ms vs ~20ms over the network. This demonstrates that **network latency is the dominant factor** in distributed system performance, not server processing time.

---

## Threading Model

```
[MessageGenerator Thread] → BlockingQueue(10,000) → [512 Sender Threads] → WS → EC2

- Producer: Single thread generates 500K messages into a bounded BlockingQueue
- Consumers: Each sender thread holds 1 persistent WebSocket connection
- Pattern: Producer-Consumer with synchronous send-and-wait per thread
- Connection: One connection per thread, reused for all messages (no per-message reconnect)
```

## Optimization History

1. **Connection reuse** (biggest win): Eliminated per-message room reconnection → 6.5x throughput improvement
2. **Thread count tuning**: Benchmarked 128→768 threads, found 512 as the sweet spot for t3.micro
3. **Command-line URL override**: Enabled easy switching between local and EC2 testing
