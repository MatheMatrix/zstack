# VM 元数据 — sblk 二进制布局

## 1. Header Block (4096 Bytes)

Header 大小等于 O_DIRECT ALIGNMENT（4KB），单次对齐 I/O 即可完成读写。内部分为三个区域：

| 区域 | 偏移范围 | 用途 |
|------|---------|------|
| 控制区 | [0, 96) | 崩溃恢复关键字段 + ControlChecksum |
| VM 摘要区 | [96, 928) | 扫描优化字段 + SummaryChecksum |
| 预留区 | [928, 4096) | 未来扩展，零填充 |

### 1.1 字段定义

```
═══════════════════════════════════════════════════════════════════════════════
控制区 [0, 96)  —  崩溃恢复关键字段 + ControlChecksum
═══════════════════════════════════════════════════════════════════════════════
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
56      8B     SchemaVersion     uint64 BE   Payload JSON schema 版本（扩容后 20 bit/段）
──────
64B     (以上为 ControlChecksum 覆盖范围)
──────
64      32B    ControlChecksum   raw bytes   SHA-256(bytes[0:64])

═══════════════════════════════════════════════════════════════════════════════
VM 摘要区 [96, 928)  —  扫描优化字段 + SummaryChecksum
═══════════════════════════════════════════════════════════════════════════════
96      1B     VmCategory        uint8       VM 类别（0=REGULAR, 1=TEMPLATE, 2=TEMPLATE_CACHE）
97      32B    VmUuid            ASCII       VM UUID hex 字符串（32 字符，无连字符）
129     2B     VmNameLen         uint16 BE   VmName 实际字节长度（0 表示未设置）
131     765B   VmName            UTF-8       VM 名称（varchar(255)×utf8，最大 765 字节）
──────
896B    (以上 VM 摘要字段，[96:896) 为 SummaryChecksum 覆盖范围)
──────
896     32B    SummaryChecksum   raw bytes   SHA-256(bytes[96:896])

═══════════════════════════════════════════════════════════════════════════════
预留区 [928, 4096)  —  未来扩展
═══════════════════════════════════════════════════════════════════════════════
928     3168B  Reserved          zero        零填充，未来扩展使用

══════
Total:  4096B
```

### 1.2 字段设计理由

**Magic (4B, offset 0)**
- `0x5A534D54` = ASCII "ZSMT" (ZStack Metadata)
- hexdump 一眼可辨识
- brute-force 恢复时每个 4KB 对齐位置只需读前 4 字节判断

**HeaderVersion (2B, offset 4)**
- 二进制布局版本，只在 Header/Slot 结构变更时递增
- uint16 足够（不可能有 65535 次布局变更）
- 与 SchemaVersion 职责分离：HeaderVersion 管"怎么读"，SchemaVersion 管"读出的 JSON 怎么解释"

**ActiveSlot (1B, offset 6) + PendingOp (1B, offset 7)**
- 各 1B 足够（`PendingOp` 当前已定义取值 0~2）
- 不用 bit flags：语义清晰，调试简单
- 紧凑排列，在同一个 8B 对齐块内
- 前向兼容：预留值域 **3~255**。读取端遇到未知值时按 `STORAGE_CHANGE` 语义处理（保守路径），不做 `CONFIG_UPDATE` 快速清理，避免误判导致数据丢失

**PendingOp 前向兼容约束（Q4-6）**

| 取值 | 语义 | 处理策略 |
|------|------|----------|
| 0 | NONE | 正常读取流程 |
| 1 | CONFIG_UPDATE | 可按配置变更修复策略清理 PendingOp |
| 2 | STORAGE_CHANGE | 走存储变更保守修复路径 |
| 3~255（未知） | 未来扩展保留值 | **按 STORAGE_CHANGE 处理**（保守回退） |

> 设计理由：未知 PendingOp 若误按 CONFIG_UPDATE 清理，可能在扩容/布局切换未完成场景提前丢弃恢复机会；按 STORAGE_CHANGE 保守处理最多增加一次 full-refresh，不会扩大数据风险。

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

**SchemaVersion (8B, offset 56)**
- Payload JSON 的业务 schema 版本，uint64
- 读 Header 即可判断是否认识该版本，无需解码整个 Slot
- **编码规则**：将 `dbf.getDbVersion()` 返回的数据库版本字符串（如 `"4.10.12"`）解析为数字组件后压缩为 uint64：**`(A << 40) | (B << 20) | C`**。例如 `"4.10.12"` → `(4 << 40) | (10 << 20) | 12`。解码：`A = v >> 40`，`B = (v >> 20) & 0xFFFFF`，`C = v & 0xFFFFF`。每个组件 20 bit，最大支持 **1,048,575**
- Header 中的 SchemaVersion 与 DTO JSON 中的 `schemaVersion` 字符串（`dbf.getDbVersion()`）是同一语义的不同表示，写入时编码、读取时解码
- 扩容理由：原 uint32 每段 10 bit（最大 1023），uint64 每段 20 bit，容量扩大一倍（bit 数），版本号空间充裕
- **格式合约**：`dbf.getDbVersion()` 返回值必须匹配 `^\d+\.\d+\.\d+$`，每段 ≤ 1,048,575。不匹配时拒绝编码并记录 ERROR 日志
- **Python 2 注意**：位移操作需用 long 字面量避免溢出，如 `4L << 40`。或统一使用 `int()` 包裹：`int(a) << 40 | int(b) << 20 | int(c)`。Python 2 的 `int` 在超过 `sys.maxint` 时自动提升为 `long`，但显式使用 `long()` 更安全
- **字段迁移说明**：原控制区 Reserved(4B, offset 60) 的位置被 SchemaVersion 扩展吸收。控制区内的预留空间功能由 Header 预留区 [928, 4096)（3168B）承担，空间充裕

