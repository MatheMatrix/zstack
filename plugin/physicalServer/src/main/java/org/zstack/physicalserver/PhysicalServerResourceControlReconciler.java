package org.zstack.physicalserver;

import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.core.componentloader.PluginRegistry;
import org.zstack.header.core.Completion;
import org.zstack.header.core.ReturnValueCompletion;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.physicalserver.PhysicalServerCpuSet;
import org.zstack.header.physicalserver.PhysicalServerCpuTopology;
import org.zstack.header.physicalserver.PhysicalServerResourceApplicationMode;
import org.zstack.header.physicalserver.PhysicalServerResourceConsumerState;
import org.zstack.header.physicalserver.PhysicalServerResourceControlAdapter;
import org.zstack.header.physicalserver.PhysicalServerResourceIsolationMode;
import org.zstack.header.physicalserver.ResourceControlCommand;
import org.zstack.header.physicalserver.ResourceControlResponse;
import org.zstack.header.physicalserver.ResourceControlResult;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.zstack.core.Platform.operr;

public class PhysicalServerResourceControlReconciler {
    private static final CLogger logger = Utils.getLogger(
            PhysicalServerResourceControlReconciler.class);

    @Autowired
    private PluginRegistry pluginRgty;
    @Autowired
    private PhysicalServerAssignmentRepository assignments;
    @Autowired
    private PhysicalServerCpuPlanner planner;

    public void reconcile(
            String serverUuid,
            Map<String, PhysicalServerResourceAssignmentVO> assignmentSnapshot,
            Completion completion) {
        PhysicalServerResourceControlAdapterRegistry adapters = adapters();
        Map<String, String> before = capacityFootprint(
                assignmentSnapshot.values(), adapters);
        List<String> roleTypes = new ArrayList<>(assignmentSnapshot.keySet());
        roleTypes.sort(Comparator
                .comparingInt((String roleType) -> isolationOrder(adapters.get(roleType)))
                .thenComparing(String::compareTo));
        reconcileRoles(
                serverUuid, assignmentSnapshot, adapters, roleTypes,
                0, null, before, completion);
    }

    private void reconcileRoles(
            String serverUuid,
            Map<String, PhysicalServerResourceAssignmentVO> assignmentSnapshot,
            PhysicalServerResourceControlAdapterRegistry adapters,
            List<String> roleTypes,
            int index,
            ErrorCode firstError,
            Map<String, String> before,
            Completion completion) {
        if (index == roleTypes.size()) {
            if (!before.equals(capacityFootprint(
                    assignmentSnapshot.values(), adapters))) {
                refreshCapacities(serverUuid);
            }
            if (firstError == null) {
                completion.success();
            } else {
                completion.fail(firstError);
            }
            return;
        }

        String roleType = roleTypes.get(index);
        reconcileRole(
                assignmentSnapshot.get(roleType), assignmentSnapshot, adapters,
                new Completion(completion) {
                    @Override
                    public void success() {
                        reconcileRoles(
                                serverUuid, assignmentSnapshot, adapters,
                                roleTypes, index + 1, firstError, before, completion);
                    }

                    @Override
                    public void fail(ErrorCode errorCode) {
                        logger.warn(String.format(
                                "failed to reconcile physical server resource assignment: " +
                                        "serverUuid[%s], roleType[%s], error[%s]",
                                serverUuid, roleType, errorCode));
                        reconcileRoles(
                                serverUuid, assignmentSnapshot, adapters,
                                roleTypes, index + 1,
                                firstError == null ? errorCode : firstError,
                                before, completion);
                    }
                });
    }

    private void reconcileRole(
            PhysicalServerResourceAssignmentVO assignment,
            Map<String, PhysicalServerResourceAssignmentVO> assignmentSnapshot,
            PhysicalServerResourceControlAdapterRegistry adapters,
            Completion completion) {
        PhysicalServerResourceControlAdapter adapter =
                adapters.get(assignment.getRoleType());
        if (adapter == null) {
            failUnsynced(
                    assignment,
                    adapterUnavailableReason(assignment.getRoleType(), adapters),
                    completion);
            return;
        }

        PhysicalServerResourceConsumerState state;
        try {
            state = adapter.getState(assignment.getServerUuid());
        } catch (RuntimeException error) {
            failUnsynced(assignment, "RESOURCE_CONSUMER_UNAVAILABLE", completion);
            return;
        }
        if (state != PhysicalServerResourceConsumerState.AVAILABLE) {
            failUnsynced(
                    assignment,
                    state == PhysicalServerResourceConsumerState.MISSING
                            ? "RESOURCE_CONSUMER_MISSING"
                            : unavailableReason(adapter, assignment.getServerUuid()),
                    completion);
            return;
        }

        collectTopology(
                assignment, adapter, adapters,
                new ReturnValueCompletion<PhysicalServerCpuTopology>(completion) {
                    @Override
                    public void success(PhysicalServerCpuTopology topology) {
                        validateAndApply(
                                assignment, assignmentSnapshot, adapter,
                                topology, completion);
                    }

                    @Override
                    public void fail(ErrorCode errorCode) {
                        failUnsynced(
                                assignment,
                                PhysicalServerReasonCodes.from(errorCode),
                                completion);
                    }
                });
    }

