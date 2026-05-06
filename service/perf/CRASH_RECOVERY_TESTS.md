# Crash & Recovery Tests

These tests verify that the payment application correctly handles producer and worker crashes, ensuring data persistence and eventual consistency through the outbox pattern.

## Prerequisites

- Docker and Docker Compose installed
- PostgreSQL client (psql) installed
- JDK 17+ (for building the Docker image)
- Application built: `mvn clean package -DskipTests`

## Test Scenarios

### 1. Worker Crash & Recovery Test

**File:** `test-worker-crash-recovery.sh`

**Scenario:**
This test simulates a worker process crashing in the middle of processing outbox entries and verifies recovery.

**What it tests:**
- Worker claims outbox entries (marks them as `processing`)
- Worker process crashes (killed)
- Records remain in `processing` state with `claimed_by` and `claimed_at` timestamps
- Worker restarts and reclaims stale `processing` records (based on lease timeout)
- All outbox entries eventually complete successfully

**Flow:**
1. Start docker-compose with producer and worker
2. Create 50 payments via the producer API (creates payment + outbox entry)
3. Allow worker to process some records
4. Kill the worker container (simulating crash)
5. Verify processing records are still in the database
6. Restart the worker
7. Verify all records eventually complete (status = `done`)
8. Verify all corresponding payments complete (status = `completed`)

**Run the test:**
```bash
./perf/test-worker-crash-recovery.sh
```

**Customize test parameters:**
```bash
# Create 100 payments, wait 5 seconds before crash, allow 120 seconds for recovery
PAYMENT_COUNT=100 CRASH_DELAY_SECONDS=5 RECOVERY_MAX_WAIT_SECONDS=120 \
  ./perf/test-worker-crash-recovery.sh
```

