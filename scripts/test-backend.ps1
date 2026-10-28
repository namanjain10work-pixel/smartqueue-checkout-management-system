[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$oldProfile = $env:SPRING_PROFILES_ACTIVE
$oldMl = $env:SMARTQUEUE_ML_ENABLED
Push-Location (Join-Path $projectRoot 'backend')
try {
    # Test resources supply the isolated in-memory H2 database.
    Remove-Item Env:SPRING_PROFILES_ACTIVE -ErrorAction SilentlyContinue
    $env:SMARTQUEUE_ML_ENABLED = 'false'
    & .\mvnw.cmd test
    if ($LASTEXITCODE -ne 0) { throw "Tests failed: Maven exit code $LASTEXITCODE" }
} finally {
    [Environment]::SetEnvironmentVariable('SPRING_PROFILES_ACTIVE', $oldProfile, 'Process')
    [Environment]::SetEnvironmentVariable('SMARTQUEUE_ML_ENABLED', $oldMl, 'Process')
    Pop-Location
}
