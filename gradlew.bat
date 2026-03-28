@if "%DEBUG%"=="" @echo off
set DIRNAME=%~dp0
set CLASSPATH=%DIRNAME%\gradle\wrapper\gradle-wrapper.jar
set JAVA_EXE=%JAVA_HOME%\bin\java.exe
if not exist "%JAVA_EXE%" set JAVA_EXE=java
"%JAVA_EXE%" -Xmx64m -Xms64m -Dorg.gradle.appname=%~n0 -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
