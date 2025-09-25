@echo off
setlocal enabledelayedexpansion

for %%I in ("%~f0") do set BatchFileName=%%~nxI

for %%F in (*) do (
    if not "%%F"=="%BatchFileName%" del /f /q "%%F"
)

for /d %%D in (*) do rd /s /q "%%D"

echo Downloading repository...
curl -L -o repo.zip https://github.com/CoolPotato31F/Java-Graphics/archive/refs/heads/main.zip

echo Extracting repository...
powershell -Command "Expand-Archive -Path repo.zip -DestinationPath . -Force"

for /d %%D in ("Java-Graphics-main") do (
    move "%%D\*" .
    rd /s /q "%%D"
)

del repo.zip

echo Done!

pause
