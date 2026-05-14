package org.zstack.storage.snapshot.group;

import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.core.cascade.AbstractAsyncCascadeExtension;
import org.zstack.core.cascade.CascadeAction;
import org.zstack.core.cascade.CascadeConstant;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.core.db.Q;
import org.zstack.core.db.SQL;
import org.zstack.header.core.Completion;
import org.zstack.header.storage.snapshot.group.VolumeSnapshotGroupRefVO;
import org.zstack.header.storage.snapshot.group.VolumeSnapshotGroupRefVO_;
import org.zstack.header.storage.snapshot.group.VolumeSnapshotGroupVO;
import org.zstack.header.storage.snapshot.group.VolumeSnapshotGroupVO_;
import org.zstack.header.vm.VmDeletionStruct;
import org.zstack.header.vm.VmInstanceVO;
import org.zstack.header.vm.additions.VmHostBackupFileVO;
import org.zstack.header.vm.additions.VmHostBackupFileVO_;
import org.zstack.header.vm.additions.VmHostFileManager;
import org.zstack.header.vm.devices.VmInstanceResourceMetadataManager;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Cascade extension keyed on VmInstance for cleaning up VolumeSnapshotGroup VOs
 * when a VM is destroyed.
 *
 * Background: snapshot groups are VM-scoped. When a VM is destroyed, any remaining
 * group VOs (whether complete or incomplete due to partial single-snapshot deletions)
 * become orphaned. Without this cleanup, those rows would survive beyond the VM
 * and pollute downstream queries.
 *
 * On DELETION_CHECK we do NOT block — VM destroy should proceed even with
 * incomplete groups (per product decision); cleanup is automatic.
 */
public class VolumeSnapshotGroupCascadeExtension extends AbstractAsyncCascadeExtension {
    private static final CLogger logger = Utils.getLogger(VolumeSnapshotGroupCascadeExtension.class);

    private static final String NAME = VolumeSnapshotGroupVO.class.getSimpleName();

    @Autowired
    private DatabaseFacade dbf;
    @Autowired
    private VmInstanceResourceMetadataManager vidm;
    @Autowired
    private VmHostFileManager vmHostFileManager;

    @Override
    public void asyncCascade(CascadeAction action, Completion completion) {
        if (action.isActionCode(CascadeConstant.DELETION_CLEANUP_CODE)) {
            handleDeletionCleanup(action, completion);
        } else if (action.isActionCode(CascadeConstant.DELETION_DELETE_CODE,
                CascadeConstant.DELETION_FORCE_DELETE_CODE)) {
            handleDeletion(action, completion);
        } else {
            completion.success();
        }
    }

    private void handleDeletion(CascadeAction action, Completion completion) {
        if (!VmInstanceVO.class.getSimpleName().equals(action.getParentIssuer())) {
            completion.success();
            return;
        }

        List<String> vmUuids = vmUuidsFromAction(action);
        if (vmUuids.isEmpty()) {
            completion.success();
            return;
        }

        List<String> groupUuids = Q.New(VolumeSnapshotGroupVO.class)
                .select(VolumeSnapshotGroupVO_.uuid)
                .in(VolumeSnapshotGroupVO_.vmInstanceUuid, vmUuids)
                .listValues();
        if (groupUuids.isEmpty()) {
            completion.success();
            return;
        }

        logger.debug(String.format("VM destroy cascade: force-removing %d snapshot group(s) %s for vm(s) %s " +
                "(includes any incomplete groups from prior single-snapshot deletions)",
                groupUuids.size(), groupUuids, vmUuids));

        // 1. drop all refs first (FK-like constraint via business logic)
        SQL.New(VolumeSnapshotGroupRefVO.class)
                .in(VolumeSnapshotGroupRefVO_.volumeSnapshotGroupUuid, groupUuids)
                .delete();

        // 2. clean associated metadata + backup files
        groupUuids.forEach(vidm::deleteArchiveVmInstanceResourceMetadataGroup);
        cleanVmHostBackupFilesForGroup(groupUuids);

        // 3. remove group VOs
        dbf.removeByPrimaryKeys(groupUuids, VolumeSnapshotGroupVO.class);

        completion.success();
    }

    private void cleanVmHostBackupFilesForGroup(List<String> groupUuids) {
        if (groupUuids.isEmpty()) {
            return;
        }

        List<String> backupUuidList = Q.New(VmHostBackupFileVO.class)
                .in(VmHostBackupFileVO_.resourceUuid, groupUuids)
                .select(VmHostBackupFileVO_.uuid)
                .listValues();

        backupUuidList.forEach(vmHostFileManager::cleanVmHostBackupFile);
    }

    private void handleDeletionCleanup(CascadeAction action, Completion completion) {
        try {
            dbf.eoCleanup(VolumeSnapshotGroupVO.class);
        } catch (Throwable t) {
            logger.warn("eoCleanup VolumeSnapshotGroupVO failed: " + t.getMessage());
        } finally {
            completion.success();
        }
    }

    private List<String> vmUuidsFromAction(CascadeAction action) {
        Object ctx = action.getParentIssuerContext();
        if (ctx == null) {
            return Collections.emptyList();
        }
        List<String> uuids = new ArrayList<>();
        if (ctx instanceof List) {
            for (Object o : (List<?>) ctx) {
                if (o instanceof VmDeletionStruct) {
                    uuids.add(((VmDeletionStruct) o).getInventory().getUuid());
                }
            }
        }
        return uuids;
    }

    @Override
    public List<String> getEdgeNames() {
        return Arrays.asList(VmInstanceVO.class.getSimpleName());
    }

    @Override
    public String getCascadeResourceName() {
        return NAME;
    }
}
