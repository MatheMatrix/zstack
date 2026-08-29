# ZStack Execution Observability API Test Cases

Status: API declaration, implementation contract, and integration acceptance cases.

Related specification: [TELEMETRY_SPECIFICATION.md](TELEMETRY_SPECIFICATION.md)

API declaration: [APIQueryExecutionMsg.java](../header/src/main/java/org/zstack/header/core/execution/APIQueryExecutionMsg.java) and [APIQueryExecutionReply.java](../header/src/main/java/org/zstack/header/core/execution/APIQueryExecutionReply.java).

Integration cases: [ExecutionObservabilityApiCase.groovy](../test/src/test/groovy/org/zstack/test/integration/observability/ExecutionObservabilityApiCase.groovy) and [CreateVmExecutionObservabilityCase.groovy](../test/src/test/groovy/org/zstack/test/integration/observability/CreateVmExecutionObservabilityCase.groovy). Both cases are currently annotated with `@SkipTestSuite` so they remain opt-in until the CI integration profile enrolls the new package; remove that annotation when the profile is ready.

## 1. Scope

The query resource is one `Execution` instance, not only an API request. A root execution can be created by:

- `API`: an API request, normally associated with `apiUuid`;
- `SCHEDULED_TASK`: one invocation of an internal scheduled task, identified by a unique `taskRunUuid`;
- `MESSAGE`: a non-API message without an upstream observation context.

The first phase covers management nodes and CloudBus only. Agent internals are out of scope, and business code must not be required to add instrumentation.

The external observability contract uses `*Uuid` names (`executionUuid`, `apiUuid`, `messageUuid`, `taskRunUuid`, and `stageUuid`). Existing internal `ThreadLocal` and compatibility fields such as `apiId` are not changed by this naming rule.

## 2. Contract under test

### 2.1 Search executions

```http
GET /v1/executions
```

The ZStack message is `APIQueryExecutionMsg`. The collection response is `APIQueryExecutionReply`, whose REST list field is `inventories`; the same message supports `/v1/executions/{executionUuid}` through its optional path and returns one inventory with the requested detail view.

Supported filters:

```text
executionUuid
apiUuid
messageUuid
taskRunUuid
triggerType
triggerName
state
startedAfter / startedBefore
nodeUuid
limit / cursor
```

At most one exact selector (`executionUuid`, `apiUuid`, `messageUuid`, or `taskRunUuid`) may be supplied. Without an exact selector, a bounded trigger or time filter is required to prevent an unbounded scan.

`nodeUuid` restricts the search to observations collected by one management node. When omitted, the service aggregates observations from all reachable management nodes. `state` filters the lifecycle state of the root execution; it is not the state of an individual stage. For example, `state=WAITING` means all active branches are waiting for an external condition.

### 2.2 Inspect one execution

```http
GET /v1/executions/{executionUuid}?detail=summary
GET /v1/executions/{executionUuid}?detail=timeline
GET /v1/executions/{executionUuid}?detail=criticalPath
```

`summary` is the default view. `timeline` and `criticalPath` must support a server-side limit or cursor. The operation is read-only and idempotent.

### 2.3 Minimum response contract

Every execution summary must contain:

```text
executionUuid
trigger.type / trigger.name
nodeUuid
state
acceptedAt / startedAt / finishedAt
elapsedMs / lastHeartbeatAt / observedAt
activeStages[] (each stage has `stageUuid`)
partial / sourceNodes
```

When available, the response also contains `apiUuid`, `messageUuid`, `taskRunUuid`, `rootMessageUuid`, `operationId`, `traceId`, `retries`, `error`, and `expiresAt`. Missing correlation UUIDs are `null`, not a value borrowed from another identifier type.

The execution state is one of:

```text
RECEIVED | QUEUED | RUNNING | WAITING | SUCCEEDED
FAILED | TIMEOUT | CANCELLED | UNKNOWN | STALE | EXPIRED
```

### 2.4 Current implementation boundary

The first implementation keeps a bounded registry in each management node and
aggregates it through CloudBus. It covers API admission/response, CloudBus
message delivery/reply/timeout/cancellation, `ThreadFacade`
periodic/cancelable/timeout/timer tasks, and inherited message stages. Execution
context is propagated through the existing CloudBus task context, so child
messages remain correlated when they cross management nodes. Agent spans and
durable restart recovery remain follow-up work; a node restart can therefore
produce `partial`/`UNKNOWN` data until a persistent store is introduced.

