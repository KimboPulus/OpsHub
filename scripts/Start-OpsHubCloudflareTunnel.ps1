param(
    [int] $BackendPort = 8080,
    [int] $FrontendPort = 5173
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$frontend = Join-Path $root "frontend"
$tools = Join-Path $root ".tools"
$cloudflaredLocal = Join-Path $tools "cloudflared.exe"

function Wait-ForUrl {
    param(
        [string] $Url,
        [int] $Seconds = 60
    )

    $deadline = (Get-Date).AddSeconds($Seconds)

    while ((Get-Date) -lt $deadline) {
        try {
            Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 3 | Out-Null
            return
        }
        catch {
            Start-Sleep -Seconds 2
        }
    }

    throw "Timed out waiting for $Url"
}

function Get-Cloudflared {
    $existing = Get-Command "cloudflared" -ErrorAction SilentlyContinue

    if ($existing) {
        return $existing.Source
    }

    if (!(Test-Path -LiteralPath $cloudflaredLocal)) {
        New-Item -ItemType Directory -Force -Path $tools | Out-Null
        $url = "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-windows-amd64.exe"
        Write-Host "Downloading cloudflared..."
        Invoke-WebRequest -Uri $url -OutFile $cloudflaredLocal
    }

    return $cloudflaredLocal
}

if (!(Test-Path -LiteralPath (Join-Path $frontend "node_modules"))) {
    Write-Host "Installing frontend packages..."
    Push-Location $frontend
    npm install
    Pop-Location
}

Write-Host "Starting backend on port $BackendPort..."
$backendProcess = Start-Process -FilePath (Join-Path $root "mvnw.cmd") `
    -ArgumentList "spring-boot:run", "-Dspring-boot.run.arguments=--server.port=$BackendPort" `
    -WorkingDirectory $root `
    -PassThru `
    -WindowStyle Hidden

Write-Host "Starting frontend on port $FrontendPort..."
$frontendProcess = Start-Process -FilePath "npm.cmd" `
    -ArgumentList "run", "dev", "--", "--host", "127.0.0.1", "--port", "$FrontendPort" `
    -WorkingDirectory $frontend `
    -PassThru `
    -WindowStyle Hidden

try {
    Wait-ForUrl "http://127.0.0.1:$BackendPort/api/issues"
    Wait-ForUrl "http://127.0.0.1:$FrontendPort"

    $cloudflared = Get-Cloudflared

    Write-Host ""
    Write-Host "Cloudflare will print the public trycloudflare.com URL below."
    Write-Host "Keep this window open. Press Ctrl+C to stop everything."
    Write-Host ""

    & $cloudflared tunnel --url "http://127.0.0.1:$FrontendPort"
}
finally {
    Write-Host "Stopping local app processes..."
    Stop-Process -Id $frontendProcess.Id -Force -ErrorAction SilentlyContinue
    Stop-Process -Id $backendProcess.Id -Force -ErrorAction SilentlyContinue
}
