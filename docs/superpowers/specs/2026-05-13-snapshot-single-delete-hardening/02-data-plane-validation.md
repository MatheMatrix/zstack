# 数据面：四层校验

## 6.1 共享工具模块

**新建** `zstacklib/zstacklib/utils/snapshot_recovery.py`：

```python
class ChainSnapshot:
    path: str
    backing_file: str | None
    virtual_size: int
    actual_size: int
    file_format: str
    md5_header: str

class ChainSnapshotSet:
    operation: str
    timestamp: float
    snapshots: dict[str, ChainSnapshot]
    def dump_to_file(self, path): ...
    @staticmethod
    def load_from_file(path) -> "ChainSnapshotSet": ...

def take_chain_snapshot(paths: list[str]) -> ChainSnapshotSet: ...
def verify_post_op(before: ChainSnapshotSet, expected: dict[str, str]) -> VerifyResult: ...
```

**扩展** `linux.py`：

```python
def qcow2_get_backing_chain_strict(path) -> list[str]:
    """读 qcow2 backing chain，遇错抛 QcowReadError"""

def qemu_img_check(path, repair=None) -> CheckResult:
    """qemu-img check，结构化结果"""
```

## 6.2 L1 — 操作前 chain 快照

**目的**：进程崩溃 / 宿主机断电后，重启能根据 dump 判断"上次进度"

**dump 路径**：`/var/lib/zstack/snapshot-recovery/<volume_uuid>-<op_id>.json`

接入示例（`vm_plugin.py` block_commit handler）：

```python
@kvmagent.replyerror
def block_commit(self, req):
    cmd = jsonobject.loads(req[http.REQUEST_BODY])

    # L1：dump pre-op chain
    paths = [cmd.top, cmd.base] + (cmd.topChildrenInstallPathInDb or [])
    pre_snap = take_chain_snapshot(paths)
    pre_snap.operation = 'commit'
    recovery_file = "/var/lib/zstack/snapshot-recovery/%s-%s.json" % (
        cmd.volumeUuid, uuidhelper.uuid())
    pre_snap.dump_to_file(recovery_file)

    try:
        vm = get_vm_by_uuid(cmd.vmUuid)
        vm.do_block_commit(cmd, cmd.volume)
        for child in (cmd.topChildrenInstallPathInDb or []):
            if linux.qcow2_get_backing_file(child) != cmd.base:
                linux.qcow2_rebase_no_check(cmd.base, child)

        # L2：post-op verify
        verify_post_commit(pre_snap, cmd.base)

        linux.rm_file_force(recovery_file)
        return jsonobject.dumps(rsp)
    except Exception:
        raise   # 失败保留 recovery 文件
```

**生命周期**：
- 成功 → 删除
- 失败 → 保留供下次操作 / 启动恢复消费
- 超 24h → kvmagent 启动时清理

### 6.2.1 其它路径 L1 接入模板

**离线 commit**（`localstorage.py:859` / `nfs:.625` / `smp:.506` / `sb:.1285`）：

```python
# paths = top + base + 兄弟节点（commit 后兄弟需 rebase 到 base）
paths = [cmd.top, cmd.base] + (cmd.topChildrenInstallPathInDb or [])
pre_snap = take_chain_snapshot(paths)
pre_snap.operation = 'offline-commit'
recovery_file = ".../%s-%s.json" % (cmd.volumeUuid, uuidhelper.uuid())
pre_snap.dump_to_file(recovery_file)
try:
    linux.qcow2_commit(cmd.top, cmd.base)
    for child in (cmd.topChildrenInstallPathInDb or []):
        if linux.qcow2_get_backing_file(child) != cmd.base:
            linux.qcow2_rebase_no_check(cmd.base, child)
    verify_post_commit(pre_snap, cmd.base)
    linux.rm_file_force(recovery_file)
except Exception:
    raise
```

**离线 pull**（`localstorage.py:835` 等）：

```python
# paths = src + dst + dst.children（pull 后 dst.children 需 rebase 到 src）
paths = [cmd.srcPath, cmd.dstPath] + (cmd.dstChildrenInstallPathInDb or [])
pre_snap = take_chain_snapshot(paths)
pre_snap.operation = 'offline-pull'
...
linux.qcow2_commit(cmd.dstPath, cmd.srcPath)  # pull = reverse commit
verify_post_pull(cmd.srcPath, expected_backing=pre_snap.snapshots[cmd.srcPath].backing_file,
                 full_rebase=False)
```

**fullRebase**（`create_template_with_task_daemon` + mv，详见 `docs/snapshot-single-delete/12-fullrebase-and-cleanup.md`）：

```python
# paths = dst + dst 整条 backing chain（fullRebase 会全部展平进 tmp）
chain = linux.qcow2_get_backing_chain_strict(cmd.destPath)
paths = [cmd.destPath] + chain
pre_snap = take_chain_snapshot(paths)
pre_snap.operation = 'fullRebase'
pre_snap.metadata['tmp_path'] = cmd.destPath + '.tmp'  # 登记临时文件路径
recovery_file = ...
pre_snap.dump_to_file(recovery_file)
try:
    create_template_with_task_daemon(cmd.destPath, cmd.destPath + '.tmp')
    linux.mv(cmd.destPath + '.tmp', cmd.destPath)
    verify_post_pull(cmd.destPath, expected_backing=None, full_rebase=True)
    linux.rm_file_force(recovery_file)
except Exception:
    # 若 tmp 残留，启动恢复扫到 metadata.tmp_path 即可清理
    raise
```

