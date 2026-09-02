@echo off
setlocal EnableDelayedExpansion
set "SCRIPT_DIR=%~dp0"
for %%i in ("%~dp0..") do set "JENESIS_HOME=%%~fi"

set "STAMP="
set "FOUND_DIR="
set "DIR=%CD%"
:findstamp
if exist "!DIR!\build\jenesis\jenesis.version" (
    for /f "usebackq delims=" %%v in ("!DIR!\build\jenesis\jenesis.version") do if not defined STAMP set "STAMP=%%v"
    set "FOUND_DIR=!DIR!"
    goto :stampdone
)
for %%i in ("!DIR!\..") do set "PARENT=%%~fi"
if "!PARENT!"=="!DIR!" goto :stampdone
set "DIR=!PARENT!"
goto :findstamp
:stampdone

if not defined STAMP (
    call "%SCRIPT_DIR%jenesis-run.bat" %*
    exit /b !errorlevel!
)

set "VERSION="
for %%f in ("%JENESIS_HOME%\sources\*-sources.jar") do (
    if not defined VERSION (
        set "VERSION=%%~nf"
        if /i "!VERSION:~0,14!"=="build.jenesis-" set "VERSION=!VERSION:~14!"
        if /i "!VERSION:~-8!"=="-sources" set "VERSION=!VERSION:~0,-8!"
    )
)
if "!STAMP!"=="!VERSION!" (
    call "%SCRIPT_DIR%jenesis-run.bat" %*
    exit /b !errorlevel!
)

set "TARGET_HOME="
for %%h in (
    "%USERPROFILE%\scoop\apps\jenesis\!STAMP!"
    "%JENESIS_HOME%\..\!STAMP!"
) do (
    if not defined TARGET_HOME (
        if exist "%%~h\bin" set "TARGET_HOME=%%~fh"
    )
)

if not defined TARGET_HOME (
    echo jenesis: !FOUND_DIR!\build\jenesis is at version !STAMP!, which is not installed 1>&2
    echo jenesis: install that version, or run the installed version with 'jenesis-run' 1>&2
    exit /b 1
)

if exist "!TARGET_HOME!\bin\jenesis-run.bat" (
    call "!TARGET_HOME!\bin\jenesis-run.bat" %*
    exit /b !errorlevel!
)
call "!TARGET_HOME!\bin\jenesis.bat" %*
exit /b !errorlevel!
