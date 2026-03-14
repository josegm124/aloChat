@ECHO OFF
SETLOCAL

SET APP_HOME=%~dp0
SET CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar
IF "%GRADLE_USER_HOME%"=="" SET GRADLE_USER_HOME=%APP_HOME%\.gradle-home

IF NOT "%JAVA_HOME%"=="" (
  SET JAVA_EXE=%JAVA_HOME%\bin\java.exe
) ELSE (
  SET JAVA_EXE=java.exe
)

"%JAVA_EXE%" -Dorg.gradle.appname=gradlew -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
