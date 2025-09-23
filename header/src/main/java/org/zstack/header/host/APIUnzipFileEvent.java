package org.zstack.header.host;

import org.zstack.header.message.APIEvent;
import org.zstack.header.rest.RestResponse;

@RestResponse(fieldsTo = {"all"})
public class APIUnzipFileEvent extends APIEvent {
    private String unzipFilePath;

    public String getUnzipFilePath() {
        return unzipFilePath;
    }

    public void setUnzipFilePath(String unzipFilePath) {
        this.unzipFilePath = unzipFilePath;
    }

    public APIUnzipFileEvent() {
        super(null);
    }

    public APIUnzipFileEvent(String apiId) {
        super(apiId);
    }

    public static APIUnzipFileEvent __example__() {
        APIUnzipFileEvent event = new APIUnzipFileEvent();
        event.setUnzipFilePath("/root/sds/storage");
        return event;
    }
}
