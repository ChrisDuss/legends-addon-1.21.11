param(
    [string]$MinecraftVersion = "1.21.11",
    [switch]$SkipSetup
)

. (Join-Path $PSScriptRoot "common.ps1")

if (-not $SkipSetup) {
    & (Join-Path $PSScriptRoot "setup-dev-server.ps1") -MinecraftVersion $MinecraftVersion
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
}

$javaHome = Use-Java21
$runtimeDir = Join-Path $PSScriptRoot "runtime"
$paperJar = Join-Path $runtimeDir "paper-server.jar"

if (-not (Test-Path $paperJar)) {
    throw "Paper jar not found at $paperJar. Run setup-dev-server.ps1 first."
}

Push-Location $runtimeDir
try {
    & (Join-Path $javaHome "bin\\java.exe") -Xms1G -Xmx1G -jar $paperJar --nogui
} finally {
    Pop-Location
}
