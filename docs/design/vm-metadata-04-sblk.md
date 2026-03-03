# sblk 二进制存储协议

## 1. 背景

ZStack 共享块存储（sblk）场景下，VM 元数据需要持久化到 LVM Logical Volume 上。多个管理节点可能通过共享块设备并发访问同一 LV。

核心挑战：

- **无文件系统**：LV 是裸块设备，无法使用常规文件 I/O
- **共享访问**：多节点通过 O_DIRECT 绕过 page cache 直接读写
- **崩溃安全**：任意时刻断电或进程崩溃后，数据必须可恢复
- **空间受限**：LV 初始 4MB，最大 64MB，需高效利用

> **对比 local/NFS**：文件系统场景使用 JSON 明文 + tmp + fsync + rename 原子写，  
> sblk 因裸块设备特性需要完全不同的存储协议。

### 1.1 灾备接管场景 — AB 双 Slot 的核心驱动力

除常规读写外，sblk 元数据协议必须支持**跨平台灾备接管**场景：

```
环境：
  sanA / sanB — 两套拥有相同 LUN（数量和大小）的 SAN 存储
  zsvA（原平台）/ zsvB（目标平台）— 两套独立的 ZSV 管理平台

操作流程：
  1. zsvA 将 sanA 添加为 sblk 存储，在上面创建 VM 并正常读写
  2. zsvB 将 sanB 添加为存储目标（iSCSI server），但不注册为 sblk 存储
  3. 存储侧配置 sanA → sanB 的 LUN 级数据复制（块级，平台不感知）
  4. zsvA 的 sanA 发生故障
  5. zsvB 使用 sanB 注册 sblk 存储，通过扫描 LV 上的元数据恢复 VM
```

此场景下 LV 元数据的角色发生本质转变：

| | 正常运行 | 灾备接管 |
|---|---------|---------|
| **元数据权威来源** | 管理面 DB | **LV 上的元数据** |
| **LV 元数据角色** | DB 的副本/缓存 | **唯一的 VM 恢复来源** |
| **管理面 DB 可用？** | ✅ zsvA DB 可用 | ❌ zsvA 故障，zsvB DB 无此 VM 记录 |
| **full-refresh 可行？** | ✅ 从 DB 重建 | ❌ **无 DB 数据可重建** |

**核心问题**：存储侧复制是**块级别快照**，可能捕获到 LV 正在写入的中间状态。

如果使用简单的单区覆盖写方案（写入中数据被覆盖），此时：

```
zsvA 正在执行 write_metadata():
  已写入部分新数据，旧数据已被覆盖

此刻 sanA → sanB 块级复制发生

sanB 上的 LV：
  payload 部分损坏，checksum 校验失败 → CORRUPTED
  旧数据已被覆盖 → 不可读
  zsvB DB 无此 VM 记录 → 无法 full-refresh
  → VM 不可恢复 ❌
```

**A/B 双 Slot 方案**在同一场景下：

```
zsvA 正在执行 write_metadata():
  Phase 1: 标记 PendingOp, ActiveSlot=A 不变
  Phase 2: 正在写入 Slot B (inactive)...

此刻 sanA → sanB 块级复制发生

sanB 上的 LV：
  Header: ActiveSlot=A, PendingOp≠0
  Slot A: 完整有效（旧数据，写入过程中未被触碰）
  Slot B: 部分损坏

zsvB 读取:
  Header → ActiveSlot=A → 读 Slot A → checksum pass
  → 返回旧元数据 → VM 可注册 ✅
```

**A/B 双 Slot 的核心保证：写入过程中旧数据始终完好。** 这是灾备场景下 VM 可恢复的前提条件，也是本协议采用 A/B Dual Slot 而非更简单方案的根本原因。

> **方案选型结论**：单区覆盖写方案（无论是否有 Header 哨兵）在灾备接管场景下都会丢失 VM；
> A/B 双 Slot 是能保证任意复制时刻都有可读数据的最简方案。
> 协议复杂度是为灾备可靠性买单。

---

## 2. 设计目标

| 目标 | 要求 |
|------|------|
| 原子性 | 任意崩溃点数据不损坏，已提交数据不丢失 |
| 自描述 | Slot 自带位置信息，Header 损坏时仍可恢复 |
| 高效 I/O | O_DIRECT + O_SYNC，对齐到扇区/页边界 |
| 简单可靠 | 纯二进制定长字段，无 JSON 解析开销 |
| 可观测 | hexdump 直接可读，状态可诊断 |
| 前向兼容 | HeaderVersion 管布局演进，SchemaVersion 管 payload 演进 |

---

## 3. 整体架构

LV 初始预分配 4MB 空间（虚拟机在正常使用场景下，元数据一般只有几十 KB）。直接以 Raw Data 存储 JSON 元数据，不格式化文件系统，减少性能开销。采用 **预分配固定大小 LV + Raw Data 存储 + A/B 分区原子写** 方案，规避频繁创建/删除 LV 的性能问题。

```
LV Layout (e.g. 4MB)
┌──────────────┬────────────────────┬────────────────────┐
│ Header Block │      Slot A        │      Slot B        │
│   512B       │   ~2MB             │   ~2MB             │
│ (pad to 4KB) │                    │                    │
└──────────────┴────────────────────┴────────────────────┘
offset: 0       4096                 4096 + SlotACapacity

空间计算公式（4KB 对齐）：
available = LV_SIZE - 4096
slot_capacity = floor(available / 2 / 4096) * 4096

示例（4MB LV）：
available = 4194304 - 4096 = 4190208
slot_capacity = floor(4190208 / 2 / 4096) * 4096 = 2093056

Header:  [0, 4096)
Slot A:  [4096, 2097152)         约 2043 KB
Slot B:  [2097152, 4190208)      约 2043 KB
Tail:    [4190208, 4194304)      约 4 KB (未使用)
```

- **Header Block (512B)**：控制信息，单扇区原子写保证
- **Slot A / Slot B**：双槽交替写入，A/B 切换实现原子更新
- **Header 占用前 4KB**：虽然 Header 只有 512B，但为满足 O_DIRECT 对齐要求，Header 后到 Slot A 之间填充零

### 3.1 A/B Dual Slot 机制

