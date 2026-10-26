@echo off
rem Builds a local TEST MSIX of SwiftClient with throwaway identity values (not for upload),
rem then installs and starts it via test-store-install.ps1 (needs Windows Developer Mode).
rem For the real Store package fill in build.appx in package.json and run: npm run dist:store
cd /d "%~dp0.."
call npx electron-builder --win appx -c.appx.identityName=SwiftSMP.SwiftClientTest "-c.appx.publisher=CN=SwiftSMPTest" -c.appx.publisherDisplayName=SwiftSMP > "%~dp0..\dist\store-test-build.log" 2>&1
echo exit %ERRORLEVEL% >> "%~dp0..\dist\store-test-build.log"
if "%1"=="install" powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0test-store-install.ps1"
