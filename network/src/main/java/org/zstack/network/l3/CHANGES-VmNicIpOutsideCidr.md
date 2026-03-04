# VM网卡IP地址支持不在L3 CIDR范围内 - 修改总结

## 功能概述

支持云主机网卡配置不在L3网络IP Range范围内的IP地址，同时支持通过QGA设置自定义DNS服务器。

## 一、DNS功能实现

### 1.1 系统标签定义
**文件**: `compute/src/main/java/org/zstack/compute/vm/VmSystemTags.java`
```java
// 新增DNS系统标签
public static String STATIC_DNS_L3_UUID_TOKEN = "l3NetworkUuid";
public static String STATIC_DNS_TOKEN = "staticDns";
public static PatternedSystemTag STATIC_DNS = new PatternedSystemTag(
    String.format("staticDns::{%s}::{%s}", STATIC_DNS_L3_UUID_TOKEN, STATIC_DNS_TOKEN),
    VmInstanceVO.class);
```

### 1.2 API消息修改
**文件**: `header/src/main/java/org/zstack/header/vm/APISetVmStaticIpMsg.java`
```java
@APIParam(required = false)
private List<String> dnsAddresses;
```

**文件**: `header/src/main/java/org/zstack/header/vm/SetVmStaticIpMsg.java`
```java
private List<String> dnsAddresses;
```

**文件**: `header/src/main/java/org/zstack/header/vm/APIChangeVmNicNetworkMsg.java`
```java
@APIParam(required = false)
private String ip;
@APIParam(required = false)
private String ip6;
@APIParam(required = false)
private String netmask;
@APIParam(required = false)
private String gateway;
@APIParam(required = false)
private String ipv6Gateway;
@APIParam(required = false)
private String ipv6Prefix;

@APIParam(required = false)
private List<String> dnsAddresses;
```

**文件**: `header/src/main/java/org/zstack/header/vm/ChangeVmNicNetworkMsg.java`
```java
private String ip;
private String ip6;
private String netmask;
private String gateway;
private String ipv6Gateway;
private String ipv6Prefix;
private List<String> dnsAddresses;
```

### 1.3 DNS操作方法
**文件**: `compute/src/main/java/org/zstack/compute/vm/StaticIpOperator.java`
```java
// 设置静态DNS
public void setStaticDns(String vmUuid, String l3Uuid, List<String> dnsAddresses)

// 删除静态DNS
public void deleteStaticDnsByVmUuidAndL3Uuid(String vmUuid, String l3Uuid)

// 获取静态DNS
public List<String> getStaticDnsByVmUuidAndL3Uuid(String vmUuid, String l3Uuid)
```

### 1.4 DNS获取接口
**文件**: `network/src/main/java/org/zstack/network/service/NetworkServiceManager.java`
```java
// 新增接口方法
List<String> getVmNicDns(String vmUuid, String l3NetworkUuid);
```

**文件**: `network/src/main/java/org/zstack/network/service/NetworkServiceManagerImpl.java`
```java
@Override
public List<String> getVmNicDns(String vmUuid, String l3NetworkUuid) {
    // 优先返回系统标签DNS，否则返回L3网络DNS
    List<String> customDns = new StaticIpOperator().getStaticDnsByVmUuidAndL3Uuid(vmUuid, l3NetworkUuid);
    if (customDns != null && !customDns.isEmpty()) {
        return customDns;
    }
    return getL3NetworkDns(l3NetworkUuid);
}
```

### 1.5 VM实例处理
**文件**: `compute/src/main/java/org/zstack/compute/vm/VmInstanceBase.java`
- `handle(APISetVmStaticIpMsg)` - 传递dnsAddresses
- `setIpamStaticIp()` - 调用setStaticDns()
- `setNoIpamStaticIp()` - 调用setStaticDns()
- `handle(APIChangeVmNicNetworkMsg)` - 传递dnsAddresses
- `changeVmNicNetwork()` - SetStaticIp内部类处理DNS

### 1.6 GuestTools集成
**文件**: `premium/guesttools/src/main/java/org/zstack/guesttools/GuestToolsManagerImpl.java`
```java
// 使用getVmNicDns()替代getL3NetworkDns()
ipConfig.setDns(nwServiceMgr.getVmNicDns(vmUuid, nic.getL3NetworkUuid()));
```

