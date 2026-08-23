package org.zstack.storage.zbs;

import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.core.asyncbatch.While;
import org.zstack.core.cascade.AbstractAsyncCascadeExtension;
import org.zstack.core.cascade.CascadeAction;
import org.zstack.core.cascade.CascadeConstant;
import org.zstack.core.cloudbus.EventCallback;
import org.zstack.core.cloudbus.EventFacade;
import org.zstack.core.db.Q;
import org.zstack.header.Component;
import org.zstack.header.core.Completion;
import org.zstack.header.core.WhileDoneCompletion;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.errorcode.ErrorCodeList;
import org.zstack.header.errorcode.OperationFailureException;
import org.zstack.header.physicalserver.PhysicalServerManager;
import org.zstack.header.storage.addon.primary.ExternalPrimaryStorageVO;
import org.zstack.header.storage.addon.primary.ExternalPrimaryStorageVO_;
import org.zstack.header.storage.primary.PrimaryStorageCanonicalEvent;
import org.zstack.header.storage.primary.PrimaryStorageInventory;
import org.zstack.header.storage.primary.PrimaryStorageVO;
import org.zstack.storage.addon.primary.ExternalPrimaryStorageCanonicalEvent;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.zstack.core.Platform.operr;
import static org.zstack.utils.clouderrorcode.CloudOperationsErrorCode.ORG_ZSTACK_CORE_10000;

public class ZbsCpuIsolationCascadeExtension extends AbstractAsyncCascadeExtension implements Component {
    private static final String NAME = "ZbsCpuIsolationAssignment";
    private static final CLogger logger = Utils.getLogger(
            ZbsCpuIsolationCascadeExtension.class);

    @Autowired
    private EventFacade evtf;
    @Autowired
    private ZbsNodeRefContributor nodeRefs;
    @Autowired
    private ZbsPhysicalServerIdentityResolver physicalServerIdentities;
    @Autowired(required = false)
    private PhysicalServerManager physicalServerManager;

    private EventCallback<ExternalPrimaryStorageCanonicalEvent.AddonInfoChangedData> addonInfoChanged;
    private EventCallback<PrimaryStorageCanonicalEvent.PrimaryStorageDeletedData> primaryStorageDeleted;

    @Override
    public void asyncCascade(CascadeAction action, Completion completion) {
        if (!PrimaryStorageVO.class.getSimpleName().equals(action.getParentIssuer())
                || !CascadeConstant.DELETION_CODES.contains(action.getActionCode())) {
            completion.success();
            return;
        }

        Set<String> deleting = deletingZbsPrimaryStorages(action);
        if (deleting.isEmpty()) {
            completion.success();
            return;
        }

        List<String> lastRelationServers = lastRelationServers(deleting);
        if (action.isActionCode(CascadeConstant.DELETION_CHECK_CODE)
                || lastRelationServers.isEmpty()) {
            completion.success();
            return;
        }

        release(
                lastRelationServers,
                action.isActionCode(CascadeConstant.DELETION_FORCE_DELETE_CODE),
                completion);
    }

    public void beforePersistAddonInfo(
            String primaryStorageUuid,
            AddonInfo addonInfo,
            Completion completion) {
        Set<String> newRelationServers =
                physicalServerIdentities.resolveServerUuids(
                        primaryStorageUuid, addonInfo);
        String oldAddonInfo = Q.New(ExternalPrimaryStorageVO.class)
                .select(ExternalPrimaryStorageVO_.addonInfo)
                .eq(ExternalPrimaryStorageVO_.uuid, primaryStorageUuid)
                .findValue();
        if (oldAddonInfo == null || oldAddonInfo.isEmpty()) {
            completion.success();
            return;
        }
        List<String> removedLastRelations = lastRelationServers(
                Collections.singleton(primaryStorageUuid));
        removedLastRelations.removeAll(newRelationServers);
        release(removedLastRelations, false, completion);
    }

