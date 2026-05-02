# Release Checklist: Payment Processing Service

## Pre-merge

- [ ] All tasks for the release scope are completed in `specs/001-process-payments/tasks.md`
- [ ] CI is green (lint, unit tests, integration tests)
- [ ] Coverage check passes and is at or above the configured threshold
- [ ] Contract tests are green and API changes are reviewed
- [ ] Performance tests run for the target environment profile

## Pre-deploy

- [ ] Production configuration reviewed (DB credentials, simulator disabled)
- [ ] Payment worker scaling targets defined (instances, batch size, poll interval)
- [ ] Alerting thresholds set (outbox pending size, failed payments)
- [ ] Rollback plan documented

## Post-deploy

- [ ] Smoke test `POST /payments` and `GET /payments/{id}`
- [ ] Metrics confirm processing throughput and no duplicate processing
- [ ] Monitor retry rates and failure rates for 24h