    private void collectTopology(
            PhysicalServerResourceAssignmentVO assignment,
            PhysicalServerResourceControlAdapter adapter,
            PhysicalServerResourceControlAdapterRegistry adapters,
            ReturnValueCompletion<PhysicalServerCpuTopology> completion) {
        PhysicalServerResourceControlAdapter topologyAdapter =
                adapters.get(adapter.getTopologyRoleType());
        if (topologyAdapter == null) {
            completion.fail(operr(
                    PhysicalServerConstant.ERROR_CODE,
                    "CPU_TOPOLOGY_SOURCE_MISSING: roleType[%s] requires topologyRoleType[%s]",
                    assignment.getRoleType(), adapter.getTopologyRoleType()));
            return;
        }
        PhysicalServerResourceConsumerState state;
        try {
            state = topologyAdapter.getState(assignment.getServerUuid());
        } catch (RuntimeException error) {
            completion.fail(operr(
                    PhysicalServerConstant.ERROR_CODE,
                    "CPU_TOPOLOGY_SOURCE_FAILED: %s", error.getMessage()));
            return;
        }
        if (state != PhysicalServerResourceConsumerState.AVAILABLE) {
            completion.fail(operr(
                    PhysicalServerConstant.ERROR_CODE,
                    state == PhysicalServerResourceConsumerState.MISSING
                            ? "CPU_TOPOLOGY_SOURCE_MISSING"
                            : "CPU_TOPOLOGY_EXECUTOR_UNREACHABLE"));
            return;
        }
        try {
            topologyAdapter.collectTopology(assignment.getServerUuid(), completion);
        } catch (RuntimeException error) {
            completion.fail(operr(
                    PhysicalServerConstant.ERROR_CODE,
                    "CPU_TOPOLOGY_EXECUTOR_UNREACHABLE: %s", error.getMessage()));
        }
    }

    private void validateAndApply(
            PhysicalServerResourceAssignmentVO assignment,
            Map<String, PhysicalServerResourceAssignmentVO> assignmentSnapshot,
            PhysicalServerResourceControlAdapter adapter,
            PhysicalServerCpuTopology topology,
            Completion completion) {
        Set<Integer> allocatedExclusiveCpus = allocatedExclusiveCpus(
                assignment, assignmentSnapshot.values(), adapters(), topology);
        PhysicalServerResourceAssignmentVO current = assignment;
        try {
            String cpuSet = current.getCpuSet();
            if (cpuSet == null || cpuSet.isEmpty()) {
                cpuSet = adapter.getDefaultCpuSet(topology, allocatedExclusiveCpus);
            }
            cpuSet = planner.validateAndNormalize(
                    adapter.getIsolationMode(), cpuSet,
                    topology, allocatedExclusiveCpus);
            if (!cpuSet.equals(current.getCpuSet())) {
                current = assignments.initializeCpuSet(current, cpuSet);
                if (current == null) {
                    completion.fail(operr(
                            PhysicalServerConstant.ERROR_CODE,
                            "RESOURCE_ASSIGNMENT_NOT_FOUND: assignmentUuid[%s]",
                            assignment.getUuid()));
                    return;
                }
                assignmentSnapshot.put(current.getRoleType(), current);
            }
        } catch (RuntimeException error) {
            failUnsynced(
                    assignment,
                    PhysicalServerReasonCodes.from(error.getMessage()),
                    completion);
            return;
        }
        apply(current, assignmentSnapshot, adapter, topology, "APPLY", null, completion);
    }

    public void release(
            String serverUuid,
            String roleType,
            String consumerUuid,
            Completion completion) {
        PhysicalServerResourceAssignmentVO assignment =
                assignments.find(serverUuid, roleType);
        if (assignment == null) {
            completion.success();
            return;
        }
        PhysicalServerResourceControlAdapter adapter = adapters().get(roleType);
        if (adapter == null) {
            failUnsynced(assignment, "RESOURCE_ASSIGNMENT_ADAPTER_MISSING", completion);
            return;
        }
        apply(
                assignment, assignmentsByRole(serverUuid), adapter,
                null, "RELEASE", consumerUuid, completion);
    }

