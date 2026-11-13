[CmdletBinding()]
param(
    [ValidateSet("win-x64", "win-arm64")]
    [string]$Runtime = "win-x64",
    [ValidateSet("Debug", "Release")]
    [string]$Configuration = "Release"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$artifacts = Join-Path $root "artifacts"
$package = Join-Path $artifacts "package-$Runtime"
$tests = Join-Path $root "tests/KabenSatuData.Agent.Tests/KabenSatuData.Agent.Tests.csproj"
$service = Join-Path $root "src/KabenSatuData.Agent.Service/KabenSatuData.Agent.Service.csproj"
$companion = Join-Path $root "src/KabenSatuData.Agent.Companion/KabenSatuData.Agent.Companion.csproj"

dotnet restore $tests
dotnet test $tests --configuration $Configuration --no-restore
dotnet restore $service --runtime $Runtime
dotnet restore $companion --runtime $Runtime

if (Test-Path $package) {
    Remove-Item $package -Recurse -Force
}

$serviceOutput = Join-Path $package "Service"
$companionOutput = Join-Path $package "Companion"
New-Item -ItemType Directory -Path $serviceOutput -Force | Out-Null
New-Item -ItemType Directory -Path $companionOutput -Force | Out-Null

dotnet publish $service `
    --configuration $Configuration `
    --runtime $Runtime `
    --self-contained true `
    --no-restore `
    --output $serviceOutput

dotnet publish $companion `
    --configuration $Configuration `
    --runtime $Runtime `
    --self-contained true `
    --no-restore `
    --output $companionOutput

Copy-Item (Join-Path $PSScriptRoot "install.ps1") $package
Copy-Item (Join-Path $PSScriptRoot "uninstall.ps1") $package
Copy-Item (Join-Path $root "README.md") $package

$archive = Join-Path $artifacts "KabenSatuDataAgent-$Runtime.zip"
if (Test-Path $archive) {
    Remove-Item $archive -Force
}
Compress-Archive -Path (Join-Path $package "*") -DestinationPath $archive -CompressionLevel Optimal
Get-FileHash $archive -Algorithm SHA256 | Format-List
Write-Host "Package selesai: $archive"