```
正常状态 (ActiveSlot=A):
  读取 → Slot A (当前有效数据)

写入时:
  Phase 1 → 标记 intent 到 Header
  Phase 2 → 写新数据到 Slot B (inactive)
  Phase 3 → 切换 ActiveSlot 到 B + 清除 intent

下次写入:
  Phase 1 → 标记 intent
  Phase 2 → 写新数据到 Slot A (此时 inactive)
  Phase 3 → 切换 ActiveSlot 到 A
```

交替写入确保：**任意崩溃点至少有一个 Slot 包含完整有效数据**。

### 3.2 版本管理

两个独立的版本号，职责分离：

| 版本号 | 位置 | 含义 | 何时递增 |
|--------|------|------|---------|
| HeaderVersion | Header Block | 二进制布局版本（字段偏移、大小、Checksum 算法） | 增删 Header/Slot 字段时 |
| SchemaVersion | Header Block | Payload JSON 业务 schema 版本 | Payload 中 JSON 字段增减时 |

读取时：

- `HeaderVersion > MAX_KNOWN` → 拒绝解析，提示升级软件
- `SchemaVersion > MAX_KNOWN` → 可读出 payload，但提示部分字段可能无法识别

### 3.3 Python 2 兼容性

当前 Agent 环境为 Python 2.7（与 ZStack KVM Agent 一致）：

- `struct.pack/unpack` 处理大端序二进制
- `ctypes` 分配对齐内存缓冲区（O_DIRECT 要求）
- `buffer()` 实现零拷贝写入
- `hashlib.sha256` (Python 2.7+ 内置)
- 编码时使用 `from __future__ import print_function, unicode_literals` 保持 2/3 兼容
- `struct.pack`/`hashlib`/`ctypes` 在 Python 2.7+ 和 3.x 行为一致
- Python 3 迁移随 Agent 整体迁移计划进行，不单独迁移

---

## 4. 二进制布局

### 4.1 Header Block (512 Bytes)

单扇区大小，硬件层面保证原子写入：写入要么完全成功（全新数据），要么完全未发生（全旧数据），不存在中间状态。

#### 4.1.1 字段定义

```
Offset  Size   Field             Type        Description
──────  ─────  ────────────────  ──────────  ──────────────────────────────────────────
0       4B     Magic             uint32 BE   固定 0x5A534D54 ("ZSMT")
4       2B     HeaderVersion     uint16 BE   二进制格式版本号，当前 = 1
6       1B     ActiveSlot        uint8       0 = Slot A，1 = Slot B
7       1B     PendingOp         uint8       0 = 无，1 = config_update，2 = storage_change
8       8B     WriteSequence     uint64 BE   单调递增写计数器
16      8B     SlotAOffset       uint64 BE   Slot A 在 LV 中的字节偏移
24      8B     SlotACapacity     uint64 BE   Slot A 容量（字节）
32      8B     SlotBOffset       uint64 BE   Slot B 在 LV 中的字节偏移
40      8B     SlotBCapacity     uint64 BE   Slot B 容量（字节）
48      8B     LastUpdateTime    uint64 BE   最后成功写入的 epoch 毫秒
56      4B     SchemaVersion     uint32 BE   Payload JSON schema 版本
60      4B     Reserved          uint32      预留，必须写 0
──────
64B     (以上为 Checksum 覆盖范围)
──────
64      32B    Checksum          raw bytes   SHA-256(bytes[0:64])
96      416B   Padding           zero        填充至 512B
──────
Total:  512B
```

#### 4.1.2 字段设计理由

**Magic (4B, offset 0)**
- `0x5A534D54` = ASCII "ZSMT" (ZStack Metadata)
- hexdump 一眼可辨识
- brute-force 恢复时每个扇区只需读前 4 字节判断

**HeaderVersion (2B, offset 4)**
- 二进制布局版本，只在 Header/Slot 结构变更时递增
- uint16 足够（不可能有 65535 次布局变更）
- 与 SchemaVersion 职责分离：HeaderVersion 管"怎么读"，SchemaVersion 管"读出的 JSON 怎么解释"

**ActiveSlot (1B, offset 6) + PendingOp (1B, offset 7)**
- 各 1B 足够（取值范围 0~2）
- 不用 bit flags：语义清晰，调试简单
- 紧凑排列，在同一个 8B 对齐块内

**WriteSequence (8B, offset 8)**
- uint64，理论上限 ~1.8×10¹⁹
- 以 1000 次/秒计算，约 5.84 亿年溢出
- 自然对齐在 offset 8

**SlotAOffset / SlotBOffset (各 8B, offset 16/32)**
- **显式存储**，不再通过 `SlotBOffset = ALIGNMENT + SlotACapacity` 间接计算
- 消除了 SlotACapacity 修改导致 SlotB 定位错误的连锁风险
- 恢复时直接从 raw Header 提取 offset 即可尝试读 Slot

**SlotACapacity / SlotBCapacity (各 8B, offset 24/40)**
- uint64 最大值 16EB，远超 64MB LV 上限
- 两个字段分开存储，为未来非对称 Slot 预留可能

**LastUpdateTime (8B, offset 48)**
- epoch 毫秒，uint64
- 诊断用途：不读 Slot 即可判断最后更新时间
- 冲突检测：多主脑裂时辅助判断数据新鲜度

**SchemaVersion (4B, offset 56)**
- Payload JSON 的业务 schema 版本
- 读 Header 即可判断是否认识该版本，无需解码整个 Slot
- **编码规则**：将 `dbf.getDbVersion()` 返回的数据库版本字符串（如 `"4.10.12"`）解析为数字组件后压缩为 uint32：`(A << 20) | (B << 10) | C`。例如 `"4.10.12"` → `(4 << 20) | (10 << 10) | 12 = 0x00402C0C = 4205580`。解码：`A = v >> 20`，`B = (v >> 10) & 0x3FF`，`C = v & 0x3FF`。每个组件最大支持 1023
- Header 中的 SchemaVersion 与 DTO JSON 中的 `schemaVersion` 字符串（`dbf.getDbVersion()`）是同一语义的不同表示，写入时编码、读取时解码

