# Performance Tests (k6)

Prereqs:

- k6 installed
- PostgreSQL, simulator and payment application running via containerization (recommended) or locally

### Running via containerization (Recommended): 
Start the required services by running the following command in ./service:
- For single instance:
  ```bash
  docker compose -f docker-compose.test.yml up
  ```
- For multi instance with N producers and M workers:
  ```bash
  docker compose -f docker-compose.scale.test.yml up --scale producer=N --scale worker=M
  ```

To close services after tests:
- For single instance:
  ```bash
  docker compose -f docker-compose.test.yml down
  ```
- For multi instance:
  ```bash
  docker compose -f docker-compose.scale.test.yml down
  ```

## Producer Tests:

Single instance:

```bash
k6 run perf/k6-single.js
```

Multi instance (start N producer instances on different ports and point load to a load balancer or one instance):

```bash
k6 run perf/k6-multi.js
```

Override defaults:

```bash
VUS=200 DURATION=120s API_BASE_URL=http://localhost:8080 k6 run perf/k6-single.js
```

## Worker/Outbox Completion Tests:

```bash
./perf/run-worker-metrics.sh
```

The worker scenario seeds bulk rows directly into PostgreSQL, polls the database until the outbox drains, and then reports completion counts plus drain duration. The following SQL query pattern is used as the ground truth for completion:

```sql
SELECT COUNT(*) FROM payments WHERE status = 'completed';
SELECT COUNT(*) FROM outbox WHERE status = 'done';
SELECT COUNT(*) FROM outbox WHERE status = 'pending';
SELECT COUNT(*) FROM outbox WHERE status = 'processing';
```

Configure the worker scenario with environment variables:

```bash
WORKER_BACKLOG_SIZE=500 WORKER_POLL_INTERVAL_SECONDS=1 WORKER_MAX_WAIT_SECONDS=600 ./perf/run-worker-metrics.sh
```

Compare two worker runs with different loads by changing the backlog size or worker count and reusing the same script:

```bash
WORKER_RUN_LABEL=baseline WORKER_BACKLOG_SIZE=100 ./perf/run-worker-metrics.sh
WORKER_RUN_LABEL=larger-load WORKER_BACKLOG_SIZE=500 ./perf/run-worker-metrics.sh
```
