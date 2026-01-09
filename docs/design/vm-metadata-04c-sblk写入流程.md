# VM 元数据 — sblk 写入流程

## 1. 设计原则

1. **Phase 1 不破坏现状**：写入的 Header 保留 Active Slot 的完整定位能力（布局字段 = 旧值）
2. **Phase 3 一次性提交**：ActiveSlot 切换、布局更新、PendingOp 清除 + VM 摘要更新在同一次 4KB Header 写入中完成
3. **Slot 自描述**：每个 Slot 内嵌位置信息，即使 Header 损坏也可恢复
4. **崩溃安全模型**：见 Part 4a §5.3

---

## 2. op_type 决策机制

> op_type 由控制面指定，Agent 端直接使用。

管理层面调用 `writeMetadata(payload, storageStructureChange)` 时显式指定 op_type：

| 控制面输入 | Agent 端映射 | PendingOp 值 |
|-----------|-------------|-------------|
| `storageStructureChange = false` | CONFIG_UPDATE | 1 |
| `storageStructureChange = true` | STORAGE_CHANGE | 2 |

**控制面决策规则**：

| 场景 | storageStructureChange | 来源 |
|------|----------------------|------|
| `@MetadataImpact(CONFIG)` API（CPU/内存/标签等） | `false` | 注解 |
| `@MetadataImpact(STORAGE)` API（磁盘挂载/卸载、快照等） | `true` | 注解 |
| Full-refresh / 首次写入 | `true` | 控制面显式指定 |
| 多次 `markDirty` 合并 | OR 升级（任一为 true 则 true） | Poller 批量处理 |

**好处**：
- 控制面对 op_type 拥有完整语义信息（知道哪个 API 触发了变更）
- Agent 无需读取旧 payload 做 diff，减少一次 I/O
- `VmMetadataDirtyVO` 记录 `storageStructureChange` 字段，Poller 批量处理时直接使用

---

## 3. 完整流程

### 3.1 前置步骤

```
target_slot = 1 - Header.ActiveSlot
new_seq     = Header.WriteSequence + 1

如果 payload 超出当前 Slot 容量：
  new_lv_size = calculate_extend_size(current_lv_size, required)
  执行 lvextend（详见 Part 4e §2.4）
  new_layout = calculate_slot_layout(new_lv_size)
否则：
  new_layout = 当前 Header 中的布局（offset + capacity 不变）
```

### 3.2 Phase 1 — Mark Intent (4KB Header 写入)

```
写入 Header（4096B）：

  控制区 [0, 64)：
    Magic          = 0x5A534D54        (不变)
    HeaderVersion  = 当前版本           (不变)
    ActiveSlot     = 旧值              ← 不切换
    PendingOp      = op_type (1 或 2)  ← 标记意图
    WriteSequence  = new_seq           ← 递增
    SlotAOffset    = 旧值              ← 不变
    SlotACapacity  = 旧值              ← 不变
    SlotBOffset    = 旧值              ← 不变
    SlotBCapacity  = 旧值              ← 不变
    LastUpdateTime = 旧值              ← 不变
    SchemaVersion  = 旧值              ← 不变
  ControlChecksum  = SHA-256(bytes[0:64])

  VM 摘要区 [96, 928)：保持旧值不变（Phase 1 不更新摘要）
  SummaryChecksum  = 旧值              ← 不重算
  预留区 [928, 4096)：零填充

关键约束：布局字段（Offset/Capacity）和 VM 摘要全部保持旧值
理由：确保崩溃后 Active Slot 的定位信息完好；摘要在 Phase 3 统一更新
```

### 3.3 Phase 2 — Write Payload

```
目标 Slot = target_slot
使用 new_layout 中的 offset/capacity

写入 Slot 数据：
  SlotHeader:
    Magic        = 0x5A534454
    SeqNum       = new_seq
    SlotOffset   = new_layout 中目标 slot 的 offset
    SlotCapacity = new_layout 中目标 slot 的 capacity
    PayloadLen   = len(payload)
  Payload:
    元数据 DTO JSON（systemTags/resourceConfigs 为 per-Resource Base64）
  Checksum:
    SHA-256(SlotHeader + Payload)

写入按 ALIGNMENT(4096) 对齐，零填充
```