    public void forget(String serverUuid, String roleType) {
        PhysicalServerResourceAssignmentVO assignment =
                assignments.find(serverUuid, roleType);
        if (assignment != null && assignments.delete(assignment.getUuid())) {
            refreshCapacities(serverUuid);
        }
    }

    public void stageForServiceRestart(
            PhysicalServerResourceAssignmentVO assignment,
            Map<String, PhysicalServerResourceAssignmentVO> assignmentSnapshot,
            PhysicalServerResourceControlAdapter adapter,
            Completion completion) {
        if (adapter.getApplicationMode()
                != PhysicalServerResourceApplicationMode.RESOURCE_HANDLES) {
            completion.fail(operr(
                    PhysicalServerConstant.ERROR_CODE,
                    "SERVICE_RESTART_NOT_SUPPORTED: roleType[%s] is provider-managed",
                    assignment.getRoleType()));
            return;
        }
        ResourceControlCommand command = command(
                assignment,
                assignmentSnapshot.get(PhysicalServerRoleType.MANAGEMENT),
                adapter,
                "APPLY");
        try {
            adapter.apply(
                    assignment.getServerUuid(), null, command,
                    new ReturnValueCompletion<ResourceControlResponse>(completion) {
                        @Override
                        public void success(ResourceControlResponse response) {
                            if (stageMatches(command, response)) {
                                completion.success();
                                return;
                            }
                            failUnsynced(
                                    assignment,
                                    "RESOURCE_CONTROL_STAGE_MISMATCH",
                                    completion);
                        }

                        @Override
                        public void fail(ErrorCode errorCode) {
                            failUnsynced(
                                    assignment,
                                    PhysicalServerReasonCodes.from(errorCode),
                                    completion);
                        }
                    });
        } catch (RuntimeException error) {
            failUnsynced(
                    assignment,
                    PhysicalServerReasonCodes.from(error.getMessage()),
                    completion);
        }
    }

    private boolean stageMatches(
            ResourceControlCommand command,
            ResourceControlResponse response) {
        if (response == null) {
            return false;
        }
        try {
            if (!command.getCpuSet().equals(
                    normalizeCpuSet(response.getCpuSet(), null))) {
                return false;
            }
        } catch (RuntimeException error) {
            return false;
        }
        return memoryMatches(response, command.getMemory());
    }

    private void apply(
            PhysicalServerResourceAssignmentVO assignment,
            Map<String, PhysicalServerResourceAssignmentVO> assignmentSnapshot,
            PhysicalServerResourceControlAdapter adapter,
            PhysicalServerCpuTopology topology,
            String operation,
            String consumerUuid,
            Completion completion) {
        ResourceControlCommand command = command(
                assignment,
                assignmentSnapshot.get(PhysicalServerRoleType.MANAGEMENT),
                adapter,
                operation);
        try {
            adapter.apply(
                    assignment.getServerUuid(), consumerUuid, command,
                    new ReturnValueCompletion<ResourceControlResponse>(completion) {
                        @Override
                        public void success(ResourceControlResponse response) {
                            if ("RELEASE".equals(operation)) {
                                completeRelease(
                                        assignment, adapter, command,
                                        response, completion);
                            } else {
                                completeApply(
                                        assignment, assignmentSnapshot,
                                        adapter, topology, command,
                                        response, completion);
                            }
                        }

                        @Override
                        public void fail(ErrorCode errorCode) {
                            failUnsynced(
                                    assignment,
                                    PhysicalServerReasonCodes.from(errorCode),
                                    completion);
                        }
                    });
        } catch (RuntimeException error) {
            failUnsynced(
                    assignment,
                    PhysicalServerReasonCodes.from(error.getMessage()),
                    completion);
        }
    }

    private void completeApply(
            PhysicalServerResourceAssignmentVO assignment,
            Map<String, PhysicalServerResourceAssignmentVO> assignmentSnapshot,
            PhysicalServerResourceControlAdapter adapter,
            PhysicalServerCpuTopology topology,
            ResourceControlCommand command,
            ResourceControlResponse response,
            Completion completion) {
        if (!matches(adapter, command, response, topology, false)) {
            failUnsynced(assignment, "RESOURCE_CONTROL_ACTUAL_MISMATCH", completion);
            return;
        }
        if (!assignments.markSynced(assignment)) {
            completion.success();
            return;
        }
        assignment.setState(PhysicalServerResourceAssignmentState.Synced);
        assignmentSnapshot.put(assignment.getRoleType(), assignment);
        completion.success();
    }

