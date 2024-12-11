package org.zstack.storage.primary;

import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.core.cloudbus.CloudBus;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.core.trash.StorageTrashLifeCycleExtensionPoint;
import org.zstack.header.core.trash.InstallPathRecycleInventory;
import org.zstack.header.core.trash.InstallPathRecycleVO;
import org.zstack.header.storage.primary.AllocatePrimaryStorageSpaceMsg;
import org.zstack.header.storage.primary.PrimaryStorageConstant;
import org.zstack.header.storage.primary.PrimaryStorageVO;
import org.zstack.header.storage.primary.ReleasePrimaryStorageSpaceMsg;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DefaultPrimaryStorageTrashLifeCycleExtension implements StorageTrashLifeCycleExtensionPoint {
    @Autowired
    private DatabaseFacade dbf;

    @Autowired
    private CloudBus bus;

    private final static CLogger logger = Utils.getLogger(DefaultPrimaryStorageTrashLifeCycleExtension.class);

    protected boolean isPrimaryStorage(String storageType) {
        return PrimaryStorageVO.class.getSimpleName().equals(storageType);
    }

    @Override
    public void beforeCreateTrash(InstallPathRecycleVO vo) {
    }

    @Override
    public List<Long> afterCreateTrash(List<InstallPathRecycleInventory> inventoryList) {
        if (inventoryList.isEmpty()) {
            return Collections.EMPTY_LIST;
        }
        Map<String, Long> storageTrashMap = inventoryList.stream().filter(i -> isPrimaryStorage(i.getStorageType()))
                .collect(Collectors.groupingBy(InstallPathRecycleInventory::getStorageUuid,
                        Collectors.summingLong(InstallPathRecycleInventory::getSize)));
        List<AllocatePrimaryStorageSpaceMsg> amsgs = storageTrashMap.entrySet().stream().map(e -> {
            String storageUuid = e.getKey();
            Long totalTrashSize = e.getValue();
            AllocatePrimaryStorageSpaceMsg amsg = new AllocatePrimaryStorageSpaceMsg();
            amsg.setForce(true);
            amsg.setRequiredPrimaryStorageUuid(storageUuid);
            amsg.setNoOverProvisioning(true);
            amsg.setRequiredInstallUri(inventoryList.get(0).getInstallPath());
            amsg.setSize(totalTrashSize);
            bus.makeTargetServiceIdByResourceUuid(amsg, PrimaryStorageConstant.SERVICE_ID, amsg.getRequiredPrimaryStorageUuid());
            return amsg;
        }).collect(Collectors.toList());
        bus.send(amsgs);
        return inventoryList.stream().map(InstallPathRecycleInventory::getTrashId).collect(Collectors.toList());
    }

    @Override
    public List<Long> afterRemoveTrash(List<InstallPathRecycleInventory> inventoryList) {
        if (inventoryList.isEmpty()) {
            return Collections.EMPTY_LIST;
        }
        Map<String, Long> storageTrashMap = inventoryList.stream().filter(i -> isPrimaryStorage(i.getStorageType()))
                .collect(Collectors.groupingBy(InstallPathRecycleInventory::getStorageUuid,
                        Collectors.summingLong(InstallPathRecycleInventory::getSize)));
        List<ReleasePrimaryStorageSpaceMsg> rmsgs = storageTrashMap.entrySet().stream().map(e -> {
            String storageUuid = e.getKey();
            Long totalTrashSize = e.getValue();
            ReleasePrimaryStorageSpaceMsg amsg = new ReleasePrimaryStorageSpaceMsg();
            amsg.setPrimaryStorageUuid(storageUuid);
            amsg.setNoOverProvisioning(true);
            amsg.setAllocatedInstallUrl(inventoryList.get(0).getInstallPath());
            amsg.setDiskSize(totalTrashSize);
            bus.makeTargetServiceIdByResourceUuid(amsg, PrimaryStorageConstant.SERVICE_ID, amsg.getPrimaryStorageUuid());
            return amsg;
        }).collect(Collectors.toList());
        bus.send(rmsgs);
        return inventoryList.stream().map(InstallPathRecycleInventory::getTrashId).collect(Collectors.toList());
    }
}
