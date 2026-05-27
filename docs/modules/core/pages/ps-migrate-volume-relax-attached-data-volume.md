# 放宽 Ceph/NFS 数据盘冷迁移：挂载态可迁移（VM Stopped）可行性分析与设计

## 一、背景

`APIPrimaryStorageMigrateVolumeMsg` 当前对挂载态数据盘的约束：

| 存储 | 现行约束 |
|---|---|
| Ceph→Ceph | 数据盘有挂载关系（`vmInstanceUuid != null`）即拒绝，强制用户先 detach |
| NFS→NFS | 同上 |
| SharedBlock→SharedBlock | 允许挂载，但 VM 必须 Stopped |
| Local→Local | 不支持单卷 API |

经分析（见 [primary-storage-migrate-volume.md](primary-storage-migrate-volume.md)），Ceph/NFS 的 detach 要求是**产品边界**而非技术约束。在挂载态卷迁移已检测到目标 PS 与 VM 的 cluster/L2 兼容性的前提下，Ceph/NFS 同样能做到"挂着迁"。

## 二、目标

让 Ceph→Ceph、NFS→NFS 的数据盘冷迁移对齐 SharedBlock 语义：

1. 卷可保持 `vmInstanceUuid != null`（挂载关系不变）
2. VM 必须处于 `Stopped` 状态
3. 迁移完成后用户**无需 re-attach**，下次启动 VM 自动使用新 PS

不在本期范围：

- Local→Local（单卷 API 不支持，本期不扩展）
- Running/Paused VM 的在线迁移（属于热迁移路径，由 `KvmBlockLiveMigrationWorkFlow` 处理）

## 三、可行性分析

### 3.1 当前流程是否依赖"卷已 detach"？

逐项检查冷迁移 Flow 对 `vmInstanceUuid` 的依赖：

| Flow | 行为 | 依赖挂载状态？ |
|---|---|---|
| `ReserveCapacityFromDstPSFlow` | 在目标 PS 上预留容量 | ❌ 不关心 |
| `CephToCephMigrateVolumeFlow` | rbd export-diff / import-diff，按 segment 复制 | ❌ 不关心 |
| `NfsToNfsMigrateVolumeFlow` | 复制 qcow2 / raw 文件到目标 NFS export | ❌ 不关心 |
| `UpdateVolumeVOFlow` | 只更新 `primaryStorageUuid` 和 `installPath` | ❌ 不动 `vmInstanceUuid` |
| `DiscardVolumeReferenceFlow` | 处理快照引用 | ❌ 不关心 |
| `CephDeleteVolumeFromSrcPSFlow` / `NfsDeleteVolumeFromSrcPSFlow` | 删除源 PS 上的卷 | ❌ 不关心 |

**结论**：Flow 链本身不依赖"卷已 detach"，移除拦截器中的 detach 强制要求**不会破坏现有 Flow 行为**。

### 3.2 VM 启动是否能正确使用新 PS？

VM 启动时 `VmInstanceBase.startVm` 通过 `VolumeInventory.valueOf(VolumeVO)` 实时构造 domain XML：

- `installPath` 来自 VolumeVO（已被 `UpdateVolumeVOFlow` 改写）
- `primaryStorageUuid` 同上
- 调度器（`DesignatedAllocateHostMsg`）会校验目标 host 所在 cluster 是否挂载新 PS

**前提条件**：目标 PS 必须挂到 VM 可调度 cluster。这正是 Root 盘冷迁移在拦截器里做的校验（`StorageMigrationApiInterceptor.java:304-320`）。**数据盘场景需要补一段类似校验**。

### 3.3 Ceph/NFS 的共享存储语义

| 存储 | cluster 可见性 |
|---|---|
| Ceph | 单一 Ceph 集群，所有 host 通过 librbd 访问。`PrimaryStorageClusterRefVO` 决定能否被调度到该 cluster |
| NFS | 通过 mount 在 host 上可见。`PrimaryStorageClusterRefVO` 决定哪些 cluster 会 mount |

只要目标 PS attach 到了 VM 可调度的 cluster（且 L2 网络可达），迁移完 VM 启动毫无问题。

### 3.4 候选 PS 计算（`getPrimaryStorageCandidatesForVolumeMigration`）

当前实现对 Data 盘**不做 cluster 兼容性校验**（只对 Root 盘做 vmNic L2 匹配）。挂载态数据盘需要：

