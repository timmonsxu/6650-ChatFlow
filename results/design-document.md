# CS6650 Assignment 1 - Design Document

**Author:** Timmons Xu | **GitHub:** https://github.com/timmonsxu/6650-ChatFlow | **Date:** Feb 2026

---

## 1. Architecture Diagram

```
                        Client (Local Machine, Bellevue WA)
┌──────────────────────────────────────────────────────────────────────┐
│                                                                      │
│  [MessageGenerator Thread]                                           │
│        │                                                             │
│        │ queue.put()                                                 │
│        ▼                                                             │
│  ┌─────────────────────┐                                             │
│  │  BlockingQueue      │                                             │
│  │  (capacity: 10,000) │                                             │
│  └──────┬──────────────┘                                             │
│         │ queue.poll()                                               │
│         ▼                                                            │
│  ┌─────────────────────────────────────────────┐                     │
│  │            ExecutorService                  │                     │
│  │  ┌──────────┐ ┌──────────┐   ┌──────────┐   │                     │
│  │  │ Sender 1 │ │ Sender 2 │...│ Sender N │   │                     │
│  │  │ WS Conn  │ │ WS Conn  │   │ WS Conn  │   │                     │
│  │  └────┬─────┘ └────┬─────┘   └────┬─────┘   │                     │
│  └───────┼─────────────┼──────────────┼────────┘                     │
│          │             │              │                              │
│  ┌───────┴─────────────┴──────────────┴──────────┐                   │
│  │              MetricsCollector                 │                   │
│  │  (AtomicLong counters + LatencyRecords)       │                   │
│  └───────────────────────────────────────────────┘                   │
└──────────┬─────────────┬──────────────┬──────────────────────────────┘
           │ WebSocket   │              │
           ▼             ▼              ▼
┌──────────────────────────────────────────────────────────────────────┐
│                 Server (EC2 t3.micro, us-west-2)                     │
│                                                                      │
│  Spring Boot + WebSocket                                             │
│  ┌─────────────────────────────┐   ┌──────────────────┐              │
│  │  ChatWebSocketHandler       │   │ HealthController │              │
│  │  /chat/{roomId}             │   │ GET /health      │              │
│  │  - Parse JSON               │   └──────────────────┘              │
│  │  - Validate fields          │                                     │
│  │  - Echo + serverTimestamp   │                                     │
│  └─────────────────────────────┘                                     │
└──────────────────────────────────────────────────────────────────────┘
```

## 2. Major Classes and Relationships

### Server (Spring Boot)

| Class                          | Responsibility                                                                  |
| ------------------------------ | ------------------------------------------------------------------------------- |
| `ChatWebSocketHandler`         | Core logic: parse JSON → validate 5 fields → echo with serverTimestamp + status |
| `WebSocketConfig`              | Register `/chat/{roomId}` handler, configure buffer sizes and idle timeout      |
| `HealthController`             | `GET /health` → returns `{"status":"UP","timestamp":"..."}`                     |
| `ChatMessage` / `ChatResponse` | POJOs for deserialization and response construction                             |

### Client (Pure Java)

| Class               | Responsibility                                                                                                            |
| ------------------- | ------------------------------------------------------------------------------------------------------------------------- |
| `ChatClient`        | Entry point. Orchestrates: generator start → warmup phase → main phase → metrics output                                   |
| `MessageGenerator`  | Single Runnable. Produces 500K messages into BlockingQueue with random data (90/5/5 distribution)                         |
| `SenderThread`      | Runnable consumer. Holds one persistent WebSocket connection. Synchronous send-and-wait. Retry with exponential backoff   |
| `ConnectionManager` | Creates/reconnects WebSocket connections. Inner class `ChatWebSocketClient` implements `sendAndWait()` via CountDownLatch |
| `MetricsCollector`  | Thread-safe counters (AtomicLong). Part 2 adds ConcurrentLinkedQueue of LatencyRecords, CSV output, statistical analysis  |

## 3. Threading Model

```
Phase 1 - Warmup:    [Generator] → Queue → [32 SenderThreads × 1000 msgs each]
Phase 2 - Main:      [Generator] → Queue → [512 SenderThreads until queue empty]
```

- **Producer-Consumer pattern** with `LinkedBlockingQueue` (capacity 10,000)
- Generator runs ahead to ensure senders never starve
- Each sender owns one WebSocket connection (no sharing, no locking needed)
- Warmup threads use `AtomicInteger` shared counter; main threads poll until queue empty

## 4. WebSocket Connection Management

- **One connection per sender thread**, established at thread start, reused for all messages
- Connections are randomly assigned to rooms 1-20 (server is echo-only in A1, room doesn't affect behavior)
- **Reconnection**: If `client.isOpen()` returns false, `ConnectionManager.reconnect()` creates a new `ChatWebSocketClient` to the same URI
- **Synchronous send-and-wait**: `ChatWebSocketClient.sendAndWait()` uses a `CountDownLatch` — send message, block until `onMessage()` callback counts down, return response
- **Retry**: Up to 5 retries with exponential backoff (10ms, 20ms, 40ms, 80ms, 160ms)

## 5. Little's Law Analysis

**Formula:** λ = L / W (throughput = concurrency / response time)

**Measured RTT:** ~20ms (Bellevue → Oregon EC2 round trip including server processing)

### Predictions vs Actual Results

| Threads     | Predicted λ (msg/s) | Actual λ (msg/s) | Actual / Predicted | Analysis                                              |
| ----------- | ------------------- | ---------------- | ------------------ | ----------------------------------------------------- |
| 32 (warmup) | 1,600               | 1,597            | 99.8%              | Near-perfect match                                    |
| 128         | 6,400               | 6,291            | 98.3%              | Near-perfect match                                    |
| 256         | 12,800              | 11,801           | 92.2%              | Slight server overhead                                |
| 384         | 19,200              | 14,734           | 76.7%              | Server saturation begins                              |
| 512         | 25,600              | 19,823           | 77.4%              | Server CPU bottleneck                                 |
| 640         | 32,000              | 20,435           | 63.9%              | Diminishing returns, throughput plateau               |
| 768         | 38,400              | —                | —                  | Server refused connections (connection limit reached) |

### Key Observations

1. **Little's Law is highly accurate at low concurrency.** At 32-128 threads, actual throughput matches prediction within 2%. This validates our RTT measurement (~20ms) and confirms linear scaling behavior.

2. **Server saturation starts around 384 threads.** The t3.micro (2 vCPU, 1GB RAM) begins showing diminishing returns as Tomcat's thread pool and CPU become bottlenecks.

3. **Throughput plateau at 512-640 threads.** Both configurations achieve ~20K msg/s, indicating we've hit the server's practical ceiling. Adding more threads (640 vs 512) yields only 3% improvement — not worth the extra resource consumption.

4. **Hard limit at 768 threads.** Server refused connections, likely due to:
   - Tomcat's default max connections limit
   - OS file descriptor limits on t3.micro
   - Memory pressure from too many concurrent WebSocket sessions

5. **Optimal configuration: 512 threads** — achieves near-maximum throughput (~20K msg/s) without hitting connection limits or excessive resource usage.

### Why Little's Law Deviates at High Concurrency

The gap between predicted and actual throughput at 384+ threads is caused by:

- Server-side thread context switching overhead
- Tomcat thread pool contention
- CPU saturation on 2 vCPUs handling 500+ concurrent connections
- Increased GC pressure from many concurrent WebSocket sessions
- Network buffer contention at high message rates

**Final results (512 threads):** 500,000 messages, 0 failures, peak throughput 19,823 msg/s, ~44s total wall time.
