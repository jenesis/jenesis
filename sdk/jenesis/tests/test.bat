@echo off
setlocal EnableDelayedExpansion
REM Happy-path test for the Windows SDK scripts (sdk\jenesis\bin\jenesis*.bat).
REM Mirrors sdk\tests\test.sh: jenesis-init -> jenesis-version -> jenesis-validate
REM against a freshly-staged SDK at <repo>\sdk\{lib,sources}\. Exits 0 only when
REM every check passes.

REM This script lives at <repo>\sdk\tests\, so its parent is the SDK home.
for %%i in ("%~dp0..") do set "SDK_HOME=%%~fi"

set "SOURCES_JAR="
for %%f in ("%SDK_HOME%\sources\*-sources.jar") do (
    if not defined SOURCES_JAR set "SOURCES_JAR=%%f"
)
if not defined SOURCES_JAR (
    echo sdk-tests: no sources jar at %SDK_HOME%\sources - stage the SDK first 1>&2
    exit /b 1
)

for %%i in ("%SOURCES_JAR%") do set "JAR_NAME=%%~ni"
set "VERSION=!JAR_NAME!"
if /i "!VERSION:~0,14!"=="build.jenesis-" set "VERSION=!VERSION:~14!"
if /i "!VERSION:~-8!"=="-sources" set "VERSION=!VERSION:~0,-8!"
echo sdk-tests: SDK version !VERSION!

set "TMPDIR=%TEMP%\sdk-tests-%RANDOM%-%RANDOM%"
mkdir "%TMPDIR%" || exit /b 1
set "PROJ=%TMPDIR%\proj"
mkdir "%PROJ%"
set "OUTFILE=%TMPDIR%\out.txt"

REM [1/10] jenesis-version on fresh directory: exit 1, reports missing build/jenesis
echo [1/10] jenesis-version on fresh directory
call "%SDK_HOME%\bin\jenesis-version.bat" "%PROJ%" > "%OUTFILE%" 2>&1
set "RC=!ERRORLEVEL!"
if not "!RC!"=="1" goto :fail
findstr /c:"sdk is at version !VERSION!" "%OUTFILE%" >nul || goto :fail
findstr /c:"no build/jenesis found" "%OUTFILE%" >nul || goto :fail
echo   ok

REM [2/10] jenesis-init: populates build\jenesis and writes jenesis.version
echo [2/10] jenesis-init
call "%SDK_HOME%\bin\jenesis-init.bat" "%PROJ%" > "%OUTFILE%" 2>&1
if errorlevel 1 goto :fail
if not exist "%PROJ%\build\jenesis\" goto :fail
if not exist "%PROJ%\build\jenesis\jenesis.version" goto :fail
set "RECORDED="
for /f "usebackq delims=" %%v in ("%PROJ%\build\jenesis\jenesis.version") do if not defined RECORDED set "RECORDED=%%v"
if not "!RECORDED!"=="!VERSION!" goto :fail
echo   ok

REM [3/10] jenesis-version on initialised project: exit 0, reports matching version
echo [3/10] jenesis-version on initialised project
call "%SDK_HOME%\bin\jenesis-version.bat" "%PROJ%" > "%OUTFILE%" 2>&1
if errorlevel 1 goto :fail
findstr /c:"build/jenesis is at version !VERSION!" "%OUTFILE%" >nul || goto :fail
echo   ok

REM [4/10] jenesis-validate: reports zero drift against the bundled sources
echo [4/10] jenesis-validate
call "%SDK_HOME%\bin\jenesis-validate.bat" "%PROJ%" > "%OUTFILE%" 2>&1
findstr /c:"0 differs, 0 missing, 0 additional" "%OUTFILE%" >nul || goto :fail
echo   ok

