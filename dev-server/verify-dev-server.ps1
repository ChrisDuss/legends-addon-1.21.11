param(
    [string]$MinecraftVersion = "1.21.11",
    [int]$TimeoutSeconds = 120
)

. (Join-Path $PSScriptRoot "common.ps1")

& (Join-Path $PSScriptRoot "setup-dev-server.ps1") -MinecraftVersion $MinecraftVersion

$javaHome = Use-Java21
$runtimeDir = Join-Path $PSScriptRoot "runtime"
$paperJar = Join-Path $runtimeDir "paper-server.jar"
$latestLog = Join-Path $runtimeDir "logs\\latest.log"

if (-not (Test-Path $paperJar)) {
    throw "Paper jar not found at $paperJar. Run setup-dev-server.ps1 first."
}

foreach ($path in @($latestLog)) {
    if (Test-Path $path) {
        Remove-Item $path -Force
    }
}

$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = Join-Path $javaHome "bin\\java.exe"
$psi.WorkingDirectory = $runtimeDir
$psi.Arguments = "-Xms512M -Xmx1G -jar `"$paperJar`" --nogui"
$psi.UseShellExecute = $false
$psi.RedirectStandardInput = $false
$psi.RedirectStandardOutput = $false
$psi.RedirectStandardError = $false
$psi.CreateNoWindow = $true

$process = New-Object System.Diagnostics.Process
$process.StartInfo = $psi
$process.Start() | Out-Null

$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
$booted = $false
while ((Get-Date) -lt $deadline) {
    Start-Sleep -Milliseconds 500
    if (Test-Path $latestLog) {
        $content = Get-Content $latestLog -Raw
        if ($content -match "Done \(") {
            $booted = $true
            break
        }
    }

    if ($process.HasExited) {
        break
    }
}

if (-not $process.HasExited) {
    $process.Kill()
    $process.WaitForExit(30000) | Out-Null
}

if (-not $booted) {
    throw "Paper server did not finish booting within $TimeoutSeconds seconds. Check $latestLog."
}

Write-Host "Paper server booted successfully and was stopped cleanly."
