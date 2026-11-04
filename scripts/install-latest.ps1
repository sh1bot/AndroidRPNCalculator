# SPDX-License-Identifier: Apache-2.0
<#
.SYNOPSIS
Download the latest successful main-branch APK, install over ADB, and open it.
.EXAMPLE
.\scripts\install-latest.ps1 -Watch
.EXAMPLE
.\scripts\install-latest.ps1 -Watch -Serial '192.168.1.20:37123'
#>
[CmdletBinding()]
param(
    [switch]$Watch,
    [string]$Serial,
    [ValidateRange(5,3600)][int]$IntervalSeconds = 15,
    [string]$Repository = 'sh1bot/AndroidRPNCalculator',
    [string]$AdbPath
)
$ErrorActionPreference = 'Stop'
if (-not (Get-Command gh -ErrorAction SilentlyContinue)) { throw 'Install GitHub CLI: winget install --exact --id GitHub.cli. Then run gh auth login.' }
if (-not $AdbPath) {
    $adbCommand = Get-Command adb -ErrorAction SilentlyContinue
    if ($adbCommand) { $AdbPath = $adbCommand.Source }
    else {
        $candidates = @()
        if ($env:ANDROID_HOME) { $candidates += Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe' }
        if ($env:LOCALAPPDATA) { $candidates += Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe' }
        $AdbPath = $candidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
    }
}
if (-not $AdbPath) { throw 'ADB not found. Install SDK Platform-Tools in Android Studio, or supply -AdbPath C:\path\to\adb.exe.' }
$deviceArgs = @()
if ($Serial) { $deviceArgs = @('-s', $Serial) }
$lastRunId = ''
Write-Host 'Looking for successful main builds. Connect/unlock your phone and authorize USB or wireless debugging.'
if ($Watch) { Write-Host 'Watching every 15 seconds by default. Ctrl+C stops. Updates will close and relaunch the calculator.' }
do {
    $json = & gh run list --repo $Repository --workflow android.yml --branch main --event push --status success --limit 1 --json databaseId,number,headSha
    if ($LASTEXITCODE -ne 0) { throw 'Could not list builds. Run gh auth login and check your connection.' }
    $runs = @($json | ConvertFrom-Json)
    if ($runs.Count -eq 0) {
        if (-not $Watch) { throw 'No successful main-branch builds available yet.' }
    } else {
        $run = $runs[0]
        if ([string]$run.databaseId -ne $lastRunId) {
            & $AdbPath @deviceArgs get-state
            if ($LASTEXITCODE -ne 0) { throw 'Phone is not connected/authorized, or multiple devices are connected. Check adb devices and use -Serial if needed.' }
            $downloadDir = Join-Path ([IO.Path]::GetTempPath()) ('ExactRPN-' + [Guid]::NewGuid().ToString('N'))
            New-Item -ItemType Directory -Path $downloadDir | Out-Null
            try {
                Write-Host "Downloading build $($run.number), commit $($run.headSha)..."
                & gh run download ([string]$run.databaseId) --repo $Repository --name "ExactRPN-debug-$($run.number)" --dir $downloadDir
                if ($LASTEXITCODE -ne 0) { throw 'Artifact download failed. It may have expired; start a new GitHub build.' }
                $apk = Join-Path $downloadDir 'app-debug.apk'
                if (-not (Test-Path -LiteralPath $apk)) { throw 'Expected app-debug.apk was not present in the artifact.' }
                & $AdbPath @deviceArgs install -r $apk
                if ($LASTEXITCODE -ne 0) {
                    throw 'Installation failed. If ADB reports UPDATE_INCOMPATIBLE, the signing key changed. Configure the permanent signing secrets, then uninstall the old app ONCE (this clears its data). This script never uninstalls automatically.'
                }
                & $AdbPath @deviceArgs shell am force-stop dev.exactrpn
                if ($LASTEXITCODE -ne 0) { throw 'Could not stop the previous app process.' }
                & $AdbPath @deviceArgs shell am start -W -n dev.exactrpn/.MainActivity
                if ($LASTEXITCODE -ne 0) { throw 'Installed APK but could not launch the app.' }
                $lastRunId = [string]$run.databaseId
                Write-Host "Installed and opened build $($run.number)."
            } finally {
                Remove-Item -LiteralPath $downloadDir -Recurse -Force
            }
        }
    }
    if ($Watch) { Start-Sleep -Seconds $IntervalSeconds }
} while ($Watch)
