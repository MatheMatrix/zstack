package org.zstack.header.host;

import org.zstack.header.message.APIEvent;
import org.zstack.header.rest.RestResponse;

@RestResponse(fieldsTo = {"all"})
public class APIUploadFileEvent extends APIEvent {
    public APIUploadFileEvent() {
        super(null);
    }

    public APIUploadFileEvent(String apiId) {
        super(apiId);
    }

    public static APIUploadFileEvent __example__() {
        APIUploadFileEvent event = new APIUploadFileEvent();
        return event;
    }
}
