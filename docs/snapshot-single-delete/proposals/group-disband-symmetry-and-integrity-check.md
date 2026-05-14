# 快照组解散对称化 + VM 级完整性拦截（A+C 组合方案）

> 范围：`VolumeSnapshotTreeBase.ungroupAfter*`、`VolumeSnapshotGroupBase`、`VolumeSnapshotGroupChecker`、VM 删除 cascade、Attach/Detach 卷
> 关联 Bug：bugs.md 中 **Bug 11 / Bug 12 / Bug 13**（待登记）
> 基线：5.5.6
> 状态：提案（未实施）
> 决策点已确认：拦截 = **VM 级**；VM destroy 时 incomplete = **cascade 自动清理**；force = **API 字段**

---

## 1. 背景

`VolumeSnapshotGroupVO` 表示"VM 上多盘一致性快照集"，每盘一条 `VolumeSnapshotGroupRefVO`。当前删除快照时存在两条不对称的解散路径：

| 路径 | 入口 | 触发条件 | 解散行为 |
|---|---|---|---|
| `ungroupAfterDeleteSingleSnapshot`（行 1427-1443） | scope=single 删单快照 | 该快照属于某 group | 仅 `ref.snapshotDeleted=true`；**所有 ref 都 deleted 才删 group VO** |
| `ungroupAfterDeleted`（行 2148-2169） | scope=chain 删子树 | 待删 snapshot 的根 volume 是 **Root** | **立即删除整个 group VO**，data 盘 ref 变孤儿 |

后果：
- root 盘单删 chain → group VO 消失，data 盘 ref 还指向已不存在的 group → 残留孤儿
- data 盘单删 chain → group VO 仍在，ref.snapshotDeleted=true → 组 incomplete
- 后续对该 VM 删组 / 建组 / 删 VM / 挂卸盘 → 没有任何拦截，所有操作"看起来正常"实际带病前进

本提案双管齐下：
- **A**：解散逻辑统一对称（消除孤儿源头）
- **C**：VM 级完整性拦截（让残留 incomplete 组成为后续操作的硬阻断点）

---

## 2. 方案 A — 解散对称化

### 2.1 改动

`VolumeSnapshotTreeBase.ungroupAfterDeleted` 行 2148-2169 移除 `Root` 特例：

```java
private void ungroupAfterDeleted(List<VolumeSnapshotInventory> snapshots) {
    List<String> uuids = snapshots.stream()
            .map(VolumeSnapshotInventory::getUuid).collect(Collectors.toList());

    SQL.New(VolumeSnapshotGroupRefVO.class)
       .in(VolumeSnapshotGroupRefVO_.volumeSnapshotUuid, uuids)
       .set(VolumeSnapshotGroupRefVO_.snapshotDeleted, true).update();

    // 不再区分 root / data，统一查"全 ref deleted 才解散整组"
    Set<String> groupUuids = Q.New(VolumeSnapshotGroupRefVO.class)
            .select(VolumeSnapshotGroupRefVO_.volumeSnapshotGroupUuid)
            .in(VolumeSnapshotGroupRefVO_.volumeSnapshotUuid, uuids)
            .listValues().stream().map(Object::toString).collect(Collectors.toSet());

    for (String groupUuid : groupUuids) {
        long remaining = Q.New(VolumeSnapshotGroupRefVO.class)
                .eq(VolumeSnapshotGroupRefVO_.volumeSnapshotGroupUuid, groupUuid)
                .eq(VolumeSnapshotGroupRefVO_.snapshotDeleted, false).count();
        if (remaining == 0) {
            vidm.deleteArchiveVmInstanceResourceMetadataGroup(groupUuid);
            cleanVmHostBackupFilesForGroup(Collections.singletonList(groupUuid));
            dbf.removeByPrimaryKey(groupUuid, VolumeSnapshotGroupVO.class);
        }
    }
}
```

### 2.2 收益

- root 盘 chain 删除不再立即删 group VO，与 data 盘行为对齐
- 不再产生"group 已不存在 / ref 仍在"的孤儿
- `ungroupAfterDeleteSingleSnapshot` 与 `ungroupAfterDeleted` 行为合并，可后续重构为同一私有方法

### 2.3 兼容性