### 3.4 Phase 3 — Commit (4KB Header 写入)

```
写入 Header（4096B）：

  控制区 [0, 64)：
    Magic          = 0x5A534D54        (不变)
    HeaderVersion  = 当前版本           (不变)
    ActiveSlot     = target_slot       ← 切换
    PendingOp      = 0                 ← 清除
    WriteSequence  = new_seq           ← 保持 Phase 1 值
    SlotAOffset    = new_layout 值     ← 此时更新
    SlotACapacity  = new_layout 值     ← 此时更新
    SlotBOffset    = new_layout 值     ← 此时更新
    SlotBCapacity  = new_layout 值     ← 此时更新
    LastUpdateTime = now()             ← 此时更新
    SchemaVersion  = 当前 schema 版本  ← 此时更新
  ControlChecksum  = SHA-256(bytes[0:64])

  VM 摘要区 [96, 928)：
    VmCategory     = vm_category       ← 此时更新
    VmUuid         = vm_uuid           ← 此时更新（首次写入后不变）
    VmNameLen      = len(vm_name_utf8) ← 此时更新
    VmName         = vm_name_utf8      ← 此时更新
  SummaryChecksum  = SHA-256(bytes[96:896])

  预留区 [928, 4096)：零填充

关键：ActiveSlot 切换 + 布局更新 + PendingOp 清除 + VM 摘要更新
      在同一次 4KB Header O_DIRECT 写入中完成。
```

### 3.5 Header 字段变更对照表

| 字段 | Phase 1 | Phase 3 |
|------|---------|---------|
| Magic | 不变 | 不变 |
| HeaderVersion | 不变 | 不变 |
| ActiveSlot | **不变**（旧值） | **切换**（target） |
| PendingOp | **设置**（op_type） | **清除**（0） |
| WriteSequence | **递增**（new_seq） | 不变（保持 new_seq） |
| SlotAOffset | **不变**（旧值） | **更新**（new_layout） |
| SlotACapacity | **不变**（旧值） | **更新**（new_layout） |
| SlotBOffset | **不变**（旧值） | **更新**（new_layout） |
| SlotBCapacity | **不变**（旧值） | **更新**（new_layout） |
| LastUpdateTime | **不变**（旧值） | **更新**（now） |
| SchemaVersion | **不变**（旧值） | **更新**（当前版本） |
| ControlChecksum | 重算 | 重算 |
| VM 摘要区 | **不变** | **更新** |
| SummaryChecksum | **不变** | **重算** |

---

## 4. 崩溃场景分析

### 4.1 崩溃分析表

| 崩溃点 | Header 状态 | Active Slot | Target Slot | 恢复行为 | 结果 |
|--------|------------|-------------|-------------|----------|------|
| Phase 1 之前 | 旧值，pending=0 | 有效 | 旧/空 | 正常读 Active | (Y) 读旧数据 |
| Phase 1 之后，Phase 2 之前 | pending=op, seq=new, **布局=旧** | 有效（旧布局定位正确） | 旧/空 | 用旧布局找 Target → SeqNum≠new_seq → 回退 Active | (Y) 读旧数据 |
| Phase 2 进行中 | pending=op, seq=new, **布局=旧** | 有效 | 损坏(partial write) | 用旧布局找 Target → Checksum fail → 回退 Active | (Y) 读旧数据 |
| Phase 2 完成，Phase 3 之前 (无 extend) | pending=op, seq=new, **布局=旧** | 有效 | 有效，在旧布局位置 | 用旧布局找 Target → SeqNum==new_seq → 使用新数据 | (Y) NEED_REPAIR + 读新数据（Phase 2 数据有效，需 repair 完成 Phase 3） |
| Phase 2 完成，Phase 3 之前 (有 extend) | pending=op, seq=new, **布局=旧** | 有效 | 有效，但在新布局位置 | 用旧布局找 Target → 旧位置无有效数据 → 回退 Active | (!) 读旧数据；但 repair_pending_op 双布局尝试可恢复新数据（详见 Part 4d §4.5） |
| Phase 3 之后 | 全新值，pending=0 | 新 Active 有效 | — | 正常读新 Active | (Y) 读新数据 |

### 4.2 LV extend + 崩溃场景详细分析

