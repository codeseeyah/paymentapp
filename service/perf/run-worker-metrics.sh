#!/usr/bin/env bash
set -euo pipefail

echo "Running worker metrics tests..."

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
first_completion_ms=""
deadline_epoch=$(( $(date +%s) + WORKER_MAX_WAIT_SECONDS ))
poll_count=0

while true; do
  verification_output=$(psql "${DATABASE_URL}" \
    -v ON_ERROR_STOP=1 \
    -t -A -F ' ' \
    -f "${SCRIPT_DIR}/sql/verify-worker-completion.sql")

  read -r completed_payments failed_payments done_outbox failed_outbox pending_outbox processing_outbox <<EOF
${verification_output}
EOF

  poll_count=$((poll_count + 1))

  # Record first completion time
  if [[ -z "${first_completion_ms}" ]] && [[ "${completed_payments}" -gt 0 ]]; then
    first_completion_ms=$(date +%s%3N)
  fi

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
first_completion_duration_ms=$((${first_completion_ms:-$end_ms} - start_ms))

# Calculate derived metrics
throughput=$(echo "scale=2; ${completed_payments} * 1000 / ${drain_duration_ms}" | bc)
success_rate=$(echo "scale=2; ${completed_payments} * 100 / ${WORKER_BACKLOG_SIZE}" | bc)
total_failed=$((failed_payments + failed_outbox))

WORKER_SEEDED_COUNT="${WORKER_BACKLOG_SIZE}" \
WORKER_RUN_LABEL="${WORKER_RUN_LABEL}" \
WORKER_COMPLETED_COUNT="${completed_payments}" \
WORKER_FAILED_COUNT="${failed_payments}" \
WORKER_FAILED_OUTBOX_COUNT="${failed_outbox}" \
WORKER_DONE_OUTBOX_COUNT="${done_outbox}" \
WORKER_PENDING_OUTBOX_COUNT="${pending_outbox}" \
WORKER_PROCESSING_OUTBOX_COUNT="${processing_outbox}" \
WORKER_DRAIN_DURATION_MS="${drain_duration_ms}" \
WORKER_FIRST_COMPLETION_MS="${first_completion_duration_ms}" \
WORKER_POLL_COUNT="${poll_count}" \
WORKER_THROUGHPUT="${throughput}" \
WORKER_SUCCESS_RATE="${success_rate}" \
WORKER_TOTAL_FAILED="${total_failed}" \
k6 run "${SCRIPT_DIR}/k6-worker.js"