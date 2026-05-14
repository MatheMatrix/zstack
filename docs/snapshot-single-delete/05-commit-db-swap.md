# 05 — Commit DB 翻转（最关键）

## 5.1 物理时序图

```
Commit 前：
  dst.qcow2  ←backing—  src.qcow2  ←backing—  grandchild.qcow2
   父                     子                    孙

blockCommit(top=src, base=dst) 完成后：
  dst.qcow2 内容 = 原 src 内容（src 的 delta 已 flush 进 dst 文件）
  src.qcow2 已被 DELETE（VIR_DOMAIN_BLOCK_COMMIT_DELETE）或将被回收

期望逻辑：
  src(保留) ← grandchild     ← 但 uuid 不变，所以用 path 互换实现：
  
DB 互换：
  dst.installPath ← src 旧 path     (dst 记录"指"已合并的文件)
  src.installPath ← dst 旧 path     (src 记录"指"待回收的文件)
  src.parentUuid  ← dst.parentUuid  (跨过 dst)
  src.distance    -= 1
```

## 5.2 为什么互换 path？

- `blockCommit` 落地的物理文件是 dst 的路径，但数据是 src 的
- 用户视角"保留的是子节点（src）"
- 互换后：dst 这条 DB 记录指向已合并文件，src 这条 DB 记录指向旧 dst 文件路径（即将被 `deleteVolumeSnapshotAndSyncVolumeSize` 删除）
- `cleanupAfterDeleteSingleSnapshot` 接下来按 `currentRoot.uuid`（dst 的 uuid）逻辑层删除，但物理文件路径已是旧 dst 文件，被回收

## 5.3 完整 SQL 操作（`VolumeTree.java:471-545`）

```java
new SQLBatch() {
    @Override
    protected void scripts() {
        // 1) src 及所有后代 distance -1
        List<String> descendantsUuid = srcLeaf.getDescendants().stream()
            .map(...uuid)
            .filter(u -> !u.equals(srcLeaf.uuid) && !u.equals(volume.uuid))
            .toList();
        List<VolumeSnapshotVO> vos = Q.New(VolumeSnapshotVO.class)
            .in(VolumeSnapshotVO_.uuid, descendantsUuid).list();
        vos.forEach(vo -> vo.setDistance(vo.getDistance() - 1));

        // 2) dst 是树根 → 新建 VolumeSnapshotTreeVO
        VolumeSnapshotTreeVO newTree = null;
        if (dstSnapshotInv.getParentUuid() == null) {
            newTree = new VolumeSnapshotTreeVO();
            newTree.setUuid(Platform.getUuid());
            newTree.setVolumeUuid(volume.getUuid());
            newTree.setStatus(VolumeSnapshotTreeStatus.Completed);
            newTree.setCurrent(descendantsUuid.contains(volume.getUuid()));
            if (getAliveChainSnapshotUuids().contains(srcSnapshotInv.getUuid())) {
                newTree.setCurrent(true);
            }
            dbf.persist(newTree);
        }
        if (!vos.isEmpty() && newTree != null) {
            VolumeSnapshotTreeVO finalNewTree = newTree;
            vos.forEach(vo -> vo.setTreeUuid(finalNewTree.getUuid()));
        }

        // 3) dst 互换 installPath, size
        sql(VolumeSnapshotVO.class).eq(VolumeSnapshotVO_.uuid, dstSnapshotInv.getUuid())
            .set(VolumeSnapshotVO_.primaryStorageInstallPath, srcSnapshotInv.getPrimaryStorageInstallPath())
            .set(VolumeSnapshotVO_.size, srcSnapshotInv.getSize())
            .update();

        // 4) GroupRef 同步 installPath
        if (dstSnapshotInv.getGroupUuid() != null) {
            sql(VolumeSnapshotGroupRefVO.class)
                .eq(VolumeSnapshotGroupRefVO_.volumeSnapshotGroupUuid, dstSnapshotInv.getGroupUuid())
                .eq(VolumeSnapshotGroupRefVO_.volumeSnapshotUuid, dstSnapshotInv.getUuid())
                .set(VolumeSnapshotGroupRefVO_.volumeSnapshotInstallPath,
                     srcSnapshotInv.getPrimaryStorageInstallPath())
                .update();
        }

        // 5) src 互换 installPath，parentUuid 跨过 dst，distance -1
        sql(VolumeSnapshotVO.class).eq(VolumeSnapshotVO_.uuid, srcSnapshotInv.getUuid())
            .set(VolumeSnapshotVO_.primaryStorageInstallPath, dstSnapshotInv.getPrimaryStorageInstallPath())
            .set(VolumeSnapshotVO_.size, newInstallPathSize)
            .set(VolumeSnapshotVO_.distance, srcSnapshotInv.getDistance() - 1)
            .set(VolumeSnapshotVO_.parentUuid, dstSnapshotInv.getParentUuid())
            .set(VolumeSnapshotVO_.treeUuid,
                 newTree != null ? newTree.getUuid() : srcSnapshotInv.getTreeUuid())
            .update();

        dbf.updateCollection(vos);
    }
}.execute();   // 单事务原子提交
```

## 5.4 commit() 主流程概览

`VolumeSnapshotTreeBase.java:921-1094`：

1. `AllocatePrimaryStorageSpaceMsg` —— 预分配空间
2. 分支：
   - 在线 → `CommitVolumeSnapshotOnHypervisorMsg` → KVMHost → libvirt blockCommit
   - 离线 → `CommitVolumeSnapshotOnPrimaryStorageMsg` → 存储后端 → qemu-img commit
3. 透传 `srcChildrenInstallPathInDb`（兄弟节点列表，见 11 节）
4. `updateDatabaseAfterCommit` —— DB 翻转
5. 失败 rollback：通过 FlowChain 释放已分配存储空间
