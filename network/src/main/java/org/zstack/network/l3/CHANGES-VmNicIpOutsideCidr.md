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

## 五、IP 范围校验规则（基于 DHCP 服务）

**已删除全局配置**: `vm.allow.ip.outside.range`（原 `VmGlobalConfig.ALLOW_IP_OUTSIDE_RANGE`）

**当前规则**：是否允许设置 CIDR 范围外的 IP 地址，取决于目标 L3 网络是否启用了 DHCP 服务：

| L3 网络类型 | DHCP 服务 | `enableIpAddressAllocation()` | 允许范围外 IP |
|------------|-----------|------------------------------|-------------|
| 扁平网络（有 DHCP） | ✅ | `true` | ❌ 拒绝 |
| 公有网络（有 DHCP） | ✅ | `true` | ❌ 拒绝 |
| 扁平网络（无 DHCP） | ❌ | `false` | ✅ 允许 |

**校验位置**：
- `VmInstanceApiInterceptor.validate(APISetVmStaticIpMsg)` — IP 必须在 IP Range 内（DHCP 启用时）
- `VmInstanceApiInterceptor.validate(APIChangeVmNicNetworkMsg)` — system tag 中的 staticIp 必须在 IP Range 内（DHCP 启用时）

---

## 六、APIChangeVmNicNetworkMsg 支持设置 IP/掩码/网关

### 6.1 消息新增显式字段（参考 APISetVmStaticIpMsg）

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
```

**文件**: `header/src/main/java/org/zstack/header/vm/ChangeVmNicNetworkMsg.java`
```java
private String ip;
private String ip6;
private String netmask;
private String gateway;
private String ipv6Gateway;
private String ipv6Prefix;
```

通过 API 成员变量直接传入 IP 地址、掩码、网关信息，不再依赖 system tags 传递。

### 6.2 Interceptor 校验逻辑

**文件**: `compute/src/main/java/org/zstack/compute/vm/VmInstanceApiInterceptor.java`

在 `validate(APIChangeVmNicNetworkMsg)` 中，直接校验 `msg.getIp()` 和 `msg.getIp6()` 是否已被占用：
```java
if (msg.getIp() != null && !msg.getIp().isEmpty()
        && Q.New(UsedIpVO.class).eq(UsedIpVO_.ip, msg.getIp())
            .eq(UsedIpVO_.l3NetworkUuid, msg.getDestL3NetworkUuid()).isExists()) {
    throw new ApiMessageInterceptionException(...);
}
if (msg.getIp6() != null && !msg.getIp6().isEmpty()
        && Q.New(UsedIpVO.class).eq(UsedIpVO_.ip, IPv6NetworkUtils.getIpv6AddressCanonicalString(msg.getIp6()))
            .eq(UsedIpVO_.l3NetworkUuid, msg.getDestL3NetworkUuid()).isExists()) {
    throw new ApiMessageInterceptionException(...);
}
```

### 6.3 API→内部消息传递

**文件**: `compute/src/main/java/org/zstack/compute/vm/VmInstanceBase.java`

在 `handle(APIChangeVmNicNetworkMsg)` 中，逐字段传递给内部消息：
```java
ChangeVmNicNetworkMsg cmsg = new ChangeVmNicNetworkMsg();
// ... 其他字段 ...
cmsg.setIp(msg.getIp());
cmsg.setIp6(msg.getIp6());
cmsg.setNetmask(msg.getNetmask());
cmsg.setGateway(msg.getGateway());
cmsg.setIpv6Gateway(msg.getIpv6Gateway());
cmsg.setIpv6Prefix(msg.getIpv6Prefix());
```

### 6.4 changeVmNicNetwork 处理逻辑

**文件**: `compute/src/main/java/org/zstack/compute/vm/VmInstanceBase.java`

在 `changeVmNicNetwork()` 的 disable-ipam 分支中，直接从 `msg` 的显式字段读取 IP/掩码/网关，不再通过 `StaticIpOperator.getNicNetworkInfoBySystemTag()` 解析 system tags。

### 6.5 Bug 修复

**文件**: `compute/src/main/java/org/zstack/compute/vm/VmInstanceApiInterceptor.java`

1. `getRequiredIpMap()` → `getStaticIp()`：interceptor 中处理 `staticIp` 时应使用 `msg.getStaticIp()` 而非 `msg.getRequiredIpMap()`
2. 循环变量误用修复：`for` 循环中的迭代变量与外部变量混淆导致逻辑错误
