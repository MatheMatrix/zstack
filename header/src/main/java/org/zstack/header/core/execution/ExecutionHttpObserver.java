package org.zstack.header.core.execution;

/** Observes outbound HTTP child stages under the current execution context. */
public interface ExecutionHttpObserver {
    /** Start an outbound HTTP child stage under the current execution context. */
    String startHttpRequest(String method, String url);

    /** Finish an outbound HTTP child stage with a terminal state and status. */
    void finishHttpRequest(String requestUuid, String state, Integer statusCode, String error);
}
