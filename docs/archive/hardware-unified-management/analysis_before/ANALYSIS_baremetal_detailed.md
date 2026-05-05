# Baremetal v1 模块深度代码分析

## 1. 创建 Chassis FlowChain

### 文件：`BaremetalChassisManagerImpl.java`

**主消息处理入口** (Line 873-902):
```java
private void handle(CreateBaremetalChassisMsg msg)
```

**实际上没有显式的 FlowChain**，而是通过同步任务链处理：

**关键步骤** (Line 876-901):
1. **许可证检查** (Line 884)：`checkBaremetalLicense(msg)`
2. **参数验证** (Line 886)：`chassisValidator.validate(msg)`
3. **创建 VO** (Line 891)：`createBaremetalChassisVO(msg)`

### `createBaremetalChassisVO` 方法详细流程 (Line 680-734):

**Flow 1: 创建 Chassis VO** (Line 681-712)
- 生成 UUID
- 设置 IPMI 信息（地址、端口、用户名、密码）
- 初始状态：`State=Enabled`, `Status=HWInfoUnknown`
- 持久化到数据库
- **错误处理**：捕获 `SQLIntegrityConstraintViolationException`（重复 IPMI 地址/端口）

**Flow 2: 触发硬件发现（可选）** (Line 714-732)
- 条件：`msg.getReboot() == true`
- 调用：`getHardwareInfo(bmc, completion)`

---

## 2. 硬件发现 FlowChain

### Inspect 触发入口 (Line 921-953)

**Flow 步骤**:

### Step 1: IPMI 连接检查 (Line 926-930)
```java
if (!BaremetalUtils.isIpmiServerReachable(bmc.toInventory())) {
    reply.setError(...);
    return;
}
```

### Step 2: 删除旧硬件信息 (Line 932-935)
```java
SQL.New(BaremetalHardwareInfoVO.class)
    .eq(BaremetalHardwareInfoVO_.chassisUuid, msg.getUuid())
    .hardDelete();
```

### Step 3: 启动硬件信息检查定时器 (Line 938)
```java
checkHwInfo(bmc.getUuid());
```

**定时器详情** (Line 794-834):
- **超时时间**: `BaremetalChassisGlobalConfig.GET_CHASSIS_HW_INFO_TIMEOUT * 60 * 1000ms`
- **检查间隔**: 10秒
- **超时处理** (Line 820-829): 设置状态为 `PxeBootFailed`

### Step 4: 触发 PXE 启动 (Line 940-952)

**`getHardwareInfo` 实现** (Line 760-769):
```java
if (BaremetalUtils.powerResetRemoteServer(bmc.toInventory(), BaremetalChassisConstant.SERVER_BOOT_DEV_PXE)) {
    bmc.setStatus(BaremetalChassisStatus.PxeBooting);
    dbf.update(bmc);
    completion.success();
}
```

### Discovery OS 回调处理 (Line 119-272)

**REST 处理器注册** (Line 119-124):
```java
restf.registerSyncHttpCallHandler(BaremetalChassisConstant.SEND_HARDWARE_INFO,
    BaremetalChassisCommands.SendHardwareInfoCmd.class,
    cmd -> {
        handleSendHardwareInfo(cmd);
        return null;
    });
```

---

## 3. Instance 部署 FlowChain

### 文件：`BaremetalInstanceManagerImpl.java`

**FlowChain 构建** (Line 444-860):
```java
final FlowChain chain = FlowChainBuilder.newShareFlowChain();
chain.setName(String.format("create-baremetal-instance-on-chassis-%s", msg.getChassisUuid()));
```

### Flow 详细步骤:

#### Flow 1: `occupy-baremetal-chassis` (Line 457-475)
- 占用 Chassis，设置状态为 `Allocated`
- **Rollback**: 恢复状态为 `Available`

#### Flow 2: `create-baremetal-instance-vo` (Line 477-540)
- 创建 Instance VO
- **Rollback**: 删除 VO

#### Flow 3: `create-baremetal-nic-vo-for-pxe-boot-device` (Line 542-599)
- 为 PXE 启动网卡创建 Nic VO
- **Rollback**: 删除所有 Nic 和 Bonding

