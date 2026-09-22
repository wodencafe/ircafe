param(
    [Parameter(Mandatory = $true)]
    [string]$MsiPath
)

# Run on a disposable Windows CI runner; installation is machine-wide.
$ErrorActionPreference = 'Stop'
$msi = (Resolve-Path $MsiPath).Path
$testRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("ircafe-msi-test-" + [guid]::NewGuid())
$installDir = Join-Path $testRoot 'IRCafe'
New-Item -ItemType Directory -Path $testRoot | Out-Null

function Invoke-MsiExec([string]$Arguments, [string]$LogPath) {
    $process = Start-Process msiexec.exe -ArgumentList "$Arguments /qn /norestart /L*v `"$LogPath`"" -Wait -PassThru
    if ($process.ExitCode -notin @(0, 3010)) {
        if (Test-Path $LogPath) {
            Get-Content $LogPath | Write-Host
        }
        throw "msiexec failed with exit code $($process.ExitCode). Log: $LogPath"
    }
}

$installed = $false
try {
    Invoke-MsiExec "/i `"$msi`" INSTALLDIR=`"$installDir`"" (Join-Path $testRoot 'install.log')
    $installed = $true
    foreach ($relativePath in @('IRCafe.exe', 'app\IRCafe.cfg', 'runtime\bin\server\jvm.dll')) {
        if (-not (Test-Path (Join-Path $installDir $relativePath))) {
            throw "Installed package is missing $relativePath"
        }
    }
    $startMenu = Join-Path ([Environment]::GetFolderPath('CommonPrograms')) 'IRCafe\IRCafe.lnk'
    if (-not (Test-Path $startMenu)) {
        throw "Installed package is missing the Start Menu shortcut: $startMenu"
    }
} finally {
    if ($installed) {
        Invoke-MsiExec "/x `"$msi`"" (Join-Path $testRoot 'uninstall.log')
    }
}

if (Test-Path (Join-Path $installDir 'IRCafe.exe')) {
    throw 'Uninstall left IRCafe.exe behind'
}
if (Test-Path $startMenu) {
    throw 'Uninstall left the Start Menu shortcut behind'
}
Write-Host "MSI install/uninstall smoke test passed. Logs: $testRoot"
