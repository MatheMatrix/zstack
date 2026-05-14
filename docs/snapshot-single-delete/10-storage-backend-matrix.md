# 10 — 存储后端支持矩阵

## 10.1 支持情况汇总

| 存储类型 | scope=single | 在线 commit | 离线 commit | pull | 备注 |
|---|---|---|---|---|---|
| **LocalStorage** | ✅ | KVMHost | `/localstorage/snapshot/offlinecommit` | `/localstorage/snapshot/offlinemerge` | qcow2 文件 |
| **NFS** | ✅ | KVMHost | `/nfsprimarystorage/offlinesnapshotcommit` | `/nfsprimarystorage/offlinesnapshotmerge` | qcow2 文件 |
| **SMP** | ✅ | KVMHost | `OFFLINE_COMMIT_SNAPSHOT_PATH` | `OFFLINE_MERGE_SNAPSHOT_PATH` | 共享挂载点 |
| **SharedBlock** | ✅ | KVMHost | 同 + 扩 LV | 同 + 扩 LV | LVM + qcow2 |
| **Ceph (RBD)** | ⚠️ 受限 | ❌ | ❌ | ❌ | RBD snapshot 不支持合并 |

## 10.2 LocalStorage

**Java**：`LocalStorageKvmBackend.java:3825/3846`

```java
// 离线 commit
postRequest("/localstorage/snapshot/offlinecommit", cmd);
// 离线 pull
postRequest("/localstorage/snapshot/offlinemerge", cmd);
```

**Python**：`kvmagent/plugins/localstorage.py:835/859`

```python
# offline_commit_snapshot
if linux.qcow2_get_backing_file(cmd.top) != linux.qcow2_get_backing_file(cmd.base):
    linux.qcow2_commit(cmd.top, cmd.base)

if cmd.topChildrenInstallPathInDb:
    for children in cmd.topChildrenInstallPathInDb:
        if linux.qcow2_get_backing_file(children) != cmd.base:
            linux.qcow2_rebase_no_check(cmd.base, children)
```

```python
# offline_merge_snapshot
src_path = cmd.srcPath if not cmd.fullRebase else ""
if linux.qcow2_get_backing_file(cmd.destPath) == src_path:
    return    # 幂等
if not cmd.fullRebase:
    linux.qcow2_rebase(cmd.srcPath, cmd.destPath)
else:
    tmp = .../%s.qcow2 % uuid
    qcow2.create_template_with_task_daemon(cmd.destPath, tmp, task_spec=cmd)
    shell.call("mv %s %s" % (tmp, cmd.destPath))
```

## 10.3 NFS

**Java**：`NfsPrimaryStorageKVMBackend.java:1996/2031`

**Python**：`nfs_primarystorage_plugin.py:601/625`

逻辑与 LocalStorage 几乎一致（同样用 qcow2_commit / qcow2_rebase）。

## 10.4 SMP（SharedMountPoint）

**Java**：`smp/KvmBackend.java:2443/2466`

**Python**：`shared_mountpoint_plugin.py:483/506`

逻辑同 NFS。

## 10.5 SharedBlock

**Python**：`shared_block_plugin.py:1247/1285`

```python
# offline_merge：扩 LV + 激活 LV + rebase
total_required_size = self.get_total_required_size(dst_abs_path)
if current_size < total_required_size:
    lvm.extend_lv_from_cmd(dst, total_required_size, cmd, extend_thin_by_specified_size=True)
with lvm.RecursiveOperateLv(src_abs_path, shared=True):
    linux.qcow2_rebase(src_abs_path, dst_abs_path)
```

```python
# offline_commit：commit 后清理 base 元数据
with lvm.RecursiveOperateLv(top, shared=True):
    if linux.qcow2_get_backing_file(cmd.top) != linux.qcow2_get_backing_file(cmd.base):
        linux.qcow2_commit(cmd.top, cmd.base)
    if cmd.topChildrenInstallPathInDb:
        for c in cmd.topChildrenInstallPathInDb:
            with lvm.RecursiveOperateLv(c, shared=True):
                if linux.qcow2_get_backing_file(c) != base:
                    linux.qcow2_rebase_no_check(base, c)
lvm.delete_lv_meta(base)
```

## 10.6 Ceph

`CephPrimaryStorageBase` **未实现** `CommitVolumeSnapshotOnPrimaryStorageMsg` / `PullVolumeSnapshotOnPrimaryStorageMsg`。

例外：`CephPrimaryStorageBase.java:2984` 临时快照删除时硬编码 `scope=Single, direction=Commit`，但仅用于撤销临时快照场景。

普通 RBD 快照：`cephdriver.py:87` 的 `delete_snapshot` 直接调 `rbd snap rm`，**不支持中间节点合并**。

**结论**：Ceph 普通快照不支持 `scope=single`。

## 10.7 在线场景统一走 KVMHost

`plugin/kvm/.../KVMHost.java:1043/1159`：
- `commitVolumeSnapshot` → `POST /vm/volume/blockcommit`
- `pullVolumeSnapshot` → `POST /vm/volume/blockpull`

所有支持的存储类型在 VM 在线时都走 libvirt blockCommit / blockPull，由 KVMHost 统一处理。
