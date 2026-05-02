# Research: Payment Processing Service

Date: 2026-05-01

This document captures Phase 0 decisions for the payment-processing feature.

1) Outbox pattern & polling strategy

- Decision: Use a database-backed outbox with an `outbox` table. Workers will
  poll the outbox using `SELECT ... FOR UPDATE SKIP LOCKED` to claim rows and
  then immediately commit the claim before making external calls.
- Rationale: `FOR UPDATE SKIP LOCKED` provides a robust, simple way to distribute
  work across multiple worker instances without an external message queue.
- Alternatives considered: Advisory locks, LISTEN/NOTIFY + queue; rejected due
  to complexity and cross-instance reliability concerns.

2) Claiming rows & commit-before-external-call

- Decision: Worker flow:
  1. Begin transaction
  2. `SELECT ... FOR UPDATE SKIP LOCKED` rows to claim
  3. Update claimed rows: set `status='processing'`, `claimed_by`, `claimed_at`
  4. Commit transaction
  5. Call external service outside transaction
  6. On success/failure, write final state in a short transaction
- Rationale: Keeping the external call outside the claim transaction avoids
  long-held locks and transaction timeouts.

3) Partial index for polling performance

- Decision: Create a partial index on `outbox(next_attempt_at)` WHERE `status='pending'`.
- Rationale: Polls will target pending work ordered by `next_attempt_at`; a
  partial index reduces index size and speeds lookups.

4) Idempotency strategy

- Decision: Incoming requests must include an `Idempotency-Key` header. The
  `payments` table will have a unique partial index on `idempotency_key` (WHERE idempotency_key IS NOT NULL).
  Producer will insert payments using `INSERT ... ON CONFLICT (idempotency_key) DO NOTHING RETURNING id`.
- Rationale: Ensures duplicates do not create multiple payments/outbox rows while
  keeping the insert operation transactional and simple.

5) Backoff & retry policy

- Decision: Exponential backoff with jitter. Default parameters: base delay
  200ms; multiplier 2.0; jitter factor uniform in [0.5, 1.0]; max attempts: 5.
- Rationale: Balances retry aggressiveness with safety; jitter prevents thundering herd.

6) External service simulation

- Decision: Provide a small HTTP simulator used in tests that responds with a
  random delay between 10ms and 2000ms and a configurable failure probability.
- Rationale: Enables deterministic tests (set RNG seed) and load/perf tests.

7) Testing approach

- Unit: JUnit 5 with Mockito for service logic.
- Integration: Spring Boot Test + Testcontainers (Postgres) to validate DB flows.
- Performance: Gatling (Scala) or k6 (JavaScript) scripts for single-instance
  and multi-instance runs. Use the simulator to control external latency/failure.

Decisions above will be implemented in Phase 1. Any remaining unknowns will be
converted into research tasks in the plan.
