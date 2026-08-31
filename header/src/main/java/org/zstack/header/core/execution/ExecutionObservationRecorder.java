package org.zstack.header.core.execution;

import org.zstack.header.message.APIMessage;
import org.zstack.header.message.Message;

/**
 * Thin integration seam used by core CloudBus, REST and thread adapters.
 * Implementations must keep observation best-effort and must not change the
 * semantics of the observed operation.
 */
public interface ExecutionObservationRecorder {
    /**
     * Records an API request at acceptance time. Implementations must be best effort and must
     * not alter the API's business semantics when observation is unavailable or fails.
     */
    void recordApiRequest(APIMessage message);

    /** Records a message when it is delivered to its local handler. */
    void recordMessageDelivery(Message message);

    /** Records an API response and correlates it with the request using the API/message UUID. */
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

    /**
     * Starts an observation for a scheduled task and returns its execution UUID for completion
     * correlation.
     */
    String startScheduledTask(String taskName, String taskClass);

    /**
     * Finishes a scheduled-task observation; a non-null error produces a failed terminal state,
     * while a null error produces a successful terminal state.
     */
    void finishScheduledTask(String executionUuid, Throwable error);
}
