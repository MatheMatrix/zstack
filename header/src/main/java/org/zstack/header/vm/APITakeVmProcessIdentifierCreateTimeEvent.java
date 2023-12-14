package org.zstack.header.vm;

import org.zstack.header.message.APIEvent;
import org.zstack.header.rest.RestResponse;

@RestResponse(fieldsTo = {"createTime"})
public class APITakeVmProcessIdentifierCreateTimeEvent extends APIEvent {
    private String createTime;

    public APITakeVmProcessIdentifierCreateTimeEvent() {
    }

    public APITakeVmProcessIdentifierCreateTimeEvent(String apiId) {
        super(apiId);
    }

    public String getCreateTime() {
        return createTime;
    }

    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }

    public static APITakeVmProcessIdentifierCreateTimeEvent __example__() {
        APITakeVmProcessIdentifierCreateTimeEvent event = new APITakeVmProcessIdentifierCreateTimeEvent();
        event.setCreateTime("Mon Dec 11 18:10:23 2023");
        return event;
    }
}
