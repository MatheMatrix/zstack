package org.zstack.compute.vm;

import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.core.Platform;
import org.zstack.core.componentloader.PluginRegistry;
import org.zstack.core.workflow.FlowChainBuilder;
import org.zstack.header.core.Completion;
import org.zstack.header.core.NoErrorCompletion;
import org.zstack.header.core.workflow.*;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.network.l3.L3NetworkInventory;
import org.zstack.header.vm.*;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import java.util.*;
import java.util.stream.Collectors;

import static org.zstack.utils.clouderrorcode.CloudOperationsErrorCode.ORG_ZSTACK_COMPUTE_VM_10321;

public class VmNicLifecycleManager implements
        PreVmInstantiateResourceExtensionPoint,
        VmReleaseResourceExtensionPoint,
        VmInstanceMigrateExtensionPoint,
        InstantiateResourceOnAttachingNicExtensionPoint,
        ReleaseNetworkServiceOnDetachingNicExtensionPoint {

    private static final CLogger logger = Utils.getLogger(VmNicLifecycleManager.class);

    @Autowired
    private PluginRegistry pluginRgty;

    // Resolve lazily: PluginRegistry is only populated AFTER Spring init phase
    // (Platform.loadExtensions), so caching in init() would miss all plugins.
    private List<VmNicLifecycleExtensionPoint> getExtensions() {
        return pluginRgty.getExtensionList(VmNicLifecycleExtensionPoint.class);
    }

    // ================= Filtering =================

    private List<VmNicInventory> filterNics(VmNicLifecycleExtensionPoint ext,
                                            List<VmNicInventory> allNics) {
        List<VmNicInventory> matched = new ArrayList<>();
        for (VmNicInventory nic : allNics) {
            try {
                if (ext.isApplicable(nic)) {
                    matched.add(nic);
                }
            } catch (Exception e) {
                logger.error(String.format("[VmNicLifecycle] %s.isApplicable(nic=%s) " +
                        "threw exception", ext.getClass().getSimpleName(), nic.getUuid()), e);
                throw new RuntimeException(String.format(
                        "%s.isApplicable failed for nic[uuid:%s]",
                        ext.getClass().getSimpleName(), nic.getUuid()), e);
            }
        }
        return matched;
    }

    // ================= Fail-fast runners =================

    private void runSetup(Iterator<VmNicLifecycleExtensionPoint> it,
                          String hostUuid, List<VmNicInventory> allNics,
                          Completion completion) {
        if (!it.hasNext()) {
            completion.success();
            return;
        }
        VmNicLifecycleExtensionPoint ext = it.next();
        List<VmNicInventory> nics;
        try {
            nics = filterNics(ext, allNics);
        } catch (RuntimeException e) {
            completion.fail(Platform.operr(ORG_ZSTACK_COMPUTE_VM_10321, "%s", e.getMessage()));
            return;
        }
        if (nics.isEmpty()) {
            runSetup(it, hostUuid, allNics, completion);
            return;
        }
        long start = System.currentTimeMillis();
        try {
            ext.setupOnHost(hostUuid, nics, new Completion(completion) {
                @Override
                public void success() {
                    logger.debug(String.format("[VmNicLifecycle] %s.setupOnHost" +
                            "(host=%s, nics=%d) completed in %dms",
                            ext.getClass().getSimpleName(), hostUuid, nics.size(),
                            System.currentTimeMillis() - start));
                    runSetup(it, hostUuid, allNics, completion);
                }

                @Override
                public void fail(ErrorCode errorCode) {
                    logger.warn(String.format("[VmNicLifecycle] %s.setupOnHost(host=%s) " +
                            "failed: %s", ext.getClass().getSimpleName(), hostUuid, errorCode));
                    completion.fail(errorCode);
                }
            });
        } catch (Throwable t) {
            logger.warn(String.format("[VmNicLifecycle] %s.setupOnHost(host=%s) " +
                    "threw exception", ext.getClass().getSimpleName(), hostUuid), t);
            completion.fail(Platform.operr(ORG_ZSTACK_COMPUTE_VM_10321, "%s.setupOnHost threw: %s",
                    ext.getClass().getSimpleName(), t.getMessage()));
        }
    }

    private void runPreMigrate(Iterator<VmNicLifecycleExtensionPoint> it,
                               String srcHostUuid, String destHostUuid,
                               List<VmNicInventory> allNics, Completion completion) {
        if (!it.hasNext()) {
            completion.success();
            return;
        }
        VmNicLifecycleExtensionPoint ext = it.next();
        List<VmNicInventory> nics;
        try {
            nics = filterNics(ext, allNics);
        } catch (RuntimeException e) {
            completion.fail(Platform.operr(ORG_ZSTACK_COMPUTE_VM_10321, "%s", e.getMessage()));
            return;
        }
        if (nics.isEmpty()) {
            runPreMigrate(it, srcHostUuid, destHostUuid, allNics, completion);
            return;
        }
        long start = System.currentTimeMillis();
        try {
            ext.preMigrate(srcHostUuid, destHostUuid, nics, new Completion(completion) {
                @Override
                public void success() {
                    logger.debug(String.format("[VmNicLifecycle] %s.preMigrate" +
                            "(src=%s, dest=%s, nics=%d) completed in %dms",
                            ext.getClass().getSimpleName(), srcHostUuid, destHostUuid,
                            nics.size(), System.currentTimeMillis() - start));
                    runPreMigrate(it, srcHostUuid, destHostUuid, allNics, completion);
                }

                @Override
                public void fail(ErrorCode errorCode) {
                    logger.warn(String.format("[VmNicLifecycle] %s.preMigrate" +
                            "(src=%s, dest=%s) failed: %s",
                            ext.getClass().getSimpleName(), srcHostUuid,
                            destHostUuid, errorCode));
                    completion.fail(errorCode);
                }
            });
        } catch (Throwable t) {
            logger.warn(String.format("[VmNicLifecycle] %s.preMigrate(src=%s, dest=%s) " +
                    "threw exception", ext.getClass().getSimpleName(),
                    srcHostUuid, destHostUuid), t);
            completion.fail(Platform.operr(ORG_ZSTACK_COMPUTE_VM_10321, "%s.preMigrate threw: %s",
                    ext.getClass().getSimpleName(), t.getMessage()));
        }
    }

    // ================= Fail-logged runners =================

    private void runCleanup(Iterator<VmNicLifecycleExtensionPoint> it,
                            String hostUuid, List<VmNicInventory> allNics,
                            NoErrorCompletion completion) {
        if (!it.hasNext()) {
            completion.done();
            return;
        }
        VmNicLifecycleExtensionPoint ext = it.next();
        List<VmNicInventory> nics;
        try {
            nics = filterNics(ext, allNics);
        } catch (RuntimeException e) {
            logger.warn(String.format("[VmNicLifecycle] %s.isApplicable threw exception " +
                    "during cleanup", ext.getClass().getSimpleName()), e);
            runCleanup(it, hostUuid, allNics, completion);
            return;
        }
        if (nics.isEmpty()) {
            runCleanup(it, hostUuid, allNics, completion);
            return;
        }
        long start = System.currentTimeMillis();
        try {
            ext.cleanupFromHost(hostUuid, nics, new NoErrorCompletion(completion) {
                @Override
                public void done() {
                    logger.debug(String.format("[VmNicLifecycle] %s.cleanupFromHost" +
                            "(host=%s, nics=%d) completed in %dms",
                            ext.getClass().getSimpleName(), hostUuid, nics.size(),
                            System.currentTimeMillis() - start));
                    runCleanup(it, hostUuid, allNics, completion);
                }
            });
        } catch (Throwable t) {
            logger.warn(String.format("[VmNicLifecycle] %s.cleanupFromHost(host=%s) " +
                    "threw exception", ext.getClass().getSimpleName(), hostUuid), t);
            runCleanup(it, hostUuid, allNics, completion);
        }
    }

    private void runCleanupStale(Iterator<VmNicLifecycleExtensionPoint> it,
                                 String lastHostUuid, List<VmNicInventory> allNics,
                                 NoErrorCompletion completion) {
        if (!it.hasNext()) {
            completion.done();
            return;
        }
        VmNicLifecycleExtensionPoint ext = it.next();
        List<VmNicInventory> nics;
        try {
            nics = filterNics(ext, allNics);
        } catch (RuntimeException e) {
            logger.warn(String.format("[VmNicLifecycle] %s.isApplicable threw exception " +
                    "during cleanupStale", ext.getClass().getSimpleName()), e);
            runCleanupStale(it, lastHostUuid, allNics, completion);
            return;
        }
        if (nics.isEmpty()) {
            runCleanupStale(it, lastHostUuid, allNics, completion);
            return;
        }
        long start = System.currentTimeMillis();
        try {
            ext.cleanupStaleResource(lastHostUuid, nics, new NoErrorCompletion(completion) {
                @Override
                public void done() {
                    logger.debug(String.format("[VmNicLifecycle] %s.cleanupStaleResource" +
                            "(lastHost=%s, nics=%d) completed in %dms",
                            ext.getClass().getSimpleName(), lastHostUuid, nics.size(),
                            System.currentTimeMillis() - start));
                    runCleanupStale(it, lastHostUuid, allNics, completion);
                }
            });
        } catch (Throwable t) {
            logger.warn(String.format("[VmNicLifecycle] %s.cleanupStaleResource" +
                    "(lastHost=%s) threw exception",
                    ext.getClass().getSimpleName(), lastHostUuid), t);
            runCleanupStale(it, lastHostUuid, allNics, completion);
        }
    }

    private void runPostMigrate(Iterator<VmNicLifecycleExtensionPoint> it,
                                String srcHostUuid, String destHostUuid,
                                List<VmNicInventory> allNics,
                                NoErrorCompletion completion) {
        if (!it.hasNext()) {
            completion.done();
            return;
        }
        VmNicLifecycleExtensionPoint ext = it.next();
        List<VmNicInventory> nics;
        try {
            nics = filterNics(ext, allNics);
        } catch (RuntimeException e) {
            logger.warn(String.format("[VmNicLifecycle] %s.isApplicable threw exception " +
                    "during postMigrate", ext.getClass().getSimpleName()), e);
            runPostMigrate(it, srcHostUuid, destHostUuid, allNics, completion);
            return;
        }
        if (nics.isEmpty()) {
            runPostMigrate(it, srcHostUuid, destHostUuid, allNics, completion);
            return;
        }
        long start = System.currentTimeMillis();
        try {
            ext.postMigrate(srcHostUuid, destHostUuid, nics, new Completion(completion) {
                @Override
                public void success() {
                    logger.debug(String.format("[VmNicLifecycle] %s.postMigrate" +
                            "(src=%s, dest=%s, nics=%d) completed in %dms",
                            ext.getClass().getSimpleName(), srcHostUuid, destHostUuid,
                            nics.size(), System.currentTimeMillis() - start));
                    runPostMigrate(it, srcHostUuid, destHostUuid, allNics, completion);
                }

                @Override
                public void fail(ErrorCode errorCode) {
                    logger.warn(String.format("[VmNicLifecycle] %s.postMigrate" +
                            "(src=%s, dest=%s) failed: %s - continuing (fail-logged)",
                            ext.getClass().getSimpleName(), srcHostUuid,
                            destHostUuid, errorCode));
                    runPostMigrate(it, srcHostUuid, destHostUuid, allNics, completion);
                }
            });
        } catch (Throwable t) {
            logger.warn(String.format("[VmNicLifecycle] %s.postMigrate(src=%s, dest=%s) " +
                    "threw exception", ext.getClass().getSimpleName(),
                    srcHostUuid, destHostUuid), t);
            runPostMigrate(it, srcHostUuid, destHostUuid, allNics, completion);
        }
    }

    private void runFailedMigrate(Iterator<VmNicLifecycleExtensionPoint> it,
                                  String srcHostUuid, String destHostUuid,
                                  List<VmNicInventory> allNics,
                                  NoErrorCompletion completion) {
        if (!it.hasNext()) {
            completion.done();
            return;
        }
        VmNicLifecycleExtensionPoint ext = it.next();
        List<VmNicInventory> nics;
        try {
            nics = filterNics(ext, allNics);
        } catch (RuntimeException e) {
            logger.warn(String.format("[VmNicLifecycle] %s.isApplicable threw exception " +
                    "during failedMigrate", ext.getClass().getSimpleName()), e);
            runFailedMigrate(it, srcHostUuid, destHostUuid, allNics, completion);
            return;
        }
        if (nics.isEmpty()) {
            runFailedMigrate(it, srcHostUuid, destHostUuid, allNics, completion);
            return;
        }
        long start = System.currentTimeMillis();
        try {
            ext.failedMigrate(srcHostUuid, destHostUuid, nics,
                    new NoErrorCompletion(completion) {
                @Override
                public void done() {
                    logger.debug(String.format("[VmNicLifecycle] %s.failedMigrate" +
                            "(src=%s, dest=%s, nics=%d) completed in %dms",
                            ext.getClass().getSimpleName(), srcHostUuid, destHostUuid,
                            nics.size(), System.currentTimeMillis() - start));
                    runFailedMigrate(it, srcHostUuid, destHostUuid, allNics, completion);
                }
            });
        } catch (Throwable t) {
            logger.warn(String.format("[VmNicLifecycle] %s.failedMigrate(src=%s, dest=%s) " +
                    "threw exception", ext.getClass().getSimpleName(),
                    srcHostUuid, destHostUuid), t);
            runFailedMigrate(it, srcHostUuid, destHostUuid, allNics, completion);
        }
    }

    // ================= VM instantiate / release (F-003) =================

    @Override
    public void preBeforeInstantiateVmResource(VmInstanceSpec spec)
            throws VmInstantiateResourceException {
        // sync hook - no resource operation
    }

    @Override
    public void preInstantiateVmResource(VmInstanceSpec spec, Completion completion) {
        List<VmNicInventory> allNics = spec.getDestNics();
        if (allNics == null || allNics.isEmpty() || getExtensions().isEmpty()) {
            completion.success();
            return;
        }
        if (spec.getDestHost() == null) {
            completion.success();
            return;
        }
        String destHostUuid = spec.getDestHost().getUuid();
        String lastHostUuid = spec.getVmInventory().getLastHostUuid();
        VmInstanceConstant.VmOperation op = spec.getCurrentVmOperation();

        FlowChain chain = FlowChainBuilder.newSimpleFlowChain();
        chain.setName("vmnic-lifecycle-pre-instantiate-"
                + spec.getVmInventory().getUuid());

        // Step 1: HA stale cleanup when lastHost != destHost && Start op
        if (lastHostUuid != null && !lastHostUuid.equals(destHostUuid)
                && op == VmInstanceConstant.VmOperation.Start) {
            chain.then(new NoRollbackFlow() {
                final String __name__ = "cleanup-stale-resource-from-last-host";

                @Override
                public void run(final FlowTrigger trigger, Map data) {
                    runCleanupStale(getExtensions().iterator(), lastHostUuid, allNics,
                            new NoErrorCompletion(trigger) {
                                @Override
                                public void done() {
                                    trigger.next();
                                }
                            });
                }
            });
        }

        // Step 2: setup on destination host
        chain.then(new NoRollbackFlow() {
            final String __name__ = "setup-on-dest-host";

            @Override
            public void run(final FlowTrigger trigger, Map data) {
                runSetup(getExtensions().iterator(), destHostUuid, allNics,
                        new Completion(trigger) {
                            @Override
                            public void success() {
                                trigger.next();
                            }

                            @Override
                            public void fail(ErrorCode errorCode) {
                                trigger.fail(errorCode);
                            }
                        });
            }
        });

        chain.done(new FlowDoneHandler(completion) {
            @Override
            public void handle(Map data) {
                completion.success();
            }
        }).error(new FlowErrorHandler(completion) {
            @Override
            public void handle(ErrorCode errCode, Map data) {
                completion.fail(errCode);
            }
        }).start();
    }

    @Override
    public void preReleaseVmResource(VmInstanceSpec spec, Completion completion) {
        // preRelease called on VmInstantiateResourcePreFlow rollback
        doCleanup(spec, new NoErrorCompletion(completion) {
            @Override
            public void done() {
                completion.success();
            }
        });
    }

    @Override
    public void releaseVmResource(VmInstanceSpec spec, Completion completion) {
        if (spec.getCurrentVmOperation() == VmInstanceConstant.VmOperation.Reboot) {
            // Reboot does not change host; resources remain valid
            completion.success();
            return;
        }
        doCleanup(spec, new NoErrorCompletion(completion) {
            @Override
            public void done() {
                completion.success();
            }
        });
    }

    private void doCleanup(VmInstanceSpec spec, NoErrorCompletion completion) {
        List<VmNicInventory> allNics = spec.getDestNics();
        if (allNics == null || allNics.isEmpty() || getExtensions().isEmpty()) {
            completion.done();
            return;
        }
        if (spec.getDestHost() == null) {
            completion.done();
            return;
        }
        String hostUuid = spec.getDestHost().getUuid();
        runCleanup(getExtensions().iterator(), hostUuid, allNics, completion);
    }

    // ================= Hot attach / detach (F-004) =================

    @Override
    public void instantiateResourceOnAttachingNic(VmInstanceSpec spec,
                                                  L3NetworkInventory l3,
                                                  Completion completion) {
        VmInstanceInventory vm = spec.getVmInventory();
        if (!VmInstanceState.Running.toString().equals(vm.getState())
                || getExtensions().isEmpty()) {
            completion.success();
            return;
        }
        List<VmNicInventory> newNics = spec.getDestNics().stream()
                .filter(nic -> nic.getL3NetworkUuid().equals(l3.getUuid()))
                .collect(Collectors.toList());
        if (newNics.isEmpty()) {
            completion.success();
            return;
        }
        String hostUuid = vm.getHostUuid();
        if (hostUuid == null) {
            completion.success();
            return;
        }
        runSetup(getExtensions().iterator(), hostUuid, newNics, completion);
    }

    @Override
    public void releaseResourceOnAttachingNic(VmInstanceSpec spec,
                                              L3NetworkInventory l3,
                                              NoErrorCompletion completion) {
        // Attach rollback
        doCleanupForNic(spec, l3, completion);
    }

    @Override
    public void releaseResourceOnDetachingNic(VmInstanceSpec spec,
                                              VmNicInventory nic,
                                              NoErrorCompletion completion) {
        if (getExtensions().isEmpty()) {
            completion.done();
            return;
        }
        String hostUuid = spec.getVmInventory().getHostUuid();
        if (hostUuid == null) {
            completion.done();
            return;
        }
        runCleanup(getExtensions().iterator(), hostUuid,
                Collections.singletonList(nic), completion);
    }

    private void doCleanupForNic(VmInstanceSpec spec, L3NetworkInventory l3,
                                 NoErrorCompletion completion) {
        if (getExtensions().isEmpty()) {
            completion.done();
            return;
        }
        VmInstanceInventory vm = spec.getVmInventory();
        String hostUuid = vm.getHostUuid();
        if (hostUuid == null) {
            completion.done();
            return;
        }
        List<VmNicInventory> nics = spec.getDestNics().stream()
                .filter(nic -> nic.getL3NetworkUuid().equals(l3.getUuid()))
                .collect(Collectors.toList());
        if (nics.isEmpty()) {
            completion.done();
            return;
        }
        runCleanup(getExtensions().iterator(), hostUuid, nics, completion);
    }

    // ================= Migration (F-005) =================

    @Override
    public void preMigrateVm(VmInstanceInventory inv, String destHostUuid,
                             Completion completion) {
        if (getExtensions().isEmpty()) {
            completion.success();
            return;
        }
        List<VmNicInventory> allNics = inv.getVmNics();
        if (allNics == null || allNics.isEmpty()) {
            completion.success();
            return;
        }
        String srcHostUuid = inv.getHostUuid();
        runPreMigrate(getExtensions().iterator(), srcHostUuid, destHostUuid, allNics,
                completion);
    }

    @Override
    public void beforeMigrateVm(VmInstanceInventory inv, String destHostUuid) {
        // sync hook - no routing
    }

    @Override
    public void afterMigrateVm(VmInstanceInventory inv, String srcHostUuid,
                               NoErrorCompletion completion) {
        // notify hook - no routing
        completion.done();
    }

    @Override
    public void failedToMigrateVm(VmInstanceInventory inv, String destHostUuid,
                                  ErrorCode reason, NoErrorCompletion completion) {
        if (getExtensions().isEmpty()) {
            completion.done();
            return;
        }
        List<VmNicInventory> allNics = inv.getVmNics();
        if (allNics == null || allNics.isEmpty()) {
            completion.done();
            return;
        }
        String srcHostUuid = inv.getHostUuid();
        runFailedMigrate(getExtensions().iterator(), srcHostUuid, destHostUuid, allNics,
                completion);
    }

    // postMigrate hook - fail-logged since migration already completed
    @Override
    public void postMigrateVm(VmInstanceInventory inv, String destHostUuid,
                              Completion completion) {
        if (getExtensions().isEmpty()) {
            completion.success();
            return;
        }
        List<VmNicInventory> allNics = inv.getVmNics();
        if (allNics == null || allNics.isEmpty()) {
            completion.success();
            return;
        }
        String srcHostUuid = inv.getHostUuid();
        runPostMigrate(getExtensions().iterator(), srcHostUuid, destHostUuid, allNics,
                new NoErrorCompletion(completion) {
                    @Override
                    public void done() {
                        completion.success();
                    }
                });
    }
}