**Reserved (4B, offset 60)**
- 将 Checksum 推到 offset 64（8B 对齐）
- 必须写 0，读取时忽略
- 未来可用于 flags、compression type 等

**Checksum (32B, offset 64)**
- SHA-256 of bytes[0:64]
- 覆盖 Checksum 之前的所有字段（含 Reserved 和 Magic）
- **不覆盖 Padding**：Padding 不携带信息，排除在外方便未来使用 Padding 区域扩展字段而不破坏旧版本兼容性
- 校验逻辑：`sha256(block[0:64]) == block[64:96]`

**Padding (416B, offset 96)**
- 填充至 512B
- 扩展储备：未来增字段从 Padding 分配，bump HeaderVersion
- **约束**：新增字段的默认值必须与 zero（全 0 字节）语义兼容（旧版本写入的 Padding 为 zero）

#### 4.1.3 hexdump 示例

```
00000000  5a 53 4d 54 00 01 00 01  00 00 00 00 00 00 00 2a  |ZSMT...........*|
          ^^^^^^^^^ ^^^^^ ^^ ^^    ^^^^^^^^^^^^^^^^^^^^^^^^^
          Magic     V=1  A=0 P=1   WriteSeq = 42

00000010  00 00 00 00 00 00 10 00  00 00 00 00 00 1f e0 00  |................|
          ^^^^^^^^^^^^^^^^^^^^^^^^^  ^^^^^^^^^^^^^^^^^^^^^^^^^
          SlotAOffset = 4096         SlotACapacity = 2088960

00000020  00 00 00 00 00 1f f0 00  00 00 00 00 00 1f e0 00  |................|
          ^^^^^^^^^^^^^^^^^^^^^^^^^  ^^^^^^^^^^^^^^^^^^^^^^^^^
          SlotBOffset = 2093056      SlotBCapacity = 2088960

00000030  00 00 01 8e 3a 5b c0 00  00 00 00 02 00 00 00 00  |....:...........|
          ^^^^^^^^^^^^^^^^^^^^^^^^^  ^^^^^^^^^^^ ^^^^^^^^^^^
          LastUpdate=1709123456000   SchemaV=2   Reserved=0

00000040  a1 b2 c3 d4 ... (32 bytes SHA-256 checksum) ...   |................|

00000060  00 00 00 00 ... (padding to 512B) ...              |................|
```

#### 4.1.4 版本兼容策略

```
读取时:
  if header_version > MAX_KNOWN_VERSION:
      → 拒绝解析，返回错误，提示升级软件

  if header_version == 1:
      → 用 V1 布局解析（当前方案）

  # 未来 V2 示例:
  if header_version == 2:
      → Reserved 位置改为 CompressionType
      → Padding 区域 offset 96~103 分配给新字段
      → Checksum 范围扩大到 bytes[0:104]
```

### 4.2 Slot 结构

Slot 是数据搬运工，职责单一：可靠地存取 payload、支持自描述恢复。

#### 4.2.1 字段定义

```
Offset   Size   Field          Type        Description
───────  ─────  ────────────── ──────────  ──────────────────────────────────
0        4B     Magic          uint32 BE   固定 0x5A534454 ("ZSDT")
4        8B     SeqNum         uint64 BE   写序号，与 Header.WriteSequence 对应
12       8B     SlotOffset     uint64 BE   自描述：本 Slot 在 LV 中的字节偏移
20       8B     SlotCapacity   uint64 BE   自描述：本 Slot 容量
28       8B     PayloadLen     uint64 BE   Payload 实际字节数
───────
36B      (固定 Header 部分)
───────
36       NB     Payload        raw bytes   元数据 DTO JSON 明文（systemTags/resourceConfigs 为 per-Resource Base64 编码）
36+N     32B    Checksum       raw bytes   SHA-256(bytes[0:36+N])
───────
Total:   36 + N + 32 B
```

#### 4.2.2 字段说明

| 字段 | 设计理由 |
|------|---------|
| Magic | 标识 Slot 数据块，brute-force 恢复的入口条件 |
| SeqNum | 与 Header.WriteSequence 匹配来判断 Phase 2 是否完成 |
| SlotOffset | Header 损坏时的自描述定位；brute-force 时 `stored_offset == actual_offset` 是强校验 |
| SlotCapacity | 配合 SlotOffset 可重建布局；`SlotA.Offset + SlotA.Capacity` 可定位 SlotB |
| PayloadLen | 8B (uint64)，虽然实际不超过 32MB，但保持与其他字段统一的 8B 对齐 |
| Payload | 变长，元数据 DTO JSON（systemTags/resourceConfigs 字段为 per-Resource Base64 编码） |
| Checksum | 尾部放置，SHA-256 覆盖 SlotHeader + Payload 全部内容 |

#### 4.2.3 Checksum 放尾部的理由

| 方案 | 优点 | 缺点 | 结论 |
|------|------|------|------|
| Checksum 在尾部（当前） | 写入自然流程；覆盖全部数据 | 需先读 PayloadLen 才知道 Checksum 位置 | ✅ 采用 |
| Checksum 在 Header 固定位置 | 固定偏移 | 不读 Payload 也无法校验，没有实际收益 | ❌ |
| Header/Payload 双 Checksum | 可先验证 Header | 增加写入复杂度，1MB 优化读已覆盖大多数场景 | ❌ |

#### 4.2.4 Slot 读取校验清单

**正常路径 (strict 模式)：**
1. `Magic == 0x5A534454`
2. `SlotOffset == expected_offset`
3. `SlotCapacity == expected_capacity`
4. `PayloadLen > 0` 且 `PayloadLen <= SlotCapacity - 36 - 32`
5. `SHA-256(bytes[0:36+PayloadLen]) == bytes[36+PayloadLen:36+PayloadLen+32]`

**恢复路径 (relaxed 模式)：**
1. `Magic == 0x5A534454`
2. `SlotOffset == actual_read_offset`（验证自描述一致性）
3. 不校验 Capacity（恢复时传入的 capacity 可能是推算的）
4. PayloadLen 范围合理
5. Checksum 校验通过

#### 4.2.5 Slot 结构不做修改的理由

