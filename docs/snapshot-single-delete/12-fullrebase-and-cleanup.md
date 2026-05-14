# 12 — fullRebase 与残留文件清理

## 12.1 fullRebase 触发

**触发条件**：src 快照是树根（`srcSnapshotInv.getParentUuid() == null`），即 src 没有 backing file。
此时 pull 操作不能简单 rebase（没有新 backing 可指），必须把 dst 文件 flatten 成独立 qcow2。

Java 侧构造 `OfflineMergeSnapshotCmd` 时设置 `fullRebase = true`。

## 12.2 agent 侧实现（`localstorage.py:835-857`）

```python
src_path = cmd.srcPath if not cmd.fullRebase else ""

if linux.qcow2_get_backing_file(cmd.destPath) == src_path:
    return    # 幂等

if not cmd.fullRebase:
    linux.qcow2_rebase(cmd.srcPath, cmd.destPath)
else:
    tmp = os.path.join(os.path.dirname(cmd.destPath),
                       '%s.qcow2' % uuidhelper.uuid())
    qcow2.create_template_with_task_daemon(cmd.destPath, tmp, task_spec=cmd)
    shell.call("mv %s %s" % (tmp, cmd.destPath))
```

## 12.3 `create_template_with_task_daemon`

**文件**：`zstacklib/zstacklib/utils/qcow2.py:10`

```python
def create_template_with_task_daemon(src, dst, task_spec, dst_format='qcow2', opts=None, **daemonargs):
    t_shell = traceable_shell.get_shell(task_spec)
    p_file = tempfile.mktemp()

    class ConvertTaskDaemon(plugin.TaskDaemon):
        def _cancel(self):
            traceable_shell.cancel_job_by_api(self.api_id)
            linux.rm_file_force(self.dst_path)

        def _get_percent(self):
            p = linux.tail_1(p_file, split=b"\r")
            ...

    with ConvertTaskDaemon(dst, task_spec):
        linux.create_template(src, dst, dst_format=dst_format, shell=t_shell,
                              progress_output=p_file, opts=opts)
        # qemu-img convert -f qcow2 -O qcow2 -p <src> <dst>
```

特性：
- 遍历整条 backing chain，输出独立 qcow2
- 支持进度上报（`-p`）
- 流式转换，无内存限制
- 通过 `TaskDaemon` 支持取消（取消时删临时文件）

## 12.4 mv 替换的并发安全

- **文件系统场景**：`mv` 同 FS 内是 `rename(2)` 原子操作
- **LVM 场景**：`lvm.lv_rename` 元数据级原子
- **读取并发**：rename 前后读到的是旧/新文件，无半态损坏
- 上层依赖 `chainSubmit` 串行化同一树的操作，避免读到中间状态

## 12.5 残留文件清理责任

| 场景 | 清理者 |
|---|---|
| 在线非 active commit | `VIR_DOMAIN_BLOCK_COMMIT_DELETE` 自动删 top |
| 在线 active commit | pivot 后 top 游离，由 `deleteVolumeSnapshotAndSyncVolumeSize` 清理 |
| 离线 commit/pull | `deleteVolumeSnapshotAndSyncVolumeSize` 下发 `VolumeSnapshotPrimaryStorageDeletionMsg` |
| SharedBlock commit | `lvm.delete_lv_meta(base)` 删元数据；LV 真删走 `delete_bits` → `lvm.delete_lv` |

## 12.6 物理删除入口（`VolumeSnapshotTreeBase.java:1307`）

```java
private void deleteVolumeSnapshotAndSyncVolumeSize(Completion completion) {
    VolumeSnapshotPrimaryStorageDeletionMsg pmsg = new VolumeSnapshotPrimaryStorageDeletionMsg();
    pmsg.setUuid(currentRoot.getUuid());
    bus.makeTargetServiceIdByResourceUuid(pmsg, VolumeSnapshotConstant.SERVICE_ID,
            currentRoot.getPrimaryStorageUuid());
    bus.send(pmsg, ...);
}
```

各存储后端处理 `VolumeSnapshotPrimaryStorageDeletionMsg`，调用各自的 `delete_bits` HTTP 端点。

## 12.7 失败补偿 TODO

`VolumeSnapshotTreeBase.java:1325`：

```java
//TODO add gc
logger.warn(String.format("failed to delete snapshot[uuid:%s] on primary storage[uuid:%s], ..."));
```

物理文件删除失败仅 warn 日志，**无 GC 补偿**，存在文件/LV 泄露风险。
