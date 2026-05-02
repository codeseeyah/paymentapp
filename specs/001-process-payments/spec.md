# Feature Specification: Payment Processing Service

**Feature Branch**: `001-process-payments`
**Created**: 2026-05-01
**Status**: Draft
**Input**: User description: "I need an application that accepts payment requests from clients and processes them by calling an external payment service. Each payment request should be tracked with a status that reflects where it is in the lifecycle (received, being processed, completed, or failed). If the application crashes or restarts at any point, no payment requests should be lost and any that were mid-processing should be safely retried. The same payment request submitted twice should not be processed twice. The application should be able to run as multiple instances at the same time to handle more load. The external payment service can be slow, taking anywhere from 10ms to 2 seconds to respond, and may fail, so the application needs to retry failed calls with increasing delays between attempts, up to a maximum number of retries before marking a payment as permanently failed."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Submit Payment (Priority: P1)

A client submits a payment request and receives an acknowledgement. The system accepts the request reliably and schedules it for processing.

**Why this priority**: Core capability — receiving payments is the primary value.

**Independent Test**: Submit a payment request and verify persistence and acknowledgement.

**Acceptance Scenarios**:

1. **Given** a valid payment request, **When** the client submits it, **Then** the server responds 202 Accepted, the payment is saved with status `received`, and a corresponding work-queue record is created — both in the same transaction.
2. **Given** a duplicate payment request (same idempotency key), **When** the client resubmits, **Then** the server responds 200 OK with the existing payment resource and no new payment or outbox record is created.

---

### User Story 2 - Process Payments (Priority: P1)

Background worker(s) process saved payments by calling the external payment service and update payment status.

**Why this priority**: Processing delivers business outcome.

**Independent Test**: Insert a received `payment` with a queued work item, run the worker, and observe final status `completed` or `failed` after retries exhausted.

**Acceptance Scenarios**:

1. **Given** a queued work item, **When** a worker claims it, **Then** it commits a transaction setting `payment.status = in_progress` and incrementing retry metadata, then calls the external API with the idempotency key.
2. **Given** the external service returns success, **When** the worker receives the response, **Then** `payment.status = completed` and the work item is marked `done` in a single transaction.
3. **Given** the external service returns a transient failure, **When** the worker handles the error, **Then** a backoff timestamp is scheduled and the work item remains queued for future pickup.
4. **Given** retries have reached the configured maximum, **When** the worker picks up the record, **Then** the payment is marked `failed`, the work item is marked `failed`, and no external call is made.

---

### User Story 3 - Durable Recovery & Exactly-Once (Priority: P1)

If the application restarts or multiple instances run, processing must be durable and safe.

**Why this priority**: Reliability and correctness across restarts and scaling.

**Independent Test**: Simulate a worker crash after the external API succeeds but before the completion transaction commits. Verify the payment is retried, the external service deduplicates via idempotency key, and the payment eventually reaches `completed`.

**Acceptance Scenarios**:

1. **Given** a worker crashes after committing the claim transaction (status `in_progress`, retry incremented) but before the external API call completes, **When** the system recovers, **Then** the work item remains queued with a backoff timestamp and is retried after the delay.
2. **Given** a worker crashes after a successful external API call but before committing the completion transaction, **When** the system retries, **Then** the external service deduplicates the call via the idempotency key, the worker receives success again, and commits completion cleanly.
3. **Given** multiple instances polling concurrently, **When** workers claim work items, **Then** the system ensures each work item is claimed by exactly one worker at a time — no double-processing.

---

### Edge Cases

- Duplicate submissions with the same idempotency key: deduplicated, no duplicate work item created.
- Worker crash between claim commit and API call: work item remains queued, retried after the backoff timestamp elapses.
- External API timeout: treated as transient failure, backoff applied, retry scheduled.
- Max retries reached: payment marked `failed`, work item marked `failed`, no further attempts.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST accept payment requests and atomically persist a payment record (status `received`) and a work-queue record in a single durable transaction.
- **FR-002**: The system MUST return 202 Accepted with the payment id on first submission, and 200 OK with the existing record on duplicate idempotency key.
- **FR-003**: The system MUST track payment lifecycle states: `received`, `in_progress`, `completed`, `failed`.
- **FR-004**: The system MUST process outbox records by invoking the external payment service, always passing the idempotency key to ensure safe retries.
- **FR-005**: The system MUST retry failed external calls with exponential backoff, up to a configurable maximum, then mark both records as `failed`.
- **FR-006**: The system MUST use a safe claim mechanism so only one worker processes a given work item at a time.
- **FR-007**: The system MUST commit the claim transaction (increment retry, set `in_progress`) before making the external API call, so a crash during the call leaves the record in a retryable state.
- **FR-008**: The system MUST survive crashes and restarts without losing payment or work-queue records; any incomplete work item will be retried after its backoff timestamp.
- **FR-009**: The system MUST support horizontal scaling: multiple instances claim work concurrently without duplicate processing.

### Key Entities

**Payments**:
- `id` (primary key)
- `idempotency_key` (unique)
- `payload`
- `status` (`received`, `in_progress`, `completed`, `failed`)
- `timestamp_added`
- `timestamp_modified`

**Work Item (Queue)**:
- `id` (primary key)
- `payment_id` (foreign key → payments)
- `idempotency_key`
- `payload`
- `status` (`queued`, `done`, `failed`)
- `retry_count` (default 0)
- `next_attempt_timestamp`
- `timestamp_added`

## Success Criteria *(mandatory)*

- **SC-001**: 99.99% of submitted payment requests result in both a `payments` and `outbox` record persisted atomically within 1 second.
- **SC-002**: Duplicate submissions using the same idempotency key produce 0 duplicate payments or outbox records.
- **SC-003**: After a worker crash, all in-flight outbox records are retried within their backoff window on restart — no records permanently stuck.
- **SC-004**: Retries follow exponential backoff and respect the configured maximum; payments with exhausted retries are marked `failed`.
- **SC-005**: Three or more concurrent instances process the outbox concurrently with 0 cases of the same outbox record processed by more than one worker simultaneously.

## Assumptions

- A durable storage engine is available that supports atomic persistence and safe work item claiming.
- The external payment service exposes an idempotent endpoint that deduplicates on the provided idempotency key.
- Amounts are stored using a decimal/fixed-point type to preserve financial precision.
- If the client does not provide an idempotency key, the system generates one and treats the request as unique.
- The worker polls on a configurable interval (e.g. every 500ms); this is an acceptable latency tradeoff for a database-backed queue.
- The storage engine can be optimized with indexes suitable for the chosen query pattern.