| 候选改进 | 结论 | 理由 |
|---------|------|------|
| PayloadLen 缩为 4B | ❌ 不改 | 只省 4B，破坏 8B 对齐 |
| 增加 SlotIndex (A/B 标识) | ❌ 不改 | SeqNum 已够判断顺序，SlotIndex 冗余 |
| 增加 Slot 独立版本号 | ❌ 不改 | Header 的 HeaderVersion 已管控全局布局版本 |

---

## 5. 三阶段原子写入

### 5.1 设计原则

1. **512B 原子写保证**：Header Block 恰好 512B（一个扇区），硬件保证写入原子性
2. **Phase 1 不破坏现状**：Phase 1 写入的 Header 必须保留 Active Slot 的完整定位能力
3. **Phase 3 一次性提交**：ActiveSlot 切换、布局更新、PendingOp 清除在同一个 512B 原子写中完成
4. **Slot 自描述**：每个 Slot 内嵌位置信息，即使 Header 损坏也可恢复

### 5.2 完整流程

```
前置：确定目标
  target_slot = 1 - Header.ActiveSlot
  new_seq     = Header.WriteSequence + 1

  如果 payload 超出当前 Slot 容量：
    new_lv_size = calculate_extend_size(current_lv_size, required)
    执行 lvextend
    new_layout = calculate_slot_layout(new_lv_size)
  否则：
    new_layout = 当前 Header 中的布局（offset + capacity 不变）
```

#### 前置步骤 — 确定 op_type（控制面指定）

> **op_type 由控制面指定**：`@MetadataImpact(CONFIG)` → `op_type = CONFIG_UPDATE (1)`，`@MetadataImpact(STORAGE)` → `op_type = STORAGE_CHANGE (2)`。管理层面通过 `storageStructureChange` 字段贯穿整条消息链（`UpdateVmInstanceMetadataMsg` → `WriteVmMetadataToPrimaryStorageMsg` → Agent command），Agent 收到命令时直接使用该值，无需自行读取旧 payload 做 diff。

> **注意**：local/NFS 不使用 op_type（JSON atomic rename 无中间状态），`storageStructureChange` 仅在 sblk 场景下转换为 PendingOp 值。

#### Phase 1 — Mark Intent (512B 原子写)

```
写入 Header：
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
  Checksum       = SHA-256(bytes[0:64])

关键约束：布局字段（Offset/Capacity）全部保持旧值
理由：确保崩溃后 Active Slot 的定位信息完好
```

#### Phase 2 — Write Payload (可能跨多个扇区)

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

#### Phase 3 — Commit (512B 原子写)

```
写入 Header：
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
  Checksum       = SHA-256(bytes[0:64])

关键：ActiveSlot 切换 + 布局更新 + PendingOp 清除在同一个 512B 原子写中完成
      要么全部生效（全新），要么全部未生效（全旧）
```

### 5.3 崩溃场景完整分析

#### 5.3.1 崩溃分析表

| 崩溃点 | Header 状态 | Active Slot | Target Slot | 恢复行为 | 结果 |
|--------|------------|-------------|-------------|----------|------|
| Phase 1 之前 | 旧值，pending=0 | 有效 | 旧/空 | 正常读 Active | ✅ 读旧数据 |
| Phase 1 之后，Phase 2 之前 | pending=op, seq=new, **布局=旧** | 有效（旧布局定位正确） | 旧/空 | 用旧布局找 Target → SeqNum≠new_seq → 回退 Active | ✅ 读旧数据 |
| Phase 2 进行中 | pending=op, seq=new, **布局=旧** | 有效 | 损坏(partial write) | 用旧布局找 Target → Checksum fail → 回退 Active | ✅ 读旧数据 |
| Phase 2 完成，Phase 3 之前 (无 extend) | pending=op, seq=new, **布局=旧** | 有效 | 有效，在旧布局位置 | 用旧布局找 Target → SeqNum==new_seq → 使用新数据 | ✅ 读新数据 |
| Phase 2 完成，Phase 3 之前 (有 extend) | pending=op, seq=new, **布局=旧** | 有效 | 有效，但在新布局位置 | 用旧布局找 Target → 旧位置无有效数据 → 回退 Active | ⚠️ 读旧数据（本次写入丢失） |
| Phase 3 之后 | 全新值，pending=0 | 新 Active 有效 | — | 正常读新 Active | ✅ 读新数据 |

#### 5.3.2 LV extend + 崩溃场景详细分析

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
- SlotBOffset=2MiB+4096（旧值）→ Slot B 数据在该位置 → **定位正确** ✅
- 读到旧数据，返回 NEED_REPAIR 或 STORAGE_CHANGE_INCOMPLETE

**对比旧方案（不修复的情况）：**
- 旧方案 Phase 1 会写新 capacity → SlotBOffset = 4096+4MB → Slot B 实际数据在 2MiB+4096 → **定位失败** ❌

#### 5.3.3 extend 场景丢失写入的权衡

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
| Phase 2 写入旧布局位置 | ❌ | 旧容量不够（否则不需要 extend） |
| 四阶段写入（Phase 2.5 更新布局） | ❌ | Phase 2.5 崩溃后回到同样问题 |
| Write Ahead Log | ❌ | 过度设计，复杂度与收益不对等 |

**结论：接受此场景下的行为，三阶段足够。**

### 5.4 Header 字段变更对照表

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
| Checksum | 重算 | 重算 |

---

## 6. 读取与恢复流程

### 6.1 读取主流程

```
read_metadata(lv_path, lv_size):

  1. 以 O_DIRECT | O_SYNC 只读打开 LV
  2. 读 Header Block (512B)
  3. 反序列化 + 校验 Header（magic、version、checksum）

  4. 如果 Header 有效：
       从 Header 直接读取 Slot 定位信息：
         slot_a_off = Header.SlotAOffset     ← 显式读取，不计算
         slot_a_cap = Header.SlotACapacity
         slot_b_off = Header.SlotBOffset     ← 显式读取，不计算
         slot_b_cap = Header.SlotBCapacity

       根据 PendingOp 分支 → Flow A / B / C

  5. 如果 Header 无效：
       → 进入恢复流程（§6.3）
```

### 6.2 三种读取分支

#### 6.2.1 Flow A — PendingOp = 0 (正常)

