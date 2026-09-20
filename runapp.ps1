function runapp {
    $pkg    = "com.swiftapp"
    $apk    = ".\app\build\outputs\apk\debug\app-debug.apk"
    $adbDir = "$env:LOCALAPPDATA\Android\Sdk\platform-tools"

    # adb PATH mein add karo (sirf agar pehle se na ho)
    if ((Test-Path $adbDir) -and ($env:PATH -notlike "*$adbDir*")) {
        $env:PATH = "$adbDir;$env:PATH"
    }

    if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
        Write-Host "adb nahi mila. Android SDK ka platform-tools path check karo." -ForegroundColor Red
        return
    }

    if (-not (Test-Path ".\gradlew.bat")) {
        Write-Host "gradlew.bat nahi mila. Project root folder mein jaake chalao." -ForegroundColor Red
        return
    }

    # Device connected hai ya nahi
    $devices = adb devices | Select-String "\tdevice$"
    if (-not $devices) {
        Write-Host "Koi device/emulator connected nahi hai. USB debugging on karke 'adb devices' check karo." -ForegroundColor Red
        return
    }

    Write-Host "Building Swift..." -ForegroundColor Cyan
    .\gradlew.bat assembleDebug
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Build failed!" -ForegroundColor Red
        return
    }

    Write-Host "Installing APK..." -ForegroundColor Cyan
    adb install -r $apk
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Installation failed!" -ForegroundColor Red
        return
    }

    Write-Host "Launching Swift..." -ForegroundColor Cyan
    adb shell monkey -p $pkg -c android.intent.category.LAUNCHER 1 | Out-Null

    Write-Host "Swift launched successfully!" -ForegroundColor Green
}