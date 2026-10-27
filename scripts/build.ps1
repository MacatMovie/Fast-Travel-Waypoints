param(
    [Parameter(Position=0)][string]$Target,
    [switch]$Clean,
    [switch]$All,
    [switch]$CollectOnly,
    [string]$LogPath
)
$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
$TargetsDir = Join-Path $Root 'targets'
$DistDir = Join-Path $Root 'dist'
$GlobalGradleDir = 'C:\Gradle'
$LocalGradleUserHome = Join-Path $Root '.gradle-user-home'
New-Item -ItemType Directory -Force -Path $DistDir | Out-Null
New-Item -ItemType Directory -Force -Path $LocalGradleUserHome | Out-Null

$transcriptStarted = $false
if ($LogPath) {
    $logDir = Split-Path -Parent $LogPath
    if ($logDir) { New-Item -ItemType Directory -Force -Path $logDir | Out-Null }
    try {
        Start-Transcript -Path $LogPath -Force | Out-Null
        $transcriptStarted = $true
    } catch {
        Write-Warning "Could not start transcript logging: $($_.Exception.Message)"
    }
}

function Resolve-JavaHome([int]$Version) {
    $envName = "JAVA${Version}_HOME"
    $candidate = [Environment]::GetEnvironmentVariable($envName)
    if ($candidate -and (Test-Path (Join-Path $candidate 'bin\java.exe'))) { return $candidate }

    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
        $javaExe = Join-Path $env:JAVA_HOME 'bin\java.exe'
        $v = (& cmd.exe /d /c ('"' + $javaExe + '" -version 2^>^&1')) | Select-Object -First 1
        if ($v -match ('"' + $Version + '(?:\.|")')) { return $env:JAVA_HOME }
    }

    $roots = @('C:\Program Files\Java', 'C:\Program Files\Eclipse Adoptium', 'C:\Program Files\Microsoft', 'C:\Program Files\Zulu')
    foreach ($r in $roots) {
        if (-not (Test-Path $r)) { continue }
        $dirs = Get-ChildItem $r -Directory -ErrorAction SilentlyContinue | Where-Object {
            $_.Name -match "(jdk|temurin|openjdk|zulu).*$Version|^jdk-$Version"
        }
        foreach ($d in $dirs) {
            if (Test-Path (Join-Path $d.FullName 'bin\java.exe')) { return $d.FullName }
        }
    }
    throw "Java $Version not found. Set $envName to your JDK $Version folder."
}

function Get-Gradle([string]$Version) {
    New-Item -ItemType Directory -Force -Path $GlobalGradleDir | Out-Null
    $gradleHome = Join-Path $GlobalGradleDir "gradle-$Version"
    $exe = Join-Path $gradleHome 'bin\gradle.bat'
    if (Test-Path $exe) {
        Write-Host "Using global Gradle $Version from $gradleHome" -ForegroundColor DarkGray
        return $exe
    }

    $zip = Join-Path $GlobalGradleDir "gradle-$Version-bin.zip"
    if (-not (Test-Path $zip)) {
        Write-Host "Gradle $Version not found in C:\Gradle. Downloading once..." -ForegroundColor Yellow
        Invoke-WebRequest -UseBasicParsing "https://services.gradle.org/distributions/gradle-$Version-bin.zip" -OutFile $zip
    } else {
        Write-Host "Found cached Gradle archive: $zip" -ForegroundColor DarkGray
    }

    Write-Host "Extracting Gradle $Version to C:\Gradle..." -ForegroundColor Yellow
    Expand-Archive -Path $zip -DestinationPath $GlobalGradleDir -Force
    if (-not (Test-Path $exe)) { throw "Gradle $Version extraction failed. Expected $exe" }
    return $exe
}