```
读 Active Slot → 校验通过 → 返回 OK + payload

如果 Active Slot 校验失败：
  读 Inactive Slot（仅用于诊断）
  如果 Inactive 有效：
    → CORRUPTED + 提示"Active 损坏，Inactive 有效但可能是旧数据"
    → repair_action: 切换 Active 或 full-refresh
  如果 Inactive 也无效：
    → CORRUPTED + "两个 Slot 均损坏"
    → repair_action: full-refresh
```

#### 6.2.2 Flow B — PendingOp = 1 (CONFIG_UPDATE 中断)

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

#### 6.2.3 Flow C — PendingOp = 2 (STORAGE_CHANGE 中断)

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

#### 6.2.4 ReadResult 状态语义

| Status | payload | is_usable() | 调用方行为 |
|--------|---------|-------------|-----------|
| OK | ✅ 有效 | True | 正常使用 |
| NEED_REPAIR | ✅ 有效 | True | 使用数据 + 触发后台 repair |
| RECOVERED | ✅ 有效 | True | 使用数据 + 触发 Header 重建 |
| STORAGE_CHANGE_INCOMPLETE | ⚠️ stale 数据 | **False** | **禁止注册 VM**，必须 full-refresh |
| CORRUPTED | ❌ 无 | False | 必须 full-refresh |

### 6.3 Header 损坏恢复流程

当 Header 校验失败（magic 错误、checksum 不匹配、version 不认识）时，进入分层恢复。

#### 6.3.1 恢复层次

```
Layer 1: Raw Header 字段提取
  │  Header 512B = 单扇区，即使 Checksum 坏了，字段可能仍可读
  │  尝试提取 ActiveSlot、SlotAOffset、SlotBOffset 等
  │  比无 Offset 字段时多了直接定位信息，恢复成功率更高
  │
  ▼
Layer 2: 布局推算
  │  用 _calculate_slot_layout(lv_size) 从当前 LV 大小推算 Offset/Capacity
  │  在推算位置尝试读两个 Slot
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

#### 6.3.2 恢复时的 Slot 选择策略

当找到两个有效 Slot 时：

```
优先级：
  1. Raw Header 中的 ActiveSlot hint（如果可提取）→ 使用 hint 指向的 Slot
  2. 无 hint → 使用 SeqNum 更高的 Slot（最后写入的数据更新）
  3. 只有一个有效 → 使用该 Slot
  4. 都无效 → CORRUPTED

注意：恢复路径使用 relaxed 校验模式
  - 不校验 SlotCapacity（因为传入的 capacity 可能是推算的，与 Slot 自描述不同）
  - 依赖 Checksum 作为最终数据完整性裁判
```

#### 6.3.3 Layer 1 详细逻辑

```
读取 Header 原始 512B 数据

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

**Layer 1 相比旧方案的改进：** 旧方案 Header 中没有 SlotBOffset，只能从 SlotACapacity 间接推算。新方案 Header 显式存储 SlotAOffset + SlotBOffset，raw 提取后直接可用，减少一步间接计算的出错风险。

### 6.4 Slot 读取优化

#### 6.4.1 一次读优化

```
optimistic_read_size = min(slot_capacity, 1MB)

第一次读: 从 slot_offset 读 optimistic_read_size
  → 大多数情况下 payload < 1MB，一次读取完成

如果 payload + header + checksum > optimistic_read_size:
  第二次读: 从 slot_offset 读 aligned_up(total_needed)
  → 仅在极大 payload 时触发
```

#### 6.4.2 strict vs relaxed 校验

| 校验项 | 正常路径 (strict) | 恢复路径 (relaxed) |
|--------|-------------------|-------------------|
| Magic == ZSDT | ✅ | ✅ |
| SlotOffset == expected | ✅ | ✅ |
| SlotCapacity == expected | ✅ | ❌ 跳过 |
| PayloadLen 范围合理 | ✅ | ✅ |
| SHA-256 Checksum | ✅ | ✅ |

---

## 7. Repair 与 Full-Refresh

### 7.1 pending_op 的性质

| PendingOp | 含义 | 旧数据安全性 | 可否简单清除 |
|-----------|------|-------------|-------------|
| 0 | 无操作 | — | — |
| 1 (CONFIG_UPDATE) | VM 配置变更中断 | ✅ 旧配置安全可用 | ✅ 可以 |
| 2 (STORAGE_CHANGE) | 存储拓扑变更中断 | ❌ 旧拓扑与实际不符 | ❌ **绝不可以** |

**核心区别：** CONFIG_UPDATE 的旧数据"过时但安全"，STORAGE_CHANGE 的旧数据"过时且危险"。

**pending_op 语义说明**：

- **普通配置更新流程**：用户 API (改CPU/内存) → DB 更新成功 → 写入元数据 (pending=1)
- **存储变更更新流程**：用户 API (创建快照) → 存储操作完成 (快照已创建) → 写入元数据 (pending=2)

| pending 值 | 含义 | 写入中断的后果 |
|-----------|------|--------------|
| 0 | 空闲，上次写入已完成 | — |
| 1 | 正在写入普通配置变更的元数据 | 丢失一次配置更新，可接受，能恢复 |
| 2 | 正在写入存储变更后的元数据（存储上已有新快照/卷） | 存储上有新数据，但元数据没记录！危险！不能用于注册 VM |

### 7.2 repair_pending_op 策略

#### 7.2.1 CONFIG_UPDATE (pending_op = 1)

```
读取 Header → 确认 pending_op = 1

计算 target_slot 定位信息（使用 Header 中的旧布局）
读取 Target Slot

Case A: Target 有效 且 SeqNum == Header.WriteSequence
  → Phase 2 已完成，只需完成 Phase 3
  → 写入新 Header:
      ActiveSlot     = target_slot
      PendingOp      = 0
      WriteSequence  = 保持
      布局字段        = 保持旧值（因为 Phase 1 没更新布局）
      LastUpdateTime = now()
  → 返回 repaired=True, "Completed Phase 3"

Case B: Target 无效
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

#### 7.2.2 STORAGE_CHANGE (pending_op = 2)

```
读取 Header → 确认 pending_op = 2

计算 target_slot 定位信息（使用 Header 中的旧布局）
读取 Target Slot

