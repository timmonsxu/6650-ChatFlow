# ChatFlow Server

## Prerequisites
- Java 17+
- Maven 3.8+

## Build
```bash
cd server
mvn clean package -DskipTests
```

## Run Locally
```bash
java -jar ./target/server-1.0.0.jar
```
Server starts on port 8080.

## Test

### Health Check
```bash
curl http://localhost:8080/health
```

### WebSocket (using wscat)
```bash
npm install -g wscat
wscat -c ws://localhost:8080/chat/1
```

Then send a message:
```json
{"userId":"123","username":"user123","message":"hello world","timestamp":"2026-02-07T12:00:00Z","messageType":"TEXT"}
```

## Deploy to EC2

1. Launch t2.micro (Amazon Linux 2023, us-west-2)
2. Security Group: open port 8080 (TCP) + 22 (SSH)
3. SSH in and install Java:
   
   
   ```bash
   ssh -i $HOME\.ssh\6650-Timmons-Project.pem ec2-user@54.184.109.66
   sudo yum install java-17-amazon-corretto -y
   ```
4. Upload jar:
   ```bash
   scp -i your-key.pem target/server-1.0.0.jar ec2-user@<EC2-IP>:~/
   ```
5. Run:
   ```bash
   nohup java -jar server-1.0.0.jar &
   ```
6. Verify:
   ```bash
   curl http://<EC2-IP>:8080/health
   wscat -c ws://<EC2-IP>:8080/chat/1
   ```