**SharedBlock**：`paths` 用 LV 设备路径（`/dev/<vg>/<lv>`），`take_chain_snapshot` 内部对 LV 路径做 `qemu-img info` 即可，无需特殊分支。

## 6.3 L2 — 操作后自检

```python
def verify_post_commit(pre, base):
    actual_backing = linux.qcow2_get_backing_chain_strict(base)[0:1]
    expected_backing = pre.snapshots[base].backing_file
    if actual_backing and actual_backing[0] != expected_backing:
        raise PostOpVerifyError(...)
    # size 检查降级为 warn：commit src 可能是零差量、qcow2 压缩、稀疏文件，不能强制断言增大
    # 阈值与容差由 [snapshot_recovery] size_check_threshold_bytes / size_check_tolerance_ratio 配置（见 6.7）
    if pre.snapshots[base].actual_size > config.size_check_threshold_bytes:
        new_size = linux.get_local_file_disk_usage(base)
        if new_size < pre.snapshots[base].actual_size * config.size_check_tolerance_ratio:
            logger.warn("base %s disk usage shrank from %d to %d after commit, "
                        "possibly compression/sparse, verify backing OK" %
                        (base, pre.snapshots[base].actual_size, new_size))

def verify_post_rebase(target, expected_backing):
    actual = linux.qcow2_get_backing_file(target)
    if actual != expected_backing:
        raise PostOpVerifyError(...)

def verify_post_pull(dst, expected_backing, full_rebase):
    actual = linux.qcow2_get_backing_file(dst)
    if full_rebase and actual:
        raise PostOpVerifyError(...)
    if not full_rebase and actual != expected_backing:
        raise PostOpVerifyError(...)
```

接入点：

| 操作 | 文件位置 | 验证 |
|---|---|---|
| 在线 blockCommit | `vm_plugin.py:9845` 主操作完成 | verify_post_commit |
| 在线兄弟 rebase | `vm_plugin.py:9857` 循环内 | verify_post_rebase |
| 离线 commit | `localstorage.py:859` / `nfs:.625` / `smp:.506` / `sb:.1285` | commit + rebase |
| 离线 pull | `localstorage.py:835` 等 | verify_post_pull |
| fullRebase mv 后 | 同上 | verify_post_pull(full_rebase=True) |

失败抛 `PostOpVerifyError`（继承 `kvmagent.KvmError`）→ HTTP 500 → 控制面 FlowChain error → reconciler 介入

## 6.4 L3 — qemu-img check（异常路径）

```python
def qemu_img_check(path, repair=None):
    args = ['check', '-f', 'qcow2']
    if repair: args += ['-r', repair]
    args.append(path)
    out = shell.call(qemu_img.cmd(args))
    return parse_check_output(out)
```

触发：
1. L2 失败前先跑一次区分"qemu-img 静默错误" vs "文件已损坏"
2. 启动恢复诊断时
3. 控制面 `CheckSnapshotIntegrityMsg` 显式触发

**仅检测，不自动修复**。`-r` 修复仅在控制面 API 显式批准时使用。

## 6.5 L4 — blockJob 状态机加固

```python
class BlockJobState(enum.Enum):
    NOT_STARTED, RUNNING, READY, COMPLETED, PIVOTED, CANCELLED, FAILED

class BlockJobMonitor:
    def __init__(self, domain, disk_name, active_commit, timeout_sec): ...
    def poll(self) -> BlockJobState: ...
    def wait_until(self, target_states: set, timeout: int) -> BlockJobState: ...
```

**active commit 状态机**：

```
NOT_STARTED → RUNNING ──(timeout)──► FAILED → raise
                │ ready event
                ▼
              READY ──(timeout)──► FAILED → blockJobAbort(no pivot) → CANCELLED → raise
                │ blockJobAbort(PIVOT)
                ▼
              PIVOTED ──verify domain XML source==base──► COMPLETED
                                  │ no
                                  ▼ FAILED → raise
```

**改造点**：
1. 用 `wait_until({READY})` 替换"轮询 job 不在"
2. pivot 前必须确认 READY
3. 任何超时显式 CANCELLED
4. 终态通过读 domain XML disk source 二次确认

## 6.6 启动恢复

```python
def on_kvmagent_startup():
    for f in glob('/var/lib/zstack/snapshot-recovery/*.json'):
        snap = ChainSnapshotSet.load_from_file(f)
        if time.time() - snap.timestamp > 86400:
            linux.rm_file_force(f); continue
        for path in snap.snapshots:
            if not os.path.exists(path): continue
            result = qemu_img_check(path)
            if result.image_corrupted:
                logger.error("recovery: corrupted file %s" % path)
        write_diagnostic_report(snap, f.replace('.json', '.report.json'))
```

**只诊断不改文件**。控制面通过 `GET /snapshot-recovery/report` 端点读取诊断报告。

## 6.7 配置

```ini
[snapshot_recovery]
enable_l1_dump = true
enable_l2_verify = true
enable_l3_check_on_error = true
recovery_dir = /var/lib/zstack/snapshot-recovery
recovery_max_age_hours = 24
blockjob_timeout_sec = 3600
size_check_threshold_bytes = 104857600   # 100 MiB；base.actual_size 大于此值才做 size warn
size_check_tolerance_ratio = 0.9          # 允许新尺寸不低于旧尺寸 * 此比值，否则记 warn
```
