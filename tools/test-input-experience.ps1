[CmdletBinding()]
param([string]$Serial, [string]$TestClass)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$repositoryRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$sdkSetting = Get-Content (Join-Path $repositoryRoot "local.properties") |
    Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
if (-not $sdkSetting) { throw "Configure sdk.dir in local.properties before running device tests." }
$sdkRoot = $sdkSetting.Substring($sdkSetting.IndexOf('=') + 1).Replace('\:', ':').Replace('\\', '\')
$adb = Join-Path $sdkRoot "platform-tools/adb.exe"

function Invoke-Adb {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)
    $output = @(& $adb -s $Serial @Arguments 2>&1 | ForEach-Object { $_.ToString() })
    if ($LASTEXITCODE -ne 0) { throw "ADB command failed: $($Arguments[0])" }
    return $output
}

$devices = @(& $adb devices | Select-String '^(\S+)\s+device$' | ForEach-Object { $_.Matches[0].Groups[1].Value })
if ($LASTEXITCODE -ne 0) { throw "Unable to list ADB devices." }
if (-not $Serial) {
    if ($devices.Count -ne 1) { throw "Connect one device or provide -Serial." }
    $Serial = $devices[0]
}
if ($Serial -notin $devices) { throw "The selected device is not connected and authorized." }
$abi = @(Invoke-Adb @("shell", "getprop", "ro.product.cpu.abi"))[0].Trim()
if ($abi -notin @("arm64-v8a", "armeabi-v7a", "x86_64")) { throw "Unsupported test ABI." }

Push-Location $repositoryRoot
try {
    & ./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest -PrequireRime=true --no-parallel "-Pandroid.injected.build.abi=$abi"
    if ($LASTEXITCODE -ne 0) { throw "Device test APK build failed." }
    $appDirectory = Join-Path $repositoryRoot "app/build/intermediates/apk/debug"
    $testDirectory = Join-Path $repositoryRoot "app/build/intermediates/apk/androidTest/debug"
    $appMetadata = Get-Content (Join-Path $appDirectory "output-metadata.json") -Raw | ConvertFrom-Json
    $testMetadata = Get-Content (Join-Path $testDirectory "output-metadata.json") -Raw | ConvertFrom-Json
    $appArtifact = @($appMetadata.elements | Where-Object { $_.filters.value -contains $abi })
    if ($appArtifact.Count -ne 1 -or @($testMetadata.elements).Count -ne 1) { throw "Unexpected test APK outputs." }
    Invoke-Adb @("install", "-r", "-t", (Join-Path $appDirectory $appArtifact[0].outputFile))
    Invoke-Adb @("install", "-r", "-t", (Join-Path $testDirectory $testMetadata.elements[0].outputFile))

    $originalMethod = @(Invoke-Adb @("shell", "settings", "get", "secure", "default_input_method"))[0].Trim()
    $changedMethod = $false
    try {
        if ($originalMethod -like 'dev.zeroinput.ime*') {
            $alternate = Invoke-Adb @("shell", "ime", "list", "-s") |
                Where-Object { $_ -and $_ -notlike 'dev.zeroinput.ime*' -and $_ -notmatch 'VoiceInput' } |
                Select-Object -First 1
            if (-not $alternate) { throw "Enable a system keyboard so tests can isolate the native runtime." }
            Invoke-Adb @("shell", "ime", "set", $alternate.Trim())
            $changedMethod = $true
        }
        $instrumentArguments = @("shell", "am", "instrument", "-w")
        if ($TestClass) { $instrumentArguments += @("-e", "class", $TestClass) }
        $instrumentArguments += "dev.zeroinput.ime.debug.test/androidx.test.runner.AndroidJUnitRunner"
        $result = Invoke-Adb $instrumentArguments
        $result | Write-Output
        if (($result -join "`n") -notmatch 'OK \(\d+ tests?\)' -or ($result -join "`n") -match 'FAILURES!!!') {
            throw "Device regression tests failed."
        }
    } finally {
        if ($changedMethod) { Invoke-Adb @("shell", "ime", "set", $originalMethod) }
    }
} finally {
    Pop-Location
}