- 旧 root 单删 chain 后立即解散的"快"行为消失：仍要等 data 盘 ref 也清理才解散
- 实际上历史路径就是 bug —— 旧行为留下孤儿 ref，新行为留下 incomplete 组（被 C 拦截后用户必须清理）

---

## 3. 方案 C — VM 级完整性拦截

### 3.1 拦截入口

| 入口 API | 拦截条件 | 错误信息 | force 字段 |
|---|---|---|---|
| `APIDeleteVolumeSnapshotGroupMsg`（其他组） | VM 上有 incomplete 组（exclude 自身） | `VM[uuid=%s] 存在不完整快照组%s，请先清理后再删除其他快照组` | ✅ |
| `APICreateVolumeSnapshotGroupMsg` | VM 上有 incomplete 组 | `VM[uuid=%s] 存在不完整快照组%s，请先清理后再创建新快照组` | ❌（不应允许） |
| `APIAttachDataVolumeToVmMsg` | VM 上有 incomplete 组 | `VM[uuid=%s] 存在不完整快照组%s，请先清理后再挂载磁盘` | ❌ |
| `APIDetachDataVolumeFromVmMsg` | VM 上有 incomplete 组 | `VM[uuid=%s] 存在不完整快照组%s，请先清理后再卸载磁盘` | ❌ |
| `APIDestroyVmInstanceMsg` | VM 上有 incomplete 组 | **不拦截**（cascade 自动清理） | ❌ |

**豁免**：
- 删 incomplete 组**自身** → 放行（exclude 当前 group_uuid）
- 单快照 API（`APIDeleteVolumeSnapshotMsg`） → 放行（清债途径）

### 3.2 incomplete 检测

在 `VolumeSnapshotGroupChecker` 新增静态方法：

```java
public class VolumeSnapshotGroupChecker {
    /**
     * 返回 VM 上所有 incomplete 组（部分 ref 已 snapshotDeleted=true 但仍存在未删的 ref）。
     * @param excludeGroupUuid 排除指定 group（如删自身时不算违例），null 表示不排除
     */
    public static List<String> findIncompleteGroupsOnVm(String vmUuid, String excludeGroupUuid) {
        List<String> groupUuids = Q.New(VolumeSnapshotGroupVO.class)
                .select(VolumeSnapshotGroupVO_.uuid)
                .eq(VolumeSnapshotGroupVO_.vmInstanceUuid, vmUuid)
                .listValues();

        List<String> incomplete = new ArrayList<>();
        for (Object o : groupUuids) {
            String guuid = o.toString();
            if (guuid.equals(excludeGroupUuid)) continue;
            long deletedRefs = Q.New(VolumeSnapshotGroupRefVO.class)
                    .eq(VolumeSnapshotGroupRefVO_.volumeSnapshotGroupUuid, guuid)
                    .eq(VolumeSnapshotGroupRefVO_.snapshotDeleted, true).count();
            long totalRefs = Q.New(VolumeSnapshotGroupRefVO.class)
                    .eq(VolumeSnapshotGroupRefVO_.volumeSnapshotGroupUuid, guuid).count();
            if (deletedRefs > 0 && deletedRefs < totalRefs) {
                incomplete.add(guuid);
            }
        }
        return incomplete;
    }
}
```

### 3.3 拦截织入示例

#### 3.3.1 删除其他组

```java
// VolumeSnapshotGroupBase.handle(APIDeleteVolumeSnapshotGroupMsg)
private void handle(APIDeleteVolumeSnapshotGroupMsg msg) {
    APIDeleteVolumeSnapshotGroupEvent evt = new APIDeleteVolumeSnapshotGroupEvent(msg.getId());
    String vmUuid = self.getVmInstanceUuid();
    if (!msg.isForce()) {
        List<String> incomplete = VolumeSnapshotGroupChecker
                .findIncompleteGroupsOnVm(vmUuid, self.getUuid());
        if (!incomplete.isEmpty()) {
            evt.setError(operr("VM[uuid=%s] 存在不完整快照组%s，请先清理后再删除其他快照组",
                    vmUuid, incomplete));
            bus.publish(evt);
            return;
        }
    }
    // ... 原逻辑
}
```

#### 3.3.2 创建新组 / 挂卸盘

各 API handle 入口：

