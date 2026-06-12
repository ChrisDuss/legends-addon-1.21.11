Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Get-RepoRoot {
    return [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
}

function Get-JavaHome {
    if ($env:JAVA_HOME) {
        $javaExe = Join-Path $env:JAVA_HOME "bin\\java.exe"
        if (Test-Path $javaExe) {
            return $env:JAVA_HOME
        }
    }

    $candidates = @()
    $adoptiumRoot = "C:\\Program Files\\Eclipse Adoptium"
    if (Test-Path $adoptiumRoot) {
        $candidates += Get-ChildItem $adoptiumRoot -Directory | Sort-Object Name -Descending | Select-Object -ExpandProperty FullName
    }

    $javaRoot = "C:\\Program Files\\Java"
    if (Test-Path $javaRoot) {
        $candidates += Get-ChildItem $javaRoot -Directory | Sort-Object Name -Descending | Select-Object -ExpandProperty FullName
    }

    foreach ($candidate in $candidates) {
        $javaExe = Join-Path $candidate "bin\\java.exe"
        $javacExe = Join-Path $candidate "bin\\javac.exe"
        if ((Test-Path $javaExe) -and (Test-Path $javacExe) -and $candidate -match "21|22|23|24|25") {
            return $candidate
        }
    }

    throw "Java 21+ was not found. Install a JDK 21+ or set JAVA_HOME first."
}

function Use-Java21 {
    $javaHome = Get-JavaHome
    $env:JAVA_HOME = $javaHome
    $env:Path = (Join-Path $javaHome "bin") + ";" + $env:Path
    return $javaHome
}

function Get-PaperBuildInfo {
    param(
        [Parameter(Mandatory = $true)]
        [string]$MinecraftVersion
    )

    $headers = @{
        "User-Agent" = "legends-addon-dev-server/1.0 ([email protected])"
    }

    $builds = Invoke-RestMethod -Headers $headers -Uri "https://fill.papermc.io/v3/projects/paper/versions/$MinecraftVersion/builds"
    $stable = $builds | Where-Object { $_.channel -eq "STABLE" } | Select-Object -First 1
    if ($null -eq $stable) {
        throw "No stable Paper build found for Minecraft $MinecraftVersion."
    }

    return [PSCustomObject]@{
        Headers     = $headers
        Version     = $MinecraftVersion
        DownloadUrl = $stable.downloads.'server:default'.url
        BuildId     = $stable.id
    }
}
