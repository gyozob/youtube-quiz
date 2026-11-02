<#
    setup-toolchain.ps1 - portable, no-admin Android toolchain for this project.

    Installs JDK 17, the Android SDK command-line tools, the Android SDK platform
    + build-tools + platform-tools (adb), and a Gradle distribution into a single
    self-contained folder. Nothing is written to Program Files or the registry
    (except an optional user-scope PATH update, which needs no admin).

    Re-running is safe: every step is skipped if its output already exists.
    Pass -Force to re-download and re-extract everything.

    The install location defaults to an "android-tools" folder beside this
    repository. Override it with -ToolsRoot, or by setting YTQUIZ_TOOLS_ROOT.
    An existing install is detected through ANDROID_HOME, so re-running picks
    up wherever the toolchain already lives.

    Usage:
        pwsh -File tools\setup-toolchain.ps1
        pwsh -File tools\setup-toolchain.ps1 -ToolsRoot "C:\somewhere\else"
        pwsh -File tools\setup-toolchain.ps1 -Force
#>
[CmdletBinding()]
param(
    [string] $ToolsRoot,
    [switch] $Force,
    [switch] $SkipPathUpdate
)

$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'toolchain.ps1')

# --- pinned versions -------------------------------------------------------
# Resolved against the upstream repositories on 2026-11-02.
$JdkUrl     = 'https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse'
$JdkDirName = 'jdk-17'
$CmdlineUrl = 'https://dl.google.com/android/repository/commandlinetools-win-16111833_latest.zip'
$GradleVer  = '9.8.2'
$GradleUrl  = "https://services.gradle.org/distributions/gradle-$GradleVer-bin.zip"
$AndroidApi = '37'

$ProjectRoot = Split-Path -Parent $PSScriptRoot
$ToolsRoot   = Resolve-ToolsRoot -Explicit $ToolsRoot -ProjectRoot $ProjectRoot
Write-Host "Toolchain location: $ToolsRoot" -ForegroundColor Cyan

$DownloadDir = Join-Path $ToolsRoot 'downloads'
$JdkHome     = Join-Path $ToolsRoot $JdkDirName
$SdkRoot     = Join-Path $ToolsRoot 'sdk'
$GradleHome  = Join-Path $ToolsRoot "gradle-$GradleVer"

function Write-Step { param($m) Write-Host "`n==> $m" -ForegroundColor Cyan }
function Write-Ok   { param($m) Write-Host "    [ok] $m" -ForegroundColor Green }
function Write-Skip { param($m) Write-Host "    [skip] $m" -ForegroundColor DarkGray }

function Get-Download {
    param([string] $Url, [string] $FileName)
    $dest = Join-Path $DownloadDir $FileName
    if ((Test-Path $dest) -and -not $Force) {
        Write-Skip "already downloaded: $FileName"
        return $dest
    }
    Write-Host "    downloading $FileName ..."
    # curl.exe is far faster than Invoke-WebRequest for large files.
    & curl.exe -L --fail --retry 3 --silent --show-error -o $dest $Url
    if ($LASTEXITCODE -ne 0) { throw "download failed: $Url" }
    $mb = [math]::Round((Get-Item $dest).Length / 1MB, 1)
    Write-Ok "downloaded $FileName ($mb MB)"
    return $dest
}

function Expand-To {
    param([string] $Zip, [string] $Dest)
    # ExtractToDirectory is an order of magnitude faster than Expand-Archive here.
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    [System.IO.Compression.ZipFile]::ExtractToDirectory($Zip, $Dest)
}

New-Item -ItemType Directory -Force -Path $ToolsRoot, $DownloadDir | Out-Null

