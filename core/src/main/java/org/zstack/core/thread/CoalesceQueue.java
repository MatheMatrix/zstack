package org.zstack.core.thread;

import org.zstack.header.core.AbstractCompletion;
import org.zstack.header.core.Completion;
import org.zstack.header.errorcode.ErrorCode;

import java.util.List;

/**
 * A coalesce queue for requests that do NOT expect a return value.
 *
 * @param <T> Request Item Type
 */
public abstract class CoalesceQueue<T> extends AbstractCoalesceQueue<T, Void, Void> {

    /**
     * Submit a request.
     *
     * @param syncSignature the sync signature; requests with the same signature will be coalesced
     * @param item          the request item
     * @param completion    the completion callback
     */
    public void submit(String syncSignature, T item, Completion completion) {
        submitRequest(syncSignature, item, completion);
    }

    /**
     * Executes the batched requests.
     * <p>
     * Subclasses must implement this method to process the coalesced items.
     *
     * @param items      the list of coalesced request items
     * @param completion the completion callback for the batch execution
     */
    protected abstract void executeBatch(List<T> items, Completion completion);

    @Override
    protected final void executeBatch(List<T> items, AbstractCompletion batchCompletion) {
        executeBatch(items, (Completion) batchCompletion);
    }

    @Override
    protected final AbstractCompletion createBatchCompletion(String syncSignature, List<PendingRequest> requests, SyncTaskChain chain) {
        return new Completion(chain) {
            @Override
            public void success() {
                handleSuccess(syncSignature, requests, null, chain);
            }

            @Override
            public void fail(ErrorCode errorCode) {
                handleFailure(syncSignature, requests, errorCode, chain);
            }
        };
    }

    @Override
    protected final Void calculateResult(T item, Void batchResult) {
        return null;
    }
}
