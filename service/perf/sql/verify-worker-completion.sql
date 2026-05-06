\set ON_ERROR_STOP on

SELECT
  (SELECT COUNT(*) FROM payments WHERE status = 'completed') AS completed_payments,
  (SELECT COUNT(*) FROM payments WHERE status = 'failed') AS failed_payments,
  (SELECT COUNT(*) FROM outbox WHERE status = 'done') AS done_outbox,
  (SELECT COUNT(*) FROM outbox WHERE status = 'failed') AS failed_outbox,
  (SELECT COUNT(*) FROM outbox WHERE status = 'pending') AS pending_outbox,
  (SELECT COUNT(*) FROM outbox WHERE status = 'processing') AS processing_outbox;