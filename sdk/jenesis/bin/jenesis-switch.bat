@echo off
setlocal EnableDelayedExpansion
rem jenesis-switch: discover the Jenesis version recorded in
rem build\jenesis\jenesis.version of each target, install it with Scoop if
rem needed, and put it in front of PATH for this session:
rem
rem     jenesis-switch [targets...]
rem
rem With no arguments, the current directory is the target. With one or more
rem arguments, every target must agree on the recorded version, otherwise the
rem switch is aborted. Run it from cmd.exe, which runs a batch file in the
rem session it was typed in; PowerShell runs one in a child process, so the
rem directory it names has to be put on PATH there by hand.

set "_JS_VERSION="
set "_JS_FROM="
set "_JS_ERROR="
set "_JS_HOME="

if "%~1"=="" (
    call :discover "." ""
) else (
    for %%t in (%*) do if not defined _JS_ERROR call :discover "%%~t" "%%~t - "
)
if defined _JS_ERROR exit /b 1
if not defined _JS_VERSION (
    echo jenesis-switch: no version discovered 1>&2
    exit /b 1
)

echo jenesis-switch: discovered version !_JS_VERSION!

call :locate
if not defined _JS_HOME (
    where scoop >nul 2>&1
    if not errorlevel 1 (
        echo jenesis-switch: installing Jenesis !_JS_VERSION! via Scoop
        call scoop install jenesis@!_JS_VERSION!
        call :locate
    )
)
if not defined _JS_HOME (
    echo jenesis-switch: Jenesis !_JS_VERSION! is not installed and could not be installed - install it with 'scoop install jenesis@!_JS_VERSION!' or from https://jenesis.build 1>&2
    exit /b 1
)

rem A second switch in the same session drops what the first one put in front,
rem so the versions do not stack up in PATH.
set "_JS_BIN=!_JS_HOME!\bin"
set "_JS_PATH=!PATH!"
if defined JENESIS_SWITCH_BIN set "_JS_PATH=!_JS_PATH:%JENESIS_SWITCH_BIN%;=!"
set "_JS_PATH=!_JS_BIN!;!_JS_PATH!"
echo jenesis-switch: !_JS_BIN! leads PATH in this session
echo jenesis-switch: switched to Jenesis !_JS_VERSION!
endlocal & set "PATH=%_JS_PATH%" & set "JENESIS_SWITCH_BIN=%_JS_BIN%"
exit /b 0

:discover
set "_JS_TARGET=%~1"
set "_JS_LABEL=%~2"
if not exist "!_JS_TARGET!\" (
    echo jenesis-switch: target '!_JS_TARGET!' is not a directory 1>&2
    set "_JS_ERROR=1"
    exit /b 1
)
if not exist "!_JS_TARGET!\build\jenesis\" (
    echo jenesis-switch: !_JS_LABEL!no build\jenesis found 1>&2
    set "_JS_ERROR=1"
    exit /b 1
)
if not exist "!_JS_TARGET!\build\jenesis\jenesis.version" (
    echo jenesis-switch: !_JS_LABEL!no version found 1>&2
    set "_JS_ERROR=1"
    exit /b 1
)
set "_JS_CURRENT="
for /f "usebackq delims=" %%v in ("!_JS_TARGET!\build\jenesis\jenesis.version") do if not defined _JS_CURRENT set "_JS_CURRENT=%%v"
if not defined _JS_CURRENT (
    echo jenesis-switch: !_JS_LABEL!jenesis.version is empty 1>&2
    set "_JS_ERROR=1"
    exit /b 1
)
if not defined _JS_VERSION (
    set "_JS_VERSION=!_JS_CURRENT!"
    set "_JS_FROM=!_JS_TARGET!"
    exit /b 0
)
if not "!_JS_VERSION!"=="!_JS_CURRENT!" (
    echo jenesis-switch: version conflict - !_JS_FROM! is at !_JS_VERSION!, !_JS_TARGET! is at !_JS_CURRENT! 1>&2
    set "_JS_ERROR=1"
    exit /b 1
)
exit /b 0

:locate
set "_JS_HOME="
if exist "%USERPROFILE%\scoop\apps\jenesis\!_JS_VERSION!\bin\" set "_JS_HOME=%USERPROFILE%\scoop\apps\jenesis\!_JS_VERSION!"
exit /b 0
