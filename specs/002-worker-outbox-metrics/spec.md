# Feature Specification: Worker Outbox Performance Metrics

**Feature Branch**: `002-worker-outbox-metrics`  
**Created**: 2026-05-06  
**Status**: Draft  
**Input**: User description: "within the performance tests, there is a test which performs testing on the producer. Create another test which tests metrcis of the worker and how long it takes to complete jobs in the outbox."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Measure Worker Completion Time (Priority: P1)

An engineer runs a worker-focused performance test against a prepared outbox backlog and wants to see how long jobs take to reach a completed state.

**Why this priority**: This is the core value of the new test and the main reason to add it to the performance suite.

**Independent Test**: Run the worker scenario with a non-empty backlog and verify that the report includes job completion latency and total drain time.

**Acceptance Scenarios**:

1. **Given** a prepared backlog of outbox jobs, **When** the worker performance test runs, **Then** the report includes how long it took to complete the jobs and how many jobs reached a terminal state.
2. **Given** jobs complete successfully during the run, **When** the test finishes, **Then** the report includes a completion summary that can be compared with earlier runs.

---

### User Story 2 - Compare Worker Load Behavior (Priority: P2)

An engineer varies worker load conditions to understand how concurrency affects completion time and backlog drain rate.

**Why this priority**: This helps determine whether worker capacity is sufficient under expected load.

**Independent Test**: Run the worker scenario with different backlog sizes or worker counts and compare the resulting completion metrics.

**Acceptance Scenarios**:

1. **Given** two worker test runs with different load conditions, **When** the results are compared, **Then** the report clearly shows whether completion time improved or degraded.
2. **Given** a larger backlog than the baseline run, **When** the worker test completes, **Then** the summary shows the longer or shorter drain time in a way that can be tracked over time.

---

### User Story 3 - Separate Producer and Worker Results (Priority: P3)

An engineer wants the existing producer performance test and the new worker performance test to stay distinct so each measurement answers a different question.

**Why this priority**: Clear separation avoids confusing request throughput with job completion performance.

**Independent Test**: Run the producer scenario and the worker scenario separately and confirm they produce distinct summaries.

**Acceptance Scenarios**:

1. **Given** the performance suite contains both producer and worker scenarios, **When** a user runs the worker scenario, **Then** the results focus on outbox completion metrics and not producer request generation.
2. **Given** a user runs the producer scenario, **When** the test finishes, **Then** the existing producer-focused results remain unchanged.

---

### Edge Cases

- What happens when the worker backlog is empty at the start of the run? The test should report that no jobs were available rather than appearing stalled.
- What happens when some jobs are retried before completion? The report should still include the final completion time and note that retries occurred.
- What happens when one run has a much larger backlog than another? The summary should remain comparable and show the difference in drain time.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The performance test suite MUST include a worker-focused scenario that measures how long outbox jobs take to reach a terminal state.
- **FR-002**: The worker scenario MUST report total backlog drain time, completed job count, and job completion latency for each run.
- **FR-003**: The worker scenario MUST distinguish between completed, retried, and failed jobs in its output.
- **FR-004**: The worker scenario MUST be runnable independently from the existing producer performance scenario.
- **FR-005**: The worker scenario MUST support repeatable runs so results can be compared across different load conditions.
- **FR-006**: The worker scenario MUST allow the test operator to vary the workload size and worker capacity being exercised.

### Key Entities *(include if feature involves data)*

- **Worker Performance Run**: A single execution of the worker-focused test, including workload size, worker capacity, and summary output.
- **Outbox Job**: A queued payment-processing item whose completion time is measured by the test.
- **Performance Summary**: The reported results for a run, including drain time, completion latency, and counts of completed, retried, and failed jobs.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Every worker test run against a non-empty backlog reports total drain time and completion latency for processed jobs.
- **SC-002**: For runs with at least 100 outbox jobs, the report includes a completion summary that can be compared across runs without manual recalculation.
- **SC-003**: Users can execute the producer scenario and the worker scenario separately and receive distinct summaries from each.
- **SC-004**: At least 95% of completed jobs in a worker run are represented in the final performance summary.

## Assumptions

- The existing producer performance test remains available as a separate scenario.
- The worker test measures job completion from the perspective of the test run and the observed outbox lifecycle.
- The performance harness can be configured with different backlog sizes and worker loads without changing the test definition itself.
- A run with no queued jobs is a valid edge case and should produce a clear, non-failing summary.
