# Tasks: Worker Outbox Performance Metrics

**Input**: Design documents from `/specs/002-worker-outbox-metrics/` (`spec.md`, `plan.md`, `data-model.md`, `research.md`, `quickstart.md`)

## Phase 1: Setup (Shared Infrastructure)

Purpose: Add the new worker performance scenario and supporting folder structure inside `service/perf/`.

- [x] T001 Create the worker performance scenario file at `service/perf/k6-worker.js` based on the existing `service/perf/k6-producers.js` structure
- [x] T002 [P] Create SQL helper files for worker backlog seeding and verification in `service/perf/sql/`
- [x] T003 [P] Update `service/perf/README.md` to document the new worker scenario, database seeding, and SQL ground-truth queries

---

## Phase 2: Foundational (Blocking Prerequisites)

Purpose: Build the reusable database seeding and reporting flow that every worker test run depends on.

- [x] T004 Implement bulk backlog seeding SQL for `payments` and `outbox` in `service/perf/sql/seed-worker-backlog.sql`
- [x] T005 [P] Implement SQL verification queries in `service/perf/sql/verify-worker-completion.sql` using `SELECT COUNT(*) FROM payments WHERE status = 'completed';` and matching outbox counts
- [x] T006 Add worker run configuration defaults and environment variable handling in `service/perf/k6-worker.js` for backlog size, worker count, and poll interval
- [x] T007 Add database result capture logic in `service/perf/k6-worker.js` so the scenario records seeded counts, final completed counts, and drain duration from PostgreSQL

**Checkpoint**: The worker scenario can seed the database, query completion counts, and report a run summary without touching the producer perf script.

---

## Phase 3: User Story 1 - Measure Worker Completion Time (Priority: P1) 🎯 MVP

**Goal**: Run a worker-focused perf test against a prepared backlog and report how long it takes jobs to complete.

**Independent Test**: Seed a known backlog, run `service/perf/k6-worker.js`, and confirm the summary reports completed-job count and total drain time from the database.

### Implementation for User Story 1

- [x] T008 [US1] Implement worker backlog seeding workflow in `service/perf/k6-worker.js` so the test seeds bulk records before timing starts
- [x] T009 [US1] Implement completion polling and drain-time measurement in `service/perf/k6-worker.js` using PostgreSQL counts as ground truth
- [x] T010 [US1] Emit a final worker summary in `service/perf/k6-worker.js` with total drained jobs, completed jobs, and elapsed time

**Checkpoint**: User Story 1 is complete when the worker perf script can seed, run, and report completion time independently.

---

## Phase 4: User Story 2 - Compare Worker Load Behavior (Priority: P2)

**Goal**: Allow the worker scenario to run with different backlog sizes or worker capacities so completion time can be compared across runs.

**Independent Test**: Execute the worker scenario twice with different backlog or worker-count settings and compare the reported drain times.

### Implementation for User Story 2

- [x] T011 [P] [US2] Add configurable backlog-size and worker-capacity inputs to `service/perf/k6-worker.js`
- [x] T012 [US2] Add run metadata capture in `service/perf/k6-worker.js` for the configured workload so results can be compared between runs
- [x] T013 [US2] Update `service/perf/README.md` with example commands for comparing two worker runs with different load settings

**Checkpoint**: User Story 2 is complete when the same worker scenario can be rerun with different load settings and produce comparable summaries.

---

## Phase 5: User Story 3 - Separate Producer and Worker Results (Priority: P3)

**Goal**: Keep the existing producer perf test and the new worker perf test distinct so each answers a different question.

**Independent Test**: Run `service/perf/k6-producers.js` and `service/perf/k6-worker.js` separately and confirm each reports a distinct summary.

### Implementation for User Story 3

- [x] T014 [US3] Keep the existing producer scenario unchanged in `service/perf/k6-producers.js` and ensure the new worker scenario is invoked separately
- [x] T015 [US3] Add a short comparison section to `service/perf/README.md` explaining when to use `k6-producers.js` versus `k6-worker.js`
- [x] T016 [US3] Verify the worker scenario uses database counts only and does not depend on producer throughput metrics in `service/perf/k6-worker.js`

**Checkpoint**: User Story 3 is complete when the worker perf script is clearly independent from the producer load test.

---

## Phase 6: Polish & Cross-Cutting Concerns

Purpose: Tighten documentation, validation, and run instructions across the perf feature.

- [x] T017 [P] Update `specs/002-worker-outbox-metrics/quickstart.md` with the final run command and SQL verification steps
- [x] T018 [P] Review `service/perf/sql/` files for consistent SQL syntax and fix any nonstandard comparisons so PostgreSQL is the source of truth
- [x] T019 Validate the complete worker scenario end-to-end from `service/perf/README.md` instructions and capture any follow-up notes

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - blocks all user stories
- **User Stories (Phase 3+)**: Depend on Foundational completion
- **Polish (Phase 6)**: Depends on all targeted user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Starts after Foundational; no dependency on other user stories
- **User Story 2 (P2)**: Starts after Foundational; may reuse the same worker scenario but remains independently testable
- **User Story 3 (P3)**: Starts after Foundational; keeps the producer scenario separate from the worker scenario

### Within Each User Story

- Seed and query helpers before summary/reporting logic
- Database ground-truth checks before comparison features
- Documentation updates after the scenario behavior is implemented

### Parallel Opportunities

- `T002` and `T003` can run in parallel because they touch different files
- `T004` and `T005` can run in parallel because one seeds and the other verifies
- `T011` and `T012` can run in parallel because they add independent worker configuration and metadata capture
- `T017` and `T018` can run in parallel because one updates quickstart docs and the other checks SQL syntax

---

## Parallel Example: User Story 1

```bash
# Seed and verification helpers can be prepared independently:
Task: "Implement bulk backlog seeding SQL for `payments` and `outbox` in `service/perf/sql/seed-worker-backlog.sql`"
Task: "Implement SQL verification queries in `service/perf/sql/verify-worker-completion.sql` using `SELECT COUNT(*) FROM payments WHERE status = 'completed';` and matching outbox counts"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational database seeding and verification
3. Complete Phase 3: User Story 1 worker timing and completion summary
4. Stop and validate the worker scenario independently

### Incremental Delivery

1. Complete Setup + Foundational so the perf harness can seed and query the database
2. Add User Story 1 so the worker scenario reports drain time and completion counts
3. Add User Story 2 so workload comparisons are possible
4. Add User Story 3 so producer and worker perf tests remain clearly separate
5. Finish with polish and quickstart validation

### Parallel Team Strategy

With multiple contributors:

1. One contributor builds the worker script and database seeding flow
2. Another contributor adds the SQL verification helpers and README updates
3. A third contributor adds the load-variation and comparison reporting improvements

---

## Notes

- [P] tasks can run in parallel when they touch different files and have no blocking dependencies
- Each user story is independently testable from the `service/perf/` directory
- The existing producer perf scenario should remain unchanged
- Use PostgreSQL counts as the source of truth for completed jobs, not logs or application counters