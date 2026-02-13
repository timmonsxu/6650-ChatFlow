# Measure WebSocket round-trip time to EC2
# Usage: .\measure-rtt.ps1

param(
    [string]$serverHost = "54.184.109.66",
    [int]$port = 8080,
    [int]$count = 20
)

$uri = "ws://${serverHost}:${port}/chat/1"
$ws = New-Object System.Net.WebSockets.ClientWebSocket
$ct = New-Object System.Threading.CancellationToken

Write-Host "Connecting to $uri ..." -ForegroundColor Cyan
$ws.ConnectAsync($uri, $ct).Wait()
Write-Host "Connected! Measuring RTT with $count messages...`n" -ForegroundColor Green

$msg = '{"userId":"1","username":"testuser","message":"ping","timestamp":"2026-02-07T12:00:00Z","messageType":"TEXT"}'
$bytes = [System.Text.Encoding]::UTF8.GetBytes($msg)
$segment = New-Object System.ArraySegment[byte] -ArgumentList @(,$bytes)
$buf = New-Object byte[] 4096

$rtts = @()

for ($i = 1; $i -le $count; $i++) {
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $ws.SendAsync($segment, [System.Net.WebSockets.WebSocketMessageType]::Text, $true, $ct).Wait()
    $result = $ws.ReceiveAsync((New-Object System.ArraySegment[byte] -ArgumentList @(,$buf)), $ct).Result
    $sw.Stop()

    $rtt = $sw.Elapsed.TotalMilliseconds
    $rtts += $rtt
    Write-Host ("  Message {0,2}: {1,8:F2} ms" -f $i, $rtt)
}

$ws.CloseAsync([System.Net.WebSockets.WebSocketCloseStatus]::NormalClosure, "done", $ct).Wait()

# Statistics
$sorted = $rtts | Sort-Object
$mean = ($rtts | Measure-Object -Average).Average
$min = $sorted[0]
$max = $sorted[-1]
$median = if ($count % 2 -eq 0) { ($sorted[$count/2 - 1] + $sorted[$count/2]) / 2 } else { $sorted[($count-1)/2] }
$p95idx = [math]::Ceiling($count * 0.95) - 1
$p99idx = [math]::Ceiling($count * 0.99) - 1
$p95 = $sorted[$p95idx]
$p99 = $sorted[$p99idx]

Write-Host "`n===== RTT Statistics =====" -ForegroundColor Yellow
Write-Host ("  Mean:   {0:F2} ms" -f $mean)
Write-Host ("  Median: {0:F2} ms" -f $median)
Write-Host ("  Min:    {0:F2} ms" -f $min)
Write-Host ("  Max:    {0:F2} ms" -f $max)
Write-Host ("  P95:    {0:F2} ms" -f $p95)
Write-Host ("  P99:    {0:F2} ms" -f $p99)

Write-Host "`n===== Little's Law Prediction =====" -ForegroundColor Yellow
$rttSec = $mean / 1000.0
foreach ($threads in @(32, 64, 128, 256, 512)) {
    $throughput = [math]::Round($threads / $rttSec)
    Write-Host ("  {0,3} threads => ~{1,6} msg/s" -f $threads, $throughput)
}

$target = 500000
$bestThreads = 256
$predictedThroughput = [math]::Round($bestThreads / $rttSec)
$predictedTime = [math]::Round($target / $predictedThroughput)
Write-Host ("`n  Target: {0} messages" -f $target)
Write-Host ("  With {0} threads: ~{1} msg/s => ~{2} seconds" -f $bestThreads, $predictedThroughput, $predictedTime)
