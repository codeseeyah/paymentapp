# Implementation Plan: Worker Outbox Performance Metrics

**Branch**: `002-worker-outbox-metrics` | **Date**: 2026-05-06 | **Spec**: [spec.md](specs/002-worker-outbox-metrics/spec.md)
**Input**: Feature specification from `/specs/002-worker-outbox-metrics/spec.md`

## Summary

Add a new worker-focused performance scenario under `service/perf/` that seeds bulk rows into PostgreSQL, lets the outbox workers process them, and measures how long it takes for jobs to reach completed state. The scenario must use database counts as ground truth, with SQL queries such as `SELECT COUNT(*) FROM payments WHERE status = 'completed';`, and must remain separate from the existing producer load test.

## Technical Context

**Language/Version**: JavaScript for k6 scripts plus SQL fixtures and shell orchestration; existing service remains Java 17 / Spring Boot 3.x
**Primary Dependencies**: k6, Docker Compose, PostgreSQL 15, `psql`/SQL fixtures, existing Spring Boot worker service and simulator
**Storage**: PostgreSQL `payments` and `outbox` tables used as the source of truth for job state
**Testing**: Performance test scripts in `service/perf/` that seed the database, run worker load, and query completion counts directly from PostgreSQL
**Target Platform**: Linux with Docker
**Project Type**: Web service plus performance test harness
**Performance Goals**: Measure backlog drain time and completion latency for worker processing, with repeatable runs against a seeded backlog
**Constraints**: Database status counts are the authoritative signal; SQL must use standard equality predicates (`=`), not application-derived counters or logs
**Scale/Scope**: Reuse the existing scale-test environment with Postgres, simulator, producers, and workers; add one worker-oriented perf scenario and its support files

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- Code Quality: PASS. The plan keeps changes isolated to the perf harness and uses existing repo conventions.
- Test-First & Coverage: PASS. The new perf scenario is specified as a deterministic test artifact with explicit validation criteria.
- Functional Correctness & Contracts: PASS. The scenario is grounded in database state and verifies worker completion rather than inferred signals.
- Continuous Verification & CI Gates: PASS. The plan preserves repeatability through seeded data and SQL assertions.
- Observability: PASS. The worker run will report completion timing and counts from the database.

## Project Structure

### Documentation (this feature)

```text
specs/002-worker-outbox-metrics/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
└── quickstart.md        # Phase 1 output (/speckit.plan command)
```

### Source Code (repository root)

```text
service/
├── perf/
│   ├── README.md
│   ├── k6-single.js
│   ├── k6-multi.js
│   ├── k6-producers.js
│   ├── k6-worker.js            # new worker perf scenario
│   ├── run-scale-test.sh
│   ├── run-scale-test.ps1
│   └── sql/                    # new SQL seed/query helpers for worker runs
└── docker-compose.scale.yml
```

**Structure Decision**: Extend the existing `service/perf/` harness with a new worker scenario, SQL seed/query helpers, and an orchestration script that seeds PostgreSQL and reads completion counts from the database. Keep the producer scenario unchanged.

## Complexity Tracking

No constitution violations require justification for this feature.