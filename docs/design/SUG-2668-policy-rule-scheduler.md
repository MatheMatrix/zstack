---
summary: "支持安全组和 VPC 防火墙规则集按计划定时生效。"
version: "5.5.38"
jira_key: "SUG-2668"
jira: "http://jira.zstack.io/browse/SUG-2668"
status: "Draft"
last_updated: "2026-07-27"
---

# Func Spec: 安全组/防火墙定时生效

> `version` 固定为 `5.5.38`，后续修改本 spec 时不得变更。

## 1. 背景与范围

本需求来源于 [SUG-2668](http://jira.zstack.io/browse/SUG-2668)。用户需要为安全组或 VPC 防火墙规则集设置定时生效计划，使规则在指定时间内自动生效或失效，避免在上下班、临时开放等场景中手工启停规则。

定时计划支持一次性生效和每周重复生效。每周计划可同时限定固定日期范围、星期和每天时段。定时计划不得改变规则原有启停状态，未设置计划的安全组和防火墙规则集保持现有行为。

## 2. 功能范围

### 2.1 功能点

| 编号 | 功能点 | 说明 |
|---|---|---|
| F-01 | 定时计划管理 | <ul><li>增删改查功能</li><li>绑定、解绑</li><li>多资源复用；删除时级联解绑</li></ul> |
| F-02 | 时间基准 | UTC 和本地时间；本地时间保存平台 IANA 时区 ID。 |
| F-03 | 一次性计划 | 分钟级绝对生效区间。 |
| F-04 | 每周计划 | <ul><li>固定日期范围</li><li>星期</li><li>每日生效时段；`00:00—00:00` 表示全天</li></ul> |
| F-05 | 绑定范围 | 仅支持安全组和 VPC 防火墙规则集；单个资源只能绑定一个计划。 |
| F-06 | 周期检查 | 每分钟扫描已绑定计划的资源，仅刷新结果变化的资源。 |
| F-07 | 安全组刷新 | 生效结果变化时，按 Host 下发完整规则。 |
| F-08 | VPC 防火墙刷新 | 生效结果变化时，按关联防火墙批量替换规则集配置。 |
| F-09 | 原有启停兼容 | <ul><li>Disabled 规则始终不生效</li><li>不修改规则状态和原有启停 API 行为</li></ul> |
| F-10 | 双 MN 和并发 | <ul><li>周期检查在双 MN 中只执行一次</li><li>同一资源的周期刷新与用户操作串行执行</li></ul> |
| F-11 | 升级兼容 | `schedulerUuid` 为空的存量资源保持原行为。 |

### 2.2 非本期目标

- 不使用 iptables `time` 扩展。
- 不在 Host 或 VRouter 保存或计算定时计划。
- 不改造现有单规则/单操作 API。
- 不允许将定时计划绑定到安全组规则、VPC 防火墙或防火墙规则。
- 不支持一天多个时段、跨天时段、节假日、按月和 Cron 表达式。
- 不保存规则当前生效状态、下次切换时间和执行历史。

## 3. 后端方案设计

### 3.1 总体方案

```text
┌──────────────────────────────────────── 用户层 ────────────────────────────────────────┐
│                              用户 ── UI / CLI / SDK ──> Cloud API                      │
└────────────────────────────────────────────┬───────────────────────────────────────────┘
                                             │
┌────────────────────────────────────────────▼───────────────────────────────────────────┐
│                                      控制面（MN）                                       │
│                                                                                        │
│              安全组操作            定时计划管理、绑定/解绑          防火墙规则集操作       │
│                  │                         │                           │                 │
│                  ▼                         ▼                           ▼                 │
│  ┌────────────────────┐  计划查询   ┌────────────────────┐   计划查询  ┌────────────────────┐ │
│  │     安全组服务      │───────────>│    定时计划服务      │<──────────│   VPC 防火墙服务    │ │
│  │   安全组及其规则    │<───────────│ 定时计划与时间判定   │──────────>│ 规则集及其规则      │ │
│  └──────────┬─────────┘  资源刷新   └─────────▲──────────┘   资源刷新  └─────────┬──────────┘ │
│             ▲                              │                              ▲              │
│       VM / Host 生命周期               每分钟周期检查                 VRouter / HA 生命周期  │
└─────────────┼──────────────────────────────────────────────────────────────┼──────────────┘
              │                                                              │
              ▼                                                              ▼
┌──────────────────────────────────────── 数据面 ────────────────────────────────────────┐
│       Host 安全组规则（iptables）                         VRouter 防火墙规则（iptables） │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

### 3.2 定时计划时间规则

```text
Once

  startDate startTime                              endDate endTime
          ●════════════════ 规则生效 ═════════════════════○
          └──────────────── 左闭右开 ─────────────────────┘

Weekly

  startDate                                             endDate
      ├────────────────── 固定日期范围 ─────────────────────┤
      │ 周一  startTime ●════════════════════○ endTime       │
      │ 周二  startTime ●════════════════════○ endTime       │
      │ ...                                                  │
      │ 周五  startTime ●════════════════════○ endTime       │
      │ 全天  00:00 ═════════ 全天约定 ═════════ 00:00       │
      └──────────────────────────────────────────────────────┘
```

- 日期格式为 `yyyy-MM-dd`，时间格式为 `HH:mm`；API 不接受秒，数据库中的秒固定为 `00`。
- `Once` 的起止日期和时间组成绝对区间，开始分钟包含，结束分钟不包含。
- `Weekly` 同时满足以下三个条件时生效：当前日期位于 `startDate—endDate` 固定日期范围内、当前星期位于 `weekDays`、当前时间位于 `startTime—endTime` 每日时段。
- `Weekly` 的固定日期范围包含首尾且允许单日；`weekDays` 使用 1—7 表示星期一至星期日；`00:00—00:00` 表示所选星期全天生效，其他时段必须为同一天内的左闭右开正区间。全天约定不表示跨天时段。
- 本地时间在创建时固化为平台当前 IANA 时区 ID，例如 `Asia/Shanghai`，后续不受平台时区配置变化影响。
- 本地时间判定使用 `ZonedDateTime` 结合计划时区，含 DST 的时区以归一化结果为准。

### 3.3 绑定关系和生效规则

- 每个安全组或 VPC 防火墙规则集最多绑定一个计划，同一计划可以绑定多个资源。
- 原有状态为 `Disabled` 的规则始终不生效；其他规则在资源未绑定计划时保持原行为，绑定后仅在计划内生效。
- 计划只影响下发结果，不修改安全组规则的 `state`、防火墙规则的 `state/isApplied/expired` 或规则集发布状态。

### 3.4 周期检查任务

```text
MN-1 ThreadFacade                         MN-2 ThreadFacade
        │ 每 60 秒，对齐整分钟                    │ 每 60 秒，对齐整分钟
        ▼                                       ▼
isManagedByUs(固定任务标识)             isManagedByUs(固定任务标识)
        │ true                                  │ false
        ▼                                       └─> 跳过
 上次成功检查 T1 ───────────────── 当前分钟 T2
        │
        ├─> 按安全组分组计算 T1/T2
        │       └─> 状态变化 ──> ChangeSecurityGroupSchedulerMsg(REFRESH)
        │
        └─> 按防火墙规则集分组计算 T1/T2
                └─> 状态变化 ──> ChangeVpcFirewallRuleSetSchedulerMsg(REFRESH)
```

- `PolicyRuleSchedulerScanTask` 不使用通用 Scheduler Job/Trigger，避免生成分钟级 `SchedulerJobHistoryVO`；T1 和失败待重试资源仅保存在负责 MN 内存中。
- 用 `ThreadFacade.submitPeriodicTask` 以 60 秒为周期运行，通过初始延迟对齐到下一整分钟。
- T1/T2 取实际 wall-clock 分钟（按各计划时区归一到分钟），按两端点比较状态变化，不逐分钟回放。
- 扫描任务 `run()` 最外层捕获全部异常并记录告警（未捕获异常会使 `scheduleAtFixedRate` 永久取消任务），本轮不更新 T1。
- 失去任务归属时清空本地状态；首次取得任务归属时刷新全部安全组和 VPC 防火墙规则集。
- 扫描计算或消息投递异常时不更新 T1；资源刷新失败加入待重试集合。
- Update、Delete、Attach 和 Detach 立即刷新受影响资源，不等待周期任务；新建计划不触发刷新。
- 常规轮次仅扫描已绑定资源，仅刷新结果变化或等待重试的资源。

### 3.5 安全组和防火墙处理

| 处理项 | 安全组 | VPC 防火墙规则集 |
|---|---|---|
| 查询数据 | 安全组、规则和关联 VM 网卡 | 规则集当前已发布规则和 `VpcFirewallRuleSetL3RefVO` |
| 原有状态 | `Disabled` 规则不下发 | `Disabled` 规则保持禁用；只处理 `isApplied=true` 的当前版本 |
| 下发单位 | 每个 Host 一份完整规则 | 每个关联防火墙一条 `ApplyRuleSetChangesCmd` |
| 空结果 | 保留安全组 UUID 并下发空规则，清理 Host 旧规则 | 规则集未挂载到任何防火墙时直接成功 |
| 数据面 | `zstack-utility` 刷新 iptables 链 | `zstack-vyos` 批量替换规则集配置 |

- 防火墙规则集以 `isApplied=true` 确定当前版本；已标记 `expired` 但尚未被正式 Apply 替换的旧规则仍属于当前版本。
- `ApplyRuleSetChangesCmd.deleteRules` 携带当前版本，`newRules` 携带按计划调整后的同一版本。命令按 `VpcFirewallRuleSetL3RefVO` 关联的防火墙分组，并覆盖 HA 对端。
- `VpcFirewallRuleBuilder` 统一生成规则 TO，供规则创建、规则启停、`APIApplyRuleSetChanges`、规则集挂载、VRouter 启动/重启、HA 同步和完整刷新调用。
- 安全组规则修改、VM 迁移、Host 重连，以及 VRouter 启动、HA 同步、规则集挂载和完整配置刷新，均按执行时的计划结果生成规则。
- 安全组因计划外导致 0 条有效规则时，只要仍有关联 VM 网卡就以空规则下发对应安全组，清理 Host 旧规则，不将其从 `HostRuleTO` 中丢弃。
- 安全组 Host 失败仍写入 `SecurityGroupFailureHostVO`，不改变现有安全组 API 的成功语义。
- 防火墙规则增删改、启停和规则集修改复用 `Firewall-RuleSet-${ruleSetUuid}` 队列，公开 API 不变。
- `zstack-utility` 和 `zstack-vyos` 的重复刷新幂等。

### 3.6 并发和双 MN

```text
Update/Attach/Detach ────────────────┐
                                    ├─> PolicyRuleScheduler-${schedulerUuid}
Delete ─> Cascade ─> Delete*Msg ─────┘               │
                                                     └─> 资源本地消息
                                                             │
用户修改规则 ──────────────────────────────────────────────────────────────┤
周期检查任务 ──> Change*SchedulerMsg(REFRESH) ──────────────────────────────┤
                                                                           │
                         ┌──────────────────────────────────┴─────────────────────────────────┐
                         ▼                                                                    ▼
       SecurityGroup-${securityGroupUuid}                              Firewall-RuleSet-${ruleSetUuid}

MN-1：isManagedByUs = true ────────────────X
                                      MN 成员变化后重新分配固定任务标识
MN-2：isManagedByUs = false ─────────────────────> 下一整分钟首次全量刷新 ──> 周期检查
```

Attach/Detach 固定从计划队列调用资源消息；不同计划并发绑定同一资源时，后进入资源队列的请求重新读取绑定关系。

## 4. 模块依赖

时间策略位于新增的 `plugin-premium/policyRuleScheduler` 模块。

```text
plugin-premium/policyRuleScheduler
  |- PolicyRuleSchedulerVO / API / Cascade
  |- PolicyRuleSchedulerFacade
  |- PolicyRuleSchedulerScanTask
  |- PolicyRuleSchedulerSecurityGroupExtension
  |      implements
  |        └─ zstack/plugin/securityGroup
  |             └─ SecurityGroupRuleFilterExtensionPoint
  |
  └─ PolicyRuleSchedulerVpcFirewallExtension
         implements
           └─ plugin-premium/vpcFirewall
                └─ VpcFirewallRuleStateExtensionPoint
```

| 模块 | 本期职责 |
|---|---|
| `plugin-premium/policyRuleScheduler` | 保存定时计划并提供 API、周期检查；`PolicyRuleSchedulerFacade` 负责可绑定性、当前生效状态和批量计算；实现两类规则扩展。 |
| `zstack/plugin/securityGroup` | 增加 `schedulerUuid`、`ChangeSecurityGroupSchedulerMsg` 和规则过滤扩展点；`RuleCalculator` 在原有 `Enabled` 过滤后调用扩展。 |
| `plugin-premium/vpcFirewall` | 增加 `schedulerUuid`、`ChangeVpcFirewallRuleSetSchedulerMsg`、规则状态扩展点和 `VpcFirewallRuleBuilder`，复用现有批量下发。 |
| `zstack-utility` / `zstack-vyos` | 不解析定时计划，处理 MN 下发的最终规则并补充必要的幂等适配。 |

`policyRuleScheduler` 依赖安全组和 VPC 防火墙模块；资源模块只依赖各自定义的扩展接口，不依赖计划表或时间计算实现。
未注册扩展实现时，资源模块直接使用原规则结果。

## 5. 数据库表设计

```sql
-- premium：安全策略定时计划
CREATE TABLE IF NOT EXISTS `PolicyRuleSchedulerVO` (
    `uuid` varchar(32) NOT NULL,                                            -- 计划 UUID
    `name` varchar(255) NOT NULL,                                          -- 计划名称
    `description` varchar(2048) DEFAULT NULL,                              -- 计划描述
    `timeZone` varchar(64) NOT NULL,                                       -- UTC 或 IANA 时区 ID，例如 Asia/Shanghai
    `repeatType` varchar(32) NOT NULL,                                     -- Once 或 Weekly
    `startDate` date NOT NULL,                                             -- Once 的开始日期或 Weekly 固定日期范围的开始日期
    `endDate` date NOT NULL,                                               -- Once 的结束日期或 Weekly 固定日期范围的结束日期
    `startTime` time NOT NULL,                                             -- Once 或 Weekly 的开始时间；Weekly 全天时为 00:00:00
    `endTime` time NOT NULL,                                               -- Once 或 Weekly 的结束时间；Weekly 全天时为 00:00:00
    `weekDays` varchar(32) DEFAULT NULL,                                   -- Weekly 使用，1 至 7 逗号分隔；Once 为空
    `createDate` timestamp NOT NULL DEFAULT '0000-00-00 00:00:00',         -- 创建时间
    `lastOpDate` timestamp NOT NULL DEFAULT '0000-00-00 00:00:00'
        ON UPDATE CURRENT_TIMESTAMP,                                      -- 最后修改时间
    PRIMARY KEY (`uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- zstack：安全组的计划引用
ALTER TABLE `SecurityGroupVO`
    ADD COLUMN `schedulerUuid` varchar(32) DEFAULT NULL; -- 安全组使用的定时计划 UUID

-- premium：VPC 防火墙规则集的计划引用
ALTER TABLE `VpcFirewallRuleSetVO`
    ADD COLUMN `schedulerUuid` varchar(32) DEFAULT NULL; -- 防火墙规则集使用的定时计划 UUID
```

```java
@BaseResource
@Entity
@Table
class PolicyRuleSchedulerVO extends ResourceVO implements OwnedByAccount {
    @Transient
    String accountUuid;
}
```

安全组和防火墙规则集通过 `schedulerUuid` 引用计划，该挂载关系不增加关联表和数据库外键。`SecurityGroupVO.schedulerUuid` 落在开源 `zstack` 表，仅由 premium 填充；计划语义只在 premium 解析。

`PolicyRuleSchedulerVO` 不保存当前生效状态或可绑定状态。两者都由计划内容、时区和当前分钟计算；绑定计划时，只有仍存在未来生效分钟的计划才允许绑定。

删除账号时级联删除其计划。删除计划时级联清空安全组和防火墙规则集的 `schedulerUuid` 并刷新受影响资源；删除安全组或防火墙规则集不删除可复用计划。

## 6. API 设计

### 6.1 APICreatePolicyRuleScheduler

#### API

```java
@Action(category = "policyRuleScheduler")
@TagResourceType(PolicyRuleSchedulerVO.class)
@RestRequest(path = "/policy-rule-schedulers", method = POST,
        responseClass = APICreatePolicyRuleSchedulerEvent.class)
class APICreatePolicyRuleSchedulerMsg extends APICreateMessage {
    @APIParam(maxLength = 255)                    String name;
    @APIParam(required = false, maxLength = 2048) String description;
    @APIParam                                     String timeZone;    // UTC 或 IANA 时区 ID
    @APIParam(validValues = {"Once", "Weekly"})   String repeatType;
    @APIParam                                     String startDate;   // yyyy-MM-dd；Weekly 固定日期范围开始
    @APIParam                                     String endDate;     // yyyy-MM-dd；Weekly 固定日期范围结束
    @APIParam                                     String startTime;   // HH:mm；Weekly 全天时与 endTime 均为 00:00
    @APIParam                                     String endTime;     // HH:mm
    @APIParam(required = false)                   List<Integer> weekDays;
}
```

#### Validate

- `timeZone` 必须为 `UTC` 或有效的 IANA 时区 ID；日期和时间分别使用 `yyyy-MM-dd`、`HH:mm`。
- `Once`：`weekDays` 为空，按 `timeZone` 合成的绝对时间范围必须为正区间。
- `Weekly`：`startDate` 不晚于 `endDate`；`weekDays` 非空、不重复且取值为 1—7；每日时段仅允许 `00:00—00:00`（全天）或同日正区间。

#### Workflow

```text
APICreatePolicyRuleSchedulerMsg
  |
  └─ persist PolicyRuleSchedulerVO
```

### 6.2 APIUpdatePolicyRuleScheduler

#### API

```java
@Action(category = "policyRuleScheduler")
@RestRequest(path = "/policy-rule-schedulers/{uuid}/actions", method = PUT, isAction = true,
        responseClass = APIUpdatePolicyRuleSchedulerEvent.class)
class APIUpdatePolicyRuleSchedulerMsg extends APIMessage
        implements PolicyRuleSchedulerMessage {
    @APIParam(resourceType = PolicyRuleSchedulerVO.class, checkAccount = true, operationTarget = true)
    String uuid;
    @APIParam(maxLength = 255)                    String name;
    @APIParam(required = false, maxLength = 2048) String description;
    @APIParam                                     String timeZone;
    @APIParam(validValues = {"Once", "Weekly"})   String repeatType;
    @APIParam                                     String startDate;
    @APIParam                                     String endDate;
    @APIParam                                     String startTime;   // Weekly 全天时与 endTime 均为 00:00
    @APIParam                                     String endTime;
    @APIParam(required = false)                   List<Integer> weekDays;

    @Override
    String getSchedulerUuid() { return uuid; }
}
```

#### Validate

- 请求全量覆盖计划（PUT 全量替换，非字段级 patch），不读取数据库原值补齐；必填字段缺失时失败，`description` 未提交时清空。
- `Once` 要求 `weekDays` 为空并保存为 `NULL`；`Weekly` 必须提交非空 `weekDays`；全量替换后的时间表达按创建 API 校验，包括 `Weekly` 的全天约定。
- 更新后可以没有未来生效分钟；已有绑定保留并立即刷新，新的 Attach 被拒绝。

#### Workflow

```text
APIUpdatePolicyRuleSchedulerMsg
  |
  |- Flow["update-policy-rule-scheduler"]
      |
      |- [then]: Flow["update-scheduler"]
      |   |
      |   |- load current scheduler
      |   |- [time expression changed]: load referenced resources
      |   |- replace PolicyRuleSchedulerVO
      |   |
      |   └─ [rollback]: "restore-scheduler"
      |       |
      |       |- restore old PolicyRuleSchedulerVO
      |       └─ [time expression changed]: refresh referenced resources
      |
      └─ [then, time expression changed]: NoRollbackFlow["refresh-referenced-resources"]
          |
          |- ChangeSecurityGroupSchedulerMsg(securityGroupUuid, operation=REFRESH)
          └─ ChangeVpcFirewallRuleSetSchedulerMsg(ruleSetUuid, operation=REFRESH)
```

只修改名称或描述时不刷新资源。恢复动作失败时保留原始错误并记录告警。

### 6.3 APIDeletePolicyRuleScheduler

#### API

```java
@Action(category = "policyRuleScheduler")
@RestRequest(path = "/policy-rule-schedulers/{uuid}", method = DELETE,
        responseClass = APIDeletePolicyRuleSchedulerEvent.class)
class APIDeletePolicyRuleSchedulerMsg extends APIDeleteMessage {
    @APIParam(resourceType = PolicyRuleSchedulerVO.class, checkAccount = true,
            operationTarget = true, successIfResourceNotExisting = true)
    String uuid;
}
```

#### Validate

- 被引用计划允许删除，由 Cascade 解绑。

#### Workflow

```text
APIDeletePolicyRuleSchedulerMsg
  |
  └─ CascadeFacade
      |
      └─ PolicyRuleSchedulerCascadeExtension
          |
          └─ DeletePolicyRuleSchedulerMsg(schedulerUuid)
              |
              |- [then]: Flow["delete-scheduler"]
              |   |
              |   |- load referenced resources
              |   └─ SQLBatch
              |       |
              |       |- clear SecurityGroupVO.schedulerUuid
              |       |- clear VpcFirewallRuleSetVO.schedulerUuid
              |       └─ remove PolicyRuleSchedulerVO
              |
              └─ [then]: NoRollbackFlow["refresh-unbound-resources"]
                  |
                  |- ChangeSecurityGroupSchedulerMsg(securityGroupUuid, operation=REFRESH)
                  └─ ChangeVpcFirewallRuleSetSchedulerMsg(ruleSetUuid, operation=REFRESH)
```

清空引用和删除计划在同一个数据库事务中完成。资源刷新采用 best-effort，失败不恢复已删除计划。

### 6.4 APIQueryPolicyRuleScheduler

#### API

```java
@Action(category = "policyRuleScheduler", names = {"read"})
@RestRequest(path = "/policy-rule-schedulers",
        optionalPaths = {"/policy-rule-schedulers/{uuid}"}, method = GET,
        responseClass = APIQueryPolicyRuleSchedulerReply.class)
@AutoQuery(replyClass = APIQueryPolicyRuleSchedulerReply.class,
        inventoryClass = PolicyRuleSchedulerInventory.class)
class APIQueryPolicyRuleSchedulerMsg extends APIQueryMessage {}
```

#### Validate

- 无。

#### Workflow

```text
APIQueryPolicyRuleSchedulerMsg
  |
  |- AutoQuery["query-policy-rule-scheduler"]
      |
      |- Query PolicyRuleSchedulerVO
```

### 6.5 APIAttachPolicyRuleScheduler

#### API

```java
@Action(category = "policyRuleScheduler")
@RestRequest(path = "/policy-rule-schedulers/actions", method = PUT, isAction = true,
        responseClass = APIAttachPolicyRuleSchedulerEvent.class)
class APIAttachPolicyRuleSchedulerMsg extends APIMessage
        implements PolicyRuleSchedulerMessage {
    @APIParam(resourceType = PolicyRuleSchedulerVO.class, checkAccount = true)
    String schedulerUuid;
    @APIParam(validValues = {"SecurityGroup", "VpcFirewallRuleSet"})
    String resourceType;
    @APIParam(resourceType = ResourceVO.class, checkAccount = true, operationTarget = true)
    String resourceUuid;

    @Override
    String getSchedulerUuid() { return schedulerUuid; }
}
```

```text
resourceType
  |- SecurityGroup         -> SecurityGroupVO
  └─ VpcFirewallRuleSet    -> VpcFirewallRuleSetVO
```

#### Validate

- `resourceUuid` 必须存在且匹配 `resourceType`，不尝试推断其他类型。
- 计划在校验时必须仍有未来生效分钟。
- 目标已绑定时拒绝；更换计划需先解绑。

#### Workflow

```text
APIAttachPolicyRuleSchedulerMsg
  |
  |- reload and validate latest scheduler
  |
  |- [resourceType = SecurityGroup]:
  |      ChangeSecurityGroupSchedulerMsg(
  |          securityGroupUuid, schedulerUuid, operation=ATTACH)
  |
  └─ [resourceType = VpcFirewallRuleSet]:
         ChangeVpcFirewallRuleSetSchedulerMsg(
             ruleSetUuid, schedulerUuid, operation=ATTACH)
```

### 6.6 APIDetachPolicyRuleScheduler

#### API

```java
@Action(category = "policyRuleScheduler")
@RestRequest(path = "/policy-rule-schedulers/actions", method = PUT, isAction = true,
        responseClass = APIDetachPolicyRuleSchedulerEvent.class)
class APIDetachPolicyRuleSchedulerMsg extends APIMessage
        implements PolicyRuleSchedulerMessage {
    @APIParam(resourceType = PolicyRuleSchedulerVO.class, checkAccount = true)
    String schedulerUuid;
    @APIParam(validValues = {"SecurityGroup", "VpcFirewallRuleSet"})
    String resourceType;
    @APIParam(resourceType = ResourceVO.class, checkAccount = true, operationTarget = true)
    String resourceUuid;

    @Override
    String getSchedulerUuid() { return schedulerUuid; }
}
```

#### Validate

- `resourceUuid` 必须存在且匹配 `resourceType`，不尝试推断其他类型。
- 目标当前的 `schedulerUuid` 必须与请求一致。
- 解绑不校验计划时间。

#### Workflow

```text
APIDetachPolicyRuleSchedulerMsg
  |
  |- [resourceType = SecurityGroup]:
  |      ChangeSecurityGroupSchedulerMsg(
  |          securityGroupUuid, schedulerUuid, operation=DETACH)
  |
  └─ [resourceType = VpcFirewallRuleSet]:
         ChangeVpcFirewallRuleSetSchedulerMsg(
             ruleSetUuid, schedulerUuid, operation=DETACH)
```

安全组和 VPC 防火墙规则集的查询结果返回 `schedulerUuid`，用于显示当前绑定关系。

## 7. 本地消息设计与场景处理

### 7.1 消息定义

```text
API Attach ──────────────────────> Change*SchedulerMsg(ATTACH)
API Detach ──────────────────────> Change*SchedulerMsg(DETACH)
API Update / Delete / 周期检查 ─> Change*SchedulerMsg(REFRESH)
```

```java
interface PolicyRuleSchedulerMessage {
    String getSchedulerUuid();
}

class DeletePolicyRuleSchedulerMsg extends NeedReplyMessage
        implements PolicyRuleSchedulerMessage {
    String schedulerUuid;
}

class ChangeSecurityGroupSchedulerMsg extends NeedReplyMessage
        implements SecurityGroupMessage {
    enum Operation { ATTACH, DETACH, REFRESH }

    String securityGroupUuid;
    String schedulerUuid;
    Operation operation;
}

class ChangeVpcFirewallRuleSetSchedulerMsg extends NeedReplyMessage
        implements VpcFirewallRuleSetMessage {
    enum Operation { ATTACH, DETACH, REFRESH }

    String ruleSetUuid;
    String schedulerUuid;
    Operation operation;
}
```

`ATTACH`、`DETACH` 必须携带 `schedulerUuid`；`REFRESH` 不携带该字段，也不修改绑定关系。消息处理时读取最新规则和绑定，不携带计划快照。

### 7.2 安全组消息

```text
ChangeSecurityGroupSchedulerMsg
  |
  └─ ChainTask["SecurityGroup-${securityGroupUuid}"]
      |
      └─ Flow["change-security-group-scheduler"]
          |
          |- [then, ATTACH / DETACH]: Flow["change-scheduler-binding"]
          |   |
          |   |- ATTACH: require empty binding, then set schedulerUuid
          |   |- DETACH: require matched binding, then clear schedulerUuid
          |   |
          |   └─ [rollback]: "restore-scheduler-binding"
          |       |
          |       |- restore schedulerUuid
          |       └─ apply rules with restored binding
          |
          └─ [then]: NoRollbackFlow["apply-security-group-rules"]
              |
              └─ SecurityGroupManagerImpl.RuleCalculator
                  |
                  |- query rules and VM NIC references
                  |- filter original Disabled rules
                  |- SecurityGroupRuleFilterExtensionPoint
                  |    |
                  |    └─ PolicyRuleSchedulerSecurityGroupExtension
                  |         └─ PolicyRuleSchedulerFacade.isInSchedule(...)
                  |
                  └─ group by Host and apply complete rules
```

`SecurityGroupRuleFilterExtensionPoint` 同时接入 KVM 和 SDN 两条 `RuleCalculator` 规则生成路径。计划外返回空规则列表，但保留安全组 UUID 和 VM 网卡关系，用于清理 Host 上的旧规则。

Host 下发失败沿用安全组现有处理，写入 `SecurityGroupFailureHostVO`，不触发绑定关系回滚；规则计算或下发前的控制面失败进入 Flow rollback。

### 7.3 VPC 防火墙规则集消息

```text
ChangeVpcFirewallRuleSetSchedulerMsg
  |
  └─ ChainTask["Firewall-RuleSet-${ruleSetUuid}"]
      |
      └─ Flow["change-vpc-firewall-rule-set-scheduler"]
          |
          |- [then, ATTACH / DETACH]: Flow["change-scheduler-binding"]
          |   |
          |   |- ATTACH: require empty binding, then set schedulerUuid
          |   |- DETACH: require matched binding, then clear schedulerUuid
          |   |
          |   └─ [rollback]: "restore-scheduler-binding"
          |       |
          |       |- restore schedulerUuid
          |       └─ apply rules with restored binding
          |
          └─ [then]: NoRollbackFlow["apply-vpc-firewall-rules"]
              |
              |- query isApplied rules and firewall references
              |- VpcFirewallRuleBuilder
              |    |
              |    └─ VpcFirewallRuleStateExtensionPoint
              |         |
              |         └─ PolicyRuleSchedulerVpcFirewallExtension
              |              └─ PolicyRuleSchedulerFacade.isInSchedule(...)
              |
              ├─ deleteRules = current applied rules
              ├─ newRules = rules built for current schedule
              └─ 按关联防火墙及 HA 对端发送 ApplyRuleSetChangesCmd
```

`VpcFirewallRuleStateExtensionPoint` 返回需要强制禁用的规则 UUID；`VpcFirewallRuleBuilder` 再与规则原有状态合并，不修改规则 VO。

任一 VRouter 下发失败时，`ATTACH`、`DETACH` 恢复原绑定并按原绑定重新下发；`REFRESH` 返回错误，由调用方按最新数据库状态重试。

### 7.4 场景处理

- **计划在 Attach 处理期间结束**：计划队列执行时重新校验；计划已不存在未来生效分钟时不发送资源变更消息。
- **绑定关系变更处理时资源已删除**：`ATTACH`、`DETACH` 返回资源不存在错误；晚到的 `REFRESH` 直接成功。
- **计划引用不存在**：作为异常数据按未绑定计划生成规则并记录告警。
- **同一计划同时影响多个资源**：按资源 UUID 去重并有界并发发送消息。

| 操作 | 失败处理 |
|---|---|
| Attach/Detach | 恢复原绑定，再按原绑定刷新整个资源。 |
| Update | 恢复旧计划，再按旧计划刷新全部关联资源。 |
| Delete | 清空引用和删除计划后不回滚；记录失败，等待资源完整刷新。 |
| 周期检查 | 不修改控制面，失败目标按最新状态重试。 |

恢复动作失败时保留原始错误并记录告警，后续 VRouter 重连或完整配置刷新按最新数据库状态覆盖。

## 8. 后端 UT自测

只增加 Groovy 集成 Case，并通过注入时间主动触发周期检查，不等待真实分钟。

```groovy
class PolicyRuleSchedulerApiCase extends PremiumSubCase {
    EnvPremiumSpec env

    void testPolicyRuleSchedulerLifecycle() {
        // 场景：用户完成定时计划的创建、全量修改、绑定、解绑和删除。
        // 准备：创建安全组和防火墙规则集，并构造 UTC Once、本地时区 Weekly 普通时段和全天计划。
        // 执行：创建并查询计划；将 Once 全量修改为 Weekly；分别绑定两个资源，
        //      推进到计划结束后解绑安全组，再删除仍被规则集引用的计划。
        // 校验：分钟、时区、日期、weekDays 和 00:00—00:00 原样返回；
        //      PUT 未提交的 description 被清空；已结束计划仍可解绑；
        //      删除后计划不存在，规则集 schedulerUuid 为空。
    }

    void testPolicyRuleSchedulerAccountIsolation() {
        // 场景：普通账号只能操作自己创建的定时计划。
        // 准备：账号 A 创建计划并绑定自己的安全组，账号 B 创建另一个安全组。
        // 执行：账号 B 查询、修改、删除账号 A 的计划，并尝试将该计划绑定到自己的安全组。
        // 校验：账号 B 查询不到该计划，其余请求均失败；计划、原绑定及数据面状态不变。
    }

    void testInvalidSchedulerOperationsKeepExistingState() {
        // 场景：用户提交非法时间表达式或对已有绑定执行冲突操作。
        // 准备：创建一个有效计划并绑定安全组，记录计划、绑定和 Host 下发结果。
        // 执行：分别提交非法时区、秒级时间、非法区间和非法 weekDays；再使用不匹配的
        //      resourceType、其他计划和错误 schedulerUuid 操作该安全组。
        // 校验：请求均失败；计划内容、原 schedulerUuid 和 Host 规则保持不变；
        //      已结束计划不能新绑定，失败操作不产生引用。
    }

    void testDeleteAccountCascadesPolicyRuleScheduler() {
        // 场景：账号删除时级联删除其定时计划和已绑定的安全组、规则集。
        // 准备：普通账号创建计划、安全组和防火墙规则集并完成绑定。
        // 执行：删除账号。
        // 校验：计划随账号删除，两类资源删除后数据库中无 schedulerUuid 残留。
    }

    void testSharedSchedulerLifecycleAcrossResources() {
        // 场景：同一计划被多个安全组和防火墙规则集复用。
        // 准备：两个安全组和两个防火墙规则集绑定同一计划，各资源均有原状态允许生效的规则。
        // 执行：全量修改计划使当前分钟由计划外变为计划内，再保持绑定直接删除计划。
        // 校验：Update 刷新全部四个资源并使规则生效；Delete 后计划不存在，
        //      四个资源的 schedulerUuid 均为空且按未绑定计划刷新，资源及规则自身未被删除。
    }

    void testSharedSchedulerUpdateRollsBackAllResourcesOnFailure() {
        // 场景：修改一份被多个资源复用的计划时，其中一个 VRouter 刷新失败。
        // 准备：安全组和两个防火墙规则集绑定同一计划，旧计划处于计划外，新计划处于计划内；
        //      记录各资源的下发结果，并让最后一个 VRouter 失败。
        // 执行：全量修改计划。
        // 校验：Update 返回原始刷新错误并恢复旧计划；此前已成功刷新的安全组、规则集和 VRouter
        //      均再次按旧计划刷新，全部资源保持原 schedulerUuid，最终数据面与旧计划一致。
    }

    @Override
    void test() {
        env.create {
            testPolicyRuleSchedulerLifecycle()
            testPolicyRuleSchedulerAccountIsolation()
            testInvalidSchedulerOperationsKeepExistingState()
            testDeleteAccountCascadesPolicyRuleScheduler()
            testSharedSchedulerLifecycleAcrossResources()
            testSharedSchedulerUpdateRollsBackAllResourcesOnFailure()
        }
    }
}

class SecurityGroupScheduledActivationCase extends PremiumSubCase {
    EnvPremiumSpec env

    void testSecurityGroupWorkdayScheduleLifecycle() {
        // 场景：安全组在工作日期和工作时段内生效，计划外恢复原有启停语义。
        // 准备：安全组包含 Enabled、Disabled 规则并绑定 Asia/Shanghai 时区、固定日期范围内
        //      周一至周五 09:00—18:00 的 Weekly 计划。
        // 执行：以 UTC 时刻覆盖本地日期范围外、周末、08:59、09:00、17:59、18:00；
        //      再将计划改为 00:00—00:00 全天并解绑。
        // 校验：只有日期、星期和时段同时匹配时下发 Enabled 规则；全天计划覆盖所选日全部分钟；
        //      Disabled 规则始终不下发，解绑后恢复原行为，规则 state 从未改变；
        //      计划外空结果仍携带安全组 UUID并清理 Host 旧规则。
    }

    void testSecurityGroupLifecycleRefreshUsesCurrentSchedule() {
        // 场景：安全组因 VM 网卡加入、VM 迁移和 Host 重连重新下发规则。
        // 准备：两个 Host 上运行可迁移 VM，安全组绑定当前处于计划外的 Weekly 计划。
        // 执行：依次将安全组加入 VM 网卡、迁移 VM、重连目标 Host；随后推进到计划内并再次重连。
        // 校验：前三次完整规则均不包含定时规则；计划内重连包含原状态为 Enabled 的规则；
        //      所有下发均读取执行时的计划，安全组和规则 state 不变。
    }

    void testSecurityGroupScheduleFiltersKvmAndSdnRules() {
        // 场景：同一定时计划通过 KVM 和 SDN 两条安全组规则生成路径下发。
        // 准备：分别创建使用 KVM、SDN 规则生成路径的 VM 网卡，并关联同一安全组；
        //      安全组绑定当前处于计划外的计划。
        // 执行：分别触发两类规则刷新，再推进到计划内重复刷新。
        // 校验：计划外两条路径均过滤定时规则，计划内均恢复原 Enabled 规则；
        //      两条路径使用同一计划结果且不修改规则 state。
    }

    void testSecurityGroupLargeRuleSetRefreshesPerHost() {
        // 场景：包含大量规则的安全组在分钟边界按资源和 Host 批量刷新。
        // 准备：两个 Host 上的 VM 使用同一安全组，安全组有 100 条 Enabled 规则；
        //      安全组绑定 09:00—09:01 生效的 Once 计划并加入两个 VM 网卡；
        //      在 08:59 触发一次周期检查后清空消息和 Host 模拟器计数。
        // 执行：在 09:00 触发周期检查，在同一分钟重复检查，再推进到 09:01 检查。
        // 校验：09:00 的 100 条规则状态切换只产生一个
        //      ChangeSecurityGroupSchedulerMsg(operation=REFRESH)，
        //      两个 Host 各收到一个 ApplySecurityGroupRuleCmd，且按端口包含全部 100 条
        //      业务规则；同一分钟重复检查不产生刷新消息和 Host 请求；
        //      09:01 仍只有一个 REFRESH 消息，每个相关 Host 只收到一个 ApplySecurityGroupRuleCmd，
        //      100 条定时业务规则均不在下发结果中；
        //      安全组的 schedulerUuid 和 100 条规则的原始 state 均未改变。
    }

    void testSecurityGroupSchedulerChangeWithHostFailure() {
        // 场景：安全组绑定计划时一个 Host 下发失败，沿用安全组现有失败处理。
        // 准备：两个 Host 上的 VM 使用同一安全组，其中一个 Host 首次下发失败。
        // 执行：绑定计划，恢复失败 Host 后触发失败记录重试。
        // 校验：绑定 API 成功并保存 schedulerUuid；成功 Host 使用计划结果；
        //      失败 Host 生成 SecurityGroupFailureHostVO，重试读取当前绑定并最终一致。
    }

    @Override
    void test() {
        env.create {
            testSecurityGroupWorkdayScheduleLifecycle()
            testSecurityGroupLifecycleRefreshUsesCurrentSchedule()
            testSecurityGroupScheduleFiltersKvmAndSdnRules()
            testSecurityGroupLargeRuleSetRefreshesPerHost()
            testSecurityGroupSchedulerChangeWithHostFailure()
        }
    }
}

class VpcFirewallScheduledActivationCase extends PremiumSubCase {
    EnvPremiumSpec env

    void testVpcFirewallRuleSetWorkdayScheduleLifecycle() {
        // 场景：一个规则集挂载到多个防火墙后，按工作日计划统一控制所有可用规则。
        // 准备：两个 VPC 防火墙关联同一规则集，规则集包含 Enabled、Disabled 规则，
        //      并绑定 UTC 时区、带固定日期范围的 Weekly 计划。
        // 执行：覆盖日期、星期和分钟边界；在计划外和计划内分别触发 VRouter 完整配置刷新，
        //      重复一次相同的完整刷新，并在同一分钟重复周期检查。
        // 校验：计划外规则集内所有规则为 disable；计划内只有原有状态允许生效的规则为 enable；
        //      两个关联 VRouter 均收到批量命令，未关联防火墙不收到请求；
        //      重复批量替换结果一致，数据库 state、isApplied、expired 和规则集发布状态不变。
    }

    void testVpcFirewallScheduledRefreshUsesPublishedRuleVersion() {
        // 场景：规则集存在尚未正式 Apply 的新版本时，定时刷新仍使用当前已发布版本。
        // 准备：修改已发布规则，使旧规则为 isApplied=true、expired=true，新规则尚未应用；
        //      规则集绑定当前处于计划内的定时计划。
        // 执行：先触发定时刷新，再调用 APIApplyRuleSetChanges，随后再次触发完整刷新。
        // 校验：正式 Apply 前的批量命令仍替换旧版本且不包含新规则；
        //      Apply 后的完整刷新使用新版本，定时刷新不改变版本字段。
    }

    void testVpcFirewallLargeRuleSetRefreshesInOneBatch() {
        // 场景：包含 100 条规则的规则集跨过分钟边界。
        // 准备：单个非 HA 防火墙关联包含 100 条 Enabled 规则的规则集，
        //      规则集绑定 09:00—09:01 生效的 Once 计划。
        // 执行：在 09:00、同一分钟和 09:01 分别触发周期检查。
        // 校验：09:00 和 09:01 各产生一个
        //      ChangeVpcFirewallRuleSetSchedulerMsg(operation=REFRESH)；
        //      每次仅向该防火墙发送一条包含 100 条规则的 ApplyRuleSetChangesCmd；
        //      同一分钟不重复发送，规则状态和发布状态不变。
    }

    void testVpcFirewallAttachAndDetachRollbackOnPartialFailure() {
        // 场景：同一规则集的计划绑定和解绑分别遇到 VRouter 部分失败。
        // 准备：规则集挂载到两个防火墙并记录未绑定结果。
        // 执行：依次注入 Attach 失败、成功 Attach 和 Detach 失败；
        //      每次均让第一个 VRouter 成功、第二个 VRouter 失败。
        // 校验：失败 Attach 后 schedulerUuid 仍为空；失败 Detach 后保留原绑定；
        //      第一个 VRouter 每次均恢复到操作前结果；
        //      恢复命令失败时保留原始 API 错误，完整配置刷新后与当前数据库状态一致。
    }

    void testVpcFirewallPeriodicRefreshRetriesLatestState() {
        // 场景：分钟边界刷新两个 VRouter 时部分失败，重试前计划再次跨过边界。
        // 准备：规则集绑定 09:00—09:01 生效的 Once 计划并关联两个 VRouter；
        //      第一个 VRouter 正常，第二个 VRouter 在 09:00 刷新失败。
        // 执行：09:00 触发周期检查；恢复第二个 VRouter 后在 09:01 再次触发。
        // 校验：09:00 已成功的 VRouter 不因部分失败回退；09:01 两个 VRouter 均按当前计划
        //      收到 disable，失败目标不补发已经过时的 enable，待重试记录被清除。
    }

    void testDeleteSchedulerKeepsCascadeResultOnRefreshFailure() {
        // 场景：删除仍被规则集引用的计划时，VRouter 刷新失败。
        // 准备：规则集绑定当前生效的计划，删除时注入 VRouter 请求失败。
        // 执行：删除计划，随后恢复并重连 VRouter 以触发完整配置刷新。
        // 校验：删除 API 成功，计划不存在，规则集 schedulerUuid 为空；
        //      删除结果不因数据面失败回滚，
        //      重连后规则集按未绑定计划状态生效。
    }

    @Override
    void test() {
        env.create {
            testVpcFirewallRuleSetWorkdayScheduleLifecycle()
            testVpcFirewallScheduledRefreshUsesPublishedRuleVersion()
            testVpcFirewallLargeRuleSetRefreshesInOneBatch()
            testVpcFirewallAttachAndDetachRollbackOnPartialFailure()
            testVpcFirewallPeriodicRefreshRetriesLatestState()
            testDeleteSchedulerKeepsCascadeResultOnRefreshFailure()
        }
    }
}

class PolicyRuleSchedulerMultiManagementNodeCase extends PremiumSubCase {
    EnvPremiumSpec env

    void testPeriodicTaskOwnershipAndFailover() {
        // 场景：双 MN 中只有 Owner 扫描，Owner 切换后重新计算当前计划结果。
        // 准备：创建已绑定和未绑定计划的安全组、规则集，由 MN-1 完成一次扫描，
        //      并保留一个周期刷新失败的资源。
        // 执行：两个 MN 在同一分钟触发任务；随后停止 MN-1 并让 MN-2 接管。
        // 校验：接管前只有 MN-1 发送消息；MN-2 首轮刷新全部安全组和规则集，
        //      失败资源按当前数据库状态恢复；后续只刷新状态变化或等待重试的资源。
    }

    void testPeriodicTaskContinuesAfterScanFailure() {
        // 场景：某轮扫描抛出异常后，周期任务不会停止或跳过时间边界。
        // 准备：记录上次成功检查分钟 T1，并让本轮安全组或防火墙规则集计算抛出异常。
        // 执行：连续触发失败分钟和下一分钟的周期任务。
        // 校验：失败轮次不更新 T1，周期任务没有被取消；下一轮仍从原 T1 计算状态变化并发送刷新。
    }

    @Override
    void test() {
        env.create {
            testPeriodicTaskOwnershipAndFailover()
            testPeriodicTaskContinuesAfterScanFailure()
        }
    }
}

class PolicyRuleSchedulerConcurrencyCase extends PremiumSubCase {
    EnvPremiumSpec env

    void testConcurrentAttachUsesResourceQueue() {
        // 场景：两个不同计划同时绑定同一个资源。
        // 准备：创建两个可绑定计划和一个未绑定目标资源。
        // 执行：并发向同一安全组绑定两个计划，再对防火墙规则集重复该场景。
        // 校验：每个资源只有一个 Attach 成功，另一个返回已绑定；schedulerUuid、Host/VRouter
        //      最终状态均对应成功计划，没有混合两次请求的结果。
    }

    void testSchedulerOperationsUseSchedulerQueue() {
        // 场景：同一计划的 Update、Attach 和 Delete 来自不同 MN。
        // 准备：阻塞 Update，并在另一个 MN 依次提交 Attach 和 Delete。
        // 执行：确认后续请求未越过 Update，再释放阻塞。
        // 校验：三个操作按进入计划队列的顺序执行；Delete 最终清空全部匹配引用并删除计划；
        //      排在 Delete 之后的 Attach 返回计划不存在，不产生孤儿引用。
    }

    void testSecurityGroupRuleChangeDuringScheduledRefresh() {
        // 场景：安全组计划刷新与规则修改并发。
        // 准备：阻塞 Attach 的 Host 刷新响应，随后提交规则 state 修改和 Detach。
        // 执行：释放 Host 响应并等待资源队列处理完成。
        // 校验：最终 schedulerUuid 为空、规则 state 等于修改值；最后一次 Host 规则命令与该末态一致；
        //      规则自身不增加 schedulerUuid。
    }

    void testVpcFirewallRuleChangeDuringScheduledRefresh() {
        // 场景：规则集计划刷新与规则增删改、启停并发。
        // 准备：周期刷新已开始并阻塞 VRouter 响应。
        // 执行：提交创建规则、修改规则、启停规则和修改规则集操作后释放刷新。
        // 校验：上述操作按提交顺序完成；最后一次批量规则命令与数据库末态一致，
        //      没有旧刷新覆盖后续用户操作。
    }

    void testAttachAndUpdateUseSameSchedulerQueue() {
        // 场景：Attach 与把计划修改为已结束状态的 Update 并发。
        // 准备：分别构造 Update 先进入队列和 Attach 先进入队列两个资源。
        // 执行：并发提交两个 API。
        // 校验：Update 先执行时 Attach 因计划不可绑定失败；Attach 先执行时绑定成功，
        //      随后的 Update 保留绑定并按已结束计划刷新，不出现处理中途二次判定。
    }

    void testLateRefreshUsesCurrentBinding() {
        // 场景：旧计划产生的刷新消息晚于解绑和重新绑定到达资源队列。
        // 准备：安全组和防火墙规则集均绑定计划 A，阻塞两类 REFRESH 消息进入资源队列；
        //      计划 A 当前生效，计划 B 当前不生效。
        // 执行：两个资源依次解绑计划 A、绑定计划 B，再释放旧 REFRESH 消息。
        // 校验：两个资源仍绑定计划 B；晚到消息按计划 B 生成最终规则，
        //      不恢复计划 A 的引用或数据面结果。
    }

    void testLateSecurityGroupRefreshAfterResourceDeletion() {
        // 场景：安全组周期刷新进入资源队列前，安全组已被用户删除。
        // 准备：通过周期检查产生 ChangeSecurityGroupSchedulerMsg(operation=REFRESH)，
        //      并阻塞其进入安全组资源队列。
        // 执行：调用公开 API 删除安全组，再释放刷新消息。
        // 校验：刷新消息成功返回且不向 Host 下发；不新增 SecurityGroupFailureHostVO，
        //      已删除安全组和关联记录均未被重新创建。
    }

    void testLateVpcFirewallRuleSetRefreshAfterResourceDeletion() {
        // 场景：防火墙规则集周期刷新进入资源队列前，规则集已被用户删除。
        // 准备：通过周期检查产生 ChangeVpcFirewallRuleSetSchedulerMsg(operation=REFRESH)，
        //      并阻塞其进入规则集资源队列。
        // 执行：调用公开 API 删除规则集，再释放刷新消息。
        // 校验：刷新消息成功返回且不向 VRouter 下发；资源不进入周期任务待重试集合，
        //      已删除规则集和关联记录均未被重新创建。
    }

    @Override
    void test() {
        env.create {
            testConcurrentAttachUsesResourceQueue()
            testSchedulerOperationsUseSchedulerQueue()
            testSecurityGroupRuleChangeDuringScheduledRefresh()
            testVpcFirewallRuleChangeDuringScheduledRefresh()
            testAttachAndUpdateUseSameSchedulerQueue()
            testLateRefreshUsesCurrentBinding()
            testLateSecurityGroupRefreshAfterResourceDeletion()
            testLateVpcFirewallRuleSetRefreshAfterResourceDeletion()
        }
    }
}
```

## 9. 开发量评估

| 工作项 | 预计人日 |
|---|---:|
| `PolicyRuleScheduler` 表、全量 API、校验和 Cascade 删除 | 2—3 |
| 共享时间判定、周期检查任务、本地消息和双 MN 处理 | 2 |
| 安全组 MN 处理及 `zstack-utility` 小范围调整 | 2—3 |
| 防火墙规则集规则构建、批量刷新及 `zstack-vyos` 幂等适配 | 2—3 |
| 后端 Groovy UT、联调和回归 | 3—4 |
| **合计** | **11—15 人日** |

单后端约 2.5—3 周，不含前端开发。

## 10. 参考资料

- [SUG-2668](http://jira.zstack.io/browse/SUG-2668) — 产品需求。
- [安全组优化-后端](http://confluence.zstack.io/pages/viewpage.action?pageId=144797977) — 功能点、API 和数据库设计格式参考。
- `zstack/compute/src/main/java/org/zstack/compute/vm/VmInstanceBase.java` — API 转本地消息、资源队列持有、Flow 失败恢复和删除 Cascade 语义参考。
- `zstack/plugin/securityGroup/src/main/java/org/zstack/network/securitygroup/APIAttachSecurityGroupToL3NetworkMsg.java`、`APIDetachSecurityGroupFromL3NetworkMsg.java` — Attach/Detach 关系 API 的命名和校验语义。
- `zstack/plugin/securityGroup/src/main/java/org/zstack/network/securitygroup/SecurityGroupApiInterceptor.java`、`SecurityGroupManagerImpl.java`、`RefreshSecurityGroupRulesOnHostMsg.java`、`RefreshSecurityGroupRulesOnVmMsg.java` — `SecurityGroupMessage` 路由、资源队列和现有规则刷新入口。
- `zstack-utility/kvmagent/kvmagent/plugins/securitygroup_plugin.py` — 安全组规则和 iptables 链处理。
- `zstack/core/src/main/java/org/zstack/core/thread/DispatchQueueImpl.java`、`zstack/core/src/main/java/org/zstack/core/cloudbus/ResourceDestinationMaker.java` — 本地同步队列和双 MN 资源路由。
- `zstack/core/src/main/java/org/zstack/core/db/SQLBatch.java`、`zstack/core/src/main/java/org/zstack/core/cascade/CascadeFacadeImpl.java`、`zstack/identity/src/main/java/org/zstack/identity/AccountBase.java` — 控制面批量修改和 Cascade 的失败边界。
- `zstack/header/src/main/java/org/zstack/header/vo/ResourceVO.java`、`ResourceTypeMetadata.java`、`zstack/header/src/main/java/org/zstack/header/aspect/OwnedByAccountAspect.aj` — 账号资源的继承映射、资源类型和账户资源引用。
- `zstack/premium/mevoco/src/main/java/org/zstack/header/scheduler/SchedulerTriggerVO.java`、`zstack/premium/mevoco/src/main/java/org/zstack/scheduler/SchedulerFacadeImpl.java`、`SchedulerCascadeExtension.java` — 同类计划资源的创建和账号级联删除参考。
- `zstack/premium/mevoco/src/main/java/org/zstack/scheduler/SchedulerApiInterceptor.java`、`AbstractSchedulerJob.java` — Scheduler 资源路由和通用 Scheduler 任务历史实现参考。
- `zstack/premium/plugin-premium/container/src/main/java/org/zstack/container/ContainerManagerImpl.java`、`zstack/core/src/main/java/org/zstack/core/rest/RESTApiFacadeImpl.java` — 固定任务标识配合 `ResourceDestinationMaker` 的现有实现参考。
- `zstack/premium/plugin-premium/vpcFirewall/src/main/java/org/zstack/vpcfirewall/VpcFirewallApiInterceptor.java`、`VpcFirewallManagerImpl.java`、`VpcFirewallRuleSetBase.java`、`VpcFirewallBackend.java`、`entity/VpcFirewallCommands.java`、`entity/VpcFirewallRuleSetL3RefVO.java` — 防火墙规则集消息路由、资源队列、发布流程和批量下发入口。
- `zstack/test/src/test/groovy/org/zstack/test/integration/networkservice/provider/flat/securitygroup/ChangeSecurityGroupRuleStateCase.groovy`、`zstack/premium/test-premium/src/test/groovy/org/zstack/test/integration/premium/vpc/firewall/VpcFirewallLifeCycleCase.groovy` — Groovy Case 的场景方法和 `test()` 编排风格。
- `zstack-vyos/plugin/firewall.go` — VPC 防火墙规则状态处理。
