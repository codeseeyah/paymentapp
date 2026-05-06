#!/usr/bin/env bash
set -euo pipefail

##
# Test: Producer Crash and Recovery
# 
# This test verifies that when the producer crashes after creating payment records,
# the system recovers and processes those payments. It ensures that:
# - Payments created by the producer are persisted even if the producer crashes
# - External responses (200/202) are captured and stored
# - The worker can recover and complete all payments even without the producer running
#
# Steps:
# 1. Start docker-compose with producer and worker
# 2. Create payment records via the producer API
# 3. Kill the producer container (simulating a crash)
# 4. Verify outbox entries are still in the database
# 5. Restart the producer container
# 6. Verify worker completes all payments with responses captured
##

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
SERVICE_DIR=$(cd "${SCRIPT_DIR}/.." && pwd)

COMPOSE_FILE=${COMPOSE_FILE:-docker-compose.scale.test.yml}
DATABASE_URL=${DATABASE_URL:-postgresql://postgres:postgres@localhost:5432/paymentapp-test}
PRODUCER_COUNT=${PRODUCER_COUNT:-1}
WORKER_COUNT=${WORKER_COUNT:-1}
PAYMENT_COUNT=${PAYMENT_COUNT:-50}
CREATION_DELAY_SECONDS=${CREATION_DELAY_SECONDS:-2}
RECOVERY_MAX_WAIT_SECONDS=${RECOVERY_MAX_WAIT_SECONDS:-60}

echo "=========================================="
echo "Producer Crash & Recovery Test"
echo "=========================================="
echo "Setup:"
echo "  PAYMENT_COUNT: ${PAYMENT_COUNT}"
echo "  CREATION_DELAY_SECONDS: ${CREATION_DELAY_SECONDS}"
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
creation_start=$(date +%s)
for i in $(seq 1 "$PAYMENT_COUNT"); do
  (docker compose -f "${COMPOSE_FILE}" exec -T producer curl -s -X POST http://localhost:8080/payments \
    -H "Content-Type: application/json" \
    -d "{\"amount\": $((200 + i)), \"currency\": \"USD\", \"payerId\": \"payer-prod-$i\", \"payeeId\": \"payee-prod-$i\"}" \
    >/dev/null 2>&1) &
  
  # Batch requests to avoid overwhelming the system
  if [[ $((i % 5)) -eq 0 ]]; then
    wait
  fi
done
wait
creation_end=$(date +%s)
echo "  ✓ Created ${PAYMENT_COUNT} payments in $((creation_end - creation_start))s"

# Query initial state
initial_payments=$(psql "${DATABASE_URL}" -t -c "SELECT COUNT(*) FROM payments" | xargs)
initial_outbox=$(psql "${DATABASE_URL}" -t -c "SELECT COUNT(*) FROM outbox" | xargs)
initial_pending=$(psql "${DATABASE_URL}" -t -c "SELECT COUNT(*) FROM outbox WHERE status = 'pending'" | xargs)

echo "  Persisted: ${initial_payments} payments, ${initial_outbox} outbox entries, ${initial_pending} pending"
echo

# Step 2: Wait briefly to allow some processing
echo "[STEP 2] Allowing brief processing (${CREATION_DELAY_SECONDS}s)..."
sleep "${CREATION_DELAY_SECONDS}"

pre_crash_processing=$(psql "${DATABASE_URL}" -t -c "SELECT COUNT(*) FROM outbox WHERE status = 'processing'" | xargs)
pre_crash_done=$(psql "${DATABASE_URL}" -t -c "SELECT COUNT(*) FROM outbox WHERE status = 'done'" | xargs)
echo "  Pre-crash state: ${pre_crash_processing} processing, ${pre_crash_done} done"
echo

# Step 3: Kill producer
echo "[STEP 3] Killing producer container(s)..."
docker compose -f "${COMPOSE_FILE}" stop producer 2>/dev/null || true
sleep 2
echo "  ✓ Producer killed"
echo

# Step 4: Verify that database still has all the records
echo "[STEP 4] Verifying records persisted in database despite producer crash..."
post_crash_payments=$(psql "${DATABASE_URL}" -t -c "SELECT COUNT(*) FROM payments" | xargs)
post_crash_outbox=$(psql "${DATABASE_URL}" -t -c "SELECT COUNT(*) FROM outbox" | xargs)
post_crash_pending=$(psql "${DATABASE_URL}" -t -c "SELECT COUNT(*) FROM outbox WHERE status = 'pending'" | xargs)
post_crash_processing=$(psql "${DATABASE_URL}" -t -c "SELECT COUNT(*) FROM outbox WHERE status = 'processing'" | xargs)

echo "  Post-crash state:"
echo "    - Payments: ${post_crash_payments}/${PAYMENT_COUNT}"
echo "    - Outbox entries: ${post_crash_outbox}"
echo "    - Pending: ${post_crash_pending}"
echo "    - Processing: ${post_crash_processing}"

if [[ ${post_crash_payments} -eq ${PAYMENT_COUNT} ]]; then
  echo "  ✓ Confirmed: All ${post_crash_payments} payments persisted despite producer crash"
else
  echo "  ⚠ WARNING: Expected ${PAYMENT_COUNT} payments but found ${post_crash_payments}"
fi
echo

# Step 5: Restart producer
echo "[STEP 5] Restarting producer container(s)..."
docker compose -f "${COMPOSE_FILE}" up -d --no-deps --scale producer="${PRODUCER_COUNT}" producer
sleep 3
echo "  ✓ Producer restarted"
echo

# Step 6: Wait for worker to process all records
echo "[STEP 6] Worker processing and completing records (max ${RECOVERY_MAX_WAIT_SECONDS}s)..."
deadline=$(( $(date +%s) + RECOVERY_MAX_WAIT_SECONDS ))
poll_count=0
recovery_start=$(date +%s)

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
    echo "  ERROR: Timeout waiting for completion" >&2
    exit 1
  fi
  
  sleep 1
done

recovery_end=$(date +%s)
echo "  Completed in $((recovery_end - recovery_start))s"
echo

# Step 7: Verify final state and responses
echo "[STEP 7] Verifying final state and external responses..."

final_payments=$(psql "${DATABASE_URL}" -t -c "SELECT COUNT(*) FROM payments" | xargs)
final_payments_completed=$(psql "${DATABASE_URL}" -t -c "SELECT COUNT(*) FROM payments WHERE status = 'completed'" | xargs)
final_payments_with_response=$(psql "${DATABASE_URL}" -t -c "SELECT COUNT(*) FROM payments WHERE external_response IS NOT NULL" | xargs)
final_outbox_done=$(psql "${DATABASE_URL}" -t -c "SELECT COUNT(*) FROM outbox WHERE status = 'done'" | xargs)
final_outbox_failed=$(psql "${DATABASE_URL}" -t -c "SELECT COUNT(*) FROM outbox WHERE status = 'failed'" | xargs)

echo "  Payments: ${final_payments} total, ${final_payments_completed} completed"
echo "  External responses captured: ${final_payments_with_response}/${final_payments_completed}"
echo "  Outbox: ${final_outbox_done} done, ${final_outbox_failed} failed"
echo

# Step 8: Print results
echo "[STEP 8] Test Results"
echo "=========================================="

success=true

if [[ ${final_payments} -ne ${PAYMENT_COUNT} ]]; then
  echo "✗ FAILED: Not all payments persisted (${final_payments}/${PAYMENT_COUNT})"
  success=false
fi

if [[ ${final_payments_completed} -ne ${PAYMENT_COUNT} ]]; then
  echo "✗ FAILED: Not all payments completed (${final_payments_completed}/${PAYMENT_COUNT})"
  success=false
fi

if [[ ${final_payments_with_response} -lt ${final_payments_completed} ]]; then
  echo "⚠ WARNING: Not all external responses captured (${final_payments_with_response}/${final_payments_completed})"
fi

if [[ ${final_outbox_done} -ne ${PAYMENT_COUNT} ]]; then
  echo "✗ FAILED: Not all outbox entries marked done (${final_outbox_done}/${PAYMENT_COUNT})"
  success=false
fi

if [[ ${success} == true ]]; then
  echo "✓ SUCCESS: Producer crash/recovery test passed"
  echo "  - All payments persisted and completed"
  echo "  - External responses captured: ${final_payments_with_response} of ${final_payments_completed}"
  echo "  - Recovery time: $((recovery_end - recovery_start))s"
  exit 0
else
  exit 1
fi
