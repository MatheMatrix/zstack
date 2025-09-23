package org.zstack.header.host;

import org.springframework.http.HttpMethod;
import org.zstack.header.message.APIMessage;
import org.zstack.header.message.APIParam;
import org.zstack.header.rest.RestRequest;

@RestRequest(
        path = "/host/{uuid}/unzip-file",
        method = HttpMethod.POST,
        responseClass = APIUnzipFileEvent.class
)
public class APIUnzipFileMsg extends APIMessage implements HostMessage {
    @APIParam(nonempty = true, resourceType = HostVO.class)
    private String uuid;

    @APIParam(nonempty = true)
    private String filePath;

    @APIParam(required = false)
    private String unzipFilePath;

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getUnzipFilePath() {
        return unzipFilePath;
    }

    public void setUnzipFilePath(String unzipFilePath) {
        this.unzipFilePath = unzipFilePath;
    }

    @Override
    public String getHostUuid() {
        return uuid;
    }

    public static APIUnzipFileMsg __example__() {
        APIUnzipFileMsg msg = new APIUnzipFileMsg();
        msg.setUuid(uuid(HostVO.class));
        msg.setFilePath("/root/sds/storage.tar.gz");
        msg.setUnzipFilePath("/root/sds/storage");
        return msg;
    }
}