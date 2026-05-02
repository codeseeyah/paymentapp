---
description: "Task list for Payment Processing Service"
---

# Tasks: Payment Processing Service

**Input**: Design documents from `/specs/001-process-payments/` (`spec.md`, `plan.md`, `data-model.md`, `contracts/openapi.yaml`)

## Phase 1: Setup (Shared Infrastructure)

Purpose: Project initialization and local/dev infra required by all work.

- [x] T001 Create Maven Spring Boot project skeleton in service/ with `pom.xml` and main class at `service/src/main/java/com/example/paymentapp/PaymentAppApplication.java`
- [x] T002 [P] Create `service/docker-compose.yml` that launches PostgreSQL and the payment simulator; include a README snippet in `service/README.md`
- [x] T003 [P] Add repository metadata and repository root docs: `service/README.md` and `service/.gitignore`
- [x] T004 [P] Add code formatting and linting configuration to `service/pom.xml` (Spotless or Checkstyle) and a pre-commit sample hook in `service/.githooks/`

---

## Phase 2: Foundational (Blocking Prerequisites)

Purpose: DB schema, migrations, and core libraries/services that all stories depend on.

- [x] T005 Create Flyway SQL migration at `service/db/migration/V1__init_payments_outbox.sql` implementing the `payments` and `outbox` tables, the partial index on `outbox(next_attempt_at) WHERE status='pending'`, and the unique index on `payments(idempotency_key)`
- [x] T006 [P] Add datasource and Flyway configuration in `service/src/main/resources/application-local.yml` and a `service/src/main/resources/application.yml` template for CI
- [x] T007 [P] Implement JPA entity `Payment` at `service/src/main/java/com/example/paymentapp/model/Payment.java` (fields per `data-model.md`)
- [x] T008 [P] Implement JPA entity `Outbox` at `service/src/main/java/com/example/paymentapp/model/Outbox.java`
- [x] T009 [P] Implement `PaymentRepository` and `OutboxRepository` in `service/src/main/java/com/example/paymentapp/repository/` including a native `claimPending` method using `SELECT ... FOR UPDATE SKIP LOCKED` (or Spring Data equivalent)
- [x] T010 [P] Add Micrometer + Spring Boot Actuator dependencies and minimal configuration in `service/pom.xml` and `service/src/main/resources/application-local.yml`

**Checkpoint**: Run `./mvnw -DskipTests package` and ensure Flyway migrations are applied against the local docker-compose Postgres.

---

## Phase 3: User Story 1 - Submit Payment (Priority: P1) 🎯 MVP

Goal: Accept payment requests, persist them with idempotency, and queue work via outbox.

Independent Test: `POST /payments` returns 202 Accepted and DB has `payments` row and `outbox` row.

### Tests (must be written before or alongside implementation)

- [x] T011 [P] [US1] Add integration test `service/src/test/java/com/example/paymentapp/integration/PaymentControllerIT.java` using Testcontainers to validate `POST /payments` persists `payments` + `outbox` and returns 202

### Implementation

- [x] T012 [US1] Create DTOs `PaymentRequest` and `PaymentResource` at `service/src/main/java/com/example/paymentapp/dto/`
- [x] T013 [P] [US1] Implement `PaymentService.createAndQueuePayment(...)` in `service/src/main/java/com/example/paymentapp/service/PaymentService.java` — transactionally insert payment and outbox, enforce idempotency (use `idempotency_key` unique constraint or `findByIdempotencyKey` + insert)
- [x] T014 [US1] Implement `PaymentController` in `service/src/main/java/com/example/paymentapp/controller/PaymentController.java` with `POST /payments` and `GET /payments/{id}` (handle `Idempotency-Key` header and return 200 for duplicates)
- [x] T015 [P] [US1] Add unit tests for `PaymentService` covering idempotency and transactional behavior at `service/src/test/java/.../PaymentServiceTest.java`

**Checkpoint**: Tests for US1 must pass and demonstrate idempotent insert and outbox creation before moving to processing implementation.

---

## Phase 4: User Story 2 - Process Payments (Priority: P1)

Goal: Background worker(s) claim outbox rows, call external payment service, and update payments/outbox with success or schedule retry.

