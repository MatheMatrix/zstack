package org.zstack.header.vm.metadata;

import org.zstack.header.message.APIMessage;

import java.util.List;

public interface VmUuidFromApiResolver {
    boolean supports(APIMessage msg);

    List<String> resolveVmUuids(APIMessage msg);
}