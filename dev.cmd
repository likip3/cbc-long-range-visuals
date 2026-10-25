@echo off
setlocal
cd /d "%~dp0"
if not exist "%~dp0.tools\java21\bin\javac.exe" (
    echo Project-local JDK 21 is missing. Run setup-dev.cmd first.
    exit /b 1
)
set "JAVA_HOME=%~dp0.tools\java21"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "GRADLE_USER_HOME=%~dp0.gradle-user-home"
call "%~dp0gradlew.bat" %*
exit /b %errorlevel%

