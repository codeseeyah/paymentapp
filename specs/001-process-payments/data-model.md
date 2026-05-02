# Data Model: Payment Processing Service

This document specifies the PostgreSQL schema and example queries for the
`payments` and `outbox` tables used by the payment-processing service.

## Extensions

Recommended extensions (run once per database):

```sql
CREATE EXTENSION IF NOT EXISTS pgcrypto; -- provides gen_random_uuid()
```

## payments table

```sql
CREATE TABLE payments (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  idempotency_key TEXT,
  amount NUMERIC(18,4) NOT NULL,
  currency VARCHAR(3) NOT NULL,
  payer_id TEXT,
  payee_id TEXT,
  status VARCHAR(16) NOT NULL DEFAULT 'received',
  attempt_count INT NOT NULL DEFAULT 0,
  last_attempted_at TIMESTAMPTZ,
  external_response JSONB,
  failure_reason TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Unique idempotency key when provided by client
CREATE UNIQUE INDEX ux_payments_idempotency_key ON payments(idempotency_key) WHERE idempotency_key IS NOT NULL;
```

Notes:
- Use `NUMERIC` (decimal/fixed-point) for monetary values to preserve precision.
- `idempotency_key` must be provided by clients for deduplication; the server may generate one when absent.

## outbox table

```sql
CREATE TABLE outbox (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  payment_id UUID NOT NULL REFERENCES payments(id) ON DELETE CASCADE,
  payload JSONB NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'pending', -- pending, processing, done, failed
  attempt_count INT NOT NULL DEFAULT 0,
  next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  claimed_by TEXT,
  claimed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Partial index to accelerate polling for pending work
CREATE INDEX idx_outbox_pending_next_attempt ON outbox(next_attempt_at) WHERE status = 'pending';
```

## Claiming rows (worker)

Pseudocode for safe claim + commit pattern:

```sql
BEGIN;
  -- claim N rows ready to process
  SELECT id, payment_id, payload
  FROM outbox
  WHERE status = 'pending' AND next_attempt_at <= now()
  ORDER BY next_attempt_at
  LIMIT 10
  FOR UPDATE SKIP LOCKED;

  -- update claimed rows to mark in-progress
  UPDATE outbox
  SET status = 'processing', claimed_by = :worker_id, claimed_at = now(), updated_at = now()
  WHERE id IN (/* ids from select */);
COMMIT;
```

Process outside the transaction (call external API). Then commit the result:

On success:

```sql
BEGIN;
  UPDATE payments
  SET status = 'completed', attempt_count = attempt_count + 1, external_response = :resp, updated_at = now()
  WHERE id = :payment_id;

  UPDATE outbox
  SET status = 'done', updated_at = now()
  WHERE id = :outbox_id;
COMMIT;
```

On transient failure:

```sql
BEGIN;
  UPDATE outbox
  SET attempt_count = attempt_count + 1,
      next_attempt_at = now() + :backoff_interval,
      status = CASE WHEN attempt_count + 1 >= :max_attempts THEN 'failed' ELSE 'pending' END,
      updated_at = now()
  WHERE id = :outbox_id;

  UPDATE payments
  SET attempt_count = attempt_count + 1,
      status = CASE WHEN attempt_count + 1 >= :max_attempts THEN 'failed' ELSE status END,
      failure_reason = CASE WHEN attempt_count + 1 >= :max_attempts THEN :failure_reason ELSE failure_reason END,
      updated_at = now()
  WHERE id = :payment_id;
COMMIT;
```

## Monitoring & metrics

- Expose metrics: `payments_processed_total`, `payments_failed_total`, `outbox_pending_gauge`, `outbox_processing_gauge`, `outbox_retry_attempts`
- Use Micrometer + Prometheus in Spring Boot to collect metrics and alert when `outbox_pending_gauge` grows beyond threshold.
