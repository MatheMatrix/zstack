package org.zstack.header.core.execution;

import org.zstack.header.message.APIMessage;
import org.zstack.header.message.Message;

import java.util.List;

/**
 * System-level execution observation hooks. Implementations must be non-blocking
 * from the caller's point of view; observation must never change message semantics.
 */
public interface ExecutionObservabilityFacade {
    void recordApiRequest(APIMessage message);

    void recordMessageDelivery(Message message);

    void recordApiResponse(Message message);

    /** Mark a request or a child stage that reached the CloudBus timeout path. */
    default void recordMessageTimeout(String messageUuid) {
    }

    /** Mark a request or a child stage cancelled before a reply arrived. */
    default void recordMessageCancellation(String messageUuid, String reason) {
    }

    String startScheduledTask(String taskName, String taskClass);

    void finishScheduledTask(String executionUuid, Throwable error);

    List<ExecutionInventory> queryLocal(APIQueryExecutionMsg query);
}