# --- 1. JDK 17 -------------------------------------------------------------
Write-Step 'JDK 17 (Eclipse Temurin)'
if ((Test-Path (Join-Path $JdkHome 'bin\java.exe')) -and -not $Force) {
    Write-Skip "already installed at $JdkHome"
} else {
    if (Test-Path $JdkHome) { Remove-Item -Recurse -Force $JdkHome }
    $zip = Get-Download $JdkUrl 'temurin-jdk17-win-x64.zip'
    $tmp = Join-Path $ToolsRoot '_jdk_tmp'
    if (Test-Path $tmp) { Remove-Item -Recurse -Force $tmp }
    Expand-To $zip $tmp
    # The archive contains a single versioned root folder; flatten it.
    $inner = Get-ChildItem $tmp -Directory | Select-Object -First 1
    Move-Item $inner.FullName $JdkHome
    Remove-Item -Recurse -Force $tmp
    Write-Ok "installed $JdkHome"
}
$env:JAVA_HOME = $JdkHome
$env:PATH = "$JdkHome\bin;$env:PATH"
& "$JdkHome\bin\java.exe" -version 2>&1 | ForEach-Object { Write-Host "    $_" }

# --- 2. Android SDK command-line tools -------------------------------------
Write-Step 'Android SDK command-line tools'
$CmdlineLatest = Join-Path $SdkRoot 'cmdline-tools\latest'
if ((Test-Path (Join-Path $CmdlineLatest 'bin\sdkmanager.bat')) -and -not $Force) {
    Write-Skip "already installed at $CmdlineLatest"
} else {
    if (Test-Path $CmdlineLatest) { Remove-Item -Recurse -Force $CmdlineLatest }
    $zip = Get-Download $CmdlineUrl 'commandlinetools-win.zip'
    $tmp = Join-Path $ToolsRoot '_cmdline_tmp'
    if (Test-Path $tmp) { Remove-Item -Recurse -Force $tmp }
    Expand-To $zip $tmp
    # The zip unpacks to "cmdline-tools\"; sdkmanager requires it to live at
    # "<sdk>\cmdline-tools\latest\" or it cannot locate the SDK root.
    New-Item -ItemType Directory -Force -Path (Split-Path $CmdlineLatest) | Out-Null
    Move-Item (Join-Path $tmp 'cmdline-tools') $CmdlineLatest
    Remove-Item -Recurse -Force $tmp
    Write-Ok "installed $CmdlineLatest"
}

$env:ANDROID_HOME = $SdkRoot
$env:ANDROID_SDK_ROOT = $SdkRoot

# Recent command-line tools deprecate `sdkmanager` in favour of the `android`
# CLI. The two differ in package naming -- `platforms/android-36` rather than
# `platforms;android-36` -- and the old shim silently mis-parses the semicolon
# form, reporting success while installing nothing. Always use `android`.
$android = Join-Path $CmdlineLatest 'bin\android.exe'
if (-not (Test-Path $android)) { throw "android CLI not found at $android" }

# --- 3. SDK packages -------------------------------------------------------
Write-Step "Android SDK packages (platform-tools, android-$AndroidApi, build-tools)"

$installed = (& $android sdk list 2>$null) -join "`n"

# Resolve exact package names from the repository rather than hardcoding them:
# platform names carry a minor version (`android-37.0`) and build-tools get
# superseded, so composing the strings by hand goes stale.
function Resolve-Package {
    param([string] $Pattern, [string] $Regex)
    & $android sdk list --all $Pattern 2>$null |
        Select-String -Pattern $Regex -AllMatches |
        ForEach-Object { $_.Matches.Value } |
        Where-Object { $_ -notmatch '-(rc|beta|alpha)' } |
        Sort-Object -Unique |
        Select-Object -Last 1
}

$platform = Resolve-Package 'platforms*' "platforms/android-$AndroidApi(\.\d+)?\b"
$buildTools = Resolve-Package 'build-tools*' "build-tools/$AndroidApi(\.\d+)*\b"
if (-not $platform) { throw "no SDK platform found for API $AndroidApi" }
if (-not $buildTools) { throw "no build-tools found for API $AndroidApi" }

foreach ($p in @('platform-tools', $platform, $buildTools)) {
    if ($installed -match [regex]::Escape($p) -and -not $Force) {
        Write-Skip "already installed: $p"
        continue
    }
    Write-Host "    installing $p ..."
    & $android sdk install $p
    if ($LASTEXITCODE -ne 0) { throw "failed to install SDK package: $p" }
    Write-Ok "installed $p"
}

