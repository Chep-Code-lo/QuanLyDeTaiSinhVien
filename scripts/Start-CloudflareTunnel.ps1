param(
    [switch]$Build,
    [switch]$Stop
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$runtimeDir = Join-Path $projectRoot '.runtime/cloudflare'
$statePath = Join-Path $runtimeDir 'state.json'
$envPath = Join-Path $projectRoot '.env'
$utf8 = New-Object System.Text.UTF8Encoding($false)

function Get-OwnedTunnel {
    if (!(Test-Path -LiteralPath $statePath)) { return $null }
    $saved = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
    $process = Get-Process -Id $saved.processId -ErrorAction SilentlyContinue
    if ($process -and $process.ProcessName -eq 'cloudflared' -and
        $process.StartTime.ToUniversalTime().ToString('o') -eq $saved.startedAt) {
        return [PSCustomObject]@{ Process = $process; Url = $saved.url }
    }
    return $null
}

function Read-TunnelLog([string]$Path) {
    if (!(Test-Path -LiteralPath $Path)) { return '' }
    $stream = [System.IO.File]::Open($Path, [System.IO.FileMode]::Open,
        [System.IO.FileAccess]::Read, [System.IO.FileShare]::ReadWrite)
    $reader = New-Object System.IO.StreamReader($stream)
    try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
}

if ($Stop) {
    $owned = Get-OwnedTunnel
    if ($owned) { Stop-Process -Id $owned.Process.Id; Write-Host 'Stopped this project tunnel.' }
    else { Write-Host 'No active tunnel managed by this project.' }
    if (Test-Path -LiteralPath $statePath) { Remove-Item -LiteralPath $statePath }
    return
}

if (!(Test-Path -LiteralPath $envPath)) {
    throw 'Create the project .env file first. See .env.example and README.md.'
}
$envText = [System.IO.File]::ReadAllText($envPath)
function Get-EnvValue([string]$Name) {
    $match = [regex]::Match($envText, "(?m)^$([regex]::Escape($Name))=([^\r\n]*)")
    return $match.Groups[1].Value.Trim()
}
function Set-EnvValue([string]$Name, [string]$Value) {
    $pattern = "(?m)^$([regex]::Escape($Name))=[^\r\n]*"
    $line = "$Name=$Value"
    if ([regex]::IsMatch($script:envText, $pattern)) {
        $script:envText = [regex]::Replace($script:envText, $pattern, [System.Text.RegularExpressions.MatchEvaluator]{ param($match) $line })
    } else {
        $script:envText = $script:envText.TrimEnd() + "`r`n$line`r`n"
    }
}

$frontendPort = Get-EnvValue 'FRONTEND_PORT'
if (!$frontendPort) { $frontendPort = '5173' }
if ($frontendPort -notmatch '^\d+$') { throw 'FRONTEND_PORT must be a number.' }
$origin = "http://127.0.0.1:$frontendPort"
$cloudflared = Get-Command cloudflared -ErrorAction SilentlyContinue
if (!$cloudflared) {
    $installed = 'C:\Program Files (x86)\cloudflared\cloudflared.exe'
    if (Test-Path -LiteralPath $installed) { $cloudflared = Get-Item -LiteralPath $installed }
    else { throw 'Install cloudflared first: winget install --id Cloudflare.cloudflared -e' }
}
$executable = if ($cloudflared.Source) { $cloudflared.Source } else { $cloudflared.FullName }

Push-Location $projectRoot
try {
    $owned = Get-OwnedTunnel
    if ($owned) {
        $tunnelUrl = $owned.Url
        Write-Host "Reusing tunnel: $tunnelUrl"
    } else {
        # Verify the local frontend before creating a public tunnel.
        $null = Invoke-WebRequest -UseBasicParsing -Uri $origin -TimeoutSec 10
        $null = New-Item -ItemType Directory -Force -Path $runtimeDir
        # Do not inherit ~/.cloudflared/config.yml: named-tunnel ingress rules
        # can silently turn every request to a Quick Tunnel into HTTP 404.
        $quickConfig = Join-Path $runtimeDir 'quick.yml'
        [System.IO.File]::WriteAllText($quickConfig, "# Isolated Quick Tunnel configuration.`r`n", $utf8)
        $stamp = Get-Date -Format 'yyyyMMdd-HHmmss-fff'
        $stdout = Join-Path $runtimeDir "$stamp.stdout.log"
        $stderr = Join-Path $runtimeDir "$stamp.stderr.log"
        $tunnel = Start-Process -FilePath $executable -ArgumentList @(
            'tunnel', '--config', ('"' + $quickConfig + '"'), '--no-autoupdate', '--url', $origin, '--protocol', 'http2'
        ) -PassThru -WindowStyle Hidden -RedirectStandardOutput $stdout -RedirectStandardError $stderr
        $tunnelStartedAt = $tunnel.StartTime.ToUniversalTime().ToString('o')
        $deadline = (Get-Date).AddSeconds(60)
        $tunnelUrl = $null
        while ((Get-Date) -lt $deadline) {
            $tunnel.Refresh()
            if ($tunnel.HasExited) { throw "cloudflared exited. Inspect $stderr" }
            $logs = (Read-TunnelLog $stderr) + (Read-TunnelLog $stdout)
            $match = [regex]::Match($logs, 'https://[a-z0-9-]+\.trycloudflare\.com')
            if ($match.Success) { $tunnelUrl = $match.Value; break }
            Start-Sleep -Milliseconds 500
        }
        if (!$tunnelUrl) {
            Stop-Process -Id $tunnel.Id -ErrorAction SilentlyContinue
            throw "No tunnel URL received after 60 seconds. Inspect $stderr"
        }
        $state = @{ processId = $tunnel.Id; startedAt = $tunnelStartedAt; url = $tunnelUrl }
        [System.IO.File]::WriteAllText($statePath, ($state | ConvertTo-Json), $utf8)
    }

    # Keep existing named domains; replace expired Quick Tunnel origins.
    $origins = @((Get-EnvValue 'CORS_ALLOWED_ORIGINS') -split ',' | ForEach-Object { $_.Trim() } |
        Where-Object { $_ -and $_ -notmatch '^https://[^/]+\.trycloudflare\.com/?$' })
    $origins += @("http://localhost:$frontendPort", $origin, $tunnelUrl)
    Set-EnvValue 'APP_BASE_URL' $tunnelUrl
    Set-EnvValue 'CORS_ALLOWED_ORIGINS' (($origins | Select-Object -Unique) -join ',')
    Set-EnvValue 'SESSION_COOKIE_SECURE' 'true'
    Set-EnvValue 'SESSION_COOKIE_SAME_SITE' 'lax'
    Set-EnvValue 'CSRF_COOKIE_SECURE' 'true'
    Set-EnvValue 'CSRF_COOKIE_SAME_SITE' 'lax'
    Set-EnvValue 'CSRF_COOKIE_DOMAIN' ''
    Set-EnvValue 'SERVER_FORWARD_HEADERS_STRATEGY' 'framework'
    [System.IO.File]::WriteAllText($envPath, $envText, $utf8)

    if ($Build) { & docker compose up -d --no-deps --build backend frontend }
    else { & docker compose up -d --no-deps backend }
    if ($LASTEXITCODE -ne 0) { throw 'Docker update failed. The tunnel state is saved; rerun after fixing Docker.' }

    $deadline = (Get-Date).AddSeconds(90)
    $ready = $false
    while ((Get-Date) -lt $deadline) {
        try {
            $health = Invoke-WebRequest -UseBasicParsing -Uri "$tunnelUrl/api/v1/health" -TimeoutSec 10
            if ($health.StatusCode -eq 200) { $ready = $true; break }
        } catch { Start-Sleep -Milliseconds 1000 }
    }
    if (!$ready) { throw "Tunnel created but API is not ready. Check Docker logs and $tunnelUrl/api/v1/health" }
    Write-Host "`nCloudflare Tunnel is ready: $tunnelUrl" -ForegroundColor Green
    Write-Host 'Open this HTTPS URL for both the frontend and API.'
    Write-Host 'Stop it with: .\scripts\Start-CloudflareTunnel.ps1 -Stop'
} finally {
    Pop-Location
}
