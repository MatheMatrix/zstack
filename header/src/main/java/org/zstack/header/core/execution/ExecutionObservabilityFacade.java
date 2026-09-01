package org.zstack.header.core.execution;

import java.util.List;

/**
 * System-level execution observation query facade.
 *
 * <p>Integration hooks are exposed through narrow observer interfaces so that
 * CloudBus, REST, and thread adapters only depend on the lifecycle they report.</p>
 */
public interface ExecutionObservabilityFacade {
    List<ExecutionInventory> queryLocal(APIQueryExecutionMsg query);
}