Independent Test: Insert a `received` payment/outbox row, run worker, and observe `completed` or `failed` after configured retries.

### Tests

- [x] T016 [P] [US2] Add integration test `service/src/test/java/com/example/paymentapp/integration/OutboxWorkerIntegrationTest.java` using the simulator to validate claim-commit-call-update flow and retry scheduling

### Implementation

- [x] T017 [US2] Implement `ExternalPaymentClient` at `service/src/main/java/com/example/paymentapp/client/ExternalPaymentClient.java` — call external API, include `Idempotency-Key`, handle timeouts
- [x] T018 [US2] Implement `OutboxWorker` at `service/src/main/java/com/example/paymentapp/worker/OutboxWorker.java`:
  - claim rows with `SELECT ... FOR UPDATE SKIP LOCKED` via `OutboxRepository`
  - update claimed rows to `processing`, commit
  - call `ExternalPaymentClient` outside transaction
  - on success: update `payments` -> `completed` and `outbox` -> `done`
  - on transient failure: increment `attempt_count`, set `next_attempt_at` using exponential backoff and jitter; set `status='failed'` when attempts >= max
- [x] T019 [P] [US2] Implement `BackoffCalculator` utility and configuration keys in `service/src/main/resources/application.yml` (baseDelay, multiplier, jitterRange, maxAttempts)

---

## Phase 5: User Story 3 - Durable Recovery & Exactly-Once (Priority: P1)

Goal: Ensure durability and exactly-once final processing across restarts and multiple instances.

Independent Test: Simulate a worker crash after claim commit and ensure the payment is retried and processed exactly once.

- [x] T020 [US3] Validate DB indexes and migrations exist: add `service/src/test/java/.../SchemaMigrationTest.java` to assert partial and unique indexes from `V1__init_payments_outbox.sql`
- [x] T021 [P] [US3] Implement `MultiInstanceProcessingTest` at `service/src/test/java/.../MultiInstanceProcessingTest.java` that starts multiple worker threads/processes against the same DB and verifies no double-processing
- [x] T022 [US3] Implement `RecoveryIntegrationTest` at `service/src/test/java/.../RecoveryIntegrationTest.java` that simulates worker crash after claim commit and validates retry and final status
- [x] T023 [P] [US3] Add contract tests `service/src/test/java/.../ContractTests.java` that assert idempotency semantics (duplicate submissions) and final status correctness
- [x] T024 [US3] Add health/observability tests `service/src/test/java/.../HealthMetricsTest.java` validating Actuator metrics (outbox_pending_gauge, payments_processed_total)

---

## Phase N: Polish & Cross-Cutting Concerns

- [x] T025 [P] Add performance test scripts under `service/perf/` for single-instance and multi-instance (Gatling or k6)
- [x] T026 Add CI workflow at `.github/workflows/ci.yml` to run lint, unit tests, integration tests (Testcontainers), and enforce coverage thresholds
- [x] T027 [P] Update `service/README.md`, `specs/001-process-payments/quickstart.md`, and `specs/001-process-payments/performance-tests.md` with run and test instructions
- [x] T028 [P] Create `specs/001-process-payments/release-checklist.md` describing merge/QA/monitoring steps for releases

---

## Dependencies & Execution Order

- Setup (Phase 1) first, then Foundational (Phase 2). User stories (Phase 3+) depend on Foundational.
- Tests for a story must be added and failing (where applicable) before implementation tasks are completed.

## Parallel Opportunities

- Tasks marked `[P]` can be implemented in parallel (different files, no blocking dependencies). Examples: `T002`, `T003`, `T004`, `T006`, `T007`, `T008`, `T009`, `T010`, `T012`, `T014`, `T016`, `T019`, `T021`, `T023`, `T025`, `T027`, `T028`.

## Implementation Strategy (MVP first)

1. Complete Phase 1 (setup) and Phase 2 (foundational migrations + entities).
2. Implement User Story 1 (T011-T015) as MVP, verify idempotency and outbox writes.
3. Implement User Story 2 worker (T017-T019) and integration tests.
4. Add multi-instance and recovery tests (T021-T023).
5. Run performance tests and finalize CI/polish.
