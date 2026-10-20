@echo off
rem ============================================================
rem  One-click Shizuku restart (no USB cable needed)
rem  1) phone and PC on the same Wi-Fi
rem  2) turn on "Wireless debugging" in Developer options
rem     (MIUI turns it off after reboot, turn it on once)
rem  The port is discovered automatically via mDNS.
rem ============================================================
setlocal enabledelayedexpansion
set "ROOT=%~dp0.."
set "ADB=%ROOT%\.android-sdk\platform-tools\adb.exe"
set "TMP=%ROOT%\.tmp"
if not exist "%TMP%" mkdir "%TMP%"
set "TARGET="

echo [1/4] discovering phone ...
"%ADB%" mdns services > "%TMP%\mdns.txt" 2>nul
for /f "tokens=1,2,3" %%a in ('findstr /c:"_adb-tls-connect" "%TMP%\mdns.txt" 2^>nul') do set "TARGET=%%c"
if not defined TARGET set "TARGET=192.168.1.12:46573"
echo     target = !TARGET!

echo [2/4] connecting ...
"%ADB%" connect !TARGET! > "%TMP%\connect.txt" 2>&1
type "%TMP%\connect.txt"
set "DEV=-s !TARGET!"
"%ADB%" %DEV% get-state >nul 2>&1
if errorlevel 1 (
  set "DEV="
  echo     wireless not available, fallback to USB
)

echo [3/4] locating Shizuku starter ...
set "APK="
"%ADB%" %DEV% shell pm path moe.shizuku.privileged.api > "%TMP%\path.txt" 2>nul
for /f "delims=" %%L in ('type "%TMP%\path.txt"') do set "APK=%%L"
if "!APK!"=="" (
  echo     Shizuku not found, or phone not reachable.
  echo     Turn on Wireless debugging on the phone, then run this again.
  pause
  exit /b 1
)
set "APK=!APK:package:=!"
set "STARTER=!APK:/base.apk=/lib/arm64/libshizuku.so!"
echo     !STARTER!

echo [4/4] starting Shizuku ...
"%ADB%" %DEV% shell "!STARTER!"
echo.
echo Done. Open Shizuku on the phone; it should say "running".
pause
