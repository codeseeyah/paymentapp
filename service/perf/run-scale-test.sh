#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
SERVICE_DIR=$(cd "${SCRIPT_DIR}/.." && pwd)

cd "${SERVICE_DIR}"

docker compose -f docker-compose.scale.yml up --build --abort-on-container-exit k6