---

## 二、IP地址不在L3 CIDR范围内的约束实现

### 2.1 L3网络IP统计
**文件**: `network/src/main/java/org/zstack/network/l3/L3NetworkManagerImpl.java`

**修改**: 按L3Network和Zone统计UsedIp时排除`ipRangeUuid=null`的记录
```sql
-- 修改前
select count(distinct uip.ip), uip.l3NetworkUuid, uip.ipVersion
from UsedIpVO uip where uip.l3NetworkUuid in (:uuids) ...

-- 修改后
select count(distinct uip.ip), uip.l3NetworkUuid, uip.ipVersion
from UsedIpVO uip where uip.l3NetworkUuid in (:uuids)
and uip.ipRangeUuid is not null ...
```

### 2.2 添加IpRange时更新孤儿IP
**文件**: `network/src/main/java/org/zstack/network/l3/NormalIpRangeFactory.java`

**修改**: 只查询`ipRangeUuid=null`的UsedIpVO进行更新
```java
List<UsedIpVO> usedIpVos = Q.New(UsedIpVO.class)
        .eq(UsedIpVO_.l3NetworkUuid, vo.getL3NetworkUuid())
        .eq(UsedIpVO_.ipVersion, vo.getIpVersion())
        .isNull(UsedIpVO_.ipRangeUuid)  // 新增条件
        .list();
```

### 2.3 添加IpRange时校验特殊地址
**文件**: `network/src/main/java/org/zstack/network/l3/L3NetworkApiInterceptor.java`

**新增**: 添加第一个IpRange时校验gateway/network/broadcast地址未被占用
```java
// 当添加第一个IpRange时，检查这些地址是否已被使用
if (l3IpRanges.isEmpty()) {
    // 检查gateway地址
    boolean gatewayUsed = Q.New(UsedIpVO.class)
            .eq(UsedIpVO_.l3NetworkUuid, ipr.getL3NetworkUuid())
            .eq(UsedIpVO_.ip, ipr.getGateway())
            .isNull(UsedIpVO_.ipRangeUuid)
            .isExists();
    if (gatewayUsed) {
        throw new ApiMessageInterceptionException(...);
    }
    // 检查network地址和broadcast地址...
}
```

### 2.4 DHCP配置排除CIDR外地址
**文件**: `network/src/main/java/org/zstack/network/service/DhcpExtension.java`

**修改1**: `isDualStackNicInSingleL3Network()`方法过滤`ipRangeUuid=null`
```java
List<UsedIpInventory> validIps = nic.getUsedIps().stream()
        .filter(ip -> ip.getIpRangeUuid() != null)
        .collect(Collectors.toList());
```

**修改2**: `setDualStackNicOfSingleL3Network()`方法过滤`ipRangeUuid=null`

**修改3**: `makeDhcpStruct()`主循环跳过`ipRangeUuid=null`
```java
for (UsedIpVO ip : nic.getUsedIps()) {
    if (ip.getIpRangeUuid() == null) {
        logger.debug("skip DHCP for vmnic[ip:%s] because it's outside L3 CIDR range");
        continue;
    }
    // ...
}
```

### 2.5 安全组计算排除CIDR外地址
**文件**: `plugin/securityGroup/src/main/java/org/zstack/network/securitygroup/SecurityGroupManagerImpl.java`

**修改**: `getVmIpsBySecurityGroup()`方法SQL添加`ip.ipRangeUuid is not null`
```sql
select ip.ip from VmNicVO nic, VmNicSecurityGroupRefVO ref, SecurityGroupVO sg, UsedIpVO ip
where ... and ip.ipRangeUuid is not null
```

### 2.6 EIP禁止绑定CIDR外地址
**文件**: `plugin/eip/src/main/java/org/zstack/network/service/eip/EipApiInterceptor.java`

**新增**: `validate(APIAttachEipMsg)`添加检查
```java
UsedIpVO usedIpVO = dbf.findByUuid(msg.getUsedIpUuid(), UsedIpVO.class);
if (usedIpVO != null && usedIpVO.getIpRangeUuid() == null) {
    throw new ApiMessageInterceptionException(argerr(
            "cannot bindBind EIP to IP address[%s] which is outside L3 network CIDR range",
            usedIpVO.getIp()));
}
```

