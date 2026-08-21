<#
.SYNOPSIS
    Stops the services started by run-local.ps1.

.DESCRIPTION
    Kills each PID recorded in logs\pids.txt, together with the Java child process
    Maven spawned. If the PID file is missing or stale, falls back to freeing the
    known CareerForge ports.
#>

[CmdletBinding()]
param()

$ErrorActionPreference = 'SilentlyContinue'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$pidFile = 'logs\pids.txt'
$stopped = 0

if (Test-Path $pidFile) {
    Get-Content $pidFile | ForEach-Object {
        $name, $processId = $_.Split('=', 2)
        if ($processId) {
            # Maven forks a java.exe child; kill the tree, not just the wrapper.
            Get-CimInstance Win32_Process -Filter "ParentProcessId=$processId" |
                ForEach-Object { Stop-Process -Id $_.ProcessId -Force }
            Stop-Process -Id $processId -Force
            Write-Host "Stopped $name (pid $processId)" -ForegroundColor Yellow
            $stopped++
        }
    }
    Remove-Item $pidFile -Force
}

# Fallback: free any CareerForge port still held. -ErrorAction SilentlyContinue here
# specifically: Get-NetTCPConnection's CIM query throws a (non-terminating, but still
# console-printed) error when a port simply isn't in use — normal for most of these ports
# most of the time, not a real failure, so it's silenced rather than left to alarm the reader.
$ports = 8080, 8081, 8082, 8083, 8084, 8085, 8086, 8087, 8088, 8761, 8888
foreach ($port in $ports) {
    Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue | ForEach-Object {
        Stop-Process -Id $_.OwningProcess -Force
        Write-Host "Freed port $port" -ForegroundColor Yellow
        $stopped++
    }
}

if ($stopped -eq 0) { Write-Host 'Nothing was running.' -ForegroundColor Green }
else { Write-Host "Stopped $stopped process(es)." -ForegroundColor Green }
