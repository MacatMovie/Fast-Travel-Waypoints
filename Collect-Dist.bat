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

echo ============================================================
echo Fast Travel Waypoints - REBUILD DIST FROM EXISTING BUILDS
echo ============================================================
echo This does NOT compile anything.
echo It only copies already-built jars from targets\*\build\libs.
echo ============================================================
echo.

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\build.ps1" -CollectOnly
set "EXITCODE=%ERRORLEVEL%"

echo.
if "%EXITCODE%"=="0" (
    echo Finished. Jars are in: %~dp0dist
) else (
    echo Collection failed. Exit code: %EXITCODE%
)
echo.
pause
exit /b %EXITCODE%