- 复用 Root 盘的 L2 cluster 匹配逻辑（数据盘自身没有 nic，但**所属 VM 有**）
- 或在 API 拦截阶段补一次 dstPS cluster 校验

### 3.5 并发与一致性（重要：startVm 不阻断 Migrating 卷）

VM Stopped 状态下：

- QEMU 进程不存在，无 fd / librbd watcher 持有源卷
- libvirt domain XML 不会被运行时引用
- SharedBlock 担心的"LV 在线变更"在 Ceph/NFS 不存在等价问题

#### 3.5.1 同步链分析：数据盘迁移**不**锁 VM 操作

ZStack 的 `thdf.chainSubmit` 按 `(serviceId + resourceUuid)` 串行化。各迁移路径的实际归宿：

| 路径 | 进哪个 service 同步链 | sync signature | 来源 |
|---|---|---|---|
| **数据盘迁移**（含 SB 数据盘） | `VolumeConstant.SERVICE_ID` + volumeUuid → VolumeBase | `syncThreadId`（volumeUuid 级） | `StorageMigrationBase.java:1040-1044` 注释 "queue on source data volume" |
| **Root 盘迁移** | `VmInstanceConstant.SERVICE_ID` + vmUuid → VmInstanceBase | VM 的 `syncThreadName`（vmUuid 级） | `StorageMigrationBase.java:967-971` `MigrateRootVolumeOverlayMsg` |
| **VM 启动/停止/重启** | `VmInstanceConstant.SERVICE_ID` + vmUuid → VmInstanceBase | VM 的 `syncThreadName`（vmUuid 级） | `VmInstanceBase.java:8031` |

**关键事实**：数据盘迁移与 VM 操作在 thdf 中是**完全独立的两个 SyncThread 队列**，互不阻塞。Root 盘迁移天然不会与并发 startVm 撞车，是因为它共用 VM 同步链——**这一保护对数据盘不成立**。

#### 3.5.2 startVm 也不做卷状态校验

| 检查点 | 结论 |
|---|---|
| `VmInstanceApiInterceptor.validate(APIStartVmInstanceMsg)` (line 559) | 只设 cluster/host，**不检查关联卷状态** |
| `VmInstanceBase.startVm` (line 7563) | 只检查 VM state，不检查 `VolumeStatus` |
| `AbstractVolume.forbiddenOperations[Migrating]` | 仅禁止 Template/Backup/Snapshot/Delete，**不含 VM 启动消息** |

#### 3.5.3 灾难场景

```
T0: VM Stopped, 数据盘 Status=Ready, installPath=ceph-A
T1: 用户发起 Migrate → 进 volume sync 链；setStatus(Migrating)
T2: 用户并发发 startVm → 进 vm sync 链（不同队列！没人拦）
    VM 用 installPath=ceph-A 起 qemu
T3: CephToCephMigrateVolumeFlow 完成 export-diff/import-diff
T4: UpdateVolumeVOFlow 改 installPath → ceph-B
T5: CephDeleteVolumeFromSrcPSFlow 删 ceph-A 上的卷
T6: VM qemu 句柄崩溃，数据写入黑洞
```

#### 3.5.4 SB 既有缺陷（独立问题，本期顺修）

**该并发漏洞在现有 SharedBlock 数据盘迁移路径上同样存在**——SB 数据盘走的也是 `MigrateDataVolumeOverlayMsg` → VolumeBase 这条同一代码路径，没有任何 VM 级同步保护。

生产没爆雷的原因推测：

1. SB 客户体量较小
2. LV path 变更秒级完成，撞车窗口短
3. UI 在 Stopped + 卷迁移中可能 disable 启动按钮
4. HA 自动拉起若命中此窗口，故障易被归因为"存储抖动"

**结论**：建议为此独立立 Jira 单（如 `ZSTAC-XXXXX: SB StartVm during data volume migration race`）追溯归属，但**实际修复合并到本期**，统一在 §4.4 完成。

#### 3.5.5 同 VM 多卷并发迁移

不同 volumeUuid 的 sync signature 不同，因此**同一 VM 上的多块数据盘可以并行进入迁移**。Stopped VM 下无害（不读写卷），但 §4.4 的 startVm 检查使用 "any attached volume status == Migrating" 的批量校验，自然覆盖此场景。

