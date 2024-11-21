package org.zstack.core.aspect;

/**
 */
public aspect AspectOrder {
    declare precedence : ExceptionSafeAspect, ThreadAspect, MessageSafeAspect, AsyncSafeAspect, AsyncBackupAspect, LogSafeAspect, BackAspect,
            CompletionSingleCallAspect, EncryptAspect, DecryptAspect, DbDeadlockAspect, DbConnectionPoolAspect, DeferAspect, AnnotationTransactionAspect, UnitTestBypassMethodAspect;
}
