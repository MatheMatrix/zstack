package org.zstack.storage.primary.local;


import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.transaction.annotation.Transactional;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.core.db.Q;
import org.zstack.core.gc.GCStatus;
import org.zstack.core.gc.GarbageCollectorVO;
import org.zstack.core.gc.GarbageCollectorVO_;
import org.zstack.core.thread.AsyncThread;
import org.zstack.header.Component;
import org.zstack.header.exception.CloudRuntimeException;
import org.zstack.header.host.HostVO;
import org.zstack.header.managementnode.ManagementNodeReadyExtensionPoint;
import org.zstack.header.storage.primary.PrimaryStorageVO;
import org.zstack.header.storage.primary.PrimaryStorageVO_;
import org.zstack.kvm.KVMHostVO;
import org.zstack.kvm.KVMHostVO_;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;
import org.zstack.utils.ssh.Ssh;
import org.zstack.utils.ssh.SshResult;

import javax.persistence.Query;
import javax.persistence.Tuple;
import java.util.*;
import java.util.stream.Stream;

import static org.zstack.storage.primary.local.UpdateBlockDeviceUuidGlobalProperty.DEDUPLICATEVOLUMEGC;


@Configurable(preConstruction = true, autowire = Autowire.BY_TYPE)
public class UpdateBlockDeviceUuidExtension implements Component, ManagementNodeReadyExtensionPoint {
    protected static final CLogger logger = Utils.getLogger(UpdateBlockDeviceUuidExtension.class);

    @Autowired
    protected DatabaseFacade dbf;

    class DeleteDuplicateVolumeGC {
        private Query query;
        private Integer first = 0;
        private Integer max = 1000;
        private long count = Q.New(GarbageCollectorVO.class).eq(GarbageCollectorVO_.status, GCStatus.Idle).count();
        private String sql = getSql();

        @AsyncThread
        @Transactional
        public void deleteDuplicate() {
            doDeleteDuplicate();
        }

        @Transactional
        public void doDeleteDuplicate() {
            query = dbf.getEntityManager().createQuery(sql);
            query.setMaxResults(max);
            query.setParameter("status", GCStatus.Idle);

            int times = (int) (count / max) + (count % max != 0 ? 1 : 0);
            HashSet<String> volumeUuids = new HashSet<>();
            for (int i = 0; i < times; i++) {
                query.setFirstResult(first);
                List<GarbageCollectorVO> vos = query.getResultList();
                dedup(vos, volumeUuids);
            }
        }

        private String getSql() {
            String sql = StringUtils.join(Stream.of("ceph", "shared-block", "nfs", "smp", "nfs", "delete-volume", "mini-storage",
                    "aliyun-ebs", "aliyun-nas").map(it -> " vo.name like 'gc-" + it + "%'").iterator(), " or");
            return String.format("select vo from GarbageCollectorVO vo where vo.status = :status and (%s)", sql);
        }

        private String getContextVolumeUuid(GarbageCollectorVO vo) {
            String context = vo.getContext();
            JsonObject jo = new JsonParser().parse(context).getAsJsonObject();
            return jo.get("volume").getAsJsonObject().get("uuid").getAsString();
        }

        private void dedup(List<GarbageCollectorVO> vos, HashSet<String> volumeUuids) {
            vos.forEach(vo -> {
                String volUuid = getContextVolumeUuid(vo);
                if (volumeUuids.contains(volUuid)) {
                    dbf.getEntityManager().remove(vo);
                } else {
                    volumeUuids.add(volUuid);
                    first += 1;
                }
            });
        }
    }

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
        if (DEDUPLICATEVOLUMEGC) {
            List<PrimaryStorageVO> psVOs = Q.New(PrimaryStorageVO.class).eq(PrimaryStorageVO_.type, "LocalStorage").list();
            psVOs.forEach(ps -> {
                String mountPath = ps.getMountPath();
                List<String> hostUuids = Q.New(LocalStorageHostRefVO.class).select(LocalStorageHostRefVO_.primaryStorageUuid, LocalStorageHostRefVO_.hostUuid).find();
                List<KVMHostVO> KVMHostVO = Q.New(KVMHostVO.class).in(KVMHostVO_.uuid, hostUuids).find();

                KVMHostVO.forEach(host -> {
                    Ssh ssh = new Ssh();
                    ssh.setUsername(host.getUsername()).setPassword(host.getPassword()).setPort(host.getPort())
                            .setHostname(host.getManagementIp()).setTimeout(60);

                    // Check if the mount path exists in /etc/fstab
                    String checkCommand = String.format("grep -wF '%s' /etc/fstab", mountPath);
                    SshResult fstabCheck = ssh.command(checkCommand).run();
                    ssh.reset();
                    if (fstabCheck.getReturnCode() != 0) {
                        logger.warn(String.format("Mount path [%s] not found in /etc/fstab on host [%s], skipping UUID update", mountPath, host.getUuid()));
                        return;
                    }

                    // /dev/sda /root/data1 xfs defaults 0 0
                    String fstabEntry = fstabCheck.getStdout().trim();
                    if (fstabEntry.startsWith("UUID=")) {
                        return;
                    }

                    // Extract the device path from the fstab entry
                    String devicePath = fstabEntry.split(" ")[0];

                    // 获取 device UUID
                    SshResult ret = ssh.command(String.format("blkid -s UUID -o value '%s'", devicePath)).run();
                    String uuid = ret.getStdout().trim();
                    if (uuid.isEmpty()) {
                        logger.warn(String.format("Failed to get UUID for device [%s] on host [%s], skipping UUID update", devicePath, host.getUuid()));
                        return;
                    }

                    // Update the fstab entry with UUID
                    String uuidFstabEntry = fstabEntry.replace(devicePath, String.format("UUID=%s", uuid));

                    // 删除原有的 fstab 条目


                    ret = ssh.command(String.format("echo '%s' >> /etc/fstab", fstabEntry)).run();
                    ssh.reset();
                    if (ret.getReturnCode() != 0) {
                        logger.warn("Failed to append new fstab entry: " + ret.getStderr());
                    }
                });
            });
        }
    }
}