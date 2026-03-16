# 总体描述
在考虑对接ZNS SDN控制器时，首先需要考虑如何做好Cloud资源对象和ZNS资源映射。
ZNS: segments --> L2Network + L3Network + IpRange
ZNS: segments port --> VmNic + UsedIp
ZNS: segments + transport zone --> L2NetworkClusterRefVO
因为最终会把zns ui嵌套到cloud ui中，其他资源对象不需要映射。

# ZNS SDN控制器
ZStack 已经定义SdnControllerVO， 目前已经有 H3cVcfcSdnController，SugonSdnController，OvnController，HuaweiIMasterSdnController等实现。
新定义ZnsControllerVO, 继承 SdnControllerVO, 不添加新的字段
- vendorType： ZNS
- vendorVersion： 1.0

对应需要新增以下类：
- ZnsControllerVO: 继承SdnControllerVO, 无额外字段, 需要建表SQL(仅uuid主键关联)
- ZnsSdnControllerFactory: 实现SdnControllerFactory接口, 注册vendorType为"ZNS"
- ZnsSdnController: 实现SdnController接口, 处理控制器生命周期（创建/删除/重连）; addHost/removeHost 仅操作数据库，不修改物理机配置
- ZnsSdnControllerL2: 实现SdnControllerL2接口
- ZnsSdnControllerConstant: 定义常量 ZNS_CONTROLLER = "ZNS"

## 创建SDN控制器
必须现在ZNS完成添加computer Manager的操作，才能在Cloud侧创建对应的SdnController。
UI调用: APIAddSdnControllerMsg, 同时携带一个SystemTags: computerManagerUuid::xxxx, cloud侧根据这个tag把computer Manager和SdnController关联起来

然后cloud通过获取ZNS host的参数, 设置SdnControllerHostRefVO的vSwitchType为OvnKernel或者OvnDpdk, 来区分物理机部署的ovs类型。

需要对现有API做以下调整：APISdnControllerAddHostMsg/APISdnControllerRemoveHostMsg zns controller不支持这两个API


## 创建VmNic
用户在cloud侧创建虚拟机/applianceVm; 给虚拟机/applianceVm 添加网卡, Cloud调用 ZNS API 创建segment port, ZNS负责IP地址管理
用户在cloud侧删除虚拟机/applianceVm; 给虚拟机/applianceVm 删除网卡,Cloud调用 ZNS API 删除segment port
cloud调用zns segment port API的时候, 需要携带一个SystemTags: computerManagerUuid::xxxx


## 删除SDN控制器

## 同步

由于ZNS和Cloud之间可能出现配置不一致，因此需要提供一个定期同步机制。定时器间隔 5mins.
设计原则：Segment以ZNS为准（ZNS管理网络），Segment Port以Cloud为准（Cloud管理虚拟机）。

1. Cloud读取属于当前cloud的Segment列表，cloud查询的时候会提供computer Manager的uuid,
   1. 如果zns存在，但是cloud不存在，需要创建cloud侧的L2Network, L3Network, ip range
   2. 如果zns存在，cloud也存在，需要比较两个侧的参数，如果不一致，更新cloud侧参数为zns侧参数
   3. 如果zns不存在，但是cloud存在，不删除Cloud侧的L2Network, L3Network，而是标记为disable状态
2. 在完成Segment同步以后，Cloud读取属于当前cloud的Segment port列表，cloud查询的时候会提供computer Manager的uuid,
   1. 如果zns不存在，但是cloud存在，调用zns segment port API创建segment port
   2. 如果zns存在，但是cloud不存在，调用zns segment port API删除segment port
   3. 如果zns存在，cloud也存在，需要比较两个侧的的参数，如果不一致，更新zns侧参数为cloud侧参数


# L2Network

## 基础信息
L2NetworkVO 有重要字段：
- type: 它的值为L2NetworkType.types, 有：L2NoVlanNetwork, L2VlanNetwork, VxlanNetworkPool, VxlanNetwork, TfL2Network, HardwareVxlanNetworkPool, HardwareVxlanNetwork
- vSwitchType: 它的值为 VSwitchType.types, 有: LinuxBridge, TfL2Network, MacVlan, OvnDpdk, OvsDpdk
- virtualNetworkId: vlanId or vxlanID
- physicalInterface: 物理网卡名称

在不同的feature开发过程中，前面本来有明确意义的字段已经有些混乱了。
ZNS L2Network type 有三个值：L2NoVlanNetwork, L2VlanNetwork, L2GeneveNetwork. L2GeneveNetwork为新增类型，类似于L2VlanNetwork
ZNS L2Network vSwitchType: ZNS, 目前ZNS的vSwitchType是固定的，叫ZNS, 不区分kernel和dpdk, 
因为这个是host级别的属性，和L2Network无关; 物理机上部署了ovs kernel的机器和部署了ovs dpdk的机器都可以接入ZNS网络, 由用户在UI选择网卡类型的时候选择。
SdnControllerHostRefVO.vSwitchType 复用"OvnDpdk", "OvnKernel"表示物理机部署的ovs类型。
因此新增一个vSwitchType: ZNS (用于L2NetworkVO.vSwitchType)。
ZNS L2Network physicalInterface: 为null
ZNS L2Network virtualNetworkId: Vlan Id or Geneve Id

