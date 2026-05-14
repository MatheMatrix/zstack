# 06 — Pull / pullToVolume DB 改写

## 6.1 Pull 物理语义

```
Pull 前：
  grandparent ← src(待删) ← dst(子) ← descendants

qemu-img rebase(dst → grandparent) 完成后：
  dst.qcow2 文件中数据 = 原 dst delta + 原 src delta（合并）
  dst 的 backing file = grandparent
  src.qcow2 待删除

DB 改写：
  dst.parentUuid ← src.parentUuid (跨过 src)
  dst.distance   -= 1
  dst.size       = 合并后的实际大小
  所有后代 distance -1
```

## 6.2 `updateDatabaseAfterPull()` — `VolumeTree.java:418-469`

```java
public void updateDatabaseAfterPull(VolumeSnapshotInventory srcSnapshotInv,
        VolumeSnapshotLeaf dstSnapshotLeaf, long newInstallPathSize) {

    VolumeSnapshotInventory dstSnapshotInv = dstSnapshotLeaf.getInventory();

    new SQLBatch() {
        @Override
        protected void scripts() {
            // 1) 收集 dst 及所有后代（不含 volume 虚拟节点）
            List<String> descendantsUuid = dstSnapshotLeaf.getDescendants().stream()
                    .map(...uuid)
                    .filter(u -> !u.equals(volume.uuid))
                    .toList();
            List<VolumeSnapshotVO> vos = q(VolumeSnapshotVO.class)
                    .in(VolumeSnapshotVO_.uuid, descendantsUuid).list();

            // 2) distance -1；dst 节点特殊处理
            vos.forEach(vo -> {
                vo.setDistance(vo.getDistance() - 1);
                if (vo.getUuid().equals(dstSnapshotInv.getUuid())) {
                    vo.setParentUuid(srcSnapshotInv.getParentUuid());
                    vo.setSize(newInstallPathSize);
                }
            });

            // 3) src 是树根 → 新建 VolumeSnapshotTreeVO，后代迁移
            VolumeSnapshotTreeVO newTree = null;
            if (srcSnapshotInv.getParentUuid() == null) {
                newTree = new VolumeSnapshotTreeVO();
                newTree.setCurrent(descendantsUuid.contains(volume.getUuid()));
                newTree.setVolumeUuid(volume.getUuid());
                newTree.setUuid(Platform.getUuid());
                newTree.setStatus(VolumeSnapshotTreeStatus.Completed);
                if (getAliveChainSnapshotUuids().contains(dstSnapshotInv.getUuid())) {
                    newTree.setCurrent(true);
                }
                dbf.persist(newTree);
                VolumeSnapshotTreeVO finalNewTree = newTree;
                vos.forEach(vo -> vo.setTreeUuid(finalNewTree.getUuid()));
            }

            dbf.updateCollection(vos);

            // 4) 新树建好且 dst 就是 volume 自身（pull-to-volume 边界）→ 原树标记非 current
            if (newTree != null && dstSnapshotInv.getUuid().equals(volume.getUuid())
                    && q(VolumeSnapshotTreeVO.class)
                        .eq(VolumeSnapshotTreeVO_.uuid, srcSnapshotInv.getTreeUuid()).count() == 1) {
                sql(VolumeSnapshotTreeVO.class)
                    .eq(VolumeSnapshotTreeVO_.uuid, srcSnapshotInv.getTreeUuid())
                    .set(VolumeSnapshotTreeVO_.current, false).update();
            }
        }
    }.execute();
}
```

## 6.3 `updateDatabaseAfterPullToVolume()` — `VolumeTree.java:396-416`

特殊场景：dst 是 volume 自身（即 latest 快照被合并进活跃 volume 文件）。

```java
public void updateDatabaseAfterPullToVolume(VolumeSnapshotInventory srcSnapshotInv) {
    new SQLBatch() {
        @Override
        protected void scripts() {
            // 1) src（latest）标记为非 latest
            sql(VolumeSnapshotVO.class).eq(VolumeSnapshotVO_.uuid, srcSnapshotInv.getUuid())
                .set(VolumeSnapshotVO_.latest, false).update();

            // 2) src 的父节点成为新的 latest
            if (srcSnapshotInv.getParentUuid() != null) {
                sql(VolumeSnapshotVO.class)
                    .eq(VolumeSnapshotVO_.uuid, srcSnapshotInv.getParentUuid())
                    .set(VolumeSnapshotVO_.latest, true).update();
            }

            // 3) src 是树根 → 整棵树 current=false（链空了）
            if (srcSnapshotInv.getParentUuid() == null) {
                sql(VolumeSnapshotTreeVO.class)
                    .eq(VolumeSnapshotTreeVO_.uuid, srcSnapshotInv.getTreeUuid())
                    .set(VolumeSnapshotTreeVO_.current, false).update();
            }
        }
    }.execute();
}
```

## 6.4 pull() 主流程概览（`VolumeSnapshotTreeBase.java:1097-1304`）

1. `GetVolumeBackingChainFromPrimaryStorageMsg` —— 取祖父路径
2. `AllocatePrimaryStorageSpaceMsg`
3. 分支：
   - 在线 → `PullVolumeSnapshotOnHypervisorMsg` → libvirt block stream
   - 离线 → `PullVolumeSnapshotOnPrimaryStorageMsg` → `qemu-img rebase`
4. `updateDatabaseAfterPull` / `updateDatabaseAfterPullToVolume`
5. 失败 rollback：释放分配空间
