package org.zstack.header.host;

import org.springframework.http.HttpMethod;
import org.zstack.header.log.NoLogging;
import org.zstack.header.message.APIMessage;
import org.zstack.header.message.APIParam;
import org.zstack.header.rest.RestRequest;

@RestRequest(
        path = "/host/{uuid}/upload-file",
        method = HttpMethod.POST,
        responseClass = APIUploadFileEvent.class
)
public class APIUploadFileMsg extends APIMessage implements HostMessage {
    @APIParam(nonempty = true, resourceType = HostVO.class)
    private String uuid;

    @NoLogging(type = NoLogging.Type.Uri)
    private String url;

    private String installPath;

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getInstallPath() {
        return installPath;
    }

    public void setInstallPath(String installPath) {
        this.installPath = installPath;
    }

    @Override
    public String getHostUuid() {
        return uuid;
    }

    public static APIUploadFileMsg __example__() {
        APIUploadFileMsg msg = new APIUploadFileMsg();
        msg.setUuid(uuid());
        msg.setUrl("http://192.168.1.1/disk/images/test.qcow2");
        msg.setInstallPath("/root/sds/storage.tar.gz");
        return msg;
    }
}