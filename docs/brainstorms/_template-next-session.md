<!--
===============================================================================
next-session.md 模板 —— v1
===============================================================================
用法：
  - 每 session 结束前用这个模板覆盖 docs/brainstorms/next-session.md
  - 字段顺序固定，不要增减一级标题（读的是 Claude，不是人）
  - 能引用外部文件就引用，不要复制粘贴
  - 填空位用 `<...>` 或 `TODO`，完成后删掉占位符
  - 超过 120 行要警觉：多半是该 ADR / runbook 没抽出去

外部引用约定（这些文件是长期存在的，本文件只引用不复述）：
  - docs/decisions/          ← 已落地、不要再议的技术决策（ADR 格式）
  - docs/runbooks/           ← 环境/部署/回滚操作手册
  - CLAUDE.md                ← 项目铁律（commit / push / agent routing）
  - docs/plans/<latest>.md   ← 当前 Phase/Epic 的完整 plan
===============================================================================
-->

# Next Session — <feature> <phase-code>

> **上轮 (<YYYY-MM-DD>)**: <1-2 句话，结果导向>
> **当前位置**: <Phase X 第 N/总 步>，剩 <列未完成 Phase>

---

## 1. 已完成（本轮 commits on <repo>）

```
<sha7> <type>[scope]: <subject>
<sha7> <type>[scope]: <subject>
```

**被吸收/作废**：`<sha>` → 原因（如"被 consolidate commit 替代"）

---

## 2. 下一步（进入 session 后直接做这件事）

**单元**: U<n>  **Phase**: <2C>  **模块**: `<plugin/kvm or premium/...>`

**动作 3 件**（按先后）：
1. <动作 1 — 具体到文件/方法>
2. <动作 2>
3. <动作 3>

**可并行**: <yes/no>。若 yes，列出拆分点：
- U<x> = <scope>
- U<y> = <scope>
  → 开 `/oh-my-claudecode:ultrawork` 或 `dispatch-fleet`

---

## 3. Blockers / 未补 gap

<!-- 不是"还没做"，是"发现上轮漏掉 / 依赖缺 / 等外部决策" -->

| # | 阻塞内容 | 影响哪个 U | 待办 / 归属 |
|---|---|---|---|
| 1 | <desc> | U<n> | @owner / 找 <agent-name> |

---

## 4. 本轮学到的 ⚠️（可能影响下轮）

<!-- 只写"如果下轮不知道就会踩"的教训。超过 3 条就该写 runbook。 -->

1. **<关键词>**: <一句话结论>（详见 `<文件>:<行>`）
2. ...

---

## 5. 不要重议的决策（引用 ADR，不要复述）

- [ADR-007 PSC 无 DB FK to PhysicalServerVO](../decisions/ADR-007-psc-no-fk.md)
- [ADR-012 FK rename follows parent rename](../decisions/ADR-012-fk-rename-convention.md)

<!-- 本轮若新产生决策：先写进 docs/decisions/，再加链接到这里 -->

---

## 6. Session 间保留的文件

```
conf/db/upgrade/V5.5.18__schema.sql     ← 唯一 schema 权威（请勿分裂）
docs/plans/<current-plan>.md
docs/runbooks/<relevant>.md
docs/brainstorms/next-session.md        ← 本文件
```

可删：`/tmp/*` 临时快照、本机一次性测试 DB。

---

## 7. 下 session 入口建议（直接粘贴给 Claude）

```
继续 <feature> <phase>。上轮完成 <1 句成果>。读 docs/brainstorms/next-session.md
获取完整上下文。

本 session 做第 2 节的"下一步"，按该节 3 个动作执行。
若标记为可并行，直接开 ultrawork / dispatch-fleet，按第 2 节的拆分方案分派
executor agent，每模块独立 commit + push。

铁律见 CLAUDE.md，环境见 docs/runbooks/testing-envs.md，不再复述。
```

---

<!--
===============================================================================
Checklist before handoff（写完删掉这段）:
  [ ] 第 1 节 commits 全部 push 了
  [ ] 第 2 节"下一步"具体到文件，不是"继续 Phase 2"
  [ ] 第 4 节教训都是 1 行（超过就抽 runbook）
  [ ] 第 5 节的决策都已经写成 ADR（不要只写在这里）
  [ ] 第 7 节入口 prompt 可以直接复制到下次对话
===============================================================================
-->