function Show-MatrixProgress([int]$Current, [int]$Total, [string]$Name) {
    if ($Total -le 0) { return }
    $width = 30
    $filled = [Math]::Floor(($Current / $Total) * $width)
    if ($filled -gt $width) { $filled = $width }
    $bar = ('#' * $filled) + ('-' * ($width - $filled))
    $pct = [Math]::Floor(($Current / $Total) * 100)
    Write-Host ""
    Write-Host ("[{0}] {1,3}%  ({2}/{3})  {4}" -f $bar, $pct, $Current, $Total, $Name) -ForegroundColor Cyan
}

function Collect-TargetJars([string]$Name) {
    $dir = Join-Path $TargetsDir $Name
    $libs = Join-Path $dir 'build\libs'
    if (-not (Test-Path $libs)) {
        Write-Host "No existing build output for $Name" -ForegroundColor Yellow
        return 0
    }

    # Metadata gets a strict numeric mod_version; the output JAR filename adds a 'v'.
    # The collector keeps the user-facing prefixed naming convention in dist.
    $properties = Get-Content (Join-Path $dir 'gradle.properties')
    $versionLine = $properties | Where-Object { $_ -match '^mod_version\s*=' } | Select-Object -First 1
    $mcLine = $properties | Where-Object { $_ -match '^minecraft_version\s*=' } | Select-Object -First 1
    if (-not $versionLine -or -not $mcLine) { throw "Missing mod_version or minecraft_version for $Name" }
    $modVersion = ($versionLine -split '=', 2)[1].Trim()
    $mcVersion = ($mcLine -split '=', 2)[1].Trim()
    $releaseVersion = if ($modVersion.StartsWith('v')) { $modVersion } else { "v$modVersion" }
    $unprefixedVersion = $releaseVersion.Substring(1)
    $loaderName = switch ($Name) {
        'forge-1.20.1' { 'Forge' }
        'neoforge-1.21.1' { 'NeoForge' }
        'fabric-1.21.1' { 'Fabric' }
        default { throw "Unknown target '$Name'" }
    }
    $namePrefix = "Fast_Travel_Waypoints-$loaderName-$mcVersion"
    $releaseName = "$namePrefix-$releaseVersion.jar"
    $oldName = "$namePrefix-$unprefixedVersion.jar"

    # Some existing build directories may contain a JAR from before the v-prefix fix.
    # Choose the newest matching output and copy only the correctly named release JAR.
    $jar = Get-ChildItem $libs -File -Filter '*.jar' | Where-Object {
        $_.Name -eq $releaseName -or $_.Name -eq $oldName
    } | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1

    if (-not $jar) {
        Write-Host "No release JAR found for $Name (expected $releaseName)" -ForegroundColor Yellow
        return 0
    }

    Copy-Item $jar.FullName (Join-Path $DistDir $releaseName) -Force
    $staleOutput = Join-Path $DistDir $oldName
    if (Test-Path $staleOutput) { Remove-Item $staleOutput -Force }
    Write-Host "Collected: $releaseName" -ForegroundColor DarkGray
    return 1
}