### 3.6 失败回滚

现有 Flow 已带 rollback（`CephToCephMigrateVolumeFlow.rollback` 删除目标 PS 上半成品的卷）。`UpdateVolumeVOFlow` 失败时 VolumeVO 状态机会回到 Migrating 之前。

新增挂载态支持后，由于不修改 `vmInstanceUuid`，回滚无新增风险。**唯一新增的回滚需求**：如果迁移失败，新 installPath 写回失败，需保证 VolumeVO 仍指向源 PS 的旧路径——这是现有 `UpdateVolumeVOFlow` 已有的行为。

### 3.7 风险评估

| 风险 | 描述 | 缓解 |
|---|---|---|
| dstPS 未挂到 VM cluster | 迁移成功但 VM 永远起不来 | 拦截器补 cluster 兼容性校验 |
| 迁移过程用户 attach/detach | 并发改 vmInstanceUuid | 现有 `MigrateDataVolumeOverlayMsg` 已在 volume sync 锁内串行化；attach/detach 也走同一锁 |
| **迁移过程用户启动 VM** | VolumeVO=Migrating，但 startVm 未阻断（见 3.5）；数据盘 sync 链与 VM sync 链是不同队列，互不串行 | **必须**在 `APIStartVmInstanceMsg` 拦截层补卷状态校验 |
| Shareable 卷 | 共享卷可能挂多个 VM | 现行已禁止 Shareable 卷迁移，保留此约束 |
| 系统盘 image cache | Data 盘无关 | 不涉及 |

### 3.8 兼容性

- 现有 detach → migrate → attach 流程**不受影响**（依然有效）
- 新增的"挂载态 + Stopped"路径是**新增能力**，无 API 行为破坏
- SDK / 前端不需要改动签名

**可行性结论**：✅ **技术上可行**。工作量集中在拦截器、候选 PS 计算与 startVm 并发防护。

## 四、设计方案

### 4.1 修改点清单

| # | 文件 | 改动 |
|---|---|---|
| 1 | `StorageMigrationApiInterceptor.validate(APIPrimaryStorageMigrateVolumeMsg)` | 数据盘分支统一改为「VM 必须 Stopped」，去除 SharedBlock 与非 SharedBlock 的差异 |
| 2 | `StorageMigrationApiInterceptor`（同方法内） | 挂载态数据盘新增 dstPS 与 VM cluster 兼容性校验（参考 Root 盘的 vmNic L2 匹配逻辑） |
| 3 | `StorageMigrationBase.getPrimaryStorageCandidatesForVolumeMigration` | Data 盘候选 PS 计算补一段：若卷挂载在 VM 上，复用 VM vmNic L2 匹配过滤 |
| 4 | `VmInstanceApiInterceptor.validate(APIStartVmInstanceMsg)` | **新增**：拒绝启动有 `VolumeStatus=Migrating` 数据盘的 VM（修补现有 SB 路径同样存在的并发漏洞） |
| 5 | 文档 | 更新 [primary-storage-migrate-volume.md](primary-storage-migrate-volume.md) 对比表与"为什么强制 detach"章节 |

### 4.2 拦截器改造（核心）

`StorageMigrationApiInterceptor.java:321-342` 数据盘分支改造方案：

```java
} else if (srcVolume.getType() == VolumeType.Data) {
    if (srcVolume.getVmInstanceUuid() != null) {
        VmInstanceVO vm = dbf.findByUuid(srcVolume.getVmInstanceUuid(), VmInstanceVO.class);
        // 1. 统一要求 VM Stopped（原仅 SharedBlock 路径有此校验）
        if (vm.getState() != VmInstanceState.Stopped) {
            throw new ApiMessageInterceptionException(Platform.argerr(
                "cannot migrate data volume[uuid:%s] when vm[uuid:%s] is not stopped.",
                msg.getVolumeUuid(), srcVolume.getVmInstanceUuid()
            ));
        }

        // 2. 校验 dstPS 与 VM 的 cluster/L2 兼容性
        //    （SharedBlock 在候选阶段已通过 NotSupportCrossClusterMigrationMetrics 保证，
        //     Ceph/NFS 跨 cluster 无需共享，但仍需保证 dstPS 挂到了某个能跑 VM 的 cluster）
        checkDstPsClusterCompatibility(vm, dstPS);
    }

    if (Q.New(ShareableVolumeVmInstanceRefVO.class)
            .eq(ShareableVolumeVmInstanceRefVO_.volumeUuid, srcVolume.getUuid()).isExists()) {
        throw new ApiMessageInterceptionException(Platform.argerr(
            "do not support storage migration while shared volume[uuid:%s] attached",
            srcVolume.getUuid()
        ));
    }
}
```

