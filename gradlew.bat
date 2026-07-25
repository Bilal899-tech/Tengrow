@rem SafeGuard Gradle wrapper
@if "%DEBUG%"=="" @set DEBUG=
@set APP_HOME=%CD%
@set WRAPPER_JAR=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar
@if exist "%JAVA_HOME%" set JAVA_HOME=%JAVA_HOME:"=%
@if exist "%JAVA_HOME%" set JAVA_EXE=%JAVA_HOME%\bin\java.exe
@if not exist "%JAVA_HOME%" set JAVA_EXE=java.exe
"%JAVA_EXE%" %DEBUG% -Dorg.gradle.appname=safeguard -classpath "%WRAPPER_JAR%" org.gradle.wrapper.GradleWrapperMain %*
