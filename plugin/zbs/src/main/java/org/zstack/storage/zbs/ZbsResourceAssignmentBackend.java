package org.zstack.storage.zbs;

import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.core.componentloader.PluginRegistry;
import org.zstack.core.thread.ThreadFacade;
import org.zstack.core.thread.ThreadFacadeImpl;
import org.zstack.header.core.Completion;
import org.zstack.header.core.ReturnValueCompletion;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.physicalserver.PhysicalServerCpuTopology;
import org.zstack.header.physicalserver.PhysicalServerCpuSet;
import org.zstack.header.physicalserver.PhysicalServerResourceApplicationMode;
import org.zstack.header.physicalserver.PhysicalServerResourceConsumerState;
import org.zstack.header.physicalserver.PhysicalServerResourceControlAdapter;
import org.zstack.header.physicalserver.PhysicalServerResourceIsolationMode;
import org.zstack.header.physicalserver.ManagedServiceResourceUsage;
import org.zstack.header.physicalserver.ResourceControlCommand;
import org.zstack.header.physicalserver.ResourceControlResponse;
import org.zstack.header.physicalserver.RoleServiceManifest;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.zstack.core.Platform.operr;
import static org.zstack.utils.clouderrorcode.CloudOperationsErrorCode.ORG_ZSTACK_CORE_10000;