## 3. Invariants

1. `executionUuid` is globally unique for one root execution.
2. A child Message, Task, Flow, or callback with observation context inherits the same `executionUuid`.
3. A retry remains in the same execution and is represented by a new `attempt` or retry event.
4. If any active branch can still make progress, the global state is `RUNNING`. The state is `WAITING` only when all active branches wait for an external condition.
5. Failure to aggregate one management node produces `partial=true`; it must not become a business `FAILED` or `NOT_FOUND` result.
6. If the final state cannot be confirmed after a node restart, return `UNKNOWN` or `STALE`, never an invented success or failure.
7. An uninstrumented agent is a known visibility boundary (`visibility=BOUNDARY_ONLY`), not missing management-node data (`partial=true`).
8. Timeline pagination is stable: no duplicate or missing events across pages.
9. An expired tombstone returns `state=EXPIRED`; an execution that was never observed returns `NOT_FOUND`.

## 4. Functional test cases

Each case is written as Given/When/Then and the executable cases live in
`test/src/test/groovy/org/zstack/test/integration/observability/`.

### EXE-001 API lookup by apiUuid

**Given** an API request creates `apiUuid=A1` and root `executionUuid=E1`.

**When** calling `GET /v1/executions?apiUuid=A1`.

**Then** exactly one execution is returned, with `executionUuid=E1`, `trigger.type=API`, and `trigger.apiUuid=A1`.

### EXE-002 In-progress API summary

**Given** `E1` is waiting for a CloudBus reply.

**When** calling `GET /v1/executions/E1?detail=summary`.

**Then** the response is `WAITING` and includes `activeStages`, node UUID, structured waiting reason, `lastHeartbeatAt`, and elapsed time without requiring business-side instrumentation.

### EXE-003 Completed API

**Given** `E1` receives an `APIReply` or `APIEvent`.

**When** querying both summary and timeline.

**Then** a terminal state and `startedAt`, `finishedAt`, total duration, and completed stages are returned. Repeating the query does not mutate the result.

### EXE-004 Failed, timed-out, and cancelled executions

**Given** three executions terminate with business failure, downstream timeout, and cancellation.

**When** querying each execution.

**Then** they return `FAILED`, `TIMEOUT`, and `CANCELLED`, respectively, with the failing or timed-out stage, error summary, and attempt information.

### EXE-005 Independent scheduled-task runs

**Given** task definition `T1` fires twice at different times.

**When** searching by `triggerType=SCHEDULED_TASK`, task name, and bounded time range.

**Then** two different `taskRunUuid` and `executionUuid` values are returned; a fixed task-definition ID must not overwrite previous runs.

### EXE-006 Scheduled task message inheritance

**Given** scheduled run `E1` sends a CloudBus message that is consumed by another management node.

**When** querying by `taskRunUuid` or the child `messageUuid`.

**Then** both resolve to `E1`, and the message event references the scheduled-task event as its parent.

### EXE-007 Root execution for context-free Message

**Given** CloudBus receives an internal Message without `executionUuid`.

**When** the consumer initializes observation context.

**Then** a `MESSAGE` root execution is created with `rootMessageUuid` equal to the message UUID, and lookup by that message UUID succeeds.

### EXE-008 Context-bearing Message does not fork the root

**Given** a Message carries context for `E1`.

**When** it is sent and consumed by either management node.

**Then** its event remains under `E1`; no second root execution is created.

### EXE-009 Parallel branches

**Given** `E1` has two active branches, one running and one waiting.

**When** querying summary.

**Then** global state is `RUNNING` and both branches appear in `activeStages`; a single current-stage field must not hide a branch.

### EXE-010 All branches waiting

**Given** every active branch of `E1` waits for a lock, resource, CloudBus reply, or external response.

**When** querying summary.

**Then** global state is `WAITING`, and every active stage has a structured `waitingOn` or `waitingReason`.

### EXE-011 Retry accounting

**Given** a downstream call fails and is retried twice.

**When** querying timeline.

**Then** one `executionUuid` contains three attempts or equivalent retry events, with execution duration separated from `retryBackoff`.

### EXE-012 Cross-management-node consistency

**Given** the root starts on M1 and child stages execute on M2.

**When** querying the same execution through M1 and M2.

**Then** UUIDs, state, and parent/child relationships are consistent, and `sourceNodes` lists the participating nodes.

