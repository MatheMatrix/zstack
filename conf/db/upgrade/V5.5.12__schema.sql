-- ZSTAC-68709: Add targetQueueKey column for evaluation task queue concurrency control
CALL ADD_COLUMN('ModelEvaluationTaskVO', 'targetQueueKey', 'VARCHAR(512)', 1, NULL);

-- ZSTAC-70478: Add deleted (soft-delete) column for ModelServiceVO
CALL ADD_COLUMN('ModelServiceVO', 'deleted', 'tinyint(1)', 0, '0');
