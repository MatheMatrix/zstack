package org.zstack.header.network.l2;

import org.zstack.header.core.Completion;
import org.zstack.header.core.NoErrorCompletion;
import org.zstack.header.host.HostInventory;
import org.zstack.header.message.Message;

import java.util.List;

public interface L2Network {
    void handleMessage(Message msg);

    void deleteHook(Completion completion);
}
