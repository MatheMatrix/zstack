package org.zstack.header.network.l2;

import org.zstack.header.core.Completion;
import org.zstack.header.core.NoErrorCompletion;
import org.zstack.header.host.HypervisorType;

public interface VSwitchOvsHypervisorFactory {
    HypervisorType getHypervisorType();

    void installPackages(String hostUuid, Completion completion);
    void startService(String hostUuid, VSwitchOvsConfigStruct struct, Completion completion);

    void unInstallPackages(String hostUuid, NoErrorCompletion completion);
    void stopService(String hostUuid, VSwitchOvsConfigStruct struct, NoErrorCompletion completion);
}
