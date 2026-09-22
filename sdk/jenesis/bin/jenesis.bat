@echo off
setlocal EnableDelayedExpansion
set "SCRIPT_DIR=%~dp0"
for %%i in ("%~dp0..") do set "JENESIS_HOME=%%~fi"

rem Every Jenesis up to and including this version is refused: its engine is no longer
rem considered safe to run, so a project that records one is sent to jenesis-unsafe rather
rem than built. Raise it here and in jenesis together, in the release that supersedes the
rem version it names.
set "UNSAFE_THROUGH=0.14.0"

set "VENDORED="
set "DIR=%CD%"
:findvendored
if exist "!DIR!\build\jenesis\" (
    set "VENDORED=!DIR!\build\jenesis"
    goto :vendoreddone
)
for %%i in ("!DIR!\..") do set "PARENT=%%~fi"
if "!PARENT!"=="!DIR!" goto :vendoreddone
set "DIR=!PARENT!"
goto :findvendored
:vendoreddone

set "VERSION="
for %%f in ("%JENESIS_HOME%\sources\*-sources.jar") do (
    if not defined VERSION (
        set "VERSION=%%~nf"
        if /i "!VERSION:~0,14!"=="build.jenesis-" set "VERSION=!VERSION:~14!"
        if /i "!VERSION:~-8!"=="-sources" set "VERSION=!VERSION:~0,-8!"
    )
)

set "STAMP="
if defined VENDORED (
    if exist "!VENDORED!\jenesis.version" (
        for /f "usebackq delims=" %%v in ("!VENDORED!\jenesis.version") do if not defined STAMP set "STAMP=%%v"
    )
)
if defined STAMP set "STAMP=!STAMP:"=!"
if not defined STAMP set "STAMP=!VERSION!"
if not defined STAMP goto :handover

call :above "!STAMP!" "!UNSAFE_THROUGH!"
if not defined ABOVE goto :refuse
goto :handover

rem A version is read as its leading numeric components, so that a pre-release of a version
rem counts as that version: 0.14.0-rc1 is refused where 0.14.0 is, and a version that names
rem no number at all reads as 0.0.0 and is refused as well.
:above
set "ABOVE="
set "GIVEN="
for /f "delims=-+_ " %%v in ("%~1") do set "GIVEN=%%v"
set "G1=0" & set "G2=0" & set "G3=0"
for /f "tokens=1-3 delims=." %%a in ("!GIVEN!") do (
    if not "%%a"=="" set "G1=%%a"
    if not "%%b"=="" set "G2=%%b"
    if not "%%c"=="" set "G3=%%c"
)
call :numeric G1
call :numeric G2
call :numeric G3
set "F1=0" & set "F2=0" & set "F3=0"
for /f "tokens=1-3 delims=." %%a in ("%~2") do (
    if not "%%a"=="" set "F1=%%a"
    if not "%%b"=="" set "F2=%%b"
    if not "%%c"=="" set "F3=%%c"
)
if !G1! gtr !F1! (
    set "ABOVE=1"
    exit /b 0
)
if !G1! lss !F1! exit /b 0
if !G2! gtr !F2! (
    set "ABOVE=1"
    exit /b 0
)
if !G2! lss !F2! exit /b 0
if !G3! gtr !F3! set "ABOVE=1"
exit /b 0

:numeric
for /f "delims=0123456789" %%z in ("!%~1!") do set "%~1=0"
if not defined %~1 set "%~1=0"
exit /b 0

:handover
if not exist "%SCRIPT_DIR%jenesis-unsafe.bat" (
    echo jenesis: %SCRIPT_DIR% ships no jenesis-unsafe.bat to hand the build over to 1>&2
    exit /b 1
)
call "%SCRIPT_DIR%jenesis-unsafe.bat" %*
exit /b !errorlevel!

:refuse
set "WHERE=!VENDORED!"
if not defined WHERE set "WHERE=%JENESIS_HOME%"
echo jenesis: !WHERE! is at version !STAMP!, and every Jenesis up to !UNSAFE_THROUGH! is no longer considered safe 1>&2
echo jenesis: refusing to run - this launcher executes a Jenesis newer than !UNSAFE_THROUGH! only, so 1>&2
echo     that a project cannot pull an engine whose release is no longer trusted. 1>&2
echo. 1>&2
echo Move the project on, which is the fix this asks for: re-vendor build\jenesis from the 1>&2
echo installed version and commit what changes. 1>&2
echo. 1>&2
echo     jenesis-init                 re-vendor build\jenesis from the installed version 1>&2
echo     jenesis-validate             report how the vendored tree differs from it first 1>&2
echo. 1>&2
echo Where !STAMP! has to run all the same - reproducing an old build, a branch that is not 1>&2
echo moving - the unguarded launcher still runs it, and still verifies the vendored sources 1>&2
echo against the published ones before it does: 1>&2
echo. 1>&2
echo     jenesis-unsafe [selectors]   run the !STAMP! the project records 1>&2
echo. 1>&2
echo Warning: that command runs an engine that is no longer considered safe. 1>&2
exit /b 1
