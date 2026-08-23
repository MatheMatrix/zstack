package org.zstack.storage.zbs;

import org.zstack.header.core.Completion;
import org.zstack.header.core.ReturnValueCompletion;

public interface ZbsCpuIsolationProvider {
    String getProviderType();

    boolean isAvailable(ZbsNodeRef nodeRef);

    void query(ZbsNodeRef nodeRef, ReturnValueCompletion<ZbsCpuIsolationFact> completion);

    void update(ZbsNodeRef nodeRef, ZbsCpuIsolationUpdate update, Completion completion);
}
