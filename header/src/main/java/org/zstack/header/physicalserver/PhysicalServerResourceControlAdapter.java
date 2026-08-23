package org.zstack.header.physicalserver;

import org.zstack.header.core.Completion;
import org.zstack.header.core.ReturnValueCompletion;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public interface PhysicalServerResourceControlAdapter {
    String getRoleType();

    PhysicalServerResourceIsolationMode getIsolationMode();

    PhysicalServerResourceApplicationMode getApplicationMode();

    default String getTopologyRoleType() {
        return getRoleType();
    }

    default String getDefaultCpuSet(
            PhysicalServerCpuTopology topology,
            Set<Integer> allocatedExclusiveCpus) {
        Set<Integer> available = new TreeSet<>(topology.getOnlineCpus());
        available.removeAll(allocatedExclusiveCpus);
        return PhysicalServerCpuSet.format(available);
    }

    default void refreshAssociations() {
    }

    default void refreshAssociations(Collection<String> serverUuids) {
        refreshAssociations();
    }

    default void refreshCapacity(String serverUuid) {
    }

    Set<String> getAssociatedServerUuids();

    default Set<String> getEligibleDefaultServerUuids() {
        return getAssociatedServerUuids();
    }

    PhysicalServerResourceConsumerState getState(String serverUuid);

    default String getUnavailableReason(String serverUuid) {
        return "RESOURCE_CONSUMER_UNAVAILABLE";
    }

    Map<String, PhysicalServerResourceConsumerState> getStates(
            Collection<String> serverUuids);

    void collectTopology(
            String serverUuid,
            ReturnValueCompletion<PhysicalServerCpuTopology> completion);

    void apply(
            String serverUuid,
            String consumerUuid,
            ResourceControlCommand command,
            ReturnValueCompletion<ResourceControlResponse> completion);

    void collectManagedServiceUsage(
            String serverUuid,
            boolean includeAuxiliaryServices,
            ReturnValueCompletion<List<ManagedServiceResourceUsage>> completion);

    void restartManagedServices(
            String serverUuid,
            boolean includeAuxiliaryServices,
            Collection<String> serviceNames,
            Completion completion);
}
