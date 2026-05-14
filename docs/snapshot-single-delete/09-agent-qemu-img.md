# 09 — agent 端 qemu-img 三种命令对比

## 9.1 三个函数定义

**文件**：`zstacklib/zstacklib/utils/linux.py:1389-1432`

```python
# 1389：qcow2_commit
def qcow2_commit(top, base):
    shell.call('%s -f qcow2 -b %s %s' % (qemu_img.subcmd('commit'), base, top))
    # qemu-img commit -f qcow2 -b <base> <top>
    # 语义：top delta → base，base 内容更新，top 不被自动删

# 1395：qcow2_rebase（安全 rebase）
def qcow2_rebase(backing_file, target):
    if backing_file:
        fmt = get_img_fmt(backing_file)
        backing_option = '-F %s -b "%s"' % (fmt, backing_file)
    else:
        backing_option = '-b "%s"' % backing_file

    # virtual size 一致性自动扩容
    top_virtual_size = int(qcow2_get_virtual_size(target))
    backing_chain = qcow2_get_backing_chain(target)
    for idx, bf in enumerate(backing_chain):
        if idx == len(backing_chain)-1 and get_img_fmt(bf) != 'qcow2':
            break
        bf_virtual_size = int(qcow2_get_virtual_size(bf))
        if bf_virtual_size < top_virtual_size:
            qemu_img_resize(bf, top_virtual_size)
        if bf == backing_file:
            break

    with TempAccessible(target):
        shell.call('%s -f qcow2 %s %s' % (qemu_img.subcmd('rebase'), backing_option, target))
    # qemu-img rebase -f qcow2 -F <fmt> -b "<backing>" <target>

# 1416：qcow2_rebase_no_check（unsafe rebase）
def qcow2_rebase_no_check(backing_file, target, backing_fmt=None):
    fmt = backing_fmt if backing_fmt else get_img_fmt(backing_file)
    with TempAccessible(target):
        shell.call('%s -F %s -u -f qcow2 -b "%s" %s' % (
            qemu_img.subcmd('rebase'), fmt, backing_file, target))
    # qemu-img rebase -F <fmt> -u -f qcow2 -b "<backing>" <target>
```

## 9.2 精确差异对比

| 函数 | 命令模板 | -u | 读旧 backing | 重写 delta | 用途 |
|---|---|---|---|---|---|
| `qcow2_commit` | `qemu-img commit -f qcow2 -b <base> <top>` | — | 读 top | 否（合并） | top delta 合入 base |
| `qcow2_rebase` | `qemu-img rebase -f qcow2 -F <fmt> -b <new> <target>` | 无 | **读旧/新 backing** | **是** | 安全换 backing |
| `qcow2_rebase_no_check` | `qemu-img rebase -F <fmt> -u -f qcow2 -b <new> <target>` | **有** | 否 | 否 | 只改头部指针 |

## 9.3 Unsafe rebase 数据语义

`-u`（unsafe）：
- **不读取**旧 / 新 backing file 数据
- **直接修改** target 文件 QCOW2 header 中的 `backing_file` 字段
- 前提：新旧 backing 在 target 引用的块上**数据一致**（否则读出错误数据）

在 single 删除场景，commit 完成后 base 的内容 = 原 src 内容，所以兄弟节点把 backing 从 src 改到 base 是**安全的**。

## 9.4 安全 rebase 的自动扩容

`qcow2_rebase` 遍历 backing chain，发现 backing 的 virtual size 比 target 小时，调用 `qemu_img_resize` 自动扩容，防止 rebase 后读越界。

## 9.5 SharedBlock LV 扩容（pull 时）

**文件**：`shared_block_plugin.py:1247-1285`

```python
total_required_size = self.get_total_required_size(dst_abs_path)
current_size = int(lvm.get_lv_size(dst_abs_path))
if not cmd.fullRebase:
    if current_size < total_required_size:
        lvm.extend_lv_from_cmd(dst_abs_path, total_required_size, cmd,
                               extend_thin_by_specified_size=True)
    with lvm.RecursiveOperateLv(src_abs_path, shared=True):
        linux.qcow2_rebase(src_abs_path, dst_abs_path)
```

```python
# get_total_required_size — shared_block_plugin.py:967
@staticmethod
def get_total_required_size(abs_path):
    virtual_size = linux.qcow2_virtualsize(abs_path)
    total_size = -1
    if linux.get_img_fmt(abs_path) == "qcow2":
        try:
            total_size = linux.qcow2_measure_required_size(abs_path)
            # qemu-img measure：预测完整合并后的最小大小
        except Exception as e:
            logger.warn(...)
    if total_size > virtual_size or total_size == -1:
        total_size = virtual_size
    return total_size
```

**为什么 pull 需要扩 LV**：pull 把 src 数据合并进 dst，dst 物理占用上升；如果当前 LV 容量不够，提前扩容避免写入失败。
