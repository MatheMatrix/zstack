
  ---
  📋 ZStack 硬件统一管理架构设计方案

  一、现状分析

  1.1 现有数据结构关系图

  当前架构：
  ┌─────────────────────────────────────────┐
  │           ResourceVO (基类)              │
  └─────────────────────────────────────────┘
             ↑                    ↑
             │                    │
      ┌──────┴──────┐      ┌─────┴─────────────┐
      │   HostAO    │      │BaremetalChassisVO │ (独立体系)
      │ (抽象基类)   │      │  - ipmiAddress     │
      └──────┬──────┘      │  - ipmiPort        │
             │             │  - pxeServerUuid   │
        ┌────┴─────┐       │  - hardwareInfos   │
        │  HostVO  │       └────────────────────┘
        │ +capacity│
        │ +ipmi    │
        │ +hwMonitor│
        └────┬─────┘
             │
      ┌──────┴───────┬──────────────┐
      │              │              │
  ┌───┴────┐  ┌──────┴──────┐  ┌───┴────────┐
  │KVMHostVO│  │NativeHostVO │  │其他HostVO  │
  │+username│  │+endpointUuid│  └────────────┘
  │+password│  └─────────────┘
  │+port    │
  └─────────┘

  1.2 核心问题

  1. 体系分裂：BaremetalChassisVO 独立于 Host 体系之外
  2. IPMI 信息冗余：HostVO 有 HostIpmiVO，BaremetalChassisVO 直接包含 IPMI 字段
  3. 管理接口不统一：无法用统一的 API 管理所有硬件
  4. 裸金属特殊性：Chassis 概念与 Host 不同，一个 Chassis 可能对应多个可部署单元

  ---
  二、统一架构设计

  2.1 核心设计原则

  ✅ 不废弃现有表结构
  ✅ 向下兼容现有 API
  ✅ 引入统一抽象层
  ✅ 保持各类型硬件特性

  2.2 新架构关系图

  新架构（三层模型）：

  ┌──────────────────────────────────────────────────────────┐
  │              UnifiedHardwareVO (统一硬件层)                 │
  │  uuid, name, type, zoneUuid, clusterUuid, state, status  │
  │  hardwareCategory: [COMPUTE_HOST, BAREMETAL_CHASSIS,     │
  │                     CONTAINER_HOST, STORAGE_DEVICE...]   │
  └─────────────────────┬────────────────────────────────────┘
                        │
           ┌────────────┼────────────┐
           │            │            │
  ┌────────┴────┐ ┌────┴─────┐ ┌───┴──────────────┐
  │HardwareRefVO│ │HostVO    │ │BaremetalChassisVO│
  │ (关联表)     │ │(现有)    │ │  (现有保留)       │
  │+hwUuid      │ └──────────┘ └──────────────────┘
  │+refType     │       ↑               ↑
  │+refUuid     │       │               │
  └─────────────┘       │      ┌────────┴────────┐
                        │      │HardwareDetailVO │
                ┌───────┼──────┤ (硬件详情扩展)   │
                │       │      │ +cpuModel       │
           ┌────┴───┐  │      │ +memoryType     │
           │KVMHost │  │      │ +raidConfig     │
           │  VO    │  │      │ +biosVersion    │
           └────────┘  │      └─────────────────┘
                ┌──────┴──┐
                │Native   │
                │HostVO   │
                └─────────┘

  ---
  三、详细数据结构设计

  3.1 核心新增表

  3.1.1 UnifiedHardwareVO（统一硬件表）

  /**
   * 统一硬件管理实体
   * 提供所有硬件资源的统一视图
   */
  @Entity
  @Table(name = "UnifiedHardwareVO")
  @Inheritance(strategy = InheritanceType.JOINED)
  @AutoDeleteTag
  @BaseResource
  @EntityGraph(
      parents = {
          @EntityGraph.Neighbour(type = ZoneVO.class, myField = "zoneUuid", targetField = "uuid"),
          @EntityGraph.Neighbour(type = ClusterVO.class, myField = "clusterUuid", targetField = "uuid")
      }
  )
  public class UnifiedHardwareVO extends ResourceVO {

      @Column(nullable = false, length = 128)
      private String name;

      @Column(length = 2048)
      private String description;

      /**
       * 硬件类型：COMPUTE_HOST, BAREMETAL_CHASSIS, CONTAINER_HOST,
       *         STORAGE_SERVER, NETWORK_DEVICE
       */
      @Column(nullable = false)
      @Enumerated(EnumType.STRING)
      private HardwareCategory category;

      /**
       * 具体硬件子类型：KVM, VMware, Baremetal_x86, Baremetal_ARM,
       *              Docker, Kubernetes, Ceph, etc.
       */
      @Column(nullable = false, length = 64)
      private String hardwareType;

      @Column
      @ForeignKey(parentEntityClass = ZoneEO.class, onDeleteAction = ReferenceOption.RESTRICT)
      private String zoneUuid;

      @Column
      @ForeignKey(parentEntityClass = ClusterEO.class, onDeleteAction = ReferenceOption.SET_NULL)
      private String clusterUuid;

      /**
       * 管理网络地址（可以是 IP、URL 或其他标识）
       */
      @Column(length = 256)
      private String managementAddress;

      /**
       * 硬件状态：Enabled, Disabled, Maintenance
       */
      @Column(nullable = false)
      @Enumerated(EnumType.STRING)
      private HardwareState state;

      /**
       * 连接状态：Connected, Disconnected, Unknown
       */
      @Column(nullable = false)
      @Enumerated(EnumType.STRING)
      private HardwareStatus status;

      /**
       * 架构：x86_64, aarch64, riscv64
       */
      @Column(length = 32)
      private String architecture;

      /**
       * 序列号（全局唯一硬件标识）
       */
      @Column(unique = true, length = 128)
      private String serialNumber;

      /**
       * 厂商信息
       */
      @Column(length = 64)
      private String vendor;

      @Column(length = 64)
      private String model;

      /**
       * 标签（JSON 格式存储灵活信息）
       */
      @Column(columnDefinition = "TEXT")
      private String metadata;

      @Column
      private Timestamp createDate;

      @Column
      private Timestamp lastOpDate;

      @Column
      private Timestamp lastHeartbeatTime;

      // 关联到现有实体的引用
      @OneToOne(fetch = FetchType.LAZY, mappedBy = "hardware")
      @NoView
      private HardwareReferenceVO reference;

      // 硬件详细信息
      @OneToOne(fetch = FetchType.LAZY)
      @JoinColumn(name = "uuid")
      @NoView
      private HardwareDetailVO detail;

      // IPMI/带外管理信息（统一管理）
      @OneToOne(fetch = FetchType.LAZY)
      @JoinColumn(name = "uuid")
      @NoView
      private HardwareOobManagementVO oobManagement;

      // 硬件能力信息
      @OneToOne(fetch = FetchType.LAZY)
      @JoinColumn(name = "uuid")
      @NoView
      private HardwareCapabilityVO capability;
  }

  3.1.2 HardwareReferenceVO（硬件引用关联表）

  /**
   * 硬件引用关联表
   * 将统一硬件层与现有实体关联，实现双向映射
   */
  @Entity
  @Table(name = "HardwareReferenceVO",
      uniqueConstraints = {
          @UniqueConstraint(columnNames = {"referenceType", "referenceUuid"})
      }
  )
  public class HardwareReferenceVO {

      @Id
      @Column(length = 32)
      private String uuid;

      /**
       * 关联的统一硬件 UUID
       */
      @Column(nullable = false, unique = true, length = 32)
      @ForeignKey(parentEntityClass = UnifiedHardwareVO.class, onDeleteAction = ReferenceOption.CASCADE)
      private String hardwareUuid;

      /**
       * 引用类型：HOST, BAREMETAL_CHASSIS, NATIVE_HOST,
       *         STORAGE_NODE, NETWORK_SWITCH
       */
      @Column(nullable = false, length = 64)
      @Enumerated(EnumType.STRING)
      private HardwareReferenceType referenceType;

      /**
       * 被引用实体的 UUID（如 HostVO.uuid, BaremetalChassisVO.uuid）
       */
      @Column(nullable = false, length = 32)
      private String referenceUuid;

      /**
       * 同步状态：SYNCED, OUT_OF_SYNC, CONFLICT
       */
      @Column
      @Enumerated(EnumType.STRING)
      private SyncStatus syncStatus;

      @Column
      private Timestamp createDate;

      @Column
      private Timestamp lastSyncTime;

      // 反向关联
      @OneToOne(fetch = FetchType.LAZY)
      @JoinColumn(name = "hardwareUuid", insertable = false, updatable = false)
      private UnifiedHardwareVO hardware;
  }

  3.1.3 HardwareDetailVO（硬件详细信息表）

  /**
   * 硬件详细信息扩展表
   * 存储各类硬件的通用详细信息
   */
  @Entity
  @Table(name = "HardwareDetailVO")
  public class HardwareDetailVO {

      @Id
      @Column(length = 32)
      private String uuid; // 与 UnifiedHardwareVO.uuid 一致

      // CPU 信息
      @Column
      private Integer cpuSockets;

      @Column
      private Integer cpuCores;

      @Column
      private Integer cpuThreads;

      @Column(length = 256)
      private String cpuModel;

      @Column
      private Long cpuFrequency; // MHz

      // 内存信息
      @Column
      private Long totalMemory; // Bytes

      @Column
      private Integer memorySlots;

      @Column(length = 64)
      private String memoryType; // DDR4, DDR5, etc.

      // 存储信息
      @Column(columnDefinition = "TEXT")
      private String storageDevices; // JSON: [{type, size, model, slot}]

      @Column(columnDefinition = "TEXT")
      private String raidConfig; // JSON: RAID配置信息

      // 网络信息
      @Column(columnDefinition = "TEXT")
      private String networkInterfaces; // JSON: [{name, mac, speed, pci}]

      @Column
      private Integer nicCount;

      // 固件信息
      @Column(length = 128)
      private String biosVersion;

      @Column(length = 128)
      private String biosVendor;

      @Column
      private Timestamp biosReleaseDate;

      @Column(length = 128)
      private String firmwareVersion;

      // GPU/加速器信息
      @Column(columnDefinition = "TEXT")
      private String accelerators; // JSON: [{type, model, count}]

      // 电源信息
      @Column(columnDefinition = "TEXT")
      private String powerSupplies; // JSON: [{model, capacity, status}]

      // 机箱信息
      @Column(length = 128)
      private String chassisType; // Rack, Blade, Tower

      @Column
      private Integer rackUnit;

      @Column(length = 64)
      private String rackPosition;

      // 扩展信息（JSON 格式）
      @Column(columnDefinition = "TEXT")
      private String extendedInfo;

      @Column
      private Timestamp lastDiscoveryTime;

      @Column
      private Timestamp createDate;

      @Column
      private Timestamp lastOpDate;
  }

  3.1.4 HardwareOobManagementVO（带外管理信息表）

  /**
   * 统一的带外管理（Out-of-Band Management）信息
   * 整合 IPMI, Redfish, iLO, iDRAC 等
   */
  @Entity
  @Table(name = "HardwareOobManagementVO")
  public class HardwareOobManagementVO {

      @Id
      @Column(length = 32)
      private String uuid; // 与 UnifiedHardwareVO.uuid 一致

      /**
       * 带外管理类型：IPMI, REDFISH, ILO, IDRAC, IMM
       */
      @Column(nullable = false, length = 32)
      @Enumerated(EnumType.STRING)
      private OobManagementType type;

      @Column(nullable = false, length = 128)
      private String address;

      @Column
      private Integer port;

      @Column(length = 64)
      @EncryptColumn
      @Convert(converter = PasswordConverter.class)
      private String username;

      @Column(length = 256)
      @EncryptColumn
      @Convert(converter = PasswordConverter.class)
      private String password;

      /**
       * API 版本（如 IPMI 2.0, Redfish 1.6）
       */
      @Column(length = 32)
      private String protocolVersion;

      /**
       * 连接状态
       */
      @Column
      @Enumerated(EnumType.STRING)
      private OobConnectionStatus status;

      /**
       * SSL/TLS 设置
       */
      @Column
      private Boolean useSsl;

      @Column
      private Boolean verifyCertificate;

      /**
       * 额外配置（JSON）
       */
      @Column(columnDefinition = "TEXT")
      private String additionalConfig;

      @Column
      private Timestamp lastSuccessTime;

      @Column
      private Timestamp createDate;

      @Column
      private Timestamp lastOpDate;
  }

  3.1.5 HardwareCapabilityVO（硬件能力表）

  /**
   * 硬件能力特性表
   * 记录硬件支持的功能和特性
   */
  @Entity
  @Table(name = "HardwareCapabilityVO")
  public class HardwareCapabilityVO {

      @Id
      @Column(length = 32)
      private String uuid;

      // 虚拟化能力
      @Column
      private Boolean supportsVirtualization;

      @Column(columnDefinition = "TEXT")
      private String supportedHypervisors; // JSON: ["KVM", "VMware", "Xen"]

      // 容器能力
      @Column
      private Boolean supportsContainer;

      @Column(columnDefinition = "TEXT")
      private String supportedContainerRuntimes; // JSON: ["Docker", "containerd"]

      // PXE 启动
      @Column
      private Boolean supportsPxeBoot;

      @Column
      private Boolean supportsUefiBoot;

      // NUMA
      @Column
      private Boolean supportsNuma;

      @Column
      private Integer numaNodes;

      // SR-IOV
      @Column
      private Boolean supportsSriov;

      // 硬件特性
      @Column
      private Boolean supportsHardwareRaid;

      @Column
      private Boolean supportsTpm;

      @Column
      private Boolean supportsSecureBoot;

      // 热插拔
      @Column
      private Boolean supportsHotplug;

      // 电源管理
      @Column(columnDefinition = "TEXT")
      private String powerManagementFeatures; // JSON: ["ACPI", "PowerCapping"]

      // 监控能力
      @Column(columnDefinition = "TEXT")
      private String monitoringCapabilities; // JSON: 支持的监控指标

      // 扩展能力（JSON）
      @Column(columnDefinition = "TEXT")
      private String extendedCapabilities;

      @Column
      private Timestamp createDate;

      @Column
      private Timestamp lastOpDate;
  }

  ---
  四、裸金属详细说明

  4.1 裸金属现有架构分析

  裸金属现有体系：

  BaremetalChassisVO (机箱管理)
      │
      ├─ ipmiAddress/Port/User/Pass (IPMI 信息)
      ├─ pxeServerUuid (PXE 服务器)
      ├─ state/status
      └─ hardwareInfos (Set<BaremetalHardwareInfoVO>)
            └─ type + content (硬件信息KV对)

  BaremetalInstanceVO (裸金属实例)
      └─ 部署在 Chassis 上的操作系统实例

  裸金属特殊性：
  1. Chassis（机箱）：物理硬件实体，一个机箱可能包含多个节点（刀片服务器）
  2. Instance（实例）：在 Chassis 上部署的操作系统，类似虚拟机概念
  3. PXE 部署：通过网络启动安装系统
  4. IPMI 管理：带外管理接口，可以远程开关机、监控硬件
  5. 硬件发现：通过 IPMI/Redfish 自动发现硬件配置

  4.2 裸金属接入新架构的完整流程

  4.2.1 接入流程图

  ┌─────────────────────────────────────────────────────────┐
  │ 阶段1: 硬件发现 (Hardware Discovery)                      │
  └─────────────────────────────────────────────────────────┘
      │
      ├─ 1.1 用户输入 IPMI 信息
      │      - IP地址、端口、用户名、密码
      │
      ├─ 1.2 连接 IPMI/Redfish 接口
      │      - 验证连接
      │      - 获取基本信息（厂商、型号、序列号）
      │
      └─ 1.3 硬件信息采集
             - CPU: 型号、核心数、主频
             - 内存: 容量、类型、插槽
             - 存储: 磁盘列表、RAID配置
             - 网卡: MAC地址、数量、速率
             - BIOS: 版本、配置
             ↓
  ┌─────────────────────────────────────────────────────────┐
  │ 阶段2: 创建统一硬件记录                                   │
  └─────────────────────────────────────────────────────────┘
      │
      ├─ 2.1 创建 UnifiedHardwareVO
      │      - category = BAREMETAL_CHASSIS
      │      - hardwareType = Baremetal_x86/Baremetal_ARM
      │      - serialNumber = 硬件序列号
      │      - vendor/model = 厂商信息
      │
      ├─ 2.2 创建 HardwareDetailVO
      │      - 保存详细硬件配置
      │      - CPU/内存/存储/网络详情
      │
      ├─ 2.3 创建 HardwareOobManagementVO
      │      - type = IPMI/REDFISH
      │      - 保存带外管理凭证
      │
      └─ 2.4 创建 HardwareCapabilityVO
             - supportsPxeBoot = true
             - supportsUefiBoot = 检测结果
             ↓
  ┌─────────────────────────────────────────────────────────┐
  │ 阶段3: 兼容现有 BaremetalChassisVO                        │
  └─────────────────────────────────────────────────────────┘
      │
      ├─ 3.1 创建 BaremetalChassisVO（保持向下兼容）
      │      - 从 UnifiedHardwareVO 同步基本信息
      │      - ipmiAddress/Port 从 HardwareOobManagementVO 同步
      │      - pxeServerUuid 关联 PXE 服务器
      │
      ├─ 3.2 创建 BaremetalHardwareInfoVO 记录
      │      - 从 HardwareDetailVO 转换
      │      - type="CPU", content=JSON
      │      - type="Memory", content=JSON
      │      - type="Storage", content=JSON
      │
      └─ 3.3 创建 HardwareReferenceVO
             - hardwareUuid = UnifiedHardwareVO.uuid
             - referenceType = BAREMETAL_CHASSIS
             - referenceUuid = BaremetalChassisVO.uuid
             ↓
  ┌─────────────────────────────────────────────────────────┐
  │ 阶段4: 注册到集群                                         │
  └─────────────────────────────────────────────────────────┘
      │
      ├─ 4.1 验证 Zone/Cluster
      │      - 检查 Zone 是否存在
      │      - 检查 Cluster 类型是否支持裸金属
      │
      ├─ 4.2 关联 PXE Server
      │      - 查找或创建 PXE 服务器
      │      - 建立 Chassis 与 PXE Server 关联
      │
      └─ 4.3 更新状态
             - UnifiedHardwareVO.state = Enabled
             - BaremetalChassisVO.state = Enabled
             ↓
  ┌─────────────────────────────────────────────────────────┐
  │ 阶段5: 部署实例 (可选)                                    │
  └─────────────────────────────────────────────────────────┘
      │
      ├─ 5.1 创建 BaremetalInstanceVO
      │      - 选择镜像
      │      - 配置网络
      │      - 分配存储
      │
      ├─ 5.2 PXE 部署流程
      │      - 生成 PXE 配置
      │      - 通过 IPMI 设置启动顺序
      │      - 重启服务器
      │      - 监控部署进度
      │
      └─ 5.3 部署后处理
             - 安装 Agent
             - 注册到管理平面
             - 更新实例状态

  4.2.2 关键代码流程

  步骤1: 硬件发现服务

  @Component
  public class HardwareDiscoveryService {

      /**
       * 发现裸金属硬件
       */
      public HardwareDiscoveryResult discoverBaremetalHardware(
          String ipmiAddress,
          Integer ipmiPort,
          String username,
          String password,
          OobManagementType oobType) {

          // 1. 连接 IPMI/Redfish
          OobClient oobClient = createOobClient(oobType, ipmiAddress, ipmiPort, username, password);
          oobClient.connect();

          // 2. 获取系统信息
          SystemInfo systemInfo = oobClient.getSystemInfo();

          // 3. 获取详细硬件配置
          HardwareInfo hwInfo = new HardwareInfo();
          hwInfo.setCpuInfo(oobClient.getCpuInfo());
          hwInfo.setMemoryInfo(oobClient.getMemoryInfo());
          hwInfo.setStorageInfo(oobClient.getStorageDevices());
          hwInfo.setNetworkInfo(oobClient.getNetworkInterfaces());
          hwInfo.setBiosInfo(oobClient.getBiosInfo());

          // 4. 检测能力
          HardwareCapability capability = new HardwareCapability();
          capability.setSupportsPxeBoot(oobClient.supportsPxeBoot());
          capability.setSupportsUefiBoot(oobClient.supportsUefiBoot());
          capability.setSupportsRedfish(oobClient.supportsRedfish());

          return new HardwareDiscoveryResult(systemInfo, hwInfo, capability);
      }
  }

  步骤2: 裸金属注册服务

  @Component
  public class BaremetalRegistrationService {

      @Autowired
      private HardwareDiscoveryService discoveryService;

      @Autowired
      private UnifiedHardwareManager hardwareManager;

      /**
       * 注册裸金属机箱到新架构
       */
      @Transactional
      public BaremetalRegistrationResult registerBaremetalChassis(
          BaremetalChassisRegistrationRequest req) {

          // 1. 硬件发现
          HardwareDiscoveryResult discovery = discoveryService.discoverBaremetalHardware(
              req.getIpmiAddress(),
              req.getIpmiPort(),
              req.getIpmiUsername(),
              req.getIpmiPassword(),
              req.getOobType()
          );

          // 2. 创建统一硬件记录
          UnifiedHardwareVO hardware = new UnifiedHardwareVO();
          hardware.setUuid(Platform.getUuid());
          hardware.setName(req.getName());
          hardware.setCategory(HardwareCategory.BAREMETAL_CHASSIS);
          hardware.setHardwareType(determineHardwareType(discovery.getSystemInfo()));
          hardware.setZoneUuid(req.getZoneUuid());
          hardware.setClusterUuid(req.getClusterUuid());
          hardware.setManagementAddress(req.getIpmiAddress());
          hardware.setSerialNumber(discovery.getSystemInfo().getSerialNumber());
          hardware.setVendor(discovery.getSystemInfo().getVendor());
          hardware.setModel(discovery.getSystemInfo().getModel());
          hardware.setState(HardwareState.Enabled);
          hardware.setStatus(HardwareStatus.Connected);
          dbf.persist(hardware);

          // 3. 创建硬件详情
          HardwareDetailVO detail = createHardwareDetail(hardware.getUuid(), discovery.getHwInfo());
          dbf.persist(detail);

          // 4. 创建带外管理信息
          HardwareOobManagementVO oob = new HardwareOobManagementVO();
          oob.setUuid(hardware.getUuid());
          oob.setType(req.getOobType());
          oob.setAddress(req.getIpmiAddress());
          oob.setPort(req.getIpmiPort());
          oob.setUsername(req.getIpmiUsername());
          oob.setPassword(req.getIpmiPassword());
          oob.setStatus(OobConnectionStatus.Connected);
          dbf.persist(oob);

          // 5. 创建能力记录
          HardwareCapabilityVO capability = createCapability(hardware.getUuid(), discovery.getCapability());
          dbf.persist(capability);

          // 6. 兼容处理：创建 BaremetalChassisVO
          BaremetalChassisVO chassis = createLegacyBaremetalChassis(hardware, oob, req);
          dbf.persist(chassis);

          // 7. 创建 HardwareInfoVO 记录（兼容旧格式）
          List<BaremetalHardwareInfoVO> hardwareInfoList = convertToLegacyHardwareInfo(
              chassis.getUuid(),
              discovery.getHwInfo()
          );
          hardwareInfoList.forEach(info -> dbf.persist(info));

          // 8. 创建引用关联
          HardwareReferenceVO ref = new HardwareReferenceVO();
          ref.setUuid(Platform.getUuid());
          ref.setHardwareUuid(hardware.getUuid());
          ref.setReferenceType(HardwareReferenceType.BAREMETAL_CHASSIS);
          ref.setReferenceUuid(chassis.getUuid());
          ref.setSyncStatus(SyncStatus.SYNCED);
          dbf.persist(ref);

          // 9. 关联 PXE Server
          if (req.getPxeServerUuid() != null) {
              associatePxeServer(chassis.getUuid(), req.getPxeServerUuid());
          }

          return new BaremetalRegistrationResult(hardware, chassis);
      }

      /**
       * 创建兼容的 BaremetalChassisVO
       */
      private BaremetalChassisVO createLegacyBaremetalChassis(
          UnifiedHardwareVO hardware,
          HardwareOobManagementVO oob,
          BaremetalChassisRegistrationRequest req) {

          BaremetalChassisVO chassis = new BaremetalChassisVO();
          chassis.setUuid(hardware.getUuid()); // 使用相同的 UUID
          chassis.setName(hardware.getName());
          chassis.setDescription(hardware.getDescription());
          chassis.setZoneUuid(hardware.getZoneUuid());
          chassis.setClusterUuid(hardware.getClusterUuid());
          chassis.setPxeServerUuid(req.getPxeServerUuid());

          // 同步 IPMI 信息
          chassis.setIpmiAddress(oob.getAddress());
          chassis.setIpmiPort(oob.getPort());
          chassis.setIpmiUsername(oob.getUsername());
          chassis.setIpmiPassword(oob.getPassword());

          // 同步状态
          chassis.setState(convertToBaremetalState(hardware.getState()));
          chassis.setStatus(convertToBaremetalStatus(hardware.getStatus()));

          return chassis;
      }

      /**
       * 转换硬件详情为旧格式
       */
      private List<BaremetalHardwareInfoVO> convertToLegacyHardwareInfo(
          String chassisUuid,
          HardwareInfo hwInfo) {

          List<BaremetalHardwareInfoVO> result = new ArrayList<>();

          // CPU 信息
          BaremetalHardwareInfoVO cpuInfo = new BaremetalHardwareInfoVO();
          cpuInfo.setUuid(Platform.getUuid());
          cpuInfo.setChassisUuid(chassisUuid);
          cpuInfo.setType("CPU");
          cpuInfo.setContent(JSONObjectUtil.toJsonString(hwInfo.getCpuInfo()));
          result.add(cpuInfo);

          // 内存信息
          BaremetalHardwareInfoVO memInfo = new BaremetalHardwareInfoVO();
          memInfo.setUuid(Platform.getUuid());
          memInfo.setChassisUuid(chassisUuid);
          memInfo.setType("Memory");
          memInfo.setContent(JSONObjectUtil.toJsonString(hwInfo.getMemoryInfo()));
          result.add(memInfo);

          // 存储信息
          BaremetalHardwareInfoVO storageInfo = new BaremetalHardwareInfoVO();
          storageInfo.setUuid(Platform.getUuid());
          storageInfo.setChassisUuid(chassisUuid);
          storageInfo.setType("Storage");
          storageInfo.setContent(JSONObjectUtil.toJsonString(hwInfo.getStorageInfo()));
          result.add(storageInfo);

          // 网络信息
          BaremetalHardwareInfoVO nicInfo = new BaremetalHardwareInfoVO();
          nicInfo.setUuid(Platform.getUuid());
          nicInfo.setChassisUuid(chassisUuid);
          nicInfo.setType("Network");
          nicInfo.setContent(JSONObjectUtil.toJsonString(hwInfo.getNetworkInfo()));
          result.add(nicInfo);

          return result;
      }
  }

  步骤3: 数据同步服务

  @Component
  public class HardwareSyncService {

      /**
       * 双向同步：UnifiedHardwareVO <-> BaremetalChassisVO
       */
      @Transactional
      public void syncBaremetalChassisData(String hardwareUuid) {

          // 1. 查询统一硬件记录
          UnifiedHardwareVO hardware = dbf.findByUuid(hardwareUuid, UnifiedHardwareVO.class);

          // 2. 查询关联的 BaremetalChassisVO
          HardwareReferenceVO ref = Q.New(HardwareReferenceVO.class)
              .eq(HardwareReferenceVO_.hardwareUuid, hardwareUuid)
              .eq(HardwareReferenceVO_.referenceType, HardwareReferenceType.BAREMETAL_CHASSIS)
              .find();

          BaremetalChassisVO chassis = dbf.findByUuid(ref.getReferenceUuid(), BaremetalChassisVO.class);

          // 3. 同步基本信息
          chassis.setName(hardware.getName());
          chassis.setDescription(hardware.getDescription());
          chassis.setState(convertToBaremetalState(hardware.getState()));
          chassis.setStatus(convertToBaremetalStatus(hardware.getStatus()));

          // 4. 同步 IPMI 信息
          HardwareOobManagementVO oob = dbf.findByUuid(hardwareUuid, HardwareOobManagementVO.class);
          if (oob != null) {
              chassis.setIpmiAddress(oob.getAddress());
              chassis.setIpmiPort(oob.getPort());
              chassis.setIpmiUsername(oob.getUsername());
              chassis.setIpmiPassword(oob.getPassword());
          }

          dbf.update(chassis);

          // 5. 更新同步状态
          ref.setSyncStatus(SyncStatus.SYNCED);
          ref.setLastSyncTime(new Timestamp(System.currentTimeMillis()));
          dbf.update(ref);
      }
  }

  ---
  五、API 设计（Function Spec）

  5.1 统一硬件管理 API

  5.1.1 创建硬件

  /**
   * API: CreateUnifiedHardware
   * 描述: 创建统一硬件资源
   */
  @ApiMessage
  public class APICreateUnifiedHardwareMsg extends APICreateMessage {
      @APIParam(maxLength = 128)
      private String name;

      @APIParam(required = false, maxLength = 2048)
      private String description;

      @APIParam(validValues = {"COMPUTE_HOST", "BAREMETAL_CHASSIS", "CONTAINER_HOST", "STORAGE_SERVER"})
      private String category;

      @APIParam
      private String hardwareType;

      @APIParam(resourceType = ZoneVO.class)
      private String zoneUuid;

      @APIParam(resourceType = ClusterVO.class, required = false)
      private String clusterUuid;

      @APIParam(required = false)
      private String managementAddress;

      @APIParam(required = false)
      private String architecture;

      @APIParam(required = false)
      private String serialNumber;

      @APIParam(required = false)
      private String vendor;

      @APIParam(required = false)
      private String model;

      @APIParam(required = false)
      private Map<String, String> metadata;

      // OOB 管理信息（可选）
      @APIParam(required = false)
      private OobManagementConfig oobConfig;

      // 自动发现配置（可选）
      @APIParam(required = false)
      private Boolean autoDiscover = false;
  }

  public class OobManagementConfig {
      private String type; // IPMI, REDFISH, ILO, IDRAC
      private String address;
      private Integer port;
      private String username;
      private String password;
      private String protocolVersion;
      private Boolean useSsl;
  }

  5.1.2 裸金属专用注册 API

  /**
   * API: RegisterBaremetalChassis
   * 描述: 注册裸金属机箱（含自动发现）
   */
  @ApiMessage
  public class APIRegisterBaremetalChassisMsg extends APICreateMessage {

      @APIParam(maxLength = 128)
      private String name;

      @APIParam(required = false, maxLength = 2048)
      private String description;

      @APIParam(resourceType = ZoneVO.class)
      private String zoneUuid;

      @APIParam(resourceType = ClusterVO.class)
      private String clusterUuid;

      // IPMI/Redfish 配置
      @APIParam
      private String ipmiAddress;

      @APIParam(numberRange = {1, 65535}, required = false)
      private Integer ipmiPort = 623;

      @APIParam
      private String ipmiUsername;

      @APIParam
      private String ipmiPassword;

      @APIParam(validValues = {"IPMI", "REDFISH"}, required = false)
      private String oobType = "IPMI";

      // PXE 服务器
      @APIParam(resourceType = BaremetalPxeServerVO.class, required = false)
      private String pxeServerUuid;

      /**
       * 是否执行硬件发现
       * true: 自动连接 IPMI 采集硬件信息
       * false: 仅创建记录，不采集详细信息
       */
      @APIParam(required = false)
      private Boolean performDiscovery = true;

      /**
       * 是否创建兼容的 BaremetalChassisVO
       */
      @APIParam(required = false)
      private Boolean createLegacyRecord = true;
  }

  // 返回结果
  public class APIRegisterBaremetalChassisEvent extends APIEvent {
      private UnifiedHardwareInventory hardware;
      private BaremetalChassisInventory chassis; // 兼容字段
      private HardwareDetailInventory detail;
      private HardwareDiscoveryReport discoveryReport;
  }

  public class HardwareDiscoveryReport {
      private Boolean success;
      private String message;
      private Map<String, Object> discoveredInfo;
      private List<String> warnings;
  }

  5.1.3 查询硬件

  /**
   * API: QueryUnifiedHardware
   * 描述: 查询统一硬件
   */
  @ApiMessage
  public class APIQueryUnifiedHardwareMsg extends APIQueryMessage {

      // 支持的查询条件
      public static List<String> __example__() {
          return list(
              // 按类别查询
              "category=BAREMETAL_CHASSIS",
              // 按类型查询
              "hardwareType=Baremetal_x86",
              // 按状态查询
              "state=Enabled",
              "status=Connected",
              // 按架构查询
              "architecture=x86_64",
              // 按厂商查询
              "vendor=Dell",
              // 按序列号查询
              "serialNumber=ABC123",
              // 按 Zone/Cluster
              "zoneUuid=xxx",
              "clusterUuid=yyy"
          );
      }
  }

  /**
   * API: GetUnifiedHardwareDetail
   * 描述: 获取硬件详细信息（包含所有关联数据）
   */
  @ApiMessage
  public class APIGetUnifiedHardwareDetailMsg extends APIMessage {
      @APIParam(resourceType = UnifiedHardwareVO.class)
      private String uuid;

      @APIParam(required = false)
      private Boolean includeDetail = true;

      @APIParam(required = false)
      private Boolean includeCapability = true;

      @APIParam(required = false)
      private Boolean includeOobManagement = false; // 安全考虑，默认不返回

      @APIParam(required = false)
      private Boolean includeLegacyReference = true;
  }

  // 返回
  public class APIGetUnifiedHardwareDetailReply extends APIReply {
      private UnifiedHardwareInventory hardware;
      private HardwareDetailInventory detail;
      private HardwareCapabilityInventory capability;
      private HardwareOobManagementInventory oobManagement;
      private HardwareReferenceInventory reference;
      private Object legacyEntity; // BaremetalChassisInventory 或 HostInventory
  }

  5.1.4 硬件操作 API

  /**
   * API: PowerControlHardware
   * 描述: 硬件电源控制
   */
  @ApiMessage
  public class APIPowerControlHardwareMsg extends APIMessage {
      @APIParam(resourceType = UnifiedHardwareVO.class)
      private String uuid;

      @APIParam(validValues = {"on", "off", "reset", "powerCycle"})
      private String action;

      @APIParam(required = false)
      private Boolean force = false;
  }

  /**
   * API: RefreshHardwareInfo
   * 描述: 刷新硬件信息（重新发现）
   */
  @ApiMessage
  public class APIRefreshHardwareInfoMsg extends APIMessage {
      @APIParam(resourceType = UnifiedHardwareVO.class)
      private String uuid;

      @APIParam(required = false)
      private Boolean fullRefresh = true; // false 仅刷新状态
  }

  /**
   * API: UpdateHardwareOobManagement
   * 描述: 更新带外管理配置
   */
  @ApiMessage
  public class APIUpdateHardwareOobManagementMsg extends APIMessage {
      @APIParam(resourceType = UnifiedHardwareVO.class)
      private String hardwareUuid;

      @APIParam(required = false)
      private String address;

      @APIParam(required = false)
      private Integer port;

      @APIParam(required = false)
      private String username;

      @APIParam(required = false)
      private String password;
  }

  /**
   * API: MigrateToUnifiedHardware
   * 描述: 将现有 BaremetalChassisVO 迁移到新架构
   */
  @ApiMessage
  public class APIMigrateBaremetalToUnifiedMsg extends APIMessage {
      @APIParam(resourceType = BaremetalChassisVO.class)
      private String chassisUuid;

      @APIParam(required = false)
      private Boolean performDiscovery = true;

      @APIParam(required = false)
      private Boolean keepLegacyRecord = true; // 保留旧记录
  }

  5.2 兼容性 API

  /**
   * 现有 BaremetalChassis API 保持不变，内部实现调整
   */

  // 原有 API 继续工作，内部会：
  // 1. 同时创建 UnifiedHardwareVO 和 BaremetalChassisVO
  // 2. 建立 HardwareReferenceVO 关联
  // 3. 响应返回 BaremetalChassisInventory（向下兼容）

  @ApiMessage
  public class APIAddBaremetalChassisMsg extends APICreateMessage {
      // 原有字段保持不变
      // 内部实现会调用 RegisterBaremetalChassisService
  }

  @ApiMessage
  public class APIQueryBaremetalChassisMsg extends APIQueryMessage {
      // 原有字段保持不变
      // 内部实现会通过 HardwareReferenceVO 查询
  }

  ---
  六、数据迁移方案

  6.1 迁移策略

  /**
   * 数据迁移服务
   * 将现有数据迁移到新架构
   */
  @Component
  public class HardwareMigrationService {

      /**
       * 迁移所有 KVMHostVO 到统一架构
       */
      @Transactional
      public void migrateKvmHosts() {
          List<KVMHostVO> kvmHosts = Q.New(KVMHostVO.class).list();

          for (KVMHostVO kvm : kvmHosts) {
              // 1. 创建 UnifiedHardwareVO
              UnifiedHardwareVO hardware = new UnifiedHardwareVO();
              hardware.setUuid(Platform.getUuid());
              hardware.setName(kvm.getName());
              hardware.setCategory(HardwareCategory.COMPUTE_HOST);
              hardware.setHardwareType("KVM");
              hardware.setZoneUuid(kvm.getZoneUuid());
              hardware.setClusterUuid(kvm.getClusterUuid());
              hardware.setManagementAddress(kvm.getManagementIp());
              hardware.setArchitecture(kvm.getArchitecture());
              hardware.setState(convertHostState(kvm.getState()));
              hardware.setStatus(convertHostStatus(kvm.getStatus()));
              dbf.persist(hardware);

              // 2. 创建引用关联
              HardwareReferenceVO ref = new HardwareReferenceVO();
              ref.setUuid(Platform.getUuid());
              ref.setHardwareUuid(hardware.getUuid());
              ref.setReferenceType(HardwareReferenceType.KVM_HOST);
              ref.setReferenceUuid(kvm.getUuid());
              ref.setSyncStatus(SyncStatus.SYNCED);
              dbf.persist(ref);

              // 3. 迁移 IPMI 信息（如果有）
              if (kvm.getIpmi() != null) {
                  HardwareOobManagementVO oob = new HardwareOobManagementVO();
                  oob.setUuid(hardware.getUuid());
                  oob.setType(OobManagementType.IPMI);
                  oob.setAddress(kvm.getIpmi().getIpmiAddress());
                  oob.setPort(kvm.getIpmi().getIpmiPort());
                  oob.setUsername(kvm.getIpmi().getIpmiUsername());
                  oob.setPassword(kvm.getIpmi().getIpmiPassword());
                  dbf.persist(oob);
              }

              logger.info("Migrated KVM Host: {} to UnifiedHardware: {}", kvm.getUuid(), hardware.getUuid());
          }
      }

      /**
       * 迁移所有 BaremetalChassisVO 到统一架构
       */
      @Transactional
      public void migrateBaremetalChassis() {
          List<BaremetalChassisVO> chassisList = Q.New(BaremetalChassisVO.class).list();

          for (BaremetalChassisVO chassis : chassisList) {
              // 1. 创建 UnifiedHardwareVO
              UnifiedHardwareVO hardware = new UnifiedHardwareVO();
              hardware.setUuid(chassis.getUuid()); // 使用相同 UUID
              hardware.setName(chassis.getName());
              hardware.setCategory(HardwareCategory.BAREMETAL_CHASSIS);
              hardware.setHardwareType("Baremetal_x86"); // 根据实际情况判断
              hardware.setZoneUuid(chassis.getZoneUuid());
              hardware.setClusterUuid(chassis.getClusterUuid());
              hardware.setManagementAddress(chassis.getIpmiAddress());
              hardware.setState(convertBaremetalState(chassis.getState()));
              hardware.setStatus(convertBaremetalStatus(chassis.getStatus()));
              dbf.persist(hardware);

              // 2. 创建 OOB 管理信息
              HardwareOobManagementVO oob = new HardwareOobManagementVO();
              oob.setUuid(hardware.getUuid());
              oob.setType(OobManagementType.IPMI);
              oob.setAddress(chassis.getIpmiAddress());
              oob.setPort(chassis.getIpmiPort());
              oob.setUsername(chassis.getIpmiUsername());
              oob.setPassword(chassis.getIpmiPassword());
              dbf.persist(oob);

              // 3. 迁移硬件详情
              Set<BaremetalHardwareInfoVO> hardwareInfos = chassis.getHardwareInfos();
              if (hardwareInfos != null && !hardwareInfos.isEmpty()) {
                  HardwareDetailVO detail = convertLegacyHardwareInfo(hardware.getUuid(), hardwareInfos);
                  dbf.persist(detail);
              }

              // 4. 创建引用关联（使用相同 UUID，不需要额外关联表）
              // 因为 hardware.uuid == chassis.uuid，可以直接关联

              logger.info("Migrated BaremetalChassis: {} to UnifiedHardware", chassis.getUuid());
          }
      }

      /**
       * 转换旧格式硬件信息到新格式
       */
      private HardwareDetailVO convertLegacyHardwareInfo(
          String uuid,
          Set<BaremetalHardwareInfoVO> legacyInfos) {

          HardwareDetailVO detail = new HardwareDetailVO();
          detail.setUuid(uuid);

          for (BaremetalHardwareInfoVO info : legacyInfos) {
              try {
                  JSONObject content = JSONObject.parseObject(info.getContent());

                  switch (info.getType()) {
                      case "CPU":
                          detail.setCpuModel(content.getString("model"));
                          detail.setCpuCores(content.getInteger("cores"));
                          detail.setCpuThreads(content.getInteger("threads"));
                          break;
                      case "Memory":
                          detail.setTotalMemory(content.getLong("total"));
                          detail.setMemoryType(content.getString("type"));
                          break;
                      case "Storage":
                          detail.setStorageDevices(content.toJSONString());
                          break;
                      case "Network":
                          detail.setNetworkInterfaces(content.toJSONString());
                          detail.setNicCount(content.getInteger("count"));
                          break;
                  }
              } catch (Exception e) {
                  logger.warn("Failed to parse hardware info: " + info.getType(), e);
              }
          }

          return detail;
      }
  }

  6.2 数据库迁移脚本

  -- V1.0__Create_Unified_Hardware_Tables.sql

  -- 1. 创建统一硬件表
  CREATE TABLE IF NOT EXISTS `UnifiedHardwareVO` (
      `uuid` VARCHAR(32) NOT NULL PRIMARY KEY,
      `name` VARCHAR(128) NOT NULL,
      `description` VARCHAR(2048),
      `category` VARCHAR(32) NOT NULL,
      `hardwareType` VARCHAR(64) NOT NULL,
      `zoneUuid` VARCHAR(32),
      `clusterUuid` VARCHAR(32),
      `managementAddress` VARCHAR(256),
      `state` VARCHAR(32) NOT NULL,
      `status` VARCHAR(32) NOT NULL,
      `architecture` VARCHAR(32),
      `serialNumber` VARCHAR(128) UNIQUE,
      `vendor` VARCHAR(64),
      `model` VARCHAR(64),
      `metadata` TEXT,
      `createDate` TIMESTAMP NOT NULL,
      `lastOpDate` TIMESTAMP NOT NULL,
      `lastHeartbeatTime` TIMESTAMP,
      INDEX `idx_category` (`category`),
      INDEX `idx_hardwareType` (`hardwareType`),
      INDEX `idx_zoneUuid` (`zoneUuid`),
      INDEX `idx_clusterUuid` (`clusterUuid`),
      INDEX `idx_state` (`state`),
      INDEX `idx_status` (`status`),
      CONSTRAINT `fk_UnifiedHardwareVO_ZoneVO` FOREIGN KEY (`zoneUuid`) REFERENCES `ZoneVO`(`uuid`) ON DELETE RESTRICT,
      CONSTRAINT `fk_UnifiedHardwareVO_ClusterVO` FOREIGN KEY (`clusterUuid`) REFERENCES `ClusterVO`(`uuid`) ON DELETE
  SET NULL
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8;

  -- 2. 创建硬件引用关联表
  CREATE TABLE IF NOT EXISTS `HardwareReferenceVO` (
      `uuid` VARCHAR(32) NOT NULL PRIMARY KEY,
      `hardwareUuid` VARCHAR(32) NOT NULL UNIQUE,
      `referenceType` VARCHAR(64) NOT NULL,
      `referenceUuid` VARCHAR(32) NOT NULL,
      `syncStatus` VARCHAR(32),
      `createDate` TIMESTAMP NOT NULL,
      `lastSyncTime` TIMESTAMP,
      UNIQUE KEY `uk_reference` (`referenceType`, `referenceUuid`),
      INDEX `idx_hardwareUuid` (`hardwareUuid`),
      INDEX `idx_referenceUuid` (`referenceUuid`),
      CONSTRAINT `fk_HardwareReferenceVO_UnifiedHardwareVO` FOREIGN KEY (`hardwareUuid`) REFERENCES
  `UnifiedHardwareVO`(`uuid`) ON DELETE CASCADE
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8;

  -- 3. 创建硬件详情表
  CREATE TABLE IF NOT EXISTS `HardwareDetailVO` (
      `uuid` VARCHAR(32) NOT NULL PRIMARY KEY,
      `cpuSockets` INT,
      `cpuCores` INT,
      `cpuThreads` INT,
      `cpuModel` VARCHAR(256),
      `cpuFrequency` BIGINT,
      `totalMemory` BIGINT,
      `memorySlots` INT,
      `memoryType` VARCHAR(64),
      `storageDevices` TEXT,
      `raidConfig` TEXT,
      `networkInterfaces` TEXT,
      `nicCount` INT,
      `biosVersion` VARCHAR(128),
      `biosVendor` VARCHAR(128),
      `biosReleaseDate` TIMESTAMP,
      `firmwareVersion` VARCHAR(128),
      `accelerators` TEXT,
      `powerSupplies` TEXT,
      `chassisType` VARCHAR(128),
      `rackUnit` INT,
      `rackPosition` VARCHAR(64),
      `extendedInfo` TEXT,
      `lastDiscoveryTime` TIMESTAMP,
      `createDate` TIMESTAMP NOT NULL,
      `lastOpDate` TIMESTAMP NOT NULL,
      CONSTRAINT `fk_HardwareDetailVO_UnifiedHardwareVO` FOREIGN KEY (`uuid`) REFERENCES `UnifiedHardwareVO`(`uuid`) ON
  DELETE CASCADE
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8;

  -- 4. 创建带外管理表
  CREATE TABLE IF NOT EXISTS `HardwareOobManagementVO` (
      `uuid` VARCHAR(32) NOT NULL PRIMARY KEY,
      `type` VARCHAR(32) NOT NULL,
      `address` VARCHAR(128) NOT NULL,
      `port` INT,
      `username` VARCHAR(64),
      `password` VARCHAR(256),
      `protocolVersion` VARCHAR(32),
      `status` VARCHAR(32),
      `useSsl` TINYINT(1),
      `verifyCertificate` TINYINT(1),
      `additionalConfig` TEXT,
      `lastSuccessTime` TIMESTAMP,
      `createDate` TIMESTAMP NOT NULL,
      `lastOpDate` TIMESTAMP NOT NULL,
      INDEX `idx_address` (`address`),
      CONSTRAINT `fk_HardwareOobManagementVO_UnifiedHardwareVO` FOREIGN KEY (`uuid`) REFERENCES
  `UnifiedHardwareVO`(`uuid`) ON DELETE CASCADE
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8;

  -- 5. 创建硬件能力表
  CREATE TABLE IF NOT EXISTS `HardwareCapabilityVO` (
      `uuid` VARCHAR(32) NOT NULL PRIMARY KEY,
      `supportsVirtualization` TINYINT(1),
      `supportedHypervisors` TEXT,
      `supportsContainer` TINYINT(1),
      `supportedContainerRuntimes` TEXT,
      `supportsPxeBoot` TINYINT(1),
      `supportsUefiBoot` TINYINT(1),
      `supportsNuma` TINYINT(1),
      `numaNodes` INT,
      `supportsSriov` TINYINT(1),
      `supportsHardwareRaid` TINYINT(1),
      `supportsTpm` TINYINT(1),
      `supportsSecureBoot` TINYINT(1),
      `supportsHotplug` TINYINT(1),
      `powerManagementFeatures` TEXT,
      `monitoringCapabilities` TEXT,
      `extendedCapabilities` TEXT,
      `createDate` TIMESTAMP NOT NULL,
      `lastOpDate` TIMESTAMP NOT NULL,
      CONSTRAINT `fk_HardwareCapabilityVO_UnifiedHardwareVO` FOREIGN KEY (`uuid`) REFERENCES `UnifiedHardwareVO`(`uuid`)
   ON DELETE CASCADE
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8;

  ---
  七、实施路线图

  Phase 1: 基础架构搭建 (2-3周)

  - 创建新数据表结构
  - 实现 UnifiedHardwareVO 及相关 VO
  - 实现 HardwareReferenceVO 关联机制
  - 单元测试

  Phase 2: 裸金属集成 (3-4周)

  - 实现硬件发现服务（IPMI/Redfish）
  - 实现 BaremetalRegistrationService
  - 实现数据同步服务
  - BaremetalChassisVO 兼容层
  - 集成测试

  Phase 3: KVM/Container 集成 (2-3周)

  - KVMHostVO 适配
  - NativeHostVO 适配
  - 统一查询接口
  - 兼容性测试

  Phase 4: API 开发 (2周)

  - 统一硬件管理 API
  - 裸金属专用 API
  - 兼容性 API 调整
  - API 文档

  Phase 5: 数据迁移 (1-2周)

  - 迁移脚本开发
  - 灰度迁移测试
  - 全量迁移
  - 回滚方案

  Phase 6: 上线与优化 (1-2周)

  - 性能优化
  - 监控告警
  - 文档完善
  - 培训支持

  ---
  八、关键注意事项

  1. 向下兼容：现有 BaremetalChassisVO 相关 API 必须继续工作
  2. 数据一致性：UnifiedHardwareVO 与 BaremetalChassisVO 需要双向同步
  3. 安全性：IPMI 密码等敏感信息需要加密存储
  4. 性能：大规模硬件查询需要优化索引
  5. 事务性：硬件注册需要保证原子性
  6. 幂等性：重复注册相同硬件（按序列号）应该更新而非创建
  7. 监控：硬件状态需要定期心跳检测
  8. 扩展性：为未来接入网络设备、存储设备预留扩展点

  ---
  