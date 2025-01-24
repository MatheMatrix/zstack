package org.zstack.sdnController;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.core.cloudbus.CloudBus;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.core.db.Q;
import org.zstack.header.apimediator.ApiMessageInterceptionException;
import org.zstack.header.apimediator.ApiMessageInterceptor;
import org.zstack.header.apimediator.GlobalApiMessageInterceptor;
import org.zstack.header.message.APIMessage;
import org.zstack.header.network.l2.APIAttachL2NetworkToClusterMsg;
import org.zstack.header.network.l2.L2NetworkConstant;
import org.zstack.network.l2.vxlan.vxlanNetwork.APICreateL2VxlanNetworkMsg;
import org.zstack.sdnController.header.*;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.zstack.core.Platform.argerr;

/**
 * Created by shixin.ruan on 09/17/2019
 */
public class SdnControllerApiInterceptor implements ApiMessageInterceptor, GlobalApiMessageInterceptor {
    private static final CLogger logger = Utils.getLogger(SdnControllerApiInterceptor.class);

    @Autowired
    protected DatabaseFacade dbf;
    @Autowired
    protected CloudBus bus;

    private void setServiceId(APIMessage msg) {
        if (msg instanceof SdnControllerMessage) {
            SdnControllerMessage smsg = (SdnControllerMessage) msg;
            bus.makeTargetServiceIdByResourceUuid(msg, SdnControllerConstant.SERVICE_ID, smsg.getSdnControllerUuid());
        }
    }

    public List<Class> getMessageClassToIntercept() {
        List<Class> ret = new ArrayList<>();
        ret.add(APICreateL2VxlanNetworkMsg.class);
        ret.add(APIAttachL2NetworkToClusterMsg.class);
        ret.add(APIAddSdnControllerMsg.class);
        ret.add(APIRemoveSdnControllerMsg.class);

        return ret;
    }

    public InterceptorPosition getPosition() {
        return InterceptorPosition.END;
    }

    public APIMessage intercept(APIMessage msg) throws ApiMessageInterceptionException {
        if (msg instanceof APICreateL2VxlanNetworkMsg) {
            validate((APICreateL2VxlanNetworkMsg)msg);
        } else if (msg instanceof APIAttachL2NetworkToClusterMsg){
            validate((APIAttachL2NetworkToClusterMsg)msg);
        } else if (msg instanceof APIAddSdnControllerMsg) {
            validate((APIAddSdnControllerMsg)msg);
        } else if (msg instanceof APISdnControllerAddHostMsg) {
            validate((APISdnControllerAddHostMsg)msg);
        } else if (msg instanceof APISdnControllerRemoveHostMsg) {
            validate((APISdnControllerRemoveHostMsg)msg);
        } else if (msg instanceof APISdnControllerChangeHostMsg) {
            validate((APISdnControllerChangeHostMsg)msg);
        }

        setServiceId(msg);

        return msg;
    }

    private void validate(APICreateL2VxlanNetworkMsg msg) {
    }

    private void validate(APIAttachL2NetworkToClusterMsg msg) {
    }

    private void validate(APIAddSdnControllerMsg msg) {
        if (!SdnControllerType.getAllTypeNames().contains(msg.getVendorType())) {
            throw new ApiMessageInterceptionException(argerr("Sdn controller type: %s in not in the supported list: %s ", msg.getVendorType(), SdnControllerType.getAllTypeNames()));
        }

        boolean existed = Q.New(SdnControllerVO.class).eq(SdnControllerVO_.ip, msg.getIp()).isExists();
        if (existed) {
            throw new ApiMessageInterceptionException(argerr("Sdn controller with ip [%s] is already added ", msg.getIp()));
        }
    }

    private void validate(APISdnControllerAddHostMsg msg) {
        if (Q.New(SdnControllerHostRefVO.class)
                .eq(SdnControllerHostRefVO_.sdnControllerUuid, msg.getSdnControllerUuid())
                .eq(SdnControllerHostRefVO_.hostUuid, msg.getHostUuid()).isExists()) {
            throw new ApiMessageInterceptionException(argerr("could not add host[uuid:%s] to sdn controller[uuid:%s], " +
                            " because host already add to sdn controller", msg.getHostUuid(), msg.getSdnControllerUuid()));
        }

        if (msg.getVtepIp() != null && msg.getNetmask() == null) {
            throw new ApiMessageInterceptionException(argerr("could not add host[uuid:%s] to sdn controller[uuid:%s], " +
                    " because netmask is specified", msg.getHostUuid(), msg.getSdnControllerUuid()));
        }

        if (msg.getNicNames().size() > 1 && msg.getBondMode() == null) {
            msg.setBondMode(L2NetworkConstant.BONDING_MODE_AB);
        }

        if (msg.getBondMode() != null && msg.getLacpMode() == null) {
            msg.setLacpMode(L2NetworkConstant.LACP_MODE_OFF);
        }
    }

    private void validate(APISdnControllerRemoveHostMsg msg) {
        if (!Q.New(SdnControllerHostRefVO.class)
                .eq(SdnControllerHostRefVO_.sdnControllerUuid, msg.getSdnControllerUuid())
                .eq(SdnControllerHostRefVO_.hostUuid, msg.getHostUuid()).isExists()) {
            throw new ApiMessageInterceptionException(argerr("could not remove host[uuid:%s] from sdn controller[uuid:%s], " +
                    " because host has not been added to sdn controller", msg.getHostUuid(), msg.getSdnControllerUuid()));
        }
    }

    private void validate(APISdnControllerChangeHostMsg msg) {
        SdnControllerHostRefVO refVO = Q.New(SdnControllerHostRefVO.class)
                .eq(SdnControllerHostRefVO_.sdnControllerUuid, msg.getSdnControllerUuid())
                .eq(SdnControllerHostRefVO_.hostUuid, msg.getHostUuid()).find();
        if (refVO == null) {
            throw new ApiMessageInterceptionException(argerr("could not change host[uuid:%s] of sdn controller[uuid:%s], " +
                    " because host has not been added to sdn controller", msg.getHostUuid(), msg.getSdnControllerUuid()));
        }

        if (msg.getVtepIp() != null && msg.getNetmask() == null) {
            throw new ApiMessageInterceptionException(argerr("could not change host[uuid:%s] of sdn controller[uuid:%s], " +
                    " because netmask is specified", msg.getHostUuid(), msg.getSdnControllerUuid()));
        }

        if (msg.getNicNames() == null) {
            Gson gson = new Gson();
            Type type = new TypeToken<Map<String, String>>(){}.getType();
            Map<String, String> nicNamePciAddressMap = gson.fromJson(refVO.getNicPciAddresses(), type);
            msg.setNicNames(new ArrayList<>(nicNamePciAddressMap.keySet()));
        }

        if (msg.getNicNames().size() > 1 && msg.getBondMode() == null) {
            msg.setBondMode(refVO.getBondMode());
        }
    }

}
