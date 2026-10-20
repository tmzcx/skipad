# 用途：为 skipad 工程准备好构建环境（点号 source 进当前会话）
#   . <项目根目录>
#
# 注意：本机 PowerShell 执行策略是 Restricted，直接 source 会被拦。
#       要么临时放开：powershell -ExecutionPolicy Bypass -NoExit -File .\skipad\tools\env.ps1
#       要么直接用不需要策略的 tools\build.cmd。
#
# 说明：DSH 沙箱只允许写工作区，所以 SDK / Gradle 缓存 / Android 用户目录
#       全部指向 skipad\ 下，不碰 <项目根目录> 和 .android。

$root = Split-Path -Parent $PSScriptRoot

$env:JAVA_HOME         = 'C:\Program Files\Android\openjdk\jdk-21.0.8'
$env:ANDROID_HOME      = Join-Path $root '.android-sdk'
$env:ANDROID_SDK_ROOT  = $env:ANDROID_HOME
$env:ANDROID_USER_HOME = Join-Path $root '.android-home'
$env:GRADLE_USER_HOME  = Join-Path $root '.gradle-home'

$gradleBin     = Join-Path $root '.tools\gradle-9.8.1\bin'
$platformTools = Join-Path $env:ANDROID_HOME 'platform-tools'
$env:PATH = "$gradleBin;$platformTools;$env:PATH"

Write-Host "JAVA_HOME        = $env:JAVA_HOME"
Write-Host "ANDROID_HOME     = $env:ANDROID_HOME"
Write-Host "GRADLE_USER_HOME = $env:GRADLE_USER_HOME"
Write-Host "gradle           = $gradleBin\gradle.bat  (9.8.1)"
Write-Host "adb              = $platformTools\adb.exe"
