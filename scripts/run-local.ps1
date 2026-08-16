<#
.SYNOPSIS
    Starts CareerForge AI locally with no Docker containers.

.DESCRIPTION
    Spring Boot does not read .env — only Docker Compose does. This script loads
    .env into the current process environment, activates the `nodocker` profile
    (no Redis, no MinIO), and starts each service as a background process with
    its output tee'd to logs\<service>.log.

    MongoDB Atlas is cloud-hosted, so it is the only "infrastructure" needed.

.PARAMETER Services
    Which services to start. Default is the Milestone 1 core set.
    Use -Services all to start every service.

.EXAMPLE
    .\scripts\run-local.ps1
    .\scripts\run-local.ps1 -Services all
    .\scripts\run-local.ps1 -Services config-server,discovery-server,api-gateway
#>

[CmdletBinding()]
param(
    [string[]] $Services = @('config-server', 'discovery-server', 'api-gateway', 'auth-service'),
    [switch]   $SkipBuild
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$allServices = @(
    'config-server', 'discovery-server', 'api-gateway',
    'auth-service', 'profile-service', 'jd-service',    'ai-service', 'assessment-service',    'application-service'
)
if ($Services.Count -eq 1 -and $Services[0] -eq 'all') { $Services = $allServices }

$ports = @{
    'config-server' = 8888; 'discovery-server' = 8761; 'api-gateway' = 8080
    'auth-service' = 8081; 'profile-service' = 8082; 'jd-service' = 8083
    'ai-service' = 8085; 'assessment-service' = 8086
    'application-service' = 8088
}

# ---------------------------------------------------------------- .env ------
if (-not (Test-Path '.env')) {
    throw "No .env found. Run: Copy-Item .env.example .env   then fill in MONGODB_URI and JWT_SECRET."
}

Write-Host "Loading .env ..." -ForegroundColor Cyan
Get-Content '.env' | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith('#') -and $line.Contains('=')) {
        $name, $value = $line.Split('=', 2)
        $value = $value.Trim().Trim('"').Trim("'")
        if ($value) { [Environment]::SetEnvironmentVariable($name.Trim(), $value, 'Process') }
    }
}

# ------------------------------------------------------------ validate ------
$required = @('MONGODB_URI', 'JWT_SECRET')
foreach ($name in $required) {
    if (-not [Environment]::GetEnvironmentVariable($name)) {
        throw "$name is empty in .env. See README.md -> Local setup."
    }
}
if ([Text.Encoding]::UTF8.GetByteCount($env:JWT_SECRET) -lt 32) {
    throw "JWT_SECRET must be at least 32 bytes. Generate one with scripts\new-jwt-secret.ps1"
}

# Point every service at localhost rather than Docker DNS names.
$env:EUREKA_SERVER_URI = 'http://localhost:8761/eureka/'
$env:CONFIG_IMPORT     = 'optional:configserver:http://localhost:8888'
$env:CONFIG_REPO_LOCATION = "file:$root/infrastructure/config-repo"
$env:APP_ENV           = 'local'

# ---------------------------------------------------------------- build -----
if (-not $SkipBuild) {
    Write-Host "Building all modules (skip with -SkipBuild) ..." -ForegroundColor Cyan
    & mvn.cmd -q clean install -DskipTests
    if ($LASTEXITCODE -ne 0) { throw "Maven build failed. Fix the errors above before starting services." }
    Write-Host "Build OK" -ForegroundColor Green
}

# ---------------------------------------------------------------- start -----
New-Item -ItemType Directory -Force -Path 'logs' | Out-Null
$pidFile = 'logs\pids.txt'
Remove-Item $pidFile -ErrorAction SilentlyContinue

function Wait-ForHealth {
    param([string] $Name, [int] $Port, [int] $TimeoutSeconds = 180)

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $r = Invoke-RestMethod "http://localhost:$Port/actuator/health" -TimeoutSec 3
            if ($r.status -eq 'UP') {
                Write-Host "  $Name is UP on $Port" -ForegroundColor Green
                return $true
            }
        } catch { }
        Start-Sleep -Seconds 3
        Write-Host '.' -NoNewline
    }
    Write-Host ''
    Write-Warning "$Name did not report UP within ${TimeoutSeconds}s. Check logs\$Name.log"
    return $false
}

foreach ($svc in $Services) {
    if (-not $ports.ContainsKey($svc)) { Write-Warning "Unknown service '$svc' - skipping"; continue }

    # config-server needs its own `native` profile to serve the config repo;
    # everything else runs under `nodocker`.
    $profile = if ($svc -eq 'config-server') { 'native' } else { 'nodocker' }

    Write-Host "Starting $svc (profile=$profile, port=$($ports[$svc])) " -NoNewline -ForegroundColor Cyan

    $proc = Start-Process -FilePath 'mvn.cmd' `
        -ArgumentList @('-q', '-pl', "services/$svc", 'spring-boot:run',
                        "-Dspring-boot.run.profiles=$profile") `
        -WorkingDirectory $root `
        -RedirectStandardOutput "logs\$svc.log" `
        -RedirectStandardError  "logs\$svc.err.log" `
        -PassThru -WindowStyle Hidden

    "$svc=$($proc.Id)" | Add-Content $pidFile
    Wait-ForHealth -Name $svc -Port $ports[$svc] | Out-Null
}

Write-Host ''
Write-Host 'Running. Useful URLs:' -ForegroundColor Green
Write-Host '  Eureka registry   http://localhost:8761'
Write-Host '  Gateway health    http://localhost:8080/actuator/health'
Write-Host '  Auth check (401)  curl -i http://localhost:8080/api/profile'
Write-Host ''
Write-Host 'Logs:  Get-Content logs\api-gateway.log -Wait'
Write-Host 'Stop:  .\scripts\stop-local.ps1'
