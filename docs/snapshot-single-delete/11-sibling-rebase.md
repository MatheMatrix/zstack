# 11 — 兄弟节点 rebase（分叉链关键）

## 11.1 问题背景

当 commit 完成后，待删节点 X 还可能有除 src 外的其他子节点（兄弟节点），它们的 backing file 仍然指向 X 的旧物理路径，必须重新指向 base（dst）才能继续访问。

```
分叉链示例：
         X (待删)
        /   \
      src   sibling1
       |       |
     descend  ...

commit(src → X) 完成后：
  X 物理文件内容已变成 src 数据
  sibling1 的 backing 仍指向 X 旧路径 → 必须 rebase 到 base
```

## 11.2 Java 侧收集兄弟节点路径

**文件**：`VolumeSnapshotTreeBase.java:1012-1024`

```java
// commit flow 内部
List<String> childrenInstallPath = child.getChildren().stream()
    .map(c -> c.getInventory().getPrimaryStorageInstallPath())
    .collect(Collectors.toList());
// child = src 节点
// child.getChildren() = src 的所有子节点

// 在线消息
CommitVolumeSnapshotOnHypervisorMsg cmsg = new CommitVolumeSnapshotOnHypervisorMsg();
cmsg.setSrcChildrenInstallPathInDb(childrenInstallPath);

// 离线消息（1044 行）
cmsg.setSrcChildrenInstallPathInDb(childrenInstallPath);
```

**注意**：变量名 `childrenInstallPath` 表面上像 src 的子节点，但实际语义是"待删节点 X（top）的子节点除 src 之外的兄弟节点"。代码命名上稍混乱，但 `topChildrenInstallPathInDb` 在 agent 侧含义明确：top（待删节点）所有子节点 → 它们的 backing 都需要 rebase 到 base。

KVMHost 透传：`KVMHost.java:1052`
```java
cmd.setTopChildrenInstallPathInDb(msg.getSrcChildrenInstallPathInDb());
```

## 11.3 agent 侧循环 unsafe rebase

### 在线 — `vm_plugin.py:9857`

```python
vm.do_block_commit(cmd, cmd.volume)
if cmd.topChildrenInstallPathInDb:
    for children in cmd.topChildrenInstallPathInDb:
        if linux.qcow2_get_backing_file(children) != cmd.base:
            linux.qcow2_rebase_no_check(cmd.base, children)
rsp.size = VmPlugin._get_snapshot_size(cmd.base)
```

### 离线 LocalStorage — `localstorage.py:864-869`

```python
if linux.qcow2_get_backing_file(cmd.top) != linux.qcow2_get_backing_file(cmd.base):
    linux.qcow2_commit(cmd.top, cmd.base)

if cmd.topChildrenInstallPathInDb:
    for children in cmd.topChildrenInstallPathInDb:
        if linux.qcow2_get_backing_file(children) != cmd.base:
            linux.qcow2_rebase_no_check(cmd.base, children)
```

### 离线 SharedBlock — `shared_block_plugin.py:1299-1308`

```python
with lvm.RecursiveOperateLv(top, shared=True):
    if linux.qcow2_get_backing_file(cmd.top) != linux.qcow2_get_backing_file(cmd.base):
        linux.qcow2_commit(cmd.top, cmd.base)
    if cmd.topChildrenInstallPathInDb:
        for c in cmd.topChildrenInstallPathInDb:
            with lvm.RecursiveOperateLv(c, shared=True):
                if linux.qcow2_get_backing_file(c) != base:
                    linux.qcow2_rebase_no_check(base, c)
```

## 11.4 兄弟节点 parentUuid 何时更新？

**关键事实**：兄弟节点的 `parentUuid` **不在** `updateDatabaseAfterCommit` 里更新。

`updateDatabaseAfterCommit` 只更新：
- dst 的 path（互换）
- src 的 path、size、distance、parentUuid
- src 的所有后代的 distance

兄弟节点（src 的兄弟，即 X 的其他子节点）的 DB `parentUuid` 仍指向 X。

**后续递归处理**：
- 下次 `stepDelete` 重新从 DB 构建 `VolumeTree`
- 此时 X 节点对应的物理文件路径已经是 src 数据（互换后）
- 但 DB 中兄弟节点仍挂在 X 下 → 物理 vs DB 不一致

这是 `VolumeTree.java:258` 注释中标记的 TODO：

```java
// TODO(clone) : When both chain cloning and single-node snapshot deletion are enabled,
// it is necessary to consider the dependency relationships of all snapshot nodes in the
// current snapshot tree within the VolumeSnapshotReferenceVO.
```

## 11.5 风险

- 分叉链中删中间节点时，兄弟节点物理 backing 与 DB parentUuid 暂时不一致
- 若此时发生异常重启或并发操作，可能导致快照树状态混乱
- 当前依赖"删除 X 后兄弟节点自然变成 X.parent 的子节点"这一物理事实，DB 修复留待后续操作
