package org.zstack.storage.encrypt;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.zstack.header.core.Completion;
import org.zstack.header.core.NoErrorCompletion;
import org.zstack.core.db.Q;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.vm.VmInstanceInventory;
import org.zstack.header.vm.VmInstanceMigrateExtensionPoint;
import org.zstack.header.vm.VmMigrationType;
import org.zstack.header.vm.VmPreMigrationExtensionPoint;
import org.zstack.header.volume.VolumeAO_;
import org.zstack.header.volume.VolumeInventory;
import org.zstack.header.volume.VolumeVO;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.zstack.core.Platform.operr;

/**
 * Ensures LUKS secrets are pre-defined on the destination host before live migration,
 * and cleaned up on the source host after migration (or on the destination host on failure).
 *
 * Only handles HostMigration. PrimaryStorageMigration keeps the VM on the same host,
 * so the existing libvirt secrets remain valid; VolumeEncryptedStartExtension handles
 * the next start_vm on any new host.
 */
@Configurable(preConstruction = true, autowire = Autowire.BY_TYPE)
public class VolumeEncryptedMigrateVmExtension
        implements VmPreMigrationExtensionPoint, VmInstanceMigrateExtensionPoint {

    private static final CLogger logger = Utils.getLogger(VolumeEncryptedMigrateVmExtension.class);

    @Autowired
    private VolumeEncryptedSecretHelper secretHelper;
    @Autowired
    private VolumeEncryptedResourceKeyBackend keyBackend;

    @Override
    public void preVmMigration(VmInstanceInventory vm, VmMigrationType type, String dstHostUuid, Completion completion) {
        if (type != VmMigrationType.HostMigration) {
            completion.success();
            return;
        }
        if (vm == null || StringUtils.isBlank(dstHostUuid)) {
            completion.success();
            return;
        }

        List<VolumeInventory> encryptedVols = collectEncryptedVolumes(vm);
        if (encryptedVols.isEmpty()) {
            completion.success();
            return;
        }

        String vmUuid = vm.getUuid();
        String srcHostUuid = getSourceHostUuid(vm, vm.getHostUuid());
        if (StringUtils.isBlank(srcHostUuid)) {
            completion.success();
            return;
        }

        try {
            for (VolumeInventory vol : encryptedVols) {
                secretHelper.resolveOrDefineSecretForVolumeMigration(srcHostUuid, dstHostUuid, vmUuid, vol.getUuid());
            }
            completion.success();
        } catch (Exception e) {
            completion.fail(operr("failed to pre-define LUKS secret for encrypted VM[uuid:%s] on host[uuid:%s]: %s",
                    vmUuid, dstHostUuid, e.getMessage()));
        }
    }

    @Override
    public void afterMigrateVm(VmInstanceInventory inv, String srcHostUuid, NoErrorCompletion completion) {
        if (inv == null) {
            completion.done();
            return;
        }

        String vmUuid = inv.getUuid();
        String sourceHostUuid = getSourceHostUuid(inv, srcHostUuid);
        if (StringUtils.isBlank(sourceHostUuid)) {
            completion.done();
            return;
        }

        String destHostUuid = inv.getHostUuid();
        if (StringUtils.isBlank(destHostUuid) || sourceHostUuid.equals(destHostUuid)) {
            completion.done();
            return;
        }

        for (VolumeInventory vol : collectEncryptedVolumes(inv)) {
            Integer keyVersion = keyBackend.findKeyVersionByVolume(vol.getUuid());
            secretHelper.deleteSecretOnHostBestEffort(sourceHostUuid, vmUuid, vol.getUuid(), keyVersion);
        }
        completion.done();
    }

    @Override
    public void failedToMigrateVm(VmInstanceInventory inv, String destHostUuid, ErrorCode reason, NoErrorCompletion completion) {
        if (inv == null || StringUtils.isBlank(destHostUuid)) {
            completion.done();
            return;
        }

        String vmUuid = inv.getUuid();
        for (VolumeInventory vol : collectEncryptedVolumes(inv)) {
            Integer keyVersion = keyBackend.findKeyVersionByVolume(vol.getUuid());
            secretHelper.deleteSecretOnHostBestEffort(destHostUuid, vmUuid, vol.getUuid(), keyVersion);
        }
        completion.done();
    }

    private String getSourceHostUuid(VmInstanceInventory inv, String srcHostUuid) {
        if (StringUtils.isNotBlank(srcHostUuid)) {
            return srcHostUuid;
        }
        return inv == null ? null : inv.getLastHostUuid();
    }

    private List<VolumeInventory> collectEncryptedVolumes(VmInstanceInventory vm) {
        List<VolumeInventory> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        VolumeInventory root = vm.getRootVolume();
        if (root != null && Boolean.TRUE.equals(root.getEncrypted())) {
            result.add(root);
            seen.add(root.getUuid());
        }
        if (vm.getAllDiskVolumes() != null) {
            for (VolumeInventory v : vm.getAllDiskVolumes()) {
                if (v != null && Boolean.TRUE.equals(v.getEncrypted()) && seen.add(v.getUuid())) {
                    result.add(v);
                }
            }
        }

        List<VolumeVO> dbVolumes = Q.New(VolumeVO.class)
                .eq(VolumeAO_.vmInstanceUuid, vm.getUuid())
                .list();
        for (VolumeVO v : dbVolumes) {
            if (v.isEncrypted() && seen.add(v.getUuid())) {
                result.add(VolumeInventory.valueOf(v));
            }
        }
        return result;
    }
}
