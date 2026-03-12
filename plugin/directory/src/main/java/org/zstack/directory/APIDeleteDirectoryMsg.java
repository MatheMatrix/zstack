package org.zstack.directory;

import org.springframework.http.HttpMethod;
import org.zstack.header.message.APIDeleteMessage;
import org.zstack.header.message.APIParam;
import org.zstack.header.rest.RestRequest;
import org.zstack.header.vm.MetadataImpact;

/**
 * @author shenjin
 * @date 2022/11/29 14:05
 */
@RestRequest(
        path = "/delete/directory",
        method = HttpMethod.DELETE,
        responseClass = APIDeleteDirectoryEvent.class
)
@MetadataImpact(MetadataImpact.Impact.NONE)
public class APIDeleteDirectoryMsg extends APIDeleteMessage implements DirectoryMessage, OperateDirectoryMessage {
    @APIParam(resourceType = DirectoryVO.class, successIfResourceNotExisting = true)
    private String uuid;

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public static APIDeleteDirectoryMsg __example__() {
        APIDeleteDirectoryMsg msg = new APIDeleteDirectoryMsg();
        msg.setUuid(uuid());
        return msg;
    }

    @Override
    public String getDirectoryUuid() {
        return uuid;
    }
}
