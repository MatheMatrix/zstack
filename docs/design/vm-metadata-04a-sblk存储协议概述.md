# VM 元数据 — sblk 二进制存储协议概述

## 1. 术语表

| 术语 | 定义 |
|------|------|
| sblk | ZStack 共享块存储（Shared Block Storage），基于 LVM 的裸块设备 |
| LV | LVM Logical Volume，元数据持久化的最小存储单元 |
| Header Block | LV 头部 4KB 区域，存放控制信息与 VM 摘要 |
| Slot | 数据槽，存放完整的 payload（元数据 DTO JSON） |
| Active Slot | Header.ActiveSlot 指向的当前有效 Slot |
| Inactive Slot | 未被 ActiveSlot 指向的另一个 Slot，写入目标 |
| PendingOp | Header 中的操作意图标记，0=空闲 / 1=CONFIG_UPDATE / 2=STORAGE_CHANGE |
| WriteSequence | 单调递增写计数器，用于判读 Slot 数据是否属于最新一次写入 |
| ControlChecksum | SHA-256(Header[0:64])，覆盖崩溃恢复关键字段 |
| SummaryChecksum | SHA-256(Header[96:896])，覆盖 VM 摘要字段 |
| O_DIRECT | Linux 直接 I/O 标志，绕过 page cache |
| ALIGNMENT | 4096 字节，O_DIRECT I/O 对齐粒度 |
| op_type | 写入操作类型，由控制面 `@MetadataImpact` 注解决定 |
| Full-refresh | 从管理面数据库重建完整元数据并全量写入 LV |

---

## 2. 适用范围

本文档仅覆盖 **sblk（共享块存储）** 场景下的二进制存储协议。

| 场景 | 存储协议 | 并发控制 | 崩溃安全机制 |
|------|---------|---------|-------------|
| **sblk** | 本文档：二进制 Header + A/B Dual Slot | 管理面四层串行化 (Part 2 §3.1) | A/B Dual Slot + PendingOp |
| local/NFS | JSON 明文 + tmp + fsync + rename | flock (defense-in-depth) | 原子 rename |

> local/NFS 场景不使用 op_type、PendingOp 等概念（JSON atomic rename 无中间状态）。后文所有设计均仅针对 sblk。

---

## 3. 背景与动机

ZStack 共享块存储（sblk）场景下，VM 元数据需要持久化到 LVM Logical Volume 上。多个管理节点可能通过共享块设备并发访问同一 LV。

核心挑战：

- **无文件系统**：LV 是裸块设备，无法使用常规文件 I/O
- **共享访问**：多节点通过 O_DIRECT 绕过 page cache 直接读写
- **崩溃安全**：任意时刻断电或进程崩溃后，数据必须可恢复
- **空间受限**：LV 初始 4MB，最大 64MB，需高效利用

### 3.1 灾备接管 — A/B 双 Slot 的核心驱动力

> **脑裂不在设计范围内**：如果两个平台同时对同一 LV 执行写入操作，A/B Slot 不提供保护。预留 Header Reserved 区 8B 用于存储 `platformId`，未来可用于检测跨平台写入冲突。

除常规读写外，协议必须支持**跨平台灾备接管**场景：

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
| 元数据权威来源 | 管理面 DB | **LV 上的元数据** |
| LV 元数据角色 | DB 的副本/缓存 | **唯一的 VM 恢复来源** |
| 管理面 DB 可用？ | (Y) zsvA DB 可用 | (N) zsvA 故障，zsvB DB 无此 VM |
| Full-refresh 可行？ | (Y) 从 DB 重建 | (N) **无 DB 数据可重建** |

**核心问题**：存储侧复制是**块级别快照**，可能捕获到 LV 正在写入的中间状态。

单区覆盖写在此场景下的风险：

```
zsvA 正在写入: 已写入部分新数据，旧数据已被覆盖
此刻 sanA → sanB 块级复制发生
sanB 上的 LV: payload 损坏 + 旧数据不可恢复 + zsvB 无 DB → VM 不可恢复 (N)
```

A/B 双 Slot 的保证：

```
zsvA 正在写入: Phase 2 写入 Inactive Slot，Active Slot 未被触碰
此刻 sanA → sanB 块级复制发生
sanB 上的 LV: Active Slot 完整有效 → zsvB 读到旧元数据 → VM 可注册 (Y)
```

> **结论**：A/B Dual Slot 是能保证任意复制时刻都有可读数据的最简方案。协议复杂度是为灾备可靠性买单。

---

## 4. 设计目标

| 目标 | 要求 |
|------|------|
| 原子性 | 任意崩溃点数据不损坏，已提交数据不丢失 |
| 自描述 | Slot 自带位置信息，Header 损坏时仍可恢复 |
| 高效 I/O | O_DIRECT + O_SYNC，对齐到 4KB 页边界 |
| 简单可靠 | 纯二进制定长字段，无 JSON 解析开销 |
| 可观测 | hexdump 直接可读，状态可诊断 |
| 前向兼容 | HeaderVersion 管布局演进，SchemaVersion 管 payload 演进 |

---

## 5. 整体架构

LV 初始预分配 4MB 空间（虚拟机在正常使用场景下，元数据一般只有几十 KB）。直接以 Raw Data 存储 JSON 元数据，不格式化文件系统。采用 **预分配固定大小 LV + Raw Data 存储 + A/B 分区原子写** 方案，规避频繁创建/删除 LV 的性能问题。

