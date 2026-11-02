<#
    Shared toolchain location logic, dot-sourced by the other scripts.

    Nothing here assumes a particular drive or folder name: the location is
    resolved, in order, from an explicit argument, an environment variable, an
    existing installation, and finally a default alongside the repository.
#>

function Resolve-ToolsRoot {
    param(
        # -ToolsRoot as passed on the command line, if any.
        [string] $Explicit,
        # The repository root, used for the repo-relative default.
        [Parameter(Mandatory)] [string] $ProjectRoot
    )

    if ($Explicit) { return $Explicit }

    # Lets a machine pin the location without editing any script.
    if ($env:YTQUIZ_TOOLS_ROOT) { return $env:YTQUIZ_TOOLS_ROOT }

    # setup-toolchain.ps1 points ANDROID_HOME at <root>\sdk, so an existing
    # install tells us where it already lives.
    if ($env:ANDROID_HOME -and (Test-Path $env:ANDROID_HOME)) {
        return (Split-Path -Parent $env:ANDROID_HOME)
    }

    # Default: a sibling of the checkout, so the toolchain follows the project
    # rather than a hardcoded path.
    return (Join-Path (Split-Path -Parent $ProjectRoot) 'android-tools')
}

function Get-ToolchainPaths {
    param([Parameter(Mandatory)] [string] $ToolsRoot)

    $sdk = Join-Path $ToolsRoot 'sdk'
    [pscustomobject]@{
        Root        = $ToolsRoot
        JdkHome     = Join-Path $ToolsRoot 'jdk-17'
        SdkRoot     = $sdk
        Adb         = Join-Path $sdk 'platform-tools\adb.exe'
        CmdlineBin  = Join-Path $sdk 'cmdline-tools\latest\bin'
        AndroidCli  = Join-Path $sdk 'cmdline-tools\latest\bin\android.exe'
    }
}
