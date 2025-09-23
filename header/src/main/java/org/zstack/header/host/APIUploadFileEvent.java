package org.zstack.header.host;

import org.zstack.header.message.APIEvent;
import org.zstack.header.rest.RestResponse;

@RestResponse(fieldsTo = {"all"})
public class APIUploadFileEvent extends APIEvent {
    public static APIUploadFileEvent __example__() {
        APIUploadFileEvent event = new APIUploadFileEvent();
        return event;
    }
}
