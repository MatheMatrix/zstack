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
  - getSdnControllerSecurityGroup(): 返回null, ZNS不需要安全组SDN后端
  - getSdnControllerDhcp(): 返回null, ZNS不配置DHCP
- ZnsSdnController: 实现SdnController接口, 处理控制器生命周期（创建/删除/重连）; addHost/removeHost 仅操作数据库，不修改物理机配置
- ZnsSdnControllerL2: 实现SdnControllerL2接口
  - createL2Network 调用zns api创建segment
  - deleteL2Network 调用zns api删除segment
  - attachL2NetworkToCluster/detachL2NetworkFromCluster: 调用zns api修改segment的transport zone关联
  - addVmNics(): 调用zns api创建segment port
  - removeVmNics(): 调用zns api删除segment port
  - 其它函数空实现(直接completion.success())
- ZnsSdnControllerL3: 实现SdnControllerL3接口
   - createIpRange(): 调用zns api修改segment的cidr
   - deleteIpRange(): 调用zns api修改segment的cidr
   - createL3Network()/deleteL3Network(): 空实现(直接completion.success()) 
- ZnsSdnControllerConstant: 定义常量 ZNS_CONTROLLER = "ZNS"

## 创建SDN控制器
必须现在ZNS完成添加computer Manager的操作，才能在Cloud侧创建对应的SdnController。
UI调用: APIAddSdnControllerMsg, 同时携带一个SystemTags: computerManagerUuid::xxxx, cloud侧根据这个tag把computer Manager和SdnController关联起来
Cloud后续API操作，会把这个computerManagerUuid通过作为cms uuid传给给ZNS, 这样用来区分ZNS的segment, segment port是那个cloud创建的。

在添加SdnController的过程中, initSdnController函数实现如下逻辑:
1. 根据computer Manager的uuid获取ZNS Host列表, 配置SdnControllerHostRefVO, 关联到SdnControllerVO
2. 根据computerManagerUuid获取ZNS segment列表, 配置L2NetworkVO, L3NetworkVO, IpRangeVO
3. 根据ZNS segment和transport zone的关系, 配置L2NetworkClusterRefVO, 关联L2NetworkVO和ClusterVO


## 删除SDN控制器
和其它类型的SdnController一样, 删除SdnControllerVO, SdnControllerHostRefVO, L2NetworkVO, L3NetworkVO, IpRangeVO, L2NetworkClusterRefVO等相关数据对象
但是不需要执行删除物理机ovs dpdk操作

## 同步

由于ZNS和Cloud之间可能出现配置不一致，因此需要提供一个定期同步机制。定时器间隔 5mins.
同步实现方式：定时器向SDN Controller发送SyncMsg, 该消息与其他API操作共用相同的SDN Controller队列串行执行，避免并发冲突。
1. Cloud读取属于当前cloud的Segment列表，cloud查询的时候会提供computer Manager的uuid,
   1. 如果zns存在，但是cloud不存在，且zns的segment也没有其它cms使用，调用API删除zns segment
   2. 如果zns存在，cloud也存在，需要比较两个侧的参数，如果不一致，更新zns测的参数，比如cidr
   3. 如果zns不存在，但是cloud存在，调用API添加zns segment。根据l3信息添加segment cidr
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
**注意：新增的VSwitchType("ZNS")必须设置sdnControllerType为"ZNS"**, 即 `new VSwitchType("ZNS").setSdnControllerType("ZNS")`。
这是SdnControllerManagerImpl判断L2网络是否归SDN Controller管理的关键属性，影响preInstantiateVmResource、releaseVmResource、
instantiateResourceOnAttachingNic、releaseResourceOnDetachingNic等所有扩展点的正确路由。
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

### KVM Realize Backend
需要为L2GeneveNetwork注册KVM后端实现：
- KVMRealizeL2GeneveNetworkBackend: 实现KVMCompleteNicInformationExtensionPoint接口
  - 按L2NetworkType("L2GeneveNetwork")注册到KVMHostFactory的completeNicInfoExtensions映射中
  - completeNicInformation()方法: 填充NicTO的bridgeName、physicalInterface、mtu等信息。
    对于OvnDpdk模式需要设置srcPath (与KVMRealizeL2NoVlanNetworkBackend中OvnDpdk的处理逻辑一致)
  - realize/check/delete方法: 由于ZNS/OVS管理bridge，这些方法可以做空实现，
    但必须注册，否则attach L2到cluster或host reconnect时会因找不到backend而失败
- 如果L2NoVlanNetwork和L2VlanNetwork类型的ZNS网络复用现有Backend，
  需确认这些backend能正确处理vSwitchType为ZNS的情况

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

ZNS L3 API不需要调用Sdn backend。
ZnsSdnControllerFactory.getSdnControllerL3()返回null, SdnControllerManagerImpl中getSdnControllerL3()
在controllerUuid为null或factory返回null时会自然跳过, 不影响L3 CRUD操作。