    private void completeRelease(
            PhysicalServerResourceAssignmentVO assignment,
            PhysicalServerResourceControlAdapter adapter,
            ResourceControlCommand command,
            ResourceControlResponse response,
            Completion completion) {
        if (!matches(adapter, command, response, null, true)) {
            failUnsynced(assignment, "RESOURCE_CONTROL_RELEASE_FAILED", completion);
            return;
        }
        if (!assignments.delete(assignment.getUuid())) {
            completion.fail(operr(
                    PhysicalServerConstant.ERROR_CODE,
                    "RESOURCE_ASSIGNMENT_NOT_FOUND: assignmentUuid[%s]",
                    assignment.getUuid()));
            return;
        }
        refreshCapacities(assignment.getServerUuid());
        completion.success();
    }

    private boolean matches(
            PhysicalServerResourceControlAdapter adapter,
            ResourceControlCommand command,
            ResourceControlResponse response,
            PhysicalServerCpuTopology topology,
            boolean release) {
        if (response == null) {
            return false;
        }
        String desiredCpuSet = release ? "" : command.getCpuSet();
        String actualCpuSet;
        try {
            actualCpuSet = normalizeCpuSet(response.getCpuSet(), topology);
        } catch (RuntimeException error) {
            return false;
        }
        if (!desiredCpuSet.equals(actualCpuSet)) {
            return false;
        }
        Long desiredMemory = command.getMemory() == null
                ? null : release ? 0L : command.getMemory();
        if (!memoryMatches(response, desiredMemory)) {
            return false;
        }
        if (adapter.getApplicationMode()
                == PhysicalServerResourceApplicationMode.PROVIDER_MANAGED) {
            return release
                    ? "DISABLED".equals(response.getState())
                    : "READY".equals(response.getState());
        }
        return coverageMatches(response, release)
                && handleResultsMatch(response, desiredCpuSet, desiredMemory, release);
    }

    private boolean coverageMatches(
            ResourceControlResponse response, boolean release) {
        if (response.getExpectedServiceCount() == null
                || response.getCoveredServiceCount() == null) {
            return false;
        }
        return response.getExpectedServiceCount().equals(
                response.getCoveredServiceCount())
                && (release || response.getExpectedServiceCount() > 0);
    }

    private boolean memoryMatches(
            ResourceControlResponse response, Long desiredMemory) {
        return desiredMemory == null
                || desiredMemory.equals(
                response.getMemory())
                || desiredMemory == 0L
                && response.getMemory() == null;
    }

    private boolean handleResultsMatch(
            ResourceControlResponse response,
            String desiredCpuSet,
            Long desiredMemory,
            boolean release) {
        if (response.getResults() == null
                || response.getResults().isEmpty()) {
            return release && Integer.valueOf(0).equals(
                    response.getExpectedServiceCount());
        }
        int observed = 0;
        for (ResourceControlResult result : response.getResults()) {
            if ("SKIPPED".equals(result.getState())) {
                continue;
            }
            observed++;
            String requiredState = release ? "DISABLED" : "READY";
            if (!requiredState.equals(result.getState())
                    || !desiredCpuSet.equals(normalizeEmpty(result.getCpuSet()))
                    || desiredMemory != null
                    && !desiredMemory.equals(result.getMemory())
                    && !(desiredMemory == 0L
                    && result.getMemory() == null)) {
                return false;
            }
        }
        return observed == response.getExpectedServiceCount();
    }

    private ResourceControlCommand command(
            PhysicalServerResourceAssignmentVO assignment,
            PhysicalServerResourceAssignmentVO managementAssignment,
            PhysicalServerResourceControlAdapter adapter,
            String operation) {
        ResourceControlCommand command = new ResourceControlCommand();
        command.setRoleType(assignment.getRoleType());
        command.setIsolationMode(adapter.getIsolationMode().name());
        command.setOperation(operation);
        command.setCpuSet("RELEASE".equals(operation) ? "" : assignment.getCpuSet());
        command.setMemory(assignment.getMemory());
        command.setIncludeAuxiliaryServices(
                PhysicalServerRoleType.MANAGEMENT.equals(assignment.getRoleType())
                        || managementAssignment == null);
        return command;
    }