**Environment Variables:**
- `PAYMENT_COUNT` (default: 50) - Number of payments to create
- `CRASH_DELAY_SECONDS` (default: 3) - Seconds to wait before crashing worker
- `RECOVERY_MAX_WAIT_SECONDS` (default: 60) - Max time to wait for recovery
- `DATABASE_URL` (default: postgresql://postgres:postgres@localhost:5432/paymentapp-test)
- `PRODUCER_COUNT` (default: 1)
- `WORKER_COUNT` (default: 1)
- `COMPOSE_FILE` (default: docker-compose.scale.test.yml)

**Expected result:**
```
✓ SUCCESS: All records recovered and completed after worker restart
  - Outbox records: 50 done, 0 pending, 0 processing
  - Payments completed: 50/50
```

---

### 2. Producer Crash & Recovery Test

**File:** `test-producer-crash-recovery.sh`

**Scenario:**
This test simulates the producer process crashing after creating payment records and verifies the system recovers.

**What it tests:**
- Producer creates payments and persists them to the database + outbox
- Producer process crashes (killed)
- Payment records remain in the database even though producer is gone
- Worker can process orphaned outbox entries
- External responses (200/202) are properly captured in the database
- System eventually completes all payments

**Flow:**
1. Start docker-compose with producer and worker
2. Create 50 payments via the producer API
3. Kill the producer container (simulating crash)
4. Verify all payment records are still persisted in the database
5. Restart the producer
6. Allow worker to process remaining outbox entries
7. Verify all outbox entries complete (status = `done`)
8. Verify external responses are captured in payment records
9. Verify all payments complete (status = `completed`)

**Run the test:**
```bash
./perf/test-producer-crash-recovery.sh
```

**Customize test parameters:**
```bash
# Create 100 payments, wait 3 seconds before crash, allow 120 seconds for recovery
PAYMENT_COUNT=100 CREATION_DELAY_SECONDS=3 RECOVERY_MAX_WAIT_SECONDS=120 \
  ./perf/test-producer-crash-recovery.sh
```

**Environment Variables:**
- `PAYMENT_COUNT` (default: 50) - Number of payments to create
- `CREATION_DELAY_SECONDS` (default: 2) - Seconds to wait after creation before crash
- `RECOVERY_MAX_WAIT_SECONDS` (default: 60) - Max time to wait for recovery
- `DATABASE_URL` (default: postgresql://postgres:postgres@localhost:5432/paymentapp-test)
- `PRODUCER_COUNT` (default: 1)
- `WORKER_COUNT` (default: 1)
- `COMPOSE_FILE` (default: docker-compose.scale.test.yml)

**Expected result:**
```
✓ SUCCESS: Producer crash/recovery test passed
  - All payments persisted and completed
  - External responses captured: 50 of 50
  - Recovery time: 5s
```

---

## Running Both Tests

Create a simple loop to run both tests:

```bash
#!/bin/bash
set -e

echo "Running Worker Crash & Recovery Test..."
./perf/test-worker-crash-recovery.sh
echo
echo "Running Producer Crash & Recovery Test..."
./perf/test-producer-crash-recovery.sh
echo
echo "All tests completed successfully!"
```

Or use environment variables to customize both:

```bash
PAYMENT_COUNT=100 RECOVERY_MAX_WAIT_SECONDS=120 \
  ./perf/test-worker-crash-recovery.sh && \
  ./perf/test-producer-crash-recovery.sh
```

---

## Scale Testing

Test crash recovery with multiple workers/producers:

```bash
# Test with 3 workers recovering from a crashed worker
WORKER_COUNT=3 PAYMENT_COUNT=100 \
  ./perf/test-worker-crash-recovery.sh

# Test with 3 producers and 2 workers
PRODUCER_COUNT=3 WORKER_COUNT=2 PAYMENT_COUNT=100 \
  ./perf/test-producer-crash-recovery.sh
```

---

## Database Queries for Manual Verification

After running tests, inspect the database state:

```bash
# Connect to database
psql postgresql://postgres:postgres@localhost:5432/paymentapp-test

# View final payment statuses
SELECT status, COUNT(*) FROM payments GROUP BY status;

# View final outbox statuses
SELECT status, COUNT(*) FROM outbox GROUP BY status;

# View completed payments with their external responses
SELECT id, status, external_response, attempt_count FROM payments 
WHERE status = 'completed' ORDER BY updated_at DESC LIMIT 10;

# View failed records
SELECT p.id, p.status, p.failure_reason, o.status FROM payments p 
LEFT JOIN outbox o ON p.id = o.payment_id 
WHERE p.status = 'failed' OR o.status = 'failed';

# Check for stuck processing entries (should be empty after recovery)
SELECT COUNT(*) FROM outbox WHERE status = 'processing' AND claimed_at < now() - interval '10 seconds';
```

---

## Troubleshooting

### Test hangs on startup
- Ensure Docker daemon is running
- Check that ports 5432, 6432, 8080, 8081 are available
- Review Docker Compose logs: `docker compose -f docker-compose.scale.test.yml logs`

### Test times out waiting for recovery
- Increase `RECOVERY_MAX_WAIT_SECONDS`
- Check worker logs: `docker compose -f docker-compose.scale.test.yml logs worker`
- Verify payment simulator is responding: `curl http://localhost:8081/actuator/health`

### Records stuck in "processing" state
- Check `claimed_at` timestamp vs worker lease timeout
- Verify `claimed_by` is set to a valid worker ID
- Check worker logs for exceptions during processing

### Database connection errors
- Verify PostgreSQL is running: `docker compose -f docker-compose.scale.test.yml logs postgres`
- Check pgbouncer connection pool: `docker compose -f docker-compose.scale.test.yml logs pgbouncer`
- Verify connection string in `DATABASE_URL`

---

## Test Architecture

### Outbox Pattern Recap

The outbox pattern provides exactly-once delivery semantics:

1. **Producer Phase:** Application writes payment record and outbox entry in the same transaction
2. **Worker Phase:** Worker claims outbox entries, processes them, and marks them done
3. **Recovery Phase:** If worker crashes mid-processing, another worker instance reclaims stale entries

### Key Tables & Fields

**payments table:**
- `id` - Payment UUID
- `status` - RECEIVED → PROCESSING → COMPLETED (or FAILED)
- `attempt_count` - Number of delivery attempts
- `external_response` - Response from external payment service
- `failure_reason` - Reason for permanent failure

**outbox table:**
- `id` - Outbox entry UUID
- `payment_id` - Foreign key to payments
- `status` - PENDING → PROCESSING → DONE (or FAILED)
- `claimed_by` - Worker ID that claimed this entry
- `claimed_at` - Timestamp of claim
- `next_attempt_at` - When to retry (used for backoff)
- `attempt_count` - Delivery attempts

### Lease-Based Recovery

Lease timeout is configured in `application.yml` under `payment.worker.lease-timeout-ms` (default: 10 seconds).

When a worker crashes:
1. Its outbox entries remain in `processing` state with old `claimed_at` timestamps
2. Other worker instances detect stale claims using: `claimed_at <= now() - lease_timeout`
3. Stale entries are reclaimed and reprocessed
4. External payment system handles idempotent retries using `idempotency_key` parameter