**场景：ActiveSlot=1(B)，payload 太大触发 extend**

```
初始状态：
  LV = 4MB
  SlotA: offset=4096, cap=2044KB
  SlotB: offset=2MiB+4096, cap=2044KB
  ActiveSlot = 1 (Slot B)

写入操作：
  target = Slot A (inactive)
  extend LV → 8MB
  new_layout: SlotA offset=4096, cap=4MB; SlotB offset=4MB+4096, cap=4MB

Phase 1: 写 Header
  PendingOp=op, WriteSeq=new
  SlotAOffset=4096, SlotACap=2044KB       ← 旧值！
  SlotBOffset=2MiB+4096, SlotBCap=2044KB  ← 旧值！

Phase 2: 写 payload 到 Slot A
  使用 new_layout: offset=4096, cap=4MB

崩溃！Phase 3 未执行
```

**恢复：**
- Header 中 ActiveSlot=1 → 读 Slot B
- SlotBOffset=2MiB+4096（旧值）→ Slot B 数据在该位置 → **定位正确** (Y)
- 读到旧数据，返回 NEED_REPAIR 或 STORAGE_CHANGE_INCOMPLETE

**对比旧方案（不修复的情况）：**
- 旧方案 Phase 1 会写新 capacity → SlotBOffset = 4096+4MB → Slot B 实际数据在 2MiB+4096 → **定位失败** (N)

### 4.3 extend 场景丢失写入的权衡

> **扩容场景修复**：若 Phase 1 完成后 LV 已扩容且 Phase 2 已用新布局写入，repair_pending_op 需尝试双布局恢复。详见 [Part 4d §repair](vm-metadata-04d-sblk读取与恢复.md#repair_pending_op)。

**丢失发生条件（必须同时满足）：**
1. 本次写入触发了 LV extend
2. 崩溃恰好发生在 Phase 2 完成后、Phase 3 执行前

**为什么可以接受：**
- 数据安全：旧数据完整可读，不损失已提交数据
- 语义正确：Phase 3 未完成 = 事务未提交 = 丢弃未提交数据是正确行为
- 自动恢复：management plane 检测到 pending_op 后会重试或 repair
- 概率极低：extend 不频繁（4MB→64MB 最多几次），且崩溃恰好卡在极窄窗口

**替代方案评估：**

| 方案 | 可行性 | 问题 |
|------|--------|------|
| Phase 2 写入旧布局位置 | (N) | 旧容量不够（否则不需要 extend） |
| 四阶段写入（Phase 2.5 更新布局） | (N) | Phase 2.5 崩溃后回到同样问题 |
| Write Ahead Log | (N) | 过度设计，复杂度与收益不对等 |

**结论：接受此场景下的行为，三阶段足够。**

---

## 5. 完整状态转换图

```
                    ┌──────────────┐
                    │  PendingOp=0 │  正常状态
                    │  ActiveSlot=X│
                    └──────┬───────┘
                           │
                    write_metadata()
                           │
              ┌────────────▼────────────┐
              │ Phase 1                  │
              │ PendingOp=1或2           │
              │ WriteSeq=new             │
              │ ActiveSlot=X (不变)      │
              │ Layout=旧 (不变)         │
              └────────────┬─────────────┘
                           │
                    ┌──────▼──────┐
           ┌───────│   Phase 2   │───────┐
           │       │ Write Slot  │       │
           │       └──────┬──────┘       │
           │              │              │
       崩溃(Target无效)  崩溃(Target有效) 正常
           │              │              │
           ▼              ▼              ▼
    ┌──────────┐  ┌───────────┐  ┌──────────────┐
    │回退到旧  │  │NEED_REPAIR│  │  Phase 3     │
    │Active    │  │可用新数据 │  │  Commit      │
    └────┬─────┘  └─────┬─────┘  └──────┬───────┘
         │              │               │
    ┌────▼─────┐  ┌─────▼─────┐  ┌──────▼───────┐
    │若op=1:   │  │ repair:   │  │ PendingOp=0  │
    │清除→OK   │  │完成Phase3 │  │ ActiveSlot=Y │
    │若op=2:   │  │           │  │ Layout=新    │
    │不清除→   │  └───────────┘  └──────────────┘
    │需refresh │                    正常状态
    └──────────┘
```