```java
List<String> incomplete = VolumeSnapshotGroupChecker.findIncompleteGroupsOnVm(vmUuid, null);
if (!incomplete.isEmpty()) {
    bus.replyErrorByMessageType(msg, operr("VM[uuid=%s] 存在不完整快照组%s，请先清理后再 ...",
            vmUuid, incomplete));
    return;
}
```

#### 3.3.3 VM destroy — cascade 自动清理（不拦截）

`VolumeSnapshotGroupCascadeExtension`：

```java
@Override
public void asyncCascade(CascadeAction action, Completion completion) {
    if (CascadeConstant.DELETION_CHECK_CODE.equals(action.getActionCode())) {
        // VM destroy 不拦截 incomplete 组，由后续 cleanup 阶段处理
        completion.success();
        return;
    }

    if (CascadeConstant.DELETION_CLEANUP_CODE.equals(action.getActionCode())) {
        String vmUuid = ((VmInstanceInventory) action.getParentIssuer().get(0)).getUuid();
        List<String> incomplete = VolumeSnapshotGroupChecker
                .findIncompleteGroupsOnVm(vmUuid, null);
        if (!incomplete.isEmpty()) {
            // force 删除所有 incomplete 组（包括其残留 ref）
            forceDeleteGroups(incomplete, completion);
            return;
        }
        completion.success();
    }
}
```

`forceDeleteGroups`：直接 SQLBatch 删 `VolumeSnapshotGroupRefVO` + `VolumeSnapshotGroupVO`，然后调 `vidm.deleteArchiveVmInstanceResourceMetadataGroup` + `cleanVmHostBackupFilesForGroup`。**不再走 chain 删快照** —— VM 销毁时 volume 也会被销毁，对应 snapshot tree 通过各 PS cascade 清理。

### 3.4 force 字段（仅 API 层）

```java
// APIDeleteVolumeSnapshotGroupMsg.java
@APIParam(required = false,
          description = "true = 跳过 VM 完整性检查（运维兜底）；默认 false")
private boolean force = false;
```

仅 `APIDeleteVolumeSnapshotGroupMsg` 加 `force`。其他 API（建组/挂卸盘）不应允许带病前进，不开 force。

---

## 4. 用户清债的两条路径

| 场景 | 操作 | 结果 |
|---|---|---|
| 整组清理 | `APIDeleteVolumeSnapshotGroupMsg(group_uuid=incomplete)` | 走 chain 删剩余快照 → A 解散逻辑收尾 → group VO 删除 |
| 个体清理 | 对每个残留 ref 对应的 snapshot 调 `APIDeleteVolumeSnapshotMsg` | 同上路径触发 A 解散收尾 |
| 紧急绕过 | `APIDeleteVolumeSnapshotGroupMsg(group_uuid=other, force=true)` | 跳过完整性检查删其他组（incomplete 组留待事后处理） |

---

## 5. 行为矩阵

| T0 状态 | T1 操作 | T1 结果 | T2 操作 | T2 结果 |
|---|---|---|---|---|
| 组1 完整（root + data 各一） | 删组1 root 单快照 (single) | 组1 ref 一个 deleted；**组1 VO 保留** | 删组2 | C 拦截 |
| 同上 | 同上 | 同上 | 删组1（自身） | 放行（exclude） |
| 同上 | 同上 | 同上 | 删组1 data ref 对应 snapshot | 放行 → 触发 A 解散 |
| 组1 完整 | 删组1 整组 (chain) | 全 ref deleted → 组1 VO 删 | 删组2 | 放行 |
| 组1 incomplete | 升级 management 重启 | 状态持久 | 删 VM | **放行**（cascade 自动清 incomplete） |
| 组1 incomplete | — | — | 删组2 force=true | 放行（带病删，组1 仍在） |
| 组1 incomplete | — | — | 建新组 / 挂盘 / 卸盘 | C 拦截，无 force 兜底 |

---

## 6. 改动清单