#### Flow 4: `create-baremetal-nic-vo-for-other-devices` (Line 601-782)
- 为其他网卡分配 IP 并创建 Nic VO
- **Rollback**: 释放 IP 并删除 Nic

#### Flow 5: `create-bm-custom-preconfiguration-vos` (Line 784-815)
- 创建自定义预配置参数

#### Flow 6: `pxe-boot-baremetal-instance-if-InstantStart` (Line 817-842)
- 如果策略为 InstantStart，立即启动 PXE 部署

---

## 4. IPMI 命令细节

### 文件：`BaremetalUtils.java`

### IPMIToolCaller 类 (Line 60-120)

**基础命令构建** (Line 79-83):
```java
private String buildBaseCmd() {
    passFile = PathUtil.createTempFileWithContent(password);
    return String.format("ipmitool -I %s -H '%s' -p '%d' -U '%s' -f '%s'",
            interfaceToUse, hostname, port, username, passFile);
}
```

### IPMI 操作实现

| 操作 | 方法 | 命令 |
|------|------|------|
| 开机 | `powerOn()` | `chassis power on` |
| 关机 | `powerOff()` | `chassis power off` |
| 重启 | `powerReset()` | `chassis power reset` |
| 设置启动设备 | `setBootDev(bootDev)` | `chassis bootdev pxe [options=efiboot]` |
| 电源状态 | `status()` | `chassis power status` |

**使用的工具**: `ipmitool` 命令行工具

---

## 5. 状态机转换

### BaremetalChassisStatus 转换

| 初始状态 | 触发条件 | 目标状态 | 代码位置 |
|---------|---------|---------|---------|
| `HWInfoUnknown` | 触发硬件发现 | `PxeBooting` | Line 763-764 |
| `PxeBooting` | 收到 PXE Server UUID 回调 | `Available`/`Allocated` | Line 184-191 |
| `PxeBooting` | 10分钟超时 | `PxeBootFailed` | Line 827-828 |
| `Available` | 创建 Instance | `Allocated` | Line 462-464 |
| `Allocated` | Instance Rollback | `Available` | Line 470-472 |

---

## 6. 消息处理链路

```
APICreateBaremetalChassisMsg (Line 386)
  ↓
handle(APICreateBaremetalChassisMsg msg) (Line 386)
  ↓
CreateBaremetalChassisMsg.valueOf(msg) (Line 389)
  ↓
CloudBus.send(CreateBaremetalChassisMsg) (Line 391)
  ↓
handle(CreateBaremetalChassisMsg msg) (Line 873)
  ↓
SyncTaskChain (Line 876-901)
  ├─ checkBaremetalLicense(msg) (Line 884)
  ├─ chassisValidator.validate(msg) (Line 886)
  └─ createBaremetalChassisVO(msg) (Line 891)
      ├─ dbf.persistAndRefresh(bmc) (Line 698)
      └─ [Optional] getHardwareInfo(bmc) (Line 715)
  ↓
CreateBaremetalChassisReply (Line 893)
  ↓
APICreateBaremetalChassisEvent (Line 396)
```

---

## 7. 扩展点调用时机

### Cascade 删除流程

**DeleteBaremetalChassisMsg Handler** (Line 617-678):

**FlowChain**:
1. **Flow**: `delete-dhcp-config-in-pxeserver-if-needed` (Line 622-652)
2. **Done Handler** (Line 655-670):
   - 删除 `BaremetalHardwareInfoVO`
   - 删除 `BaremetalChassisVO`

---

## 8. 可抽象到 RoleAdapter 的操作

| Baremetal 操作 | RoleAdapter 方法 |
|---------------|-----------------|
| `createBaremetalChassisVO()` | `createRole()` |
| `powerOn/Off/ResetBaremetalChassis()` | `PowerManageable.powerOn/Off/Reset()` |
| `getHardwareInfo()` | `HardwareDiscoverable.triggerDiscovery()` |
| `handleSendHardwareInfo()` | `HardwareDiscoverable.handleHardwareInfoCallback()` |
| `changeBaremetalChassisState()` | `RoleAdapter.syncToRole()` |
| `deleteBaremetalChassis()` | `deleteRole()` |

