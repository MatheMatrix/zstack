package org.zstack.header.core.execution;

import java.util.List;

/**
 * System-level execution observation hooks. Implementations must be non-blocking
 * from the caller's point of view; observation must never change message semantics.
 */
public interface ExecutionObservabilityFacade extends ExecutionObservationRecorder {
    List<ExecutionInventory> queryLocal(APIQueryExecutionMsg query);
}
