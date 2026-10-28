[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$python = Join-Path $projectRoot '.venv\Scripts\python.exe'
if (-not (Test-Path $python)) { throw 'Missing .venv. Follow the Python setup in README.md first.' }
$model = if ($env:SMARTQUEUE_MODEL_PATH) { $env:SMARTQUEUE_MODEL_PATH } else { Join-Path $projectRoot 'ml\checkout_model.joblib' }
if (-not (Test-Path $model)) { throw 'Model file missing. Run scripts/train_model.py as described in README.md.' }
Push-Location $projectRoot
try {
    Write-Host 'Starting ML service on 127.0.0.1:8001. Keep this terminal open.'
    & $python -m uvicorn app.ml_api:app --host 127.0.0.1 --port 8001
    if ($LASTEXITCODE -ne 0) { throw "Uvicorn exited with code $LASTEXITCODE" }
} finally { Pop-Location }
