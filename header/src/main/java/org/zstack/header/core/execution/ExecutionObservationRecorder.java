package org.zstack.header.core.execution;

import org.zstack.header.message.APIMessage;
import org.zstack.header.message.Message;

/**
 * Thin integration seam used by core CloudBus, REST and thread adapters.
 * Implementations must keep observation best-effort and must not change the
 * semantics of the observed operation.
 */
public interface ExecutionObservationRecorder {
    void recordApiRequest(APIMessage message);

    void recordMessageDelivery(Message message);

    void recordApiResponse(Message message);

    /** Mark a request or child stage that reached the CloudBus timeout path. */
    default void recordMessageTimeout(String messageUuid) {
    }

    /** Mark a request or child stage cancelled before a reply arrived. */
    default void recordMessageCancellation(String messageUuid, String reason) {
    }

    /** Start an outbound HTTP child stage under the current execution context. */
    default String startHttpRequest(String method, String url) {
        return null;
    }

    /** Finish an outbound HTTP child stage with a terminal state and status. */
    default void finishHttpRequest(String requestUuid, String state, Integer statusCode, String error) {
    }

    String startScheduledTask(String taskName, String taskClass);

    void finishScheduledTask(String executionUuid, Throwable error);
}
