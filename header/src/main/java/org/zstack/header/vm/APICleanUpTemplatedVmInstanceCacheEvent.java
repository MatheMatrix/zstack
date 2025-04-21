package org.zstack.header.vm;

import org.zstack.header.message.APIEvent;
import org.zstack.header.rest.RestResponse;

@RestResponse
public class APICleanUpTemplatedVmInstanceCacheEvent extends APIEvent {
    public APICleanUpTemplatedVmInstanceCacheEvent() {
        super(null);
    }

    public APICleanUpTemplatedVmInstanceCacheEvent(String apiId) {
        super(apiId);
    }

    public static APICleanUpTemplatedVmInstanceCacheEvent __example__() {
        APICleanUpTemplatedVmInstanceCacheEvent event = new APICleanUpTemplatedVmInstanceCacheEvent();
        event.setSuccess(true);
        return event;
    }
}
