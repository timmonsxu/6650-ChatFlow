# CS6650 Assignment 1 - Plan

## Deadline: 2/13/2026 5PM PST

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Server | Spring Boot + spring-boot-starter-websocket |
| Client | Pure Java + Java-WebSocket (1.5.4) |
| JSON | Jackson (comes with Spring Boot) / Gson for client |
| Threading | ExecutorService + BlockingQueue |
| Statistics | Apache Commons Math or manual calculation |
| Visualization | JFreeChart or Python matplotlib |
| Deployment | AWS EC2 t2.micro (us-west-2) |

## Repo Structure

```
/server          - Spring Boot WebSocket server
/client-part1    - Basic load testing client (500K messages)
/client-part2    - Client with per-message metrics & stats
/results         - Test results, charts, CSV output
```

## Tasks & Progress

### Phase 1: Server ✅
- [x] Initialize Spring Boot project with WebSocket starter
- [x] Implement WebSocket endpoint `/chat/{roomId}` (accept connection, parse JSON, validate, echo back)
- [x] Implement message validation (userId, username, message, timestamp, messageType)
- [x] Implement `/health` REST endpoint
- [x] Unit tests (22 test cases)
- [x] Local testing with PowerShell script
- [x] Deploy to EC2 (install Java, configure security group for port 8080 + SSH)
- [x] Verify remote WebSocket connectivity

### Phase 2: Client Part 1 ✅
- [x] Set up pure Java project with Java-WebSocket dependency
- [x] Implement MessageGenerator thread (single thread, produces 500K messages into BlockingQueue)
- [x] Implement Sender threads with persistent WebSocket connections
- [x] Warmup phase: 32 threads × 1000 messages each
- [x] Main phase: 128 threads, send remaining 468K messages
- [x] Retry logic: up to 5 retries with exponential backoff
- [x] Output: successful/failed count, wall time, throughput, connection stats
- [x] Unit tests (15 test cases) + integration tests
- [x] Little's Law analysis: RTT ≈ 20ms, predicted ~6400 msg/s, actual 6291 msg/s
- [x] EC2 test: 500K messages, 0 failures, 5294 msg/s overall, 94s total

### Phase 3: Client Part 2 ✅
- [x] Add per-message latency tracking (timestamp before send, timestamp on ack)
- [x] Write CSV: {timestamp, messageType, latency, statusCode, roomId}
- [x] Calculate stats: mean, median, p95, p99, min, max
- [x] Throughput per room
- [x] Message type distribution
- [x] EC2 test: 500K messages, 0 failures

### Phase 4: Visualization ✅
- [x] Throughput over time line chart (10-second buckets) via Python matplotlib
- [x] Generated throughput_chart.png and throughput_chart.pdf

### Phase 5: Documentation & Submission
- [x] Design Document (2 pages max) → results/design-document.md
  - Architecture diagram
  - Major classes and relationships
  - Threading model explanation
  - WebSocket connection management strategy
  - Little's Law calculations vs actual results
- [x] README for each module (server, client-part1, client-part2)
- [ ] Screenshots: Part 1 output, Part 2 output, EC2 console
- [ ] Compile PDF and submit to Canvas

## Threading Model (Client)

```
[MessageGenerator Thread] ---> BlockingQueue ---> [Sender Thread 1] ---> WS Connection 1
                                             ---> [Sender Thread 2] ---> WS Connection 2
                                             ---> ...
                                             ---> [Sender Thread N] ---> WS Connection N
```

- Producer-Consumer pattern
- Each sender thread maintains a persistent WebSocket connection
- MessageGenerator fills queue fast enough so senders never starve

## Notes

- Server uses Spring Boot from A1 onward for easy evolution into A2-A4
- Client stays pure Java, no framework needed
- Keep connection pooling in mind — reuse connections, don't open/close per message