REM [5/10] jenesis: the launcher resolves the engine from the SDK and dispatches to it;
REM `help` is a print-only goal, so it runs offline once a project descriptor exists.
echo [5/10] jenesis help
if not exist "%PROJ%\sources\" mkdir "%PROJ%\sources"
(echo module sdktest {})> "%PROJ%\sources\module-info.java"
pushd "%PROJ%"
call "%SDK_HOME%\bin\jenesis.bat" help > "%OUTFILE%" 2>&1
set "RC=!ERRORLEVEL!"
popd
if not "!RC!"=="0" goto :fail
findstr /c:"a Java build tool" "%OUTFILE%" >nul || goto :fail
echo   ok

REM [6/10] jenesis-make: runs the installed version directly, without the version lookup
echo [6/10] jenesis-make
pushd "%PROJ%"
call "%SDK_HOME%\bin\jenesis-make.bat" help > "%OUTFILE%" 2>&1
set "RC=!ERRORLEVEL!"
popd
if not "!RC!"=="0" goto :fail
findstr /c:"a Java build tool" "%OUTFILE%" >nul || goto :fail
echo   ok

REM [7/10] jenesis: a patched vendored tree is refused, never run and never built from
echo [7/10] jenesis refuses an engine the vendored sources do not match
set "PATCHED=%TMPDIR%\patched"
xcopy /s /e /y /i /q "%PROJ%" "%PATCHED%" >nul
echo // local patch>> "%PATCHED%\build\jenesis\Platform.java"
pushd "%PATCHED%"
call "%SDK_HOME%\bin\jenesis.bat" help > "%OUTFILE%" 2>&1
set "RC=!ERRORLEVEL!"
popd
if not "!RC!"=="1" goto :fail
findstr /c:"does not match the sources" "%OUTFILE%" >nul || goto :fail
findstr /c:"refusing to run" "%OUTFILE%" >nul || goto :fail
findstr /c:"a Java build tool" "%OUTFILE%" >nul && goto :fail
findstr /c:"jenesis-make [selectors]" "%OUTFILE%" >nul || goto :fail
findstr /c:"builds this project as a standard build" "%OUTFILE%" >nul || goto :fail
findstr /c:"Neither of these executes the vendored build code" "%OUTFILE%" >nul || goto :fail
findstr /c:"java build\jenesis\Make.java [selectors]" "%OUTFILE%" >nul || goto :fail
findstr /c:"compiles the engine into .jenesis\classes" "%OUTFILE%" >nul || goto :fail
findstr /c:"the project root at %PATCHED%" "%OUTFILE%" >nul || goto :fail
findstr /c:"may drive it from one of its own" "%OUTFILE%" >nul || goto :fail
findstr /c:"that command executes unreviewed code" "%OUTFILE%" >nul || goto :fail
findstr /c:"run builds from sources you" "%OUTFILE%" >nul || goto :fail
echo   ok

REM [8/10] jenesis: a stamp naming a version that cannot be installed is refused, not built
echo [8/10] jenesis on an uninstalled stamp
set "UNSTAMPED=%TMPDIR%\unstamped"
xcopy /s /e /y /i /q "%PROJ%" "%UNSTAMPED%" >nul
<nul set /p ="0.0.0-ABSENT">"%UNSTAMPED%\build\jenesis\jenesis.version"
pushd "%UNSTAMPED%"
call "%SDK_HOME%\bin\jenesis.bat" help > "%OUTFILE%" 2>&1
set "RC=!ERRORLEVEL!"
popd
if not "!RC!"=="1" goto :fail
findstr /c:"no installed Jenesis matches" "%OUTFILE%" >nul || goto :fail
findstr /c:"refusing to run" "%OUTFILE%" >nul || goto :fail
echo   ok

