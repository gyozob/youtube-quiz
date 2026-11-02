<#
    run.ps1 - build, install on the connected phone, and tail the logs.

    The toolchain is located the same way setup-toolchain.ps1 installs it:
    -ToolsRoot, else YTQUIZ_TOOLS_ROOT, else ANDROID_HOME, else a folder beside
    this repository.

    Usage:
        pwsh -File tools\run.ps1             # build + install + logcat
        pwsh -File tools\run.ps1 -NoLogs     # build + install only
        pwsh -File tools\run.ps1 -Logs       # just tail the logs
#>
[CmdletBinding()]
param(
    [string] $ToolsRoot,
    [switch] $NoLogs,
    [switch] $Logs
)

$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'toolchain.ps1')

$ProjectRoot = Split-Path -Parent $PSScriptRoot
$Package = 'com.personal.youtubequiz'

$ToolsRoot = Resolve-ToolsRoot -Explicit $ToolsRoot -ProjectRoot $ProjectRoot
$tools = Get-ToolchainPaths -ToolsRoot $ToolsRoot
$adb = $tools.Adb
$env:JAVA_HOME = $tools.JdkHome
$env:ANDROID_HOME = $tools.SdkRoot

if (-not (Test-Path $adb)) {
    throw "adb not found at $adb. Run tools\setup-toolchain.ps1 first, or pass -ToolsRoot."
}

function Write-Step { param($m) Write-Host "`n==> $m" -ForegroundColor Cyan }

function Assert-Device {
    $lines = & $adb devices | Select-Object -Skip 1 | Where-Object { $_ -match '\S' }
    $ready = $lines | Where-Object { $_ -match '\sdevice$' }
    if (-not $ready) {
        Write-Host 'No authorised device. On the phone:' -ForegroundColor Yellow
        Write-Host '  1. Enable developer options (Xiaomi: tap "OS version" 7 times,'
        Write-Host '     stock Android: tap "Build number" 7 times)'
        Write-Host '  2. Developer options > USB debugging on'
        Write-Host '     Xiaomi also needs "Install via USB"'
        Write-Host '  3. Set the USB mode to File transfer, not Charging only'
        Write-Host '  4. Unlock the screen, replug, accept "Allow USB debugging"'
        Write-Host '  See the Phone setup section of README.md for the full walkthrough.'
        if ($lines) { Write-Host "adb currently sees:`n$($lines -join "`n")" }
        throw 'no device ready'
    }
    ($ready -split '\s+')[0]
}

if ($Logs) {
    Write-Step 'Logs (Ctrl+C to stop)'
    & $adb logcat -s YTQuiz:V
    return
}

Write-Step 'Build'
Push-Location $ProjectRoot
try {
    & (Join-Path $ProjectRoot 'gradlew.bat') assembleDebug
    if ($LASTEXITCODE -ne 0) { throw 'build failed' }
} finally {
    Pop-Location
}

$apk = Join-Path $ProjectRoot 'app\build\outputs\apk\debug\app-debug.apk'
if (-not (Test-Path $apk)) { throw "APK not found at $apk" }

Write-Step 'Install'
$serial = Assert-Device
Write-Host "    device $serial"
& $adb install -r $apk
if ($LASTEXITCODE -ne 0) { throw 'install failed' }

# Android 13+ greys out the accessibility toggle for sideloaded apps. Installing
# over adb usually avoids that, but clearing the app-op costs nothing if it was
# never set.
& $adb shell cmd appops set $Package ACCESS_RESTRICTED_SETTINGS allow 2>$null | Out-Null

Write-Step 'Device'
$release = (& $adb shell getprop ro.build.version.release).Trim()
$sdk = (& $adb shell getprop ro.build.version.sdk).Trim()
$model = (& $adb shell getprop ro.product.model).Trim()
Write-Host "    $model - Android $release (API $sdk)"
$youtube = & $adb shell pm list packages com.google.android.youtube
Write-Host "    YouTube installed: $([bool]$youtube)"

Write-Step 'Next'
Write-Host '    Open the app and grant everything on the Setup tab,'
Write-Host '    then use Diagnostics to find a working pause strategy.'

if (-not $NoLogs) {
    Write-Step 'Logs (Ctrl+C to stop)'
    & $adb logcat -c
    & $adb logcat -s YTQuiz:V
}
