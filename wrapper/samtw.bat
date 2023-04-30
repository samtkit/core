@echo off

setlocal EnableDelayedExpansion

if not exist .samt\samt-wrapper.properties (
  echo ".samt\samt-wrapper.properties not found."
  exit /b 1
)

for /f "tokens=1,2 delims==" %%G in (.samt\samt-wrapper.properties) do (
    if "%%G"=="samtVersion" (
        set "samtVersion=%%H"
    )
    if "%%G"=="distributionUrl" (
        set distributionUrlPattern=%%H
    )
)

set distributionUrl=%distributionUrlPattern:$samtVersion=!samtVersion!%
set distributionUrl=%distributionUrl:\:=:%

where tar >nul || (
  echo "This script requires 'tar' to be installed." >&2
  exit /b 1
)

if not exist .samt\wrapper mkdir .samt\wrapper

set "currentVersion=0.0.0"

if exist .samt\wrapper\version.txt (
  set /p currentVersion=<.samt\wrapper\version.txt
)

if "%currentVersion%" neq "%samtVersion%" (
  echo Downloading samt %samtVersion% from '%distributionUrl%'...
  WHERE /q curl
  if %ERRORLEVEL% NEQ 0 (
    echo samtw requires 'curl' to be installed. >&2
    exit /b 1
  )

  curl -L -o .samt\wrapper\cli.tar "%distributionUrl%" || (
    echo An error occurred while downloading '%distributionUrl%' archive using curl. >&2
    exit /b 1
  )

  tar xf .samt\wrapper\cli.tar -C .samt\wrapper || (
    echo An error occurred while unpacking .samt\wrapper\cli.tar using tar. >&2
    exit /b 1
  )

  echo %samtVersion%> .samt\wrapper\version.txt
)

call ".samt\wrapper\cli-shadow\bin\cli.bat" %*
