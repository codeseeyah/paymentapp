# Performance Tests: Payment Processing Service

Purpose: Validate throughput, latency, and correctness (no double-processing)
for single-instance and multi-instance deployments.

Test environment

- PostgreSQL (same version as production) — use Testcontainers or Docker Compose
- Application artifact built from `./mvnw clean package`
- External simulator running with configurable latency/failure

Scenarios

1) Single instance baseline

- Start one application instance and simulator.
- Run load with increasing VUs (e.g., 50, 100, 200) using Gatling or k6.
- Measure: ingestion latency (API), persistence latency, total completed payments/sec, duplicate count (should be 0), outbox pending count.

2) Multi-instance scaling

- Start N application instances (N >= 3) against same DB and simulator.
- Run same load profile and measure: throughput per instance, end-to-end latency, duplicates.

3) Failure/retry stretch test

- Configure simulator to fail transiently (e.g., 10–30% failure). Verify backoff behavior,
  retry counts, and that permanent failures are marked after configured attempts.

Metrics to collect

- API request latency (p50/p95/p99)
- DB latency (insert, claim queries)
- Payments completed/sec, payments failed/sec
- Outbox pending size over time
- Duplicate processing count (must be zero)

Automation

- k6 scripts are available in `service/perf/` with parameterized VUs, duration, and base URL:
    - `service/perf/k6-single.js`
    - `service/perf/k6-multi.js`
        - `service/perf/k6-producers.js`

- Horizontal scaling (multiple producers + workers) can be run via Docker Compose:

    ```bash
    docker compose -f service/docker-compose.scale.yml up --build --abort-on-container-exit k6
    ```
