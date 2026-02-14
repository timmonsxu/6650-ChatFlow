# ChatFlow - Distributed Chat System

A WebSocket-based chat application with a Spring Boot server and Java clients.

## Project Structure

```
6650-ChatFlow/
├── server/          # Spring Boot WebSocket server (deploy to EC2)
├── client-part1/    # Single-threaded client
├── client-part2/    # Multi-threaded client with load testing
└── results/         # Test results and analysis
```

## Prerequisites

- Java 17
- Maven 3.6+
- AWS EC2 instance (for server deployment)

---

## ⚠️ IMPORTANT: Build Order

> **The server MUST be running on EC2 before building the clients!**
>
> Client tests connect to the remote server during `mvn clean install`. If the server is not running, tests will fail with connection errors.

**Correct order:**
1. Deploy and start the server on EC2
2. Then build client-part1
3. Then build client-part2

---

## 1. Server Setup (EC2)

### 1.1 EC2 Instance Configuration

When creating your EC2 instance, ensure:

| Setting | Value |
|---------|-------|
| AMI | Amazon Linux 2023 |
| Instance type | t3.micro |
| Auto-assign public IP | **Enable** |
| Security Group Inbound Rules | SSH (22) - My IP |
|  | Custom TCP (8080) - 0.0.0.0/0 |

### 1.2 Connect to EC2

```bash
# Windows PowerShell
ssh -i $HOME\.ssh\6650-Timmons-Project.pem ec2-user@54.184.109.66

# Mac/Linux
ssh -i ~/.ssh/6650-Timmons-Project.pem ec2-user@54.184.109.66
```

### 1.3 Install Dependencies on EC2

```bash
// The dependencies already installed
```

### 1.4 Clone and Build Server

```bash
// the Github Repo already been cloned
cd 6650-ChatFlow/server
```

### 1.5 Run Server

```bash
# Run in background (the application is built already)
java -jar target/server-1.0.0.jar

# Verify it's running
ps aux | grep java
```

### 1.6 Verify Server is Running

```bash
# From EC2
curl http://localhost:8080

# From your local machine (browser)
http://<54.184.109.66>:8080
```

---

## 2. Client Part 1 (Single-threaded)

### 2.1 Build

> ⚠️ **Make sure the server is running on EC2 first!**

```bash
cd client-part1
mvn clean install
```

#### ❌ If tests fail with connection errors:

```
Connection refused / Connection timed out
```

This means:
- Server is not running on EC2

**Fix:** Start the server on EC2, then retry `mvn clean install`.

#### ✅ To skip tests temporarily:

```bash
mvn clean install -DskipTests
```

### 2.2 Run

```bash
java -jar target/client-part1-1.0.0.jar
```

---

## 3. Client Part 2 (Multi-threaded Load Testing)

### 3.1 Build

> ⚠️ **Make sure the server is running on EC2 first!**

```bash
cd client-part2
mvn clean install
```

### 3.2 Run

```bash
java -jar target/client-part2-1.0.0.jar
```

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| `Permission denied (publickey)` | Wrong .pem file or wrong username |
| `Connection refused` on SSH | Check EC2 security group port 22 |
| `Connection refused` on 8080 | Server not running or security group missing port 8080 |
| Client tests fail | Server must be running before `mvn clean install` |
| `Whitelabel Error Page` | Server is running, but no mapping for `/` - this is OK |
| Java process stops after closing terminal | Use `nohup ... &` to run in background |