| # | 文件 | 改动 |
|---|---|---|
| 1 | `storage/.../VolumeSnapshotTreeBase.java` 行 2148-2169 | 移除 root 特例，统一"全 ref deleted 才解散" |
| 2 | `storage/.../group/VolumeSnapshotGroupChecker.java` | 新增 `findIncompleteGroupsOnVm(vmUuid, excludeGroupUuid)` |
| 3 | `storage/.../group/VolumeSnapshotGroupBase.java handle(APIDeleteVolumeSnapshotGroupMsg)` | 入口加 incomplete 检查 + force 旁路 |
| 4 | `storage/.../VolumeSnapshotManagerImpl.java handle(APICreateVolumeSnapshotGroupMsg)` | 入口加 incomplete 检查 |
| 5 | VM Attach/Detach DataVolume API handle | 入口加 incomplete 检查 |
| 6 | `storage/.../group/VolumeSnapshotGroupCascadeExtension.java` | DELETION_CLEANUP 阶段 force 清 incomplete 组（VM destroy 路径） |
| 7 | `header/.../group/APIDeleteVolumeSnapshotGroupMsg.java` | 新增 `boolean force = false` |
| 8 | i18n 错误码表 | 新增 `GROUP_INCOMPLETE_BLOCK_*` 系列 |
| 9 | API 文档 / changelog / 升级公告 | 提示历史 incomplete 组将首次拦截，提供清债指引 |

---

## 7. 兼容矩阵

| 场景 | 旧行为 | 新行为 | 兼容 |
|---|---|---|---|
| root chain 删除 | 立即删 group VO，留 data ref 孤儿 | 仅 mark deleted；等 data ref 齐删 | ⚠ break（更合理） |
| data chain 删除 | mark deleted，等齐 | 同（不变） | ✅ |
| single 删除 | mark deleted，等齐 | 同（不变） | ✅ |
| 升级前已存在的 incomplete 组 | 后续操作无任何提示 | 首次触发拦截 | ⚠ break（运维需清债 / force） |
| 升级前正常组 | 正常 | 正常 | ✅ |
| VM destroy 时存在 incomplete 组 | 走旧 cascade，行为不确定 | cascade 自动 force 清 | ✅ 改善 |

---

## 8. 测试要点

| 场景 | 预期 |
|---|---|
| root chain 单删 → 不立即解散 | group VO 仍在，root ref deleted=true |
| data chain 单删 → 不解散 | 同上 |
| 全部 ref 都删完 → 自动解散 | group VO 消失 + vidm 调用 + backup file 清 |
| 组1 incomplete → 删组2 | argerr/operr，提示组1 incomplete |
| 组1 incomplete → 删组1 自身 | 放行 |
| 组1 incomplete → 删组1 剩余 snapshot（个体 API） | 放行 → A 收尾解散 |
| 组1 incomplete → 建新组 | operr 拦截 |
| 组1 incomplete → attach/detach data volume | operr 拦截 |
| 组1 incomplete → 删 VM | 放行，cascade 自动清 incomplete 组 |
| 删组2 force=true，组1 incomplete | 放行，组1 保留 |
| 升级旧库 → 已存在 incomplete 组 → 任意操作首次触发拦截 | 报错信息可指导清债 |

---

## 9. 与 bugs.md 的对应

| Bug（待登记） | 描述 | 闭环来源 |
|---|---|---|
| Bug 11 | 解散非对称：root 立即删 vs data 等齐 | 方案 A |
| Bug 12 | incomplete 组持续污染后续操作，无任何检测 | 方案 C |
| Bug 13 | `getEffectiveSnapshots` 不过滤 `ref.snapshotDeleted=false` | A 间接缓解 + C 阻断后续触发场景 |

---

## 10. 风险与决策点

| 决策点 | 已确认 | 备注 |
|---|---|---|
| 拦截层级 | **VM 级** | 同 VM 上任一 incomplete 组阻断 VM 上其他组操作 |
| VM destroy 时 incomplete 处理 | **cascade 自动清理** | 不拦截，cleanup 阶段 force 删 |
| force 字段位置 | **API 字段** | 仅 `APIDeleteVolumeSnapshotGroupMsg`，建组/挂卸盘不开 force |
| `findIncompleteGroupsOnVm` 性能 | 待 review | 每次 N+1 查询；如 VM 上组数多可改单 SQL JOIN + GROUP BY HAVING |
| 升级公告 | 必须有 | 升级前需提供 SQL 检测脚本：`SELECT vmInstanceUuid, volumeSnapshotGroupUuid FROM VolumeSnapshotGroupRefVO WHERE snapshotDeleted=1 GROUP BY volumeSnapshotGroupUuid HAVING COUNT(*) < (SELECT COUNT(*) FROM VolumeSnapshotGroupRefVO r2 WHERE r2.volumeSnapshotGroupUuid=...)` |