function Build-Target([string]$Name) {
    $dir = Join-Path $TargetsDir $Name
    if (-not (Test-Path $dir)) { throw "Unknown target '$Name'." }

    $javaVersion = [int](Get-Content (Join-Path $dir 'java-version.txt') -Raw).Trim()
    $gradleVersion = (Get-Content (Join-Path $dir 'gradle-version.txt') -Raw).Trim()
    $javaHome = Resolve-JavaHome $javaVersion

    $oldJavaHome = $env:JAVA_HOME
    $oldPath = $env:Path
    $oldGradleUserHome = $env:GRADLE_USER_HOME

    try {
        $env:JAVA_HOME = $javaHome
        $env:Path = (Join-Path $javaHome 'bin') + ';' + $oldPath
        $env:GRADLE_USER_HOME = $LocalGradleUserHome

        Write-Host "`n=== $Name | Java $javaVersion | Gradle $gradleVersion ===" -ForegroundColor Cyan
        Write-Host "Java home: $javaHome" -ForegroundColor DarkGray

        Push-Location $dir
        try {
            $gradle = Get-Gradle $gradleVersion
            & $gradle --stop | Out-Host

            $gradleArgs = @('--console=plain', '--no-daemon', '--no-watch-fs')
            if ($Clean) { $gradleArgs += 'clean' }
            $gradleArgs += 'build'

            Write-Host "Running: $gradle $($gradleArgs -join ' ')" -ForegroundColor DarkGray
            $quotedGradle = '"' + $gradle + '"'
            $cmdLine = $quotedGradle + ' ' + ($gradleArgs -join ' ') + ' 2>&1'
            & cmd.exe /d /s /c $cmdLine | ForEach-Object { Write-Host $_ }
            $gradleExitCode = $LASTEXITCODE

            if ($gradleExitCode -ne 0) { throw "$Name build failed with exit code $gradleExitCode" }
            if ((Collect-TargetJars $Name) -ne 1) { throw "$Name build returned no distributable JAR." }
        } finally {
            Pop-Location
        }
    } finally {
        $env:JAVA_HOME = $oldJavaHome
        $env:Path = $oldPath
        if ($null -eq $oldGradleUserHome) {
            Remove-Item Env:GRADLE_USER_HOME -ErrorAction SilentlyContinue
        } else {
            $env:GRADLE_USER_HOME = $oldGradleUserHome
        }
    }
}

try {
    $allTargets = @('forge-1.20.1', 'neoforge-1.21.1', 'fabric-1.21.1')

    if ($CollectOnly) {
        Write-Host "Rebuilding dist from existing target build outputs (no compilation)..." -ForegroundColor Cyan
        $totalCollected = 0
        foreach ($t in $allTargets) { $totalCollected += (Collect-TargetJars $t) }
        Write-Host "Collected $totalCollected jar(s) into: $DistDir" -ForegroundColor Green
        exit 0
    }

    if ($All) {
        $failures = @()
        $total = $allTargets.Count
        $i = 0
        foreach ($t in $allTargets) {
            $i++
            Show-MatrixProgress $i $total $t
            try {
                Build-Target $t
                Write-Host "=== $t SUCCESS ===" -ForegroundColor Green
            } catch {
                $failures += $t
                Write-Host "=== $t FAILED ===" -ForegroundColor Red
                Write-Host $_.Exception.Message -ForegroundColor Red
            }
        }

        Write-Host "`n============================================================"
        if ($failures.Count -eq 0) {
            Write-Host "[##############################] 100%  ALL TARGETS BUILT SUCCESSFULLY" -ForegroundColor Green
            Write-Host "Output: $DistDir"
            exit 0
        }

        Write-Host ("BUILD MATRIX FINISHED WITH {0} FAILURE(S):" -f $failures.Count) -ForegroundColor Red
        $failures | ForEach-Object { Write-Host "  - $_" -ForegroundColor Red }
        throw ("Build failed for: " + ($failures -join ', '))
    }

    if (-not $Target) {
        Write-Host 'Available targets:'
        $allTargets | ForEach-Object { Write-Host "  $_" }
        Write-Host "`nUsage: .\scripts\build.ps1 forge-1.20.1 [-Clean]"
        Write-Host "       .\scripts\build.ps1 neoforge-1.21.1 [-Clean]"
        Write-Host "       .\scripts\build.ps1 fabric-1.21.1 [-Clean]"
        Write-Host "       .\scripts\build.ps1 -All [-Clean]"
        Write-Host "       .\scripts\build.ps1 -CollectOnly"
        exit 0
    }

    Show-MatrixProgress 1 1 $Target
    Build-Target $Target
    Write-Host "[##############################] 100%  $Target BUILT SUCCESSFULLY" -ForegroundColor Green
    exit 0
}
finally {
    if ($transcriptStarted) {
        try { Stop-Transcript | Out-Null } catch {}
    }
}
