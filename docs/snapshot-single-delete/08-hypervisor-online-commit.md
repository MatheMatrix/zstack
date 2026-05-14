# 08 — Hypervisor 在线 commit（libvirt blockCommit + pivot）

## 8.1 入口

**HTTP**：`POST /vm/volume/blockcommit`（`KVMConstant.KVM_BLOCK_COMMIT_VOLUME_PATH`）

**Python**：`kvmagent/kvmagent/plugins/vm_plugin.py:9845`

## 8.2 `do_block_commit()` 完整流程

`vm_plugin.py:3915-3983`：

```python
def do_block_commit(self, task_spec, volume):
    def do_block_commit_disk(task_spec, disk_name, top, base, active_commit):
        def wait_job(_):
            return not self._wait_for_block_job(disk_name, abort_on_error=True)

        def check_overlay_file(path):
            if not active_commit:
                return True
            return self._check_target_disk_existing_by_path(path, True)

        def abort_block_commit_job(_):
            flag = libvirt.VIR_DOMAIN_BLOCK_JOB_ABORT_ASYNC
            if active_commit:
                flag = libvirt.VIR_DOMAIN_BLOCK_JOB_ABORT_PIVOT
            try:
                if not self.domain.blockJobInfo(disk_name, 0):
                    return True
                self.domain.blockJobAbort(disk_name, flag)
                return True
            except Exception as e:
                logger.warn("pivot active layer failed, %s" % e)
                return False

        # flags 组合
        if active_commit:
            flags = libvirt.VIR_DOMAIN_BLOCK_COMMIT_RELATIVE
            flags |= libvirt.VIR_DOMAIN_BLOCK_COMMIT_ACTIVE
        else:
            flags = libvirt.VIR_DOMAIN_BLOCK_COMMIT_DELETE

        # 发起 blockCommit
        self.domain.blockCommit(disk_name, base, top, 0, flags)
        touchQmpSocketWhenExists(task_spec.vmUuid)

        # 等数据同步
        if not linux.wait_callback_success(wait_job, timeout=d.get_remaining_timeout(),
                                           ignore_exception_in_callback=True):
            if not check_overlay_file(base):
                raise kvmagent.KvmError('block commit failed')

        # pivot 或普通结束
        if not linux.wait_callback_success(abort_block_commit_job, d.get_remaining_timeout(),
                                           ignore_exception_in_callback=True):
            raise kvmagent.KvmError('block commit abort failed')

        # 确认 overlay（top）消失
        if not linux.wait_callback_success(check_overlay_file, base, d.get_remaining_timeout(),
                                           ignore_exception_in_callback=True):
            raise kvmagent.KvmError('block commit succeeded, but overlay file is not cleared')

        return base

    target_disk, disk_name = self._get_target_disk(volume)
    top = get_volume_actual_installpath(task_spec.top)
    base = get_volume_actual_installpath(task_spec.base)
    install_path = VmPlugin.get_source_file_by_disk(target_disk)
    active_commit = (top == install_path)   # ← 关键判定

    with BlockCommitDaemon(task_spec, self, disk_name, top=top, base=base,
                           active_commit=active_commit) as d:
        return do_block_commit_disk(task_spec, disk_name, task_spec.top,
                                    task_spec.base, active_commit)
```

## 8.3 libvirt flags 矩阵

| Flag | 作用 |
|---|---|
| `VIR_DOMAIN_BLOCK_COMMIT_DELETE` | 完成后自动删除 top 文件（非 active commit） |
| `VIR_DOMAIN_BLOCK_COMMIT_ACTIVE` | top 是活跃层，两阶段模式（需 pivot） |
| `VIR_DOMAIN_BLOCK_COMMIT_RELATIVE` | backing 用相对路径 |
| `VIR_DOMAIN_BLOCK_COMMIT_SHALLOW` | 只提交一层（**本代码未使用**） |

## 8.4 Active commit 双阶段 pivot 流程

```
Phase 1（数据同步）：
  blockCommit() → qemu 把 top delta 写进 base
  VM 持续写 top，qemu 增量同步
  轮询 blockJobInfo 直到 ready

Phase 2（pivot）：
  blockJobAbort(VIR_DOMAIN_BLOCK_JOB_ABORT_PIVOT)
  → qemu 原子切换活跃层 top → base
  → top 变游离，VM 后续写直接落 base

最后 check_overlay_file 确认 pivot 成功
```

**为什么需要 pivot**：VM 正在运行，top 文件实时被写；不能直接删 top，必须先让 qemu 把活跃层切到 base。

## 8.5 关键辅助函数

`_get_snapshot_size()` — `vm_plugin.py:8946`：
```python
@staticmethod
def _get_snapshot_size(install_path):
    size = linux.get_local_file_disk_usage(install_path)   # du -sb（actual size）
    if size is None or size == 0:
        if install_path.startswith("/dev/"):
            size = int(lvm.get_lv_size(install_path))      # LV 场景
        else:
            size = linux.qcow2_virtualsize(install_path)   # 兜底
    return size
```

返回 **actual size**（实际占用），SharedBlock 走 LV 大小。

## 8.6 active_commit 判定

```python
active_commit = (top == install_path)
```

`install_path` 是 libvirt domain XML 中 disk 当前的 source file，等于活跃层路径。当 `top` 等于活跃层时即 active commit。
