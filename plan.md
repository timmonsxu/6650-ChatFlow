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

### Phase 1: Server
- [ ] Initialize Spring Boot project with WebSocket starter
- [ ] Implement WebSocket endpoint `/chat/{roomId}` (accept connection, parse JSON, validate, echo back)
- [ ] Implement message validation (userId, username, message, timestamp, messageType)
- [ ] Implement `/health` REST endpoint
- [ ] Local testing with wscat
- [ ] Deploy to EC2 (install Java, configure security group for port 8080 + SSH)
- [ ] Verify remote WebSocket connectivity

### Phase 2: Client Part 1
- [ ] Set up pure Java project with Java-WebSocket dependency
- [ ] Implement MessageGenerator thread (single thread, produces 500K messages into BlockingQueue)
  - userId: random 1-100000
  - username: "user{userId}"
  - message: random from 50 predefined messages
  - roomId: random 1-20
  - messageType: 90% TEXT, 5% JOIN, 5% LEAVE
  - timestamp: current time
- [ ] Implement Sender threads with WebSocket connections
- [ ] Warmup phase: 32 threads × 1000 messages each
- [ ] Main phase: configurable thread count, send remaining messages
- [ ] Retry logic: up to 5 retries with exponential backoff
- [ ] Output: successful/failed count, wall time, throughput, connection stats
- [ ] Little's Law analysis: measure single-message RTT, predict max throughput

### Phase 3: Client Part 2
- [ ] Add per-message latency tracking (timestamp before send, timestamp on ack)
- [ ] Write CSV: {timestamp, messageType, latency, statusCode, roomId}
- [ ] Calculate stats: mean, median, p95, p99, min, max
- [ ] Throughput per room
- [ ] Message type distribution

### Phase 4: Visualization
- [ ] Throughput over time line chart (10-second buckets)

### Phase 5: Documentation & Submission
- [ ] Design Document (2 pages max)
  - Architecture diagram
  - Major classes and relationships
  - Threading model explanation
  - WebSocket connection management strategy
  - Little's Law calculations vs actual results
- [ ] README for each module
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