Case A: Target 有效 且 SeqNum == Header.WriteSequence
  → Phase 2 已完成，可以安全完成 Phase 3
  → 写入新 Header:
      ActiveSlot     = target_slot
      PendingOp      = 0
      WriteSequence  = 保持
      布局字段        = 保持旧值
      LastUpdateTime = now()
  → 返回 repaired=True, "Completed Phase 3 for storage change"

Case B: Target 无效
  → Phase 2 未完成
  → 旧 Active Slot 中的元数据不反映当前存储状态
  → ⚠️ 不清除 PendingOp ← 关键决策
  → 返回 repaired=False,
        error="STORAGE_CHANGE pending, target data lost.
               Metadata is stale. Must execute full-refresh
               from database to rebuild metadata."
```

#### 7.2.3 为什么 STORAGE_CHANGE 不能简单清除 PendingOp

```
如果清除 pending_op:
  Header 变为: pending=0, ActiveSlot=旧
  后续 read_metadata → 返回 OK + 旧 payload
  调用方认为数据有效 → 用旧拓扑注册 VM

  但实际存储状态已变更（如：快照已创建/删除、卷已扩容）
  旧拓扑 ≠ 当前存储 → VM 挂载错误的快照链
  → 数据损坏或丢失
```

**PendingOp=2 是一个"脏标记"**：它的存在持续提醒系统"存储状态与元数据不一致"。只有两种方式可以消除这个标记：

1. **找到有效 Target 完成 Phase 3** — 新元数据反映了存储变更，安全
2. **Full-refresh 写入全新元数据** — 从数据库重建完整拓扑，覆盖整个 Header

### 7.3 Full-Refresh 机制

#### 7.3.1 触发条件

| 场景 | 触发方 |
|------|--------|
| STORAGE_CHANGE_INCOMPLETE | management plane 检测到后主动触发 |
| CORRUPTED（两个 Slot 都损坏） | management plane 检测到后主动触发 |
| repair_pending_op 返回 repaired=False | management plane 收到失败回调后触发 |
| 管理员手动触发 | 运维命令 |

#### 7.3.2 执行方式

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

#### 7.3.3 Full-refresh 使用 STORAGE_CHANGE(2) 的理由

Full-refresh 始终使用 STORAGE_CHANGE(2)，因为：

- Full-refresh 由控制面触发，控制面知道这是全量刷新操作，显式指定 `storageStructureChange=true` → op_type=2
- 这自然解决了"full-refresh Phase 1 覆盖脏标记"问题：新的 PendingOp=2 与旧的语义一致
- 不需要引入新的 OP_FULL_REFRESH (3)

#### 7.3.4 Full-refresh 中断的场景

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

### 7.4 操作类型决策机制

> **op_type 由控制面指定**：管理层面根据 `@MetadataImpact` 注解确定 op_type，通过 `storageStructureChange` 字段传递给 Agent。

管理层面调用 `writeMetadata(payload, storageStructureChange)` 时显式指定 op_type。Agent 端直接使用该值：

- `storageStructureChange = false` → `op_type = CONFIG_UPDATE (1)` → `PendingOp = 1`
- `storageStructureChange = true` → `op_type = STORAGE_CHANGE (2)` → `PendingOp = 2`

**控制面决策规则**：
- `@MetadataImpact(CONFIG)` 标注的 API（CPU/内存/标签等变更）→ `storageStructureChange = false`
- `@MetadataImpact(STORAGE)` 标注的 API（磁盘挂载/卸载、快照创建/删除等）→ `storageStructureChange = true`
- Full-refresh / 首次写入 → `storageStructureChange = true`
- 多次 `markDirty` 合并时，`storageStructureChange` 采用 OR 升级策略（任一为 true 则结果为 true）

**好处**：
- 控制面对 op_type 拥有完整语义信息（知道哪个 API 触发了变更）
- Agent 无需读取旧 payload 做 diff，减少一次 I/O
- `VmMetadataDirtyVO` 记录 `storageStructureChange` 字段，Poller 批量处理时直接使用

**对 local/NFS 的影响**：local/NFS 不使用 op_type（JSON atomic rename 无中间状态），`storageStructureChange` 仅在 sblk 场景下转换为 PendingOp 值。

### 7.5 完整状态转换图

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

---

## 8. LV 管理与扩容

### 8.1 LV 命名规范

```
格式:  {vm_uuid}_vmmeta
示例:  a1b2c3d4e5f6_vmmeta
路径:  /dev/{vg_uuid}/{vm_uuid}_vmmeta
```

扫描规则：遍历 VG 中所有 LV，`lv_name.endswith('_vmmeta')` 即为元数据 LV。

### 8.2 LV 大小参数

| 参数 | 值 | 说明 |
|------|-----|------|
| 初始大小 | 4 MB | 足够绝大多数 VM 配置 |
| 最大大小 | 64 MB | 防止单 VM 元数据占用过多空间 |
| 对齐粒度 | 4096 B (ALIGNMENT) | 满足 O_DIRECT 对齐要求 |

### 8.3 LV 内部空间分配

```
calculate_slot_layout(lv_size):

  header_reserved = ALIGNMENT (4096 B)     ← Header 512B + padding to 4KB
  available       = lv_size - header_reserved
  slot_capacity   = (available / 2) 向下对齐到 ALIGNMENT

  slot_a_offset   = header_reserved        ← 固定 4096
  slot_a_capacity = slot_capacity
  slot_b_offset   = header_reserved + slot_capacity
  slot_b_capacity = slot_capacity

示例 (4MB LV):
  header_reserved = 4096
  available       = 4194304 - 4096 = 4190208
  slot_capacity   = (4190208 / 2) 对齐 = 2093056  (≈ 2044 KB)
  slot_a_offset   = 4096
  slot_b_offset   = 4096 + 2093056 = 2097152
