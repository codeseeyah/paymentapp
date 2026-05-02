CREATE EXTENSION IF NOT EXISTS pgcrypto;

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

CREATE UNIQUE INDEX ux_payments_idempotency_key
  ON payments(idempotency_key)
  WHERE idempotency_key IS NOT NULL;

CREATE TABLE outbox (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  payment_id UUID NOT NULL REFERENCES payments(id) ON DELETE CASCADE,
  payload JSONB NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'pending',
  attempt_count INT NOT NULL DEFAULT 0,
  next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  claimed_by TEXT,
  claimed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_outbox_pending_next_attempt
  ON outbox(next_attempt_at)
  WHERE status = 'pending';
