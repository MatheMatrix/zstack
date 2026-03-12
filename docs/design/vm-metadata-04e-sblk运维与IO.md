# VM 元数据 — sblk 运维与 I/O 细节

## 1. LV 命名与扫描

### 1.1 命名规范

```
格式:  {vm_uuid}_vmmeta
示例:  a1b2c3d4e5f6_vmmeta
路径:  /dev/{vg_uuid}/{vm_uuid}_vmmeta
```

### 1.2 扫描规则

```
scan_metadata_lvs(vg_path, lv_list_func):
  遍历 VG 中所有 LV
  筛选 lv_name.endswith('_vmmeta')
  返回 [{vm_uuid, lv_path, lv_size}, ...]
```

**大规模扫描优化**（VM 数量 > 500 时建议启用）：

| 优化手段 | 说明 |
|----------|------|
| 仅读 Header 4KB | 扫描阶段只读 Header 获取 VM 摘要（`VmUuid`/`VmName`/`VmCategory`），不读 Slot payload，单次 I/O = 4KB |
| 并行 I/O | 多个 LV 的 Header 读取可并行执行（线程池并发度受 Agent 线程数控制，默认 10） |
| SummaryChecksum 降级 | 摘要校验失败时标记 `summary_valid=false`，不在扫描阶段触发 Slot 读取 |

---

## 2. LV 容量管理

### 2.1 基本参数

| 参数 | 值 | 说明 |
|------|-----|------|
| 初始大小 | 4 MB | 足够绝大多数 VM 配置 |
| 最大大小 | 64 MB | 防止单 VM 元数据占用过多空间 |
| 对齐粒度 | 4096 B (ALIGNMENT) | 满足 O_DIRECT 对齐要求 |

### 2.2 空间分配公式

```
calculate_slot_layout(lv_size):

  header_reserved = ALIGNMENT (4096 B)     ← Header Block = 4KB
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

### 2.3 阶梯扩容策略

当 payload 超出当前 Slot 容量时触发 LV 扩容。

#### 扩容步长

| 当前 LV 大小 | 步长 |
|-------------|------|
| < 8 MB | 2 MB |
| 8 MB ~ 16 MB | 4 MB |
| 16 MB ~ 32 MB | 8 MB |
| > 32 MB | 16 MB |

#### 设计理由

- 小 LV 用小步长：避免浪费（大多数 VM 的元数据在 4MB 内就够了）
- 大 LV 用大步长：减少扩容次数（快照链很长的 VM 需要更多空间）
- 最大 64MB 上限：超过说明 VM 快照/卷数量异常，应在管理层面限制

#### 计算示例

```
场景: 当前 LV=4MB, 需要 slot 容量 3MB

required_lv = ALIGNMENT + 2 * align_up(3MB + 68B) ≈ 6MB + 4KB
当前 4MB < required 6MB

step 1: 4MB + 2MB = 6MB → 仍 < 6MB+4KB
step 2: 6MB + 2MB = 8MB → 满足
→ extend LV to 8MB
```

### 2.4 扩容时机与交互

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

**扩容与三阶段写入的交互**（崩溃安全分析见 Part 4c §4.2）：

```
关键：lvextend 后必须关闭并重新打开 fd
  → 确保内核重新读取块设备大小，新增空间对后续 pwrite 可见
  → close(fd) → fd = open(lv_path, O_RDWR | O_DIRECT | O_SYNC)

布局更新时序：
  扩容后计算 new_layout（新的 offset/capacity）
  Phase 1: Header 中 布局字段 = 旧值（不更新）
  Phase 2: payload 写入 new_layout 的 target 位置
  Phase 3: Header 中 布局字段 = new_layout（此时更新）
```

### 2.5 容量超限处理

```
如果 required_lv > MAX_LV_SIZE (64MB):
  → 抛出异常
  → 提示 "VM 元数据超过 64MB 上限，可能快照/卷数量异常"
  → 管理层面应限制：
      - 单 VM 快照数量上限
      - 定期清理过期快照
      - 合并快照链