### 新增L2GeneveNetwork注册
需要新增以下类：
- L2GeneveNetworkVO: 继承L2NetworkVO, 增加geneveId字段(类似L2VlanNetworkVO的vlan字段), 需要建表SQL
- L2GeneveNetworkInventory: 对应的Inventory类
- L2GeneveNetworkFactory: 实现L2NetworkFactory接口, 注册 `new L2NetworkType("L2GeneveNetwork")`
- L2GeneveNetwork: 继承L2NoVlanNetwork, 处理L2GeneveNetwork的消息路由
- L2NetworkConstant中新增: `L2_GENEVE_NETWORK_TYPE = "L2GeneveNetwork"`
- ZnsVmNicFactory: 注册 `new VSwitchType("ZNS")`, 绑定对应的VmNicType

## 创建L2Network
- 处理逻辑类似ovn controller, 但是调用ZNS API创建segment

### APIAttachL2NetworkToClusterMsg, APIDetachL2NetworkFromClusterMsg
- 处理逻辑类似ovn controller，
- 根据ZNS Host和transport zone的关系，把zns segment关联到transport zone

### APIChangeL2NetworkVlanIdMsg 
- L2GeneveNetwork类型不支持修改VlanId, 需要在L2NetworkApiInterceptor中拦截: 如果L2Network的type为L2GeneveNetwork, 抛出ApiMessageInterceptionException
- L2VlanNetwork, L2NoVlanNetwork类型支持
- 也仅仅需要修改L2NetworkVO数据库，不需要下发到物理机, 需要调用修改ZNS segment API

AttachedL2NetworkAllocatorFlow 根据虚拟机选择的l2网络选择候选的物理机, 它根据L2NetworkClusterRefVO找到L2Network关联的cluster, 
从而找到cluster内的物理机器作为候选机器。

# L3Network

## 基础信息
L3NetworkVO 重要字段：
- type: L3BasicNetwork, L3VpcNetwork
- category： Public, Private, System

ZNS L3的Type是: L3VpcNetwork
ZNS L3的Category规则：
- L2GeneveNetwork类型的L3只能是Private
- L2NoVlanNetwork, L2VlanNetwork类型的L3可以是Public或Private

ZNS L3不配置DHCP网络服务, 因此enableIpAddressAllocation()为false。
当前enableIpAddressAllocation()实现中，L3VpcNetwork类型会返回true(因为type != L3BasicNetwork)。
需要调整enableIpAddressAllocation()逻辑：当L3Network关联的L2Network的vSwitchType为ZNS时返回false。

ZNS L3 API不需要调用Sdn backend
ZNS L3不需要配置任何网络服务（无DHCP, 无DNS, 无UserData, 无EIP, 无PortForwarding等）

Cloud侧的IpRange只做记录，不参与IP分配。IP由ZNS负责管理和分配。

# VmNic

它的值为VmNicType.types, 有： VNIC, VF, dpdkvhostuserclient
ZNS可能是dpdk模式，也可能是kernel, 在UI选择ZNS网络以后，用户可以选择网卡类型：VNIC, dpdkvhostuserclient

## 虚拟机的物理机分配
创建虚拟机选择了ZNS网络, 默认网卡类型是VNIC, 需要选择到部署了OvnKernel的物理机; 如果选择了 dpdkvhostuserclient，
需要选择到部署了OvnDpdk的物理机。
AttachedL2NetworkAllocatorFlow 会调用AttachedL2NetworkAllocatorExtensionPoint 扩展到进一步选择物理机，
这个扩展点根据l2找到ZNS控制器，根据 SdnControllerHostRefVO 找到物理机，如果网卡是VNIC，选择vSwitchType是:OvnKernel的物理机;
如果网卡是dpdkvhostuserclient，选择vSwitchType是:OvnDpdk的物理机;

## 网卡创建过程:
VmAllocateNicFlow/ApplianceVmAllocateNicFlow 分别是创建虚拟机,applianceVm的过程创建网卡的过程。zns网络创建过程需要调整：
- 和现在逻辑一样分配网卡mac, internalId, internalName, driverType
- 调用zns创建segment port api, 获取ip/掩码/网关，ip6/前缀/网关
- zns L3网络走 enableIpAddressAllocation()为false流程, Cloud直接把ZNS返回的IP地址保存到UsedIpVO, 不走Cloud侧的IP分配流程
- 根据获取的参数创建VmNicVO, UsedIpVO

### 网卡创建失败回滚
如果调用ZNS segment port API成功获取到IP, 但后续创建VmNicVO/UsedIpVO失败, 需要回滚调用ZNS删除segment port API释放IP。
在VmAllocateNicFlow的rollback方法中实现: 检查是否已调用ZNS分配了segment port, 如果是则调用ZNS删除segment port。

## 网卡删除过程:
VmReturnReleaseNicFlow 在destroyVmWorkFlowElements中被调用, 用于虚拟机销毁时释放网卡资源。
VmDetachNicFlow 在云主机删除网卡的时候调用。
两个Flow中都需要：
- 调用zns删除segment port api
- 删除VmNicVO, UsedIpVO

