param(
    [string]$MinecraftVersion = "1.21.11",
    [switch]$ForceDownload,
    [switch]$ForceRebuild
)

. (Join-Path $PSScriptRoot "common.ps1")

$javaHome = Use-Java21
$repoRoot = Get-RepoRoot
$runtimeDir = Join-Path $PSScriptRoot "runtime"
$pluginsDir = Join-Path $runtimeDir "plugins"
$dropinsDir = Join-Path $PSScriptRoot "dropins"
$mockPluginRoot = Join-Path $repoRoot "devtools\\mock-playervaultx"
$mockBuildDir = Join-Path $mockPluginRoot "build"
$gradleWrapper = Join-Path $repoRoot "gradlew.bat"
$paperJar = Join-Path $runtimeDir "paper-server.jar"
$mockPluginJar = Join-Path $pluginsDir "PlayerVaultsX-dev-mock.jar"

New-Item -ItemType Directory -Force -Path $runtimeDir, $pluginsDir, $dropinsDir | Out-Null

$dropinsReadme = Join-Path $dropinsDir "README.txt"
if (-not (Test-Path $dropinsReadme)) {
    @"
Place any licensed server jars here that you want copied into the local Paper server.

Examples:
- PlayerVaultsX.jar
- PlayerVaultsGUI.jar
- Vault.jar

If a real PlayerVaults/PlayerVaultsX jar is present here, the local mock plugin will be skipped automatically.
"@ | Set-Content -Path $dropinsReadme -Encoding UTF8
}

$paperInfo = Get-PaperBuildInfo -MinecraftVersion $MinecraftVersion
if ($ForceDownload -or -not (Test-Path $paperJar)) {
    Write-Host "Downloading Paper $MinecraftVersion build $($paperInfo.BuildId)..."
    Invoke-WebRequest -Headers $paperInfo.Headers -Uri $paperInfo.DownloadUrl -OutFile $paperJar
}

$dropinJars = Get-ChildItem $dropinsDir -Filter *.jar -ErrorAction SilentlyContinue
foreach ($jar in $dropinJars) {
    Copy-Item $jar.FullName (Join-Path $pluginsDir $jar.Name) -Force
}

$hasRealPlayerVaultsJar = $dropinJars | Where-Object { $_.Name -match '(?i)^playervaults(x)?(?!.*gui).+\.jar$|(?i)^playervaults(x)?\.jar$' }
if ($hasRealPlayerVaultsJar) {
    if (Test-Path $mockPluginJar) {
        Remove-Item $mockPluginJar -Force
    }
    Write-Host "Detected a real PlayerVaults jar in dropins; skipping the mock plugin build."
} else {
    if (-not (Test-Path $gradleWrapper)) {
        throw "Gradle wrapper not found at $gradleWrapper."
    }

    if ($ForceRebuild -and (Test-Path $mockBuildDir)) {
        Remove-Item $mockBuildDir -Recurse -Force
    }

    $gradleTasks = @("jar")
    if ($ForceRebuild) {
        $gradleTasks = @("clean", "jar")
    }

    Write-Host "Building mock PlayerVaultsX plugin with Gradle..."
    & $gradleWrapper -p $mockPluginRoot @gradleTasks "--console=plain" "-PpaperApiVersion=$MinecraftVersion-R0.1-SNAPSHOT"
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle failed while building the mock PlayerVaultsX plugin."
    }

    $builtMockPluginJar = Join-Path $mockBuildDir "libs\\PlayerVaultsX-dev-mock.jar"
    if (-not (Test-Path $builtMockPluginJar)) {
        throw "Expected built mock plugin jar at $builtMockPluginJar."
    }

    Copy-Item $builtMockPluginJar $mockPluginJar -Force
}

$eulaPath = Join-Path $runtimeDir "eula.txt"
if (-not (Test-Path $eulaPath)) {
    "eula=true" | Set-Content -Path $eulaPath -Encoding ASCII
}

$serverPropertiesPath = Join-Path $runtimeDir "server.properties"
if (-not (Test-Path $serverPropertiesPath)) {
    @"
accepts-transfers=false
allow-flight=true
allow-nether=true
broadcast-console-to-ops=true
difficulty=easy
enable-command-block=true
enable-query=false
enable-rcon=false
enforce-secure-profile=false
gamemode=creative
generate-structures=false
hardcore=false
level-name=devworld
max-players=8
motd=Legends Addon Dev Server
online-mode=false
player-idle-timeout=0
pvp=false
server-ip=
server-port=25566
spawn-animals=false
spawn-monsters=false
spawn-protection=0
view-distance=8
"@ | Set-Content -Path $serverPropertiesPath -Encoding ASCII
}

Write-Host "Dev server setup complete."
Write-Host "Java Home: $javaHome"
Write-Host "Runtime Dir: $runtimeDir"
Write-Host "Paper Jar: $paperJar"
Write-Host "Plugins Dir: $pluginsDir"