```

---

## 3. LV 生命周期

### 3.1 LV 初始化

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

        # Step 2.5: Clear Slot B region (zero-fill)
        # 确保初始化后 Slot B 为全零，避免残留数据干扰恢复流程判断
        zero_buf = b'\x00' * layout.slot_b_capacity
        write_aligned(fd, layout.slot_b_offset, zero_buf)

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
    test_data = b'ZSMT_IO_CHECK' + b'\x00' * (4096 - 13)
    with AlignedBuffer(4096) as buf:
        buf.fill(test_data)
        buf.pwrite(fd, 0)
    with AlignedBuffer(4096) as buf:
        buf.pread(fd, 0)
        if buf.read(13) != b'ZSMT_IO_CHECK':
            raise MetadataIOError("O_DIRECT sanity check failed on %s" % lv_path)
```

- sanity check 失败 → 抛异常 → 管理层面将此 PS 标记为"不支持元数据" → 该 PS 上所有 VM 静默跳过元数据写入
- 首次 `read_metadata()` → 返回 `OK` with `payload="{}"`（而非 `CORRUPTED`），避免首次读取返回 CORRUPTED 与真正损坏混淆

### 3.2 LV 删除

```
delete_metadata(lv_path, lv_delete_func):
  直接调用 lv_delete_func(lv_path) 删除整个 LV
  无需清理内部数据
```

---

## 4. 健康检查

```
get_metadata_status(lv_path):
  只读打开 LV → 读 Header 4KB → 校验 → 返回摘要

  返回值:
    {
      valid:              bool
      header_version:     int
      active_slot:        int
      pending_op:         int
      write_sequence:     int
      slot_a_offset:      int
      slot_a_capacity:    int
      slot_b_offset:      int
      slot_b_capacity:    int
      last_update_time:   int
      schema_version:     int
      vm_category:        int
      vm_uuid:            str
      vm_name:            str
      summary_valid:      bool    ← SummaryChecksum 校验结果
    }

  用途：
    - 运维巡检
    - 监控告警（pending_op != 0 持续时间过长）
    - 诊断工具展示
```

### 4.1 Layer 4 brute-force 扫描超时与日志（Q4-2）

当 Header/常规布局恢复失败后，Layer 4 进入按步长扫描 Slot Magic 的 brute-force 路径。为避免底层 I/O 异常导致长时间阻塞，增加**全局 30 秒超时**与启动日志：

```python
# 在 brute-force 扫描前
BRUTE_FORCE_TIMEOUT_SEC = 30
start_time = time.time()
log.info("Starting brute-force scan: LV size=%dMB, max_steps=%d", lv_size_mb, max_steps)

# 在每次 pread 循环中检查
if time.time() - start_time > BRUTE_FORCE_TIMEOUT_SEC:
    log.error("Brute-force scan timed out after %ds", BRUTE_FORCE_TIMEOUT_SEC)
    return CORRUPTED
```

实现约束：
- 该超时为**扫描全局超时**，不是单次 `pread` 超时。
- 超时后直接返回 `CORRUPTED`，由上层走 full-refresh / 人工介入流程。
- 日志中的 `lv_size_mb` 与 `max_steps` 必须在扫描开始时一次性打印，便于运维关联慢盘与异常设备。

---

## 5. I/O 技术细节

### 5.1 字节序

所有多字节整数字段统一使用**大端序（Big Endian）**。Python 2 中使用 `struct.pack('>I', magic)` / `struct.pack('>H', version)` / `struct.pack('>Q', seq_num)` 等。

选择大端序的理由：
- 大端序是网络字节序，跨平台数据交换的惯例
- Magic Number `0x5A534D54` 在大端序下直接对应 ASCII "ZSMT"，便于 hexdump 调试
- LVM 元数据本身也使用大端序

### 5.2 SHA-256 输出格式

使用 **32 字节二进制**（非 64 字符十六进制字符串）。Python 2 中 `hashlib.sha256(data).digest()` 得到 32 字节 bytes。

### 5.3 O_DIRECT 与并发控制

**所有 sblk 元数据读写统一使用 `O_DIRECT | O_SYNC`**，包括 Header 和 Slot。理由：