```

Slot 最大 payload = slot_capacity - 36 (SlotHeader) - 32 (Checksum) = slot_capacity - 68

### 8.4 阶梯扩容策略

当 payload 超出当前 Slot 容量时触发 LV 扩容。

#### 8.4.1 扩容步长

| 当前 LV 大小 | 步长 |
|-------------|------|
| < 8 MB | 2 MB |
| 8 MB ~ 16 MB | 4 MB |
| 16 MB ~ 32 MB | 8 MB |
| > 32 MB | 16 MB |

#### 8.4.2 阶梯设计理由

- 小 LV 用小步长：避免浪费（大多数 VM 的元数据在 4MB 内就够了）
- 大 LV 用大步长：减少扩容次数（快照链很长的 VM 需要更多空间）
- 最大 64MB 上限：超过说明 VM 快照/卷数量异常，应在管理层面限制

#### 8.4.3 扩容计算示例

```
场景: 当前 LV=4MB, 需要 slot 容量 3MB

required_lv = ALIGNMENT + 2 * align_up(3MB + 68B) ≈ 6MB + 4KB
当前 4MB < required 6MB

step 1: 4MB + 2MB = 6MB → 仍 < 6MB+4KB
step 2: 6MB + 2MB = 8MB → 满足
→ extend LV to 8MB
```

#### 8.4.4 扩容时机

```
write_metadata() 中:

  required = SLOT_HEADER_SIZE + len(payload) + CHECKSUM_SIZE   (= 36 + N + 32)
  target_cap = Header 中 target slot 的 capacity

  if required > target_cap:
    min_lv = ALIGNMENT + 2 * align_up(required, ALIGNMENT)
    new_lv = calculate_extend_size(current_lv_size, min_lv)
    lv_extend_func(new_lv)
    重新计算 new_layout
```

#### 8.4.5 扩容与三阶段写入的交互

```
关键：lvextend 后必须关闭并重新打开 fd
  → 确保内核重新读取块设备大小，新增空间对后续 pwrite 可见
  → close(fd) → fd = open(lv_path, O_RDWR | O_DIRECT | O_SYNC)

布局更新时序：
  扩容后计算 new_layout（新的 offset/capacity）
  Phase 1: Header 中 布局字段 = 旧值（不更新）
  Phase 2: payload 写入 new_layout 的 target 位置
  Phase 3: Header 中 布局字段 = new_layout（此时更新）

  崩溃安全：见 §5.3.2
```

#### 8.4.6 容量超限处理

```
如果 required_lv > MAX_LV_SIZE (64MB):
  → 抛出异常
  → 提示 "VM 元数据超过 64MB 上限，可能快照/卷数量异常"
  → 管理层面应限制：
      - 单 VM 快照数量上限
      - 定期清理过期快照
      - 合并快照链
```

### 8.5 LV 生命周期

#### 8.5.1 LV 初始化

> **设计变更**：LV 初始化时同时写入 Header + 空 Slot A（`payload="{}"`），并执行 O_DIRECT sanity check。

```python
def initialize_metadata_lv(lv_path, lv_size):
    fd = os.open(lv_path, os.O_RDWR | os.O_DIRECT | os.O_SYNC)
    try:
        # Step 0: O_DIRECT sanity check
        _io_sanity_check(fd)

        layout = calculate_slot_layout(lv_size)

        # Step 1: Build empty payload Slot A
        empty_payload = b'{}'
        slot_a = build_slot(
            magic=SLOT_MAGIC,
            seq_num=1,
            slot_offset=layout.slot_a_offset,
            slot_capacity=layout.slot_a_capacity,
            payload=empty_payload
        )

        # Step 2: Write Slot A
        write_aligned(fd, layout.slot_a_offset, slot_a)

        # Step 3: Write Header (ActiveSlot=0, WriteSequence=1, PendingOp=0)
        header = build_header(
            active_slot=0, pending_op=0, write_sequence=1,
            slot_a_offset=layout.slot_a_offset,
            slot_a_capacity=layout.slot_a_capacity,
            slot_b_offset=layout.slot_b_offset,
            slot_b_capacity=layout.slot_b_capacity,
            last_update_time=0, schema_version=0
        )
        write_aligned(fd, 0, header)
    finally:
        os.close(fd)
```

**O_DIRECT sanity check**：

```python
def _io_sanity_check(fd):
    """Verify O_DIRECT I/O path works correctly.

    注意：sanity check 将测试数据写入 offset 0，后续 initialize_metadata_lv()
    会在同一位置写入正式 Header，自然覆盖测试数据。如果 Header 写入失败，
    offset 0 处残留测试数据（Magic ≠ 0x5A534D54），读取时将进入恢复流程
    （预期行为：初始化未完成 = 无有效元数据）。
    """
    test_data = b'ZSMT_IO_CHECK' + b'\x00' * (512 - 13)
    with AlignedBuffer(512) as buf:
        buf.fill(test_data)
        buf.pwrite(fd, 0)
    with AlignedBuffer(512) as buf:
        buf.pread(fd, 0)
        if buf.read(13) != b'ZSMT_IO_CHECK':
            raise MetadataIOError("O_DIRECT sanity check failed on %s" % lv_path)
```

- sanity check 失败 → 抛异常 → 管理层面将此 PS 标记为"不支持元数据" → 该 PS 上所有 VM 静默跳过元数据写入
- 首次 `read_metadata()` → 返回 `OK` with `payload="{}"`（而非 `CORRUPTED`），避免首次读取返回 CORRUPTED 与真正损坏混淆

> **前提条件**：sblk metadata 要求底层块设备支持 512B 扇区原子写，这与 LVM 自身的元数据更新机制使用同一保证。
> 如果 LVM 能在该存储上正常工作，则此前提已满足。

#### 8.5.2 LV 删除

```
delete_metadata(lv_path, lv_delete_func):
  直接调用 lv_delete_func(lv_path) 删除整个 LV
  无需清理内部数据
```

#### 8.5.3 LV 扫描

```
scan_metadata_lvs(vg_path, lv_list_func):
  遍历 VG 中所有 LV
  筛选 lv_name.endswith('_vmmeta')
  返回 [{vm_uuid, lv_path, lv_size}, ...]
```

### 8.6 健康检查

```
get_metadata_status(lv_path):
  只读打开 LV → 读 Header 512B → 校验 → 返回摘要

  返回值:
    {
      valid:           bool
      header_version:  int
      active_slot:     int
      pending_op:      int
      write_sequence:  int
      slot_a_offset:   int
      slot_a_capacity: int
      slot_b_offset:   int
      slot_b_capacity: int
      last_update_time: int
      schema_version:  int
    }

  用途：
    - 运维巡检
    - 监控告警（pending_op != 0 持续时间过长）
    - 诊断工具展示
