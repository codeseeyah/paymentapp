# Research: Worker Outbox Performance Metrics

Date: 2026-05-06

This document captures the design decisions for the worker-oriented performance scenario.

1) Database seeding before the perf run

- Decision: Seed a bulk backlog directly in PostgreSQL before starting the worker scenario.
- Rationale: Direct SQL seeding is deterministic, fast, and measures worker completion instead of producer request throughput.
- Alternatives considered: Creating the backlog through the HTTP producer path; rejected because it mixes producer behavior into the worker measurement.

2) Ground-truth completion counts

- Decision: Use PostgreSQL status counts as the source of truth for the run, including queries like `SELECT COUNT(*) FROM payments WHERE status = 'completed';` and matching outbox-state counts.
- Rationale: Database state is authoritative and avoids depending on logs, in-memory counters, or delayed metrics propagation.
- Alternatives considered: Prometheus counters, log scraping, or application responses; rejected because they are derivative signals and can drift from persisted state.

3) Scenario layout in the perf directory

- Decision: Add a new worker scenario alongside the existing producer scripts under `service/perf/`, with SQL helper files for seed and verification steps.
- Rationale: Keeps all performance artifacts in one place and matches the current project layout.
- Alternatives considered: Creating a separate test toolchain outside `service/perf/`; rejected to keep the feature discoverable and consistent with existing scripts.

4) Runtime environment for the worker test

- Decision: Run the worker scenario in the existing scale-test Docker Compose environment with PostgreSQL, simulator, workers, and the current service image.
- Rationale: The scale stack already models the multi-instance worker setup the scenario needs.
- Alternatives considered: Running against a standalone local database or a mocked worker; rejected because the scenario must observe real outbox processing.

5) Measurement focus

- Decision: Measure both total drain time and the number of jobs observed as completed during the run, with SQL queries used to confirm the final state.
- Rationale: This provides a useful worker-performance summary without conflating it with producer request volume.
- Alternatives considered: Measuring only latency or only throughput; rejected because each misses part of the worker completion story.