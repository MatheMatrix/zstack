package org.zstack.network.service.virtualrouter.vyos;

import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.zstack.appliancevm.ApplianceVmConstant;
import org.zstack.appliancevm.ApplianceVmInventory;
import org.zstack.appliancevm.ApplianceVmSpec;
import org.zstack.appliancevm.ApplianceVmVO;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.header.core.ReturnValueCompletion;
import org.zstack.header.core.workflow.*;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.rest.RESTFacade;
import org.zstack.header.vm.VmInstanceConstant;
import org.zstack.header.vm.VmInstanceSpec;
import org.zstack.header.vm.VmNicInventory;
import org.zstack.network.service.virtualrouter.*;
import org.zstack.utils.CollectionUtils;
import org.zstack.utils.Utils;
import org.zstack.utils.function.Function;
import org.zstack.utils.gson.JSONObjectUtil;
import org.zstack.utils.logging.CLogger;

import java.util.Map;

import static org.zstack.core.Platform.operr;

/**
 * Created by shixin.ruan on 2018/05/22.
 */
@Configurable(preConstruction = true, autowire = Autowire.BY_TYPE)
public class VyosGetVersionFlow extends NoRollbackFlow {
    private final static CLogger logger = Utils.getLogger(VyosGetVersionFlow.class);
    @Autowired
    private VyosVersionManager vyosVersionManager;
    @Autowired
    protected RESTFacade restf;
    @Autowired
    private VirtualRouterManager vrMgr;
    @Autowired
    private DatabaseFacade dbf;

    @Override
    public void run(FlowTrigger flowTrigger, Map flowData) {
        final VirtualRouterVmInventory vr = (VirtualRouterVmInventory) flowData.get(VirtualRouterConstant.VRouterParam.VR.toString());
        String vrUuid;
        VmNicInventory mgmtNic;

        if (vr != null) {
            mgmtNic = vr.getManagementNic();
            vrUuid = vr.getUuid();
        } else {
            final VmInstanceSpec spec = (VmInstanceSpec) flowData.get(VmInstanceConstant.VmInstanceParams.VmInstanceSpec.toString());
            vrUuid = spec.getVmInventory().getUuid();
            if (spec.getCurrentVmOperation() == VmInstanceConstant.VmOperation.NewCreate) {
                final ApplianceVmSpec aspec = spec.getExtensionData(ApplianceVmConstant.ApplianceVmParams.applianceVmSpec.toString(), ApplianceVmSpec.class);
                mgmtNic = CollectionUtils.find(spec.getDestNics(), new Function<VmNicInventory, VmNicInventory>() {
                    @Override
                    public VmNicInventory call(VmNicInventory arg) {
                        return arg.getL3NetworkUuid().equals(aspec.getManagementNic().getL3NetworkUuid()) ? arg : null;
                    }
                });
            } else {
                ApplianceVmVO avo = dbf.findByUuid(vrUuid, ApplianceVmVO.class);
                ApplianceVmInventory ainv = ApplianceVmInventory.valueOf(avo);
                mgmtNic = ainv.getManagementNic();
            }
        }

        flowData.put(VmInstanceConstant.VmInstanceParams.vmInstanceUuid.toString(), vrUuid);
        vyosVersionManager.vyosRouterVersionCheck(vrUuid, new ReturnValueCompletion<VyosVersionCheckResult>(flowTrigger) {
            @Override
            public void fail(ErrorCode errorCode) {
                flowTrigger.next();
            }

            @Override
            public void success(VyosVersionCheckResult returnValue) {
                if (returnValue.isNeedReconnect()) {
                    logger.warn(String.format("virtual router [uuid:%s] need to be reconnect: %s", vrUuid, JSONObjectUtil.toJsonString(returnValue)));
                    flowData.put(ApplianceVmConstant.ApplianceVmParams.isReconnect.toString(), Boolean.TRUE.toString());
                    flowData.put(ApplianceVmConstant.ApplianceVmParams.managementNicIp.toString(), mgmtNic.getIp());
                    flowData.put(ApplianceVmConstant.ApplianceVmParams.rebuildSnat.toString(), returnValue.isRebuildSnat());
                }
                flowTrigger.next();
            }
        });
    }
}
