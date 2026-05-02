Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$serviceDir = Split-Path -Parent $PSScriptRoot
Set-Location $serviceDir

docker compose -f docker-compose.scale.yml up --build --abort-on-container-exit k6
