# Implementation Plan: Payment Processing Service

**Branch**: `001-process-payments` | **Date**: 2026-05-01 | **Spec**: [spec.md](specs/001-process-payments/spec.md)
**Input**: Feature specification from `/specs/001-process-payments/spec.md`

## Summary

This feature implements a durable, horizontally scalable payment-processing
service using Java + Spring Boot and PostgreSQL. Incoming payment requests are
accepted by an HTTP API, persisted in a `payments` table, and an outbox record
is created in the same transaction. Background worker(s) poll the `outbox`
table (using `SELECT FOR UPDATE SKIP LOCKED`) to claim work, commit the claim,
then call the external payment service. Calls are retried with exponential
backoff and jitter; after a configurable max attempts payments are marked
permanently `failed`. The system will simulate the external service with 
randomized delays (10ms–2000ms) and configurable failure rates.

## Technical Context

**Language/Version**: Java 17 (LTS), Spring Boot 3.x
**Primary Dependencies**: Spring Web, Spring Data JPA (Hibernate), `org.postgresql:postgresql`, Flyway (DB migrations), Micrometer + Prometheus, Spring Boot Actuator, JUnit 5, Testcontainers, Gatling (performance tests)
**Storage**: PostgreSQL (13+). Two primary tables: `payments` (business state) and `outbox` (work queue).
**Testing**: Unit tests (JUnit 5), integration tests (Spring Test + Testcontainers), contract tests for API, performance tests (Gatling or k6) for single/multi-instance scenarios.
**Target Platform**: Linux / Docker; runs as standalone jar or in containers; multiple instances supported.
**Project Type**: Web service + background worker (single codebase; same artifact can run API and worker components).
**Performance Goals**: Ingest 1000 req/s (durably persisted within 1s); avoid duplicate processing; ensure retries resolve transient failures in most cases within configured attempts.
**Constraints**: External service latency 10ms–2000ms; worker MUST commit DB claim before making external calls to avoid long-held locks.
**Scale/Scope**: Designed to scale horizontally - workers across multiple instances can safely claim outbox rows using `FOR UPDATE SKIP LOCKED`.

## Constitution Check

The constitution (code quality, test-first, functional correctness, CI gates, observability) defines mandatory gates. Quick evaluation against the current plan/spec:

- **Code Quality**: PASS (plan requires linting, formatting, static analysis). ACTION: add CI pipeline to enforce.
- **Test-First & Coverage**: PARTIAL — spec mandates tests; CI must enforce coverage threshold (default: 80%). ACTION: add CI job enforcing coverage and require tests for each story.
- **Functional Correctness & Contracts**: PASS — acceptance criteria and contract (OpenAPI) will be produced.
- **Continuous Verification & CI Gates**: PARTIAL — CI jobs described but not yet implemented. ACTION: add pipeline (lint, test, coverage, integration using Testcontainers).
- **Observability**: PARTIAL — plan includes instrumentation (Micrometer). ACTION: implement metrics/alerts in Phase 1.

Gates marked PARTIAL must be satisfied before merging to protected branches; they will be re-checked after Phase 1.

## Project Structure (selected)

```text
service/                         # Spring Boot application
├── src/main/java/...             # controllers, services, workers, repositories
├── src/main/resources/
├── src/test/java/...             # unit + integration tests
├── db/migration/                 # Flyway migrations (SQL)
└── build.gradle or pom.xml

scripts/
├── run-local.sh
├── docker-compose.yml            # Postgres + optional simulator

specs/001-process-payments/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/openapi.yaml
└── performance-tests.md
```

**Structure Decision**: Single Spring Boot module that exposes the API and can start worker threads/processors; packaged as a single artifact so it can be scaled horizontally.

## Phase 0: Outline & Research (deliverable: `research.md`)

Research topics (Phase 0 tasks):

- DB-backed outbox best practices (SELECT FOR UPDATE SKIP LOCKED usage)
- Partial/filtered index pattern for `outbox` polling performance
- Idempotency: schema & uniqueness constraints, `INSERT ... ON CONFLICT` patterns
- Exponential backoff strategy (base delay, multiplier, jitter) and max retries
- Worker claim/commit pattern (claim row, commit, call external service)
- External service simulation strategy for deterministic testing (delay distribution, failure rates)

Output: `research.md` with decisions and rationale.

## Phase 1: Design & Contracts (deliverables)

- `data-model.md`: DB schema, indexes, and migration SQL
- `contracts/openapi.yaml`: API contract for `/payments` (POST + GET status)
- `quickstart.md`: Local dev + docker-compose + migration + run instructions
- Add performance test scenarios & scripts (Gatling/k6) in `performance-tests.md`

After Phase 1 the constitution gates will be re-evaluated and any outstanding ACTION items tracked.

## Complexity Tracking

No constitutional violations identified that block design; CI enforcement and coverage thresholds remain action items to implement in Phase 1.

