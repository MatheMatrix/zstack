# APIPrimaryStorageMigrateVolumeMsg 冷迁移数据盘逻辑分析

## 方案速览（TL 确认）

- 目标：开放 Ceph/NFS 不卸盘冷迁
- 触发条件：VM 必须 Stopped
- 校验：dstPS 需共享 VM 网络集群
- 候选 PS：挂载态按 VM nic L2 过滤
- 老语义：未挂载/Root 行为不变
- SB 已支持 不变（沿用旧路径）
- 风险拆分：startVm 并发另起 MR
- 工单：code-ZSV-12280（已实现）

---

本文整理 `APIPrimaryStorageMigrateVolumeMsg` 在各存储类型下的限制与执行流程，重点说明 Ceph / NFS / SharedBlock 对数据盘冷迁移的差异化约束及其背后的设计动机。

## 一、API 概览

- **类位置**: `premium/mevoco/src/main/java/org/zstack/storage/migration/primary/APIPrimaryStorageMigrateVolumeMsg.java`
- **REST**: `PUT /primary-storage/volumes/{volumeUuid}/actions`
- **默认超时**: 72 小时
- **入参**: `volumeUuid`、`dstPrimaryStorageUuid`
- **APINoSee（拦截器自动填充）**: `srcPrimaryStorageUuid`、`type`、`vmInstanceUuid`

## 二、API 拦截校验（`StorageMigrationApiInterceptor`）

### 通用约束

1. 源/目标 PS 不能为 `Disabled` 或 `Maintenance`
2. 卷 `Status == Ready`、`State != Disabled`
3. `srcPS == dstPS` 时仅 **Ceph** 允许（支持同 PS 换 pool）

### 数据盘专属约束

```java
if (srcVolume.getVmInstanceUuid() != null) {
    if (!srcPS.isSharedBlock()) {
        throw "please detach it before migration";    // Ceph / NFS
    } else if (state != Stopped) {
        throw "vm is not stopped";                    // SharedBlock
    }
}
```

- **Ceph / NFS**: 数据盘只要有挂载关系就拒绝（不看 VM 状态）
- **SharedBlock**: 允许挂载，但 VM 必须 Stopped
- **Shareable 共享卷**: 一律拒绝

### 存储类型矩阵

定义于 `conf/springConfigXml/storageMigration.xml` 的 `primaryStoragePrimaryStorageMetrics`：

| 源 → 目标 | 是否允许 |
|---|---|
| Ceph → Ceph | ✅ |
| NFS → NFS | ✅ |
| SharedBlock → SharedBlock | ✅（要求共享 cluster） |
| Local → Local | ❌ 单卷 API 不支持 |
| 跨类型（如 NFS→Ceph） | ❌ |

## 三、执行流程

```
APIPrimaryStorageMigrateVolumeMsg
  ↓ StorageMigrationBase.handle
PrimaryStorageMigrateVolumeMsg (本地消息)
  ↓ handle(PrimaryStorageMigrateVolumeMsg)
  ↓ 数据盘分支
MigrateDataVolumeOverlayMsg (以 volumeUuid 为 sync 锁)
  ↓
MigrateVolumeOnPrimaryStorageMsg
  ↓ 按 type 装配 FlowChain
执行迁移
```

> **注**：数据盘路径**不修改 VM 状态**。Root 盘冷迁移才有 `volumeMigrating → volumeMigrated` 状态机，并在迁移结束后通过 `setClusterAndLastHost` 重新计算 VM 可调度集群。

## 四、各存储 FlowChain 编排

| 类型 | Flow 序列 |
|---|---|
| Ceph → Ceph | `ReserveCapacityFromDstPSFlow` → `CephToCephMigrateVolumeFlow`（rbd export-diff / import-diff，按 snapshot 分 segment） → `UpdateVolumeVOFlow` → `DiscardVolumeReferenceFlow` → `CephDeleteVolumeFromSrcPSFlow` |
| NFS → NFS | `ReserveCapacityFromDstPSFlow` → `NfsToNfsMigrateVolumeFlow` → `UpdateVolumeVOFlow` → `DiscardVolumeReferenceFlow` → `NfsDeleteVolumeFromSrcPSFlow` |
| SharedBlock → SharedBlock | 同模式，由 `sharedblock` 子模块工厂提供 |

`UpdateVolumeVOFlow` 只改 `primaryStorageUuid` 和 `installPath`，**不动 `vmInstanceUuid`、设备地址、bus、cache** 等元数据。

## 五、候选 PS 计算（`getPrimaryStorageCandidatesForVolumeMigration`）

冷迁移路径：

1. 过滤 PS 状态正常（非 Disabled/Maintenance/Disconnected）
2. 按类型矩阵过滤（Ceph→Ceph / NFS→NFS / SB→SB）
3. **SharedBlock 额外约束**：`primaryStorageNotSupportCrossClusterMigrationMetrics` 要求源/目标 PS 共享 cluster
4. 是否包含源 PS 自身：仅当 `VmStorageMigrationMetric.isSupportSameStorage() == true`（Ceph 满足）

