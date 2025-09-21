<#
.SYNOPSIS
  Testa conectividade com um endpoint Redis Upstash (ou compatível) incluindo DNS, TCP/TLS, AUTH e PING.

.DESCRIPTION
  Usa a URL REDIS_URL (ou parâmetro) e executa:
    1. Parsing / mascaramento de credenciais
    2. Resolução DNS
    3. Teste TCP simples
    4. Teste TLS (handshake) se rediss:// ou host contém upstash
    5. AUTH + PING via redis-cli (se disponível) ou fallback simples

.PARAMETER RedisUrl
  (Opcional) URL completa redis:// ou rediss://. Caso omitida tenta usar $env:REDIS_URL.

.EXAMPLE
  ./test-upstash-redis.ps1
  (usa REDIS_URL do ambiente)

.EXAMPLE
  ./test-upstash-redis.ps1 "rediss://default:senha@host.upstash.io:6379"

#>
[CmdletBinding()]
param(
  [string]$RedisUrl
)

function Write-Section($title) {
  Write-Host "`n=== $title ===" -ForegroundColor Cyan
}

if (-not $RedisUrl) { $RedisUrl = $env:REDIS_URL }
if (-not $RedisUrl) {
  Write-Host "Nenhuma URL Redis fornecida (param ou REDIS_URL)." -ForegroundColor Red
  exit 1
}

# --- Parse URL ---
try {
  $uri = [System.Uri]::new($RedisUrl)
} catch {
  Write-Host "URL inválida: $_" -ForegroundColor Red; exit 1
}

$scheme = $uri.Scheme
$redisHost   = $uri.Host
$port   = if ($uri.Port -gt 0) { $uri.Port } else { 6379 }
$userInfo = $uri.UserInfo
$user = $null; $password = $null
if ($userInfo) {
  if ($userInfo.Contains(':')) { $split = $userInfo.Split(':',2); $user = $split[0]; $password = $split[1] } else { $password = $userInfo }
}

$maskedUserInfo = if ($userInfo) { if ($user) { "${user}:***" } else { "***" } } else { "(none)" }
$maskedUrl = "${scheme}://${maskedUserInfo}@${redisHost}:${port}"

$provider = if ($redisHost -match 'upstash') { 'Upstash' } else { 'Custom/Unknown' }
$useTls = ($scheme -eq 'rediss' -or $provider -eq 'Upstash')

Write-Section "INFO"
Write-Host "URL: $maskedUrl" -ForegroundColor Yellow
Write-Host "Provider: $provider" -ForegroundColor Yellow
Write-Host "TLS: $useTls" -ForegroundColor Yellow
Write-Host "User: $user" -ForegroundColor Yellow

# --- DNS ---
Write-Section "DNS"
try {
  $addresses = [System.Net.Dns]::GetHostAddresses($redisHost)
  Write-Host "Resolved: $($addresses -join ', ')" -ForegroundColor Green
} catch { Write-Host "Falha DNS: $_" -ForegroundColor Red }

# --- TCP ---
Write-Section "TCP"
try {
  $client = New-Object System.Net.Sockets.TcpClient
  $async = $client.BeginConnect($redisHost, $port, $null, $null)
  $ok = $async.AsyncWaitHandle.WaitOne(4000, $false)
  if (-not $ok) { throw "Timeout" }
  $client.EndConnect($async)
  Write-Host "Conexão TCP OK" -ForegroundColor Green
  $client.Close()
} catch { Write-Host "Falha TCP: $_" -ForegroundColor Red }

# --- TLS Handshake (opcional) ---
if ($useTls) {
  Write-Section "TLS Handshake"
  try {
    $factory = [System.Net.Security.SslStream]
  $tcp = New-Object System.Net.Sockets.TcpClient($redisHost, $port)
    $sslStream = New-Object System.Net.Security.SslStream($tcp.GetStream(), $false, { $true })
  $sslStream.AuthenticateAsClient($redisHost)
    Write-Host "Handshake TLS OK | Cipher: $($sslStream.SslProtocol)" -ForegroundColor Green
    $sslStream.Dispose(); $tcp.Close()
  } catch { Write-Host "Falha TLS: $_" -ForegroundColor Red }
}

# --- redis-cli ---
Write-Section "redis-cli"
$redisCli = Get-Command redis-cli -ErrorAction SilentlyContinue
if (-not $redisCli) {
  Write-Host "redis-cli não encontrado. Pule teste AUTH/PING detalhado." -ForegroundColor Yellow
  Write-Host "Instale: choco install redis-64 (Windows) ou use WSL/Brew." -ForegroundColor DarkYellow
} else {
  $authArgs = @()
  if ($password) { $authArgs += @('-a', $password, '--no-auth-warning') }
  if ($useTls) { $authArgs += '--tls' }
  $baseArgs = @('-h', $redisHost, '-p', $port) + $authArgs
  # AUTH implícito via -a, depois PING
  try {
    $pingResult = & redis-cli @baseArgs PING 2>&1
    if ($LASTEXITCODE -eq 0 -and $pingResult -match 'PONG') {
      Write-Host "PING OK ($pingResult)" -ForegroundColor Green
    } else {
      Write-Host "PING falhou: $pingResult" -ForegroundColor Red
    }
  } catch { Write-Host "Erro executando redis-cli: $_" -ForegroundColor Red }
}

Write-Section "Resumo"
Write-Host "Teste concluído." -ForegroundColor Cyan
