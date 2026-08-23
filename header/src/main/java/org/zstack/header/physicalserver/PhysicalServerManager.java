package org.zstack.header.physicalserver;

import org.zstack.header.core.Completion;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

public interface PhysicalServerManager {
    Map<String, String> resolveIdentities(Collection<PhysicalServerIdentitySpec> identities);

    Map<String, String> findSerialNumbersByServerUuids(
            Collection<String> serverUuids);

    void ensureResourceAssignments(Collection<String> serverUuids, String roleType);

    default void ensureResourceAssignment(String serverUuid, String roleType) {
        ensureResourceAssignments(Collections.singleton(serverUuid), roleType);
    }

    void reconcile(String serverUuid, boolean refreshFacts);

    void reconcileAll();

    default void releaseResourceAssignment(
            String serverUuid,
            String roleType,
            String consumerUuid,
            Completion completion) {
        releaseResourceAssignment(
                serverUuid, roleType, consumerUuid, false, completion);
    }

    void releaseResourceAssignment(
            String serverUuid,
            String roleType,
            String consumerUuid,
            boolean force,
            Completion completion);
}
