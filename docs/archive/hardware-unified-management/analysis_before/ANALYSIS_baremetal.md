# Baremetal 模块分析报告

## 1. VO 结构

### 1.1 BaremetalChassisVO 字段

| 字段名 | 类型 | 说明 | RoleAdapter 是否需要 |
|--------|------|------|---------------------|
| uuid | String | 主键 | ✓ 必需 |
| name | String | 名称 | ✓ 必需 |
| description | String | 描述 | ✓ 必需 |
| zoneUuid | String | 所属 Zone（外键） | ✓ 必需 |
| clusterUuid | String | 所属 Cluster（外键，RESTRICT） | ✓ 必需 |
| pxeServerUuid | String | PXE 服务器（外键，SET_NULL） | ✓ 必需（特有） |
| ipmiAddress | String | IPMI 地址 | ✓ 必需（特有） |
| ipmiPort | Integer | IPMI 端口 | ✓ 必需（特有） |
| ipmiUsername | String | IPMI 用户名 | ✓ 必需（特有） |
| ipmiPassword | String | IPMI 密码（敏感） | ✓ 必需（特有） |
| state | BaremetalChassisState | 状态（Enabled/Disabled） | ✓ 必需 |
| status | BaremetalChassisStatus | 运行状态 | ✓ 必需 |
| createDate | Timestamp | 创建时间 | ✓ 必需 |
| lastOpDate | Timestamp | 最后操作时间 | ✓ 必需 |
| hardwareInfos | Set<BaremetalHardwareInfoVO> | 硬件信息（OneToMany） | ✓ 特有关联 |

### 1.2 BaremetalHardwareInfoVO 结构

| 字段名 | 类型 | 说明 |
|--------|------|------|
| uuid | String | 主键 |
| chassisUuid | String | 所属 Chassis（外键，CASCADE） |
| type | String | 硬件类型（CPU/Memory/Disk/NIC/PXEServer） |
| content | String | 硬件信息 JSON 内容 |

## 2. 生命周期

### 2.1 创建流程

```
APICreateBaremetalChassisMsg
  → CreateBaremetalChassisMsg (内部消息)
  → BaremetalChassisManagerImpl.handle()
    1. License 检查
    2. 参数验证（IPMI 地址唯一性）
    3. 创建 VO（状态 = Enabled, status = HWInfoUnknown）
    4. 触发硬件发现（可选，reboot=true）
```

### 2.2 状态机

**BaremetalChassisState（state 字段）**：
- `Enabled` - 启用
- `Disabled` - 禁用

**BaremetalChassisStatus（status 字段）**：
- `HWInfoUnknown` - 硬件信息未知（初始状态）
- `PxeBooting` - PXE 启动中
- `PxeBootFailed` - PXE 启动失败
- `Available` - 可用（未分配）
- `Allocated` - 已分配（有 Instance）

### 2.3 硬件信息采集流程

```
PXE Boot → Discovery OS → POST /baremetal/chassis/hardware/info
  → handleSendHardwareInfo()
    ├─ 验证 ipmiAddress 唯一性
    ├─ 匹配 BaremetalChassisVO
    ├─ 处理 PXEServer 关联
    └─ 保存硬件信息（CPU/Memory/Disk/NIC）
```

## 3. 核心操作

### 3.1 IPMI 操作

| 操作 | API | 说明 |
|------|-----|------|
| 开机 | APIPowerOnBaremetalChassisMsg | IPMI Power On |
| 关机 | APIPowerOffBaremetalChassisMsg | IPMI Power Off |
| 重启 | APIPowerResetBaremetalChassisMsg | IPMI Power Reset |
| 查询电源状态 | APIGetBaremetalChassisPowerStatusMsg | IPMI Power Status |
| 硬件发现 | APIInspectBaremetalChassisMsg | PXE Boot + Discovery |

### 3.2 PXE Server 关联

- Chassis 必须关联 PXE Server 才能部署 Instance
- 通过 DHCP 发现自动关联
- pxeServerUuid 一旦设置不应频繁修改

### 3.3 与 Cluster/Zone 的关系

- **必须关联 Cluster**：外键约束 RESTRICT
- **自动关联 Zone**：从 Cluster 获取 zoneUuid

## 4. 对 RoleAdapter 接口的建议

### 4.1 必需方法

```java
// IPMI 操作
void powerOn(String uuid);
void powerOff(String uuid);
void powerReset(String uuid);
String getPowerStatus(String uuid);
boolean checkIpmiConnection(String ipmiAddress, int ipmiPort, String username, String password);

// 硬件发现
void triggerHardwareDiscovery(String uuid, boolean forcePxeBoot);
void handleHardwareInfo(String chassisUuid, String type, String content);

// PXE Server 关联
void associatePxeServer(String chassisUuid, String pxeServerUuid);
void createDhcpConfig(String chassisUuid, String pxeNicMac, String pxeNicIp);
void deleteDhcpConfig(String chassisUuid);
```

### 4.2 特殊处理

1. **IPMI 凭据管理**：密码需加密存储
2. **PXE Server 强依赖**：Chassis 必须关联 PXE Server
3. **硬件信息异步上报**：需要超时检查机制
4. **状态同步复杂性**：Chassis status 和 Instance state 需双向同步
5. **Cluster 强绑定**：必须属于 Cluster
6. **License 限制**：创建需检查配额

### 4.3 与 KVM/Container 的差异

| 特性 | Baremetal Chassis | KVM Host | Container Host |
|------|------------------|----------|----------------|
| 硬件发现 | PXE Boot + Discovery OS | SSH 连接 | Endpoint 注册 |
| 电源控制 | IPMI 远程控制 | Libvirt API | Docker API |
| PXE Server | 必需 | 不需要 | 不需要 |
| 硬件信息 | 异步上报 | 同步采集 | 同步采集 |
| Instance 关系 | 1:1（独占） | 1:N（多 VM） | 1:N（多 Pod） |
