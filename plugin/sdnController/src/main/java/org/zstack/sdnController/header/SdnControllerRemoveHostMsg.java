package org.zstack.sdnController.header;

import org.zstack.header.message.NeedReplyMessage;

public class SdnControllerRemoveHostMsg extends NeedReplyMessage implements SdnControllerMessage {
    private String sdnControllerUuid;
    private String hostUuid;
    private String vSwitchType;

    public static SdnControllerRemoveHostMsg fromApi(APISdnControllerRemoveHostMsg amsg) {
        SdnControllerRemoveHostMsg msg = new SdnControllerRemoveHostMsg();
        msg.setSdnControllerUuid(amsg.getSdnControllerUuid());
        msg.setHostUuid(amsg.getHostUuid());
        msg.setvSwitchType(amsg.getvSwitchType());
        return msg;
    }

    public void setSdnControllerUuid(String sdnControllerUuid) {
        this.sdnControllerUuid = sdnControllerUuid;
    }

    public String getHostUuid() {
        return hostUuid;
    }

    public void setHostUuid(String hostUuid) {
        this.hostUuid = hostUuid;
    }

    public String getvSwitchType() {
        return vSwitchType;
    }

    public void setvSwitchType(String vSwitchType) {
        this.vSwitchType = vSwitchType;
    }

    @Override
    public String getSdnControllerUuid() {
        return sdnControllerUuid;
    }
}
