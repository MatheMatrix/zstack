# 01 — API 入口与字段定义

## 1.1 `APIDeleteVolumeSnapshotGroupMsg`（快照组删除）

**文件**：`header/src/main/java/org/zstack/header/storage/snapshot/group/APIDeleteVolumeSnapshotGroupMsg.java:24`

```java
@APIParam(required = false, validValues = {"pull", "commit", "auto"})
private String direction = "auto";

@APIParam(required = false, validValues = {"single", "chain", "auto"})
private String scope = "chain";   // 默认保留旧行为
```

REST 路径：`DELETE /volume-snapshots/group/{uuid}`

## 1.2 `APIDeleteVolumeSnapshotMsg`（单快照删除）

**文件**：`header/.../APIDeleteVolumeSnapshotMsg.java:49`

```java
@APIParam(required = false, validValues = {"pull", "commit", "auto"})
private String direction = "auto";

@APIParam(required = false, validValues = {"single", "chain", "auto"})
private String scope = "chain";   // 默认 chain，向后兼容
```

REST 路径：`DELETE /volume-snapshots/{uuid}`

## 1.3 枚举类

### `DeleteVolumeSnapshotDirection` — `header/.../DeleteVolumeSnapshotDirection.java:3`

| 值 | 语义 |
|---|---|
| `Pull("pull")` | 下拉方向：父快照内容合入子快照 |
| `Commit("commit")` | 上提方向：子快照内容合入父快照 |
| `Auto("auto")` | 系统自动判断 |

### `DeleteVolumeSnapshotScope` — `header/.../DeleteVolumeSnapshotScope.java:3`

| 值 | 语义 |
|---|---|
| `Single("single")` | 只删除当前单节点，保留整条链 |
| `Chain("chain")` | 删除当前节点及所有后代（旧默认） |
| `Auto("auto")` | 系统自动判断（实际等同 single） |

## 1.4 传递结构体

`VolumeSnapshotDeletionStructs` — `header/.../VolumeSnapshotDeletionStructs.java:5`
跨层透传 `direction + scope + 快照列表`。

## 1.5 兼容性

API 默认值 `scope = "chain"` 保持向后兼容；**必须显式传 `scope=single`** 才会触发新功能。
