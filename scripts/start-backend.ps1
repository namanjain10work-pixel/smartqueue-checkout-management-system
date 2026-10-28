[CmdletBinding()]
param(
    [ValidateSet('H2', 'Postgres')][string]$Database = 'H2',
    [switch]$EnableMl
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$keys = @('SPRING_PROFILES_ACTIVE', 'SMARTQUEUE_DB_URL', 'SMARTQUEUE_DB_USERNAME', 'SMARTQUEUE_DB_PASSWORD', 'SMARTQUEUE_ML_ENABLED', 'SMARTQUEUE_ML_BASE_URL')
$previous = @{}
foreach ($key in $keys) { $previous[$key] = [Environment]::GetEnvironmentVariable($key, 'Process') }
Push-Location (Join-Path $projectRoot 'backend')
try {
    $env:SMARTQUEUE_ML_ENABLED = $EnableMl.IsPresent.ToString().ToLowerInvariant()
    $env:SMARTQUEUE_ML_BASE_URL = 'http://127.0.0.1:8001'
    if ($Database -eq 'Postgres') {
        $env:SPRING_PROFILES_ACTIVE = 'postgres'
        if (-not $env:SMARTQUEUE_DB_URL) { $env:SMARTQUEUE_DB_URL = 'jdbc:postgresql://localhost:5432/smartqueue' }
        if (-not $env:SMARTQUEUE_DB_USERNAME) { $env:SMARTQUEUE_DB_USERNAME = 'postgres' }
        if (-not $env:SMARTQUEUE_DB_PASSWORD) {
            $secret = Read-Host 'PostgreSQL password' -AsSecureString
            $env:SMARTQUEUE_DB_PASSWORD = [System.Net.NetworkCredential]::new('', $secret).Password
        }
    } else {
        Remove-Item Env:SPRING_PROFILES_ACTIVE -ErrorAction SilentlyContinue
    }
    Write-Host "Starting backend: database=$Database, ML=$($EnableMl.IsPresent). Keep this terminal open."
    & .\mvnw.cmd spring-boot:run
    if ($LASTEXITCODE -ne 0) { throw "Maven exited with code $LASTEXITCODE" }
    Write-Host 'Backend process ended. Check startup logs if this was unexpected.'
} finally {
    foreach ($key in $keys) { [Environment]::SetEnvironmentVariable($key, $previous[$key], 'Process') }
    Pop-Location
}
