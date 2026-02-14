# ChatFlow Client - Part 2 (Performance Analysis)

## Prerequisites
- Java 17+
- Maven 3.8+

## Build
```bash
cd client-part2
mvn clean package -DskipTests
```

## Run
```bash
# Against EC2 (default)
java -jar target/client-part2-1.0.0.jar
```

## Output
- Console: basic metrics + latency statistics + throughput over time
- `results/latency.csv`: per-message data (timestamp, messageType, latency, statusCode, roomId)
- `results/throughput.csv`: 10-second bucket throughput data for chart generation

## What's New vs Part 1
- Per-message latency tracking (timestamp before send → timestamp on ACK)
- CSV output for all latency records
- Statistical analysis: mean, median, p95, p99, min, max
- Throughput per room breakdown
- Message type distribution
- Throughput over time in 10-second buckets
