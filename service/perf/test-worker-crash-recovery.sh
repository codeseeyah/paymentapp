#!/usr/bin/env bash
set -euo pipefail

##
# Test: Worker Crash and Recovery
# 
# This test verifies that when the worker process crashes in the middle of
# processing outbox entries, the system recovers and eventually completes all payments.
#
# Steps:
# 1. Start docker-compose with worker enabled
# 2. Create payment records via the producer API
# 3. Let worker claim some records
# 4. Kill the worker container (simulating a crash)
# 5. Verify outbox records are still pending/processing
# 6. Restart the worker container
# 7. Verify all records eventually complete
##

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
SERVICE_DIR=$(cd "${SCRIPT_DIR}/.." && pwd)

COMPOSE_FILE=${COMPOSE_FILE:-docker-compose.scale.test.yml}
DATABASE_URL=${DATABASE_URL:-postgresql://postgres:postgres@localhost:5432/paymentapp-test}
PRODUCER_COUNT=${PRODUCER_COUNT:-1}
WORKER_COUNT=${WORKER_COUNT:-1}
PAYMENT_COUNT=${PAYMENT_COUNT:-50}
CRASH_DELAY_SECONDS=${CRASH_DELAY_SECONDS:-3}
RECOVERY_MAX_WAIT_SECONDS=${RECOVERY_MAX_WAIT_SECONDS:-60}

echo "=========================================="
echo "Worker Crash & Recovery Test"
echo "=========================================="
echo "Setup:"
echo "  PAYMENT_COUNT: ${PAYMENT_COUNT}"
echo "  CRASH_DELAY_SECONDS: ${CRASH_DELAY_SECONDS}"
echo "  RECOVERY_MAX_WAIT_SECONDS: ${RECOVERY_MAX_WAIT_SECONDS}"
echo

cd "${SERVICE_DIR}"

# Function to cleanup docker-compose on exit
cleanup() {
  echo "Cleaning up docker containers..."
  docker compose -f "${COMPOSE_FILE}" down --volumes 2>/dev/null || true
}
trap cleanup EXIT

# Start docker-compose
echo "Starting docker-compose with ${PRODUCER_COUNT} producer(s) and ${WORKER_COUNT} worker(s)..."
docker compose -f "${COMPOSE_FILE}" up -d \
  --scale producer="${PRODUCER_COUNT}" \
  --scale worker="${WORKER_COUNT}" \
  postgres payment-simulator producer worker pgbouncer

# Wait for services to be ready
echo "Waiting for services to be ready..."
sleep 10

# Wait for database
db_ready_deadline=$(( $(date +%s) + 120 ))
until psql "${DATABASE_URL}" -c 'SELECT 1' >/dev/null 2>&1; do
  if [[ $(date +%s) -ge ${db_ready_deadline} ]]; then
    echo "ERROR: Timed out waiting for PostgreSQL" >&2
    exit 1
  fi
  sleep 1
done

# Clean database
echo "Cleaning database..."
psql "${DATABASE_URL}" -c "TRUNCATE TABLE outbox, payments CASCADE;" 2>/dev/null || true

# Wait for producer to be ready
echo "Waiting for producer API..."
producer_ready_deadline=$(( $(date +%s) + 120 ))
while [[ $(date +%s) -lt ${producer_ready_deadline} ]]; do
  if docker compose -f "${COMPOSE_FILE}" logs producer 2>/dev/null | grep -q "Started PaymentAppApplication"; then
    echo "  ✓ Producer API is ready"
    break
  fi
  sleep 2
done

if [[ $(date +%s) -ge ${producer_ready_deadline} ]]; then
  echo "ERROR: Producer API not ready after 120 seconds" >&2
  docker compose -f "${COMPOSE_FILE}" logs producer | tail -20
  exit 1
fi

echo

# Step 1: Create payments via producer API
echo "[STEP 1] Creating ${PAYMENT_COUNT} payments via producer API..."
for i in $(seq 1 "$PAYMENT_COUNT"); do
  (docker compose -f "${COMPOSE_FILE}" exec -T producer curl -s -X POST http://localhost:8080/payments \
    -H "Content-Type: application/json" \
    -d "{\"amount\": $((100 + i)), \"currency\": \"USD\", \"payerId\": \"payer-$i\", \"payeeId\": \"payee-$i\"}" \
    >/dev/null 2>&1) &
  
  # Batch requests to avoid overwhelming the system
  if [[ $((i % 5)) -eq 0 ]]; then
    wait
  fi
done
wait
echo "  ✓ Created ${PAYMENT_COUNT} payments"

# Query initial state
initial_outbox=$(psql "${DATABASE_URL}" -t -c "SELECT COUNT(*) FROM outbox" | xargs)
initial_pending=$(psql "${DATABASE_URL}" -t -c "SELECT COUNT(*) FROM outbox WHERE status = 'pending'" | xargs)
initial_processing=$(psql "${DATABASE_URL}" -t -c "SELECT COUNT(*) FROM outbox WHERE status = 'processing'" | xargs)

echo "  Initial outbox state: ${initial_outbox} total, ${initial_pending} pending, ${initial_processing} processing"
echo

# Step 2: Let worker process some records and then crash it
echo "[STEP 2] Worker processing (${CRASH_DELAY_SECONDS}s)..."
sleep "${CRASH_DELAY_SECONDS}"

# Query pre-crash state
pre_crash_outbox=$(psql "${DATABASE_URL}" -t -c "SELECT COUNT(*) FROM outbox" | xargs)
pre_crash_pending=$(psql "${DATABASE_URL}" -t -c "SELECT COUNT(*) FROM outbox WHERE status = 'pending'" | xargs)
pre_crash_processing=$(psql "${DATABASE_URL}" -t -c "SELECT COUNT(*) FROM outbox WHERE status = 'processing'" | xargs)
pre_crash_done=$(psql "${DATABASE_URL}" -t -c "SELECT COUNT(*) FROM outbox WHERE status = 'done'" | xargs)

echo "  Pre-crash state: ${pre_crash_pending} pending, ${pre_crash_processing} processing, ${pre_crash_done} done"
echo

# Step 3: Kill worker
echo "[STEP 3] Killing worker container(s)..."
docker compose -f "${COMPOSE_FILE}" stop worker 2>/dev/null || true
sleep 2
echo "  ✓ Worker killed"
echo

# Step 4: Verify crash - check that processing rows are still in database
echo "[STEP 4] Verifying crash state..."
post_crash_outbox=$(psql "${DATABASE_URL}" -t -c "SELECT COUNT(*) FROM outbox" | xargs)
post_crash_pending=$(psql "${DATABASE_URL}" -t -c "SELECT COUNT(*) FROM outbox WHERE status = 'pending'" | xargs)
post_crash_processing=$(psql "${DATABASE_URL}" -t -c "SELECT COUNT(*) FROM outbox WHERE status = 'processing'" | xargs)
post_crash_done=$(psql "${DATABASE_URL}" -t -c "SELECT COUNT(*) FROM outbox WHERE status = 'done'" | xargs)

echo "  Post-crash state: ${post_crash_pending} pending, ${post_crash_processing} processing, ${post_crash_done} done"

if [[ ${post_crash_processing} -gt 0 ]]; then
  echo "  ✓ Confirmed: ${post_crash_processing} record(s) stuck in processing state (from crashed worker)"
else
  echo "  ⚠ WARNING: No records in processing state - worker may not have processed anything"
fi
echo

# Step 5: Restart worker
echo "[STEP 5] Restarting worker container(s)..."
docker compose -f "${COMPOSE_FILE}" up -d --no-deps --scale worker="${WORKER_COUNT}" worker
sleep 3
echo "  ✓ Worker restarted"
echo

# Step 6: Wait for all records to complete
echo "[STEP 6] Waiting for all records to complete (max ${RECOVERY_MAX_WAIT_SECONDS}s)..."
deadline=$(( $(date +%s) + RECOVERY_MAX_WAIT_SECONDS ))
poll_count=0

while true; do
  poll_count=$((poll_count + 1))
  
  current_pending=$(psql "${DATABASE_URL}" -t -c "SELECT COUNT(*) FROM outbox WHERE status = 'pending'" | xargs)
  current_processing=$(psql "${DATABASE_URL}" -t -c "SELECT COUNT(*) FROM outbox WHERE status = 'processing'" | xargs)
  current_done=$(psql "${DATABASE_URL}" -t -c "SELECT COUNT(*) FROM outbox WHERE status = 'done'" | xargs)
  current_failed=$(psql "${DATABASE_URL}" -t -c "SELECT COUNT(*) FROM outbox WHERE status = 'failed'" | xargs)
  
  if [[ ${current_pending} -eq 0 && ${current_processing} -eq 0 ]]; then
    break
  fi
  
  if [[ $((poll_count % 5)) -eq 0 ]]; then
    echo "  Poll #${poll_count}: pending=${current_pending}, processing=${current_processing}, done=${current_done}, failed=${current_failed}"
  fi
  
  if [[ $(date +%s) -ge ${deadline} ]]; then
    echo "  ERROR: Timeout waiting for outbox drain" >&2
    exit 1
  fi
  
  sleep 1
done

final_pending=$(psql "${DATABASE_URL}" -t -c "SELECT COUNT(*) FROM outbox WHERE status = 'pending'" | xargs)
final_processing=$(psql "${DATABASE_URL}" -t -c "SELECT COUNT(*) FROM outbox WHERE status = 'processing'" | xargs)
final_done=$(psql "${DATABASE_URL}" -t -c "SELECT COUNT(*) FROM outbox WHERE status = 'done'" | xargs)
final_failed=$(psql "${DATABASE_URL}" -t -c "SELECT COUNT(*) FROM outbox WHERE status = 'failed'" | xargs)
final_payments_completed=$(psql "${DATABASE_URL}" -t -c "SELECT COUNT(*) FROM payments WHERE status = 'completed'" | xargs)

echo "  Final state: pending=${final_pending}, processing=${final_processing}, done=${final_done}, failed=${final_failed}"
echo "  Payments completed: ${final_payments_completed}/${PAYMENT_COUNT}"
echo

# Step 7: Verify results
echo "[STEP 7] Verification Results"
echo "=========================================="

if [[ ${final_pending} -eq 0 && ${final_processing} -eq 0 && ${final_done} -eq ${PAYMENT_COUNT} ]]; then
  echo "✓ SUCCESS: All records recovered and completed after worker restart"
  echo "  - Outbox records: ${final_done} done, 0 pending, 0 processing"
  echo "  - Payments completed: ${final_payments_completed}/${PAYMENT_COUNT}"
  exit 0
else
  echo "✗ FAILED: Some records did not complete"
  echo "  - Expected: ${PAYMENT_COUNT} done, 0 pending, 0 processing"
  echo "  - Actual:   ${final_done} done, ${final_pending} pending, ${final_processing} processing"
  echo "  - Failed: ${final_failed}, Payments completed: ${final_payments_completed}"
  exit 1
fi
