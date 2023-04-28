@echo off

if not exist samt-wrapper.properties (
  echo "samt-wrapper.properties not found."
  exit /b 1
)

set /p samtVersion=<samt-wrapper.properties

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
  if exist %SystemRoot%\System32\curl.exe (
    curl -s -L "%distributionUrl%" | tar x -C .samt\cli || (
      echo An error occured while downloading '%distributionUrl%' archive using curl. >&2
      exit /b 1
    )
  ) else if exist %SystemRoot%\System32\wget.exe (
    wget -qO- "%distributionUrl%" | tar x -C .samt\cli || (
      echo An error occured while downloading '%distributionUrl%' archive using wget. >&2
      exit /b 1
    )
  ) else (
    echo samtw requires either 'curl' or 'wget' to be installed. >&2
    exit /b 1
  )
  echo %samtVersion% > .samt\cli\version.txt
)

call ".samt\cli\cli-shadow\bin\cli.bat" %*
