package org.zstack.header.vm;

import org.zstack.header.core.Completion;

/**
 */
public interface VmBeforeStartOnHypervisorExtensionPoint {
    void beforeStartVmOnHypervisor(VmInstanceSpec spec, Completion completion);
}
