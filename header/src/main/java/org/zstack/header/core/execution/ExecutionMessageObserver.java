package org.zstack.header.core.execution;

/**
 * Observes terminal outcomes of CloudBus messages.
 *
 * <p>The delivery hook is owned by the observability component itself because
 * it is installed as a CloudBus interceptor. Core CloudBus adapters only need
 * to report timeout and cancellation paths through this interface.</p>
 */
public interface ExecutionMessageObserver {
    /** Mark a request or child stage that reached the CloudBus timeout path. */
    void recordMessageTimeout(String messageUuid);

    /** Mark a request or child stage cancelled before a reply arrived. */
    void recordMessageCancellation(String messageUuid, String reason);
}