    private Set<Integer> allocatedExclusiveCpus(
            PhysicalServerResourceAssignmentVO current,
            Collection<PhysicalServerResourceAssignmentVO> assignmentValues,
            PhysicalServerResourceControlAdapterRegistry adapters,
            PhysicalServerCpuTopology topology) {
        Set<Integer> result = new HashSet<>();
        for (PhysicalServerResourceAssignmentVO assignment : assignmentValues) {
            if (assignment.getUuid().equals(current.getUuid())) {
                continue;
            }
            PhysicalServerResourceControlAdapter adapter =
                    adapters.get(assignment.getRoleType());
            if (adapter == null || adapter.getIsolationMode()
                    != PhysicalServerResourceIsolationMode.EXCLUSIVE
                    || assignment.getCpuSet() == null
                    || assignment.getCpuSet().isEmpty()) {
                continue;
            }
            result.addAll(PhysicalServerCpuSet.parse(
                    assignment.getCpuSet(), topology.getOnlineCpus()));
        }
        return result;
    }

    private String normalizeCpuSet(
            String cpuSet, PhysicalServerCpuTopology topology) {
        if (normalizeEmpty(cpuSet).isEmpty()) {
            return "";
        }
        if (topology == null) {
            return PhysicalServerCpuSet.normalize(cpuSet);
        }
        return PhysicalServerCpuSet.format(PhysicalServerCpuSet.parse(
                cpuSet, topology.getOnlineCpus()));
    }

    private String normalizeEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private void failUnsynced(
            PhysicalServerResourceAssignmentVO assignment,
            String reason,
            Completion completion) {
        assignments.markUnsynced(assignment.getUuid());
        assignment.setState(PhysicalServerResourceAssignmentState.Unsynced);
        completion.fail(operr(
                PhysicalServerConstant.ERROR_CODE,
                "%s: physical server resource assignment[uuid:%s, roleType:%s] is unsynced",
                reason, assignment.getUuid(), assignment.getRoleType()));
    }

    private void refreshCapacities(String serverUuid) {
        for (PhysicalServerResourceControlAdapter adapter :
                adapters().orderedAdapters()) {
            try {
                adapter.refreshCapacity(serverUuid);
            } catch (RuntimeException error) {
                logger.warn(String.format(
                        "failed to refresh capacity after resource assignment changed: " +
                                "serverUuid[%s], roleType[%s], error[%s]",
                        serverUuid, adapter.getRoleType(), error.getMessage()));
            }
        }
    }

    private Map<String, PhysicalServerResourceAssignmentVO> assignmentsByRole(
            String serverUuid) {
        Map<String, PhysicalServerResourceAssignmentVO> result =
                new LinkedHashMap<>();
        for (PhysicalServerResourceAssignmentVO assignment :
                assignments.listAssignments(
                        java.util.Collections.singleton(serverUuid))) {
            result.put(assignment.getRoleType(), assignment);
        }
        return result;
    }

    private String adapterUnavailableReason(
            String roleType,
            PhysicalServerResourceControlAdapterRegistry adapters) {
        String error = adapters.getError(roleType);
        if (error == null) {
            return "RESOURCE_ASSIGNMENT_ADAPTER_MISSING";
        }
        int separator = error.indexOf(':');
        return separator < 0 ? error : error.substring(0, separator);
    }

    private String unavailableReason(
            PhysicalServerResourceControlAdapter adapter,
            String serverUuid) {
        try {
            String reason = adapter.getUnavailableReason(serverUuid);
            return reason == null || reason.isEmpty()
                    ? "RESOURCE_CONSUMER_UNAVAILABLE" : reason;
        } catch (RuntimeException error) {
            return "RESOURCE_CONSUMER_UNAVAILABLE";
        }
    }

    private int isolationOrder(PhysicalServerResourceControlAdapter adapter) {
        return adapter != null && adapter.getIsolationMode()
                == PhysicalServerResourceIsolationMode.EXCLUSIVE ? 0 : 1;
    }

    private Map<String, String> capacityFootprint(
            Collection<PhysicalServerResourceAssignmentVO> assignmentValues,
            PhysicalServerResourceControlAdapterRegistry adapters) {
        Map<String, String> result = new LinkedHashMap<>();
        assignmentValues.stream()
                .sorted(Comparator.comparing(
                        PhysicalServerResourceAssignmentVO::getRoleType))
                .forEach(assignment -> {
                    PhysicalServerResourceControlAdapter adapter =
                            adapters.get(assignment.getRoleType());
                    if (adapter == null || adapter.getIsolationMode()
                            != PhysicalServerResourceIsolationMode.EXCLUSIVE
                            || assignment.getState()
                            != PhysicalServerResourceAssignmentState.Synced) {
                        return;
                    }
                    result.put(assignment.getRoleType(), assignment.getCpuSet());
                });
        return result;
    }

    private PhysicalServerResourceControlAdapterRegistry adapters() {
        return PhysicalServerResourceControlAdapterRegistry.load(pluginRgty);
    }
}
