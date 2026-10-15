@echo off
rem Builds the app and puts the installable file, Budgie.apk, next to this script.
rem "build.bat test" runs the tests instead.
setlocal
set "JAVA_HOME=C:\AndroidDev\jdk"
set "ANDROID_HOME=C:\AndroidDev\sdk"
set "GRADLE_USER_HOME=C:\AndroidDev\gradle-home"
set "ANDROID_USER_HOME=C:\AndroidDev\android-user-home"
set "GRADLE=C:\AndroidDev\gradle\bin\gradle.bat"

if /i "%~1"=="test" (
    call "%GRADLE%" -p "%~dp0." testDebugUnitTest
    exit /b %errorlevel%
)

call "%GRADLE%" -p "%~dp0." assembleDebug
if errorlevel 1 exit /b 1
copy /y "%~dp0app\build\outputs\apk\debug\app-debug.apk" "%~dp0Budgie.apk" >nul
echo.
echo Built: %~dp0Budgie.apk
