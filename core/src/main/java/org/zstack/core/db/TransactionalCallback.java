package org.zstack.core.db;

public interface TransactionalCallback {
    public static enum TransactionalOperation {
        PERSIST,
        UPDATE,
        REMOVE,
    }
    
    void suspend(Class<?>...entityClass);
    
    void resume(Class<?>...entityClass);

    void flush(Class<?>...entityClass);

    void beforeCommit(TransactionalOperation op, boolean readOnly, Class<?>...entityClass);

    void beforeCompletion(TransactionalOperation op, Class<?>...entityClass);

    void afterCommit(TransactionalOperation op, Class<?>...entityClass);

    void afterCompletion(TransactionalOperation op, int status, Class<?>...entityClass);
}
