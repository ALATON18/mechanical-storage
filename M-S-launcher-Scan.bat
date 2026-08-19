@echo off
setlocal EnableExtensions

title Mechanical Storage - Update, Build and Launch

set "REPO_URL=https://github.com/ALATON18/mechanical-storage.git"
set "DEFAULT_BRANCH=experiment/network-scan"
set "LAUNCHER_DIR=%~dp0"

rem If this launcher is inside the repository, use that repository.
rem Otherwise, keep the repository in a mechanical-storage folder beside it.
if exist "%LAUNCHER_DIR%.git\" (
	set "PROJECT_DIR=%LAUNCHER_DIR%"
) else (
	set "PROJECT_DIR=%LAUNCHER_DIR%mechanical-storage"
)

echo.
echo ============================================================
echo              Mechanical Storage Dev Launcher
echo ============================================================
echo.

where git >nul 2>&1
if errorlevel 1 (
	echo ERROR: Git was not found.
	echo Install Git for Windows, then run this launcher again.
	goto :failed
)

where java >nul 2>&1
if errorlevel 1 (
	echo ERROR: Java was not found.
	echo Mechanical Storage requires JDK 21.
	goto :failed
)

if not exist "%PROJECT_DIR%\.git\" (
	echo Repository not found. Cloning %DEFAULT_BRANCH%...
	echo Location: %PROJECT_DIR%
	echo.
	git clone --branch "%DEFAULT_BRANCH%" --single-branch "%REPO_URL%" "%PROJECT_DIR%"
	if errorlevel 1 (
		echo.
		echo ERROR: Git could not clone the repository.
		goto :failed
	)
)

cd /d "%PROJECT_DIR%"
if errorlevel 1 (
	echo ERROR: Could not open the project folder:
	echo %PROJECT_DIR%
	goto :failed
)

if not exist "gradlew.bat" (
	echo ERROR: gradlew.bat was not found in:
	echo %PROJECT_DIR%
	goto :failed
)

for /f "delims=" %%B in ('git branch --show-current') do set "CURRENT_BRANCH=%%B"
if not defined CURRENT_BRANCH (
	echo ERROR: Could not determine the current Git branch.
	goto :failed
)

echo Project: %PROJECT_DIR%
echo Branch:  %CURRENT_BRANCH%
echo.

for /f "delims=" %%S in ('git status --porcelain --untracked-files=no') do set "TRACKED_CHANGES=1"
if defined TRACKED_CHANGES (
	echo ERROR: The project has uncommitted changes to tracked files.
	echo Nothing was pulled so your work has not been overwritten.
	echo Commit or discard those changes, then run this launcher again.
	echo.
	git status --short
	goto :failed
)

echo [1/3] Pulling the latest changes...
git pull --ff-only
if errorlevel 1 (
	echo.
	echo ERROR: Git could not fast-forward the current branch.
	echo No merge, reset or automatic stash was attempted.
	goto :failed
)

echo.
echo [2/3] Building Mechanical Storage...
call gradlew.bat build
if errorlevel 1 (
	echo.
	echo ERROR: The Gradle build failed. Review the errors above.
	goto :failed
)

echo.
echo [3/3] Starting the Minecraft development client...
echo Close Minecraft normally when you are finished testing.
echo.
call gradlew.bat runClient
if errorlevel 1 (
	echo.
	echo ERROR: The development client stopped with an error.
	goto :failed
)

echo.
echo Minecraft closed normally.
pause
exit /b 0

:failed
echo.
echo The launcher stopped safely.
pause
exit /b 1
