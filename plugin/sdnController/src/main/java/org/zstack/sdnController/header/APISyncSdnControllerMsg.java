package org.zstack.sdnController.header;

import org.springframework.http.HttpMethod;
import org.zstack.header.identity.Action;
import org.zstack.header.message.APIMessage;
import org.zstack.header.message.APIParam;
import org.zstack.header.network.l2.L2NetworkMessage;
import org.zstack.header.rest.RestRequest;

/**
 * Created by shixin.ruan on 01/04/2024.
 */
@RestRequest(
        path = "/sdn-controllers/{sdnControllerUuid}/actions",
        method = HttpMethod.PUT,
        responseClass = APISyncSdnControllerEvent.class,
        isAction = true
)
@Action(category = SdnControllerConstant.ACTION_CATEGORY)
public class APISyncSdnControllerMsg extends APIMessage implements SdnControllerMessage {
    @APIParam(resourceType = SdnControllerVO.class, checkAccount = true, operationTarget = true)
    private String sdnControllerUuid;

    public void setSdnControllerUuid(String sdnControllerUuid) {
        this.sdnControllerUuid = sdnControllerUuid;
    }

    @Override
    public String getSdnControllerUuid() {
        return sdnControllerUuid;
    }

    public static APISyncSdnControllerMsg __example__() {
        APISyncSdnControllerMsg msg = new APISyncSdnControllerMsg();
        msg.setSdnControllerUuid(uuid());

        return msg;
    }
}
