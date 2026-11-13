[CmdletBinding()]
param(
    [switch]$RemoveDeviceData
)

$ErrorActionPreference = "Stop"
$principal = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw "Uninstaller harus dijalankan sebagai Administrator."
}

$serviceName = "KabenSatuDataAgent"
$installRoot = Join-Path $env:ProgramFiles "KabenSatuData\Agent"
$dataRoot = Join-Path $env:ProgramData "KabenSatuData\Agent"

Get-Process -Name "KabenSatuData.Agent.Companion" -ErrorAction SilentlyContinue | Stop-Process -Force
if (Get-Service -Name $serviceName -ErrorAction SilentlyContinue) {
    Stop-Service -Name $serviceName -Force -ErrorAction SilentlyContinue
    sc.exe delete $serviceName | Out-Null
}

Remove-ItemProperty `
    -Path "HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\Run" `
    -Name "KabenSatuDataAgentCompanion" `
    -ErrorAction SilentlyContinue

if (Test-Path $installRoot) {
    Remove-Item $installRoot -Recurse -Force
}
if ($RemoveDeviceData -and (Test-Path $dataRoot)) {
    Remove-Item $dataRoot -Recurse -Force
}

Write-Host "KabenSatuData Agent telah dihapus. Data enrollment dipertahankan kecuali -RemoveDeviceData digunakan."