public class ZbsResourceAssignmentBackend implements
        PhysicalServerResourceControlAdapter {
    public static final String ROLE_TYPE = "ZBS";
    public static final String ROLE_SERVICE_MANIFEST_PATH =
            "physical-server-roles/zbs.yaml";
    private static final String TOPOLOGY_ROLE_TYPE = "COMPUTE";
    private static final CLogger logger = Utils.getLogger(
            ZbsResourceAssignmentBackend.class);
    private static final RoleServiceManifest ROLE_SERVICES =
            RoleServiceManifest.load(
                    ROLE_SERVICE_MANIFEST_PATH,
                    ROLE_TYPE,
                    PhysicalServerResourceApplicationMode.PROVIDER_MANAGED);

    private final AtomicReference<Map<String, ZbsNodeRef>> zbsRefs =
            new AtomicReference<>(Collections.emptyMap());

    @Autowired
    private PluginRegistry pluginRgty;
    @Autowired
    private ThreadFacade thdf;

    @Override
    public String getRoleType() {
        return ROLE_SERVICES.getRoleType();
    }

    @Override
    public PhysicalServerResourceIsolationMode getIsolationMode() {
        return PhysicalServerResourceIsolationMode.EXCLUSIVE;
    }

    @Override
    public PhysicalServerResourceApplicationMode getApplicationMode() {
        return PhysicalServerResourceApplicationMode.PROVIDER_MANAGED;
    }

    @Override
    public String getTopologyRoleType() {
        return TOPOLOGY_ROLE_TYPE;
    }

    @Override
    public String getDefaultCpuSet(
            PhysicalServerCpuTopology topology,
            Set<Integer> allocatedExclusiveCpus) {
        List<PhysicalServerCpuTopology.CoreGroup> groups =
                topology.getCoreGroups();
        int selectedCount = groups.size() / 2;
        if (selectedCount < 1) {
            throw new IllegalArgumentException(
                    "CPU_TOPOLOGY_TOO_SMALL: default ZBS isolation selects no complete core group");
        }
        SortedSet<Integer> selected = new TreeSet<>();
        for (int index = groups.size() - selectedCount;
             index < groups.size(); index++) {
            selected.addAll(groups.get(index).getCpus());
        }
        return PhysicalServerCpuSet.format(selected);
    }

    @Override
    public void refreshAssociations() {
        refreshRefs(Collections.emptySet());
    }

    @Override
    public void refreshAssociations(Collection<String> serverUuids) {
        refreshRefs(serverUuids);
    }

    @Override
    public Set<String> getAssociatedServerUuids() {
        return new HashSet<>(zbsRefs.get().keySet());
    }

    @Override
    public Set<String> getEligibleDefaultServerUuids() {
        return getAssociatedServerUuids();
    }

    @Override
    public PhysicalServerResourceConsumerState getState(String serverUuid) {
        ZbsNodeRef ref = zbsRefs.get().get(serverUuid);
        if (ref == null) {
            return PhysicalServerResourceConsumerState.MISSING;
        }
        if (ref.getReasonCode() != null
                || resolveProvider(ref).provider == null) {
            return PhysicalServerResourceConsumerState.UNAVAILABLE;
        }
        return PhysicalServerResourceConsumerState.AVAILABLE;
    }

    @Override
    public String getUnavailableReason(String serverUuid) {
        ZbsNodeRef ref = zbsRefs.get().get(serverUuid);
        if (ref == null) {
            return "ZBS_NODE_RELATION_MISSING";
        }
        return resolveProvider(ref).reasonCode;
    }

    @Override
    public Map<String, PhysicalServerResourceConsumerState> getStates(
            Collection<String> serverUuids) {
        Map<String, PhysicalServerResourceConsumerState> result =
                new HashMap<>();
        if (serverUuids == null) {
            return result;
        }
        for (String serverUuid : serverUuids) {
            result.put(serverUuid, getState(serverUuid));
        }
        return result;
    }

    @Override
    public void collectTopology(
            String serverUuid,
            ReturnValueCompletion<PhysicalServerCpuTopology> completion) {
        completion.fail(operr(
                ORG_ZSTACK_CORE_10000,
                "ZBS uses topologyRoleType[%s]", TOPOLOGY_ROLE_TYPE));
    }

    @Override
    public void apply(
            String serverUuid,
            String consumerUuid,
            ResourceControlCommand command,
            ReturnValueCompletion<ResourceControlResponse> completion) {
        ZbsNodeRef ref = zbsRefs.get().get(serverUuid);
        ProviderResolution resolution = resolveProvider(ref);
        if (resolution.provider == null) {
            completion.fail(operr(
                    ORG_ZSTACK_CORE_10000,
                    "%s: physical server[uuid:%s]",
                    resolution.reasonCode, serverUuid));
            return;
        }
        queryProvider(
                resolution.provider,
                ref,
                new ReturnValueCompletion<ZbsCpuIsolationFact>(completion) {
                    @Override
                    public void success(ZbsCpuIsolationFact fact) {
                        ResourceControlResponse observed;
                        try {
                            observed = toResponse(fact);
                        } catch (RuntimeException error) {
                            completion.fail(operr(
                                    ORG_ZSTACK_CORE_10000,
                                    "%s", error.getMessage()));
                            return;
                        }
                        if (!shouldUpdate(command, fact)) {
                            completion.success(observed);
                            return;
                        }
                        ZbsCpuIsolationUpdate update = update(command);
                        updateProvider(
                                resolution.provider,
                                ref,
                                update,
                                new Completion(completion) {
                                    @Override
                                    public void success() {
                                        queryProvider(
                                                resolution.provider,
                                                ref,
                                                new ReturnValueCompletion<ZbsCpuIsolationFact>(completion) {
                                                    @Override
                                                    public void success(ZbsCpuIsolationFact fact) {
                                                        completeFact(fact, completion);
                                                    }

                                                    @Override
                                                    public void fail(ErrorCode errorCode) {
                                                        completion.fail(errorCode);
                                                    }
                                                });
                                    }

                                    @Override
                                    public void fail(ErrorCode errorCode) {
                                        completion.fail(errorCode);
                                    }
                                });
                    }

                    @Override
                    public void fail(ErrorCode errorCode) {
                        completion.fail(errorCode);
                    }
                });
    }

    @Override
    public void collectManagedServiceUsage(
            String serverUuid,
            boolean includeAuxiliaryServices,
            ReturnValueCompletion<List<ManagedServiceResourceUsage>> completion) {
        ZbsNodeRef ref = zbsRefs.get().get(serverUuid);
        ProviderResolution resolution = resolveProvider(ref);
        if (resolution.provider == null) {
            completion.success(ROLE_SERVICES.managedServiceUsages(
                    includeAuxiliaryServices, "UNAVAILABLE"));
            return;
        }
        queryProvider(
                resolution.provider,
                ref,
                new ReturnValueCompletion<ZbsCpuIsolationFact>(completion) {
                    @Override
                    public void success(ZbsCpuIsolationFact fact) {
                        ManagedServiceResourceUsage usage =
                                new ManagedServiceResourceUsage();
                        usage.setRoleType(ROLE_TYPE);
                        usage.setServiceName(
                                ROLE_SERVICES.getServices().get(0).getName());
                        usage.setRestartable(false);
                        usage.setState(fact.isServiceReady()
                                ? "RUNNING" : "UNAVAILABLE");
                        usage.setCpuSet(normalize(fact.getEffectiveCpuSet()));
                        usage.setMemoryLimit(fact.getMemory());
                        completion.success(Collections.singletonList(usage));
                    }

                    @Override
                    public void fail(ErrorCode errorCode) {
                        logger.warn(String.format(
                                "failed to query ZBS managed service on physical server[uuid:%s]: %s",
                                serverUuid, errorCode));
                        completion.success(ROLE_SERVICES.managedServiceUsages(
                                includeAuxiliaryServices, "UNAVAILABLE"));
                    }
                });
    }

    @Override
    public void restartManagedServices(
            String serverUuid,
            boolean includeAuxiliaryServices,
            Collection<String> serviceNames,
            Completion completion) {
        completion.fail(operr(
                ORG_ZSTACK_CORE_10000,
                "SERVICE_RESTART_NOT_SUPPORTED: roleType[%s] is provider-managed",
                ROLE_TYPE));
    }

    private boolean shouldUpdate(
            ResourceControlCommand command,
            ZbsCpuIsolationFact fact) {
        boolean release = "RELEASE".equals(command.getOperation());
        if (release) {
            return fact == null
                    || !"DISABLED".equals(fact.getIsolationState())
                    || !empty(fact.getEffectiveCpuSet())
                    || fact.getMemory() != null && fact.getMemory() != 0L;
        }
        if (fact == null || "UNSUPPORTED".equals(fact.getIsolationState())) {
            return false;
        }
        return !"READY".equals(fact.getIsolationState())
                || !normalize(command.getCpuSet()).equals(
                normalize(fact.getEffectiveCpuSet()))
                || !memoryMatches(command.getMemory(), fact.getMemory());
    }

    private ZbsCpuIsolationUpdate update(ResourceControlCommand command) {
        boolean release = "RELEASE".equals(command.getOperation());
        ZbsCpuIsolationUpdate update = new ZbsCpuIsolationUpdate();
        update.setOperation(release ? "DISABLE" : "SET");
        update.setDesiredCpuSet(release ? "" : command.getCpuSet());
        update.setMemory(release ? Long.valueOf(0L) : command.getMemory());
        return update;
    }

    private void completeFact(
            ZbsCpuIsolationFact fact,
            ReturnValueCompletion<ResourceControlResponse> completion) {
        try {
            completion.success(toResponse(fact));
        } catch (RuntimeException error) {
            completion.fail(operr(
                    ORG_ZSTACK_CORE_10000,
                    "%s", error.getMessage()));
        }
    }

    private ResourceControlResponse toResponse(ZbsCpuIsolationFact fact) {
        if (fact == null) {
            throw new IllegalArgumentException(
                    "OWNER_FACT_INVALID: Provider returned null");
        }
        if (!isOwnerState(fact.getIsolationState())) {
            throw new IllegalArgumentException(
                    "OWNER_FACT_INVALID: isolationState is not supported");
        }
        if ("READY".equals(fact.getIsolationState())
                && (!fact.isServiceReady()
                || empty(fact.getEffectiveCpuSet())
                || !normalize(fact.getConfiguredCpuSet()).equals(
                normalize(fact.getEffectiveCpuSet()))
                || fact.getExpectedServiceCount() == null
                || fact.getExpectedServiceCount() < 1
                || !fact.getExpectedServiceCount().equals(
                fact.getCoveredServiceCount()))) {
            throw new IllegalArgumentException(
                    "OWNER_SERVICE_NOT_READY: READY requires identical CPU sets and complete service coverage");
        }
        if ("DISABLED".equals(fact.getIsolationState())
                && !empty(fact.getEffectiveCpuSet())) {
            throw new IllegalArgumentException(
                    "OWNER_FACT_INVALID: DISABLED must not report an effective CPUSet");
        }

        ResourceControlResponse response = new ResourceControlResponse();
        response.setState(fact.getIsolationState());
        response.setCpuSet(normalize(fact.getEffectiveCpuSet()));
        response.setMemory(fact.getMemory());
        response.setCoveredServiceCount(fact.getCoveredServiceCount());
        response.setExpectedServiceCount(fact.getExpectedServiceCount());
        return response;
    }

    private void refreshRefs(Collection<String> serverUuids) {
        List<ZbsNodeRefContributor> contributors =
                pluginRgty.getExtensionList(ZbsNodeRefContributor.class);
        Map<String, ZbsNodeRef> loaded = new HashMap<>();
        for (ZbsNodeRefContributor contributor : contributors) {
            Map<String, ZbsNodeRef> contribution;
            try {
                contribution = contributor.bulkList(serverUuids);
            } catch (RuntimeException error) {
                logger.warn(String.format(
                        "failed to refresh ZBS node relations from contributor[%s]: %s",
                        contributor.getClass().getName(), error.getMessage()));
                return;
            }
            for (Map.Entry<String, ZbsNodeRef> entry : contribution.entrySet()) {
                if (loaded.put(entry.getKey(), entry.getValue()) != null) {
                    entry.getValue().setReasonCode(
                            "ZBS_NODE_REF_CONTRIBUTOR_AMBIGUOUS");
                }
            }
        }
        if (serverUuids == null || serverUuids.isEmpty()) {
            zbsRefs.set(Collections.unmodifiableMap(loaded));
            return;
        }
        while (true) {
            Map<String, ZbsNodeRef> current = zbsRefs.get();
            Map<String, ZbsNodeRef> replacement = new HashMap<>(current);
            for (String serverUuid : serverUuids) {
                replacement.remove(serverUuid);
            }
            replacement.putAll(loaded);
            if (zbsRefs.compareAndSet(
                    current, Collections.unmodifiableMap(replacement))) {
                return;
            }
        }
    }

    private ProviderResolution resolveProvider(ZbsNodeRef ref) {
        if (ref == null) {
            return new ProviderResolution(null, "ZBS_NODE_RELATION_MISSING");
        }
        if (ref.getReasonCode() != null) {
            return new ProviderResolution(null, ref.getReasonCode());
        }
        List<ZbsCpuIsolationProvider> available = new ArrayList<>();
        for (ZbsCpuIsolationProvider provider :
                pluginRgty.getExtensionList(ZbsCpuIsolationProvider.class)) {
            if (provider.isAvailable(ref)) {
                available.add(provider);
            }
        }
        if (available.isEmpty()) {
            return new ProviderResolution(null, "ZBS_PROVIDER_UNAVAILABLE");
        }
        if (available.size() > 1) {
            return new ProviderResolution(null, "ZBS_PROVIDER_AMBIGUOUS");
        }
        return new ProviderResolution(available.get(0), null);
    }

    private void queryProvider(
            ZbsCpuIsolationProvider provider,
            ZbsNodeRef ref,
            ReturnValueCompletion<ZbsCpuIsolationFact> completion) {
        long timeout = providerTimeoutMillis();
        AtomicBoolean completed = new AtomicBoolean();
        ThreadFacadeImpl.TimeoutTaskReceipt receipt = thdf.submitTimeoutTask(
                () -> {
                    if (completed.compareAndSet(false, true)) {
                        completion.fail(operr(
                                ORG_ZSTACK_CORE_10000,
                                "ZBS_PROVIDER_CALL_TIMEOUT: Provider[%s] Query timed out",
                                provider.getProviderType()));
                    }
                },
                TimeUnit.MILLISECONDS,
                timeout);
        try {
            provider.query(
                    ref,
                    new ReturnValueCompletion<ZbsCpuIsolationFact>(completion) {
                        @Override
                        public void success(ZbsCpuIsolationFact fact) {
                            if (completed.compareAndSet(false, true)) {
                                receipt.cancel();
                                completion.success(fact);
                            }
                        }

                        @Override
                        public void fail(ErrorCode errorCode) {
                            if (completed.compareAndSet(false, true)) {
                                receipt.cancel();
                                completion.fail(errorCode);
                            }
                        }
                    });
        } catch (RuntimeException error) {
            if (completed.compareAndSet(false, true)) {
                receipt.cancel();
                completion.fail(operr(
                        ORG_ZSTACK_CORE_10000,
                        "ZBS_PROVIDER_CALL_FAILED: Provider[%s] Query failed: %s",
                        provider.getProviderType(), error.getMessage()));
            }
        }
    }

    private void updateProvider(
            ZbsCpuIsolationProvider provider,
            ZbsNodeRef ref,
            ZbsCpuIsolationUpdate update,
            Completion completion) {
        long timeout = providerTimeoutMillis();
        AtomicBoolean completed = new AtomicBoolean();
        ThreadFacadeImpl.TimeoutTaskReceipt receipt = thdf.submitTimeoutTask(
                () -> {
                    if (completed.compareAndSet(false, true)) {
                        completion.fail(operr(
                                ORG_ZSTACK_CORE_10000,
                                "ZBS_PROVIDER_CALL_TIMEOUT: Provider[%s] Update timed out",
                                provider.getProviderType()));
                    }
                },
                TimeUnit.MILLISECONDS,
                timeout);
        try {
            provider.update(ref, update, new Completion(completion) {
                @Override
                public void success() {
                    if (completed.compareAndSet(false, true)) {
                        receipt.cancel();
                        completion.success();
                    }
                }

                @Override
                public void fail(ErrorCode errorCode) {
                    if (completed.compareAndSet(false, true)) {
                        receipt.cancel();
                        completion.fail(errorCode);
                    }
                }
            });
        } catch (RuntimeException error) {
            if (completed.compareAndSet(false, true)) {
                receipt.cancel();
                completion.fail(operr(
                        ORG_ZSTACK_CORE_10000,
                        "ZBS_PROVIDER_CALL_FAILED: Provider[%s] Update failed: %s",
                        provider.getProviderType(), error.getMessage()));
            }
        }
    }

    private long providerTimeoutMillis() {
        return Math.max(
                1,
                TimeUnit.SECONDS.toMillis(
                        ZbsResourceAssignmentGlobalConfig.PROVIDER_CALL_TIMEOUT
                                .value(Long.class)));
    }

    private boolean isOwnerState(String state) {
        return "READY".equals(state)
                || "PENDING".equals(state)
                || "UNSUPPORTED".equals(state)
                || "ERROR".equals(state)
                || "DISABLED".equals(state);
    }

    private boolean empty(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean memoryMatches(Long expected, Long actual) {
        return expected == null
                || expected.equals(actual)
                || expected == 0L && actual == null;
    }

    private static class ProviderResolution {
        private final ZbsCpuIsolationProvider provider;
        private final String reasonCode;

        private ProviderResolution(
                ZbsCpuIsolationProvider provider,
                String reasonCode) {
            this.provider = provider;
            this.reasonCode = reasonCode;
        }
    }
}
