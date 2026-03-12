# VM 元数据 — sblk 读取与恢复

## 1. 读取主流程

```
read_metadata(lv_path, lv_size):

  1. 以 O_DIRECT | O_SYNC 只读打开 LV
  2. 读 Header Block (4KB)
  3. 反序列化 + 校验 Header（magic、version、ControlChecksum）

  4. 如果 Header 有效：
       从 Header 直接读取 Slot 定位信息：
         slot_a_off = Header.SlotAOffset     ← 显式读取，不计算
         slot_a_cap = Header.SlotACapacity
         slot_b_off = Header.SlotBOffset     ← 显式读取，不计算
         slot_b_cap = Header.SlotBCapacity

       根据 PendingOp 分支 → Flow A / B / C

  5. 如果 Header 无效：
       → 进入恢复流程（§3）
```

---

## 2. 三种读取分支

### 2.1 Flow A — PendingOp = 0（正常）

```
读 Active Slot → 校验通过 → 返回 OK + payload

如果 Active Slot 校验失败：
  读 Inactive Slot
  如果 Inactive 有效：
    → DEGRADED + payload(inactive)
    → is_usable = True（允许灾备注册）
    → warning: "Active 损坏，已降级使用 Inactive，数据可能落后一写入周期"
    → repair_action: 后台触发 repair（优先切换 Active；必要时 full-refresh）
  如果 Inactive 也无效：
    → CORRUPTED + "两个 Slot 均损坏"
    → repair_action: full-refresh
```

### 2.2 Flow B — PendingOp = 1（CONFIG_UPDATE 中断）

CONFIG_UPDATE 的特点：旧数据可以安全使用（只是配置过时，不会导致数据损坏）。

```
target_slot = 1 - ActiveSlot

尝试读 Target Slot：
  如果有效 且 SeqNum == Header.WriteSequence：
    → Phase 2 已完成，Phase 3 未完成
    → NEED_REPAIR + target 的 payload（更新的数据）
    → repair_action: 完成 Phase 3

  否则（Target 无效或 SeqNum 不匹配）：
    回退读 Active Slot：
    如果有效：
      → NEED_REPAIR + active 的 payload（旧但安全的数据）
      → repair_action: 清除 PendingOp
    如果无效：
      → CORRUPTED
```

### 2.3 Flow C — PendingOp = 2（STORAGE_CHANGE 中断）

STORAGE_CHANGE 的特点：存储操作可能已在块设备层面完成，旧元数据描述的存储拓扑与实际不符，**使用旧元数据注册 VM 可能导致数据丢失**。

```
target_slot = 1 - ActiveSlot

尝试读 Target Slot：
  如果有效 且 SeqNum == Header.WriteSequence：
    → Phase 2 已完成，Phase 3 未完成
    → NEED_REPAIR + target 的 payload（新拓扑数据）
    → repair_action: 完成 Phase 3
    → 这是安全的，新数据反映了存储变更

  否则（Target 无效或 SeqNum 不匹配）：
    → Phase 2 未完成或数据损坏
    → 旧 Active Slot 的数据已过期，不反映当前存储状态

    读 Active Slot（仅用于诊断，标记为 stale）：
    → STORAGE_CHANGE_INCOMPLETE
    → payload = active 的旧数据（标记为 stale）
    → is_usable() = False    ← 关键：禁止正常使用
    → error: "存储拓扑已变更但元数据未更新，必须执行 full-refresh"
    → repair_action: "从数据库重建元数据，执行 full-refresh"
```