### 2.7 PF禁止绑定CIDR外地址
**文件**: `plugin/portForwarding/src/main/java/org/zstack/network/service/portforwarding/PortForwardingApiInterceptor.java`

**新增**: `validate(APIAttachPortForwardingRuleMsg)`添加检查
```java
VmNicVO nicVO = dbf.findByUuid(msg.getVmNicUuid(), VmNicVO.class);
if (nicVO != null && nicVO.getUsedIpUuid() != null) {
    UsedIpVO usedIpVO = dbf.findByUuid(nicVO.getUsedIpUuid(), UsedIpVO.class);
    if (usedIpVO != null && usedIpVO.getIpRangeUuid() == null) {
        throw new ApiMessageInterceptionException(...);
    }
}
```

### 2.8 LB禁止绑定CIDR外地址
**文件**: `plugin/loadBalancer/src/main/java/org/zstack/network/service/lb/LoadBalancerApiInterceptor.java`

**新增**: `validate(APIAddVmNicToLoadBalancerMsg)`添加检查
```java
for (String nicUuid : msg.getVmNicUuids()) {
    VmNicVO nicVO = dbf.findByUuid(nicUuid, VmNicVO.class);
    if (nicVO != null && nicVO.getUsedIpUuid() != null) {
        UsedIpVO usedIpVO = dbf.findByUuid(nicVO.getUsedIpUuid(), UsedIpVO.class);
        if (usedIpVO != null && usedIpVO.getIpRangeUuid() == null) {
            throw new ApiMessageInterceptionException(...);
        }
    }
}
```

---

## 三、测试用例

**文件**: `premium/test-premium/src/test/groovy/org/zstack/test/integration/premium/vpc/networkService/VmNicIpOutsideCidrCase.groovy`

**测试场景**:
1. `testSetVmNicIpOutsideCidr` - 验证设置CIDR外IP时`ipRangeUuid=null`
2. `testEipCannotBindToIpOutsideCidr` - 验证EIP不能绑定
3. `testLbCannotAddNicWithIpOutsideCidr` - 验证LB不能添加
4. `testPfCannotBindToIpOutsideCidr` - 验证PF不能绑定
5. `testIpStatisticsExcludeIpOutsideCidr` - 验证IP统计排除
6. `testAddIpRangeAssociatesOrphanIps` - 验证添加IpRange后自动关联孤儿IP

---

## 四、逻辑总结表

| 场景 | UsedIpVO.ipRangeUuid | 处理方式 |
|------|---------------------|---------|
| IP在CIDR范围内 | 有值 | 正常处理 |
| IP不在CIDR范围内 | null | 特殊处理 |
| L3网络IP统计 | - | 排除null记录 |
| DHCP下发 | - | 跳过null记录 |
| 安全组计算 | - | 排除null记录 |
| EIP/LB/PF绑定 | - | 禁止null记录 |
| 添加IpRange | - | 自动关联范围内的孤儿IP |

---

## 五、IP 范围校验规则（基于全局配置）

**全局配置**: `L3NetworkGlobalConfig.ALLOW_IP_OUTSIDE_RANGE`（`l3Network.allow.ip.outside.range`，默认 `false`）

**文件**: `network/src/main/java/org/zstack/network/l3/L3NetworkGlobalConfig.java`
```java
@GlobalConfigDef(defaultValue = "false", type = Boolean.class, description = "allow setting VM NIC IP address outside L3 network IP ranges")
@GlobalConfigValidation(validValues = {"true", "false"})
public static GlobalConfig ALLOW_IP_OUTSIDE_RANGE = new GlobalConfig(CATEGORY, "allow.ip.outside.range");
```

**规则说明**：
- `allow.ip.outside.range = false`（默认）：IP 必须在 L3 IP Range 内（原有行为）
- `allow.ip.outside.range = true`：允许设置不在 IP Range 内的 IP 地址，所有 L3 网络生效

> 注意：`enableIpAddressAllocation()` 控制的是 L3 上创建云主机网卡是否执行地址分配过程；
> `allow.ip.outside.range` 控制的是是否校验网卡地址在 L3 IP Range 之内。
> 两者关系：即使 `enableIpAddressAllocation()` 为 `true`，只要指定地址不在 IP Range 内，也不走 IPAM 分支，走 no-ipam 分支直接创建 UsedIpVO。