`checkDstPsClusterCompatibility` 复用 Root 盘的 vmNic L2 匹配逻辑（提取为辅助方法）：

```java
private void checkDstPsClusterCompatibility(VmInstanceVO vm, PrimaryStorageVO dstPS) {
    for (VmNicVO vmNic : vm.getVmNics()) {
        boolean match = false;
        L3NetworkVO l3 = dbf.findByUuid(vmNic.getL3NetworkUuid(), L3NetworkVO.class);
        L2NetworkVO l2 = dbf.findByUuid(l3.getL2NetworkUuid(), L2NetworkVO.class);
        for (PrimaryStorageClusterRefVO pcRef : dstPS.getAttachedClusterRefs()) {
            match = Q.New(L2NetworkClusterRefVO.class)
                    .eq(L2NetworkClusterRefVO_.l2NetworkUuid, l2.getUuid())
                    .eq(L2NetworkClusterRefVO_.clusterUuid, pcRef.getClusterUuid())
                    .isExists();
            if (match) break;
        }
        if (!match) {
            throw new ApiMessageInterceptionException(Platform.argerr(
                "destination primary storage is not attached to any cluster matching the VM's L2 networks"
            ));
        }
    }
}
```

### 4.3 候选 PS 计算改造

`StorageMigrationBase.getPrimaryStorageCandidatesForVolumeMigration` 当前对 Data 盘 `if (srcVolume.getType() == VolumeType.Data) return candidates;` 直接返回。修改为：

```java
if (srcVolume.getType() == VolumeType.Data) {
    // 新增：挂载态数据盘需匹配 VM 的 cluster/L2
    if (srcVolume.getVmInstanceUuid() != null) {
        VmInstanceVO srcVm = dbf.findByUuid(srcVolume.getVmInstanceUuid(), VmInstanceVO.class);
        if (srcVm != null && srcVm.getVmNics() != null && !srcVm.getVmNics().isEmpty()) {
            // 沿用下方 Root 盘的 vmNic 匹配逻辑
            candidates = filterCandidatesByVmNics(candidates, srcVm);
        }
    }
    return PrimaryStorageInventory.valueOf(candidates);
}
```

将原 Root 盘段落中的 vmNic 过滤逻辑提取为 `filterCandidatesByVmNics(candidates, vm)` 共用。

### 4.4 startVm 并发防护（必修）

`VmInstanceApiInterceptor.validate(APIStartVmInstanceMsg)` 当前只做 cluster/host 校验，需新增：

```java
private void validate(APIStartVmInstanceMsg msg) {
    // 既有逻辑：host uuid overrides cluster uuid
    if (msg.getHostUuid() != null) {
        msg.setClusterUuid(null);
    }

    // 新增：禁止启动 VM 若任一挂载卷处于迁移中
    List<String> migratingVols = Q.New(VolumeVO.class)
        .select(VolumeVO_.uuid)
        .eq(VolumeVO_.vmInstanceUuid, msg.getVmInstanceUuid())
        .eq(VolumeVO_.status, VolumeStatus.Migrating)
        .listValues();
    if (!migratingVols.isEmpty()) {
        throw new ApiMessageInterceptionException(operr(
            "cannot start VM[uuid:%s], %d attached volume(s) are migrating: %s",
            msg.getVmInstanceUuid(), migratingVols.size(), migratingVols));
    }
}
```

同时需补 `StartVmInstanceMsg`、`HaStartVmInstanceMsg` 内部消息分支（在 `VmInstanceBase.startVm` 早期 refreshVO 之后做相同检查），覆盖 API 之外的入口。

### 4.5 FlowChain 不动

`CephToCephMigrateVolumeFlow`、`NfsToNfsMigrateVolumeFlow`、`UpdateVolumeVOFlow` 等**不需要修改**。Flow 已不依赖卷的挂载状态。

### 4.6 测试用例（Groovy 集成测试位置参考）

`premium/test-premium/src/test/groovy/org/zstack/test/integration/premium/storage/migration/`

