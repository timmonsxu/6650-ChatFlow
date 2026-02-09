# WebSocket Test Script for ChatFlow Server
# Usage: .\test-ws.ps1

$uri = "ws://localhost:8080/chat/1"

$ws = New-Object System.Net.WebSockets.ClientWebSocket
$ct = New-Object System.Threading.CancellationToken

Write-Host "Connecting to $uri ..." -ForegroundColor Cyan
$ws.ConnectAsync($uri, $ct).Wait()
Write-Host "Connected!" -ForegroundColor Green

# --- Test 1: Valid message ---
Write-Host "`n--- Test 1: Valid message ---" -ForegroundColor Yellow
$validMsg = '{"userId":"123","username":"user123","message":"hello world","timestamp":"2026-02-07T12:00:00Z","messageType":"TEXT"}'
$bytes = [System.Text.Encoding]::UTF8.GetBytes($validMsg)
$segment = New-Object System.ArraySegment[byte] -ArgumentList @(,$bytes)
$ws.SendAsync($segment, [System.Net.WebSockets.WebSocketMessageType]::Text, $true, $ct).Wait()

$buf = New-Object byte[] 4096
$result = $ws.ReceiveAsync((New-Object System.ArraySegment[byte] -ArgumentList @(,$buf)), $ct).Result
$response = [System.Text.Encoding]::UTF8.GetString($buf, 0, $result.Count)
Write-Host "Response: $response" -ForegroundColor Green

# --- Test 2: Invalid userId ---
Write-Host "`n--- Test 2: Invalid userId (0) ---" -ForegroundColor Yellow
$invalidMsg = '{"userId":"0","username":"user123","message":"hello","timestamp":"2026-02-07T12:00:00Z","messageType":"TEXT"}'
$bytes = [System.Text.Encoding]::UTF8.GetBytes($invalidMsg)
$segment = New-Object System.ArraySegment[byte] -ArgumentList @(,$bytes)
$ws.SendAsync($segment, [System.Net.WebSockets.WebSocketMessageType]::Text, $true, $ct).Wait()

$result = $ws.ReceiveAsync((New-Object System.ArraySegment[byte] -ArgumentList @(,$buf)), $ct).Result
$response = [System.Text.Encoding]::UTF8.GetString($buf, 0, $result.Count)
Write-Host "Response: $response" -ForegroundColor Red

# --- Test 3: Invalid username (too short) ---
Write-Host "`n--- Test 3: Invalid username ('ab') ---" -ForegroundColor Yellow
$invalidMsg = '{"userId":"1","username":"ab","message":"hello","timestamp":"2026-02-07T12:00:00Z","messageType":"TEXT"}'
$bytes = [System.Text.Encoding]::UTF8.GetBytes($invalidMsg)
$segment = New-Object System.ArraySegment[byte] -ArgumentList @(,$bytes)
$ws.SendAsync($segment, [System.Net.WebSockets.WebSocketMessageType]::Text, $true, $ct).Wait()

$result = $ws.ReceiveAsync((New-Object System.ArraySegment[byte] -ArgumentList @(,$buf)), $ct).Result
$response = [System.Text.Encoding]::UTF8.GetString($buf, 0, $result.Count)
Write-Host "Response: $response" -ForegroundColor Red

# --- Test 4: Invalid JSON ---
Write-Host "`n--- Test 4: Invalid JSON ---" -ForegroundColor Yellow
$badJson = 'not json at all'
$bytes = [System.Text.Encoding]::UTF8.GetBytes($badJson)
$segment = New-Object System.ArraySegment[byte] -ArgumentList @(,$bytes)
$ws.SendAsync($segment, [System.Net.WebSockets.WebSocketMessageType]::Text, $true, $ct).Wait()

$result = $ws.ReceiveAsync((New-Object System.ArraySegment[byte] -ArgumentList @(,$buf)), $ct).Result
$response = [System.Text.Encoding]::UTF8.GetString($buf, 0, $result.Count)
Write-Host "Response: $response" -ForegroundColor Red

# Close
Write-Host "`n--- Closing connection ---" -ForegroundColor Cyan
$ws.CloseAsync([System.Net.WebSockets.WebSocketCloseStatus]::NormalClosure, "done", $ct).Wait()
Write-Host "Done!" -ForegroundColor Green
