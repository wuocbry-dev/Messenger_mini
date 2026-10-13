#Requires -Version 5.1
<#
.SYNOPSIS
    Run the Messenger Mini Spring Boot backend with env vars from backend/.env.
.DESCRIPTION
    Reads backend/.env, exports each KEY=VALUE into the current process
    environment, then starts the Spring Boot app via Maven.
#>

$ErrorActionPreference = "Stop"

$projectRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$envFile     = Join-Path $projectRoot "backend\.env"

if (-not (Test-Path $envFile)) {
    Write-Error "Env file not found: $envFile"
    exit 1
}

# Load .env into current process (KEY=VALUE per line, # = comment, blank = skip)
Get-Content $envFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -eq "" -or $line.StartsWith("#")) { return }

    $idx = $line.IndexOf("=")
    if ($idx -lt 1) { return }

    $key   = $line.Substring(0, $idx).Trim()
    $value = $line.Substring($idx + 1).Trim()

    # Strip optional surrounding quotes
    if ($value.StartsWith('"') -and $value.EndsWith('"')) {
        $value = $value.Substring(1, $value.Length - 2)
    } elseif ($value.StartsWith("'") -and $value.EndsWith("'")) {
        $value = $value.Substring(1, $value.Length - 2)
    }

    Set-Item -Path "Env:$key" -Value $value
    Write-Host "[env] $key = $value"
}

# Sanity check the critical Agora vars
$required = @("AGORA_APP_ID", "AGORA_APP_CERTIFICATE", "DB_HOST", "JWT_SECRET")
foreach ($k in $required) {
    if ([string]::IsNullOrWhiteSpace((Get-Item "Env:$k" -ErrorAction SilentlyContinue).Value)) {
        Write-Warning "[env] $k is empty or missing in .env"
    }
}

Set-Location (Join-Path $projectRoot "backend")

Write-Host ""
Write-Host "==> Starting Spring Boot backend on http://localhost:8080" -ForegroundColor Cyan
Write-Host "==> Press Ctrl+C to stop." -ForegroundColor DarkGray
Write-Host ""

.\mvnw.cmd spring-boot:run
