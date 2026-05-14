# 单快照节点删除（scope=single）— 总览

> 需求：ZSV-5799 "支持删除快照不删除链"
> 关联 MR：zstack#7674 / premium#10776 / zstack-utility#5743
> 入口 API：`APIDeleteVolumeSnapshotGroupMsg`（含 `direction` + `scope` 字段）

---

## 文档索引

| 文档 | 内容 |
|---|---|
| [01-api-and-fields.md](01-api-and-fields.md) | API 入口、字段、枚举定义 |
| [02-call-chain.md](02-call-chain.md) | 处理链路总览（Group → Tree → Storage） |
| [03-direction-resolution.md](03-direction-resolution.md) | `resolveDirection()` 决策表与 fromVOs 构建 |
| [04-scope-and-stepDelete.md](04-scope-and-stepDelete.md) | scope 分支与 stepDelete 递归 |
| [05-commit-db-swap.md](05-commit-db-swap.md) | Commit 路径 DB 翻转（最关键） |
| [06-pull-db-rewrite.md](06-pull-db-rewrite.md) | Pull / pullToVolume DB 改写 |
| [07-group-passthrough.md](07-group-passthrough.md) | Group 透传与并发、失败聚合 |
| [08-hypervisor-online-commit.md](08-hypervisor-online-commit.md) | 在线 libvirt blockCommit + pivot |
| [09-agent-qemu-img.md](09-agent-qemu-img.md) | agent 端 qemu-img 三种命令对比 |
| [10-storage-backend-matrix.md](10-storage-backend-matrix.md) | Local/NFS/SMP/SharedBlock/Ceph 后端差异 |
| [11-sibling-rebase.md](11-sibling-rebase.md) | 分叉链兄弟节点 rebase |
| [12-fullrebase-and-cleanup.md](12-fullrebase-and-cleanup.md) | fullRebase 树根删除与残留清理 |
| [13-premium-and-cdp.md](13-premium-and-cdp.md) | Premium / CDP / 灾备兼容性 |
| [14-limitations-and-todos.md](14-limitations-and-todos.md) | 已知限制 / TODO / FIXME |

---

## 一图概览

```
[祖父] ── [待删节点 X] ── [子 Y] ── ...
              │
   ┌──────────┴───────────┐
   │ scope=single         │
   │ direction=commit     │ 在线VM 且 X≠latest
   │ → Y 差量写入 X 文件   │
   │ → DB: 互换 path, Y.parent=X.parent
   │
   │ direction=pull       │ 离线 或 X=latest
   │ → 祖父+X 合并入 Y(rebase)
   │ → DB: Y.parent = X.parent
```

## 仓库根

- `/d/0zw/zw/zstack/` —— 开源主库
- `/d/0zw/zw/premium/` —— Premium（独立 git）
- `/d/0zw/zw/zstack-utility/` —— Python agent
