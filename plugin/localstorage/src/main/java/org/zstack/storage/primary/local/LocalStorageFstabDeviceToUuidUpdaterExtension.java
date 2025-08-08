package org.zstack.storage.primary.local;


import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Configurable;
import org.zstack.core.db.Q;
import org.zstack.core.thread.AsyncThread;
import org.zstack.header.Component;
import org.zstack.header.managementnode.ManagementNodeReadyExtensionPoint;
import org.zstack.header.storage.primary.PrimaryStorageVO;
import org.zstack.header.storage.primary.PrimaryStorageVO_;
import org.zstack.kvm.KVMHostVO;
import org.zstack.kvm.KVMHostVO_;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;
import org.zstack.utils.ssh.Ssh;
import org.zstack.utils.ssh.SshException;
import org.zstack.utils.ssh.SshResult;

import javax.persistence.Tuple;
import java.util.List;

import static org.zstack.storage.primary.local.LocalFstabDeviceToUuidUpdaterGlobalProperty.LOCAL_FSTAB_DEVICE_TO_UUID_UPDATER;


@Configurable(preConstruction = true, autowire = Autowire.BY_TYPE)
public class LocalStorageFstabDeviceToUuidUpdaterExtension implements Component, ManagementNodeReadyExtensionPoint {
    protected static final CLogger logger = Utils.getLogger(LocalStorageFstabDeviceToUuidUpdaterExtension.class);

    @Override
    public boolean start() {
        return true;
    }

    @Override
    public boolean stop() {
        return true;
    }

    @Override
    public void managementNodeReady() {
        logger.debug("1111111111LocalStorageFstabDeviceToUuidUpdaterExtension1111111111111");
        if (LOCAL_FSTAB_DEVICE_TO_UUID_UPDATER) {
            logger.debug("1111111111LocalStorageFstabDeviceToUuidUpdaterExtension2222222222");
            localStorageFstabDeviceToUuidUpdater();
        }
        logger.debug("1111111111LocalStorageFstabDeviceToUuidUpdaterExtension333333333333333");
        localStorageFstabDeviceToUuidUpdater();
    }

    @AsyncThread
    private void localStorageFstabDeviceToUuidUpdater() {
        List<Tuple> tuples = Q.New(PrimaryStorageVO.class)
                .eq(PrimaryStorageVO_.type, LocalStorageConstants.LOCAL_STORAGE_TYPE)
                .select(PrimaryStorageVO_.uuid, PrimaryStorageVO_.mountPath).listTuple();
        tuples.forEach(tuple -> {
            String psUuid = tuple.get(0, String.class);
            String mountPath = tuple.get(1, String.class);

            List<String> hostUuids = Q.New(LocalStorageHostRefVO.class)
                    .eq(LocalStorageHostRefVO_.primaryStorageUuid, psUuid)
                    .select(LocalStorageHostRefVO_.hostUuid).listValues();
            if (hostUuids.isEmpty()) {
                logger.debug(String.format("no hosts found for primary storage[uuid:%s, path:%s]", psUuid, mountPath));
                return;
            }

            List<KVMHostVO> hostVOS = Q.New(KVMHostVO.class).in(KVMHostVO_.uuid, hostUuids).list();
            hostVOS.forEach(host -> {
                Ssh ssh = null;
                try {
                    ssh = new Ssh();
                    ssh.setUsername(host.getUsername()).setPassword(host.getPassword()).setPort(host.getPort())
                            .setHostname(host.getManagementIp()).setTimeout(60);

                    // 1 Check if the mount path exists in /etc/fstab
                    SshResult fstabCheck = ssh.command(String.format("grep -wF '%s' /etc/fstab", mountPath)).run();
                    ssh.reset();
                    if (fstabCheck.getReturnCode() != 0) {
                        logger.debug(String.format("Mount path [%s] not found in /etc/fstab on host [%s], skipping UUID update", mountPath, host.getUuid()));
                        return;
                    }

                    // 2 Check if the fstab entry already uses UUID
                    String fstabEntry = fstabCheck.getStdout().trim();
                    if (fstabEntry.startsWith("UUID=")) {
                        logger.debug(String.format("Host[%s] already uses UUID for [%s]", host.getUuid(), mountPath));
                        return;
                    }

                    // 3 Extract the device path from the fstab entry.  fstabEntry = '/dev/sda /root/data1 xfs defaults 0 0'
                    String device = fstabEntry.split("\\s+")[0].trim();

                    // 4 Backup the current fstab file
                    SshResult backupResult = ssh.command("cp -f /etc/fstab /etc/fstab.bak").run();
                    if (backupResult.getReturnCode() != 0) {
                        logger.error(String.format("Failed to backup fstab on host[%s]: %s", host.getUuid(), backupResult.getStderr()));
                        return;
                    }

                    // 5 Get the UUID of the device using blkid
                    SshResult blkidResult = ssh.command(String.format("blkid -s UUID -o value '%s'", device)).run();
                    if (blkidResult.getReturnCode() != 0 || blkidResult.getStdout().isEmpty()) {
                        logger.warn(String.format("Failed to get UUID for device[%s] on host[%s]: %s", device, host.getUuid(), blkidResult.getStderr()));
                        return;
                    }
                    String uuid = blkidResult.getStdout().trim();

                    // 6 Delete the existing fstabEntry using sed
                    SshResult delResult = ssh.command(String.format("sed -i '\\|^%s|d' /etc/fstab", fstabEntry.replace("/", "\\/"))).run();
                    if (delResult.getReturnCode() != 0) {
                        throw new SshException("sed delete failed: " + delResult.getStderr());
                    }

                    // Update the fstab entry with UUID
                    String newFstabEntry = fstabEntry.replace(device, String.format("UUID=%s", uuid));
                    SshResult appendResult = ssh.command(String.format("echo '%s' >> /etc/fstab", newFstabEntry)).run();
                    if (appendResult.getReturnCode() != 0) {
                        throw new SshException("Failed to append new fstab entry: " + appendResult.getStderr());
                    }

                    SshResult verifyResult = ssh.command(String.format("grep -wF '%s' /etc/fstab", newFstabEntry)).run();
                    if (verifyResult.getReturnCode() != 0) {
                        throw new SshException(String.format("Failed to verify UUID[%s] in fstab on host[%s]: %s", uuid, host.getUuid(), verifyResult.getStderr()));
                    }

                    logger.info(String.format("Updated fstab for device[%s] to UUID[%s] on host[%s]", device, uuid, host.getUuid()));
                } catch (SshException e) {
                    logger.warn(String.format("fstab update failed on host[%s]: %s. attempting rollback", host.getUuid(), e.getMessage()));

                    if (ssh != null) {
                        SshResult rollback = ssh.command("[ -f /etc/fstab.bak ] && mv -f /etc/fstab.bak /etc/fstab").run();
                        if (rollback.getReturnCode() != 0) {
                            logger.error("rollback failed on host " + host.getUuid());
                        }
                    }
                } finally {
                    if (ssh != null) {
                        ssh.close();
                    }
                }
            });
        });
    }
}