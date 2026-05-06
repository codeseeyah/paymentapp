import { check } from 'k6';

export const options = {
  vus: 1,
  iterations: 1,
};

const runLabel = __ENV.WORKER_RUN_LABEL || 'worker-outbox-metrics';
const backlogSize = Number.parseInt(__ENV.WORKER_BACKLOG_SIZE || '0', 10);
const workerCount = Number.parseInt(__ENV.WORKER_COUNT || '0', 10);
const seededCount = Number.parseInt(__ENV.WORKER_SEEDED_COUNT || '0', 10);
const completedCount = Number.parseInt(__ENV.WORKER_COMPLETED_COUNT || '0', 10);
const failedCount = Number.parseInt(__ENV.WORKER_FAILED_COUNT || '0', 10);
const doneOutboxCount = Number.parseInt(__ENV.WORKER_DONE_OUTBOX_COUNT || '0', 10);
const pendingOutboxCount = Number.parseInt(__ENV.WORKER_PENDING_OUTBOX_COUNT || '0', 10);
const processingOutboxCount = Number.parseInt(__ENV.WORKER_PROCESSING_OUTBOX_COUNT || '0', 10);
const drainDurationMs = Number.parseInt(__ENV.WORKER_DRAIN_DURATION_MS || '0', 10);

export default function () {
  check({
    runLabel,
    backlogSize,
    seededCount,
    completedCount,
    failedCount,
    doneOutboxCount,
    pendingOutboxCount,
    processingOutboxCount,
    workerCount,
  }, {
    'all seeded jobs completed': () => completedCount === seededCount,
    'no outbox rows remain pending': () => pendingOutboxCount === 0 && processingOutboxCount === 0,
    'done outbox count matches seeded count': () => doneOutboxCount === seededCount,
    'failed job count is zero': () => failedCount === 0,
  });
}

export function handleSummary() {
  return {
    stdout: [
      'Worker outbox performance summary',
      `Run label: ${runLabel}`,
      `Backlog size: ${backlogSize}`,
      `Seeded jobs: ${seededCount}`,
      `Completed payments: ${completedCount}`,
      `Done outbox rows: ${doneOutboxCount}`,
      `Failed payments: ${failedCount}`,
      `Pending outbox rows: ${pendingOutboxCount}`,
      `Processing outbox rows: ${processingOutboxCount}`,
      `Drain duration (ms): ${drainDurationMs}`,
    ].join('\n') + '\n',
  };
}