```
LV Layout (e.g. 4MB)
┌──────────────┬────────────────────┬────────────────────┐
│ Header Block │      Slot A        │      Slot B        │
│   4KB        │   ~2MB             │   ~2MB             │
│ (4096B)      │                    │                    │
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

- **Header Block (4096B)**：控制信息 + VM 摘要信息，O_DIRECT 单次写入
- **Slot A / Slot B**：双槽交替写入，A/B 切换实现原子更新

### 5.1 已知 LV 大小集合（KNOWN_LV_SIZES）

为了支撑扩容后恢复与多布局回退，协议约束可识别的历史 LV 大小集合为：

```python
KNOWN_LV_SIZES = [4MB, 6MB, 8MB, 12MB, 16MB, 24MB, 32MB, 48MB, 64MB]
```

对应字节值（9 个固定值）：

```python
KNOWN_LV_SIZES = [
    4 * 1024 * 1024,
    6 * 1024 * 1024,
    8 * 1024 * 1024,
    12 * 1024 * 1024,
    16 * 1024 * 1024,
    24 * 1024 * 1024,
    32 * 1024 * 1024,
    48 * 1024 * 1024,
    64 * 1024 * 1024,
]
```

该集合与 Part 4e 的阶梯扩容规则保持一致，供 Part 4d 的 multi-layout 恢复穷举使用。

### 5.2 A/B Dual Slot 工作模式

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

> **PendingOp 恢复**：若写入在 Phase 1~Phase 2 之间崩溃（Header.PendingOp≠0），读取时触发 `repair_pending_op` 恢复流程，通过双布局尝试（old-layout → new-layout）定位 Target Slot 并完成 Phase 3。完整恢复逻辑详见 [Part 4d §4](vm-metadata-04d-sblk读取与恢复.md)。

### 5.3 版本管理

两个独立的版本号，职责分离：

| 版本号 | 位置 | 含义 | 何时递增 |
|--------|------|------|---------|
| HeaderVersion | Header Block | 二进制布局版本（字段偏移、大小、Checksum 算法） | 增删 Header/Slot 字段时 |
| SchemaVersion | Header Block | Payload JSON 业务 schema 版本 | Payload 中 JSON 字段增减时 |

读取策略：
- `HeaderVersion > MAX_KNOWN` → 拒绝解析，提示升级软件
- `SchemaVersion > MAX_KNOWN` → 可读出 payload，但提示部分字段可能无法识别

### 5.4 崩溃安全模型

> 本节为全文档共享的崩溃安全设计原则，Part 4c/4d/4e 中引用而不重复展开。

**核心声明**：
- **协议不依赖单次 4KB I/O 的原子性**。尽管当前主流 SSD/HDD 在扩区层面提供 512B~4KB 原子写入，但协议不将其作为安全假设。崩溃安全完全依赖 A/B Dual Slot 机制。
- **读取路径不依赖 LV 大小计算 Slot 位置**。Slot 定位信息从 Header 中显式读取（`SlotAOffset`/`SlotBOffset`），而非从 `lv_size` 计算。这保证了 LV 扩容后，旧 Header 中的偏移仍然有效。

**部分写入分析**（Header 4KB 写入中途崩溃）：

| 崩溃时刻 | 已写入字段 | 未写入字段 | 影响 |
|----------|----------|----------|------|
| Phase 1 写 Header 中途 | PendingOp 可能已写 | ActiveSlot 未变 | Active Slot 完整，数据安全 |
| Phase 3 写 Header 中途 | ActiveSlot 可能已切换 | ControlChecksum 未更新 | 校验失败 → 进入恢复流程 → 从 Slot 自描述恢复 |
| Phase 3 完全成功 | 所有字段已写 | — | 正常 |

> 即使单次 4KB 写入不原子，最坏情况是 Header 损坏 + ControlChecksum 不匹配，此时恢复流程（Part 4d §3）通过 Slot 自描述信息 + Checksum 找到有效数据。

**崩溃安全机制摘要**：

1. Header 为 4KB，通过单次 O_DIRECT + O_SYNC 写入
2. Phase 1 不切换 ActiveSlot → 崩溃后 Active Slot 定位信息完好
3. Phase 2 写入 Inactive Slot → Active Slot 数据不受影响
4. Phase 3 才切换 ActiveSlot + 更新布局 → 提交语义
5. 即使 Header 写入中途崩溃（部分字段更新），恢复流程通过 Slot 自描述 + Checksum 找到有效数据

**VM 摘要区降级**：摘要区 [96, 928) 仅用于扫描优化，写入中途崩溃 → SummaryChecksum 校验失败 → 降级读 Slot，不影响正确性。

### 5.5 Python 2 兼容性

当前 Agent 环境为 Python 2.7（与 ZStack KVM Agent 一致），代码按 Python 2 编写：

- `struct.pack/unpack` 处理大端序二进制
- `ctypes` 分配对齐内存缓冲区（O_DIRECT 要求）
- `buffer()` 实现零拷贝写入
- `hashlib.sha256` (Python 2.7+ 内置)
- Python 3 迁移随 Agent 整体迁移计划进行，不单独迁移

---

## 6. 文档导航

| 子文档 | 内容 | 典型读者 |
|--------|------|---------|
| [Part 4b — 二进制布局](vm-metadata-04b-sblk二进制布局.md) | Header Block 与 Slot 的字段定义、设计理由、hexdump 示例 | 协议实现者 |
| [Part 4c — 写入流程](vm-metadata-04c-sblk写入流程.md) | 三阶段原子写入、崩溃场景分析、状态转换图 | 协议实现者、CR 审查者 |
| [Part 4d — 读取与恢复](vm-metadata-04d-sblk读取与恢复.md) | 读取分支、Header 损坏恢复、`DEGRADED` 降级读取、multi-layout 修复与 Repair/Full-Refresh | 协议实现者、运维 |
| [Part 4e — 运维与 I/O 细节](vm-metadata-04e-sblk运维与IO.md) | LV 管理、扩容、初始化、健康检查、AlignedBuffer 代码 | Agent 开发者、运维 |
