\set ON_ERROR_STOP on

TRUNCATE TABLE outbox, payments CASCADE;

WITH seeded_payments AS (
  INSERT INTO payments (
    idempotency_key,
    amount,
    currency,
    payer_id,
    payee_id,
    status,
    attempt_count,
    created_at,
    updated_at
  )
  SELECT
    'worker-seed-' || :seed_batch || '-' || gs::text,
    10.0000,
    'USD',
    'worker-payer-' || gs::text,
    'worker-payee-' || gs::text,
    'received',
    0,
    now(),
    now()
  FROM generate_series(1, :seed_count) AS gs
  RETURNING id, idempotency_key, amount, currency, payer_id, payee_id
)
INSERT INTO outbox (
  payment_id,
  payload,
  status,
  attempt_count,
  next_attempt_at,
  claimed_by,
  claimed_at,
  created_at,
  updated_at
)
SELECT
  seeded_payments.id,
  jsonb_build_object(
    'paymentId', seeded_payments.id,
    'idempotencyKey', seeded_payments.idempotency_key,
    'amount', seeded_payments.amount,
    'currency', seeded_payments.currency,
    'payerId', seeded_payments.payer_id,
    'payeeId', seeded_payments.payee_id
  ),
  'pending',
  0,
  now(),
  NULL,
  NULL,
  now(),
  now()
FROM seeded_payments;