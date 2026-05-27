# ZSPHER-244 API 设计权衡：扩展现有 API vs 新增 API

## 方案速览（TL 确认）

- 工单：ZSPHER-244 冷迁多盘各异目标
- 前置：ZSV-12280 热迁多盘已落
- 痛点：老 API 已叠两层互斥语义
- A：老 API 加 specs 字段补丁
- B：新增独立 API（推荐）
- 理由：语义干净 校验解耦 灰度可控
- 参考：冷迁 vs 热迁 VM 早分两 API
- 待确认：仅冷迁 字段命名 上线节奏

---

# ZSPHER-244 API 设计权衡：扩展现有 API vs 新增 API

> 关联工单：[ZSPHER-244](http://jira.zstack.io/browse/ZSPHER-244) — 更改数据存储支持数据盘指定不同目标
> 关联前置：[ZSV-12280](http://jira.zstack.io/browse/ZSV-12280) — SharedBlock 多数据盘热迁移
> 状态：设计阶段
> 决策建议：方案 B（新增独立 API）

---

## 1. 背景

`APIPrimaryStorageMigrateVolumeMsg` 当前承担的语义已经叠了两层：

| 演进阶段 | 字段 | 适用场景 |
|---------|------|---------|
| 最早 | `volumeUuid`(path) + `dstPrimaryStorageUuid` | 单盘冷迁移（VM Stopped 或未挂载） |
| ZSV-12280（已落） | `volumeUuids: List<String>` + `dstPrimaryStorageUuid` | 多盘热迁移（VM Running/Paused，**统一目标 PS**） |
| ZSPHER-244（待做） | 每盘独立目标：`List<{volumeUuid, dstPsUuid}>` | 多盘**冷迁移**（VM Stopped，**每盘不同目标**） |

三种语义共用一个 API 已经出现"互斥字段"问题（`volumeUuid` 与 `volumeUuids` 必须二选一/包含关系）。ZSPHER-244 是否继续叠加，需要明确决策。

---

## 2. 方案 A：继续给 `APIPrimaryStorageMigrateVolumeMsg` 加参数

新增字段 `volumeMigrationSpecs: List<VolumeMigrationSpec{volumeUuid, dstPrimaryStorageUuid}>`。

### 优点
- 前端入口统一（一个 API 处理 volume 迁移所有场景）
- SDK 兼容：旧字段保留
- LongJob 调度复用现有 `PrimaryStorageMigrateVolumeJob`

### 缺点（多数已经显现）
1. **互斥字段爆炸**：`volumeUuid` / `volumeUuids` / `volumeMigrationSpecs` / `dstPrimaryStorageUuid` 之间需要 5+ 条互斥规则，Interceptor 校验逻辑将剧增。
2. **语义模糊**：同一 API 既是热迁移又是冷迁移，既支持单目标又支持多目标——文档和 SDK 注释难写。
3. **路径占位符 `volumeUuid` 越来越尴尬**：当请求是「按盘指定目标」时，path 上的 volumeUuid 是哪一块？需要继续打补丁说"必须是 specs 中的某一块"。
4. **审计/事件 inventory 形状不一致**：单目标返回 `VolumeInventory`，多目标返回 `List<VolumeInventory>`。老调用方期望单个，新调用方期望列表，已经在用 `inventories.get(0)` 凑活。
5. **MN 升级风险**：当前 `PrimaryStorageMigrateVolumeJob.start` 用 `JSONObjectUtil.toObject` 反序列化 jobData，加字段虽兼容但分支判断越来越复杂。

---

## 3. 方案 B：新增独立 API `APIPrimaryStorageMigrateDataVolumesMsg`

专门承载 ZSPHER-244 语义：

```java
@Action(category = VolumeConstant.ACTIONS)
public class APIPrimaryStorageMigrateDataVolumesMsg extends APIMessage {
    @APIParam(resourceType = VmInstanceVO.class)
    private String vmInstanceUuid;

    @APIParam(nonempty = true)
    private List<VolumeMigrationSpec> volumeMigrationSpecs;
    // VolumeMigrationSpec: volumeUuid + dstPrimaryStorageUuid (+ optional withSnapshots)
}
```

### 优点
1. **语义干净**：一个 API 一种语义，文档/SDK/审计天然清晰。
2. **Interceptor 简单**：只校验自己的字段，与老 API 完全解耦。
3. **入口分流**：冷迁移多目标走新 Msg → 新 Job（`PrimaryStorageMigrateDataVolumesJob`）；老 API 不动，回归"经典单盘迁移"角色。
4. **Inventory 形状自然**：新事件直接返回 `List<VolumeInventory>`，不用兼容老形状。
5. **演进空间**：未来 ZSPHER-244 的衍生需求（按盘选 withSnapshots、按盘选 strategy 等）只在新 Msg 上扩展，不污染老路径。
6. **可独立灰度**：新 API 出问题不影响存量。

### 缺点
1. 前端要多一个 API 调用入口（但 UI 本来就是新的高级选项面板，天然走新接口）。
2. SDK 需要重新生成（项目里本来就是必经流程）。
3. 多了一个 LongJob 类（但本来就需要新逻辑，写在哪里都一样）。

---

## 4. 同类先例参考

ZStack 自身的 API 演进经验：

- `APIMigrateVmMsg`（冷）/ `APILiveMigrateVmMsg`（热）—— **是分开的两个 API**，没有共用 `migrateVm` 加 `live: boolean`。
- `APIChangeVmHaPolicyMsg` / `APISetVmInstanceDefaultCdRomMsg` 这类语义独立的操作，从不强行复用旧 API。
- 当年 `volumeUuids` 加到 `APIPrimaryStorageMigrateVolumeMsg` 已经是历史包袱（即 ZSV-12280 当前进行中的工作），现在如果再叠一层 multi-target，会让这个 API 彻底变成"什么都能干的 god API"。

---

## 5. 推荐：方案 B（新增独立 API）

### 关键理由
1. ZSPHER-244 是 **冷迁移 + 每盘独立目标**，与 ZSV-12280 的 **热迁移 + 统一目标** 实际是两条完全独立的执行链路：
    - 冷迁移：`MigrateVolumeOnPrimaryStorageMsg` fan-out（每盘可走不同目标 PS）
    - 热迁移：KVM live storage migration workflow（per-volume LUN switch）

   共用 API 只是表面省事，后端早晚要按字段分流。

2. **Interceptor 复杂度上限**：现在 `validateLiveMigrateMultiDataVolumes` 已经接近 80 行，再叠一种语义会到 150+ 行，可维护性崩盘。

3. **老 API 已经背了一个互斥规则补丁**（path `volumeUuid` 必须 ∈ body `volumeUuids`），再加规则会让前端联调和文档维护痛苦。

### 建议落地形态

- 新增 `APIPrimaryStorageMigrateDataVolumesMsg` + `APIPrimaryStorageMigrateDataVolumesEvent`
- 新增 `PrimaryStorageMigrateDataVolumesJob`（LongJob），内部串行/并行调用现有 `MigrateVolumeOnPrimaryStorageMsg`（每盘一个目标 PS）
- Interceptor 单独写 `validate(APIPrimaryStorageMigrateDataVolumesMsg)`，与 multi-volume 热迁移校验完全分离
- 老 `APIPrimaryStorageMigrateVolumeMsg.volumeUuids` 路径继续仅服务 ZSV-12280 热迁移场景，不改动

### 边界情况
- 如果 PD 要求"按盘选目标"也能用于**热迁移**，再单独评估是否要在新 API 上加 `live: boolean`，或者再开一个 `APILiveMigrateDataVolumesToDifferentTargets`。
- 但 ZSPHER-244 原文明确写「SAN ↔ SAN **冷迁移**」，目前不必预留。

---

## 6. 决策待办

- [ ] 与 PD 确认 ZSPHER-244 是否仅冷迁移场景
- [ ] 与前端确认是否接受新 API（UI 本身就是新面板，预计无阻力）
- [ ] 与架构组评审字段命名 `volumeMigrationSpecs` vs `volumeTargets`
- [ ] 确认审计 inventory 形状（List 返回是否需要追加 `failedVolumes` 字段）
