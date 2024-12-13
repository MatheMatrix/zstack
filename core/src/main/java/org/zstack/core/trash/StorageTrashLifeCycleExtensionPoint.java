package org.zstack.core.trash;

import org.zstack.header.core.trash.InstallPathRecycleInventory;
import org.zstack.header.core.trash.InstallPathRecycleVO;

import java.util.List;

/**
 * Created by mingjian.deng on 2019/10/29.
 */
public interface StorageTrashLifeCycleExtensionPoint {
    void beforeCreateTrash(InstallPathRecycleVO vo);

    List<Long> afterCreateTrash(List<InstallPathRecycleInventory> inventoryList);

    List<Long> beforeRemoveTrash(List<InstallPathRecycleInventory> inventoryList);
}
