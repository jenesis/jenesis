@echo off
setlocal EnableDelayedExpansion

set "VERSION="
:arguments
if "%~1"=="" goto :parsed
if /i "%~1"=="--help" goto :usage
set "ARGUMENT=%~1"
if "!ARGUMENT:~0,1!"=="-" goto :misuse
if defined VERSION goto :misuse
set "VERSION=!ARGUMENT!"
shift
goto :arguments

:parsed
if not defined VERSION goto :misuse
echo !VERSION!| findstr /r /x "[1-9][0-9.]*[-a-zA-Z]*" >nul
if errorlevel 1 (
    echo jenesis-jdk: malformed version '!VERSION!' ^(expected ^<feature^>[.^<interim^>[.^<update^>...]][-^<word^>...], as 25, 25.0.3 or 25-temurin^) 1>&2
    exit /b 64
)
for /f "tokens=1 delims=-" %%n in ("!VERSION!") do set "NUMBERS=%%n"
for /f "tokens=1 delims=." %%f in ("!NUMBERS!") do set "FEATURE=%%f"
set "WORDS="
if not "!NUMBERS!"=="!VERSION!" set "WORDS=!VERSION:*-=!"

set "VENDOR="
set "EA="
set "JVMCI="
if defined WORDS set "WORDLIST=!WORDS:-= !"
if defined WORDS for %%w in (!WORDLIST!) do (
    call :word %%w
    if errorlevel 1 exit /b 2
)
if defined EA (
    echo jenesis-jdk: !VERSION! asks for an early-access build, which jenesis-jdk installs with SDKMAN only 1>&2
    exit /b 2
)
if defined JVMCI (
    if not defined VENDOR set "VENDOR=graalvm"
    if /i not "!VENDOR!"=="graalvm" (
        echo jenesis-jdk: !VERSION! names !VENDOR!, which builds no JDK with jvmci 1>&2
        exit /b 2
    )
)
if not defined VENDOR set "VENDOR=temurin"
set "APP=!VENDOR!!FEATURE!-jdk"

where scoop >nul 2>&1
if errorlevel 1 (
    echo jenesis-jdk: Scoop is not installed, and jenesis-jdk installs JDKs with Scoop on Windows - install it, or name a program of your own in jenesis.toolchain.installer 1>&2
    exit /b 1
)
if not "!NUMBERS!"=="!FEATURE!" (
    echo jenesis-jdk: Scoop installs the newest build of a package only, so it installs the newest !APP!, which Jenesis then checks against !VERSION! 1>&2
)
call scoop bucket list 2>nul | findstr /r /b /c:"java " >nul
if errorlevel 1 (
    call scoop bucket add java
    if errorlevel 1 (
        echo jenesis-jdk: Scoop could not add its java bucket 1>&2
        exit /b 1
    )
)
echo jenesis-jdk: installing java/!APP! with Scoop for !VERSION!
call scoop prefix !APP! >nul 2>&1
if errorlevel 1 (
    call scoop install java/!APP!
) else (
    call scoop update !APP!
)
if errorlevel 1 (
    echo jenesis-jdk: Scoop failed to install java/!APP! 1>&2
    exit /b 1
)
exit /b 0

:word
set "WORD=%~1"
for %%n in (temurin adoptium eclipse) do if /i "%WORD%"=="%%n" goto :vendor-temurin
for %%n in (zulu azul) do if /i "%WORD%"=="%%n" goto :vendor-zulu
for %%n in (corretto amazon) do if /i "%WORD%"=="%%n" goto :vendor-corretto
for %%n in (liberica bellsoft) do if /i "%WORD%"=="%%n" goto :vendor-liberica
for %%n in (graalvm community) do if /i "%WORD%"=="%%n" goto :vendor-graalvm
if /i "%WORD%"=="jvmci" (
    set "JVMCI=1"
    exit /b 0
)
if /i "%WORD%"=="ea" (
    set "EA=1"
    exit /b 0
)
for %%n in (lts ca inc corporation systems com se b) do if /i "%WORD%"=="%%n" exit /b 0
for %%n in (microsoft sapmachine sap semeru ibm oracle jetbrains) do if /i "%WORD%"=="%%n" (
    echo jenesis-jdk: Scoop's java bucket offers no JDK of %WORD% that jenesis-jdk installs 1>&2
    exit /b 2
)
echo jenesis-jdk: no vendor jenesis-jdk knows answers to '%WORD%' in %VERSION% 1>&2
exit /b 2

:vendor-temurin
call :vendor temurin
exit /b %errorlevel%
:vendor-zulu
call :vendor zulu
exit /b %errorlevel%
:vendor-corretto
call :vendor corretto
exit /b %errorlevel%
:vendor-liberica
call :vendor liberica
exit /b %errorlevel%
:vendor-graalvm
call :vendor graalvm
exit /b %errorlevel%

:vendor
if defined VENDOR if /i not "%VENDOR%"=="%~1" (
    echo jenesis-jdk: %VERSION% names two vendors, %VENDOR% and %~1 1>&2
    exit /b 2
)
set "VENDOR=%~1"
exit /b 0

:usage
echo Usage: jenesis-jdk ^<version^>
echo.
echo Installs a JDK for a Jenesis toolchain version, as 25, 25.0.3 or 25-temurin, with
echo Scoop from its java bucket, into the folder Scoop keeps its apps in, which Jenesis
echo searches by default. Where Scoop installed Jenesis, the jenesis command names
echo jenesis-jdk in JENESIS_TOOLCHAIN_INSTALLER for the run it starts, and that run
echo calls it back when no installed JDK matches jenesis.toolchain.version.
echo.
echo   --help    print this help
echo.
echo A word of the version names the vendor: temurin, zulu, corretto, liberica or
echo graalvm, and Temurin without one. Scoop installs the newest build of a feature
echo release only; Jenesis checks whatever was installed against the version before
echo it runs it.
exit /b 0

:misuse
call :usage 1>&2
exit /b 64
