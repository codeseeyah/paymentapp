ALTER TABLE payments
ALTER COLUMN external_response TYPE jsonb
USING external_response::jsonb;