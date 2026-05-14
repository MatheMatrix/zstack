# 单盘快照删除 — 场景梳理索引

本目录收录"现状代码逻辑梳理"性质的场景文档（与加固设计 spec 隔离）。
每个文件聚焦一种具体的（存储类型 × VM 状态 × 树结构）组合，按 stepDelete 轮次推演当前实现行为。

| 文件 | 存储 | VM 状态 | 树结构 / 待删节点 |
|---|---|---|---|
| `01-multi-children-stepDelete.md` | 通用 | 通用 | 抽象骨架：X→A→{B,C,D}，待删 A，多子节点 stepDelete 决策算法 |
| `02-local-running-delete-mid-with-3-children.md` | LocalStorage | Running | 1→2→{3,4,5→vol}，待删快照2，含在线 commit + vol.installPath 同步 |
| `03-local-stopped-delete-mid-with-3-children.md` | LocalStorage | Stopped | 同上树结构，全程离线 pull → `offline_merge_snapshot` → `qcow2_rebase`，差量散到每个 child，无 libvirt，无 path 互换 |
| `04-deleteSingleFlows-online-offline-decision.md` | 通用 | 通用 | `deleteSingleFlows` / `stepDelete` / `resolveDirection` / `isOnline` / `commit` / `pull` 中 online 与 direction 的判定时序、四象限到 agent 入口映射 |
| `05-local-stopped-direction-commit-actual.md` | LocalStorage | Stopped | 1→2→{3,4,5→vol}，待删快照2，**实测**记录（ZSV 真实环境抓 API uuid 全程 agent POST），direction=Commit + scope=single；轮 1/2 `offlinemerge`，轮 3 `offlinecommit`，轮 4 `delete`；修正源码推演 3 处偏差（child 顺序、VO_2 直接删、vol.installPath 不互换）|

> 当前实现 Bug 清单已独立成档：`../bugs.md`（位于 `docs/snapshot-single-delete/bugs.md`）。**P0 修复已落地**（拆 `isOnline` 为 `isOnAliveChain` + `isHypervisorOperation`，`resolveDirection` 解耦 vmState），覆盖 Bug 0/1/3/7；剩余 P0/P1（Bug 2/4/5/6）见 bugs.md。

> API 参数（`scope` / `direction`）重构提案：`../proposals/scope-direction-api-redesign.md`，覆盖 Bug 2 / Bug 8 / Bug 9。

> 待补场景候选（按需追加）：
> - NFS / SMP / SharedBlock + 在线 / 离线 各组合
> - 删根节点（dst 是树根，触发 newTree 创建）
> - 分叉链 + 在线 active commit 链上有多级 snapshot
> - fullRebase 路径（pull 大文件）
> - 快照组（多卷并发）