ZNS L3不需要配置任何网络服务（无DHCP, 无DNS, 无UserData, 无EIP, 无PortForwarding等）

Cloud侧的IpRange只做记录，不参与IP分配。IP由ZNS负责管理和分配。
SdnControllerManagerImpl的afterAddIpRange/afterDeleteIpRange会调用SdnControllerL2的addL3NetworkIpRange/deleteL3NetworkIpRange,
ZnsSdnControllerL2中这两个方法做空实现（直接completion.success()）。

## SetVmStaticIp / ChangeVmIp 操作
由于ZNS网络的IP由ZNS管理，APISetVmStaticIpMsg和APIChangeVmIpMsg需要特殊处理：
- 在VmInstanceApiInterceptor中增加校验：如果目标L3Network关联的L2Network的vSwitchType为ZNS，
  需要将用户指定的IP传给ZNS segment port API进行更新，而非走Cloud侧的IP分配流程

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
VmAllocateNicFlow/ApplianceVmAllocateNicFlow 分别是创建虚拟机,applianceVm的过程创建网卡的过程。
ApplianceVm可能使用ZNS网络, 因此ApplianceVmAllocateNicFlow也需要适配ZNS流程。
zns网络创建过程需要调整：
- 和现在逻辑一样分配网卡mac, internalId, internalName, driverType
- 调用zns创建segment port api, 获取ip/掩码/网关，ip6/前缀/网关, cms信息中携带computerManagerUuid
- zns L3网络走 enableIpAddressAllocation()为false流程, Cloud直接把ZNS返回的IP地址保存到UsedIpVO, 不走Cloud侧的IP分配流程
- 根据获取的参数创建VmNicVO, UsedIpVO

注意VmAllocateNicIpFlow（在VmAllocateNicFlow之后执行）负责给已创建的Nic分配IP。
对于ZNS网络，由于enableIpAddressAllocation()为false且IP已在VmAllocateNicFlow中通过ZNS API获取并保存，
VmAllocateNicIpFlow会跳过这些Nic，不会重复处理。

### 网卡创建失败回滚
如果调用ZNS segment port API成功获取到IP, 但后续创建VmNicVO/UsedIpVO失败, 需要回滚调用ZNS删除segment port API释放IP。
在VmAllocateNicFlow的rollback方法中实现: 检查是否已调用ZNS分配了segment port, 如果是则调用ZNS删除segment port。

## 网卡删除过程:
VmReturnReleaseNicFlow 在destroyVmWorkFlowElements中被调用, 用于虚拟机销毁时释放网卡资源。
VmDetachNicFlow 在云主机删除网卡的时候调用。
两个Flow中都需要：
- 调用zns删除segment port api
- 删除VmNicVO, UsedIpVO

## VM Start/Reboot 时的资源管理
SdnControllerManagerImpl实现了PreVmInstantiateResourceExtensionPoint和VmReleaseResourceExtensionPoint:
- preInstantiateVmResource(): VM启动/重启时, 通过vSwitchType查找sdnControllerType, 调用SdnControllerL2.addVmNics()。
  ZNS场景下: dpdkvhostuserclient网卡与OVN逻辑端口处理一致; VNIC网卡无需额外操作(addVmNics中按网卡类型判断即可)。
- releaseVmResource(): VM销毁/detachNic时, 调用SdnControllerL2.removeVmNics()。
  ZNS场景下: dpdkvhostuserclient网卡与OVN逻辑端口处理一致; VNIC网卡无需额外操作。

## VM迁移
迁移流程(VmMigrationCheckL2NetworkOnHostFlow -> VmAllocateHostForMigrateVmFlow -> VmMigrateOnHypervisorFlow)中:
- VmMigrationCheckL2NetworkOnHostFlow: 检查目标主机是否关联了VM所需的L2网络(通过L2NetworkClusterRefVO), ZNS网络无需额外处理
- 迁移时ZNS segment port不需要做操作，ZNS的segment port不绑定特定物理机信息
- dpdkvhostuserclient类型网卡: 与OVN端口一样, 如果OVN在迁移时有特殊处理(如postMigrateVm扩展点), ZNS也需要相同处理

## ChangeVmNicNetwork（换网操作）
APIChangeVmNicNetworkMsg涉及detach旧网络 + attach新网络:
- 不支持从ZNS变换成非ZNS网络，或从非ZNS变换成ZNS网络。
- 从ZNS网络变换成ZNS网络的场景，需要想ZNS调用API删除旧的segment port, 调用API创建新的segment port, 并更新VmNicVO/UsedIpVO等相关数据对象。

## FilterAttachableL3NetworkExtensionPoint
OVN实现了此扩展点用于过滤可挂载的L3网络。ZNS也需要实现此扩展点：
- 过滤逻辑: 确保只有ZNS SDN Controller关联的物理机上的VM才能挂载ZNS L3网络
- 在ZnsSdnControllerFactory或独立的扩展类中实现

