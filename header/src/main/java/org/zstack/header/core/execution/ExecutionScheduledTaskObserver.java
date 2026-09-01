package org.zstack.header.core.execution;

/** Observes individual invocations of scheduled and timer tasks. */
public interface ExecutionScheduledTaskObserver {
    String startScheduledTask(String taskName, String taskClass);

    void finishScheduledTask(String executionUuid, Throwable error);
}
