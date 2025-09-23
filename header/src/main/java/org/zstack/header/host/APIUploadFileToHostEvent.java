package org.zstack.header.host;

import org.zstack.header.message.APIEvent;
import org.zstack.header.rest.RestResponse;

@RestResponse(fieldsTo = {"all"})
public class APIUploadFileToHostEvent extends APIEvent {
    private String md5sum;

    public String getMd5sum() {
        return md5sum;
    }

    public void setMd5sum(String md5sum) {
        this.md5sum = md5sum;
    }

    public APIUploadFileToHostEvent() {
        super(null);
    }

    public APIUploadFileToHostEvent(String apiId) {
        super(apiId);
    }

    public static APIUploadFileToHostEvent __example__() {
        APIUploadFileToHostEvent event = new APIUploadFileToHostEvent();
        event.setMd5sum("5d41402abc4b2a76b9719d911017c592");
        return event;
    }
}
