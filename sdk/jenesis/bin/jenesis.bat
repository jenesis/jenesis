@echo off
setlocal EnableDelayedExpansion
set "SCRIPT_DIR=%~dp0"
for %%i in ("%~dp0..") do set "JENESIS_HOME=%%~fi"

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

if not defined VENDORED (
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

set "STAMP="
if exist "!VENDORED!\jenesis.version" (
    for /f "usebackq delims=" %%v in ("!VENDORED!\jenesis.version") do if not defined STAMP set "STAMP=%%v"
)
if not defined STAMP set "STAMP=!VERSION!"

set "TARGET_HOME="
if "!STAMP!"=="!VERSION!" (
    set "TARGET_HOME=%JENESIS_HOME%"
) else (
    for %%h in (
        "%USERPROFILE%\scoop\apps\jenesis\!STAMP!"
        "%JENESIS_HOME%\..\!STAMP!"
    ) do (
        if not defined TARGET_HOME (
            if exist "%%~h\bin\" set "TARGET_HOME=%%~fh"
        )
    )
)

if not defined TARGET_HOME (
    echo jenesis: no installed Jenesis matches !VENDORED!, building from its sources 1>&2
    goto :fromsource
)

set "REFERENCE="
for %%f in ("!TARGET_HOME!\sources\*-sources.jar") do if not defined REFERENCE set "REFERENCE=%%~ff"
if not defined REFERENCE (
    echo jenesis: !TARGET_HOME! ships no sources to verify against, building from !VENDORED! 1>&2
    goto :fromsource
)

set "EXTRACTED=%TEMP%\jenesis-verify-%RANDOM%%RANDOM%"
mkdir "!EXTRACTED!" 2>nul
pushd "!EXTRACTED!"
jar xf "!REFERENCE!" build/jenesis >nul 2>&1
popd
if not exist "!EXTRACTED!\build\jenesis\" (
    rmdir /s /q "!EXTRACTED!" 2>nul
    echo jenesis: could not read the sources of !TARGET_HOME!, building from !VENDORED! 1>&2
    goto :fromsource
)

set "DIGEST_A="
set "DIGEST_B="
for /f "usebackq delims=" %%d in (`powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $r=(Resolve-Path -LiteralPath '!VENDORED!').Path; $t=(Get-ChildItem -LiteralPath $r -Recurse -Filter *.java | ForEach-Object { $rel=$_.FullName.Substring($r.Length).Replace('\','/'); $h=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash; \"$h $rel\" } | Sort-Object) -join \"`n\"; $s=[IO.MemoryStream]::new([Text.Encoding]::UTF8.GetBytes($t)); (Get-FileHash -InputStream $s -Algorithm SHA256).Hash" 2^>nul`) do set "DIGEST_A=%%d"
for /f "usebackq delims=" %%d in (`powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $r=(Resolve-Path -LiteralPath '!EXTRACTED!\build\jenesis').Path; $t=(Get-ChildItem -LiteralPath $r -Recurse -Filter *.java | ForEach-Object { $rel=$_.FullName.Substring($r.Length).Replace('\','/'); $h=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash; \"$h $rel\" } | Sort-Object) -join \"`n\"; $s=[IO.MemoryStream]::new([Text.Encoding]::UTF8.GetBytes($t)); (Get-FileHash -InputStream $s -Algorithm SHA256).Hash" 2^>nul`) do set "DIGEST_B=%%d"
rmdir /s /q "!EXTRACTED!" 2>nul

if not defined DIGEST_A (
    echo jenesis: could not digest !VENDORED!, building from it 1>&2
    goto :fromsource
)
if not defined DIGEST_B (
    echo jenesis: could not digest the sources of !TARGET_HOME!, building from !VENDORED! 1>&2
    goto :fromsource
)
if not "!DIGEST_A!"=="!DIGEST_B!" (
    echo jenesis: !VENDORED! does not match the sources of Jenesis !STAMP!, building from it instead 1>&2
    goto :fromsource
)

if exist "!TARGET_HOME!\bin\jenesis-run.bat" (
    call "!TARGET_HOME!\bin\jenesis-run.bat" %*
    exit /b !errorlevel!
)
call "!TARGET_HOME!\bin\jenesis.bat" %*
exit /b !errorlevel!

:fromsource
if not exist "!VENDORED!\Project.java" (
    echo jenesis: !VENDORED! carries no Project.java, so there is nothing to run from source 1>&2
    exit /b 1
)
set "JAVA="
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" set "JAVA=%JAVA_HOME%\bin\java.exe"
)
if not defined JAVA (
    where java >nul 2>&1
    if errorlevel 1 (
        echo jenesis: no Java runtime found - set JAVA_HOME or add 'java' to PATH ^(Java 25 or newer required^) 1>&2
        exit /b 1
    )
    set "JAVA=java"
)
"!JAVA!" %JAVA_OPTS% "!VENDORED!\Project.java" %*
exit /b !errorlevel!