### 5.1 删除 `IsIpAddressInRangesCheckEnabled()`

移除 `L3NetworkVO.IsIpAddressInRangesCheckEnabled()` 和 `L3NetworkInventory.IsIpAddressInRangesCheckEnabled()` 方法，所有调用处改为判断全局配置。

**文件**:
- `header/src/main/java/org/zstack/header/network/l3/L3NetworkVO.java` — 删除方法
- `header/src/main/java/org/zstack/header/network/l3/L3NetworkInventory.java` — 删除方法

### 5.2 VmInstanceApiInterceptor 校验修改

**文件**: `compute/src/main/java/org/zstack/compute/vm/VmInstanceApiInterceptor.java`

#### validate(APIChangeVmNicNetworkMsg)

1. `staticIp` 范围检查放开：
```java
// 修改前
if (!l3NetworkVO.enableIpAddressAllocation()) {
    found = true;
}
// 修改后
if (!l3NetworkVO.enableIpAddressAllocation()
        || L3NetworkGlobalConfig.ALLOW_IP_OUTSIDE_RANGE.value(Boolean.class)) {
    found = true;
}
```

2. system tag 中 staticIp 范围校验：
```java
// 修改前
if (l3NetworkVO.IsIpAddressInRangesCheckEnabled()) {
// 修改后
if (!L3NetworkGlobalConfig.ALLOW_IP_OUTSIDE_RANGE.value(Boolean.class)) {
```

#### validateStaticIPv4 / validateStaticIPv6

跳过已有 NIC IP 的范围校验：
```java
// 修改前
if (!l3NetworkVO.IsIpAddressInRangesCheckEnabled()) {
    continue;
}
// 修改后
if (L3NetworkGlobalConfig.ALLOW_IP_OUTSIDE_RANGE.value(Boolean.class)) {
    continue;
}
```

#### validate(APISetVmStaticIpMsg)

1. 范围外 IP 拒绝逻辑：
```java
// 修改前
if (l3NetworkVO.IsIpAddressInRangesCheckEnabled()) {
// 修改后
if (!L3NetworkGlobalConfig.ALLOW_IP_OUTSIDE_RANGE.value(Boolean.class)) {
```

2. netmask/gateway 填充分支 — IPv4：
```java
// 修改前
if (msg.getIp() != null && !l3NetworkVO.enableIpAddressAllocation()) {
// 修改后
if (msg.getIp() != null && (!l3NetworkVO.enableIpAddressAllocation()
        || L3NetworkGlobalConfig.ALLOW_IP_OUTSIDE_RANGE.value(Boolean.class))) {
```

3. netmask/gateway 填充分支 — IPv6：
```java
// 修改前
if (msg.getIp6() != null && !l3NetworkVO.enableIpAddressAllocation()) {
// 修改后
if (msg.getIp6() != null && (!l3NetworkVO.enableIpAddressAllocation()
        || L3NetworkGlobalConfig.ALLOW_IP_OUTSIDE_RANGE.value(Boolean.class))) {
```

### 5.3 L3BasicNetwork.checkIpAvailability() 修改

**文件**: `network/src/main/java/org/zstack/network/l3/L3BasicNetwork.java`

在 `checkIpAvailability()` 方法中，增加全局配置判断，跳过 IP Range 检查：
```java
if (!self.enableIpAddressAllocation()) {
    inRange = true;
}
// 新增：全局配置开启时跳过 IP Range 检查
if (L3NetworkGlobalConfig.ALLOW_IP_OUTSIDE_RANGE.value(Boolean.class)) {
    inRange = true;
}
```

### 5.4 VmInstanceBase 处理逻辑（无需修改）

`VmInstanceBase` 中 `handle(SetVmStaticIpMsg)` 和 `changeVmNicNetwork()` 已通过 `allStaticIpsOutsideRange()` 正确判断：即使 `enableIpAddressAllocation()` 为 `true`，只要 IP 全部不在 IP Range 内，就走 no-ipam 分支直接创建 `UsedIpVO`（`ipRangeUuid = null`）。无需额外修改。
