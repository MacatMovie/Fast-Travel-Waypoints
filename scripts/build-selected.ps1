param(
    [Parameter(Mandatory=$true, Position=0)][string]$Selection,
    [switch]$Clean
)

$ErrorActionPreference = 'Continue'
$Root = Split-Path -Parent $PSScriptRoot
$BuildScript = Join-Path $PSScriptRoot 'build.ps1'
$LogsDir = Join-Path $Root 'logs'
New-Item -ItemType Directory -Force -Path $LogsDir | Out-Null

$map = [ordered]@{
    'A' = @('forge-1.20.1')
    'B' = @('neoforge-1.21.1')
    'C' = @('fabric-1.21.1')
    'D' = @('forge-1.20.1', 'neoforge-1.21.1', 'fabric-1.21.1')
}

$tokens = $Selection -split '[,\s]+' | Where-Object { $_ -and $_.Trim() }
$targets = New-Object System.Collections.Generic.List[string]
$invalid = New-Object System.Collections.Generic.List[string]

foreach ($raw in $tokens) {
    $token = $raw.Trim()
    $upper = $token.ToUpperInvariant()

    if ($map.Contains($upper)) {
        foreach ($mapped in $map[$upper]) {
            if (-not $targets.Contains($mapped)) { $targets.Add($mapped) }
        }
        continue
    }

    if (Test-Path (Join-Path $Root "targets\$token")) {
        if (-not $targets.Contains($token)) { $targets.Add($token) }
        continue
    }

    $invalid.Add($token)
}

if ($targets.Count -eq 0) {
    Write-Host ""
    Write-Host "No valid targets were selected." -ForegroundColor Red
    if ($invalid.Count -gt 0) {
        Write-Host ("Unknown selection(s): " + ($invalid -join ', ')) -ForegroundColor Red
    }
    exit 1
}

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$failures = New-Object System.Collections.Generic.List[string]
$built = 0

Write-Host ""
Write-Host "============================================================"
Write-Host ("Selected target(s): " + ($targets -join ', '))
if ($Clean) { Write-Host "Clean build: YES" }
Write-Host "============================================================"

foreach ($target in $targets) {
    $log = Join-Path $LogsDir "build-$target-$stamp.log"
    $latest = Join-Path $LogsDir 'build-one-latest.log'

    Write-Host ""
    Write-Host "============================================================"
    Write-Host "BUILDING: $target"
    Write-Host "============================================================"
    Write-Host "Log: $log"
    Write-Host ""

    $args = @(
        '-NoProfile',
        '-ExecutionPolicy', 'Bypass',
        '-File', $BuildScript,
        $target,
        '-LogPath', $log
    )
    if ($Clean) { $args += '-Clean' }

    & powershell.exe @args
    $code = $LASTEXITCODE

    if (Test-Path $log) {
        Copy-Item $log $latest -Force -ErrorAction SilentlyContinue
    }

    if ($code -eq 0) {
        Write-Host ""
        Write-Host "=== $target SUCCESS ===" -ForegroundColor Green
        $built++
    } else {
        Write-Host ""
        Write-Host "=== $target FAILED (exit code $code) ===" -ForegroundColor Red
        Write-Host "Log: $log" -ForegroundColor Red
        $failures.Add($target)
    }
}

Write-Host ""
Write-Host "============================================================"
if ($invalid.Count -gt 0) {
    Write-Host ("Ignored unknown selection(s): " + ($invalid -join ', ')) -ForegroundColor Yellow
}
if ($failures.Count -eq 0) {
    Write-Host "ALL SELECTED BUILDS FINISHED SUCCESSFULLY." -ForegroundColor Green
    Write-Host "Built $built target(s)."
    Write-Host "Finished jars are in: $(Join-Path $Root 'dist')"
    Write-Host "============================================================"
    exit 0
}

Write-Host ("SELECTED BUILDS FINISHED WITH {0} FAILURE(S)." -f $failures.Count) -ForegroundColor Red
Write-Host ("Failed: " + ($failures -join ', ')) -ForegroundColor Red
Write-Host "============================================================"
exit 1
