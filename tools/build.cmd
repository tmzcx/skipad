@echo off
rem 用法: tools\build.cmd <工程目录> <gradle 任务...>
rem 例:   tools\build.cmd .tools\smoke :app:assembleDebug
rem 说明: 一次性设置好 JAVA_HOME / ANDROID_HOME / 缓存目录再调 gradle，
rem       不依赖 PowerShell 执行策略，也不污染用户目录。
setlocal

set "ROOT=%~dp0.."
set "JAVA_HOME=C:\Program Files\Android\openjdk\jdk-21.0.8"
set "ANDROID_HOME=%ROOT%\.android-sdk"
set "ANDROID_SDK_ROOT=%ANDROID_HOME%"
set "ANDROID_USER_HOME=%ROOT%\.android-home"
set "GRADLE_USER_HOME=%ROOT%\.gradle-home"

if "%~1"=="" (
  echo usage: build.cmd ^<project-dir^> ^<gradle tasks...^>
  exit /b 1
)

set "PROJ=%~1"
shift

set "ARGS="
:collect
if "%~1"=="" goto run
set "ARGS=%ARGS% %1"
shift
goto collect

:run
call "%ROOT%\.tools\gradle-9.8.1\bin\gradle.bat" -p "%PROJ%" %ARGS%
exit /b %ERRORLEVEL%