## 六、各存储限制对比

| 维度 | Ceph→Ceph | NFS→NFS | SharedBlock→SharedBlock | Local→Local |
|---|---|---|---|---|
| 单卷 API 允许 | ✅ | ✅ | ✅ | ❌ |
| 同 PS 迁移 | ✅（换 pool） | ❌ | ❌ | — |
| 跨 cluster 迁移 | ✅ | ✅ | ❌（必须共享 cluster） | — |
| 数据盘可挂载迁移 | ❌ 须 detach | ❌ 须 detach | ✅ VM 必须 Stopped | — |
| Shareable 卷 | ❌ | ❌ | ❌ | — |
| 实际主流场景 | 同 PS 不同 pool | 跨 NFS PS | 同 SB 集群内 LV 路径变更 | — |

## 七、关键问题：Ceph / NFS 为什么强制 detach？

经过逐层排查，否定了若干常见的"技术原因"：

| 误解 | 否定理由 |
|---|---|
| QEMU 句柄冲突 | VM 关机时没有 QEMU 进程，不存在 fd / librbd watcher 冲突 |
| 设备号 / bus / cache 元数据需要重建 | 这些字段与 PS 类型无关，迁移流程根本不动它们 |
| 目标 PS 不在 VM 当前 cluster 导致起不来 | Ceph / NFS 是共享存储，集群所有 host 都能访问；`PrimaryStorageClusterRefVO` 仅用于调度暴露，不构成访问壁垒 |

### 真实原因：产品语义切分

ZStack 把"卷迁移"这件事在 API 层切成两条路径：

- **单卷迁移 API**（`APIPrimaryStorageMigrateVolumeMsg`）= 处理**游离卷**
- **VM 迁移 API**（`APIPrimaryStorageMigrateVmMsg with withDataVolumes`）= 处理**挂着的数据盘**

代码作者在 2017 年画了这条边界，把"挂着迁数据盘"的责任推给 VM 迁移 API，单卷 API 不去处理这种场景的复杂度（即使 Ceph / NFS 共享存储完全做得到）。

### SharedBlock 为何例外

SharedBlock 用 LVM LV 做 volume。一次 detach 涉及：

- 在 host 上 `lvchange -an` 停用 LV
- 释放 lvmlockd 锁
- 清理 multipath / device-mapper 映射

attach 时全部反向重做一遍。代价远高于 NFS 改文件路径或 Ceph 改 rbd path。如果对 SharedBlock 也强制 detach，体验会非常糟糕。

折中方案：**允许挂着迁，但要求 VM Stopped**——避开 LV 在线变更与 QEMU 的并发风险。

> **重要**：技术上 Ceph / NFS 完全可以做"挂着迁数据盘"，只是没人写这段代码。这是产品边界，不是技术限制。

## 八、关键文件索引

| 文件 | 作用 |
|---|---|
| `primary/APIPrimaryStorageMigrateVolumeMsg.java` | API 消息定义 |
| `StorageMigrationApiInterceptor.java:245-372` | 入参校验 |
| `StorageMigrationBase.java:213` | API → 本地消息 |
| `StorageMigrationBase.java:893` | 数据盘 vs Root 盘分支 |
| `StorageMigrationBase.java:1402` | `migrateVolume` 装配 FlowChain |
| `StorageMigrationBase.java:1624` | 候选 PS 计算 |
| `conf/serviceConfig/storageMigration.xml` | 消息路由 |
| `conf/springConfigXml/storageMigration.xml` | 类型矩阵 + ChainFactory + Metric Bean |
| `primary/ceph/CephToCephMigrateVolumeFlow.java` | Ceph 迁移核心（segment + import-diff） |
| `primary/nfs/NfsToNfsMigrateVolumeChainFactory.java` | NFS 迁移工厂 |
| `primary/local/LocalToLocalMigrateVolumeChainFactory.java` | Local 迁移工厂（仅服务 VM 整机迁移） |
| `sharedblock/SharedBlockToSharedBlockVmStorageMigrationMetric.java` | SB 能力声明 |

## 九、一句话结论

> `APIPrimaryStorageMigrateVolumeMsg` 是**游离数据盘的冷迁移 API**，仅支持同类型存储间迁移
> （Ceph→Ceph / NFS→NFS / SharedBlock→SharedBlock）。
> Ceph 唯一允许同 PS 内换 pool（这是它真实的主流场景）；
> SharedBlock 唯一允许卷处于挂载态迁移（用 VM Stopped 替代 detach，避免 LV 在线变更代价）。
> **对挂载态数据盘强制 detach 是产品边界，不是技术限制**——挂着的数据盘请走 VM 迁移 API。
