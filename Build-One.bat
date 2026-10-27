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
echo Fast Travel Waypoints - BUILD SELECTED
echo ============================================================
echo.
echo   A) Forge    1.20.1
echo   B) NeoForge 1.21.1
echo   C) Fabric   1.21.1
echo   D) ALL THREE versions
echo.
echo Enter A, B, C, D, or multiple selections separated by commas.
echo Example: A
echo Example: B
echo Example: A,B
echo Example: C
echo Example: D
echo.

if "%~1"=="" (
    set /p "SELECTION=Build target(s): "
    if not defined SELECTION (
        echo.
        echo No target entered. Nothing was built.
        echo.
        pause
        exit /b 1
    )
) else (
    set "SELECTION=%~1"
)

set "CLEANARG="
if /I "%~2"=="-Clean" set "CLEANARG=-Clean"

echo.
if defined CLEANARG (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\build-selected.ps1" "%SELECTION%" -Clean
) else (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\build-selected.ps1" "%SELECTION%"
)

set "EXITCODE=%ERRORLEVEL%"

echo.
echo ============================================================
if "%EXITCODE%"=="0" (
    echo BUILD SELECTED FINISHED SUCCESSFULLY.
    echo Finished jars are in: %~dp0dist
) else (
    echo BUILD SELECTED FAILED. Exit code: %EXITCODE%
    echo Check the logs folder for the failing target log.
)
echo ============================================================
echo.
pause
exit /b %EXITCODE%