- sblk 是共享块设备，多节点可能访问同一 LV，page cache 会导致不一致
- 元数据操作频率低（每次 API 后一次），性能开销可忽略
- 代码路径统一，减少 bug 风险
- 10MB O_DIRECT 顺序写入延迟：SSD 场景约 50ms，SAN 场景约 200ms，可接受

**前提条件：**
- Header 从 offset 0 开始，4KB 对齐
- 使用 O_DIRECT + O_SYNC 确保不被 page cache 缓存（共享块设备多节点访问要求）
- 如果 LVM 能在该存储上正常工作（O_DIRECT 路径可用），则崩溃安全前提已满足

### 5.4 文件锁

**sblk 不使用文件锁**：共享块设备上 `fcntl.flock` 语义取决于具体实现（device-mapper + cluster），不可靠。sblk 场景的并发保护完全依赖管理平面的四层串行化机制（见 Part 2 §3.1）。即使毫秒级窗口的并发写入，因全量覆盖写语义，后者覆盖前者，结果依然正确（最终一致性）。无需引入额外分布式锁机制。

> local/NFS 使用 `fcntl.flock(fd, LOCK_EX | LOCK_NB)` 作为 defense-in-depth，在本地文件系统和 NFS 上语义可靠。NFS 的 `flock` 通过 NLM 协议实现，对同一 NFS server 上的多个客户端提供互斥语义。正常路径下不会有并发写入（管理面四层串行化已保证），flock 仅作为防御性保护防止异常重入。

### 5.5 O_DIRECT 内存对齐

O_DIRECT 要求用户态 buffer 地址和长度对齐到页大小（4KB）。`ctypes.create_string_buffer` 分配的内存**不保证**特定对齐。

**解决方案**：使用 `posix_memalign` + `ctypes` 封装为 `AlignedBuffer` 类。

---

## 6. AlignedBuffer 参考实现

> **Fallback 策略**：若 `posix_memalign` 不可用（极端环境），可 fallback 到 `mmap` + `MAP_ANONYMOUS` 分配页对齐内存。但 `posix_memalign` 在所有主流 Linux 发行版上均可用（glibc 2.0+），fallback 仅作防御性预留。

```python
import ctypes
import errno
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
        written = 0
        while written < self._size:
            ret = _libc.pwrite(
                fd,
                self._ptr.value + written,
                self._size - written,
                ctypes.c_longlong(file_offset + written)
            )
            if ret < 0:
                err = ctypes.get_errno()
                if err == errno.EINTR:
                    continue
                raise OSError(err, "pwrite failed: " + os.strerror(err))
            if ret == 0:
                raise IOError("pwrite returned 0 (disk full?)")
            written += ret

    def pread(self, fd, file_offset):
        """Read from fd at file_offset into buffer using pread."""
        read_bytes = 0
        while read_bytes < self._size:
            ret = _libc.pread(
                fd,
                self._ptr.value + read_bytes,
                self._size - read_bytes,
                ctypes.c_longlong(file_offset + read_bytes)
            )
            if ret < 0:
                err = ctypes.get_errno()
                if err == errno.EINTR:
                    continue
                raise OSError(err, "pread failed: " + os.strerror(err))
            if ret == 0:
                raise IOError("pread returned 0 (unexpected EOF)")
            read_bytes += ret

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
# 写入 Header (4KB)
with AlignedBuffer(4096) as buf:
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

## 7. 约束与不変量

1. **I/O 对齐不変量**：所有 Header/Slot 读写必须通过 4KB 对齐缓冲区执行，且使用 `O_DIRECT | O_SYNC`。
2. **I/O 完整性不変量**：`pwrite/pread` 必须循环至 `self._size` 全部写完/读完；任何 short write/read 都不能被当作成功返回。
3. **中断处理不変量**：遇到 `EINTR` 必须重试，不允许直接上抛导致部分数据路径中断。
4. **扫描时延上限不変量**：Layer 4 brute-force 扫描总时长上限 30 秒，超时统一返回 `CORRUPTED`。
5. **诊断可观测不変量**：brute-force 扫描开始时必须记录 `LV size` 和 `max_steps`，超时必须记录 ERROR。
