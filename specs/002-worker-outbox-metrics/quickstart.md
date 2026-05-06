# Quickstart: Worker Outbox Performance Metrics

## Prerequisites

- Docker and Docker Compose
- `psql` available on the host or inside a database container
- k6 installed, or the `grafana/k6` container used through Compose

## Start the scale environment

From `service/`:

```bash
docker compose -f docker-compose.scale.yml up -d postgres simulator producer-1 producer-2 worker-1 worker-2 nginx
```

## Seed a worker backlog

Load a bulk SQL fixture into PostgreSQL so the worker scenario starts with real rows in `payments` and `outbox`.

Example query pattern for validating the backlog state:

```sql
SELECT COUNT(*) FROM payments WHERE status = 'completed';
SELECT COUNT(*) FROM outbox WHERE status = 'done';
SELECT COUNT(*) FROM outbox WHERE status = 'pending';
```

The perf harness should treat the database as the source of truth for the run.

## Run the producer scenario

```bash
k6 run perf/k6-producers.js
```

## Run the worker scenario

Run the new worker-focused perf test from `service/perf/` once the backlog is seeded.

Expected behavior:

- Seed bulk records into PostgreSQL before timing starts.
- Poll the database for completion counts.
- Report total drain time and completion counts from the final database state.

Example runs for comparing workload settings:

```bash
WORKER_RUN_LABEL=baseline WORKER_BACKLOG_SIZE=100 WORKER_COUNT=2 ./perf/run-worker-metrics.sh
WORKER_RUN_LABEL=larger-load WORKER_BACKLOG_SIZE=500 WORKER_COUNT=4 ./perf/run-worker-metrics.sh
```

## Verify completion

After the worker run, query PostgreSQL directly to confirm the final counts match the summary:

```sql
SELECT COUNT(*) FROM payments WHERE status = 'completed';
SELECT COUNT(*) FROM outbox WHERE status = 'done';
```

## Notes

- Use standard SQL equality operators (`=`), not `==`.
- Keep this scenario separate from the producer perf test so each one answers a different question.