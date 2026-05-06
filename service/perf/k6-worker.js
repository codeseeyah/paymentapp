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
const failedOutboxCount = Number.parseInt(__ENV.WORKER_FAILED_OUTBOX_COUNT || '0', 10);
const doneOutboxCount = Number.parseInt(__ENV.WORKER_DONE_OUTBOX_COUNT || '0', 10);
const pendingOutboxCount = Number.parseInt(__ENV.WORKER_PENDING_OUTBOX_COUNT || '0', 10);
const processingOutboxCount = Number.parseInt(__ENV.WORKER_PROCESSING_OUTBOX_COUNT || '0', 10);
const drainDurationMs = Number.parseInt(__ENV.WORKER_DRAIN_DURATION_MS || '0', 10);
const firstCompletionMs = Number.parseInt(__ENV.WORKER_FIRST_COMPLETION_MS || '0', 10);
const pollCount = Number.parseInt(__ENV.WORKER_POLL_COUNT || '0', 10);
const throughput = Number.parseFloat(__ENV.WORKER_THROUGHPUT || '0');
const successRate = Number.parseFloat(__ENV.WORKER_SUCCESS_RATE || '0');
const totalFailed = Number.parseInt(__ENV.WORKER_TOTAL_FAILED || '0', 10);

export default function () {
  check({
    runLabel,
    backlogSize,
    seededCount,
    completedCount,
    failedCount,
    failedOutboxCount,
    doneOutboxCount,
    pendingOutboxCount,
    processingOutboxCount,
    workerCount,
    drainDurationMs,
    firstCompletionMs,
    pollCount,
    throughput,
    successRate,
    totalFailed,
  }, {
    'all seeded jobs completed': () => completedCount === seededCount,
    'no outbox rows remain pending': () => pendingOutboxCount === 0 && processingOutboxCount === 0,
    'done outbox count matches seeded count': () => doneOutboxCount === seededCount,
    'failed job count is zero': () => failedCount === 0,
    'failed outbox count is zero': () => failedOutboxCount === 0,
    'total failures are zero': () => totalFailed === 0,
    'success rate is 100%': () => successRate === 100,
  });
}

export function handleSummary() {
  const drainDurationSeconds = (drainDurationMs / 1000).toFixed(3);
  const firstCompletionSeconds = (firstCompletionMs / 1000).toFixed(3);
  
  return {
    stdout: [
      'Worker outbox performance summary',
      `Run label: ${runLabel}`,
      '',
      '=== Backlog & Completion ===',
      `Backlog size: ${backlogSize}`,
      `Seeded jobs: ${seededCount}`,
      `Completed payments: ${completedCount}`,
      `Failed payments: ${failedCount}`,
      `Success rate: ${successRate.toFixed(2)}%`,
      `Total failures (payments + outbox): ${totalFailed}`,
      '',
      '=== Outbox Status ===',
      `Done outbox rows: ${doneOutboxCount}`,
      `Failed outbox rows: ${failedOutboxCount}`,
      `Pending outbox rows: ${pendingOutboxCount}`,
      `Processing outbox rows: ${processingOutboxCount}`,
      '',
      '=== Timing Metrics ===',
      `Drain duration: ${drainDurationSeconds}s (${drainDurationMs}ms)`,
      `Time to first completion: ${firstCompletionSeconds}s (${firstCompletionMs}ms)`,
      `Poll cycles: ${pollCount}`,
      `Throughput: ${throughput.toFixed(2)} items/second`,
    ].join('\n') + '\n',
  };
}