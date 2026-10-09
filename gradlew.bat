@echo off
setlocal
set "APP_HOME=%~dp0"
set "WRAPPER_DIR=%APP_HOME%gradle\wrapper"
set "WRAPPER_JAR=%WRAPPER_DIR%\gradle-wrapper.jar"
set "VENDORED_JAR=%APP_HOME%third_party\shizuku-plus\gradle\wrapper\gradle-wrapper.jar"

if not exist "%WRAPPER_JAR%" (
  if not exist "%WRAPPER_DIR%" mkdir "%WRAPPER_DIR%"
  copy /Y "%VENDORED_JAR%" "%WRAPPER_JAR%" >nul
)

if defined JAVA_HOME (
  set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
) else (
  set "JAVA_EXE=java.exe"
)

"%JAVA_EXE%" -Dorg.gradle.appname=gradlew -classpath "%WRAPPER_JAR%" org.gradle.wrapper.GradleWrapperMain %*
exit /b %ERRORLEVEL%

