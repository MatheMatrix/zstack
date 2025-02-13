package org.zstack.sdnController.header;

import org.zstack.header.message.NeedReplyMessage;

public class ReconnectSdnControllerMsg extends NeedReplyMessage implements SdnControllerMessage {
    String controllerUUid;

    public String getControllerUUid() {
        return controllerUUid;
    }

    public void setControllerUUid(String controllerUUid) {
        this.controllerUUid = controllerUUid;
    }

    @Override
    public String getSdnControllerUuid() {
        return controllerUUid;
    }
}
