package org.zstack.header.network.l2;

import org.zstack.header.core.Completion;
import org.zstack.header.core.NoErrorCompletion;
import org.zstack.header.host.HypervisorType;
import org.zstack.header.vm.VmNicInventory;

import java.util.List;

public interface OvsVSwitchBackend {
    HypervisorType getHypervisorType();

    void installPackages(String hostUuid, VSwitchOvsConfigStruct struct, Completion completion);
    void startService(String hostUuid, VSwitchOvsConfigStruct struct, Completion completion);

    void unInstallPackages(String hostUuid, VSwitchOvsConfigStruct struct, NoErrorCompletion completion);
    void stopService(String hostUuid, VSwitchOvsConfigStruct struct, NoErrorCompletion completion);

    void addOvsPort(String hostUuid, String vswitchType, boolean sync, boolean reinstall,
                     List<VmNicInventory> nics, Completion completion);
    void delOvsPort(String hostUuid, String vswitchType, List<VmNicInventory> nics, Completion completion);
}
