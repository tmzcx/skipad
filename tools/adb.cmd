@echo off
rem adb wrapper: points TMP to the workspace, otherwise adb cannot create
rem %ROOT%
rem usage: tools\adb.cmd devices -l
setlocal
set "ROOT=%~dp0.."
if not exist "%ROOT%\.tmp" mkdir "%ROOT%\.tmp"
set "TMP=%ROOT%\.tmp"
set "TEMP=%ROOT%\.tmp"
"%ROOT%\.android-sdk\platform-tools\adb.exe" %*
exit /b %ERRORLEVEL%
