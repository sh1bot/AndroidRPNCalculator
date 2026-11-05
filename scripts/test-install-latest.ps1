# SPDX-License-Identifier: Apache-2.0
# Exercise the actual install script using mocked GH and ADB commands; no phone or network.
$ErrorActionPreference = 'Stop'
$global:ExactRpnTestInstalled = $false
$global:ExactRpnTestOpened = $false
function global:gh {
    $arguments = @($args)
    $global:LASTEXITCODE = 0
    if ($arguments[0] -eq 'run' -and $arguments[1] -eq 'list') {
        ConvertTo-Json -InputObject @(@{ databaseId=123; number=42; headSha='test-commit' }) -Compress
    } elseif ($arguments[0] -eq 'run' -and $arguments[1] -eq 'download') {
        $nameIndex = [Array]::IndexOf($arguments, '--name')
        $dirIndex = [Array]::IndexOf($arguments, '--dir')
        if ($nameIndex -lt 0 -or $arguments[$nameIndex+1] -ne 'ExactRPN') { throw 'Wrong artifact name requested' }
        Set-Content -LiteralPath (Join-Path $arguments[$dirIndex+1] 'ExactRPN.apk') -Value 'APK fixture'
    } else { throw "Unexpected GH invocation: $arguments" }
}
function global:Test-ExactRpnAdb {
    $arguments = @($args)
    $global:LASTEXITCODE = 0
    if ($arguments[0] -eq 'get-state') { 'device' }
    elseif ($arguments[0] -eq 'install') {
        if ($arguments[1] -ne '-r' -or -not (Test-Path -LiteralPath $arguments[2])) { throw 'Missing APK or update flag' }
        if ((Split-Path -Leaf $arguments[2]) -ne 'ExactRPN.apk') { throw 'Wrong APK filename' }
        $global:ExactRpnTestInstalled = $true
    } elseif ($arguments[0] -eq 'shell') {
        if ($arguments -contains 'start') { $global:ExactRpnTestOpened = $true }
    } else { throw "Unexpected ADB invocation: $arguments" }
}
try {
    $workflow = Get-Content -Raw (Join-Path $PSScriptRoot '../.github/workflows/android.yml')
    if ([regex]::Matches($workflow, '(?m)^\s+name: ExactRPN\s*$').Count -ne 2) { throw 'Workflow upload/download artifact names disagree' }
    & (Join-Path $PSScriptRoot 'install-latest.ps1') -Repository 'test/project' -AdbPath 'Test-ExactRpnAdb'
    if (-not $global:ExactRpnTestInstalled -or -not $global:ExactRpnTestOpened) { throw 'Install flow did not complete' }
    Write-Host 'Phone install flow passed.'
} finally {
    Remove-Item Function:\gh,Function:\Test-ExactRpnAdb
    Remove-Variable ExactRpnTestInstalled,ExactRpnTestOpened -Scope Global
}
