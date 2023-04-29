@echo off

setlocal EnableDelayedExpansion

if not exist samt-wrapper.properties (
  echo "samt-wrapper.properties not found."
  exit /b 1
)

for /f "tokens=1,2 delims==" %%G in (samt-wrapper.properties) do (
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

if not exist .samt\cli mkdir .samt\cli

if not exist .samt\.gitignore echo *> .samt\.gitignore

set "currentVersion=0.0.0"

if exist .samt\cli\version.txt (
  set /p currentVersion=<.samt\cli\version.txt
)

if "%currentVersion%" neq "%samtVersion%" (
  echo Downloading samt %samtVersion% from '%distributionUrl%'...
  WHERE /q curl
  if %ERRORLEVEL% EQU 0 (
    curl -L -o .samt\cli\cli.tar "%distributionUrl%" || (
      echo An error occurred while downloading '%distributionUrl%' archive using curl. >&2
      exit /b 1
    )

  ) else (
    echo samtw requires 'curl' to be installed. >&2
    exit /b 1
  )

  tar xf .samt\cli\cli.tar -C .samt\cli || (
    echo An error occurred while unpacking .samt\cli\cli.tar using tar. >&2
    exit /b 1
  )

  echo %samtVersion%> .samt\cli\version.txt
)

call ".samt\cli\cli-shadow\bin\cli.bat" %*