> **H2 修复 — 管理面自愈链路**：当控制面收到 `STORAGE_CHANGE_INCOMPLETE` 状态时，执行 `markDirty(vmUuid, true)`（`storageStructureChange=true`）触发 Poller 全量重建。若 Poller 重试耗尽，dirty 行被删除的同时在 `VmMetadataPathFingerprintVO` 上标记 `lastFlushFailed=true`。独立的 `MetadataStaleRecoveryTask`（每 30 分钟）扫描该标记并重新 `markDirty()`，为低频 VM 提供持续自愈能力。详见 [Part 2 §4.8](vm-metadata-02-脏标记与Poller.md#48-stale-恢复任务h2-修复)。

### 2.4 ReadResult 状态语义

| Status | payload | is_usable() | 调用方行为 |
|--------|---------|-------------|-----------|
| OK | (Y) 有效 | True | 正常使用 |
| NEED_REPAIR | (Y) 有效 | True | 使用数据 + 触发后台 repair |
| RECOVERED | (Y) 有效 | True | 使用数据 + 触发 Header 重建 |
| DEGRADED | (!) 有效（非最新） | True | 允许继续（如灾备注册）+ 必须告警 + 触发 repair |
| STORAGE_CHANGE_INCOMPLETE | (!) stale 数据 | **False** | **禁止注册 VM**，必须 full-refresh |
| CORRUPTED | (N) 无 | False | 必须 full-refresh |

### 2.5 Slot 读取优化

```
optimistic_read_size = min(slot_capacity, 1MB)

第一次读: 从 slot_offset 读 optimistic_read_size
  → 大多数情况下 payload < 1MB，一次读取完成

如果 payload + header + checksum > optimistic_read_size:
  第二次读: 从 slot_offset 读 aligned_up(total_needed)
  → 仅在极大 payload 时触发
```

---

## 3. Header 损坏恢复

当 Header 校验失败（magic 错误、checksum 不匹配、version 不认识）时，进入分层恢复。

### 3.1 恢复层次总览

```
Layer 1: Raw Header 字段提取
  │  即使 ControlChecksum 校验失败，Header 控制区 [0, 64) 的字段可能仍可读
  │  尝试提取 ActiveSlot、SlotAOffset、SlotBOffset 等
  │  比无 Offset 字段时多了直接定位信息，恢复成功率更高
  │
  ▼
Layer 2: 布局推算
  │  用 `blockdev --getsize64 /dev/{vg}/{lv}` 获取当前 LV 实际大小
  │  先尝试当前 lv_size 对应布局，再穷举 KNOWN_LV_SIZES 的历史布局
  │  KNOWN_LV_SIZES = [4MB, 6MB, 8MB, 12MB, 16MB, 24MB, 32MB, 48MB, 64MB]
  │  每个布局最多探测 A/B 两个 Slot（最多 18 次 I/O 探测）
  │  命中任一可校验 Slot 即返回该布局候选
  │
  ▼
Layer 3: Slot A 自描述辅助定位 Slot B
  │  如果 Layer 2 的 Slot B 位置失败
  │  从 Slot A 的 SlotOffset + SlotCapacity 推算旧 Slot B 位置
  │  覆盖 LV extend 后布局变化的情况
  │
  ▼
Layer 4: Brute-force 扫描
     最后手段，以 1MB 为单位批量读取 LV，在内存中逐 ALIGNMENT 对齐位置搜索
     匹配条件：ZSDT Magic + SlotOffset == actual_offset（双重校验，误报极低）
     64MB LV ≈ 64 次 × 1MB 读 ≈ 64MB I/O（顺序读，SSD 场景 <1s）
```

### 3.2 Slot 选择策略

当找到两个有效 Slot 时：

```
优先级：
  1. Raw Header 中的 ActiveSlot hint（如果可提取）→ 使用 hint 指向的 Slot
  2. 无 hint → 使用 SeqNum 更高的 Slot（最后写入的数据更新）
  3. 只有一个有效 → 使用该 Slot
  4. 都无效 → CORRUPTED

注意：恢复路径使用 relaxed 校验模式（见 Part 4b §2.4）
  - 不校验 SlotCapacity（因为传入的 capacity 可能是推算的，与 Slot 自描述不同）
  - 依赖 Checksum 作为最终数据完整性裁判
```

### 3.3 Layer 2 多布局穷举（Q4-1）

```
KNOWN_LV_SIZES = [4MB, 6MB, 8MB, 12MB, 16MB, 24MB, 32MB, 48MB, 64MB]

输入: current_lv_size
候选集合: [size in KNOWN_LV_SIZES where size <= current_lv_size]

对每个候选 size:
  1. layout = calculate_slot_layout(size)
  2. 尝试读取 slotA/slotB 的 slot header magic + checksum
  3. 若任一 slot 可校验通过，记录该 layout 为可用候选

选择策略:
  - 优先命中 Header hint 指向的 slot/layout
  - 否则按 SeqNum 选择更新数据

复杂度上界:
  - 候选布局最多 9 个
  - 每布局最多 2 次 slot 探测
  - 总探测 ≤ 18 次 I/O（不含最终 payload 读取）
```

### 3.4 Layer 1 详细逻辑

```
读取 Header 原始 4KB 数据（控制区字段在 [0, 64) 内）

尝试解析 Magic:
  if magic != 0x5A534D54 → 跳过 Layer 1，进入 Layer 2

Magic 正确但 Checksum 错误（单 bit 翻转等场景）:
  提取各字段作为 hint:
    active_slot_hint  ← 如果值 ∈ {0, 1} 则可信
    slot_a_off_hint   ← 如果值 > 0 且 < lv_size 则可用
    slot_b_off_hint   ← 如果值 > slot_a_off_hint 且 < lv_size 则可用

  用 hint 的 offset 尝试读 Slot:
    如果成功 → 返回 RECOVERED
    如果失败 → 继续 Layer 2
```

**Layer 1 的改进**：Header 显式存储 SlotAOffset + SlotBOffset，raw 提取后直接可用，无需从 SlotACapacity 间接推算，减少一步出错风险。

---

## 4. PendingOp 语义与 Repair 策略

### 4.1 PendingOp 语义对照

| PendingOp | 含义 | 写入中断的后果 | 旧数据安全性 | 可否简单清除 |
|-----------|------|--------------|-------------|-------------|
| 0 | 空闲，上次写入已完成 | — | — | — |
| 1 (CONFIG_UPDATE) | 正在写入普通配置变更的元数据 | 丢失一次配置更新，可接受 | (Y) 旧配置安全可用 | (Y) 可以 |
| 2 (STORAGE_CHANGE) | 正在写入存储变更后的元数据（存储上已有新快照/卷） | 存储上有新数据，但元数据没记录！ | (N) 旧拓扑与实际不符 | (N) **绝不可以** |

**核心区别**：CONFIG_UPDATE 的旧数据"过时但安全"，STORAGE_CHANGE 的旧数据"过时且危险"。

### 4.2 repair_pending_op — CONFIG_UPDATE (pending_op = 1)

```
读取 Header → 确认 pending_op = 1

先用 Header 旧布局计算 target_slot 并读取

如果旧布局失败，再用当前 LV 大小推导新布局重试 target_slot
（双布局尝试：old-layout → new-layout）

Case A: Target 有效 且 SeqNum == Header.WriteSequence
  → Phase 2 已完成，只需完成 Phase 3
  → 写入新 Header:
      ActiveSlot     = target_slot
      PendingOp      = 0
      WriteSequence  = 保持
      布局字段        = 若命中旧布局则保持旧值，若命中新布局则更新为新布局
      LastUpdateTime = now()
  → 返回 repaired=True, "Completed Phase 3"

Case B: Target 无效（旧/新布局均失败）
  → Phase 2 未完成（或数据损坏）
  → 安全丢弃本次写入，恢复到旧状态
  → 写入新 Header:
      ActiveSlot     = 保持（旧值）
      PendingOp      = 0         ← 清除
      WriteSequence  = 保持
      布局字段        = 保持
      LastUpdateTime = 保持
  → 返回 repaired=True, "Aborted incomplete config update"
```

### 4.3 repair_pending_op — STORAGE_CHANGE (pending_op = 2)

```
读取 Header → 确认 pending_op = 2

先用 Header 旧布局计算 target_slot 并读取

如果旧布局失败，再用当前 LV 大小推导新布局重试 target_slot
（双布局尝试：old-layout → new-layout）

Case A: Target 有效 且 SeqNum == Header.WriteSequence
  → Phase 2 已完成，可以安全完成 Phase 3
  → 写入新 Header:
      ActiveSlot     = target_slot
      PendingOp      = 0
      WriteSequence  = 保持
      布局字段        = 若命中新布局则更新为新布局，否则保持旧值
      LastUpdateTime = now()
  → 返回 repaired=True, "Completed Phase 3 for storage change"

Case B: Target 无效（旧/新布局均失败）
  → Phase 2 未完成
  → 旧 Active Slot 中的元数据不反映当前存储状态
  → (!) 不清除 PendingOp ← 关键决策
  → 返回 repaired=False,
        error="STORAGE_CHANGE pending, target data lost.
               Metadata is stale. Must execute full-refresh
               from database to rebuild metadata."
```

### 4.4 为什么 STORAGE_CHANGE 不能简单清除 PendingOp

```
如果清除 pending_op:
  Header 变为: pending=0, ActiveSlot=旧
  后续 read_metadata → 返回 OK + 旧 payload
  调用方认为数据有效 → 用旧拓扑注册 VM

  但实际存储状态已变更（如：快照已创建/删除、卷已扩容）
  旧拓扑 ≠ 当前存储 → VM 挂载错误的快照链
  → 数据损坏或丢失
```

**PendingOp=2 是一个"脏标记"**：它的存在持续提醒系统"存储状态与元数据不一致"。只有两种方式可以消除该标记：

1. **找到有效 Target 完成 Phase 3** — 新元数据反映了存储变更，安全
2. **Full-refresh 写入全新元数据** — 从数据库重建完整拓扑，覆盖整个 Header

### 4.5 双布局 repair 伪代码（Q4-5）

```python
def repair_pending_op(header, current_lv_size):
    target = 1 - header.active_slot

    # 尝试 1：Header 旧布局
    old_layout = header.layout
    slot = try_read_target(target, old_layout)
    if slot.valid and slot.seq_num == header.write_sequence:
        return complete_phase3(header, slot, old_layout)

    # 尝试 2：当前 LV 新布局
    new_layout = calculate_slot_layout(current_lv_size)
    slot = try_read_target(target, new_layout)
    if slot.valid and slot.seq_num == header.write_sequence:
        return complete_phase3(header, slot, new_layout)  # 同步更新 Header 布局字段

    # 双布局均失败
    if header.pending_op == STORAGE_CHANGE:
        return RepairResult(repaired=False, keep_pending=True)
    else:
        return clear_pending_and_keep_active(header)
```

---

## 5. Full-Refresh 机制

### 5.1 触发条件

| 场景 | 触发方 |
|------|--------|
| STORAGE_CHANGE_INCOMPLETE | management plane 检测到后主动触发 |
| CORRUPTED（两个 Slot 都损坏） | management plane 检测到后主动触发 |
| repair_pending_op 返回 repaired=False | management plane 收到失败回调后触发 |
| 管理员手动触发 | 运维命令 |

### 5.2 执行方式

Full-refresh 本质上是一次普通的 `write_metadata` 调用：

```
full_refresh(lv_path, lv_size_getter, lv_extend_func):

  1. Management plane 从数据库查询 VM 的完整存储拓扑
  2. 生成最新的 payload JSON
  3. 调用 write_metadata(lv_path, payload, storageStructureChange=True)
     → 控制面显式指定 op_type = STORAGE_CHANGE (2)

  写入流程:
    Phase 1: PendingOp=2, WriteSeq=old+1
    Phase 2: 写入新 payload 到 inactive Slot
    Phase 3: ActiveSlot 切换, PendingOp=0

  成功后:
    - 旧的 STORAGE_CHANGE pending 状态被覆盖
    - 新元数据反映数据库中的最新拓扑
    - 两个 Slot 中至少有一个包含正确数据
```

### 5.3 Full-refresh 使用 STORAGE_CHANGE(2) 的理由

- Full-refresh 由控制面触发，显式指定 `storageStructureChange=true` → op_type=2
- 这自然解决了"full-refresh Phase 1 覆盖脏标记"问题：新的 PendingOp=2 与旧的语义一致
- 不需要引入新的 OP_FULL_REFRESH (3)

### 5.4 Full-refresh 中断场景

```
如果 full-refresh 本身在 Phase 2 之前崩溃:
  Phase 1 写入了 PendingOp=2
  Target 无效
  repair → Case B for STORAGE_CHANGE → 返回 STORAGE_CHANGE_INCOMPLETE
  此时 Active Slot 仍然是旧的

  是否有风险？
  → management plane 知道 full-refresh 失败了
     （write_metadata 会抛异常），会重试。
  → 重试仍会使用 op=2（控制面显式指定），PendingOp 语义一致。
  → 只要 management plane 正确实现重试逻辑，不会误用旧数据。
```

---

## 6. 部分写入安全性分析

> 完整分析见 [Part 4a §5.3](vm-metadata-04a-sblk存储协议概述.md#53-崩溃安全模型)。本节仅补充读取/恢复视角的关键结论。

本协议**不依赖单次 4KB I/O 的原子性**。即使 Header 的 4KB 写入在中途崩溃导致部分字段更新：

- **ControlChecksum 不匹配** → 进入 Header 损坏恢复流程（§3）
- **Layer 1**：从 raw Header 提取 Slot 偏移量（Magic 正确时各字段大概率可读）
- **Layer 2**：从 `blockdev --getsize64` 获取的 `lv_size` 推算 Slot 布局
- **兜底**：Slot 自描述 + SHA-256 Checksum 保证最终数据完整性

> 最坏情况（Header + 一个 Slot 都损坏）下，恢复流程仍能通过 Brute-force 扫描（Layer 4）找到另一个有效 Slot。

---

## 附录 A. 读取与恢复测试矩阵

| # | 场景 | Header 状态 | PendingOp | Active Slot | Inactive Slot | 预期结果 |
|---|------|-------------|-----------|-------------|---------------|----------|
| 1 | 正常读取 | 有效 | 0 | 有效 | — | OK + payload |
| 2 | Active 损坏（降级路径） | 有效 | 0 | 无效 | 有效 | DEGRADED + payload(inactive), is_usable=True |
| 3 | 两 Slot 损坏 | 有效 | 0 | 无效 | 无效 | CORRUPTED |
| 4 | CONFIG Phase 2 完成 | 有效 | 1 | 有效（旧） | 有效 + SeqMatch | NEED_REPAIR + 新 payload |
| 5 | CONFIG Phase 2 未完成 | 有效 | 1 | 有效（旧） | 无效 | NEED_REPAIR + 旧 payload |
| 6 | STORAGE Phase 2 完成 | 有效 | 2 | 有效（旧） | 有效 + SeqMatch | NEED_REPAIR + 新 payload |
| 7 | STORAGE Phase 2 未完成 | 有效 | 2 | 有效（旧） | 无效 | STORAGE_CHANGE_INCOMPLETE |
| 8 | Header Checksum 错误 | 无效 | — | 有效 | 有效 | RECOVERED (Layer 1/2) |
| 9 | Header Magic 错误 | 无效 | — | 有效 | — | RECOVERED (Layer 2/4) |
| 10 | 全新 LV（刚初始化） | 有效 | 0 | 有效（空 `{}`） | 零 | OK + `{}` |
| 11 | LV 扩容后旧 Header | 有效 | 0 | 有效 | — | OK（Slot offset 从 Header 读取，不依赖 lv_size） |
| 12 | 灾备复制中途快照 | 可能部分 | — | 有效 | 半写 | RECOVERED / NEED_REPAIR |
| 13 | Layer 2 多布局命中历史 4MB 布局 | Header 无效 | — | 旧布局有效 | — | RECOVERED（枚举命中，≤18 次探测） |
| 14 | Layer 2 当前布局失败→Layer 3 自描述成功 | Header 无效 | — | SlotA 有效 | SlotB 旧偏移 | RECOVERED（Layer 3） |
| 15 | repair_pending_op: old-layout 失败、new-layout 成功 | 有效 | 1/2 | 旧布局 target 无效 | 新布局 target 有效 + SeqMatch | repaired=True；若 pending=2 同步更新布局字段 |
| 16 | brute-force 扫描超时 | Header 无效 | — | 未定位 | 未定位 | CORRUPTED（返回超时错误，避免长时间阻塞） |
| 17 | extend+Phase 2 完成+初始读取（旧布局 Header） | 有效 | 1/2 | 有效（旧） | 有效但在新布局位置 | 回退 Active → NEED_REPAIR(op=1) / STORAGE_CHANGE_INCOMPLETE(op=2)；后续 repair 双布局尝试可恢复 |

---

## 7. 约束与不変量

| 约束 ID | 约束描述 | 违反后果 | 检查点 |
|---------|----------|----------|--------|
| C-RD | Flow A 中 Active 损坏且 Inactive 可校验时，必须返回 `DEGRADED` 且 `is_usable=True` | 可恢复数据被误判为不可用，灾备恢复失败 | §2.1 / §2.4 |
| C-RC | `STORAGE_CHANGE_INCOMPLETE` 必须保持 `is_usable=False`，禁止注册路径消费 | 用 stale 拓扑注册 VM，可能造成数据损坏 | §2.3 / §2.4 |
| C-SC | `STORAGE_CHANGE_INCOMPLETE` 必须通过 `markDirty(vmUuid, true)` 触发全量重建，且重试耗尽后由 `MetadataStaleRecoveryTask` 接管恢复，禁止静默放弃 | 低频 VM 存储拓扑与元数据永久不一致 | §2.3 / Part 2 §4.8 |
| C-RP | `repair_pending_op` 必须按 old-layout → new-layout 双布局尝试；pending=2 双失败时不得清除 PendingOp | extend+Phase2 完成场景丢失可恢复数据，或错误掩盖存储变更脏态 | §4.3 / §4.5 |
| C-SV | Layer 2 多布局穷举集合固定为 9 种 `KNOWN_LV_SIZES`，探测上界 ≤18 次 I/O | 恢复复杂度失控或遗漏历史布局导致恢复失败 | §3.1 / §3.4 |
