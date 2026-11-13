[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern("^https://")]
    [string]$ServerUrl,
    [string]$EnrollmentToken
)

$ErrorActionPreference = "Stop"
$principal = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw "Installer harus dijalankan sebagai Administrator."
}

if ([string]::IsNullOrWhiteSpace($EnrollmentToken)) {
    $EnrollmentToken = Read-Host "Masukkan kode enrollment sekali pakai"
}
if ([string]::IsNullOrWhiteSpace($EnrollmentToken)) {
    throw "Kode enrollment wajib diisi."
}

$sourceRoot = Split-Path -Parent $PSScriptRoot
if (-not (Test-Path (Join-Path $sourceRoot "Service"))) {
    $sourceRoot = $PSScriptRoot
}

$installRoot = Join-Path $env:ProgramFiles "KabenSatuData\Agent"
$dataRoot = Join-Path $env:ProgramData "KabenSatuData\Agent"
$serviceName = "KabenSatuDataAgent"
$serviceExe = Join-Path $installRoot "Service\KabenSatuData.Agent.Service.exe"
$companionExe = Join-Path $installRoot "Companion\KabenSatuData.Agent.Companion.exe"
$sourceServiceExe = Join-Path $sourceRoot "Service\KabenSatuData.Agent.Service.exe"
$sourceCompanionExe = Join-Path $sourceRoot "Companion\KabenSatuData.Agent.Companion.exe"

if (-not (Test-Path $sourceServiceExe) -or -not (Test-Path $sourceCompanionExe)) {
    throw "Package Agent tidak lengkap. Folder Service dan Companion wajib berisi executable."
}

Get-Process -Name "KabenSatuData.Agent.Companion" -ErrorAction SilentlyContinue | Stop-Process -Force
if (Get-Service -Name $serviceName -ErrorAction SilentlyContinue) {
    Stop-Service -Name $serviceName -Force -ErrorAction SilentlyContinue
    sc.exe delete $serviceName | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Service Agent lama tidak dapat dihapus."
    }
    Start-Sleep -Seconds 2
}

New-Item -ItemType Directory -Path $installRoot -Force | Out-Null
New-Item -ItemType Directory -Path $dataRoot -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $dataRoot "work") -Force | Out-Null

Copy-Item (Join-Path $sourceRoot "Service") $installRoot -Recurse -Force
Copy-Item (Join-Path $sourceRoot "Companion") $installRoot -Recurse -Force

icacls.exe $dataRoot /inheritance:r /grant:r "SYSTEM:(OI)(CI)F" "BUILTIN\Administrators:(OI)(CI)F" | Out-Null
icacls.exe (Join-Path $dataRoot "work") /grant "BUILTIN\Users:(OI)(CI)M" | Out-Null

$EnrollmentToken | & $serviceExe enroll --server $ServerUrl
if ($LASTEXITCODE -ne 0) {
    throw "Enrollment Agent gagal. Service belum dipasang."
}

sc.exe create $serviceName binPath= "`"$serviceExe`"" start= auto DisplayName= "KabenSatuData Agent" | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "Windows Service Agent tidak dapat dibuat."
}
sc.exe description $serviceName "Managed device Agent resmi KabenSatuData untuk perangkat Puskesmas." | Out-Null
sc.exe failure $serviceName reset= 86400 actions= restart/5000/restart/15000/restart/60000 | Out-Null

$runKey = "HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\Run"
New-ItemProperty -Path $runKey -Name "KabenSatuDataAgentCompanion" -Value "`"$companionExe`"" -PropertyType String -Force | Out-Null

Start-Service -Name $serviceName
Start-Process -FilePath $companionExe

Write-Host "KabenSatuData Agent berhasil dipasang dan dijalankan." -ForegroundColor Green