    private void release(
            List<String> serverUuids,
            boolean force,
            Completion completion) {
        if (serverUuids.isEmpty()) {
            completion.success();
            return;
        }
        new While<>(serverUuids).each((serverUuid, each) -> {
            if (physicalServerManager == null) {
                each.done();
                return;
            }
            physicalServerManager.releaseResourceAssignment(
                    serverUuid,
                    ZbsResourceAssignmentBackend.ROLE_TYPE,
                    null,
                    force,
                    new Completion(each) {
                @Override
                public void success() {
                    each.done();
                }

                @Override
                public void fail(ErrorCode errorCode) {
                    if (force) {
                        logger.error(String.format(
                                "failed to release ZBS resource assignment before force deleting " +
                                        "the last relation for physical server[uuid:%s]: %s",
                                serverUuid, errorCode));
                        each.done();
                        return;
                    }
                    each.addError(errorCode);
                    each.allDone();
                }
            });
        }).run(new WhileDoneCompletion(completion) {
            @Override
            public void done(ErrorCodeList errorCodeList) {
                if (!errorCodeList.getCauses().isEmpty()) {
                    completion.fail(errorCodeList.getCauses().get(0));
                    return;
                }
                completion.success();
            }
        });
    }

    private Set<String> deletingZbsPrimaryStorages(CascadeAction action) {
        List<PrimaryStorageInventory> inventories = action.getParentIssuerContext();
        if (inventories == null || inventories.isEmpty()) {
            return Collections.emptySet();
        }
        List<String> candidates = new ArrayList<>();
        for (PrimaryStorageInventory inventory : inventories) {
            candidates.add(inventory.getUuid());
        }
        return new HashSet<>(Q.New(ExternalPrimaryStorageVO.class)
                .select(ExternalPrimaryStorageVO_.uuid)
                .in(ExternalPrimaryStorageVO_.uuid, candidates)
                .eq(ExternalPrimaryStorageVO_.identity, ZbsConstants.IDENTITY)
                .listValues());
    }

    private List<String> lastRelationServers(Set<String> deletingPrimaryStorageUuids) {
        Map<String, ZbsNodeRef> refs = nodeRefs.bulkList(Collections.emptySet());
        List<String> result = new ArrayList<>();
        for (ZbsNodeRef ref : refs.values()) {
            if (!ref.includesAnyPrimaryStorage(deletingPrimaryStorageUuids)) {
                continue;
            }
            if (ref.getReasonCode() != null) {
                throw new OperationFailureException(operr(
                        ORG_ZSTACK_CORE_10000,
                        "cannot determine the last ZBS relationship for physical server[uuid:%s], reason[%s]",
                        ref.getServerUuid(), ref.getReasonCode()));
            }
            boolean hasRemainingRelationship = ref.getPrimaryStorageUuids().stream()
                    .anyMatch(uuid -> !deletingPrimaryStorageUuids.contains(uuid));
            if (!hasRemainingRelationship) {
                result.add(ref.getServerUuid());
            }
        }
        return result;
    }

    private void sendReconcileAll() {
        if (physicalServerManager != null) {
            physicalServerManager.reconcileAll();
        }
    }

    @Override
    public boolean start() {
        addonInfoChanged = new EventCallback<ExternalPrimaryStorageCanonicalEvent.AddonInfoChangedData>() {
            @Override
            protected void run(
                    Map<String, String> tokens,
                    ExternalPrimaryStorageCanonicalEvent.AddonInfoChangedData data) {
                sendReconcileAll();
            }
        };
        primaryStorageDeleted = new EventCallback<PrimaryStorageCanonicalEvent.PrimaryStorageDeletedData>() {
            @Override
            protected void run(
                    Map<String, String> tokens,
                    PrimaryStorageCanonicalEvent.PrimaryStorageDeletedData data) {
                sendReconcileAll();
            }
        };
        evtf.on(ExternalPrimaryStorageCanonicalEvent.ADDON_INFO_CHANGED_PATH, addonInfoChanged);
        evtf.on(PrimaryStorageCanonicalEvent.PRIMARY_STORAGE_DELETED_PATH, primaryStorageDeleted);
        return true;
    }

    @Override
    public boolean stop() {
        if (addonInfoChanged != null) {
            evtf.off(addonInfoChanged);
        }
        if (primaryStorageDeleted != null) {
            evtf.off(primaryStorageDeleted);
        }
        return true;
    }

    @Override
    public List<String> getEdgeNames() {
        return Collections.singletonList(PrimaryStorageVO.class.getSimpleName());
    }

    @Override
    public String getCascadeResourceName() {
        return NAME;
    }

    @Override
    public CascadeAction createActionForChildResource(CascadeAction action) {
        return null;
    }
}