REM [9/10] jenesis-validate: a class file beside its source is inert and stays unreported,
REM one whose source is gone is live code and is named
echo [9/10] jenesis-validate on locally compiled classes
set "COMPILED=%TMPDIR%\compiled"
xcopy /s /e /y /i /q "%PROJ%" "%COMPILED%" >nul
pushd "%COMPILED%"
javac -nowarn build\jenesis\Project.java >nul 2>&1
popd
if not exist "%COMPILED%\build\jenesis\Project.class" goto :fail
call "%SDK_HOME%\bin\jenesis-validate.bat" "%COMPILED%" > "%OUTFILE%" 2>&1
findstr /c:"0 differs, 0 missing, 0 additional" "%OUTFILE%" >nul || goto :fail
del "%COMPILED%\build\jenesis\BuildExecutorCallback.java"
call "%SDK_HOME%\bin\jenesis-validate.bat" "%COMPILED%" > "%OUTFILE%" 2>&1
findstr /c:"build/jenesis/BuildExecutorCallback.class additional" "%OUTFILE%" >nul || goto :fail
findstr /c:"build/jenesis/BuildExecutorCallback.java missing" "%OUTFILE%" >nul || goto :fail
echo   ok

REM [10/10] jenesis-jdk: a toolchain version becomes the Scoop package of its vendor and feature
REM release, an unknown vendor word or an early-access build is refused, and --enable records the
REM installer once
echo [10/10] jenesis-jdk with Scoop, and --enable
set "FAKE_BIN=%TMPDIR%\fake-bin"
mkdir "%FAKE_BIN%"
set "SCOOP_LOG=%TMPDIR%\scoop.log"
> "%FAKE_BIN%\scoop.cmd" echo @echo off
>> "%FAKE_BIN%\scoop.cmd" echo ^>^> "%SCOOP_LOG%" echo %%*
>> "%FAKE_BIN%\scoop.cmd" echo if /i "%%~1"=="bucket" if /i "%%~2"=="list" echo java  https://github.com/ScoopInstaller/Java
>> "%FAKE_BIN%\scoop.cmd" echo if /i "%%~1"=="prefix" exit /b 1
>> "%FAKE_BIN%\scoop.cmd" echo exit /b 0
set "SAVED_PATH=%PATH%"
set "PATH=%FAKE_BIN%;%PATH%"
call "%SDK_HOME%\bin\jenesis-jdk.bat" 25.0.3-temurin > "%OUTFILE%" 2>&1
if errorlevel 1 goto :fail
findstr /x /c:"install java/temurin25-jdk" "%SCOOP_LOG%" >nul || goto :fail
findstr /c:"installs the newest temurin25-jdk" "%OUTFILE%" >nul || goto :fail
call "%SDK_HOME%\bin\jenesis-jdk.bat" 25-zulu > "%OUTFILE%" 2>&1
if errorlevel 1 goto :fail
findstr /x /c:"install java/zulu25-jdk" "%SCOOP_LOG%" >nul || goto :fail
call "%SDK_HOME%\bin\jenesis-jdk.bat" 25-nosuchvendor > "%OUTFILE%" 2>&1
if not "!ERRORLEVEL!"=="2" goto :fail
call "%SDK_HOME%\bin\jenesis-jdk.bat" 26-ea > "%OUTFILE%" 2>&1
if not "!ERRORLEVEL!"=="2" goto :fail
set "PATH=%SAVED_PATH%"
set "SAVED_USERPROFILE=%USERPROFILE%"
set "USERPROFILE=%TMPDIR%\enabled-home"
mkdir "%USERPROFILE%"
call "%SDK_HOME%\bin\jenesis-jdk.bat" --enable > "%OUTFILE%" 2>&1
if errorlevel 1 goto :fail
call "%SDK_HOME%\bin\jenesis-jdk.bat" --enable > "%OUTFILE%" 2>&1
if errorlevel 1 goto :fail
set "COUNT=0"
for /f %%c in ('findstr /x /c:"jenesis.toolchain.installer=jenesis-jdk" "%USERPROFILE%\.jenesis\jenesis.properties"') do set /a COUNT+=1
set "USERPROFILE=%SAVED_USERPROFILE%"
if not "!COUNT!"=="1" goto :fail
echo   ok

rmdir /s /q "%TMPDIR%" >nul 2>&1
echo sdk-tests: all checks passed
exit /b 0

:fail
echo sdk-tests: failure 1>&2
if exist "%OUTFILE%" type "%OUTFILE%" 1>&2
rmdir /s /q "%TMPDIR%" >nul 2>&1
exit /b 1
