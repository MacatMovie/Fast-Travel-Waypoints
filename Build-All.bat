@echo off
setlocal EnableExtensions
rem Keep the console open when Windows/Explorer launches this through cmd /c.
rem CALL avoids the broken nested-quote behaviour that paths containing spaces can trigger.
if not defined FTW_CMDK_RELAUNCH (
    echo %CMDCMDLINE% | findstr /I /C:" /c " >nul
    if not errorlevel 1 (
        set "FTW_CMDK_RELAUNCH=1"
        cmd.exe /d /k call "%~f0" %*
        exit /b
    )
)
cd /d "%~dp0"

if not exist "%~dp0logs" mkdir "%~dp0logs"
for /f %%I in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd-HHmmss"') do set "STAMP=%%I"
set "LOG=%~dp0logs\build-all-%STAMP%.log"
set "LATEST=%~dp0logs\build-all-latest.log"

echo ============================================================
echo Fast Travel Waypoints - BUILD ALL
echo ============================================================
echo Forge 1.20.1 + NeoForge 1.21.1 + Fabric 1.21.1
echo Live output is enabled.
echo Log: %LOG%
echo Finished jars: %~dp0dist
echo ============================================================
echo.

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\build.ps1" -All -LogPath "%LOG%" %*
set "EXITCODE=%ERRORLEVEL%"
copy /Y "%LOG%" "%LATEST%" >nul 2>nul

echo.
echo ============================================================
if "%EXITCODE%"=="0" (
    echo BUILD ALL FINISHED SUCCESSFULLY.
    echo Finished jars are in: %~dp0dist
) else (
    echo BUILD ALL FAILED. Exit code: %EXITCODE%
    echo Please send me this log file:
    echo   %LATEST%
)
echo ============================================================
echo.
pause
exit /b %EXITCODE%