# Verify rather than trust the exit code -- see the semicolon note above.
$platformDir = Join-Path $SdkRoot ($platform -replace '/', '\')
foreach ($probe in @(
    (Join-Path $SdkRoot 'platform-tools\adb.exe'),
    (Join-Path $platformDir 'android.jar')
)) {
    if (-not (Test-Path $probe)) { throw "expected SDK file missing after install: $probe" }
}
Write-Ok 'SDK verified (adb + android.jar present)'

# --- 4. Gradle -------------------------------------------------------------
Write-Step "Gradle $GradleVer"
if ((Test-Path (Join-Path $GradleHome 'bin\gradle.bat')) -and -not $Force) {
    Write-Skip "already installed at $GradleHome"
} else {
    if (Test-Path $GradleHome) { Remove-Item -Recurse -Force $GradleHome }
    $zip = Get-Download $GradleUrl "gradle-$GradleVer-bin.zip"
    Expand-To $zip $ToolsRoot   # unpacks directly as gradle-<ver>\
    Write-Ok "installed $GradleHome"
}

# --- 5. Project wiring -----------------------------------------------------
Write-Step 'Project wiring'

# local.properties tells the Android Gradle plugin where the SDK is. It is
# machine-specific and git-ignored.
$localProps = Join-Path $ProjectRoot 'local.properties'
# A .properties file treats both ':' and '\' as escapes, so a Windows path has
# to have each one doubled or lint rejects the file.
$escapedSdk = ($SdkRoot -replace '\\', '\\') -replace ':', '\:'
"sdk.dir=$escapedSdk" | Set-Content -Path $localProps -Encoding ASCII -NoNewline
Write-Ok 'wrote local.properties'

# activate.ps1 puts the whole toolchain on PATH for one shell session.
$activate = Join-Path $PSScriptRoot 'activate.ps1'
$activateBody = @"
# Generated by setup-toolchain.ps1 - dot-source this to use the toolchain:
#     . .\tools\activate.ps1
`$env:JAVA_HOME        = '$JdkHome'
`$env:ANDROID_HOME     = '$SdkRoot'
`$env:ANDROID_SDK_ROOT = '$SdkRoot'
`$env:PATH = '$JdkHome\bin;$SdkRoot\platform-tools;$CmdlineLatest\bin;$GradleHome\bin;' + `$env:PATH
Write-Host 'youtube-quiz toolchain active (java, adb, sdkmanager, gradle on PATH)' -ForegroundColor Green
"@
$activateBody | Set-Content -Path $activate -Encoding UTF8
Write-Ok 'wrote tools/activate.ps1'

if (-not $SkipPathUpdate) {
    # User-scope environment variables: no admin required, picked up by new shells.
    [Environment]::SetEnvironmentVariable('JAVA_HOME', $JdkHome, 'User')
    [Environment]::SetEnvironmentVariable('ANDROID_HOME', $SdkRoot, 'User')
    [Environment]::SetEnvironmentVariable('ANDROID_SDK_ROOT', $SdkRoot, 'User')
    $userPath = [Environment]::GetEnvironmentVariable('PATH', 'User')
    if (-not $userPath) { $userPath = '' }
    foreach ($p in @("$JdkHome\bin", "$SdkRoot\platform-tools", "$CmdlineLatest\bin")) {
        if ($userPath -notlike "*$p*") { $userPath = "$p;$userPath" }
    }
    [Environment]::SetEnvironmentVariable('PATH', $userPath.TrimEnd(';'), 'User')
    Write-Ok 'user-scope JAVA_HOME / ANDROID_HOME / PATH updated (new terminals only)'
}

Write-Step 'Done'
Write-Host "    JAVA_HOME    $JdkHome"
Write-Host "    ANDROID_HOME $SdkRoot"
Write-Host "    GRADLE_HOME  $GradleHome"
Write-Host ''
Write-Host '    In this session:   . .\tools\activate.ps1'
Write-Host '    Then:              adb devices ; .\gradlew.bat assembleDebug'
