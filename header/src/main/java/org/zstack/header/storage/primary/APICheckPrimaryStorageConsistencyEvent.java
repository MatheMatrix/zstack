package org.zstack.header.storage.primary;

import org.zstack.header.message.APIEvent;
import org.zstack.header.rest.RestResponse;

@RestResponse(fieldsTo = {"all"})
public class APICheckPrimaryStorageConsistencyEvent extends APIEvent {
    private boolean consistent;

    public APICheckPrimaryStorageConsistencyEvent() {
    }

    public APICheckPrimaryStorageConsistencyEvent(String apiId) {
        super(apiId);
    }

    public boolean isConsistent() {
        return consistent;
    }

    public void setConsistent(boolean consistent) {
        this.consistent = consistent;
    }

    public static APICheckPrimaryStorageConsistencyEvent __example__() {
        APICheckPrimaryStorageConsistencyEvent event = new APICheckPrimaryStorageConsistencyEvent();
        event.setConsistent(true);
        return event;
    }
}