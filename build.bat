@echo off
setlocal

REM === CONFIGURATION - UPDATE THESE PATHS ===
REM Starsector 0.98a-RC8 uses Java 17
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot
set STARSECTOR_CORE=C:\Program Files (x86)\Fractal Softworks\Starsector\starsector-core

REM === Build paths ===
set SRC_DIR=src
set OUT_DIR=build
set JAR_DIR=jars

REM Check if JAVA_HOME exists
if not exist "%JAVA_HOME%\bin\javac.exe" (
    echo ERROR: javac not found at %JAVA_HOME%
    echo Please update JAVA_HOME in this script to point to your JDK 7 installation
    pause
    exit /b 1
)

REM Clean and create build directory
if exist "%OUT_DIR%" rmdir /s /q "%OUT_DIR%"
mkdir "%OUT_DIR%"

echo Compiling Java sources...
"%JAVA_HOME%\bin\javac" -source 17 -target 17 ^
    -cp "%STARSECTOR_CORE%\starfarer.api.jar;%STARSECTOR_CORE%\lwjgl.jar;%STARSECTOR_CORE%\lwjgl_util.jar;%STARSECTOR_CORE%\log4j-1.2.9.jar" ^
    -d "%OUT_DIR%" ^
    %SRC_DIR%\tacticaloverhaul\*.java

if errorlevel 1 (
    echo.
    echo BUILD FAILED - Compilation errors above
    pause
    exit /b 1
)

echo Creating JAR file...
"%JAVA_HOME%\bin\jar" cf "%JAR_DIR%\TacticalOverhaul.jar" -C "%OUT_DIR%" .

if errorlevel 1 (
    echo.
    echo BUILD FAILED - Could not create JAR
    pause
    exit /b 1
)

echo.
echo BUILD SUCCESSFUL!
echo JAR created at: %JAR_DIR%\TacticalOverhaul.jar
echo.
echo You can now enable the mod in Starsector's launcher.
pause