**ControlChecksum (32B, offset 64)**
- SHA-256 of bytes[0:64]
- 覆盖 Checksum 之前的所有控制字段（Magic 到 SchemaVersion，共 64B）
- **不覆盖 VM 摘要区和预留区**：职责分离，控制区和摘要区各自独立校验
- 校验逻辑：`sha256(block[0:64]) == block[64:96]`

**VmCategory (1B, offset 96)**
- VM Business 类别，用于批量扫描时快速分类筛选
- 0=REGULAR（普通虚拟机，含链式克隆子 VM），1=TEMPLATE（模板 VM），2=TEMPLATE_CACHE（缓存 VM）
- 旧版本写入的 Header 此处为 0，解读为 REGULAR（向后兼容）
- 枚举值与 Java 侧 `VmMetadataCategory` 一致（见 [Part 1a §2.2](vm-metadata-01a-数据模型与序列化.md#22-vmmetadatacategory-枚举)）

**VmUuid (32B, offset 97)**
- VM UUID 的 hex 字符串表示（32 字符 ASCII，无连字符）
- 扫描时无需解码 Slot 即可按 UUID 检索
- 固定 32B，无需长度前缀

**VmNameLen (2B, offset 129)**
- VmName 字段的实际 UTF-8 字节长度
- uint16 BE，最大 65535，远超 765B 上限
- 0 表示未设置（旧版本兼容：旧 Header 此处为 0，意为"名称不可用，需读 Slot"）

**VmName (765B, offset 131)**
- VM 名称 UTF-8 编码，MySQL `varchar(255)` + `charset utf8` 最大 765 字节
- **截断规则**：若 VM 名称的 UTF-8 字节数超出 765B，截断到 765B（在 UTF-8 字符边界截断，避免截断多字节字符的中间）。截断不影响 Slot 中的完整 JSON 中的 VM 名称
- 尾部未使用空间零填充

**SummaryChecksum (32B, offset 896)**
- SHA-256 of bytes[96:896]
- 独立于 ControlChecksum，仅覆盖 VM 摘要字段
- 扫描时校验失败 → 摘要不可信 → 需读 Slot 获取 VM 信息（降级但不影响数据正确性）
- 与 ControlChecksum 分离的理由：控制区是崩溃恢复的核心，不能因为摘要区写入异常导致控制区被判为无效

**Reserved (3168B, offset 928)**
- 零填充至 4096B
- 未来扩展空间充裕：可增加大量新字段而无需再次扩容 Header

### 1.3 hexdump 示例

```
// 控制区 [0, 64)
00000000  5a 53 4d 54 00 01 00 01  00 00 00 00 00 00 00 2a  |ZSMT...........*|
          ^^^^^^^^^ ^^^^^ ^^ ^^    ^^^^^^^^^^^^^^^^^^^^^^^^^
          Magic     V=1  A=0 P=1   WriteSeq = 42

00000010  00 00 00 00 00 00 10 00  00 00 00 00 00 1f e0 00  |................|
          ^^^^^^^^^^^^^^^^^^^^^^^^^  ^^^^^^^^^^^^^^^^^^^^^^^^^
          SlotAOffset = 4096         SlotACapacity = 2088960

00000020  00 00 00 00 00 1f f0 00  00 00 00 00 00 1f e0 00  |................|
          ^^^^^^^^^^^^^^^^^^^^^^^^^  ^^^^^^^^^^^^^^^^^^^^^^^^^
          SlotBOffset = 2093056      SlotBCapacity = 2088960

00000030  00 00 01 8e 3a 5b c0 00  00 00 04 00 00 a0 00 0c  |....:...........|
          ^^^^^^^^^^^^^^^^^^^^^^^^^  ^^^^^^^^^^^^^^^^^^^^^^^^^
          LastUpdate=1709123456000   SchemaVersion="4.10.12"

// ControlChecksum [64, 96)
00000040  a1 b2 c3 d4 ... (32 bytes SHA-256 of [0:64]) ...  |................|

// VM 摘要区 [96, 896)
00000060  01 61 62 63 64 65 66 30  31 32 33 34 35 36 37 38  |.abcdef012345678|
          ^^ ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
          Cat=1  VmUuid (前 15B) = "abcdef0123456789..."

00000070  39 30 31 32 33 34 35 36  37 38 39 61 62 63 64 65  |901234567890abcde|
          ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
          VmUuid (后 17B) = "...901234567890abcde"

00000081  00 09 e6 b5 8b e8 af 95  56 4d 00 00 00 ...      |........VM......|
          ^^^^^ ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
          NameLen=9  VmName="测试VM" (UTF-8, 9 bytes)

          ... (VmName padding to offset 896) ...

// SummaryChecksum [896, 928)
00000380  f1 e2 d3 c4 ... (32 bytes SHA-256 of [96:896]) ...|................|

// 预留区 [928, 4096)
000003A0  00 00 00 00 ... (zero padding to 4096B) ...        |................|
```

### 1.4 版本兼容策略

```
读取时:
  if header_version > MAX_KNOWN_VERSION:
      → 拒绝解析，返回错误，提示升级软件

  if header_version == 1:
      → 用 V1 布局解析（当前方案）

  # 未来 V2 示例:
  if header_version == 2:
      → 控制区 Reserved 位置改为 CompressionType
      → 预留区 offset 928~935 分配给新字段
      → ControlChecksum 范围不变（仍 bytes[0:64]）
      → 新字段在预留区有各自独立校验或归入 SummaryChecksum
```

---

## 2. Slot 结构

Slot 是数据搬运工，职责单一：可靠地存取 payload、支持自描述恢复。

### 2.1 字段定义

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

> **注**：Checksum 中 `bytes[0:36+N]` 的偏移量相对于 **Slot 起始位置**（即 `SlotOffset`），非 LV 起始位置。实现时应从 SlotOffset 开始读取 `36+N` 字节作为 Checksum 输入。

### 2.2 字段说明

| 字段 | 设计理由 |
|------|---------|
| Magic | 标识 Slot 数据块，brute-force 恢复的入口条件 |
| SeqNum | 与 Header.WriteSequence 匹配来判断 Phase 2 是否完成 |
| SlotOffset | Header 损坏时的自描述定位；brute-force 时 `stored_offset == actual_offset` 是强校验 |
| SlotCapacity | 配合 SlotOffset 可重建布局；`SlotA.Offset + SlotA.Capacity` 可定位 SlotB |
| PayloadLen | 8B (uint64)，虽然实际不超过 32MB，但保持与其他字段统一的 8B 对齐 |
| Payload | 变长，元数据 DTO JSON（systemTags/resourceConfigs 字段为 per-Resource Base64 编码） |
| Checksum | 尾部放置，SHA-256 覆盖 SlotHeader + Payload 全部内容 |

### 2.3 Checksum 放尾部的理由

| 方案 | 优点 | 缺点 | 结论 |
|------|------|------|------|
| Checksum 在尾部（当前） | 写入自然流程；覆盖全部数据 | 需先读 PayloadLen 才知道 Checksum 位置 | (Y) 采用 |
| Checksum 在 Header 固定位置 | 固定偏移 | 不读 Payload 也无法校验，没有实际收益 | (N) |
| Header/Payload 双 Checksum | 可先验证 Header | 增加写入复杂度，1MB 优化读已覆盖大多数场景 | (N) |

### 2.4 校验清单

> 两种校验模式：正常路径 (strict) 用于常规读取，恢复路径 (relaxed) 用于 Header 损坏后的 Slot 恢复。

| 校验项 | strict（正常读取） | relaxed（恢复路径） |
|--------|-------------------|-------------------|
| Magic == 0x5A534454 | (Y) | (Y) |
| SlotOffset == expected | (Y) | (Y) |
| SlotCapacity == expected | (Y) | (N) 跳过（推算 capacity 可能不准） |
| PayloadLen 范围合理 | (Y) | (Y) |
| SHA-256 Checksum | (Y) | (Y) |

### 2.5 不做修改的候选项

| 候选改进 | 结论 | 理由 |
|---------|------|------|
| PayloadLen 缩为 4B | (N) 不改 | 只省 4B，破坏 8B 对齐 |
| 增加 SlotIndex (A/B 标识) | (N) 不改 | SeqNum 已够判断顺序，SlotIndex 冗余 |
| 增加 Slot 独立版本号 | (N) 不改 | Header 的 HeaderVersion 已管控全局布局版本 |

## 3. 约束与不変量

1. **控制区校验不変量**：`ControlChecksum = SHA-256(bytes[0:64])`，读取端必须先校验再使用控制字段。
2. **摘要区校验不変量**：`SummaryChecksum = SHA-256(bytes[96:896])`；校验失败只影响摘要可用性，不影响控制区有效性判断。
3. **PendingOp 安全不変量**：未知 `PendingOp`（3~255）必须按 `STORAGE_CHANGE` 保守处理，不得降级为配置类快速路径。
4. **Slot 自描述不変量**：`SlotOffset` 与 `SlotCapacity` 必须可用于 Header 损坏场景的恢复定位。
5. **兼容演进不変量**：新增字段优先使用 Header 预留区 [928,4096)，避免破坏既有控制区校验边界。