```

---

## 9. I/O 与字节序技术细节

### 9.1 字节序

所有多字节整数字段统一使用**大端序（Big Endian）**。Python 2 中使用 `struct.pack('>I', magic)` / `struct.pack('>H', version)` / `struct.pack('>Q', seq_num)` 等。

选择大端序的理由：

- 大端序是网络字节序，是跨平台数据交换的惯例
- Magic Number `0x5A534D54` 在大端序下直接对应 ASCII "ZSMT"，便于 hexdump 调试
- LVM 元数据本身也使用大端序

### 9.2 SHA-256 输出格式

使用 **32 字节二进制**（非 64 字符十六进制字符串）。在 Python 2 中使用 `hashlib.sha256(data).digest()` 得到 32 字节 bytes。

### 9.3 O_DIRECT 与扇区原子写

**512B 扇区原子写在 LVM + 分布式存储场景下的可靠性：**

| 存储层 | 保证 |
|--------|------|
| 本地 SCSI/SATA/NVMe 磁盘 | 单扇区（512B 或 4KB）原子写是 ATA/SCSI 标准保证 |
| LVM Logical Volume | LV 底层最终映射到物理设备的扇区，LVM 层不会拆分对齐的扇区写入 |
| SAN (iSCSI/FC) | SCSI 协议保证单扇区原子写 |
| 分布式块存储（如 sblk 底层） | 取决于具体实现，但通常遵循块设备语义 |

**前提条件：**
- 写入对齐到扇区边界（Header 从 offset 0 开始，天然对齐）
- 使用 O_DIRECT 或 O_SYNC 确保不被 page cache 合并/拆分

**所有 sblk 元数据读写统一使用 `O_DIRECT | O_SYNC`**，包括 Header 和 Slot。理由：

- sblk 是共享块设备，多节点可能访问同一 LV，page cache 会导致不一致
- 元数据操作频率低（每次 API 后一次），性能开销可忽略
- 代码路径统一，减少 bug 风险
- 10MB O_DIRECT 顺序写入延迟：SSD 场景约 50ms，SAN 场景约 200ms，可接受

### 9.4 O_DIRECT 内存对齐

O_DIRECT 要求用户态 buffer 地址对齐到逻辑扇区大小（通常 512B）。`ctypes.create_string_buffer` 分配的内存**不保证**特定对齐。

**解决方案**：使用 `posix_memalign` + `ctypes` 封装为 `AlignedBuffer` 类。写入时 data 长度必须是 512B 的倍数。

```python
import ctypes
import os

_libc = ctypes.CDLL('libc.so.6', use_errno=True)

class AlignedBuffer(object):
    """Page-aligned buffer for O_DIRECT I/O. Use as context manager."""

    def __init__(self, size, alignment=4096):
        self._alignment = alignment
        self._size = ((size + alignment - 1) // alignment) * alignment
        self._ptr = ctypes.c_void_p()
        ret = _libc.posix_memalign(
            ctypes.byref(self._ptr), alignment, self._size)
        if ret != 0:
            raise OSError(ret, "posix_memalign failed")
        # Zero-fill
        ctypes.memset(self._ptr, 0, self._size)

    def fill(self, data, offset=0):
        """Copy data into buffer at given offset."""
        ctypes.memmove(self._ptr.value + offset, data, len(data))

    def read(self, length, offset=0):
        """Read bytes from buffer."""
        return ctypes.string_at(self._ptr.value + offset, length)

    def pwrite(self, fd, file_offset):
        """Write buffer contents to fd at file_offset using pwrite."""
        ret = _libc.pwrite(fd, self._ptr, self._size,
                           ctypes.c_longlong(file_offset))
        if ret < 0:
            errno = ctypes.get_errno()
            raise OSError(errno, "pwrite failed: " + os.strerror(errno))

    def pread(self, fd, file_offset):
        """Read from fd at file_offset into buffer using pread."""
        ret = _libc.pread(fd, self._ptr, self._size,
                          ctypes.c_longlong(file_offset))
        if ret < 0:
            errno = ctypes.get_errno()
            raise OSError(errno, "pread failed: " + os.strerror(errno))

    def close(self):
        if self._ptr.value:
            _libc.free(self._ptr)
            self._ptr = ctypes.c_void_p()

    def __enter__(self):
        return self

    def __exit__(self, *args):
        self.close()

    def __del__(self):
        self.close()
```

**使用示例**：

```python
# 写入 Header (512B)
with AlignedBuffer(512) as buf:
    buf.fill(header_bytes)
    buf.pwrite(fd, 0)

# 读取 Slot (up to 1MB optimistic)
with AlignedBuffer(1 * 1024 * 1024) as buf:
    buf.pread(fd, slot_offset)
    data = buf.read(expected_size)

# 写入 Slot — 构造函数自动将 size 向上对齐到 alignment(4096)
# 注意：pwrite 始终写入 self._size（对齐后）字节，创建时应精确传入所需大小
slot_total = SLOT_HEADER_SIZE + len(payload) + CHECKSUM_SIZE  # 36 + N + 32
with AlignedBuffer(slot_total) as buf:  # e.g. 36+1000+32=1068 → 自动对齐为 4096
    buf.fill(slot_bytes)
    buf.pwrite(fd, slot_offset)
```

### 9.5 文件锁

**sblk 不使用文件锁**：共享块设备上 `fcntl.flock` 语义取决于具体实现（device-mapper + cluster），不可靠。sblk 场景的并发保护完全依赖管理平面的四层串行化机制（见 Part 2 §3.1）。即使毫秒级窗口的并发写入，因全量覆盖写语义，后者覆盖前者，结果依然正确（最终一致性）。无需引入额外分布式锁机制。

**local/NFS 使用 flock 作为 defense-in-depth**：`fcntl.flock(fd, LOCK_EX | LOCK_NB)` 在本地文件系统和 NFS 上语义可靠，作为额外安全网防御编程错误或异常并发。`LOCK_NB` 非阻塞：获取失败立即报错（不应发生的情况）。
