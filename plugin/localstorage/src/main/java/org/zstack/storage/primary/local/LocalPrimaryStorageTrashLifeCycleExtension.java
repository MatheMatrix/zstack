package org.zstack.storage.primary.local;

import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.core.cloudbus.CloudBus;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.core.db.Q;
import org.zstack.core.trash.StorageTrashLifeCycleExtensionPoint;
import org.zstack.header.core.trash.InstallPathRecycleInventory;
import org.zstack.header.core.trash.InstallPathRecycleVO;
import org.zstack.header.host.HostVO;
import org.zstack.header.host.HostVO_;
import org.zstack.header.storage.primary.*;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import java.util.*;
import java.util.stream.Collectors;

public class LocalPrimaryStorageTrashLifeCycleExtension implements StorageTrashLifeCycleExtensionPoint {
    @Autowired
    private DatabaseFacade dbf;

    @Autowired
    private CloudBus bus;

    private final static CLogger logger = Utils.getLogger(LocalPrimaryStorageTrashLifeCycleExtension.class);

    private boolean isLocalPrimaryStorage(String storageUuid) {
        return LocalStorageConstants.LOCAL_STORAGE_TYPE.equals(
                Q.New(PrimaryStorageVO.class)
                .eq(PrimaryStorageVO_.uuid, storageUuid)
                .select(PrimaryStorageVO_.type).findValue());
    }

    @Override
    public void beforeCreateTrash(InstallPathRecycleVO vo) {
        if (!isLocalPrimaryStorage(vo.getStorageUuid())) {
            return;
        }

        List<String> hostUuids = Q.New(LocalStorageResourceRefVO.class).
                eq(LocalStorageResourceRefVO_.primaryStorageUuid, vo.getStorageUuid()).
                eq(LocalStorageResourceRefVO_.resourceUuid, vo.getResourceUuid()).
                select(LocalStorageResourceRefVO_.hostUuid).listValues();
        if (!hostUuids.isEmpty()) {
            vo.setHostUuid(hostUuids.get(0));
        }
    }

    @Override
    public List<Long> afterCreateTrash(List<InstallPathRecycleInventory> trashList) {
        if (trashList.isEmpty()) {
            return Collections.EMPTY_LIST;
        }
        Map<String, List<InstallPathRecycleInventory>> storageTrashMap = trashList.stream()
                .collect(Collectors.groupingBy(InstallPathRecycleInventory::getStorageUuid));

        List<String> localStorageUuids = Q.New(PrimaryStorageVO.class).select(PrimaryStorageVO_.uuid)
                .in(PrimaryStorageVO_.uuid, storageTrashMap.keySet())
                .eq(PrimaryStorageVO_.type, LocalStorageConstants.LOCAL_STORAGE_TYPE).listValues();

        for (String localStorageUuid : localStorageUuids) {
            List<InstallPathRecycleInventory> localTrashList = storageTrashMap.get(localStorageUuid);
            List<AllocatePrimaryStorageSpaceMsg> amsgs = localTrashList.stream().collect(
                Collectors.groupingBy(InstallPathRecycleInventory::getHostUuid,
                Collectors.summingLong(InstallPathRecycleInventory::getSize)))
                .entrySet().stream().map(e -> {
                    String hostUuid = e.getKey();
                    Long totalTrashSize = e.getValue();

                    LocalStorageUtils.InstallPath installPath = new LocalStorageUtils.InstallPath();
                    installPath.hostUuid = hostUuid;
                    installPath.installPath = localTrashList.get(0).getInstallPath();

                    AllocatePrimaryStorageSpaceMsg amsg = new AllocatePrimaryStorageSpaceMsg();
                    amsg.setForce(true);
                    amsg.setRequiredPrimaryStorageUuid(localStorageUuid);
                    amsg.setNoOverProvisioning(true);
                    amsg.setRequiredInstallUri(installPath.makeFullPath());
                    amsg.setSize(totalTrashSize);
                    bus.makeTargetServiceIdByResourceUuid(amsg, PrimaryStorageConstant.SERVICE_ID, amsg.getRequiredPrimaryStorageUuid());
                    return amsg;
                }).collect(Collectors.toList());

            bus.send(amsgs);
        }

        return trashList.stream().filter(t -> localStorageUuids.contains(t.getStorageUuid()))
                .map(InstallPathRecycleInventory::getTrashId).collect(Collectors.toList());
    }

    @Override
    public List<Long> beforeRemoveTrash(List<InstallPathRecycleInventory> trashList) {
        if (trashList.isEmpty()) {
            return Collections.EMPTY_LIST;
        }

        Map<String, List<InstallPathRecycleInventory>> storageTrashMap = trashList.stream()
                .collect(Collectors.groupingBy(InstallPathRecycleInventory::getStorageUuid));

        List<String> localStorageUuids = Q.New(PrimaryStorageVO.class).select(PrimaryStorageVO_.uuid)
                .in(PrimaryStorageVO_.uuid, storageTrashMap.keySet())
                .eq(PrimaryStorageVO_.type, LocalStorageConstants.LOCAL_STORAGE_TYPE).listValues();

        if (localStorageUuids.isEmpty()) {
            return Collections.EMPTY_LIST;
        }

        List<String> existingHostUuids = Q.New(HostVO.class).select(HostVO_.uuid)
                .in(HostVO_.uuid, trashList.stream().map(InstallPathRecycleInventory::getHostUuid).collect(Collectors.toList()))
                .listValues();


        for (String localStorageUuid : localStorageUuids) {
            List<InstallPathRecycleInventory> localTrashList = storageTrashMap.get(localStorageUuid);
            List<ReleasePrimaryStorageSpaceMsg> rmsgs = new ArrayList<>();
            localTrashList.stream().collect(Collectors.groupingBy(InstallPathRecycleInventory::getHostUuid, Collectors.summingLong(InstallPathRecycleInventory::getSize)))
                    .forEach((hostUuid, totalTrashSize) -> {
                    if (!existingHostUuids.contains(hostUuid)) {
                        logger.debug(String.format("host %s has been deleted, skip releasing trash capacity[trash size:%s]", hostUuid, totalTrashSize));
                        return;
                    }

                    LocalStorageUtils.InstallPath installPath = new LocalStorageUtils.InstallPath();
                    installPath.hostUuid = hostUuid;
                    installPath.installPath = localTrashList.get(0).getInstallPath();

                    ReleasePrimaryStorageSpaceMsg rmsg = new ReleasePrimaryStorageSpaceMsg();
                    rmsg.setPrimaryStorageUuid(localStorageUuid);
                    rmsg.setNoOverProvisioning(true);
                    rmsg.setAllocatedInstallUrl(installPath.makeFullPath());
                    rmsg.setDiskSize(totalTrashSize);
                    bus.makeTargetServiceIdByResourceUuid(rmsg, PrimaryStorageConstant.SERVICE_ID, rmsg.getPrimaryStorageUuid());
                    rmsgs.add(rmsg);
                });

            bus.send(rmsgs);
        }

        return trashList.stream().filter(t -> localStorageUuids.contains(t.getStorageUuid()))
                .map(InstallPathRecycleInventory::getTrashId).collect(Collectors.toList());
    }
}
