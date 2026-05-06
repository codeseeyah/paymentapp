#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
SERVICE_DIR=$(cd "${SCRIPT_DIR}/.." && pwd)

DATABASE_URL=${DATABASE_URL:-postgresql://postgres:postgres@localhost:5432/paymentapp-test}
WORKER_BACKLOG_SIZE=${WORKER_BACKLOG_SIZE:-100}
WORKER_POLL_INTERVAL_SECONDS=${WORKER_POLL_INTERVAL_SECONDS:-1}
WORKER_MAX_WAIT_SECONDS=${WORKER_MAX_WAIT_SECONDS:-300}
WORKER_SEED_BATCH=${WORKER_SEED_BATCH:-$(date +%s)}
WORKER_RUN_LABEL=${WORKER_RUN_LABEL:-worker-outbox-metrics}

db_ready_deadline_epoch=$(( $(date +%s) + 120 ))
until psql "${DATABASE_URL}" -c 'SELECT 1' >/dev/null 2>&1; do
  if [[ $(date +%s) -ge ${db_ready_deadline_epoch} ]]; then
    echo "Timed out waiting for PostgreSQL to become ready" >&2
    exit 1
  fi
  sleep 1
done

cd "${SERVICE_DIR}"

psql "${DATABASE_URL}" \
  -v ON_ERROR_STOP=1 \
  -v seed_count="${WORKER_BACKLOG_SIZE}" \
  -v seed_batch="${WORKER_SEED_BATCH}" \
  -f "${SCRIPT_DIR}/sql/seed-worker-backlog.sql" >/dev/null

start_ms=$(date +%s%3N)
deadline_epoch=$(( $(date +%s) + WORKER_MAX_WAIT_SECONDS ))

while true; do
  verification_output=$(psql "${DATABASE_URL}" \
    -v ON_ERROR_STOP=1 \
    -t -A -F ' ' \
    -f "${SCRIPT_DIR}/sql/verify-worker-completion.sql")

  read -r completed_payments failed_payments done_outbox failed_outbox pending_outbox processing_outbox <<EOF
${verification_output}
EOF

  if [[ "${pending_outbox}" == "0" && "${processing_outbox}" == "0" ]]; then
    break
  fi

  if [[ $(date +%s) -ge ${deadline_epoch} ]]; then
    echo "Timed out waiting for outbox drain" >&2
    exit 1
  fi

  sleep "${WORKER_POLL_INTERVAL_SECONDS}"
done

end_ms=$(date +%s%3N)
drain_duration_ms=$((end_ms - start_ms))

WORKER_SEEDED_COUNT="${WORKER_BACKLOG_SIZE}" \
WORKER_RUN_LABEL="${WORKER_RUN_LABEL}" \
WORKER_COMPLETED_COUNT="${completed_payments}" \
WORKER_FAILED_COUNT="${failed_payments}" \
WORKER_DONE_OUTBOX_COUNT="${done_outbox}" \
WORKER_PENDING_OUTBOX_COUNT="${pending_outbox}" \
WORKER_PROCESSING_OUTBOX_COUNT="${processing_outbox}" \
WORKER_DRAIN_DURATION_MS="${drain_duration_ms}" \
k6 run "${SCRIPT_DIR}/k6-worker.js"