@echo off
setlocal EnableDelayedExpansion
set "SCRIPT_DIR=%~dp0"
for %%i in ("%~dp0..") do set "JENESIS_HOME=%%~fi"

set "VENDORED="
set "DIR=%CD%"
:findvendored
if exist "!DIR!\build\jenesis\" (
    set "ROOT=!DIR!"
    set "VENDORED=!DIR!\build\jenesis"
    goto :vendoreddone
)
for %%i in ("!DIR!\..") do set "PARENT=%%~fi"
if "!PARENT!"=="!DIR!" goto :vendoreddone
set "DIR=!PARENT!"
goto :findvendored
:vendoreddone

if not defined VENDORED (
    call "%SCRIPT_DIR%jenesis-make.bat" %*
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
if defined STAMP set "STAMP=!STAMP:"=!"
if not defined STAMP set "STAMP=!VERSION!"
if not defined STAMP (
    set "REASON=!VENDORED! records no version in jenesis.version and %JENESIS_HOME% ships no sources to assume one from"
    goto :refuse
)

set "TARGET_HOME="
if "!STAMP!"=="!VERSION!" (
    set "TARGET_HOME=%JENESIS_HOME%"
) else (
    call :find
    if not defined TARGET_HOME (
        where scoop >nul 2>&1
        if not errorlevel 1 (
            echo jenesis: build/jenesis records !STAMP!, installing that version via Scoop 1>&2
            call scoop install jenesis@!STAMP! 1>&2
            call :find
        )
    )
)

if not defined TARGET_HOME (
    set "REASON=no installed Jenesis matches the !STAMP! that !VENDORED! records, and it could not be installed - install it with 'scoop install jenesis@!STAMP!' or from https://jenesis.build"
    goto :refuse
)

set "REFERENCE="
for %%f in ("!TARGET_HOME!\sources\*-sources.jar") do if not defined REFERENCE set "REFERENCE=%%~ff"
if not defined REFERENCE (
    set "REASON=Jenesis !STAMP! at !TARGET_HOME! ships no sources jar, so !VENDORED! cannot be verified against it"
    goto :refuse
)

set "EXTRACTED=%TEMP%\jenesis-verify-%RANDOM%%RANDOM%"
mkdir "!EXTRACTED!" 2>nul
pushd "!EXTRACTED!"
jar xf "!REFERENCE!" build/jenesis >nul 2>&1
popd
if not exist "!EXTRACTED!\build\jenesis\" (
    rmdir /s /q "!EXTRACTED!" 2>nul
    set "REASON=the sources of Jenesis !STAMP! in !REFERENCE! could not be read, so !VENDORED! cannot be verified against them"
    goto :refuse
)

set "DIGEST_A="
set "DIGEST_B="
for /f "usebackq delims=" %%d in (`powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $r=(Resolve-Path -LiteralPath '!VENDORED!').Path; $t=(Get-ChildItem -LiteralPath $r -Recurse -Filter *.java | ForEach-Object { $rel=$_.FullName.Substring($r.Length).Replace('\','/'); $h=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash; \"$h $rel\" } | Sort-Object) -join \"`n\"; $s=[IO.MemoryStream]::new([Text.Encoding]::UTF8.GetBytes($t)); (Get-FileHash -InputStream $s -Algorithm SHA256).Hash" 2^>nul`) do set "DIGEST_A=%%d"
for /f "usebackq delims=" %%d in (`powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $r=(Resolve-Path -LiteralPath '!EXTRACTED!\build\jenesis').Path; $t=(Get-ChildItem -LiteralPath $r -Recurse -Filter *.java | ForEach-Object { $rel=$_.FullName.Substring($r.Length).Replace('\','/'); $h=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash; \"$h $rel\" } | Sort-Object) -join \"`n\"; $s=[IO.MemoryStream]::new([Text.Encoding]::UTF8.GetBytes($t)); (Get-FileHash -InputStream $s -Algorithm SHA256).Hash" 2^>nul`) do set "DIGEST_B=%%d"
rmdir /s /q "!EXTRACTED!" 2>nul

if not defined DIGEST_A (
    set "REASON=!VENDORED! could not be digested, so it cannot be verified"
    goto :refuse
)
if not defined DIGEST_B (
    set "REASON=the sources of Jenesis !STAMP! could not be digested, so !VENDORED! cannot be verified against them"
    goto :refuse
)
if not "!DIGEST_A!"=="!DIGEST_B!" (
    set "REASON=!VENDORED! does not match the sources of Jenesis !STAMP!"
    goto :refuse
)

if not exist "!TARGET_HOME!\bin\jenesis-make.bat" (
    set "REASON=Jenesis !STAMP! at !TARGET_HOME! ships no bin\jenesis-make.bat to dispatch to"
    goto :refuse
)
call "!TARGET_HOME!\bin\jenesis-make.bat" %*
exit /b !errorlevel!

:find
for %%h in (
    "%USERPROFILE%\scoop\apps\jenesis\!STAMP!"
    "%JENESIS_HOME%\..\!STAMP!"
) do (
    if not defined TARGET_HOME (
        if exist "%%~h\bin\" set "TARGET_HOME=%%~fh"
    )
)
exit /b 0

:refuse
echo jenesis: !REASON! 1>&2
echo jenesis: refusing to run - this launcher only executes a released Jenesis whose published 1>&2
echo     sources match !VENDORED! exactly, so that no unreviewed build code ever runs under it. 1>&2
echo. 1>&2
echo The released engine still builds this project as a standard build, which is often all a 1>&2
echo project needs. Neither of these executes the vendored build code: 1>&2
echo. 1>&2
echo     jenesis-make [selectors]         run the installed engine as it stands 1>&2
echo     scoop install jenesis@!STAMP!   install and switch to the version the project records 1>&2
echo. 1>&2
echo On a POSIX shell, '. jenesis-switch' switches to the recorded version in one step. 1>&2
echo. 1>&2
echo Only where the project truly needs its own engine, run the vendored sources yourself, from 1>&2
echo the project root at !ROOT!. Read the project's build instructions first: Make.java is 1>&2
echo the usual entry point, but a project that vendors a changed engine states why it does, and 1>&2
echo may drive it from one of its own. 1>&2
echo. 1>&2
echo     java build\jenesis\Make.java [selectors] 1>&2
echo. 1>&2
echo Source mode recompiles the engine on every invocation. To pay that once instead: 1>&2
echo. 1>&2
echo     javac build\jenesis\Make.java 1>&2
echo     java build.jenesis.Project [selectors] 1>&2
echo. 1>&2
echo Warning: those commands execute unreviewed code with the rights of your build and can 1>&2
echo break the encapsulation the released engine gives you. Only run builds from sources you 1>&2
echo trust. 1>&2
exit /b 1
