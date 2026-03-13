
# 总体描述
在考虑对接ZNS SDN控制器时，首先需要考虑如何做好Cloud资源对象和ZNS资源映射。
ZNS: segments --> L2Network + L3Network + IpRange
ZNS: segments port --> VmNic + UsedIp
ZNS: segments + transport zone --> L2NetworkClusterRefVO
因为最终会把zns ui嵌套到cloud ui中，其他资源对象不需要映射。

# ZNS SDN控制器
ZStack 已经定义SdnControllerVO， 目前已经HuaweiIMasterSdnControllerVO，OvnControllerVO等。
新定义个OvnControllerVO, 直接继承SdnControllerVO
- vendorType： ZNS
- vendorVersion： 1.0

## 创建SDN控制器
用户在ZNS页面创建computor Manager的时候，ZNS 调用 cloud API 创建: APIAddSdnControllerMsg
用户在ZNS页面添加Host的时候，调用 cloud API 修改 SdnControllerHostRefVO, 需要添加一个新的API: APIChangeSdnControllerHostsMsg

Cloud UI不能手动添加ZNS控制器.

## ZNS创建Segment
用户在ZNS页面创建Segment, ZNS 调用 cloud API 创建L2 network, L3 network
Cloud侧不能创建/删除/修改ZNS L2Network, L3NetworkVO

用户在ZNS页面删除Segment, ZNS 调用 cloud API 删除L3 network, L2 network

用户在ZNS页面给Segment添加cidr, ZNS 调用 cloud API 创建Ip Range
用户在ZNS页面给Segment删除cidr, ZNS 调用 cloud API 删除Ip Range

用户在ZNS页面给Segment添加Transport Zone, ZNS 调用 cloud API: APIAttachL2NetworkToClusterMsg
用户在ZNS页面给Segment删除Transport Zone, ZNS 调用 cloud API: APIDetachL2NetworkFromClusterMsg

## 创建VmNic
用户在cloud侧创建虚拟机/applianceVm; 给虚拟机/applianceVm 添加网卡, Cloud调用 ZNS API 创建segment port, ZNS负责IP地址管理
ZNS 删除segment port之前，需要check cloud，还在使用port 不能删除
用户在cloud侧删除虚拟机/applianceVm; 给虚拟机/applianceVm 删除网卡,Cloud调用 ZNS API 删除segment port

## 同步

Cloud 定时同步ZNS, 


# L2Network

## 基础信息
L2NetworkVO 有重要字段：
- type: 它的值为L2NetworkType.types, 有：NoVlan, Vlan, VxlanPool, Vxlan, TfL2Network
- vSwitchType: 它的值为 VSwitchType.types, 有: Linux bridge, OVS-DPDK, TfL2Network, OvnDpdk,
- virtualNetworkId: vlanId or vxlanID
- physicalInterface: 物理网卡名称

在不同的feature开发过程中，前面本来有明确意义的字段已经有些混乱了。
ZNS L2Network type 有三个值：NoVlan, Vlan, Geneve. Geneve的数据类型和Vlan数据类型一样
ZNS L2Network vSwitchType: ZNS
ZNS L2Network physicalInterface: 为null
ZNS L2Network virtualNetworkId: Vlan Id or Geneve Id

ZNS L2 API不需要调用Sdn backend

## L2NetworkClusterRefVO

### APIAttachL2NetworkToClusterMsg, APIDetachL2NetworkFromClusterMsg
对vSwitchType = ZNS类型的, 仅仅保存数据库，不需要下发到物理机

### APIChangeL2NetworkVlanIdMsg 
- geneve类型不支持; vlan, novlan类型支持; 
- 也仅仅需要修改L2NetworkVO数据库，不需要下发到物理机, 需要调用修改ZNS segment API

AttachedL2NetworkAllocatorFlow 根据虚拟机选择的l2网络选择候选的物理机, 它根据L2NetworkClusterRefVO找到L2Network关联的cluster, 
从而找到cluster内的物理机器作为候选机器。

# L3Network

## 基础信息
L3NetworkVO 重要字段：
- type: L3BasicNetwork, L3VpcNetwork
- category： Public, Private, System

ZNS L3的Type是： L3VpcNetwork, category: Private
ZNS L3 API不需要调用Sdn backend

# VmNic

它的值为VmNicType.types, 有： VNIC, VF, dpdkvhostuserclient
ZNS可能是dpdk模式，也可能是kernel, 在UI选择ZNS网络以后，用户可以选择网卡类型：VNIC, dpdkvhostuserclient

## 虚拟机的物理机分配
创建虚拟机选择了ZNS网络, 默认网卡类型是VNIC, 需要选择到部署了ovs kernel的物理机; 如果选择了 dpdkvhostuserclient，
需要选择到部署了ovs kernel的物理机 dpdk的机器。
AttachedL2NetworkAllocatorFlow 会调用AttachedL2NetworkAllocatorExtensionPoint 扩展到进一步选择物理机，
这个扩展点根据l2找到ZNS控制器，根据 SdnControllerHostRefVO 找到物理机，如果网卡是VNIC，选择vSwitchType是:ZNS-Kernel的物理机;
如果网卡是dpdkvhostuserclient，选择vSwitchType是:ZNS-DPDK的物理机;

## 网卡创建过程:
VmAllocateNicFlow/ApplianceVmAllocateNicFlow 分别是创建虚拟机,applianceVm的过程创建网卡的过程。zns网路创建过程需要调整：
- 和现在逻辑一样分配网卡mac, internalId, internalName, driverType
- 调用zns创建segment port api, 获取ip/掩码/网关，ip6/前缀/网卡, 
- zns L3网络走 enableIpAddressAllocation()为false流程
- 根据获取的参数创建VmNicVO, UsedIpVO

## 网卡删除过程:
VmReturnReleaseNicFlow 
- 调用zns删除segment port api,
- 删除VmNicVO, UsedIpVO

