@echo off
cd /d "%~dp0"
where javac >nul 2>nul
if errorlevel 1 (
  echo Java JDK was not found. Install JDK 11 or newer and add it to PATH.
  pause
  exit /b 1
)
if not exist out mkdir out
javac -encoding UTF-8 -cp "lib\*" -d out src\*.java
if errorlevel 1 (
  echo Compilation failed. Check the error messages above.
  pause
  exit /b 1
)
java -cp "out;lib\*" Main
pause
