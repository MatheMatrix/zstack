package org.zstack.appliancevm;

import org.json.JSONObject;
import org.zstack.header.network.l3.L3NetworkInventory;
import org.zstack.header.vm.VmInstanceSpec;
import org.zstack.header.vm.VmInstanceType;
import org.zstack.kvm.KVMAgentCommands;
import org.zstack.kvm.KVMHostInventory;
import org.zstack.kvm.KVMStartVmAddonExtensionPoint;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.zstack.appliancevm.ApplianceVmConstant.DEFAULT_ISO_PATH;

/**
 * Created by weiwang on 18/07/2017.
 */
public class ApplianceVmConfigDriveAddon implements KVMStartVmAddonExtensionPoint {
    private static final CLogger logger = Utils.getLogger(ApplianceVmConfigDriveAddon.class);

    @Override
    public VmInstanceType getVmTypeForAddonExtension() {
        return ApplianceVmFactory.type;
    }

    @Override
    public void addAddon(KVMHostInventory host, VmInstanceSpec spec, KVMAgentCommands.StartVmCmd cmd) {
        if (!spec.getVmInventory().getType().equals(ApplianceVmConstant.APPLIANCE_VM_TYPE)) {
            return;
        }
        Optional<L3NetworkInventory> opt = spec.getL3Networks().stream().filter(l3 -> !l3.isSystem()).findFirst();
        if (!opt.isPresent()) {
            return;
        }
        L3NetworkInventory guestL3 = opt.get();
        String configDrive = ApplianceVmSystemTag.CONFIG_DRIVE.getTokenByResourceUuid(guestL3.getUuid(), ApplianceVmSystemTag.CONFIG_DRIVE_TOKEN);
        if (configDrive == null) {
            return;
        }

        String isoPath = String.format("%s/%s/config.iso", DEFAULT_ISO_PATH, spec.getVmInventory().getUuid());
        Map<String, String> configDriveMap = new HashMap<String, String>() {
            {
                put("isoInfo", configDrive);
                put("isoPath", isoPath);
            }
        };
        JSONObject jsonObject = new JSONObject(configDriveMap);
        cmd.getAddons().put(ApplianceVmSystemTag.CONFIG_DRIVE_TOKEN, jsonObject.toString());
        logger.debug(String.format("add config drive %s to vm[uuid: %s] start cmd", configDriveMap, spec.getVmInventory().getUuid()));
    }
}
