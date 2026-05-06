# Data Model: Worker Outbox Performance Metrics

This document defines the data used by the worker performance scenario.

## Worker Performance Run

Represents one execution of the worker-focused perf test.

Fields:

- `run_id`: Unique identifier for the perf run.
- `started_at`: Time the run begins.
- `finished_at`: Time the run ends.
- `seeded_payment_count`: Number of payment rows inserted for the backlog.
- `seeded_outbox_count`: Number of outbox rows inserted for the backlog.
- `completed_payment_count`: Number of rows in `payments` with `status = 'completed'` when sampled.
- `completed_outbox_count`: Number of rows in `outbox` with terminal success state when sampled.
- `drain_duration_ms`: Elapsed time from start to backlog drain completion.
- `worker_count`: Number of worker instances participating in the run.

Relationships:

- A run owns one or more SQL seed batches.
- A run produces one or more database snapshots during execution.

Validation rules:

- Seed counts must be positive for a meaningful worker backlog test.
- Completion counts must come from PostgreSQL queries, not derived in-memory values.
- Drain duration must be computed from measured timestamps in the run.

## SQL Seed Batch

Represents the bulk insert set used to prepare the backlog.

Fields:

- `batch_id`: Unique identifier for the seed operation.
- `payment_rows_inserted`: Number of rows inserted into `payments`.
- `outbox_rows_inserted`: Number of rows inserted into `outbox`.
- `seeded_status`: Initial worker-visible state for the rows.

Relationships:

- A seed batch belongs to exactly one worker performance run.

Validation rules:

- Seed rows must create a backlog that workers can legitimately consume.
- Seed status must match the worker flow being measured.

## Database Snapshot

Represents a point-in-time count captured during the run.

Fields:

- `snapshot_id`: Unique identifier for the sample.
- `sampled_at`: Timestamp of the query.
- `payments_completed`: Result of `SELECT COUNT(*) FROM payments WHERE status = 'completed';`.
- `outbox_done`: Result of the corresponding terminal outbox count.
- `outbox_pending`: Remaining backlog count.

Relationships:

- A run may have multiple snapshots.
- Snapshots are compared to determine drain progress.

Validation rules:

- Queries must use standard SQL equality syntax.
- Snapshot counts must never exceed the seeded rows for the same run.

## Completion Summary

Represents the final report emitted by the perf scenario.

Fields:

- `completed_jobs`: Final completed count.
- `failed_jobs`: Final failed count, if any.
- `retry_count`: Observed retry volume during the run.
- `drain_duration_ms`: Total time until completion criteria were met.

Validation rules:

- The final summary must match the last database snapshot.
- The summary must report the database state that the test used as ground truth.