新增 case：

1. `CephToCephAttachedDataVolumeMigrationCase` — VM Stopped + 数据盘挂载，迁移成功，启动 VM 后能读旧数据
2. `NfsToNfsAttachedDataVolumeMigrationCase` — 同上
3. `RunningVmRejectedCase` — VM Running 时拒绝迁移
4. `DstPsClusterMismatchRejectedCase` — 目标 PS 未挂到 VM cluster 时 API 拦截
5. `StartVmDuringMigrationRejectedCase` — 迁移中并发 startVm 必被拒绝

参考现有 `NfsToNfsMigrateDataVolumeCase.groovy` 的脚手架。

## 五、回滚方案

| 场景 | 回滚 |
|---|---|
| 拦截器允许后迁移失败 | 复用现有 `UpdateVolumeVOFlow` rollback + `CephToCephMigrateVolumeFlow.rollback` 删除目标半成品 |
| 上线后发现问题 | GlobalConfig 增加开关 `mevoco.storageMigration.relaxAttachedDataVolume`（默认 false），允许灰度 |
| 老用户期望"挂着即拒绝" | 保留 detach → migrate → attach 路径不变，新功能为额外能力，不破坏旧行为 |

### 5.1 GlobalConfig（建议）

```xml
<globalConfig>
    <name>relaxAttachedDataVolume</name>
    <category>mevoco.storageMigration</category>
    <description>Allow data volume migration while attached to a stopped VM</description>
    <defaultValue>false</defaultValue>
    <type>boolean</type>
</globalConfig>
```

拦截器读取该 config，默认关闭新行为，老用户无感知。运维侧打开后启用新逻辑。

`startVm` 的并发防护（4.4）**不挂在此开关下**，无条件生效（因为它修补的是 SB 路径既有缺陷）。

## 六、工作量估算

| 项 | 估算 |
|---|---|
| 拦截器改造（含辅助方法提取） | 0.5 天 |
| 候选 PS 计算改造 | 0.5 天 |
| GlobalConfig + 灰度开关 | 0.3 天 |
| startVm 并发防护 + 单测 | 0.5 天 |
| Groovy 集成测试 5 个 | 2 天 |
| 文档更新 | 0.2 天 |
| **合计** | **~4 天** |

## 七、验收标准

1. ✅ Ceph→Ceph 数据盘在挂载 + VM Stopped 状态下能成功迁移，启动 VM 后磁盘内容一致
2. ✅ NFS→NFS 同上
3. ✅ Running/Paused VM 上的数据盘迁移被拒绝
4. ✅ dstPS 未挂到 VM cluster 时被 API 层拒绝
5. ✅ 迁移过程中 `APIStartVmInstanceMsg` 被拒绝
6. ✅ Shareable 卷继续拒绝
7. ✅ SharedBlock 现有行为不变（含新增的 startVm 并发防护）
8. ✅ 已 detach 卷（`vmInstanceUuid == null`）迁移路径行为不变
9. ✅ GlobalConfig 关闭时退化为旧行为（startVm 防护除外）

## 八、开放问题

1. **GlobalConfig 默认值** — 是否首版默认 true？建议首版 false，下个 LTS 转 true
2. **是否同步扩展 `APIPrimaryStorageMigrateVmMsg withDataVolumes`**? 当前 VM 整机迁移已支持数据盘，本期不动
3. **startVm 防护是否需要适配 4.4 之外的入口**（如 HA 自动拉起 `HaStartVmInstanceMsg`、scheduler `StartVmInstanceJob`）— 建议覆盖到 `VmInstanceBase.startVm` 公共入口
4. **SB 既有并发漏洞的 Jira 单归属** — 建议单独立单（用于追溯与回归用例归类），但代码修复合并到本期 MR

## 九、参考

- 现有逻辑分析：[primary-storage-migrate-volume.md](primary-storage-migrate-volume.md)
- `StorageMigrationApiInterceptor.java:245-372`
- `StorageMigrationBase.java:893,1624`
- `CephToCephMigrateVolumeFlow.java`
- `VmInstanceApiInterceptor.java:559` (startVm 校验缺失点)
- `VmInstanceBase.java:7563` (startVm 主体)
- `AbstractVolume.java:49,72` (Migrating 状态机)
- `premium/conf/springConfigXml/storageMigration.xml`