### EXE-013 Management-node aggregation failure

**Given** M2 is temporarily unreachable while M1 has a partial summary for `E1`.

**When** querying through M1.

**Then** available data is returned with `partial=true` and a missing-source timestamp or reason; the result is not `NOT_FOUND` or business `FAILED`.

### EXE-014 Root-node restart

**Given** the root node restarts before the final business result is known.

**When** querying `E1`.

**Then** return `UNKNOWN` or `STALE` with the last observation time, not a guessed terminal state.

### EXE-015 Agent visibility boundary

**Given** a management node sends a request to an agent that has no instrumentation.

**When** querying the timeline.

**Then** return the agent request target and waiting state with `visibility=BOUNDARY_ONLY`; `partial` still only describes management-node data completeness.

### EXE-016 Stable timeline pagination

**Given** `E1` produces more events than the page limit.

**When** walking pages with `limit` and the returned `cursor`.

**Then** order is stable, events are neither duplicated nor skipped, and an invalid cursor returns a parameter error without changing execution state.

### EXE-017 Critical path

**Given** parallel branches exist and one slow branch determines the completion time.

**When** querying `detail=criticalPath`.

**Then** return the causal branch, stage durations, and node that determine total latency; parallel durations are not simply summed as total duration.

### EXE-018 Trace backend unavailable

**Given** a local execution summary exists but its Trace backend record is sampled out or delayed.

**When** querying summary or timeline.

**Then** return the available execution summary and `traceAvailable=false` or equivalent; absence of a Trace record must not remove state visibility.

### EXE-019 Expired execution

**Given** `E1` exceeds the execution-summary retention period.

**When** querying `E1`.

**Then** return `state=EXPIRED` and expiry metadata, distinguishable from an execution that never existed.

### EXE-020 Authorization and redaction

**Given** the caller is not an administrator or troubleshooting role.

**When** requesting internal stages, node details, message bodies, or full errors.

**Then** deny the request or return a redacted view; credentials, raw request payloads, complete message bodies, thread dumps, and internal addresses are not exposed.

### EXE-021 Read-only and idempotent query

**Given** one execution is queried repeatedly with summary, timeline, and critical path views.

**When** the calls complete.

**Then** they do not trigger retry, compensation, cancellation, or business-state changes, and do not add execution events.

### EXE-022 Fan-out event limit

**Given** a batch API fans out many messages and child tasks.

**When** querying timeline or children.

**Then** server limits and pagination are respected, `truncated=true` or equivalent is returned when needed, and summary remains within the response-time budget.

### EXE-023 Create VM API lookup

**Given** the integration environment contains an instance offering, image, and L3 network, and `CreateVmInstance` is called with `apiUuid=A1`.

**When** the VM is created and calling `GET /v1/executions?apiUuid=A1`.

**Then** exactly one execution is returned for the VM creation API, its `executionUuid` is present, `trigger.type=API`, and the timeline contains the API admission event and stages with `stageUuid`.

## 5. Request validation cases

| ID | Input | Expected result |
|---|---|---|
| VAL-001 | No selector, trigger filter, or bounded time range | Parameter error; reject an unbounded scan |
| VAL-002 | More than one exact selector, such as `apiUuid` and `messageUuid` | Parameter error |
| VAL-003 | `limit < 1` or above the server maximum | Parameter error or documented truncation with the effective limit |
| VAL-004 | Invalid `state`, `triggerType`, or `detail` | Parameter error; do not silently use a default |
| VAL-005 | `startedBefore` earlier than `startedAfter` | Parameter error |
| VAL-006 | Unknown `executionUuid` | `404 NOT_FOUND` without revealing other executions |
| VAL-007 | Existing execution queried without permission | `403` or security-equivalent masked result |

## 6. Automation order

1. Convert the response fields, enum values, and validation rules into an OpenAPI/JSON Schema contract test.
2. Add single-management-node `SubCase` tests for API, scheduled task, root Message, child Message, retry, and parallel branches.
3. Add two-management-node integration cases for propagation, query routing, aggregation timeout, and node restart.
4. Add authorization, retention, Trace sampling, pagination, event-limit, and query-latency regression cases.

The concrete storage, Trace backend, and watch/stream mechanism are implementation choices. They must not change the external states and query semantics above. The current bounded in-memory registry is intentionally an MVP; durable retention and restart recovery are follow-up work.
