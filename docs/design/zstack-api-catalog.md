# ZStack API 清单

> 自动生成自 `ApiHelper.groovy` 及仓库源码扫描
> 生成日期：2026-03-02
> API 消息总数：1949  |  模块数：83  |  包数：230

---

## 目录

1. [core](#module-core) (16 APIs)
2. [header](#module-header) (367 APIs)
3. [plugin/account-import](#module-pluginaccount-import) (1 APIs)
4. [plugin/acl](#module-pluginacl) (7 APIs)
5. [plugin/applianceVm](#module-pluginappliancevm) (1 APIs)
6. [plugin/ceph](#module-pluginceph) (15 APIs)
7. [plugin/directory](#module-plugindirectory) (8 APIs)
8. [plugin/eip](#module-plugineip) (9 APIs)
9. [plugin/flatNetworkProvider](#module-pluginflatnetworkprovider) (3 APIs)
10. [plugin/hostNetworkInterface](#module-pluginhostnetworkinterface) (3 APIs)
11. [plugin/kvm](#module-pluginkvm) (5 APIs)
12. [plugin/ldap](#module-pluginldap) (9 APIs)
13. [plugin/loadBalancer](#module-pluginloadbalancer) (35 APIs)
14. [plugin/localstorage](#module-pluginlocalstorage) (5 APIs)
15. [plugin/nfsPrimaryStorage](#module-pluginnfsprimarystorage) (1 APIs)
16. [plugin/portForwarding](#module-pluginportforwarding) (8 APIs)
17. [plugin/sdnController](#module-pluginsdncontroller) (6 APIs)
18. [plugin/securityGroup](#module-pluginsecuritygroup) (21 APIs)
19. [plugin/sftpBackupStorage](#module-pluginsftpbackupstorage) (4 APIs)
20. [plugin/sharedMountPointPrimaryStorage](#module-pluginsharedmountpointprimarystorage) (1 APIs)
21. [plugin/sshKeyPair](#module-pluginsshkeypair) (7 APIs)
22. [plugin/sugonSdnController](#module-pluginsugonsdncontroller) (1 APIs)
23. [plugin/vip](#module-pluginvip) (7 APIs)
24. [plugin/virtualRouterProvider](#module-pluginvirtualrouterprovider) (10 APIs)
25. [plugin/vxlan](#module-pluginvxlan) (12 APIs)
26. [premium/accesskey](#module-premiumaccesskey) (4 APIs)
27. [premium/autoscaling](#module-premiumautoscaling) (30 APIs)
28. [premium/baremetal](#module-premiumbaremetal) (38 APIs)
29. [premium/baremetal2](#module-premiumbaremetal2) (52 APIs)
30. [premium/billing](#module-premiumbilling) (23 APIs)
31. [premium/cbt](#module-premiumcbt) (7 APIs)
32. [premium/cdp](#module-premiumcdp) (21 APIs)
33. [premium/cloudformation](#module-premiumcloudformation) (19 APIs)
34. [premium/crypto](#module-premiumcrypto) (32 APIs)
35. [premium/drs](#module-premiumdrs) (10 APIs)
36. [premium/externalbackup](#module-premiumexternalbackup) (4 APIs)
37. [premium/faulttolerance](#module-premiumfaulttolerance) (4 APIs)
38. [premium/guesttools](#module-premiumguesttools) (11 APIs)
39. [premium/hybrid](#module-premiumhybrid) (158 APIs)
40. [premium/iam1](#module-premiumiam1) (16 APIs)
41. [premium/iam2](#module-premiumiam2) (87 APIs)
42. [premium/imagereplicator](#module-premiumimagereplicator) (4 APIs)
43. [premium/log](#module-premiumlog) (4 APIs)
44. [premium/loginControl](#module-premiumlogincontrol) (7 APIs)
45. [premium/managements](#module-premiummanagements) (3 APIs)
46. [premium/mevoco](#module-premiummevoco) (309 APIs)
47. [premium/plugin-premium/aliyunproxy](#module-premiumplugin-premiumaliyunproxy) (8 APIs)
48. [premium/plugin-premium/aliyun-storage](#module-premiumplugin-premiumaliyun-storage) (26 APIs)
49. [premium/plugin-premium/block-primary-storage](#module-premiumplugin-premiumblock-primary-storage) (4 APIs)
50. [premium/plugin-premium/daho](#module-premiumplugin-premiumdaho) (14 APIs)
51. [premium/plugin-premium/flowMeter](#module-premiumplugin-premiumflowmeter) (15 APIs)
52. [premium/plugin-premium/ministorage](#module-premiumplugin-premiumministorage) (5 APIs)
53. [premium/plugin-premium/multicast-router](#module-premiumplugin-premiummulticast-router) (7 APIs)
54. [premium/plugin-premium/ovf](#module-premiumplugin-premiumovf) (6 APIs)
55. [premium/plugin-premium/policyRoute](#module-premiumplugin-premiumpolicyroute) (19 APIs)
56. [premium/plugin-premium/portMirror](#module-premiumplugin-premiumportmirror) (10 APIs)
57. [premium/plugin-premium/routeProtocol](#module-premiumplugin-premiumrouteprotocol) (11 APIs)
58. [premium/plugin-premium/sns-aliyun-sms](#module-premiumplugin-premiumsns-aliyun-sms) (2 APIs)
59. [premium/plugin-premium/software-package-plugin](#module-premiumplugin-premiumsoftware-package-plugin) (7 APIs)
60. [premium/plugin-premium/sso-plugin](#module-premiumplugin-premiumsso-plugin) (10 APIs)
61. [premium/plugin-premium/storage-device](#module-premiumplugin-premiumstorage-device) (30 APIs)
62. [premium/plugin-premium/ticket](#module-premiumplugin-premiumticket) (23 APIs)
63. [premium/plugin-premium/virtualSwitchNetwork](#module-premiumplugin-premiumvirtualswitchnetwork) (17 APIs)
64. [premium/plugin-premium/vpcFirewall](#module-premiumplugin-premiumvpcfirewall) (29 APIs)
65. [premium/plugin-premium/zboxbackup](#module-premiumplugin-premiumzboxbackup) (3 APIs)
66. [premium/plugin-premium/zce-x-plugin](#module-premiumplugin-premiumzce-x-plugin) (9 APIs)
67. [premium/plugin-premium/zops-plugin](#module-premiumplugin-premiumzops-plugin) (5 APIs)
68. [premium/plugin-premium/zstone-plugin](#module-premiumplugin-premiumzstone-plugin) (7 APIs)
69. [premium/plugin-premium/zsv](#module-premiumplugin-premiumzsv) (2 APIs)
70. [premium/sharedblock](#module-premiumsharedblock) (8 APIs)
71. [premium/slb](#module-premiumslb) (7 APIs)
72. [premium/snmp](#module-premiumsnmp) (5 APIs)
73. [premium/sns](#module-premiumsns) (70 APIs)
74. [premium/tag2](#module-premiumtag2) (5 APIs)
75. [premium/twoFactorAuthentication](#module-premiumtwofactorauthentication) (4 APIs)
76. [premium/volumebackup](#module-premiumvolumebackup) (27 APIs)
77. [premium/vpc](#module-premiumvpc) (38 APIs)
78. [premium/xdragon](#module-premiumxdragon) (1 APIs)
79. [premium/zbox](#module-premiumzbox) (4 APIs)
80. [premium/zwatch](#module-premiumzwatch) (93 APIs)
81. [resourceconfig](#module-resourceconfig) (7 APIs)
82. [search](#module-search) (3 APIs)
83. [simulator/simulatorHeader](#module-simulatorsimulatorheader) (3 APIs)

---

<a id="module-core"></a>

## 模块：`core`

### `org.zstack.core.captcha`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIRefreshCaptchaMsg` | core/src/main/java/org/zstack/core/captcha/APIRefreshCaptchaMsg.java |

### `org.zstack.core.config`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIGetGlobalConfigOptionsMsg` | core/src/main/java/org/zstack/core/config/APIGetGlobalConfigOptionsMsg.java |
| 2 | `APIQueryGlobalConfigMsg` | core/src/main/java/org/zstack/core/config/APIQueryGlobalConfigMsg.java |
| 3 | `APIResetGlobalConfigMsg` | core/src/main/java/org/zstack/core/config/APIResetGlobalConfigMsg.java |
| 4 | `APIUpdateGlobalConfigMsg` | core/src/main/java/org/zstack/core/config/APIUpdateGlobalConfigMsg.java |

### `org.zstack.core.debug`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APICleanQueueMsg` | core/src/main/java/org/zstack/core/debug/APICleanQueueMsg.java |
| 2 | `APIDebugSignalMsg` | core/src/main/java/org/zstack/core/debug/APIDebugSignalMsg.java |
| 3 | `APIGetDebugSignalMsg` | core/src/main/java/org/zstack/core/debug/APIGetDebugSignalMsg.java |

### `org.zstack.core.errorcode`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APICheckElaborationContentMsg` | core/src/main/java/org/zstack/core/errorcode/APICheckElaborationContentMsg.java |
| 2 | `APIGetElaborationCategoriesMsg` | core/src/main/java/org/zstack/core/errorcode/APIGetElaborationCategoriesMsg.java |
| 3 | `APIGetElaborationsMsg` | core/src/main/java/org/zstack/core/errorcode/APIGetElaborationsMsg.java |
| 4 | `APIReloadElaborationMsg` | core/src/main/java/org/zstack/core/errorcode/APIReloadElaborationMsg.java |

### `org.zstack.core.eventlog`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIQueryEventLogMsg` | core/src/main/java/org/zstack/core/eventlog/APIQueryEventLogMsg.java |

### `org.zstack.core.gc`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIDeleteGCJobMsg` | core/src/main/java/org/zstack/core/gc/APIDeleteGCJobMsg.java |
| 2 | `APIQueryGCJobMsg` | core/src/main/java/org/zstack/core/gc/APIQueryGCJobMsg.java |
| 3 | `APITriggerGCJobMsg` | core/src/main/java/org/zstack/core/gc/APITriggerGCJobMsg.java |

---

<a id="module-header"></a>

## 模块：`header`

### `org.zstack.header`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIIsOpensourceVersionMsg` | header/src/main/java/org/zstack/header/APIIsOpensourceVersionMsg.java |

### `org.zstack.header.allocator`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIGetCpuMemoryCapacityMsg` | header/src/main/java/org/zstack/header/allocator/APIGetCpuMemoryCapacityMsg.java |
| 2 | `APIGetHostAllocatorStrategiesMsg` | header/src/main/java/org/zstack/header/allocator/APIGetHostAllocatorStrategiesMsg.java |

### `org.zstack.header.apimediator`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIIsReadyToGoMsg` | header/src/main/java/org/zstack/header/apimediator/APIIsReadyToGoMsg.java |

### `org.zstack.header.cluster`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIChangeClusterStateMsg` | header/src/main/java/org/zstack/header/cluster/APIChangeClusterStateMsg.java |
| 2 | `APICreateClusterMsg` | header/src/main/java/org/zstack/header/cluster/APICreateClusterMsg.java |
| 3 | `APIDeleteClusterMsg` | header/src/main/java/org/zstack/header/cluster/APIDeleteClusterMsg.java |
| 4 | `APIQueryClusterMsg` | header/src/main/java/org/zstack/header/cluster/APIQueryClusterMsg.java |
| 5 | `APIUpdateClusterMsg` | header/src/main/java/org/zstack/header/cluster/APIUpdateClusterMsg.java |
| 6 | `APIUpdateClusterOSMsg` | header/src/main/java/org/zstack/header/cluster/APIUpdateClusterOSMsg.java |

### `org.zstack.header.configuration`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIChangeDiskOfferingStateMsg` | header/src/main/java/org/zstack/header/configuration/APIChangeDiskOfferingStateMsg.java |
| 2 | `APIChangeInstanceOfferingStateMsg` | header/src/main/java/org/zstack/header/configuration/APIChangeInstanceOfferingStateMsg.java |
| 3 | `APICreateDiskOfferingMsg` | header/src/main/java/org/zstack/header/configuration/APICreateDiskOfferingMsg.java |
| 4 | `APICreateInstanceOfferingMsg` | header/src/main/java/org/zstack/header/configuration/APICreateInstanceOfferingMsg.java |
| 5 | `APIDeleteDiskOfferingMsg` | header/src/main/java/org/zstack/header/configuration/APIDeleteDiskOfferingMsg.java |
| 6 | `APIDeleteInstanceOfferingMsg` | header/src/main/java/org/zstack/header/configuration/APIDeleteInstanceOfferingMsg.java |
| 7 | `APIGenerateApiJsonTemplateMsg` | header/src/main/java/org/zstack/header/configuration/APIGenerateApiJsonTemplateMsg.java |
| 8 | `APIGenerateApiTypeScriptDefinitionMsg` | header/src/main/java/org/zstack/header/configuration/APIGenerateApiTypeScriptDefinitionMsg.java |
| 9 | `APIGenerateGroovyClassMsg` | header/src/main/java/org/zstack/header/configuration/APIGenerateGroovyClassMsg.java |
| 10 | `APIGenerateSqlForeignKeyMsg` | header/src/main/java/org/zstack/header/configuration/APIGenerateSqlForeignKeyMsg.java |
| 11 | `APIGenerateSqlIndexMsg` | header/src/main/java/org/zstack/header/configuration/APIGenerateSqlIndexMsg.java |
| 12 | `APIGenerateSqlVOViewMsg` | header/src/main/java/org/zstack/header/configuration/APIGenerateSqlVOViewMsg.java |
| 13 | `APIGenerateTestLinkDocumentMsg` | header/src/main/java/org/zstack/header/configuration/APIGenerateTestLinkDocumentMsg.java |
| 14 | `APIGetGlobalPropertyMsg` | header/src/main/java/org/zstack/header/configuration/APIGetGlobalPropertyMsg.java |
| 15 | `APIQueryDiskOfferingMsg` | header/src/main/java/org/zstack/header/configuration/APIQueryDiskOfferingMsg.java |
| 16 | `APIQueryInstanceOfferingMsg` | header/src/main/java/org/zstack/header/configuration/APIQueryInstanceOfferingMsg.java |
| 17 | `APIUpdateDiskOfferingMsg` | header/src/main/java/org/zstack/header/configuration/APIUpdateDiskOfferingMsg.java |
| 18 | `APIUpdateInstanceOfferingMsg` | header/src/main/java/org/zstack/header/configuration/APIUpdateInstanceOfferingMsg.java |

### `org.zstack.header.console`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIQueryConsoleProxyAgentMsg` | header/src/main/java/org/zstack/header/console/APIQueryConsoleProxyAgentMsg.java |
| 2 | `APIReconnectConsoleProxyAgentMsg` | header/src/main/java/org/zstack/header/console/APIReconnectConsoleProxyAgentMsg.java |
| 3 | `APIRequestConsoleAccessMsg` | header/src/main/java/org/zstack/header/console/APIRequestConsoleAccessMsg.java |
| 4 | `APIUpdateConsoleProxyAgentMsg` | header/src/main/java/org/zstack/header/console/APIUpdateConsoleProxyAgentMsg.java |

### `org.zstack.header.core`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIGetChainTaskMsg` | header/src/main/java/org/zstack/header/core/APIGetChainTaskMsg.java |

### `org.zstack.header.core.encrypt`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIGetEncryptedFieldMsg` | header/src/main/java/org/zstack/header/core/encrypt/APIGetEncryptedFieldMsg.java |
| 2 | `APIUpdateEncryptKeyMsg` | header/src/main/java/org/zstack/header/core/encrypt/APIUpdateEncryptKeyMsg.java |

### `org.zstack.header.core.external.service`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIGetExternalServicesMsg` | header/src/main/java/org/zstack/header/core/external/service/APIGetExternalServicesMsg.java |
| 2 | `APIReloadExternalServiceMsg` | header/src/main/java/org/zstack/header/core/external/service/APIReloadExternalServiceMsg.java |

### `org.zstack.header.core.progress`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIGetTaskProgressMsg` | header/src/main/java/org/zstack/header/core/progress/APIGetTaskProgressMsg.java |

### `org.zstack.header.core.webhooks`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APICreateWebhookMsg` | header/src/main/java/org/zstack/header/core/webhooks/APICreateWebhookMsg.java |
| 2 | `APIDeleteWebhookMsg` | header/src/main/java/org/zstack/header/core/webhooks/APIDeleteWebhookMsg.java |
| 3 | `APIQueryWebhookMsg` | header/src/main/java/org/zstack/header/core/webhooks/APIQueryWebhookMsg.java |
| 4 | `APIUpdateWebhookMsg` | header/src/main/java/org/zstack/header/core/webhooks/APIUpdateWebhookMsg.java |

### `org.zstack.header.host`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddHostMsg` | header/src/main/java/org/zstack/header/host/APIAddHostMsg.java |
| 2 | `APIChangeHostStateMsg` | header/src/main/java/org/zstack/header/host/APIChangeHostStateMsg.java |
| 3 | `APIDeleteHostMsg` | header/src/main/java/org/zstack/header/host/APIDeleteHostMsg.java |
| 4 | `APIGetHostBlockDevicesMsg` | header/src/main/java/org/zstack/header/host/APIGetHostBlockDevicesMsg.java |
| 5 | `APIGetHostPowerStatusMsg` | header/src/main/java/org/zstack/header/host/APIGetHostPowerStatusMsg.java |
| 6 | `APIGetHostSensorsMsg` | header/src/main/java/org/zstack/header/host/APIGetHostSensorsMsg.java |
| 7 | `APIGetHostTaskMsg` | header/src/main/java/org/zstack/header/host/APIGetHostTaskMsg.java |
| 8 | `APIGetHostWebSshUrlMsg` | header/src/main/java/org/zstack/header/host/APIGetHostWebSshUrlMsg.java |
| 9 | `APIGetHypervisorTypesMsg` | header/src/main/java/org/zstack/header/host/APIGetHypervisorTypesMsg.java |
| 10 | `APIGetPhysicalMachineBlockDevicesMsg` | header/src/main/java/org/zstack/header/host/APIGetPhysicalMachineBlockDevicesMsg.java |
| 11 | `APIMountBlockDeviceMsg` | header/src/main/java/org/zstack/header/host/APIMountBlockDeviceMsg.java |
| 12 | `APIPowerOnHostMsg` | header/src/main/java/org/zstack/header/host/APIPowerOnHostMsg.java |
| 13 | `APIPowerResetHostMsg` | header/src/main/java/org/zstack/header/host/APIPowerResetHostMsg.java |
| 14 | `APIQueryHostMsg` | header/src/main/java/org/zstack/header/host/APIQueryHostMsg.java |
| 15 | `APIReconnectHostMsg` | header/src/main/java/org/zstack/header/host/APIReconnectHostMsg.java |
| 16 | `APIShutdownHostMsg` | header/src/main/java/org/zstack/header/host/APIShutdownHostMsg.java |
| 17 | `APIUpdateHostIpmiMsg` | header/src/main/java/org/zstack/header/host/APIUpdateHostIpmiMsg.java |
| 18 | `APIUpdateHostMsg` | header/src/main/java/org/zstack/header/host/APIUpdateHostMsg.java |
| 19 | `APIUpdateHostnameMsg` | header/src/main/java/org/zstack/header/host/APIUpdateHostnameMsg.java |
| 20 | `APIUpdateHostNqnMsg` | header/src/main/java/org/zstack/header/host/APIUpdateHostNqnMsg.java |

### `org.zstack.header.identity`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIChangeResourceOwnerMsg` | header/src/main/java/org/zstack/header/identity/APIChangeResourceOwnerMsg.java |
| 2 | `APICreateAccountMsg` | header/src/main/java/org/zstack/header/identity/APICreateAccountMsg.java |
| 3 | `APIDeleteAccountMsg` | header/src/main/java/org/zstack/header/identity/APIDeleteAccountMsg.java |
| 4 | `APIGetAccountQuotaUsageMsg` | header/src/main/java/org/zstack/header/identity/APIGetAccountQuotaUsageMsg.java |
| 5 | `APIGetResourceAccountMsg` | header/src/main/java/org/zstack/header/identity/APIGetResourceAccountMsg.java |
| 6 | `APILogInByAccountMsg` | header/src/main/java/org/zstack/header/identity/APILogInByAccountMsg.java |
| 7 | `APILogOutMsg` | header/src/main/java/org/zstack/header/identity/APILogOutMsg.java |
| 8 | `APIQueryAccountMsg` | header/src/main/java/org/zstack/header/identity/APIQueryAccountMsg.java |
| 9 | `APIQueryAccountResourceRefMsg` | header/src/main/java/org/zstack/header/identity/APIQueryAccountResourceRefMsg.java |
| 10 | `APIQueryQuotaMsg` | header/src/main/java/org/zstack/header/identity/APIQueryQuotaMsg.java |
| 11 | `APIRenewSessionMsg` | header/src/main/java/org/zstack/header/identity/APIRenewSessionMsg.java |
| 12 | `APIRevokeResourceSharingMsg` | header/src/main/java/org/zstack/header/identity/APIRevokeResourceSharingMsg.java |
| 13 | `APIShareResourceMsg` | header/src/main/java/org/zstack/header/identity/APIShareResourceMsg.java |
| 14 | `APIUpdateAccountMsg` | header/src/main/java/org/zstack/header/identity/APIUpdateAccountMsg.java |
| 15 | `APIUpdateQuotaMsg` | header/src/main/java/org/zstack/header/identity/APIUpdateQuotaMsg.java |
| 16 | `APIValidateSessionMsg` | header/src/main/java/org/zstack/header/identity/APIValidateSessionMsg.java |

### `org.zstack.header.identity.login`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIGetLoginProceduresMsg` | header/src/main/java/org/zstack/header/identity/login/APIGetLoginProceduresMsg.java |
| 2 | `APILogInMsg` | header/src/main/java/org/zstack/header/identity/login/APILogInMsg.java |

### `org.zstack.header.identity.role.api`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAttachRoleToAccountMsg` | header/src/main/java/org/zstack/header/identity/role/api/APIAttachRoleToAccountMsg.java |
| 2 | `APICreateRoleMsg` | header/src/main/java/org/zstack/header/identity/role/api/APICreateRoleMsg.java |
| 3 | `APIDeleteRoleMsg` | header/src/main/java/org/zstack/header/identity/role/api/APIDeleteRoleMsg.java |
| 4 | `APIDetachRoleFromAccountMsg` | header/src/main/java/org/zstack/header/identity/role/api/APIDetachRoleFromAccountMsg.java |
| 5 | `APIGetRolePolicyActionsMsg` | header/src/main/java/org/zstack/header/identity/role/api/APIGetRolePolicyActionsMsg.java |
| 6 | `APIQueryRoleAccountRefMsg` | header/src/main/java/org/zstack/header/identity/role/api/APIQueryRoleAccountRefMsg.java |
| 7 | `APIQueryRoleMsg` | header/src/main/java/org/zstack/header/identity/role/api/APIQueryRoleMsg.java |
| 8 | `APIUpdateRoleMsg` | header/src/main/java/org/zstack/header/identity/role/api/APIUpdateRoleMsg.java |

### `org.zstack.header.image`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddImageMsg` | header/src/main/java/org/zstack/header/image/APIAddImageMsg.java |
| 2 | `APICalculateImageHashMsg` | header/src/main/java/org/zstack/header/image/APICalculateImageHashMsg.java |
| 3 | `APIChangeImageStateMsg` | header/src/main/java/org/zstack/header/image/APIChangeImageStateMsg.java |
| 4 | `APICreateDataVolumeTemplateFromVolumeMsg` | header/src/main/java/org/zstack/header/image/APICreateDataVolumeTemplateFromVolumeMsg.java |
| 5 | `APICreateDataVolumeTemplateFromVolumeSnapshotMsg` | header/src/main/java/org/zstack/header/image/APICreateDataVolumeTemplateFromVolumeSnapshotMsg.java |
| 6 | `APICreateRootVolumeTemplateFromRootVolumeMsg` | header/src/main/java/org/zstack/header/image/APICreateRootVolumeTemplateFromRootVolumeMsg.java |
| 7 | `APICreateRootVolumeTemplateFromVolumeSnapshotMsg` | header/src/main/java/org/zstack/header/image/APICreateRootVolumeTemplateFromVolumeSnapshotMsg.java |
| 8 | `APIDeleteImageMsg` | header/src/main/java/org/zstack/header/image/APIDeleteImageMsg.java |
| 9 | `APIExpungeImageMsg` | header/src/main/java/org/zstack/header/image/APIExpungeImageMsg.java |
| 10 | `APIGetCandidateBackupStorageForCreatingImageMsg` | header/src/main/java/org/zstack/header/image/APIGetCandidateBackupStorageForCreatingImageMsg.java |
| 11 | `APIGetCandidateImagesForCreatingVmMsg` | header/src/main/java/org/zstack/header/image/APIGetCandidateImagesForCreatingVmMsg.java |
| 12 | `APIGetUploadImageJobDetailsMsg` | header/src/main/java/org/zstack/header/image/APIGetUploadImageJobDetailsMsg.java |
| 13 | `APIQueryImageMsg` | header/src/main/java/org/zstack/header/image/APIQueryImageMsg.java |
| 14 | `APIRecoverImageMsg` | header/src/main/java/org/zstack/header/image/APIRecoverImageMsg.java |
| 15 | `APISetImageBootModeMsg` | header/src/main/java/org/zstack/header/image/APISetImageBootModeMsg.java |
| 16 | `APISyncImageSizeMsg` | header/src/main/java/org/zstack/header/image/APISyncImageSizeMsg.java |
| 17 | `APIUpdateImageMsg` | header/src/main/java/org/zstack/header/image/APIUpdateImageMsg.java |

### `org.zstack.header.longjob`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APICancelLongJobMsg` | header/src/main/java/org/zstack/header/longjob/APICancelLongJobMsg.java |
| 2 | `APICleanLongJobMsg` | header/src/main/java/org/zstack/header/longjob/APICleanLongJobMsg.java |
| 3 | `APIDeleteLongJobMsg` | header/src/main/java/org/zstack/header/longjob/APIDeleteLongJobMsg.java |
| 4 | `APIQueryLongJobMsg` | header/src/main/java/org/zstack/header/longjob/APIQueryLongJobMsg.java |
| 5 | `APIRerunLongJobMsg` | header/src/main/java/org/zstack/header/longjob/APIRerunLongJobMsg.java |
| 6 | `APIResumeLongJobMsg` | header/src/main/java/org/zstack/header/longjob/APIResumeLongJobMsg.java |
| 7 | `APISubmitLongJobMsg` | header/src/main/java/org/zstack/header/longjob/APISubmitLongJobMsg.java |
| 8 | `APIUpdateLongJobMsg` | header/src/main/java/org/zstack/header/longjob/APIUpdateLongJobMsg.java |

### `org.zstack.header.managementnode`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIGetCurrentTimeMsg` | header/src/main/java/org/zstack/header/managementnode/APIGetCurrentTimeMsg.java |
| 2 | `APIGetManagementNodeArchMsg` | header/src/main/java/org/zstack/header/managementnode/APIGetManagementNodeArchMsg.java |
| 3 | `APIGetManagementNodeOSMsg` | header/src/main/java/org/zstack/header/managementnode/APIGetManagementNodeOSMsg.java |
| 4 | `APIGetPlatformTimeZoneMsg` | header/src/main/java/org/zstack/header/managementnode/APIGetPlatformTimeZoneMsg.java |
| 5 | `APIGetSupportAPIsMsg` | header/src/main/java/org/zstack/header/managementnode/APIGetSupportAPIsMsg.java |
| 6 | `APIGetVersionMsg` | header/src/main/java/org/zstack/header/managementnode/APIGetVersionMsg.java |
| 7 | `APIQueryManagementNodeMsg` | header/src/main/java/org/zstack/header/managementnode/APIQueryManagementNodeMsg.java |

### `org.zstack.header.network.l2`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAttachL2NetworkToClusterMsg` | header/src/main/java/org/zstack/header/network/l2/APIAttachL2NetworkToClusterMsg.java |
| 2 | `APIAttachL2NetworkToHostMsg` | header/src/main/java/org/zstack/header/network/l2/APIAttachL2NetworkToHostMsg.java |
| 3 | `APICreateL2NetworkMsg` | header/src/main/java/org/zstack/header/network/l2/APICreateL2NetworkMsg.java |
| 4 | `APICreateL2NoVlanNetworkMsg` | header/src/main/java/org/zstack/header/network/l2/APICreateL2NoVlanNetworkMsg.java |
| 5 | `APICreateL2VlanNetworkMsg` | header/src/main/java/org/zstack/header/network/l2/APICreateL2VlanNetworkMsg.java |
| 6 | `APIDeleteL2NetworkMsg` | header/src/main/java/org/zstack/header/network/l2/APIDeleteL2NetworkMsg.java |
| 7 | `APIDetachL2NetworkFromClusterMsg` | header/src/main/java/org/zstack/header/network/l2/APIDetachL2NetworkFromClusterMsg.java |
| 8 | `APIDetachL2NetworkFromHostMsg` | header/src/main/java/org/zstack/header/network/l2/APIDetachL2NetworkFromHostMsg.java |
| 9 | `APIGetCandidateClustersForAttachingL2NetworkMsg` | header/src/main/java/org/zstack/header/network/l2/APIGetCandidateClustersForAttachingL2NetworkMsg.java |
| 10 | `APIGetCandidateL2NetworksForAttachingClusterMsg` | header/src/main/java/org/zstack/header/network/l2/APIGetCandidateL2NetworksForAttachingClusterMsg.java |
| 11 | `APIGetL2NetworkTypesMsg` | header/src/main/java/org/zstack/header/network/l2/APIGetL2NetworkTypesMsg.java |
| 12 | `APIGetVSwitchTypesMsg` | header/src/main/java/org/zstack/header/network/l2/APIGetVSwitchTypesMsg.java |
| 13 | `APIQueryL2NetworkMsg` | header/src/main/java/org/zstack/header/network/l2/APIQueryL2NetworkMsg.java |
| 14 | `APIQueryL2VlanNetworkMsg` | header/src/main/java/org/zstack/header/network/l2/APIQueryL2VlanNetworkMsg.java |
| 15 | `APIUpdateL2NetworkMsg` | header/src/main/java/org/zstack/header/network/l2/APIUpdateL2NetworkMsg.java |
| 16 | `APIUpdateL2NetworkVirtualNetworkIdMsg` | header/src/main/java/org/zstack/header/network/l2/APIUpdateL2NetworkVirtualNetworkIdMsg.java |

### `org.zstack.header.network.l3`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddDnsToL3NetworkMsg` | header/src/main/java/org/zstack/header/network/l3/APIAddDnsToL3NetworkMsg.java |
| 2 | `APIAddHostRouteToL3NetworkMsg` | header/src/main/java/org/zstack/header/network/l3/APIAddHostRouteToL3NetworkMsg.java |
| 3 | `APIAddIpRangeByNetworkCidrMsg` | header/src/main/java/org/zstack/header/network/l3/APIAddIpRangeByNetworkCidrMsg.java |
| 4 | `APIAddIpRangeMsg` | header/src/main/java/org/zstack/header/network/l3/APIAddIpRangeMsg.java |
| 5 | `APIAddIpv6RangeByNetworkCidrMsg` | header/src/main/java/org/zstack/header/network/l3/APIAddIpv6RangeByNetworkCidrMsg.java |
| 6 | `APIAddIpv6RangeMsg` | header/src/main/java/org/zstack/header/network/l3/APIAddIpv6RangeMsg.java |
| 7 | `APIAddReservedIpRangeMsg` | header/src/main/java/org/zstack/header/network/l3/APIAddReservedIpRangeMsg.java |
| 8 | `APIChangeL3NetworkStateMsg` | header/src/main/java/org/zstack/header/network/l3/APIChangeL3NetworkStateMsg.java |
| 9 | `APICheckIpAvailabilityMsg` | header/src/main/java/org/zstack/header/network/l3/APICheckIpAvailabilityMsg.java |
| 10 | `APICreateL3NetworkMsg` | header/src/main/java/org/zstack/header/network/l3/APICreateL3NetworkMsg.java |
| 11 | `APIDeleteIpAddressMsg` | header/src/main/java/org/zstack/header/network/l3/APIDeleteIpAddressMsg.java |
| 12 | `APIDeleteIpRangeMsg` | header/src/main/java/org/zstack/header/network/l3/APIDeleteIpRangeMsg.java |
| 13 | `APIDeleteL3NetworkMsg` | header/src/main/java/org/zstack/header/network/l3/APIDeleteL3NetworkMsg.java |
| 14 | `APIDeleteReservedIpRangeMsg` | header/src/main/java/org/zstack/header/network/l3/APIDeleteReservedIpRangeMsg.java |
| 15 | `APIGetFreeIpMsg` | header/src/main/java/org/zstack/header/network/l3/APIGetFreeIpMsg.java |
| 16 | `APIGetIpAddressCapacityMsg` | header/src/main/java/org/zstack/header/network/l3/APIGetIpAddressCapacityMsg.java |
| 17 | `APIGetL3NetworkMtuMsg` | header/src/main/java/org/zstack/header/network/l3/APIGetL3NetworkMtuMsg.java |
| 18 | `APIGetL3NetworkRouterInterfaceIpMsg` | header/src/main/java/org/zstack/header/network/l3/APIGetL3NetworkRouterInterfaceIpMsg.java |
| 19 | `APIGetL3NetworkTypesMsg` | header/src/main/java/org/zstack/header/network/l3/APIGetL3NetworkTypesMsg.java |
| 20 | `APIQueryAddressPoolMsg` | header/src/main/java/org/zstack/header/network/l3/APIQueryAddressPoolMsg.java |
| 21 | `APIQueryIpAddressMsg` | header/src/main/java/org/zstack/header/network/l3/APIQueryIpAddressMsg.java |
| 22 | `APIQueryIpRangeMsg` | header/src/main/java/org/zstack/header/network/l3/APIQueryIpRangeMsg.java |
| 23 | `APIQueryL3NetworkMsg` | header/src/main/java/org/zstack/header/network/l3/APIQueryL3NetworkMsg.java |
| 24 | `APIRemoveDnsFromL3NetworkMsg` | header/src/main/java/org/zstack/header/network/l3/APIRemoveDnsFromL3NetworkMsg.java |
| 25 | `APIRemoveHostRouteFromL3NetworkMsg` | header/src/main/java/org/zstack/header/network/l3/APIRemoveHostRouteFromL3NetworkMsg.java |
| 26 | `APISetL3NetworkMtuMsg` | header/src/main/java/org/zstack/header/network/l3/APISetL3NetworkMtuMsg.java |
| 27 | `APISetL3NetworkRouterInterfaceIpMsg` | header/src/main/java/org/zstack/header/network/l3/APISetL3NetworkRouterInterfaceIpMsg.java |
| 28 | `APIUpdateIpRangeMsg` | header/src/main/java/org/zstack/header/network/l3/APIUpdateIpRangeMsg.java |
| 29 | `APIUpdateL3NetworkMsg` | header/src/main/java/org/zstack/header/network/l3/APIUpdateL3NetworkMsg.java |

### `org.zstack.header.network.service`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddNetworkServiceProviderMsg` | header/src/main/java/org/zstack/header/network/service/APIAddNetworkServiceProviderMsg.java |
| 2 | `APIAttachNetworkServiceProviderToL2NetworkMsg` | header/src/main/java/org/zstack/header/network/service/APIAttachNetworkServiceProviderToL2NetworkMsg.java |
| 3 | `APIAttachNetworkServiceToL3NetworkMsg` | header/src/main/java/org/zstack/header/network/service/APIAttachNetworkServiceToL3NetworkMsg.java |
| 4 | `APIDetachNetworkServiceFromL3NetworkMsg` | header/src/main/java/org/zstack/header/network/service/APIDetachNetworkServiceFromL3NetworkMsg.java |
| 5 | `APIDetachNetworkServiceProviderFromL2NetworkMsg` | header/src/main/java/org/zstack/header/network/service/APIDetachNetworkServiceProviderFromL2NetworkMsg.java |
| 6 | `APIGetNetworkServiceTypesMsg` | header/src/main/java/org/zstack/header/network/service/APIGetNetworkServiceTypesMsg.java |
| 7 | `APIQueryNetworkServiceL3NetworkRefMsg` | header/src/main/java/org/zstack/header/network/service/APIQueryNetworkServiceL3NetworkRefMsg.java |
| 8 | `APIQueryNetworkServiceProviderMsg` | header/src/main/java/org/zstack/header/network/service/APIQueryNetworkServiceProviderMsg.java |

### `org.zstack.header.query`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIGenerateInventoryQueryDetailsMsg` | header/src/main/java/org/zstack/header/query/APIGenerateInventoryQueryDetailsMsg.java |
| 2 | `APIGenerateQueryableFieldsMsg` | header/src/main/java/org/zstack/header/query/APIGenerateQueryableFieldsMsg.java |

### `org.zstack.header.resourceattribute.api`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APICreateResourceAttributeKeyMsg` | header/src/main/java/org/zstack/header/resourceattribute/api/APICreateResourceAttributeKeyMsg.java |
| 2 | `APICreateResourceAttributeValueMsg` | header/src/main/java/org/zstack/header/resourceattribute/api/APICreateResourceAttributeValueMsg.java |
| 3 | `APIDeleteResourceAttributeKeyMsg` | header/src/main/java/org/zstack/header/resourceattribute/api/APIDeleteResourceAttributeKeyMsg.java |
| 4 | `APIDeleteResourceAttributeValueMsg` | header/src/main/java/org/zstack/header/resourceattribute/api/APIDeleteResourceAttributeValueMsg.java |
| 5 | `APIQueryResourceAttributeKeyMsg` | header/src/main/java/org/zstack/header/resourceattribute/api/APIQueryResourceAttributeKeyMsg.java |
| 6 | `APIQueryResourceAttributeValueMsg` | header/src/main/java/org/zstack/header/resourceattribute/api/APIQueryResourceAttributeValueMsg.java |
| 7 | `APIUpdateResourceAttributeKeyMsg` | header/src/main/java/org/zstack/header/resourceattribute/api/APIUpdateResourceAttributeKeyMsg.java |

### `org.zstack.header.search`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APICreateSearchIndexMsg` | header/src/main/java/org/zstack/header/search/APICreateSearchIndexMsg.java |
| 2 | `APIDeleteSearchIndexMsg` | header/src/main/java/org/zstack/header/search/APIDeleteSearchIndexMsg.java |

### `org.zstack.header.storage.addon.backup`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddExternalBackupStorageMsg` | header/src/main/java/org/zstack/header/storage/addon/backup/APIAddExternalBackupStorageMsg.java |

### `org.zstack.header.storage.addon.primary`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddExternalPrimaryStorageMsg` | header/src/main/java/org/zstack/header/storage/addon/primary/APIAddExternalPrimaryStorageMsg.java |
| 2 | `APIDiscoverExternalPrimaryStorageMsg` | header/src/main/java/org/zstack/header/storage/addon/primary/APIDiscoverExternalPrimaryStorageMsg.java |
| 3 | `APIUpdateExternalPrimaryStorageMsg` | header/src/main/java/org/zstack/header/storage/addon/primary/APIUpdateExternalPrimaryStorageMsg.java |

### `org.zstack.header.storage.backup`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddBackupStorageMsg` | header/src/main/java/org/zstack/header/storage/backup/APIAddBackupStorageMsg.java |
| 2 | `APIAttachBackupStorageToZoneMsg` | header/src/main/java/org/zstack/header/storage/backup/APIAttachBackupStorageToZoneMsg.java |
| 3 | `APIChangeBackupStorageStateMsg` | header/src/main/java/org/zstack/header/storage/backup/APIChangeBackupStorageStateMsg.java |
| 4 | `APICleanUpTrashOnBackupStorageMsg` | header/src/main/java/org/zstack/header/storage/backup/APICleanUpTrashOnBackupStorageMsg.java |
| 5 | `APIDeleteBackupStorageMsg` | header/src/main/java/org/zstack/header/storage/backup/APIDeleteBackupStorageMsg.java |
| 6 | `APIDeleteExportedImageFromBackupStorageMsg` | header/src/main/java/org/zstack/header/storage/backup/APIDeleteExportedImageFromBackupStorageMsg.java |
| 7 | `APIDetachBackupStorageFromZoneMsg` | header/src/main/java/org/zstack/header/storage/backup/APIDetachBackupStorageFromZoneMsg.java |
| 8 | `APIExportImageFromBackupStorageMsg` | header/src/main/java/org/zstack/header/storage/backup/APIExportImageFromBackupStorageMsg.java |
| 9 | `APIGetBackupStorageCapacityMsg` | header/src/main/java/org/zstack/header/storage/backup/APIGetBackupStorageCapacityMsg.java |
| 10 | `APIGetBackupStorageTypesMsg` | header/src/main/java/org/zstack/header/storage/backup/APIGetBackupStorageTypesMsg.java |
| 11 | `APIGetTrashOnBackupStorageMsg` | header/src/main/java/org/zstack/header/storage/backup/APIGetTrashOnBackupStorageMsg.java |
| 12 | `APIQueryBackupStorageMsg` | header/src/main/java/org/zstack/header/storage/backup/APIQueryBackupStorageMsg.java |
| 13 | `APIReconnectBackupStorageMsg` | header/src/main/java/org/zstack/header/storage/backup/APIReconnectBackupStorageMsg.java |
| 14 | `APIScanBackupStorageMsg` | header/src/main/java/org/zstack/header/storage/backup/APIScanBackupStorageMsg.java |
| 15 | `APIUpdateBackupStorageMsg` | header/src/main/java/org/zstack/header/storage/backup/APIUpdateBackupStorageMsg.java |

### `org.zstack.header.storage.primary`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddPrimaryStorageMsg` | header/src/main/java/org/zstack/header/storage/primary/APIAddPrimaryStorageMsg.java |
| 2 | `APIAddStorageProtocolMsg` | header/src/main/java/org/zstack/header/storage/primary/APIAddStorageProtocolMsg.java |
| 3 | `APIAttachPrimaryStorageToClusterMsg` | header/src/main/java/org/zstack/header/storage/primary/APIAttachPrimaryStorageToClusterMsg.java |
| 4 | `APIChangePrimaryStorageStateMsg` | header/src/main/java/org/zstack/header/storage/primary/APIChangePrimaryStorageStateMsg.java |
| 5 | `APICleanUpImageCacheOnPrimaryStorageMsg` | header/src/main/java/org/zstack/header/storage/primary/APICleanUpImageCacheOnPrimaryStorageMsg.java |
| 6 | `APICleanUpStorageTrashOnPrimaryStorageMsg` | header/src/main/java/org/zstack/header/storage/primary/APICleanUpStorageTrashOnPrimaryStorageMsg.java |
| 7 | `APICleanUpTrashOnPrimaryStorageMsg` | header/src/main/java/org/zstack/header/storage/primary/APICleanUpTrashOnPrimaryStorageMsg.java |
| 8 | `APIDeletePrimaryStorageMsg` | header/src/main/java/org/zstack/header/storage/primary/APIDeletePrimaryStorageMsg.java |
| 9 | `APIDetachPrimaryStorageFromClusterMsg` | header/src/main/java/org/zstack/header/storage/primary/APIDetachPrimaryStorageFromClusterMsg.java |
| 10 | `APIGetPrimaryStorageAllocatorStrategiesMsg` | header/src/main/java/org/zstack/header/storage/primary/APIGetPrimaryStorageAllocatorStrategiesMsg.java |
| 11 | `APIGetPrimaryStorageCapacityMsg` | header/src/main/java/org/zstack/header/storage/primary/APIGetPrimaryStorageCapacityMsg.java |
| 12 | `APIGetPrimaryStorageLicenseInfoMsg` | header/src/main/java/org/zstack/header/storage/primary/APIGetPrimaryStorageLicenseInfoMsg.java |
| 13 | `APIGetPrimaryStorageTypesMsg` | header/src/main/java/org/zstack/header/storage/primary/APIGetPrimaryStorageTypesMsg.java |
| 14 | `APIGetPrimaryStorageUsageReportMsg` | header/src/main/java/org/zstack/header/storage/primary/APIGetPrimaryStorageUsageReportMsg.java |
| 15 | `APIGetTrashOnPrimaryStorageMsg` | header/src/main/java/org/zstack/header/storage/primary/APIGetTrashOnPrimaryStorageMsg.java |
| 16 | `APIGetVmInstanceMetadataFromPrimaryStorageMsg` | header/src/main/java/org/zstack/header/storage/primary/APIGetVmInstanceMetadataFromPrimaryStorageMsg.java |
| 17 | `APIQueryImageCacheMsg` | header/src/main/java/org/zstack/header/storage/primary/APIQueryImageCacheMsg.java |
| 18 | `APIQueryPrimaryStorageMsg` | header/src/main/java/org/zstack/header/storage/primary/APIQueryPrimaryStorageMsg.java |
| 19 | `APIReconnectPrimaryStorageMsg` | header/src/main/java/org/zstack/header/storage/primary/APIReconnectPrimaryStorageMsg.java |
| 20 | `APIRegisterVmInstanceMsg` | header/src/main/java/org/zstack/header/storage/primary/APIRegisterVmInstanceMsg.java |
| 21 | `APISyncPrimaryStorageCapacityMsg` | header/src/main/java/org/zstack/header/storage/primary/APISyncPrimaryStorageCapacityMsg.java |
| 22 | `APIUpdatePrimaryStorageMsg` | header/src/main/java/org/zstack/header/storage/primary/APIUpdatePrimaryStorageMsg.java |

### `org.zstack.header.storage.snapshot`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIBackupVolumeSnapshotMsg` | header/src/main/java/org/zstack/header/storage/snapshot/APIBackupVolumeSnapshotMsg.java |
| 2 | `APIBatchDeleteVolumeSnapshotMsg` | header/src/main/java/org/zstack/header/storage/snapshot/APIBatchDeleteVolumeSnapshotMsg.java |
| 3 | `APIDeleteVolumeSnapshotFromBackupStorageMsg` | header/src/main/java/org/zstack/header/storage/snapshot/APIDeleteVolumeSnapshotFromBackupStorageMsg.java |
| 4 | `APIDeleteVolumeSnapshotMsg` | header/src/main/java/org/zstack/header/storage/snapshot/APIDeleteVolumeSnapshotMsg.java |
| 5 | `APIGetVolumeSnapshotSizeMsg` | header/src/main/java/org/zstack/header/storage/snapshot/APIGetVolumeSnapshotSizeMsg.java |
| 6 | `APIQueryVolumeSnapshotMsg` | header/src/main/java/org/zstack/header/storage/snapshot/APIQueryVolumeSnapshotMsg.java |
| 7 | `APIQueryVolumeSnapshotTreeMsg` | header/src/main/java/org/zstack/header/storage/snapshot/APIQueryVolumeSnapshotTreeMsg.java |
| 8 | `APIRevertVolumeFromSnapshotMsg` | header/src/main/java/org/zstack/header/storage/snapshot/APIRevertVolumeFromSnapshotMsg.java |
| 9 | `APIShrinkVolumeSnapshotMsg` | header/src/main/java/org/zstack/header/storage/snapshot/APIShrinkVolumeSnapshotMsg.java |
| 10 | `APIUpdateVolumeSnapshotMsg` | header/src/main/java/org/zstack/header/storage/snapshot/APIUpdateVolumeSnapshotMsg.java |

### `org.zstack.header.storage.snapshot.group`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APICheckMemorySnapshotGroupConflictMsg` | header/src/main/java/org/zstack/header/storage/snapshot/group/APICheckMemorySnapshotGroupConflictMsg.java |
| 2 | `APICheckVolumeSnapshotGroupAvailabilityMsg` | header/src/main/java/org/zstack/header/storage/snapshot/group/APICheckVolumeSnapshotGroupAvailabilityMsg.java |
| 3 | `APIDeleteVolumeSnapshotGroupMsg` | header/src/main/java/org/zstack/header/storage/snapshot/group/APIDeleteVolumeSnapshotGroupMsg.java |
| 4 | `APIQueryVolumeSnapshotGroupMsg` | header/src/main/java/org/zstack/header/storage/snapshot/group/APIQueryVolumeSnapshotGroupMsg.java |
| 5 | `APIRevertVmFromSnapshotGroupMsg` | header/src/main/java/org/zstack/header/storage/snapshot/group/APIRevertVmFromSnapshotGroupMsg.java |
| 6 | `APIUngroupVolumeSnapshotGroupMsg` | header/src/main/java/org/zstack/header/storage/snapshot/group/APIUngroupVolumeSnapshotGroupMsg.java |
| 7 | `APIUpdateVolumeSnapshotGroupMsg` | header/src/main/java/org/zstack/header/storage/snapshot/group/APIUpdateVolumeSnapshotGroupMsg.java |

### `org.zstack.header.tag`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAbstractCreateTagMsg` | header/src/main/java/org/zstack/header/tag/APIAbstractCreateTagMsg.java |
| 2 | `APICreateSystemTagMsg` | header/src/main/java/org/zstack/header/tag/APICreateSystemTagMsg.java |
| 3 | `APICreateSystemTagsMsg` | header/src/main/java/org/zstack/header/tag/APICreateSystemTagsMsg.java |
| 4 | `APIDeleteTagMsg` | header/src/main/java/org/zstack/header/tag/APIDeleteTagMsg.java |
| 5 | `APIQuerySystemTagMsg` | header/src/main/java/org/zstack/header/tag/APIQuerySystemTagMsg.java |
| 6 | `APIQueryUserTagMsg` | header/src/main/java/org/zstack/header/tag/APIQueryUserTagMsg.java |
| 7 | `APIUpdateSystemTagMsg` | header/src/main/java/org/zstack/header/tag/APIUpdateSystemTagMsg.java |

### `org.zstack.header.vm`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAttachIsoToVmInstanceMsg` | header/src/main/java/org/zstack/header/vm/APIAttachIsoToVmInstanceMsg.java |
| 2 | `APIAttachL3NetworkToVmMsg` | header/src/main/java/org/zstack/header/vm/APIAttachL3NetworkToVmMsg.java |
| 3 | `APIAttachL3NetworkToVmNicMsg` | header/src/main/java/org/zstack/header/vm/APIAttachL3NetworkToVmNicMsg.java |
| 4 | `APIAttachVmNicToVmMsg` | header/src/main/java/org/zstack/header/vm/APIAttachVmNicToVmMsg.java |
| 5 | `APIChangeInstanceOfferingMsg` | header/src/main/java/org/zstack/header/vm/APIChangeInstanceOfferingMsg.java |
| 6 | `APIChangeVmNicNetworkMsg` | header/src/main/java/org/zstack/header/vm/APIChangeVmNicNetworkMsg.java |
| 7 | `APIChangeVmNicStateMsg` | header/src/main/java/org/zstack/header/vm/APIChangeVmNicStateMsg.java |
| 8 | `APIConvertTemplatedVmInstanceToVmInstanceMsg` | header/src/main/java/org/zstack/header/vm/APIConvertTemplatedVmInstanceToVmInstanceMsg.java |
| 9 | `APIConvertVmInstanceToTemplatedVmInstanceMsg` | header/src/main/java/org/zstack/header/vm/APIConvertVmInstanceToTemplatedVmInstanceMsg.java |
| 10 | `APICreateVmInstanceFromVolumeMsg` | header/src/main/java/org/zstack/header/vm/APICreateVmInstanceFromVolumeMsg.java |
| 11 | `APICreateVmInstanceFromVolumeSnapshotGroupMsg` | header/src/main/java/org/zstack/header/vm/APICreateVmInstanceFromVolumeSnapshotGroupMsg.java |
| 12 | `APICreateVmInstanceFromVolumeSnapshotMsg` | header/src/main/java/org/zstack/header/vm/APICreateVmInstanceFromVolumeSnapshotMsg.java |
| 13 | `APICreateVmInstanceMsg` | header/src/main/java/org/zstack/header/vm/APICreateVmInstanceMsg.java |
| 14 | `APICreateVmNicMsg` | header/src/main/java/org/zstack/header/vm/APICreateVmNicMsg.java |
| 15 | `APIDeleteTemplatedVmInstanceMsg` | header/src/main/java/org/zstack/header/vm/APIDeleteTemplatedVmInstanceMsg.java |
| 16 | `APIDeleteVmBootModeMsg` | header/src/main/java/org/zstack/header/vm/APIDeleteVmBootModeMsg.java |
| 17 | `APIDeleteVmConsolePasswordMsg` | header/src/main/java/org/zstack/header/vm/APIDeleteVmConsolePasswordMsg.java |
| 18 | `APIDeleteVmHostnameMsg` | header/src/main/java/org/zstack/header/vm/APIDeleteVmHostnameMsg.java |
| 19 | `APIDeleteVmNicMsg` | header/src/main/java/org/zstack/header/vm/APIDeleteVmNicMsg.java |
| 20 | `APIDeleteVmSshKeyMsg` | header/src/main/java/org/zstack/header/vm/APIDeleteVmSshKeyMsg.java |
| 21 | `APIDeleteVmStaticIpMsg` | header/src/main/java/org/zstack/header/vm/APIDeleteVmStaticIpMsg.java |
| 22 | `APIDestroyVmInstanceMsg` | header/src/main/java/org/zstack/header/vm/APIDestroyVmInstanceMsg.java |
| 23 | `APIDetachIsoFromVmInstanceMsg` | header/src/main/java/org/zstack/header/vm/APIDetachIsoFromVmInstanceMsg.java |
| 24 | `APIDetachL3NetworkFromVmMsg` | header/src/main/java/org/zstack/header/vm/APIDetachL3NetworkFromVmMsg.java |
| 25 | `APIExpungeVmInstanceMsg` | header/src/main/java/org/zstack/header/vm/APIExpungeVmInstanceMsg.java |
| 26 | `APIFlattenVmInstanceMsg` | header/src/main/java/org/zstack/header/vm/APIFlattenVmInstanceMsg.java |
| 27 | `APIFstrimVmMsg` | header/src/main/java/org/zstack/header/vm/APIFstrimVmMsg.java |
| 28 | `APIGetCandidateIsoForAttachingVmMsg` | header/src/main/java/org/zstack/header/vm/APIGetCandidateIsoForAttachingVmMsg.java |
| 29 | `APIGetCandidateL3NetworksForChangeVmNicNetworkMsg` | header/src/main/java/org/zstack/header/vm/APIGetCandidateL3NetworksForChangeVmNicNetworkMsg.java |
| 30 | `APIGetCandidatePrimaryStoragesForCreatingVmMsg` | header/src/main/java/org/zstack/header/vm/APIGetCandidatePrimaryStoragesForCreatingVmMsg.java |
| 31 | `APIGetCandidateVmForAttachingIsoMsg` | header/src/main/java/org/zstack/header/vm/APIGetCandidateVmForAttachingIsoMsg.java |
| 32 | `APIGetCandidateZonesClustersHostsForCreatingVmMsg` | header/src/main/java/org/zstack/header/vm/APIGetCandidateZonesClustersHostsForCreatingVmMsg.java |
| 33 | `APIGetInterdependentL3NetworksBackupStoragesMsg` | header/src/main/java/org/zstack/header/vm/APIGetInterdependentL3NetworksBackupStoragesMsg.java |
| 34 | `APIGetInterdependentL3NetworksImagesMsg` | header/src/main/java/org/zstack/header/vm/APIGetInterdependentL3NetworksImagesMsg.java |
| 35 | `APIGetMemorySnapshotGroupReferenceMsg` | header/src/main/java/org/zstack/header/vm/APIGetMemorySnapshotGroupReferenceMsg.java |
| 36 | `APIGetSpiceCertificatesMsg` | header/src/main/java/org/zstack/header/vm/APIGetSpiceCertificatesMsg.java |
| 37 | `APIGetVmAttachableDataVolumeMsg` | header/src/main/java/org/zstack/header/vm/APIGetVmAttachableDataVolumeMsg.java |
| 38 | `APIGetVmAttachableL3NetworkMsg` | header/src/main/java/org/zstack/header/vm/APIGetVmAttachableL3NetworkMsg.java |
| 39 | `APIGetVmBootOrderMsg` | header/src/main/java/org/zstack/header/vm/APIGetVmBootOrderMsg.java |
| 40 | `APIGetVmCapabilitiesMsg` | header/src/main/java/org/zstack/header/vm/APIGetVmCapabilitiesMsg.java |
| 41 | `APIGetVmConsoleAddressMsg` | header/src/main/java/org/zstack/header/vm/APIGetVmConsoleAddressMsg.java |
| 42 | `APIGetVmConsolePasswordMsg` | header/src/main/java/org/zstack/header/vm/APIGetVmConsolePasswordMsg.java |
| 43 | `APIGetVmDeviceAddressMsg` | header/src/main/java/org/zstack/header/vm/APIGetVmDeviceAddressMsg.java |
| 44 | `APIGetVmDnsMsg` | header/src/main/java/org/zstack/header/vm/APIGetVmDnsMsg.java |
| 45 | `APIGetVmHostnameMsg` | header/src/main/java/org/zstack/header/vm/APIGetVmHostnameMsg.java |
| 46 | `APIGetVmMigrationCandidateHostsMsg` | header/src/main/java/org/zstack/header/vm/APIGetVmMigrationCandidateHostsMsg.java |
| 47 | `APIGetVmNicAttachedNetworkServiceMsg` | header/src/main/java/org/zstack/header/vm/APIGetVmNicAttachedNetworkServiceMsg.java |
| 48 | `APIGetVmsCapabilitiesMsg` | header/src/main/java/org/zstack/header/vm/APIGetVmsCapabilitiesMsg.java |
| 49 | `APIGetVmSshKeyMsg` | header/src/main/java/org/zstack/header/vm/APIGetVmSshKeyMsg.java |
| 50 | `APIGetVmStartingCandidateClustersHostsMsg` | header/src/main/java/org/zstack/header/vm/APIGetVmStartingCandidateClustersHostsMsg.java |
| 51 | `APIGetVmTaskMsg` | header/src/main/java/org/zstack/header/vm/APIGetVmTaskMsg.java |
| 52 | `APIGetVmUptimeMsg` | header/src/main/java/org/zstack/header/vm/APIGetVmUptimeMsg.java |
| 53 | `APIMigrateVmMsg` | header/src/main/java/org/zstack/header/vm/APIMigrateVmMsg.java |
| 54 | `APIPauseVmInstanceMsg` | header/src/main/java/org/zstack/header/vm/APIPauseVmInstanceMsg.java |
| 55 | `APIQueryTemplatedVmInstanceMsg` | header/src/main/java/org/zstack/header/vm/APIQueryTemplatedVmInstanceMsg.java |
| 56 | `APIQueryVmInstanceMsg` | header/src/main/java/org/zstack/header/vm/APIQueryVmInstanceMsg.java |
| 57 | `APIQueryVmNicMsg` | header/src/main/java/org/zstack/header/vm/APIQueryVmNicMsg.java |
| 58 | `APIQueryVmPriorityConfigMsg` | header/src/main/java/org/zstack/header/vm/APIQueryVmPriorityConfigMsg.java |
| 59 | `APIRebootVmInstanceMsg` | header/src/main/java/org/zstack/header/vm/APIRebootVmInstanceMsg.java |
| 60 | `APIRecoverVmInstanceMsg` | header/src/main/java/org/zstack/header/vm/APIRecoverVmInstanceMsg.java |
| 61 | `APIReimageVmInstanceMsg` | header/src/main/java/org/zstack/header/vm/APIReimageVmInstanceMsg.java |
| 62 | `APIResumeVmInstanceMsg` | header/src/main/java/org/zstack/header/vm/APIResumeVmInstanceMsg.java |
| 63 | `APISetVmBootModeMsg` | header/src/main/java/org/zstack/header/vm/APISetVmBootModeMsg.java |
| 64 | `APISetVmBootOrderMsg` | header/src/main/java/org/zstack/header/vm/APISetVmBootOrderMsg.java |
| 65 | `APISetVmBootVolumeMsg` | header/src/main/java/org/zstack/header/vm/APISetVmBootVolumeMsg.java |
| 66 | `APISetVmClockTrackMsg` | header/src/main/java/org/zstack/header/vm/APISetVmClockTrackMsg.java |
| 67 | `APISetVmConsolePasswordMsg` | header/src/main/java/org/zstack/header/vm/APISetVmConsolePasswordMsg.java |
| 68 | `APISetVmDnsMsg` | header/src/main/java/org/zstack/header/vm/APISetVmDnsMsg.java |
| 69 | `APISetVmHostnameMsg` | header/src/main/java/org/zstack/header/vm/APISetVmHostnameMsg.java |
| 70 | `APISetVmQxlMemoryMsg` | header/src/main/java/org/zstack/header/vm/APISetVmQxlMemoryMsg.java |
| 71 | `APISetVmSoundTypeMsg` | header/src/main/java/org/zstack/header/vm/APISetVmSoundTypeMsg.java |
| 72 | `APISetVmSshKeyMsg` | header/src/main/java/org/zstack/header/vm/APISetVmSshKeyMsg.java |
| 73 | `APISetVmStaticIpMsg` | header/src/main/java/org/zstack/header/vm/APISetVmStaticIpMsg.java |
| 74 | `APIStartVmInstanceMsg` | header/src/main/java/org/zstack/header/vm/APIStartVmInstanceMsg.java |
| 75 | `APIStopVmInstanceMsg` | header/src/main/java/org/zstack/header/vm/APIStopVmInstanceMsg.java |
| 76 | `APITakeVmConsoleScreenshotMsg` | header/src/main/java/org/zstack/header/vm/APITakeVmConsoleScreenshotMsg.java |
| 77 | `APIUpdatePriorityConfigMsg` | header/src/main/java/org/zstack/header/vm/APIUpdatePriorityConfigMsg.java |
| 78 | `APIUpdateTemplatedVmInstanceMsg` | header/src/main/java/org/zstack/header/vm/APIUpdateTemplatedVmInstanceMsg.java |
| 79 | `APIUpdateVmInstanceMsg` | header/src/main/java/org/zstack/header/vm/APIUpdateVmInstanceMsg.java |
| 80 | `APIUpdateVmNicDriverMsg` | header/src/main/java/org/zstack/header/vm/APIUpdateVmNicDriverMsg.java |
| 81 | `APIUpdateVmPriorityMsg` | header/src/main/java/org/zstack/header/vm/APIUpdateVmPriorityMsg.java |

### `org.zstack.header.vm.cdrom`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APICreateVmCdRomMsg` | header/src/main/java/org/zstack/header/vm/cdrom/APICreateVmCdRomMsg.java |
| 2 | `APIDeleteVmCdRomMsg` | header/src/main/java/org/zstack/header/vm/cdrom/APIDeleteVmCdRomMsg.java |
| 3 | `APIQueryVmCdRomMsg` | header/src/main/java/org/zstack/header/vm/cdrom/APIQueryVmCdRomMsg.java |
| 4 | `APISetVmInstanceDefaultCdRomMsg` | header/src/main/java/org/zstack/header/vm/cdrom/APISetVmInstanceDefaultCdRomMsg.java |
| 5 | `APIUpdateVmCdRomMsg` | header/src/main/java/org/zstack/header/vm/cdrom/APIUpdateVmCdRomMsg.java |

### `org.zstack.header.vm.devices`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIQueryVmInstanceResourceMetadataArchiveMsg` | header/src/main/java/org/zstack/header/vm/devices/APIQueryVmInstanceResourceMetadataArchiveMsg.java |
| 2 | `APIQueryVmInstanceResourceMetadataGroupMsg` | header/src/main/java/org/zstack/header/vm/devices/APIQueryVmInstanceResourceMetadataGroupMsg.java |

### `org.zstack.header.vo`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIGetResourceNamesMsg` | header/src/main/java/org/zstack/header/vo/APIGetResourceNamesMsg.java |

### `org.zstack.header.volume`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAttachDataVolumeToHostMsg` | header/src/main/java/org/zstack/header/volume/APIAttachDataVolumeToHostMsg.java |
| 2 | `APIAttachDataVolumeToVmMsg` | header/src/main/java/org/zstack/header/volume/APIAttachDataVolumeToVmMsg.java |
| 3 | `APIBackupDataVolumeMsg` | header/src/main/java/org/zstack/header/volume/APIBackupDataVolumeMsg.java |
| 4 | `APIBatchSyncVolumeSizeMsg` | header/src/main/java/org/zstack/header/volume/APIBatchSyncVolumeSizeMsg.java |
| 5 | `APIChangeVolumeStateMsg` | header/src/main/java/org/zstack/header/volume/APIChangeVolumeStateMsg.java |
| 6 | `APICreateDataVolumeFromVolumeSnapshotMsg` | header/src/main/java/org/zstack/header/volume/APICreateDataVolumeFromVolumeSnapshotMsg.java |
| 7 | `APICreateDataVolumeFromVolumeTemplateMsg` | header/src/main/java/org/zstack/header/volume/APICreateDataVolumeFromVolumeTemplateMsg.java |
| 8 | `APICreateDataVolumeMsg` | header/src/main/java/org/zstack/header/volume/APICreateDataVolumeMsg.java |
| 9 | `APICreateVolumeSnapshotGroupMsg` | header/src/main/java/org/zstack/header/volume/APICreateVolumeSnapshotGroupMsg.java |
| 10 | `APICreateVolumeSnapshotMsg` | header/src/main/java/org/zstack/header/volume/APICreateVolumeSnapshotMsg.java |
| 11 | `APIDeleteDataVolumeMsg` | header/src/main/java/org/zstack/header/volume/APIDeleteDataVolumeMsg.java |
| 12 | `APIDetachDataVolumeFromHostMsg` | header/src/main/java/org/zstack/header/volume/APIDetachDataVolumeFromHostMsg.java |
| 13 | `APIDetachDataVolumeFromVmMsg` | header/src/main/java/org/zstack/header/volume/APIDetachDataVolumeFromVmMsg.java |
| 14 | `APIExpungeDataVolumeMsg` | header/src/main/java/org/zstack/header/volume/APIExpungeDataVolumeMsg.java |
| 15 | `APIFlattenVolumeMsg` | header/src/main/java/org/zstack/header/volume/APIFlattenVolumeMsg.java |
| 16 | `APIGetDataVolumeAttachableVmMsg` | header/src/main/java/org/zstack/header/volume/APIGetDataVolumeAttachableVmMsg.java |
| 17 | `APIGetVolumeCapabilitiesMsg` | header/src/main/java/org/zstack/header/volume/APIGetVolumeCapabilitiesMsg.java |
| 18 | `APIGetVolumeFormatMsg` | header/src/main/java/org/zstack/header/volume/APIGetVolumeFormatMsg.java |
| 19 | `APIQueryVolumeMsg` | header/src/main/java/org/zstack/header/volume/APIQueryVolumeMsg.java |
| 20 | `APIRecoverDataVolumeMsg` | header/src/main/java/org/zstack/header/volume/APIRecoverDataVolumeMsg.java |
| 21 | `APISyncVolumeSizeMsg` | header/src/main/java/org/zstack/header/volume/APISyncVolumeSizeMsg.java |
| 22 | `APIUndoSnapshotCreationMsg` | header/src/main/java/org/zstack/header/volume/APIUndoSnapshotCreationMsg.java |
| 23 | `APIUpdateVolumeMsg` | header/src/main/java/org/zstack/header/volume/APIUpdateVolumeMsg.java |

### `org.zstack.header.zone`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIChangeZoneStateMsg` | header/src/main/java/org/zstack/header/zone/APIChangeZoneStateMsg.java |
| 2 | `APICreateZoneMsg` | header/src/main/java/org/zstack/header/zone/APICreateZoneMsg.java |
| 3 | `APIDeleteZoneMsg` | header/src/main/java/org/zstack/header/zone/APIDeleteZoneMsg.java |
| 4 | `APIGetZoneMsg` | header/src/main/java/org/zstack/header/zone/APIGetZoneMsg.java |
| 5 | `APIQueryZoneMsg` | header/src/main/java/org/zstack/header/zone/APIQueryZoneMsg.java |
| 6 | `APIUpdateZoneMsg` | header/src/main/java/org/zstack/header/zone/APIUpdateZoneMsg.java |

---

<a id="module-pluginaccount-import"></a>

## 模块：`plugin/account-import`

### `org.zstack.identity.imports.api`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIQueryThirdPartyAccountSourceBindingMsg` | plugin/account-import/src/main/java/org/zstack/identity/imports/api/APIQueryThirdPartyAccountSourceBindingMsg.java |

---

<a id="module-pluginacl"></a>

## 模块：`plugin/acl`

### `org.zstack.header.acl`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddAccessControlListEntryMsg` | plugin/acl/src/main/java/org/zstack/header/acl/APIAddAccessControlListEntryMsg.java |
| 2 | `APIAddAccessControlListRedirectRuleMsg` | plugin/acl/src/main/java/org/zstack/header/acl/APIAddAccessControlListRedirectRuleMsg.java |
| 3 | `APIChangeAccessControlListRedirectRuleMsg` | plugin/acl/src/main/java/org/zstack/header/acl/APIChangeAccessControlListRedirectRuleMsg.java |
| 4 | `APICreateAccessControlListMsg` | plugin/acl/src/main/java/org/zstack/header/acl/APICreateAccessControlListMsg.java |
| 5 | `APIDeleteAccessControlListMsg` | plugin/acl/src/main/java/org/zstack/header/acl/APIDeleteAccessControlListMsg.java |
| 6 | `APIQueryAccessControlListMsg` | plugin/acl/src/main/java/org/zstack/header/acl/APIQueryAccessControlListMsg.java |
| 7 | `APIRemoveAccessControlListEntryMsg` | plugin/acl/src/main/java/org/zstack/header/acl/APIRemoveAccessControlListEntryMsg.java |

---

<a id="module-pluginappliancevm"></a>

## 模块：`plugin/applianceVm`

### `org.zstack.appliancevm`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIQueryApplianceVmMsg` | plugin/applianceVm/src/main/java/org/zstack/appliancevm/APIQueryApplianceVmMsg.java |

---

<a id="module-pluginceph"></a>

## 模块：`plugin/ceph`

### `org.zstack.storage.ceph.backup`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddCephBackupStorageMsg` | plugin/ceph/src/main/java/org/zstack/storage/ceph/backup/APIAddCephBackupStorageMsg.java |
| 2 | `APIAddMonToCephBackupStorageMsg` | plugin/ceph/src/main/java/org/zstack/storage/ceph/backup/APIAddMonToCephBackupStorageMsg.java |
| 3 | `APIQueryCephBackupStorageMsg` | plugin/ceph/src/main/java/org/zstack/storage/ceph/backup/APIQueryCephBackupStorageMsg.java |
| 4 | `APIRemoveMonFromCephBackupStorageMsg` | plugin/ceph/src/main/java/org/zstack/storage/ceph/backup/APIRemoveMonFromCephBackupStorageMsg.java |
| 5 | `APIUpdateCephBackupStorageMonMsg` | plugin/ceph/src/main/java/org/zstack/storage/ceph/backup/APIUpdateCephBackupStorageMonMsg.java |

### `org.zstack.storage.ceph.primary`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddCephPrimaryStorageMsg` | plugin/ceph/src/main/java/org/zstack/storage/ceph/primary/APIAddCephPrimaryStorageMsg.java |
| 2 | `APIAddCephPrimaryStoragePoolMsg` | plugin/ceph/src/main/java/org/zstack/storage/ceph/primary/APIAddCephPrimaryStoragePoolMsg.java |
| 3 | `APIAddMonToCephPrimaryStorageMsg` | plugin/ceph/src/main/java/org/zstack/storage/ceph/primary/APIAddMonToCephPrimaryStorageMsg.java |
| 4 | `APIDeleteCephPrimaryStoragePoolMsg` | plugin/ceph/src/main/java/org/zstack/storage/ceph/primary/APIDeleteCephPrimaryStoragePoolMsg.java |
| 5 | `APIQueryCephOsdGroupMsg` | plugin/ceph/src/main/java/org/zstack/storage/ceph/primary/APIQueryCephOsdGroupMsg.java |
| 6 | `APIQueryCephPrimaryStorageMsg` | plugin/ceph/src/main/java/org/zstack/storage/ceph/primary/APIQueryCephPrimaryStorageMsg.java |
| 7 | `APIQueryCephPrimaryStoragePoolMsg` | plugin/ceph/src/main/java/org/zstack/storage/ceph/primary/APIQueryCephPrimaryStoragePoolMsg.java |
| 8 | `APIRemoveMonFromCephPrimaryStorageMsg` | plugin/ceph/src/main/java/org/zstack/storage/ceph/primary/APIRemoveMonFromCephPrimaryStorageMsg.java |
| 9 | `APIUpdateCephPrimaryStorageMonMsg` | plugin/ceph/src/main/java/org/zstack/storage/ceph/primary/APIUpdateCephPrimaryStorageMonMsg.java |
| 10 | `APIUpdateCephPrimaryStoragePoolMsg` | plugin/ceph/src/main/java/org/zstack/storage/ceph/primary/APIUpdateCephPrimaryStoragePoolMsg.java |

---

<a id="module-plugindirectory"></a>

## 模块：`plugin/directory`

### `org.zstack.directory`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddResourcesToDirectoryMsg` | plugin/directory/src/main/java/org/zstack/directory/APIAddResourcesToDirectoryMsg.java |
| 2 | `APICreateDirectoryMsg` | plugin/directory/src/main/java/org/zstack/directory/APICreateDirectoryMsg.java |
| 3 | `APIDeleteDirectoryMsg` | plugin/directory/src/main/java/org/zstack/directory/APIDeleteDirectoryMsg.java |
| 4 | `APIMoveDirectoryMsg` | plugin/directory/src/main/java/org/zstack/directory/APIMoveDirectoryMsg.java |
| 5 | `APIMoveResourcesToDirectoryMsg` | plugin/directory/src/main/java/org/zstack/directory/APIMoveResourcesToDirectoryMsg.java |
| 6 | `APIQueryDirectoryMsg` | plugin/directory/src/main/java/org/zstack/directory/APIQueryDirectoryMsg.java |
| 7 | `APIRemoveResourcesFromDirectoryMsg` | plugin/directory/src/main/java/org/zstack/directory/APIRemoveResourcesFromDirectoryMsg.java |
| 8 | `APIUpdateDirectoryMsg` | plugin/directory/src/main/java/org/zstack/directory/APIUpdateDirectoryMsg.java |

---

<a id="module-plugineip"></a>

## 模块：`plugin/eip`

### `org.zstack.network.service.eip`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAttachEipMsg` | plugin/eip/src/main/java/org/zstack/network/service/eip/APIAttachEipMsg.java |
| 2 | `APIChangeEipStateMsg` | plugin/eip/src/main/java/org/zstack/network/service/eip/APIChangeEipStateMsg.java |
| 3 | `APICreateEipMsg` | plugin/eip/src/main/java/org/zstack/network/service/eip/APICreateEipMsg.java |
| 4 | `APIDeleteEipMsg` | plugin/eip/src/main/java/org/zstack/network/service/eip/APIDeleteEipMsg.java |
| 5 | `APIDetachEipMsg` | plugin/eip/src/main/java/org/zstack/network/service/eip/APIDetachEipMsg.java |
| 6 | `APIGetEipAttachableVmNicsMsg` | plugin/eip/src/main/java/org/zstack/network/service/eip/APIGetEipAttachableVmNicsMsg.java |
| 7 | `APIGetVmNicAttachableEipsMsg` | plugin/eip/src/main/java/org/zstack/network/service/eip/APIGetVmNicAttachableEipsMsg.java |
| 8 | `APIQueryEipMsg` | plugin/eip/src/main/java/org/zstack/network/service/eip/APIQueryEipMsg.java |
| 9 | `APIUpdateEipMsg` | plugin/eip/src/main/java/org/zstack/network/service/eip/APIUpdateEipMsg.java |

---

<a id="module-pluginflatnetworkprovider"></a>

## 模块：`plugin/flatNetworkProvider`

### `org.zstack.network.service.flat`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIChangeL3NetworkDhcpIpAddressMsg` | plugin/flatNetworkProvider/src/main/java/org/zstack/network/service/flat/APIChangeL3NetworkDhcpIpAddressMsg.java |
| 2 | `APIGetL3NetworkDhcpIpAddressMsg` | plugin/flatNetworkProvider/src/main/java/org/zstack/network/service/flat/APIGetL3NetworkDhcpIpAddressMsg.java |
| 3 | `APIGetL3NetworkIpStatisticMsg` | plugin/flatNetworkProvider/src/main/java/org/zstack/network/service/flat/APIGetL3NetworkIpStatisticMsg.java |

---

<a id="module-pluginhostnetworkinterface"></a>

## 模块：`plugin/hostNetworkInterface`

### `org.zstack.network.hostNetworkInterface.lldp.api`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIChangeHostNetworkInterfaceLldpModeMsg` | plugin/hostNetworkInterface/src/main/java/org/zstack/network/hostNetworkInterface/lldp/api/APIChangeHostNetworkInterfaceLldpModeMsg.java |
| 2 | `APIGetHostNetworkInterfaceLldpMsg` | plugin/hostNetworkInterface/src/main/java/org/zstack/network/hostNetworkInterface/lldp/api/APIGetHostNetworkInterfaceLldpMsg.java |
| 3 | `APIQueryHostNetworkInterfaceLldpMsg` | plugin/hostNetworkInterface/src/main/java/org/zstack/network/hostNetworkInterface/lldp/api/APIQueryHostNetworkInterfaceLldpMsg.java |

---

<a id="module-pluginkvm"></a>

## 模块：`plugin/kvm`

### `org.zstack.kvm`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddKVMHostMsg` | plugin/kvm/src/main/java/org/zstack/kvm/APIAddKVMHostMsg.java |
| 2 | `APIKvmRunShellMsg` | plugin/kvm/src/main/java/org/zstack/kvm/APIKvmRunShellMsg.java |
| 3 | `APIUpdateKVMHostMsg` | plugin/kvm/src/main/java/org/zstack/kvm/APIUpdateKVMHostMsg.java |

### `org.zstack.kvm.hypervisor.message`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIQueryHostOsCategoryMsg` | plugin/kvm/src/main/java/org/zstack/kvm/hypervisor/message/APIQueryHostOsCategoryMsg.java |
| 2 | `APIQueryKvmHypervisorInfoMsg` | plugin/kvm/src/main/java/org/zstack/kvm/hypervisor/message/APIQueryKvmHypervisorInfoMsg.java |

---

<a id="module-pluginldap"></a>

## 模块：`plugin/ldap`

### `org.zstack.ldap.api`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddLdapServerMsg` | plugin/ldap/src/main/java/org/zstack/ldap/api/APIAddLdapServerMsg.java |
| 2 | `APICreateLdapBindingMsg` | plugin/ldap/src/main/java/org/zstack/ldap/api/APICreateLdapBindingMsg.java |
| 3 | `APIDeleteLdapBindingMsg` | plugin/ldap/src/main/java/org/zstack/ldap/api/APIDeleteLdapBindingMsg.java |
| 4 | `APIDeleteLdapServerMsg` | plugin/ldap/src/main/java/org/zstack/ldap/api/APIDeleteLdapServerMsg.java |
| 5 | `APIGetCandidateLdapEntryForBindingMsg` | plugin/ldap/src/main/java/org/zstack/ldap/api/APIGetCandidateLdapEntryForBindingMsg.java |
| 6 | `APIGetLdapEntryMsg` | plugin/ldap/src/main/java/org/zstack/ldap/api/APIGetLdapEntryMsg.java |
| 7 | `APIQueryLdapServerMsg` | plugin/ldap/src/main/java/org/zstack/ldap/api/APIQueryLdapServerMsg.java |
| 8 | `APISyncAccountsFromLdapServerMsg` | plugin/ldap/src/main/java/org/zstack/ldap/api/APISyncAccountsFromLdapServerMsg.java |
| 9 | `APIUpdateLdapServerMsg` | plugin/ldap/src/main/java/org/zstack/ldap/api/APIUpdateLdapServerMsg.java |

---

<a id="module-pluginloadbalancer"></a>

## 模块：`plugin/loadBalancer`

### `org.zstack.network.service.lb`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddAccessControlListToLoadBalancerMsg` | plugin/loadBalancer/src/main/java/org/zstack/network/service/lb/APIAddAccessControlListToLoadBalancerMsg.java |
| 2 | `APIAddBackendServerToServerGroupMsg` | plugin/loadBalancer/src/main/java/org/zstack/network/service/lb/APIAddBackendServerToServerGroupMsg.java |
| 3 | `APIAddCertificateToLoadBalancerListenerMsg` | plugin/loadBalancer/src/main/java/org/zstack/network/service/lb/APIAddCertificateToLoadBalancerListenerMsg.java |
| 4 | `APIAddServerGroupToLoadBalancerListenerMsg` | plugin/loadBalancer/src/main/java/org/zstack/network/service/lb/APIAddServerGroupToLoadBalancerListenerMsg.java |
| 5 | `APIAddVmNicToLoadBalancerMsg` | plugin/loadBalancer/src/main/java/org/zstack/network/service/lb/APIAddVmNicToLoadBalancerMsg.java |
| 6 | `APIChangeAccessControlListServerGroupMsg` | plugin/loadBalancer/src/main/java/org/zstack/network/service/lb/APIChangeAccessControlListServerGroupMsg.java |
| 7 | `APIChangeLoadBalancerBackendServerMsg` | plugin/loadBalancer/src/main/java/org/zstack/network/service/lb/APIChangeLoadBalancerBackendServerMsg.java |
| 8 | `APIChangeLoadBalancerListenerMsg` | plugin/loadBalancer/src/main/java/org/zstack/network/service/lb/APIChangeLoadBalancerListenerMsg.java |
| 9 | `APICreateCertificateMsg` | plugin/loadBalancer/src/main/java/org/zstack/network/service/lb/APICreateCertificateMsg.java |
| 10 | `APICreateLoadBalancerListenerMsg` | plugin/loadBalancer/src/main/java/org/zstack/network/service/lb/APICreateLoadBalancerListenerMsg.java |
| 11 | `APICreateLoadBalancerMsg` | plugin/loadBalancer/src/main/java/org/zstack/network/service/lb/APICreateLoadBalancerMsg.java |
| 12 | `APICreateLoadBalancerServerGroupMsg` | plugin/loadBalancer/src/main/java/org/zstack/network/service/lb/APICreateLoadBalancerServerGroupMsg.java |
| 13 | `APIDeleteCertificateMsg` | plugin/loadBalancer/src/main/java/org/zstack/network/service/lb/APIDeleteCertificateMsg.java |
| 14 | `APIDeleteLoadBalancerListenerMsg` | plugin/loadBalancer/src/main/java/org/zstack/network/service/lb/APIDeleteLoadBalancerListenerMsg.java |
| 15 | `APIDeleteLoadBalancerMsg` | plugin/loadBalancer/src/main/java/org/zstack/network/service/lb/APIDeleteLoadBalancerMsg.java |
| 16 | `APIDeleteLoadBalancerServerGroupMsg` | plugin/loadBalancer/src/main/java/org/zstack/network/service/lb/APIDeleteLoadBalancerServerGroupMsg.java |
| 17 | `APIGetCandidateL3NetworksForLoadBalancerMsg` | plugin/loadBalancer/src/main/java/org/zstack/network/service/lb/APIGetCandidateL3NetworksForLoadBalancerMsg.java |
| 18 | `APIGetCandidateL3NetworksForServerGroupMsg` | plugin/loadBalancer/src/main/java/org/zstack/network/service/lb/APIGetCandidateL3NetworksForServerGroupMsg.java |
| 19 | `APIGetCandidateVmNicsForLoadBalancerMsg` | plugin/loadBalancer/src/main/java/org/zstack/network/service/lb/APIGetCandidateVmNicsForLoadBalancerMsg.java |
| 20 | `APIGetCandidateVmNicsForLoadBalancerServerGroupMsg` | plugin/loadBalancer/src/main/java/org/zstack/network/service/lb/APIGetCandidateVmNicsForLoadBalancerServerGroupMsg.java |
| 21 | `APIGetLoadBalancerListenerACLEntriesMsg` | plugin/loadBalancer/src/main/java/org/zstack/network/service/lb/APIGetLoadBalancerListenerACLEntriesMsg.java |
| 22 | `APIQueryCertificateMsg` | plugin/loadBalancer/src/main/java/org/zstack/network/service/lb/APIQueryCertificateMsg.java |
| 23 | `APIQueryLoadBalancerListenerMsg` | plugin/loadBalancer/src/main/java/org/zstack/network/service/lb/APIQueryLoadBalancerListenerMsg.java |
| 24 | `APIQueryLoadBalancerMsg` | plugin/loadBalancer/src/main/java/org/zstack/network/service/lb/APIQueryLoadBalancerMsg.java |
| 25 | `APIQueryLoadBalancerServerGroupMsg` | plugin/loadBalancer/src/main/java/org/zstack/network/service/lb/APIQueryLoadBalancerServerGroupMsg.java |
| 26 | `APIRefreshLoadBalancerMsg` | plugin/loadBalancer/src/main/java/org/zstack/network/service/lb/APIRefreshLoadBalancerMsg.java |
| 27 | `APIRemoveAccessControlListFromLoadBalancerMsg` | plugin/loadBalancer/src/main/java/org/zstack/network/service/lb/APIRemoveAccessControlListFromLoadBalancerMsg.java |
| 28 | `APIRemoveBackendServerFromServerGroupMsg` | plugin/loadBalancer/src/main/java/org/zstack/network/service/lb/APIRemoveBackendServerFromServerGroupMsg.java |
| 29 | `APIRemoveCertificateFromLoadBalancerListenerMsg` | plugin/loadBalancer/src/main/java/org/zstack/network/service/lb/APIRemoveCertificateFromLoadBalancerListenerMsg.java |
| 30 | `APIRemoveServerGroupFromLoadBalancerListenerMsg` | plugin/loadBalancer/src/main/java/org/zstack/network/service/lb/APIRemoveServerGroupFromLoadBalancerListenerMsg.java |
| 31 | `APIRemoveVmNicFromLoadBalancerMsg` | plugin/loadBalancer/src/main/java/org/zstack/network/service/lb/APIRemoveVmNicFromLoadBalancerMsg.java |
| 32 | `APIUpdateCertificateMsg` | plugin/loadBalancer/src/main/java/org/zstack/network/service/lb/APIUpdateCertificateMsg.java |
| 33 | `APIUpdateLoadBalancerListenerMsg` | plugin/loadBalancer/src/main/java/org/zstack/network/service/lb/APIUpdateLoadBalancerListenerMsg.java |
| 34 | `APIUpdateLoadBalancerMsg` | plugin/loadBalancer/src/main/java/org/zstack/network/service/lb/APIUpdateLoadBalancerMsg.java |
| 35 | `APIUpdateLoadBalancerServerGroupMsg` | plugin/loadBalancer/src/main/java/org/zstack/network/service/lb/APIUpdateLoadBalancerServerGroupMsg.java |

---

<a id="module-pluginlocalstorage"></a>

## 模块：`plugin/localstorage`

### `org.zstack.storage.primary.local`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddLocalPrimaryStorageMsg` | plugin/localstorage/src/main/java/org/zstack/storage/primary/local/APIAddLocalPrimaryStorageMsg.java |
| 2 | `APIGetLocalStorageHostDiskCapacityMsg` | plugin/localstorage/src/main/java/org/zstack/storage/primary/local/APIGetLocalStorageHostDiskCapacityMsg.java |
| 3 | `APILocalStorageGetVolumeMigratableHostsMsg` | plugin/localstorage/src/main/java/org/zstack/storage/primary/local/APILocalStorageGetVolumeMigratableHostsMsg.java |
| 4 | `APILocalStorageMigrateVolumeMsg` | plugin/localstorage/src/main/java/org/zstack/storage/primary/local/APILocalStorageMigrateVolumeMsg.java |
| 5 | `APIQueryLocalStorageResourceRefMsg` | plugin/localstorage/src/main/java/org/zstack/storage/primary/local/APIQueryLocalStorageResourceRefMsg.java |

---

<a id="module-pluginnfsprimarystorage"></a>

## 模块：`plugin/nfsPrimaryStorage`

### `org.zstack.storage.primary.nfs`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddNfsPrimaryStorageMsg` | plugin/nfsPrimaryStorage/src/main/java/org/zstack/storage/primary/nfs/APIAddNfsPrimaryStorageMsg.java |

---

<a id="module-pluginportforwarding"></a>

## 模块：`plugin/portForwarding`

### `org.zstack.network.service.portforwarding`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAttachPortForwardingRuleMsg` | plugin/portForwarding/src/main/java/org/zstack/network/service/portforwarding/APIAttachPortForwardingRuleMsg.java |
| 2 | `APIChangePortForwardingRuleStateMsg` | plugin/portForwarding/src/main/java/org/zstack/network/service/portforwarding/APIChangePortForwardingRuleStateMsg.java |
| 3 | `APICreatePortForwardingRuleMsg` | plugin/portForwarding/src/main/java/org/zstack/network/service/portforwarding/APICreatePortForwardingRuleMsg.java |
| 4 | `APIDeletePortForwardingRuleMsg` | plugin/portForwarding/src/main/java/org/zstack/network/service/portforwarding/APIDeletePortForwardingRuleMsg.java |
| 5 | `APIDetachPortForwardingRuleMsg` | plugin/portForwarding/src/main/java/org/zstack/network/service/portforwarding/APIDetachPortForwardingRuleMsg.java |
| 6 | `APIGetPortForwardingAttachableVmNicsMsg` | plugin/portForwarding/src/main/java/org/zstack/network/service/portforwarding/APIGetPortForwardingAttachableVmNicsMsg.java |
| 7 | `APIQueryPortForwardingRuleMsg` | plugin/portForwarding/src/main/java/org/zstack/network/service/portforwarding/APIQueryPortForwardingRuleMsg.java |
| 8 | `APIUpdatePortForwardingRuleMsg` | plugin/portForwarding/src/main/java/org/zstack/network/service/portforwarding/APIUpdatePortForwardingRuleMsg.java |

---

<a id="module-pluginsdncontroller"></a>

## 模块：`plugin/sdnController`

### `org.zstack.sdnController.header`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddSdnControllerMsg` | plugin/sdnController/src/main/java/org/zstack/sdnController/header/APIAddSdnControllerMsg.java |
| 2 | `APICreateL2HardwareVxlanNetworkMsg` | plugin/sdnController/src/main/java/org/zstack/sdnController/header/APICreateL2HardwareVxlanNetworkMsg.java |
| 3 | `APICreateL2HardwareVxlanNetworkPoolMsg` | plugin/sdnController/src/main/java/org/zstack/sdnController/header/APICreateL2HardwareVxlanNetworkPoolMsg.java |
| 4 | `APIQuerySdnControllerMsg` | plugin/sdnController/src/main/java/org/zstack/sdnController/header/APIQuerySdnControllerMsg.java |
| 5 | `APIRemoveSdnControllerMsg` | plugin/sdnController/src/main/java/org/zstack/sdnController/header/APIRemoveSdnControllerMsg.java |
| 6 | `APIUpdateSdnControllerMsg` | plugin/sdnController/src/main/java/org/zstack/sdnController/header/APIUpdateSdnControllerMsg.java |

---

<a id="module-pluginsecuritygroup"></a>

## 模块：`plugin/securityGroup`

### `org.zstack.network.securitygroup`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddSecurityGroupRuleMsg` | plugin/securityGroup/src/main/java/org/zstack/network/securitygroup/APIAddSecurityGroupRuleMsg.java |
| 2 | `APIAddVmNicToSecurityGroupMsg` | plugin/securityGroup/src/main/java/org/zstack/network/securitygroup/APIAddVmNicToSecurityGroupMsg.java |
| 3 | `APIAttachSecurityGroupToL3NetworkMsg` | plugin/securityGroup/src/main/java/org/zstack/network/securitygroup/APIAttachSecurityGroupToL3NetworkMsg.java |
| 4 | `APIChangeSecurityGroupRuleMsg` | plugin/securityGroup/src/main/java/org/zstack/network/securitygroup/APIChangeSecurityGroupRuleMsg.java |
| 5 | `APIChangeSecurityGroupRuleStateMsg` | plugin/securityGroup/src/main/java/org/zstack/network/securitygroup/APIChangeSecurityGroupRuleStateMsg.java |
| 6 | `APIChangeSecurityGroupStateMsg` | plugin/securityGroup/src/main/java/org/zstack/network/securitygroup/APIChangeSecurityGroupStateMsg.java |
| 7 | `APIChangeVmNicSecurityPolicyMsg` | plugin/securityGroup/src/main/java/org/zstack/network/securitygroup/APIChangeVmNicSecurityPolicyMsg.java |
| 8 | `APICreateSecurityGroupMsg` | plugin/securityGroup/src/main/java/org/zstack/network/securitygroup/APICreateSecurityGroupMsg.java |
| 9 | `APIDeleteSecurityGroupMsg` | plugin/securityGroup/src/main/java/org/zstack/network/securitygroup/APIDeleteSecurityGroupMsg.java |
| 10 | `APIDeleteSecurityGroupRuleMsg` | plugin/securityGroup/src/main/java/org/zstack/network/securitygroup/APIDeleteSecurityGroupRuleMsg.java |
| 11 | `APIDeleteVmNicFromSecurityGroupMsg` | plugin/securityGroup/src/main/java/org/zstack/network/securitygroup/APIDeleteVmNicFromSecurityGroupMsg.java |
| 12 | `APIDetachSecurityGroupFromL3NetworkMsg` | plugin/securityGroup/src/main/java/org/zstack/network/securitygroup/APIDetachSecurityGroupFromL3NetworkMsg.java |
| 13 | `APIGetCandidateVmNicForSecurityGroupMsg` | plugin/securityGroup/src/main/java/org/zstack/network/securitygroup/APIGetCandidateVmNicForSecurityGroupMsg.java |
| 14 | `APIQuerySecurityGroupMsg` | plugin/securityGroup/src/main/java/org/zstack/network/securitygroup/APIQuerySecurityGroupMsg.java |
| 15 | `APIQuerySecurityGroupRuleMsg` | plugin/securityGroup/src/main/java/org/zstack/network/securitygroup/APIQuerySecurityGroupRuleMsg.java |
| 16 | `APIQueryVmNicInSecurityGroupMsg` | plugin/securityGroup/src/main/java/org/zstack/network/securitygroup/APIQueryVmNicInSecurityGroupMsg.java |
| 17 | `APIQueryVmNicSecurityPolicyMsg` | plugin/securityGroup/src/main/java/org/zstack/network/securitygroup/APIQueryVmNicSecurityPolicyMsg.java |
| 18 | `APISetVmNicSecurityGroupMsg` | plugin/securityGroup/src/main/java/org/zstack/network/securitygroup/APISetVmNicSecurityGroupMsg.java |
| 19 | `APIUpdateSecurityGroupMsg` | plugin/securityGroup/src/main/java/org/zstack/network/securitygroup/APIUpdateSecurityGroupMsg.java |
| 20 | `APIUpdateSecurityGroupRulePriorityMsg` | plugin/securityGroup/src/main/java/org/zstack/network/securitygroup/APIUpdateSecurityGroupRulePriorityMsg.java |
| 21 | `APIValidateSecurityGroupRuleMsg` | plugin/securityGroup/src/main/java/org/zstack/network/securitygroup/APIValidateSecurityGroupRuleMsg.java |

---

<a id="module-pluginsftpbackupstorage"></a>

## 模块：`plugin/sftpBackupStorage`

### `org.zstack.storage.backup.sftp`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddSftpBackupStorageMsg` | plugin/sftpBackupStorage/src/main/java/org/zstack/storage/backup/sftp/APIAddSftpBackupStorageMsg.java |
| 2 | `APIQuerySftpBackupStorageMsg` | plugin/sftpBackupStorage/src/main/java/org/zstack/storage/backup/sftp/APIQuerySftpBackupStorageMsg.java |
| 3 | `APIReconnectSftpBackupStorageMsg` | plugin/sftpBackupStorage/src/main/java/org/zstack/storage/backup/sftp/APIReconnectSftpBackupStorageMsg.java |
| 4 | `APIUpdateSftpBackupStorageMsg` | plugin/sftpBackupStorage/src/main/java/org/zstack/storage/backup/sftp/APIUpdateSftpBackupStorageMsg.java |

---

<a id="module-pluginsharedmountpointprimarystorage"></a>

## 模块：`plugin/sharedMountPointPrimaryStorage`

### `org.zstack.storage.primary.smp`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddSharedMountPointPrimaryStorageMsg` | plugin/sharedMountPointPrimaryStorage/src/main/java/org/zstack/storage/primary/smp/APIAddSharedMountPointPrimaryStorageMsg.java |

---

<a id="module-pluginsshkeypair"></a>

## 模块：`plugin/sshKeyPair`

### `org.zstack.header.sshkeypair`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAttachSshKeyPairToVmInstanceMsg` | plugin/sshKeyPair/src/main/java/org/zstack/header/sshkeypair/APIAttachSshKeyPairToVmInstanceMsg.java |
| 2 | `APICreateSshKeyPairMsg` | plugin/sshKeyPair/src/main/java/org/zstack/header/sshkeypair/APICreateSshKeyPairMsg.java |
| 3 | `APIDeleteSshKeyPairMsg` | plugin/sshKeyPair/src/main/java/org/zstack/header/sshkeypair/APIDeleteSshKeyPairMsg.java |
| 4 | `APIDetachSshKeyPairFromVmInstanceMsg` | plugin/sshKeyPair/src/main/java/org/zstack/header/sshkeypair/APIDetachSshKeyPairFromVmInstanceMsg.java |
| 5 | `APIGenerateSshKeyPairMsg` | plugin/sshKeyPair/src/main/java/org/zstack/header/sshkeypair/APIGenerateSshKeyPairMsg.java |
| 6 | `APIQuerySshKeyPairMsg` | plugin/sshKeyPair/src/main/java/org/zstack/header/sshkeypair/APIQuerySshKeyPairMsg.java |
| 7 | `APIUpdateSshKeyPairMsg` | plugin/sshKeyPair/src/main/java/org/zstack/header/sshkeypair/APIUpdateSshKeyPairMsg.java |

---

<a id="module-pluginsugonsdncontroller"></a>

## 模块：`plugin/sugonSdnController`

### `org.zstack.sugonSdnController.header`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APICreateL2TfNetworkMsg` | plugin/sugonSdnController/src/main/java/org/zstack/sugonSdnController/header/APICreateL2TfNetworkMsg.java |

---

<a id="module-pluginvip"></a>

## 模块：`plugin/vip`

### `org.zstack.network.service.vip`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIChangeVipStateMsg` | plugin/vip/src/main/java/org/zstack/network/service/vip/APIChangeVipStateMsg.java |
| 2 | `APICheckVipPortAvailabilityMsg` | plugin/vip/src/main/java/org/zstack/network/service/vip/APICheckVipPortAvailabilityMsg.java |
| 3 | `APICreateVipMsg` | plugin/vip/src/main/java/org/zstack/network/service/vip/APICreateVipMsg.java |
| 4 | `APIDeleteVipMsg` | plugin/vip/src/main/java/org/zstack/network/service/vip/APIDeleteVipMsg.java |
| 5 | `APIGetVipAvailablePortMsg` | plugin/vip/src/main/java/org/zstack/network/service/vip/APIGetVipAvailablePortMsg.java |
| 6 | `APIQueryVipMsg` | plugin/vip/src/main/java/org/zstack/network/service/vip/APIQueryVipMsg.java |
| 7 | `APIUpdateVipMsg` | plugin/vip/src/main/java/org/zstack/network/service/vip/APIUpdateVipMsg.java |

---

<a id="module-pluginvirtualrouterprovider"></a>

## 模块：`plugin/virtualRouterProvider`

### `org.zstack.network.service.virtualrouter`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APICreateVirtualRouterOfferingMsg` | plugin/virtualRouterProvider/src/main/java/org/zstack/network/service/virtualrouter/APICreateVirtualRouterOfferingMsg.java |
| 2 | `APICreateVirtualRouterVmMsg` | plugin/virtualRouterProvider/src/main/java/org/zstack/network/service/virtualrouter/APICreateVirtualRouterVmMsg.java |
| 3 | `APIGetAttachablePublicL3ForVRouterMsg` | plugin/virtualRouterProvider/src/main/java/org/zstack/network/service/virtualrouter/APIGetAttachablePublicL3ForVRouterMsg.java |
| 4 | `APIGetVipUsedPortsMsg` | plugin/virtualRouterProvider/src/main/java/org/zstack/network/service/virtualrouter/APIGetVipUsedPortsMsg.java |
| 5 | `APIProvisionVirtualRouterConfigMsg` | plugin/virtualRouterProvider/src/main/java/org/zstack/network/service/virtualrouter/APIProvisionVirtualRouterConfigMsg.java |
| 6 | `APIQueryVirtualRouterOfferingMsg` | plugin/virtualRouterProvider/src/main/java/org/zstack/network/service/virtualrouter/APIQueryVirtualRouterOfferingMsg.java |
| 7 | `APIQueryVirtualRouterVmMsg` | plugin/virtualRouterProvider/src/main/java/org/zstack/network/service/virtualrouter/APIQueryVirtualRouterVmMsg.java |
| 8 | `APIReconnectVirtualRouterMsg` | plugin/virtualRouterProvider/src/main/java/org/zstack/network/service/virtualrouter/APIReconnectVirtualRouterMsg.java |
| 9 | `APIUpdateVirtualRouterMsg` | plugin/virtualRouterProvider/src/main/java/org/zstack/network/service/virtualrouter/APIUpdateVirtualRouterMsg.java |
| 10 | `APIUpdateVirtualRouterOfferingMsg` | plugin/virtualRouterProvider/src/main/java/org/zstack/network/service/virtualrouter/APIUpdateVirtualRouterOfferingMsg.java |

---

<a id="module-pluginvxlan"></a>

## 模块：`plugin/vxlan`

### `org.zstack.network.l2.vxlan.vtep`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APICreateVxlanVtepMsg` | plugin/vxlan/src/main/java/org/zstack/network/l2/vxlan/vtep/APICreateVxlanVtepMsg.java |
| 2 | `APIQueryVtepMsg` | plugin/vxlan/src/main/java/org/zstack/network/l2/vxlan/vtep/APIQueryVtepMsg.java |

### `org.zstack.network.l2.vxlan.vxlanNetwork`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APICreateL2VxlanNetworkMsg` | plugin/vxlan/src/main/java/org/zstack/network/l2/vxlan/vxlanNetwork/APICreateL2VxlanNetworkMsg.java |
| 2 | `APIQueryL2VxlanNetworkMsg` | plugin/vxlan/src/main/java/org/zstack/network/l2/vxlan/vxlanNetwork/APIQueryL2VxlanNetworkMsg.java |

### `org.zstack.network.l2.vxlan.vxlanNetworkPool`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APICreateL2VxlanNetworkPoolMsg` | plugin/vxlan/src/main/java/org/zstack/network/l2/vxlan/vxlanNetworkPool/APICreateL2VxlanNetworkPoolMsg.java |
| 2 | `APICreateVniRangeMsg` | plugin/vxlan/src/main/java/org/zstack/network/l2/vxlan/vxlanNetworkPool/APICreateVniRangeMsg.java |
| 3 | `APICreateVxlanPoolRemoteVtepMsg` | plugin/vxlan/src/main/java/org/zstack/network/l2/vxlan/vxlanNetworkPool/APICreateVxlanPoolRemoteVtepMsg.java |
| 4 | `APIDeleteVniRangeMsg` | plugin/vxlan/src/main/java/org/zstack/network/l2/vxlan/vxlanNetworkPool/APIDeleteVniRangeMsg.java |
| 5 | `APIDeleteVxlanPoolRemoteVtepMsg` | plugin/vxlan/src/main/java/org/zstack/network/l2/vxlan/vxlanNetworkPool/APIDeleteVxlanPoolRemoteVtepMsg.java |
| 6 | `APIQueryL2VxlanNetworkPoolMsg` | plugin/vxlan/src/main/java/org/zstack/network/l2/vxlan/vxlanNetworkPool/APIQueryL2VxlanNetworkPoolMsg.java |
| 7 | `APIQueryVniRangeMsg` | plugin/vxlan/src/main/java/org/zstack/network/l2/vxlan/vxlanNetworkPool/APIQueryVniRangeMsg.java |
| 8 | `APIUpdateVniRangeMsg` | plugin/vxlan/src/main/java/org/zstack/network/l2/vxlan/vxlanNetworkPool/APIUpdateVniRangeMsg.java |

---

<a id="module-premiumaccesskey"></a>

## 模块：`premium/accesskey`

### `org.zstack.accessKey`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIChangeAccessKeyStateMsg` | premium/accesskey/src/main/java/org/zstack/accessKey/APIChangeAccessKeyStateMsg.java |
| 2 | `APICreateAccessKeyMsg` | premium/accesskey/src/main/java/org/zstack/accessKey/APICreateAccessKeyMsg.java |
| 3 | `APIDeleteAccessKeyMsg` | premium/accesskey/src/main/java/org/zstack/accessKey/APIDeleteAccessKeyMsg.java |
| 4 | `APIQueryAccessKeyMsg` | premium/accesskey/src/main/java/org/zstack/accessKey/APIQueryAccessKeyMsg.java |

---

<a id="module-premiumautoscaling"></a>

## 模块：`premium/autoscaling`

### `org.zstack.autoscaling.group`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIChangeAutoScalingGroupStateMsg` | premium/autoscaling/src/main/java/org/zstack/autoscaling/group/APIChangeAutoScalingGroupStateMsg.java |
| 2 | `APICreateAutoScalingGroupMsg` | premium/autoscaling/src/main/java/org/zstack/autoscaling/group/APICreateAutoScalingGroupMsg.java |
| 3 | `APIDeleteAutoScalingGroupMsg` | premium/autoscaling/src/main/java/org/zstack/autoscaling/group/APIDeleteAutoScalingGroupMsg.java |
| 4 | `APIQueryAutoScalingGroupMsg` | premium/autoscaling/src/main/java/org/zstack/autoscaling/group/APIQueryAutoScalingGroupMsg.java |
| 5 | `APIUpdateAutoScalingGroupMsg` | premium/autoscaling/src/main/java/org/zstack/autoscaling/group/APIUpdateAutoScalingGroupMsg.java |

### `org.zstack.autoscaling.group.activity`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIQueryAutoScalingGroupActivityMsg` | premium/autoscaling/src/main/java/org/zstack/autoscaling/group/activity/APIQueryAutoScalingGroupActivityMsg.java |

### `org.zstack.autoscaling.group.instance`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIDeleteAutoScalingGroupInstanceMsg` | premium/autoscaling/src/main/java/org/zstack/autoscaling/group/instance/APIDeleteAutoScalingGroupInstanceMsg.java |
| 2 | `APIQueryAutoScalingGroupInstanceMsg` | premium/autoscaling/src/main/java/org/zstack/autoscaling/group/instance/APIQueryAutoScalingGroupInstanceMsg.java |
| 3 | `APIUpdateAutoScalingGroupInstanceMsg` | premium/autoscaling/src/main/java/org/zstack/autoscaling/group/instance/APIUpdateAutoScalingGroupInstanceMsg.java |

### `org.zstack.autoscaling.group.rule`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APICreateAutoScalingGroupAddingNewInstanceRuleMsg` | premium/autoscaling/src/main/java/org/zstack/autoscaling/group/rule/APICreateAutoScalingGroupAddingNewInstanceRuleMsg.java |
| 2 | `APICreateAutoScalingGroupRemovalInstanceRuleMsg` | premium/autoscaling/src/main/java/org/zstack/autoscaling/group/rule/APICreateAutoScalingGroupRemovalInstanceRuleMsg.java |
| 3 | `APICreateAutoScalingGroupRuleMsg` | premium/autoscaling/src/main/java/org/zstack/autoscaling/group/rule/APICreateAutoScalingGroupRuleMsg.java |
| 4 | `APIDeleteAutoScalingRuleMsg` | premium/autoscaling/src/main/java/org/zstack/autoscaling/group/rule/APIDeleteAutoScalingRuleMsg.java |
| 5 | `APIExecuteAutoScalingRuleMsg` | premium/autoscaling/src/main/java/org/zstack/autoscaling/group/rule/APIExecuteAutoScalingRuleMsg.java |
| 6 | `APIQueryAutoScalingRuleMsg` | premium/autoscaling/src/main/java/org/zstack/autoscaling/group/rule/APIQueryAutoScalingRuleMsg.java |
| 7 | `APIUpdateAutoScalingGroupAddingNewInstanceRuleMsg` | premium/autoscaling/src/main/java/org/zstack/autoscaling/group/rule/APIUpdateAutoScalingGroupAddingNewInstanceRuleMsg.java |
| 8 | `APIUpdateAutoScalingGroupRemovalInstanceRuleMsg` | premium/autoscaling/src/main/java/org/zstack/autoscaling/group/rule/APIUpdateAutoScalingGroupRemovalInstanceRuleMsg.java |
| 9 | `APIUpdateAutoScalingRuleMsg` | premium/autoscaling/src/main/java/org/zstack/autoscaling/group/rule/APIUpdateAutoScalingRuleMsg.java |

### `org.zstack.autoscaling.group.rule.trigger`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APICreateAutoScalingRuleAlarmTriggerMsg` | premium/autoscaling/src/main/java/org/zstack/autoscaling/group/rule/trigger/APICreateAutoScalingRuleAlarmTriggerMsg.java |
| 2 | `APICreateAutoScalingRuleSchedulerJobTriggerMsg` | premium/autoscaling/src/main/java/org/zstack/autoscaling/group/rule/trigger/APICreateAutoScalingRuleSchedulerJobTriggerMsg.java |
| 3 | `APICreateAutoScalingRuleTriggerMsg` | premium/autoscaling/src/main/java/org/zstack/autoscaling/group/rule/trigger/APICreateAutoScalingRuleTriggerMsg.java |
| 4 | `APIDeleteAutoScalingRuleTriggerMsg` | premium/autoscaling/src/main/java/org/zstack/autoscaling/group/rule/trigger/APIDeleteAutoScalingRuleTriggerMsg.java |
| 5 | `APIQueryAutoScalingRuleTriggerMsg` | premium/autoscaling/src/main/java/org/zstack/autoscaling/group/rule/trigger/APIQueryAutoScalingRuleTriggerMsg.java |

### `org.zstack.autoscaling.template`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAttachAutoScalingTemplateToGroupMsg` | premium/autoscaling/src/main/java/org/zstack/autoscaling/template/APIAttachAutoScalingTemplateToGroupMsg.java |
| 2 | `APICreateAutoScalingTemplateMsg` | premium/autoscaling/src/main/java/org/zstack/autoscaling/template/APICreateAutoScalingTemplateMsg.java |
| 3 | `APICreateAutoScalingVmTemplateMsg` | premium/autoscaling/src/main/java/org/zstack/autoscaling/template/APICreateAutoScalingVmTemplateMsg.java |
| 4 | `APIDeleteAutoScalingTemplateMsg` | premium/autoscaling/src/main/java/org/zstack/autoscaling/template/APIDeleteAutoScalingTemplateMsg.java |
| 5 | `APIDetachAutoScalingTemplateFromGroupMsg` | premium/autoscaling/src/main/java/org/zstack/autoscaling/template/APIDetachAutoScalingTemplateFromGroupMsg.java |
| 6 | `APIQueryAutoScalingVmTemplateMsg` | premium/autoscaling/src/main/java/org/zstack/autoscaling/template/APIQueryAutoScalingVmTemplateMsg.java |
| 7 | `APIUpdateAutoScalingVmTemplateMsg` | premium/autoscaling/src/main/java/org/zstack/autoscaling/template/APIUpdateAutoScalingVmTemplateMsg.java |

---

<a id="module-premiumbaremetal"></a>

## 模块：`premium/baremetal`

### `org.zstack.header.baremetal.chassis`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIBatchCreateBaremetalChassisMsg` | premium/baremetal/src/main/java/org/zstack/header/baremetal/chassis/APIBatchCreateBaremetalChassisMsg.java |
| 2 | `APIChangeBaremetalChassisStateMsg` | premium/baremetal/src/main/java/org/zstack/header/baremetal/chassis/APIChangeBaremetalChassisStateMsg.java |
| 3 | `APICheckBaremetalChassisConfigFileMsg` | premium/baremetal/src/main/java/org/zstack/header/baremetal/chassis/APICheckBaremetalChassisConfigFileMsg.java |
| 4 | `APICleanUpBaremetalChassisBondingMsg` | premium/baremetal/src/main/java/org/zstack/header/baremetal/chassis/APICleanUpBaremetalChassisBondingMsg.java |
| 5 | `APICreateBaremetalChassisMsg` | premium/baremetal/src/main/java/org/zstack/header/baremetal/chassis/APICreateBaremetalChassisMsg.java |
| 6 | `APIDeleteBaremetalChassisMsg` | premium/baremetal/src/main/java/org/zstack/header/baremetal/chassis/APIDeleteBaremetalChassisMsg.java |
| 7 | `APIGetBaremetalChassisPowerStatusMsg` | premium/baremetal/src/main/java/org/zstack/header/baremetal/chassis/APIGetBaremetalChassisPowerStatusMsg.java |
| 8 | `APIInspectBaremetalChassisMsg` | premium/baremetal/src/main/java/org/zstack/header/baremetal/chassis/APIInspectBaremetalChassisMsg.java |
| 9 | `APIPowerOffBaremetalChassisMsg` | premium/baremetal/src/main/java/org/zstack/header/baremetal/chassis/APIPowerOffBaremetalChassisMsg.java |
| 10 | `APIPowerOnBaremetalChassisMsg` | premium/baremetal/src/main/java/org/zstack/header/baremetal/chassis/APIPowerOnBaremetalChassisMsg.java |
| 11 | `APIPowerResetBaremetalChassisMsg` | premium/baremetal/src/main/java/org/zstack/header/baremetal/chassis/APIPowerResetBaremetalChassisMsg.java |
| 12 | `APIQueryBaremetalChassisMsg` | premium/baremetal/src/main/java/org/zstack/header/baremetal/chassis/APIQueryBaremetalChassisMsg.java |
| 13 | `APIUpdateBaremetalChassisMsg` | premium/baremetal/src/main/java/org/zstack/header/baremetal/chassis/APIUpdateBaremetalChassisMsg.java |

### `org.zstack.header.baremetal.instance`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APICreateBaremetalInstanceMsg` | premium/baremetal/src/main/java/org/zstack/header/baremetal/instance/APICreateBaremetalInstanceMsg.java |
| 2 | `APIDestroyBaremetalInstanceMsg` | premium/baremetal/src/main/java/org/zstack/header/baremetal/instance/APIDestroyBaremetalInstanceMsg.java |
| 3 | `APIExpungeBaremetalInstanceMsg` | premium/baremetal/src/main/java/org/zstack/header/baremetal/instance/APIExpungeBaremetalInstanceMsg.java |
| 4 | `APIQueryBaremetalInstanceMsg` | premium/baremetal/src/main/java/org/zstack/header/baremetal/instance/APIQueryBaremetalInstanceMsg.java |
| 5 | `APIRebootBaremetalInstanceMsg` | premium/baremetal/src/main/java/org/zstack/header/baremetal/instance/APIRebootBaremetalInstanceMsg.java |
| 6 | `APIRecoverBaremetalInstanceMsg` | premium/baremetal/src/main/java/org/zstack/header/baremetal/instance/APIRecoverBaremetalInstanceMsg.java |
| 7 | `APIStartBaremetalInstanceMsg` | premium/baremetal/src/main/java/org/zstack/header/baremetal/instance/APIStartBaremetalInstanceMsg.java |
| 8 | `APIStopBaremetalInstanceMsg` | premium/baremetal/src/main/java/org/zstack/header/baremetal/instance/APIStopBaremetalInstanceMsg.java |
| 9 | `APIUpdateBaremetalInstanceMsg` | premium/baremetal/src/main/java/org/zstack/header/baremetal/instance/APIUpdateBaremetalInstanceMsg.java |

### `org.zstack.header.baremetal.network`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APICreateBaremetalBondingMsg` | premium/baremetal/src/main/java/org/zstack/header/baremetal/network/APICreateBaremetalBondingMsg.java |
| 2 | `APIQueryBaremetalBondingMsg` | premium/baremetal/src/main/java/org/zstack/header/baremetal/network/APIQueryBaremetalBondingMsg.java |

### `org.zstack.header.baremetal.preconfiguration`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddPreconfigurationTemplateMsg` | premium/baremetal/src/main/java/org/zstack/header/baremetal/preconfiguration/APIAddPreconfigurationTemplateMsg.java |
| 2 | `APIChangePreconfigurationTemplateStateMsg` | premium/baremetal/src/main/java/org/zstack/header/baremetal/preconfiguration/APIChangePreconfigurationTemplateStateMsg.java |
| 3 | `APIDeletePreconfigurationTemplateMsg` | premium/baremetal/src/main/java/org/zstack/header/baremetal/preconfiguration/APIDeletePreconfigurationTemplateMsg.java |
| 4 | `APIQueryPreconfigurationTemplateMsg` | premium/baremetal/src/main/java/org/zstack/header/baremetal/preconfiguration/APIQueryPreconfigurationTemplateMsg.java |
| 5 | `APIUpdatePreconfigurationTemplateMsg` | premium/baremetal/src/main/java/org/zstack/header/baremetal/preconfiguration/APIUpdatePreconfigurationTemplateMsg.java |

### `org.zstack.header.baremetal.pxeserver`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAttachBaremetalPxeServerToClusterMsg` | premium/baremetal/src/main/java/org/zstack/header/baremetal/pxeserver/APIAttachBaremetalPxeServerToClusterMsg.java |
| 2 | `APICreateBaremetalPxeServerMsg` | premium/baremetal/src/main/java/org/zstack/header/baremetal/pxeserver/APICreateBaremetalPxeServerMsg.java |
| 3 | `APIDeleteBaremetalPxeServerMsg` | premium/baremetal/src/main/java/org/zstack/header/baremetal/pxeserver/APIDeleteBaremetalPxeServerMsg.java |
| 4 | `APIDetachBaremetalPxeServerFromClusterMsg` | premium/baremetal/src/main/java/org/zstack/header/baremetal/pxeserver/APIDetachBaremetalPxeServerFromClusterMsg.java |
| 5 | `APIQueryBaremetalPxeServerMsg` | premium/baremetal/src/main/java/org/zstack/header/baremetal/pxeserver/APIQueryBaremetalPxeServerMsg.java |
| 6 | `APIReconnectBaremetalPxeServerMsg` | premium/baremetal/src/main/java/org/zstack/header/baremetal/pxeserver/APIReconnectBaremetalPxeServerMsg.java |
| 7 | `APIStartBaremetalPxeServerMsg` | premium/baremetal/src/main/java/org/zstack/header/baremetal/pxeserver/APIStartBaremetalPxeServerMsg.java |
| 8 | `APIStopBaremetalPxeServerMsg` | premium/baremetal/src/main/java/org/zstack/header/baremetal/pxeserver/APIStopBaremetalPxeServerMsg.java |
| 9 | `APIUpdateBaremetalPxeServerMsg` | premium/baremetal/src/main/java/org/zstack/header/baremetal/pxeserver/APIUpdateBaremetalPxeServerMsg.java |

---

<a id="module-premiumbaremetal2"></a>

## 模块：`premium/baremetal2`

### `org.zstack.baremetal2.chassis`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddBareMetal2ChassisMsg` | premium/baremetal2/src/main/java/org/zstack/baremetal2/chassis/APIAddBareMetal2ChassisMsg.java |
| 2 | `APIBatchAddBareMetal2ChassisMsg` | premium/baremetal2/src/main/java/org/zstack/baremetal2/chassis/APIBatchAddBareMetal2ChassisMsg.java |
| 3 | `APIChangeBareMetal2ChassisStateMsg` | premium/baremetal2/src/main/java/org/zstack/baremetal2/chassis/APIChangeBareMetal2ChassisStateMsg.java |
| 4 | `APICheckBareMetal2ChassisConfigFileMsg` | premium/baremetal2/src/main/java/org/zstack/baremetal2/chassis/APICheckBareMetal2ChassisConfigFileMsg.java |
| 5 | `APICleanUpBareMetal2BondingMsg` | premium/baremetal2/src/main/java/org/zstack/baremetal2/chassis/APICleanUpBareMetal2BondingMsg.java |
| 6 | `APICreateBareMetal2BondingMsg` | premium/baremetal2/src/main/java/org/zstack/baremetal2/chassis/APICreateBareMetal2BondingMsg.java |
| 7 | `APICreateBareMetal2ChassisHardwareInfoMsg` | premium/baremetal2/src/main/java/org/zstack/baremetal2/chassis/APICreateBareMetal2ChassisHardwareInfoMsg.java |
| 8 | `APIDeleteBareMetal2ChassisMsg` | premium/baremetal2/src/main/java/org/zstack/baremetal2/chassis/APIDeleteBareMetal2ChassisMsg.java |
| 9 | `APIGetBareMetal2ChassisPowerStatusMsg` | premium/baremetal2/src/main/java/org/zstack/baremetal2/chassis/APIGetBareMetal2ChassisPowerStatusMsg.java |
| 10 | `APIGetBareMetal2SupportedBootModeMsg` | premium/baremetal2/src/main/java/org/zstack/baremetal2/chassis/APIGetBareMetal2SupportedBootModeMsg.java |
| 11 | `APIInspectBareMetal2ChassisMsg` | premium/baremetal2/src/main/java/org/zstack/baremetal2/chassis/APIInspectBareMetal2ChassisMsg.java |
| 12 | `APIPowerOffBareMetal2ChassisMsg` | premium/baremetal2/src/main/java/org/zstack/baremetal2/chassis/APIPowerOffBareMetal2ChassisMsg.java |
| 13 | `APIPowerOnBareMetal2ChassisMsg` | premium/baremetal2/src/main/java/org/zstack/baremetal2/chassis/APIPowerOnBareMetal2ChassisMsg.java |
| 14 | `APIPowerResetBareMetal2ChassisMsg` | premium/baremetal2/src/main/java/org/zstack/baremetal2/chassis/APIPowerResetBareMetal2ChassisMsg.java |
| 15 | `APIQueryBareMetal2BondingMsg` | premium/baremetal2/src/main/java/org/zstack/baremetal2/chassis/APIQueryBareMetal2BondingMsg.java |
| 16 | `APIQueryBareMetal2BondingNicRefMsg` | premium/baremetal2/src/main/java/org/zstack/baremetal2/chassis/APIQueryBareMetal2BondingNicRefMsg.java |
| 17 | `APIQueryBareMetal2ChassisMsg` | premium/baremetal2/src/main/java/org/zstack/baremetal2/chassis/APIQueryBareMetal2ChassisMsg.java |
| 18 | `APIUpdateBareMetal2ChassisMsg` | premium/baremetal2/src/main/java/org/zstack/baremetal2/chassis/APIUpdateBareMetal2ChassisMsg.java |

### `org.zstack.baremetal2.chassis.ipmi`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddBareMetal2IpmiChassisMsg` | premium/baremetal2/src/main/java/org/zstack/baremetal2/chassis/ipmi/APIAddBareMetal2IpmiChassisMsg.java |
| 2 | `APIBatchAddBareMetal2IpmiChassisMsg` | premium/baremetal2/src/main/java/org/zstack/baremetal2/chassis/ipmi/APIBatchAddBareMetal2IpmiChassisMsg.java |
| 3 | `APICheckBareMetal2IpmiChassisConfigFileMsg` | premium/baremetal2/src/main/java/org/zstack/baremetal2/chassis/ipmi/APICheckBareMetal2IpmiChassisConfigFileMsg.java |
| 4 | `APICreateBareMetal2IpmiChassisHardwareInfoMsg` | premium/baremetal2/src/main/java/org/zstack/baremetal2/chassis/ipmi/APICreateBareMetal2IpmiChassisHardwareInfoMsg.java |
| 5 | `APIUpdateBareMetal2IpmiChassisMsg` | premium/baremetal2/src/main/java/org/zstack/baremetal2/chassis/ipmi/APIUpdateBareMetal2IpmiChassisMsg.java |

### `org.zstack.baremetal2.configuration`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIChangeBareMetal2ChassisOfferingStateMsg` | premium/baremetal2/src/main/java/org/zstack/baremetal2/configuration/APIChangeBareMetal2ChassisOfferingStateMsg.java |
| 2 | `APIQueryBareMetal2ChassisOfferingMsg` | premium/baremetal2/src/main/java/org/zstack/baremetal2/configuration/APIQueryBareMetal2ChassisOfferingMsg.java |
| 3 | `APIUpdateBareMetal2ChassisOfferingMsg` | premium/baremetal2/src/main/java/org/zstack/baremetal2/configuration/APIUpdateBareMetal2ChassisOfferingMsg.java |

### `org.zstack.baremetal2.gateway`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddBareMetal2GatewayMsg` | premium/baremetal2/src/main/java/org/zstack/baremetal2/gateway/APIAddBareMetal2GatewayMsg.java |
| 2 | `APIAttachBareMetal2GatewayToClusterMsg` | premium/baremetal2/src/main/java/org/zstack/baremetal2/gateway/APIAttachBareMetal2GatewayToClusterMsg.java |
| 3 | `APIChangeBareMetal2GatewayClusterMsg` | premium/baremetal2/src/main/java/org/zstack/baremetal2/gateway/APIChangeBareMetal2GatewayClusterMsg.java |
| 4 | `APIChangeBareMetal2GatewayStateMsg` | premium/baremetal2/src/main/java/org/zstack/baremetal2/gateway/APIChangeBareMetal2GatewayStateMsg.java |
| 5 | `APIDeleteBareMetal2GatewayMsg` | premium/baremetal2/src/main/java/org/zstack/baremetal2/gateway/APIDeleteBareMetal2GatewayMsg.java |
| 6 | `APIDetachBareMetal2GatewayFromClusterMsg` | premium/baremetal2/src/main/java/org/zstack/baremetal2/gateway/APIDetachBareMetal2GatewayFromClusterMsg.java |
| 7 | `APIQueryBareMetal2GatewayMsg` | premium/baremetal2/src/main/java/org/zstack/baremetal2/gateway/APIQueryBareMetal2GatewayMsg.java |
| 8 | `APIReconnectBareMetal2GatewayMsg` | premium/baremetal2/src/main/java/org/zstack/baremetal2/gateway/APIReconnectBareMetal2GatewayMsg.java |
| 9 | `APIUpdateBareMetal2GatewayMsg` | premium/baremetal2/src/main/java/org/zstack/baremetal2/gateway/APIUpdateBareMetal2GatewayMsg.java |

### `org.zstack.baremetal2.gateway.allocator`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIGetBareMetal2GatewayAllocatorStrategiesMsg` | premium/baremetal2/src/main/java/org/zstack/baremetal2/gateway/allocator/APIGetBareMetal2GatewayAllocatorStrategiesMsg.java |

### `org.zstack.baremetal2.instance`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAttachProvisionNicToBondingMsg` | premium/baremetal2/src/main/java/org/zstack/baremetal2/instance/APIAttachProvisionNicToBondingMsg.java |
| 2 | `APIChangeBareMetal2InstancePasswordMsg` | premium/baremetal2/src/main/java/org/zstack/baremetal2/instance/APIChangeBareMetal2InstancePasswordMsg.java |
| 3 | `APICreateBareMetal2InstanceMsg` | premium/baremetal2/src/main/java/org/zstack/baremetal2/instance/APICreateBareMetal2InstanceMsg.java |
| 4 | `APIDetachProvisionNicFromBondingMsg` | premium/baremetal2/src/main/java/org/zstack/baremetal2/instance/APIDetachProvisionNicFromBondingMsg.java |
| 5 | `APIQueryBareMetal2InstanceMsg` | premium/baremetal2/src/main/java/org/zstack/baremetal2/instance/APIQueryBareMetal2InstanceMsg.java |
| 6 | `APIReconnectBareMetal2InstanceMsg` | premium/baremetal2/src/main/java/org/zstack/baremetal2/instance/APIReconnectBareMetal2InstanceMsg.java |
| 7 | `APIStartBareMetal2InstanceMsg` | premium/baremetal2/src/main/java/org/zstack/baremetal2/instance/APIStartBareMetal2InstanceMsg.java |
| 8 | `APIUpdateBareMetal2InstanceMsg` | premium/baremetal2/src/main/java/org/zstack/baremetal2/instance/APIUpdateBareMetal2InstanceMsg.java |

### `org.zstack.baremetal2.provisionnetwork`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAttachBareMetal2ProvisionNetworkToClusterMsg` | premium/baremetal2/src/main/java/org/zstack/baremetal2/provisionnetwork/APIAttachBareMetal2ProvisionNetworkToClusterMsg.java |
| 2 | `APIChangeBareMetal2ProvisionNetworkStateMsg` | premium/baremetal2/src/main/java/org/zstack/baremetal2/provisionnetwork/APIChangeBareMetal2ProvisionNetworkStateMsg.java |
| 3 | `APICreateBareMetal2ProvisionNetworkMsg` | premium/baremetal2/src/main/java/org/zstack/baremetal2/provisionnetwork/APICreateBareMetal2ProvisionNetworkMsg.java |
| 4 | `APIDeleteBareMetal2ProvisionNetworkMsg` | premium/baremetal2/src/main/java/org/zstack/baremetal2/provisionnetwork/APIDeleteBareMetal2ProvisionNetworkMsg.java |
| 5 | `APIDetachBareMetal2ProvisionNetworkFromClusterMsg` | premium/baremetal2/src/main/java/org/zstack/baremetal2/provisionnetwork/APIDetachBareMetal2ProvisionNetworkFromClusterMsg.java |
| 6 | `APIGetBareMetal2ProvisionNetworkIpAddressCapacityMsg` | premium/baremetal2/src/main/java/org/zstack/baremetal2/provisionnetwork/APIGetBareMetal2ProvisionNetworkIpAddressCapacityMsg.java |
| 7 | `APIQueryBareMetal2ProvisionNetworkMsg` | premium/baremetal2/src/main/java/org/zstack/baremetal2/provisionnetwork/APIQueryBareMetal2ProvisionNetworkMsg.java |
| 8 | `APIUpdateBareMetal2ProvisionNetworkMsg` | premium/baremetal2/src/main/java/org/zstack/baremetal2/provisionnetwork/APIUpdateBareMetal2ProvisionNetworkMsg.java |

---

<a id="module-premiumbilling"></a>

## 模块：`premium/billing`

### `org.zstack.billing`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APICalculateAccountBillingSpendingMsg` | premium/billing/src/main/java/org/zstack/billing/APICalculateAccountBillingSpendingMsg.java |
| 2 | `APICalculateAccountSpendingMsg` | premium/billing/src/main/java/org/zstack/billing/APICalculateAccountSpendingMsg.java |
| 3 | `APICalculateResourceSpendingMsg` | premium/billing/src/main/java/org/zstack/billing/APICalculateResourceSpendingMsg.java |
| 4 | `APICleanupBillingUsageMsg` | premium/billing/src/main/java/org/zstack/billing/APICleanupBillingUsageMsg.java |
| 5 | `APICreateResourcePriceMsg` | premium/billing/src/main/java/org/zstack/billing/APICreateResourcePriceMsg.java |
| 6 | `APIDeleteBillingMsg` | premium/billing/src/main/java/org/zstack/billing/APIDeleteBillingMsg.java |
| 7 | `APIDeleteResourcePriceMsg` | premium/billing/src/main/java/org/zstack/billing/APIDeleteResourcePriceMsg.java |
| 8 | `APIQueryAccountBillingMsg` | premium/billing/src/main/java/org/zstack/billing/APIQueryAccountBillingMsg.java |
| 9 | `APIQueryResourcePriceMsg` | premium/billing/src/main/java/org/zstack/billing/APIQueryResourcePriceMsg.java |
| 10 | `APIUpdateResourcePriceMsg` | premium/billing/src/main/java/org/zstack/billing/APIUpdateResourcePriceMsg.java |

### `org.zstack.billing.generator`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIGenerateAccountBillingMsg` | premium/billing/src/main/java/org/zstack/billing/generator/APIGenerateAccountBillingMsg.java |

### `org.zstack.billing.table`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAttachPriceTableToAccountMsg` | premium/billing/src/main/java/org/zstack/billing/table/APIAttachPriceTableToAccountMsg.java |
| 2 | `APIChangeAccountPriceTableBindingMsg` | premium/billing/src/main/java/org/zstack/billing/table/APIChangeAccountPriceTableBindingMsg.java |
| 3 | `APICreatePriceTableMsg` | premium/billing/src/main/java/org/zstack/billing/table/APICreatePriceTableMsg.java |
| 4 | `APIDeletePriceTableMsg` | premium/billing/src/main/java/org/zstack/billing/table/APIDeletePriceTableMsg.java |
| 5 | `APIDetachPriceTableFromAccountMsg` | premium/billing/src/main/java/org/zstack/billing/table/APIDetachPriceTableFromAccountMsg.java |
| 6 | `APIGetAccountPriceTableRefMsg` | premium/billing/src/main/java/org/zstack/billing/table/APIGetAccountPriceTableRefMsg.java |
| 7 | `APIQueryAccountPriceTableRefMsg` | premium/billing/src/main/java/org/zstack/billing/table/APIQueryAccountPriceTableRefMsg.java |
| 8 | `APIQueryPriceTableMsg` | premium/billing/src/main/java/org/zstack/billing/table/APIQueryPriceTableMsg.java |
| 9 | `APIUpdatePriceTableMsg` | premium/billing/src/main/java/org/zstack/billing/table/APIUpdatePriceTableMsg.java |

### `org.zstack.billing.userconfig`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIValidateDiskOfferingUserConfigMsg` | premium/billing/src/main/java/org/zstack/billing/userconfig/APIValidateDiskOfferingUserConfigMsg.java |
| 2 | `APIValidateInstanceOfferingUserConfigMsg` | premium/billing/src/main/java/org/zstack/billing/userconfig/APIValidateInstanceOfferingUserConfigMsg.java |
| 3 | `APIValidatePriceUserConfigMsg` | premium/billing/src/main/java/org/zstack/billing/userconfig/APIValidatePriceUserConfigMsg.java |

---

<a id="module-premiumcbt"></a>

## 模块：`premium/cbt`

### `org.zstack.header.cbt`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APICreateCbtTaskMsg` | premium/cbt/src/main/java/org/zstack/header/cbt/APICreateCbtTaskMsg.java |
| 2 | `APIDeleteCbtTaskMsg` | premium/cbt/src/main/java/org/zstack/header/cbt/APIDeleteCbtTaskMsg.java |
| 3 | `APIDisableCbtTaskMsg` | premium/cbt/src/main/java/org/zstack/header/cbt/APIDisableCbtTaskMsg.java |
| 4 | `APIEnableCbtTaskMsg` | premium/cbt/src/main/java/org/zstack/header/cbt/APIEnableCbtTaskMsg.java |
| 5 | `APIExportNbdVolumesMsg` | premium/cbt/src/main/java/org/zstack/header/cbt/APIExportNbdVolumesMsg.java |
| 6 | `APIQueryCbtTaskMsg` | premium/cbt/src/main/java/org/zstack/header/cbt/APIQueryCbtTaskMsg.java |
| 7 | `APIUnexportNbdVolumesMsg` | premium/cbt/src/main/java/org/zstack/header/cbt/APIUnexportNbdVolumesMsg.java |

---

<a id="module-premiumcdp"></a>

## 模块：`premium/cdp`

### `org.zstack.header.storage.cdp`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APICreateCdpPolicyMsg` | premium/cdp/src/main/java/org/zstack/header/storage/cdp/APICreateCdpPolicyMsg.java |
| 2 | `APICreateCdpTaskMsg` | premium/cdp/src/main/java/org/zstack/header/storage/cdp/APICreateCdpTaskMsg.java |
| 3 | `APICreateVmFromCdpBackupMsg` | premium/cdp/src/main/java/org/zstack/header/storage/cdp/APICreateVmFromCdpBackupMsg.java |
| 4 | `APIDeleteCdpPolicyMsg` | premium/cdp/src/main/java/org/zstack/header/storage/cdp/APIDeleteCdpPolicyMsg.java |
| 5 | `APIDeleteCdpTaskDataMsg` | premium/cdp/src/main/java/org/zstack/header/storage/cdp/APIDeleteCdpTaskDataMsg.java |
| 6 | `APIDeleteCdpTaskMsg` | premium/cdp/src/main/java/org/zstack/header/storage/cdp/APIDeleteCdpTaskMsg.java |
| 7 | `APIDescribeVmInstanceRecoveryPointMsg` | premium/cdp/src/main/java/org/zstack/header/storage/cdp/APIDescribeVmInstanceRecoveryPointMsg.java |
| 8 | `APIDisableCdpTaskMsg` | premium/cdp/src/main/java/org/zstack/header/storage/cdp/APIDisableCdpTaskMsg.java |
| 9 | `APIEnableCdpTaskMsg` | premium/cdp/src/main/java/org/zstack/header/storage/cdp/APIEnableCdpTaskMsg.java |
| 10 | `APIGetVmInstanceProtectedRecoveryPointsMsg` | premium/cdp/src/main/java/org/zstack/header/storage/cdp/APIGetVmInstanceProtectedRecoveryPointsMsg.java |
| 11 | `APIGetVmInstanceRecoveryPointsMsg` | premium/cdp/src/main/java/org/zstack/header/storage/cdp/APIGetVmInstanceRecoveryPointsMsg.java |
| 12 | `APIMergeDataOnBackupStorageMsg` | premium/cdp/src/main/java/org/zstack/header/storage/cdp/APIMergeDataOnBackupStorageMsg.java |
| 13 | `APIMountVmInstanceRecoveryPointMsg` | premium/cdp/src/main/java/org/zstack/header/storage/cdp/APIMountVmInstanceRecoveryPointMsg.java |
| 14 | `APIProtectVmInstanceRecoveryPointMsg` | premium/cdp/src/main/java/org/zstack/header/storage/cdp/APIProtectVmInstanceRecoveryPointMsg.java |
| 15 | `APIQueryCdpPolicyMsg` | premium/cdp/src/main/java/org/zstack/header/storage/cdp/APIQueryCdpPolicyMsg.java |
| 16 | `APIQueryCdpTaskMsg` | premium/cdp/src/main/java/org/zstack/header/storage/cdp/APIQueryCdpTaskMsg.java |
| 17 | `APIRevertVmFromCdpBackupMsg` | premium/cdp/src/main/java/org/zstack/header/storage/cdp/APIRevertVmFromCdpBackupMsg.java |
| 18 | `APIUnmountVmInstanceRecoveryPointMsg` | premium/cdp/src/main/java/org/zstack/header/storage/cdp/APIUnmountVmInstanceRecoveryPointMsg.java |
| 19 | `APIUnprotectVmInstanceRecoveryPointMsg` | premium/cdp/src/main/java/org/zstack/header/storage/cdp/APIUnprotectVmInstanceRecoveryPointMsg.java |
| 20 | `APIUpdateCdpPolicyMsg` | premium/cdp/src/main/java/org/zstack/header/storage/cdp/APIUpdateCdpPolicyMsg.java |
| 21 | `APIUpdateCdpTaskMsg` | premium/cdp/src/main/java/org/zstack/header/storage/cdp/APIUpdateCdpTaskMsg.java |

---

<a id="module-premiumcloudformation"></a>

## 模块：`premium/cloudformation`

### `org.zstack.header.cloudformation`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddStackTemplateMsg` | premium/cloudformation/src/main/java/org/zstack/header/cloudformation/APIAddStackTemplateMsg.java |
| 2 | `APICheckStackTemplateParametersMsg` | premium/cloudformation/src/main/java/org/zstack/header/cloudformation/APICheckStackTemplateParametersMsg.java |
| 3 | `APICreateResourceStackMsg` | premium/cloudformation/src/main/java/org/zstack/header/cloudformation/APICreateResourceStackMsg.java |
| 4 | `APIDecodeStackTemplateMsg` | premium/cloudformation/src/main/java/org/zstack/header/cloudformation/APIDecodeStackTemplateMsg.java |
| 5 | `APIDeleteResourceStackMsg` | premium/cloudformation/src/main/java/org/zstack/header/cloudformation/APIDeleteResourceStackMsg.java |
| 6 | `APIDeleteStackTemplateMsg` | premium/cloudformation/src/main/java/org/zstack/header/cloudformation/APIDeleteStackTemplateMsg.java |
| 7 | `APIGetResourceFromResourceStackMsg` | premium/cloudformation/src/main/java/org/zstack/header/cloudformation/APIGetResourceFromResourceStackMsg.java |
| 8 | `APIGetResourceStackFromResourceMsg` | premium/cloudformation/src/main/java/org/zstack/header/cloudformation/APIGetResourceStackFromResourceMsg.java |
| 9 | `APIGetSupportedCloudFormationResourcesMsg` | premium/cloudformation/src/main/java/org/zstack/header/cloudformation/APIGetSupportedCloudFormationResourcesMsg.java |
| 10 | `APIPreviewResourceStackMsg` | premium/cloudformation/src/main/java/org/zstack/header/cloudformation/APIPreviewResourceStackMsg.java |
| 11 | `APIQueryEventFromResourceStackMsg` | premium/cloudformation/src/main/java/org/zstack/header/cloudformation/APIQueryEventFromResourceStackMsg.java |
| 12 | `APIQueryResourceStackMsg` | premium/cloudformation/src/main/java/org/zstack/header/cloudformation/APIQueryResourceStackMsg.java |
| 13 | `APIQueryStackTemplateMsg` | premium/cloudformation/src/main/java/org/zstack/header/cloudformation/APIQueryStackTemplateMsg.java |
| 14 | `APIRestartResourceStackMsg` | premium/cloudformation/src/main/java/org/zstack/header/cloudformation/APIRestartResourceStackMsg.java |
| 15 | `APIUpdateResourceStackMsg` | premium/cloudformation/src/main/java/org/zstack/header/cloudformation/APIUpdateResourceStackMsg.java |
| 16 | `APIUpdateStackTemplateMsg` | premium/cloudformation/src/main/java/org/zstack/header/cloudformation/APIUpdateStackTemplateMsg.java |

### `org.zstack.header.cloudformation.monitor`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddResourceStackVmPortMonitorMsg` | premium/cloudformation/src/main/java/org/zstack/header/cloudformation/monitor/APIAddResourceStackVmPortMonitorMsg.java |
| 2 | `APIDeleteResourceStackVmPortMonitorMsg` | premium/cloudformation/src/main/java/org/zstack/header/cloudformation/monitor/APIDeleteResourceStackVmPortMonitorMsg.java |
| 3 | `APIGetResourceStackVmStatusMsg` | premium/cloudformation/src/main/java/org/zstack/header/cloudformation/monitor/APIGetResourceStackVmStatusMsg.java |

---

<a id="module-premiumcrypto"></a>

## 模块：`premium/crypto`

### `org.zstack.crypto.ccs`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddCCSCertificateMsg` | premium/crypto/src/main/java/org/zstack/crypto/ccs/APIAddCCSCertificateMsg.java |
| 2 | `APIAttachCCSCertificateToAccountMsg` | premium/crypto/src/main/java/org/zstack/crypto/ccs/APIAttachCCSCertificateToAccountMsg.java |
| 3 | `APIDeleteCCSCertificateMsg` | premium/crypto/src/main/java/org/zstack/crypto/ccs/APIDeleteCCSCertificateMsg.java |
| 4 | `APIDetachCCSCertificateFromAccountMsg` | premium/crypto/src/main/java/org/zstack/crypto/ccs/APIDetachCCSCertificateFromAccountMsg.java |
| 5 | `APIQueryCCSCertificateMsg` | premium/crypto/src/main/java/org/zstack/crypto/ccs/APIQueryCCSCertificateMsg.java |
| 6 | `APIUpdateCCSCertificateAccountStateMsg` | premium/crypto/src/main/java/org/zstack/crypto/ccs/APIUpdateCCSCertificateAccountStateMsg.java |

### `org.zstack.crypto.datacrypto.api`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddIntegrityResourceMsg` | premium/crypto/src/main/java/org/zstack/crypto/datacrypto/api/APIAddIntegrityResourceMsg.java |
| 2 | `APICheckBatchDataIntegrityMsg` | premium/crypto/src/main/java/org/zstack/crypto/datacrypto/api/APICheckBatchDataIntegrityMsg.java |
| 3 | `APIStartDataProtectionMsg` | premium/crypto/src/main/java/org/zstack/crypto/datacrypto/api/APIStartDataProtectionMsg.java |

### `org.zstack.crypto.securitymachine.api.secretresourcepool`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIChangeSecretResourcePoolStateMsg` | premium/crypto/src/main/java/org/zstack/crypto/securitymachine/api/secretresourcepool/APIChangeSecretResourcePoolStateMsg.java |
| 2 | `APICreateAiSiNoSecretResourcePoolMsg` | premium/crypto/src/main/java/org/zstack/crypto/securitymachine/api/secretresourcepool/APICreateAiSiNoSecretResourcePoolMsg.java |
| 3 | `APICreateFlkSecSecretResourcePoolMsg` | premium/crypto/src/main/java/org/zstack/crypto/securitymachine/api/secretresourcepool/APICreateFlkSecSecretResourcePoolMsg.java |
| 4 | `APICreateHaiTaiSecretResourcePoolMsg` | premium/crypto/src/main/java/org/zstack/crypto/securitymachine/api/secretresourcepool/APICreateHaiTaiSecretResourcePoolMsg.java |
| 5 | `APICreateInfoSecSecretResourcePoolMsg` | premium/crypto/src/main/java/org/zstack/crypto/securitymachine/api/secretresourcepool/APICreateInfoSecSecretResourcePoolMsg.java |
| 6 | `APICreateSecretResourcePoolMsg` | premium/crypto/src/main/java/org/zstack/crypto/securitymachine/api/secretresourcepool/APICreateSecretResourcePoolMsg.java |
| 7 | `APIDeleteSecretResourcePoolMsg` | premium/crypto/src/main/java/org/zstack/crypto/securitymachine/api/secretresourcepool/APIDeleteSecretResourcePoolMsg.java |
| 8 | `APIGetSignatureServerEncryptPublicKeyMsg` | premium/crypto/src/main/java/org/zstack/crypto/securitymachine/api/secretresourcepool/APIGetSignatureServerEncryptPublicKeyMsg.java |
| 9 | `APIQuerySecretResourcePoolMsg` | premium/crypto/src/main/java/org/zstack/crypto/securitymachine/api/secretresourcepool/APIQuerySecretResourcePoolMsg.java |
| 10 | `APIUpdateFlkSecSecretResourcePoolMsg` | premium/crypto/src/main/java/org/zstack/crypto/securitymachine/api/secretresourcepool/APIUpdateFlkSecSecretResourcePoolMsg.java |
| 11 | `APIUpdateInfoSecSecretResourcePoolMsg` | premium/crypto/src/main/java/org/zstack/crypto/securitymachine/api/secretresourcepool/APIUpdateInfoSecSecretResourcePoolMsg.java |
| 12 | `APIUpdateSecretResourcePoolMsg` | premium/crypto/src/main/java/org/zstack/crypto/securitymachine/api/secretresourcepool/APIUpdateSecretResourcePoolMsg.java |

### `org.zstack.crypto.securitymachine.api.securitymachine`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddFlkSecSecurityMachineMsg` | premium/crypto/src/main/java/org/zstack/crypto/securitymachine/api/securitymachine/APIAddFlkSecSecurityMachineMsg.java |
| 2 | `APIAddInfoSecSecurityMachineMsg` | premium/crypto/src/main/java/org/zstack/crypto/securitymachine/api/securitymachine/APIAddInfoSecSecurityMachineMsg.java |
| 3 | `APIAddSecurityMachineMsg` | premium/crypto/src/main/java/org/zstack/crypto/securitymachine/api/securitymachine/APIAddSecurityMachineMsg.java |
| 4 | `APIChangeSecurityMachineStateMsg` | premium/crypto/src/main/java/org/zstack/crypto/securitymachine/api/securitymachine/APIChangeSecurityMachineStateMsg.java |
| 5 | `APIDeleteSecurityMachineMsg` | premium/crypto/src/main/java/org/zstack/crypto/securitymachine/api/securitymachine/APIDeleteSecurityMachineMsg.java |
| 6 | `APIQuerySecurityMachineMsg` | premium/crypto/src/main/java/org/zstack/crypto/securitymachine/api/securitymachine/APIQuerySecurityMachineMsg.java |
| 7 | `APISecurityMachineDetectSyncMsg` | premium/crypto/src/main/java/org/zstack/crypto/securitymachine/api/securitymachine/APISecurityMachineDetectSyncMsg.java |
| 8 | `APISetSecurityMachineKeyMsg` | premium/crypto/src/main/java/org/zstack/crypto/securitymachine/api/securitymachine/APISetSecurityMachineKeyMsg.java |
| 9 | `APIUpdateFlkSecSecurityMachineMsg` | premium/crypto/src/main/java/org/zstack/crypto/securitymachine/api/securitymachine/APIUpdateFlkSecSecurityMachineMsg.java |
| 10 | `APIUpdateInfoSecSecurityMachineMsg` | premium/crypto/src/main/java/org/zstack/crypto/securitymachine/api/securitymachine/APIUpdateInfoSecSecurityMachineMsg.java |
| 11 | `APIUpdateSecurityMachineMsg` | premium/crypto/src/main/java/org/zstack/crypto/securitymachine/api/securitymachine/APIUpdateSecurityMachineMsg.java |

---

<a id="module-premiumdrs"></a>

## 模块：`premium/drs`

### `org.zstack.drs.api`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIApplyDRSAdviceMsg` | premium/drs/src/main/java/org/zstack/drs/api/APIApplyDRSAdviceMsg.java |
| 2 | `APICreateClusterDRSMsg` | premium/drs/src/main/java/org/zstack/drs/api/APICreateClusterDRSMsg.java |
| 3 | `APIDeleteClusterDRSMsg` | premium/drs/src/main/java/org/zstack/drs/api/APIDeleteClusterDRSMsg.java |
| 4 | `APIExecuteDRSSchedulingMsg` | premium/drs/src/main/java/org/zstack/drs/api/APIExecuteDRSSchedulingMsg.java |
| 5 | `APIGetClusterDRSStatusMsg` | premium/drs/src/main/java/org/zstack/drs/api/APIGetClusterDRSStatusMsg.java |
| 6 | `APIQueryClusterDRSMsg` | premium/drs/src/main/java/org/zstack/drs/api/APIQueryClusterDRSMsg.java |
| 7 | `APIQueryDRSAdviceMsg` | premium/drs/src/main/java/org/zstack/drs/api/APIQueryDRSAdviceMsg.java |
| 8 | `APIQueryDRSVmMigrationActivityMsg` | premium/drs/src/main/java/org/zstack/drs/api/APIQueryDRSVmMigrationActivityMsg.java |
| 9 | `APIUpdateClusterDRSMsg` | premium/drs/src/main/java/org/zstack/drs/api/APIUpdateClusterDRSMsg.java |
| 10 | `APIValidateClusterSupportDRSMsg` | premium/drs/src/main/java/org/zstack/drs/api/APIValidateClusterSupportDRSMsg.java |

---

<a id="module-premiumexternalbackup"></a>

## 模块：`premium/externalbackup`

### `org.zstack.externalbackup`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APICreateExternalBackupMsg` | premium/externalbackup/src/main/java/org/zstack/externalbackup/APICreateExternalBackupMsg.java |
| 2 | `APIDeleteExternalBackupMsg` | premium/externalbackup/src/main/java/org/zstack/externalbackup/APIDeleteExternalBackupMsg.java |
| 3 | `APIGetExternalBackupDetailsMsg` | premium/externalbackup/src/main/java/org/zstack/externalbackup/APIGetExternalBackupDetailsMsg.java |
| 4 | `APIQueryExternalBackupMsg` | premium/externalbackup/src/main/java/org/zstack/externalbackup/APIQueryExternalBackupMsg.java |

---

<a id="module-premiumfaulttolerance"></a>

## 模块：`premium/faulttolerance`

### `org.zstack.faulttolerance.api`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APICreateFaultToleranceVmInstanceMsg` | premium/faulttolerance/src/main/java/org/zstack/faulttolerance/api/APICreateFaultToleranceVmInstanceMsg.java |
| 2 | `APIFailoverFaultToleranceVmMsg` | premium/faulttolerance/src/main/java/org/zstack/faulttolerance/api/APIFailoverFaultToleranceVmMsg.java |
| 3 | `APIGetFaultToleranceVmsMsg` | premium/faulttolerance/src/main/java/org/zstack/faulttolerance/api/APIGetFaultToleranceVmsMsg.java |
| 4 | `APIQueryFaultToleranceVmMsg` | premium/faulttolerance/src/main/java/org/zstack/faulttolerance/api/APIQueryFaultToleranceVmMsg.java |

---

<a id="module-premiumguesttools"></a>

## 模块：`premium/guesttools`

### `org.zstack.guesttools`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAttachGuestToolsIsoToVmMsg` | premium/guesttools/src/main/java/org/zstack/guesttools/APIAttachGuestToolsIsoToVmMsg.java |
| 2 | `APIDetachGuestToolsIsoFromVmMsg` | premium/guesttools/src/main/java/org/zstack/guesttools/APIDetachGuestToolsIsoFromVmMsg.java |
| 3 | `APIGetLatestGuestToolsForVmMsg` | premium/guesttools/src/main/java/org/zstack/guesttools/APIGetLatestGuestToolsForVmMsg.java |
| 4 | `APIGetVmGuestToolsInfoMsg` | premium/guesttools/src/main/java/org/zstack/guesttools/APIGetVmGuestToolsInfoMsg.java |
| 5 | `APIQueryGuestToolsStateMsg` | premium/guesttools/src/main/java/org/zstack/guesttools/APIQueryGuestToolsStateMsg.java |
| 6 | `APIUpdateGuestToolsStateMsg` | premium/guesttools/src/main/java/org/zstack/guesttools/APIUpdateGuestToolsStateMsg.java |
| 7 | `APIUpdateVmNetworkConfigMsg` | premium/guesttools/src/main/java/org/zstack/guesttools/APIUpdateVmNetworkConfigMsg.java |

### `org.zstack.guesttools.advanced`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APICreateVmCustomSpecificationMsg` | premium/guesttools/src/main/java/org/zstack/guesttools/advanced/APICreateVmCustomSpecificationMsg.java |
| 2 | `APIDeleteVmCustomSpecificationMsg` | premium/guesttools/src/main/java/org/zstack/guesttools/advanced/APIDeleteVmCustomSpecificationMsg.java |
| 3 | `APIQueryVmCustomSpecificationMsg` | premium/guesttools/src/main/java/org/zstack/guesttools/advanced/APIQueryVmCustomSpecificationMsg.java |
| 4 | `APIUpdateVmCustomSpecificationMsg` | premium/guesttools/src/main/java/org/zstack/guesttools/advanced/APIUpdateVmCustomSpecificationMsg.java |

---

<a id="module-premiumhybrid"></a>

## 模块：`premium/hybrid`

### `org.zstack.header.aliyun.account`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddAliyunKeySecretMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/account/APIAddAliyunKeySecretMsg.java |
| 2 | `APIAttachAliyunKeyMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/account/APIAttachAliyunKeyMsg.java |
| 3 | `APIDeleteAliyunKeySecretMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/account/APIDeleteAliyunKeySecretMsg.java |
| 4 | `APIDetachAliyunKeyMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/account/APIDetachAliyunKeyMsg.java |
| 5 | `APIUpdateAliyunKeySecretMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/account/APIUpdateAliyunKeySecretMsg.java |

### `org.zstack.header.aliyun.ecs`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APICloneEcsInstanceFromLocalVmMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/ecs/APICloneEcsInstanceFromLocalVmMsg.java |
| 2 | `APICreateEcsInstanceFromEcsImageMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/ecs/APICreateEcsInstanceFromEcsImageMsg.java |
| 3 | `APIDeleteAllEcsInstancesFromDataCenterMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/ecs/APIDeleteAllEcsInstancesFromDataCenterMsg.java |
| 4 | `APIDeleteEcsInstanceLocalMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/ecs/APIDeleteEcsInstanceLocalMsg.java |
| 5 | `APIDeleteEcsInstanceMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/ecs/APIDeleteEcsInstanceMsg.java |
| 6 | `APIGetEcsInstanceTypeMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/ecs/APIGetEcsInstanceTypeMsg.java |
| 7 | `APIGetEcsInstanceVncUrlMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/ecs/APIGetEcsInstanceVncUrlMsg.java |
| 8 | `APIQueryEcsInstanceFromLocalMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/ecs/APIQueryEcsInstanceFromLocalMsg.java |
| 9 | `APIRebootEcsInstanceMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/ecs/APIRebootEcsInstanceMsg.java |
| 10 | `APIStartEcsInstanceMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/ecs/APIStartEcsInstanceMsg.java |
| 11 | `APIStopEcsInstanceMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/ecs/APIStopEcsInstanceMsg.java |
| 12 | `APISyncEcsInstanceFromRemoteMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/ecs/APISyncEcsInstanceFromRemoteMsg.java |
| 13 | `APIUpdateEcsInstanceMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/ecs/APIUpdateEcsInstanceMsg.java |
| 14 | `APIUpdateEcsInstanceVncPasswordMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/ecs/APIUpdateEcsInstanceVncPasswordMsg.java |

### `org.zstack.header.aliyun.image`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APICreateEcsImageFromEcsSnapshotMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/image/APICreateEcsImageFromEcsSnapshotMsg.java |
| 2 | `APICreateEcsImageFromLocalImageMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/image/APICreateEcsImageFromLocalImageMsg.java |
| 3 | `APIDeleteEcsImageLocalMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/image/APIDeleteEcsImageLocalMsg.java |
| 4 | `APIDeleteEcsImageRemoteMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/image/APIDeleteEcsImageRemoteMsg.java |
| 5 | `APIGetCreateEcsImageProgressMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/image/APIGetCreateEcsImageProgressMsg.java |
| 6 | `APIQueryEcsImageFromLocalMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/image/APIQueryEcsImageFromLocalMsg.java |
| 7 | `APISyncEcsImageFromRemoteMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/image/APISyncEcsImageFromRemoteMsg.java |
| 8 | `APIUpdateEcsImageMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/image/APIUpdateEcsImageMsg.java |

### `org.zstack.header.aliyun.network.connection`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddConnectionAccessPointFromRemoteMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/network/connection/APIAddConnectionAccessPointFromRemoteMsg.java |
| 2 | `APICreateAliyunRouterInterfaceRemoteMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/network/connection/APICreateAliyunRouterInterfaceRemoteMsg.java |
| 3 | `APICreateConnectionBetweenL3NetworkAndAliyunVSwitchMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/network/connection/APICreateConnectionBetweenL3NetworkAndAliyunVSwitchMsg.java |
| 4 | `APIDeleteAliyunRouterInterfaceLocalMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/network/connection/APIDeleteAliyunRouterInterfaceLocalMsg.java |
| 5 | `APIDeleteAliyunRouterInterfaceRemoteMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/network/connection/APIDeleteAliyunRouterInterfaceRemoteMsg.java |
| 6 | `APIDeleteConnectionAccessPointLocalMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/network/connection/APIDeleteConnectionAccessPointLocalMsg.java |
| 7 | `APIDeleteConnectionBetweenL3NetWorkAndAliyunVSwitchMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/network/connection/APIDeleteConnectionBetweenL3NetWorkAndAliyunVSwitchMsg.java |
| 8 | `APIDeleteVirtualBorderRouterLocalMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/network/connection/APIDeleteVirtualBorderRouterLocalMsg.java |
| 9 | `APIGetConnectionAccessPointFromRemoteMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/network/connection/APIGetConnectionAccessPointFromRemoteMsg.java |
| 10 | `APIGetConnectionBetweenL3NetworkAndAliyunVSwitchMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/network/connection/APIGetConnectionBetweenL3NetworkAndAliyunVSwitchMsg.java |
| 11 | `APIQueryAliyunRouterInterfaceFromLocalMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/network/connection/APIQueryAliyunRouterInterfaceFromLocalMsg.java |
| 12 | `APIQueryConnectionAccessPointFromLocalMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/network/connection/APIQueryConnectionAccessPointFromLocalMsg.java |
| 13 | `APIQueryConnectionBetweenL3NetworkAndAliyunVSwitchMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/network/connection/APIQueryConnectionBetweenL3NetworkAndAliyunVSwitchMsg.java |
| 14 | `APIQueryVirtualBorderRouterFromLocalMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/network/connection/APIQueryVirtualBorderRouterFromLocalMsg.java |
| 15 | `APIRecoveryVirtualBorderRouterRemoteMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/network/connection/APIRecoveryVirtualBorderRouterRemoteMsg.java |
| 16 | `APIStartConnectionBetweenAliyunRouterInterfaceMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/network/connection/APIStartConnectionBetweenAliyunRouterInterfaceMsg.java |
| 17 | `APISyncAliyunRouterInterfaceFromRemoteMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/network/connection/APISyncAliyunRouterInterfaceFromRemoteMsg.java |
| 18 | `APISyncConnectionAccessPointFromRemoteMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/network/connection/APISyncConnectionAccessPointFromRemoteMsg.java |
| 19 | `APISyncVirtualBorderRouterFromRemoteMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/network/connection/APISyncVirtualBorderRouterFromRemoteMsg.java |
| 20 | `APITerminateVirtualBorderRouterRemoteMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/network/connection/APITerminateVirtualBorderRouterRemoteMsg.java |
| 21 | `APIUpdateAliyunRouteInterfaceRemoteMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/network/connection/APIUpdateAliyunRouteInterfaceRemoteMsg.java |
| 22 | `APIUpdateConnectionBetweenL3NetWorkAndAliyunVSwitchMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/network/connection/APIUpdateConnectionBetweenL3NetWorkAndAliyunVSwitchMsg.java |
| 23 | `APIUpdateVirtualBorderRouterRemoteMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/network/connection/APIUpdateVirtualBorderRouterRemoteMsg.java |

### `org.zstack.header.aliyun.network.group`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APICreateEcsSecurityGroupRemoteMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/network/group/APICreateEcsSecurityGroupRemoteMsg.java |
| 2 | `APICreateEcsSecurityGroupRuleRemoteMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/network/group/APICreateEcsSecurityGroupRuleRemoteMsg.java |
| 3 | `APIDeleteEcsSecurityGroupInLocalMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/network/group/APIDeleteEcsSecurityGroupInLocalMsg.java |
| 4 | `APIDeleteEcsSecurityGroupRemoteMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/network/group/APIDeleteEcsSecurityGroupRemoteMsg.java |
| 5 | `APIDeleteEcsSecurityGroupRuleRemoteMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/network/group/APIDeleteEcsSecurityGroupRuleRemoteMsg.java |
| 6 | `APIQueryEcsSecurityGroupFromLocalMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/network/group/APIQueryEcsSecurityGroupFromLocalMsg.java |
| 7 | `APIQueryEcsSecurityGroupRuleFromLocalMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/network/group/APIQueryEcsSecurityGroupRuleFromLocalMsg.java |
| 8 | `APISyncEcsSecurityGroupFromRemoteMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/network/group/APISyncEcsSecurityGroupFromRemoteMsg.java |
| 9 | `APISyncEcsSecurityGroupRuleFromRemoteMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/network/group/APISyncEcsSecurityGroupRuleFromRemoteMsg.java |
| 10 | `APIUpdateEcsSecurityGroupMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/network/group/APIUpdateEcsSecurityGroupMsg.java |

### `org.zstack.header.aliyun.network.vpc`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APICreateEcsVpcRemoteMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/network/vpc/APICreateEcsVpcRemoteMsg.java |
| 2 | `APICreateEcsVSwitchRemoteMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/network/vpc/APICreateEcsVSwitchRemoteMsg.java |
| 3 | `APIDeleteEcsVpcInLocalMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/network/vpc/APIDeleteEcsVpcInLocalMsg.java |
| 4 | `APIDeleteEcsVpcRemoteMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/network/vpc/APIDeleteEcsVpcRemoteMsg.java |
| 5 | `APIDeleteEcsVSwitchInLocalMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/network/vpc/APIDeleteEcsVSwitchInLocalMsg.java |
| 6 | `APIDeleteEcsVSwitchRemoteMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/network/vpc/APIDeleteEcsVSwitchRemoteMsg.java |
| 7 | `APIQueryEcsVpcFromLocalMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/network/vpc/APIQueryEcsVpcFromLocalMsg.java |
| 8 | `APIQueryEcsVSwitchFromLocalMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/network/vpc/APIQueryEcsVSwitchFromLocalMsg.java |
| 9 | `APISyncEcsVpcFromRemoteMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/network/vpc/APISyncEcsVpcFromRemoteMsg.java |
| 10 | `APISyncEcsVSwitchFromRemoteMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/network/vpc/APISyncEcsVSwitchFromRemoteMsg.java |
| 11 | `APIUpdateEcsVpcMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/network/vpc/APIUpdateEcsVpcMsg.java |
| 12 | `APIUpdateEcsVSwitchMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/network/vpc/APIUpdateEcsVSwitchMsg.java |

### `org.zstack.header.aliyun.network.vrouter`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APICreateAliyunVpcVirtualRouterEntryRemoteMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/network/vrouter/APICreateAliyunVpcVirtualRouterEntryRemoteMsg.java |
| 2 | `APIDeleteAliyunRouteEntryRemoteMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/network/vrouter/APIDeleteAliyunRouteEntryRemoteMsg.java |
| 3 | `APIDeleteVirtualRouterLocalMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/network/vrouter/APIDeleteVirtualRouterLocalMsg.java |
| 4 | `APIQueryAliyunRouteEntryFromLocalMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/network/vrouter/APIQueryAliyunRouteEntryFromLocalMsg.java |
| 5 | `APIQueryAliyunVirtualRouterFromLocalMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/network/vrouter/APIQueryAliyunVirtualRouterFromLocalMsg.java |
| 6 | `APISyncAliyunRouteEntryFromRemoteMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/network/vrouter/APISyncAliyunRouteEntryFromRemoteMsg.java |
| 7 | `APISyncAliyunVirtualRouterFromRemoteMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/network/vrouter/APISyncAliyunVirtualRouterFromRemoteMsg.java |
| 8 | `APIUpdateAliyunVirtualRouterMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/network/vrouter/APIUpdateAliyunVirtualRouterMsg.java |

### `org.zstack.header.aliyun.oss`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddOssBucketFromRemoteMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/oss/APIAddOssBucketFromRemoteMsg.java |
| 2 | `APIAttachOssBucketToEcsDataCenterMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/oss/APIAttachOssBucketToEcsDataCenterMsg.java |
| 3 | `APICreateOssBackupBucketRemoteMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/oss/APICreateOssBackupBucketRemoteMsg.java |
| 4 | `APICreateOssBucketRemoteMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/oss/APICreateOssBucketRemoteMsg.java |
| 5 | `APIDeleteOssBucketFileRemoteMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/oss/APIDeleteOssBucketFileRemoteMsg.java |
| 6 | `APIDeleteOssBucketNameLocalMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/oss/APIDeleteOssBucketNameLocalMsg.java |
| 7 | `APIDeleteOssBucketRemoteMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/oss/APIDeleteOssBucketRemoteMsg.java |
| 8 | `APIDetachOssBucketFromEcsDataCenterMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/oss/APIDetachOssBucketFromEcsDataCenterMsg.java |
| 9 | `APIGetOssBackupBucketFromRemoteMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/oss/APIGetOssBackupBucketFromRemoteMsg.java |
| 10 | `APIGetOssBucketFileFromRemoteMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/oss/APIGetOssBucketFileFromRemoteMsg.java |
| 11 | `APIGetOssBucketNameFromRemoteMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/oss/APIGetOssBucketNameFromRemoteMsg.java |
| 12 | `APIQueryOssBucketFileNameMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/oss/APIQueryOssBucketFileNameMsg.java |
| 13 | `APIUpdateOssBucketMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/oss/APIUpdateOssBucketMsg.java |

### `org.zstack.header.aliyun.storage.disk`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAttachAliyunDiskToEcsMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/storage/disk/APIAttachAliyunDiskToEcsMsg.java |
| 2 | `APICreateAliyunDiskFromRemoteMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/storage/disk/APICreateAliyunDiskFromRemoteMsg.java |
| 3 | `APIDeleteAliyunDiskFromLocalMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/storage/disk/APIDeleteAliyunDiskFromLocalMsg.java |
| 4 | `APIDeleteAliyunDiskFromRemoteMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/storage/disk/APIDeleteAliyunDiskFromRemoteMsg.java |
| 5 | `APIDetachAliyunDiskFromEcsMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/storage/disk/APIDetachAliyunDiskFromEcsMsg.java |
| 6 | `APIQueryAliyunDiskFromLocalMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/storage/disk/APIQueryAliyunDiskFromLocalMsg.java |
| 7 | `APISyncDiskFromAliyunFromRemoteMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/storage/disk/APISyncDiskFromAliyunFromRemoteMsg.java |
| 8 | `APIUpdateAliyunDiskMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/storage/disk/APIUpdateAliyunDiskMsg.java |

### `org.zstack.header.aliyun.storage.snapshot`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APICreateAliyunSnapshotRemoteMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/storage/snapshot/APICreateAliyunSnapshotRemoteMsg.java |
| 2 | `APIDeleteAliyunSnapshotFromLocalMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/storage/snapshot/APIDeleteAliyunSnapshotFromLocalMsg.java |
| 3 | `APIDeleteAliyunSnapshotFromRemoteMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/storage/snapshot/APIDeleteAliyunSnapshotFromRemoteMsg.java |
| 4 | `APIGCAliyunSnapshotRemoteMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/storage/snapshot/APIGCAliyunSnapshotRemoteMsg.java |
| 5 | `APIQueryAliyunSnapshotFromLocalMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/storage/snapshot/APIQueryAliyunSnapshotFromLocalMsg.java |
| 6 | `APISyncAliyunSnapshotRemoteMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/storage/snapshot/APISyncAliyunSnapshotRemoteMsg.java |
| 7 | `APIUpdateAliyunSnapshotMsg` | premium/hybrid/src/main/java/org/zstack/header/aliyun/storage/snapshot/APIUpdateAliyunSnapshotMsg.java |

### `org.zstack.header.datacenter`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddDataCenterFromRemoteMsg` | premium/hybrid/src/main/java/org/zstack/header/datacenter/APIAddDataCenterFromRemoteMsg.java |
| 2 | `APIDeleteDataCenterInLocalMsg` | premium/hybrid/src/main/java/org/zstack/header/datacenter/APIDeleteDataCenterInLocalMsg.java |
| 3 | `APIGetDataCenterFromRemoteMsg` | premium/hybrid/src/main/java/org/zstack/header/datacenter/APIGetDataCenterFromRemoteMsg.java |
| 4 | `APIQueryDataCenterFromLocalMsg` | premium/hybrid/src/main/java/org/zstack/header/datacenter/APIQueryDataCenterFromLocalMsg.java |
| 5 | `APISyncDataCenterFromRemoteMsg` | premium/hybrid/src/main/java/org/zstack/header/datacenter/APISyncDataCenterFromRemoteMsg.java |

### `org.zstack.header.hybrid.account`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddHybridKeySecretMsg` | premium/hybrid/src/main/java/org/zstack/header/hybrid/account/APIAddHybridKeySecretMsg.java |
| 2 | `APIAttachHybridKeyMsg` | premium/hybrid/src/main/java/org/zstack/header/hybrid/account/APIAttachHybridKeyMsg.java |
| 3 | `APIDeleteHybridKeySecretMsg` | premium/hybrid/src/main/java/org/zstack/header/hybrid/account/APIDeleteHybridKeySecretMsg.java |
| 4 | `APIDetachHybridKeyMsg` | premium/hybrid/src/main/java/org/zstack/header/hybrid/account/APIDetachHybridKeyMsg.java |
| 5 | `APIQueryHybridKeySecretMsg` | premium/hybrid/src/main/java/org/zstack/header/hybrid/account/APIQueryHybridKeySecretMsg.java |
| 6 | `APIUpdateHybridKeySecretMsg` | premium/hybrid/src/main/java/org/zstack/header/hybrid/account/APIUpdateHybridKeySecretMsg.java |

### `org.zstack.header.hybrid.backup`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIBackupDatabaseToPublicCloudMsg` | premium/hybrid/src/main/java/org/zstack/header/hybrid/backup/APIBackupDatabaseToPublicCloudMsg.java |
| 2 | `APIDeleteBackupFileInPublicMsg` | premium/hybrid/src/main/java/org/zstack/header/hybrid/backup/APIDeleteBackupFileInPublicMsg.java |
| 3 | `APIDownloadBackupFileFromPublicCloudMsg` | premium/hybrid/src/main/java/org/zstack/header/hybrid/backup/APIDownloadBackupFileFromPublicCloudMsg.java |

### `org.zstack.header.hybrid.network.eip`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAttachHybridEipToEcsMsg` | premium/hybrid/src/main/java/org/zstack/header/hybrid/network/eip/APIAttachHybridEipToEcsMsg.java |
| 2 | `APICreateHybridEipMsg` | premium/hybrid/src/main/java/org/zstack/header/hybrid/network/eip/APICreateHybridEipMsg.java |
| 3 | `APIDeleteHybridEipFromLocalMsg` | premium/hybrid/src/main/java/org/zstack/header/hybrid/network/eip/APIDeleteHybridEipFromLocalMsg.java |
| 4 | `APIDeleteHybridEipRemoteMsg` | premium/hybrid/src/main/java/org/zstack/header/hybrid/network/eip/APIDeleteHybridEipRemoteMsg.java |
| 5 | `APIDetachHybridEipFromEcsMsg` | premium/hybrid/src/main/java/org/zstack/header/hybrid/network/eip/APIDetachHybridEipFromEcsMsg.java |
| 6 | `APIQueryHybridEipFromLocalMsg` | premium/hybrid/src/main/java/org/zstack/header/hybrid/network/eip/APIQueryHybridEipFromLocalMsg.java |
| 7 | `APISyncHybridEipFromRemoteMsg` | premium/hybrid/src/main/java/org/zstack/header/hybrid/network/eip/APISyncHybridEipFromRemoteMsg.java |
| 8 | `APIUpdateHybridEipMsg` | premium/hybrid/src/main/java/org/zstack/header/hybrid/network/eip/APIUpdateHybridEipMsg.java |

### `org.zstack.header.hybrid.network.vpn`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APICreateVpcUserVpnGatewayRemoteMsg` | premium/hybrid/src/main/java/org/zstack/header/hybrid/network/vpn/APICreateVpcUserVpnGatewayRemoteMsg.java |
| 2 | `APICreateVpcVpnConnectionRemoteMsg` | premium/hybrid/src/main/java/org/zstack/header/hybrid/network/vpn/APICreateVpcVpnConnectionRemoteMsg.java |
| 3 | `APICreateVpnIkeConfigMsg` | premium/hybrid/src/main/java/org/zstack/header/hybrid/network/vpn/APICreateVpnIkeConfigMsg.java |
| 4 | `APICreateVpnIpsecConfigMsg` | premium/hybrid/src/main/java/org/zstack/header/hybrid/network/vpn/APICreateVpnIpsecConfigMsg.java |
| 5 | `APIDeleteVpcIkeConfigLocalMsg` | premium/hybrid/src/main/java/org/zstack/header/hybrid/network/vpn/APIDeleteVpcIkeConfigLocalMsg.java |
| 6 | `APIDeleteVpcIpSecConfigLocalMsg` | premium/hybrid/src/main/java/org/zstack/header/hybrid/network/vpn/APIDeleteVpcIpSecConfigLocalMsg.java |
| 7 | `APIDeleteVpcUserVpnGatewayLocalMsg` | premium/hybrid/src/main/java/org/zstack/header/hybrid/network/vpn/APIDeleteVpcUserVpnGatewayLocalMsg.java |
| 8 | `APIDeleteVpcUserVpnGatewayRemoteMsg` | premium/hybrid/src/main/java/org/zstack/header/hybrid/network/vpn/APIDeleteVpcUserVpnGatewayRemoteMsg.java |
| 9 | `APIDeleteVpcVpnConnectionLocalMsg` | premium/hybrid/src/main/java/org/zstack/header/hybrid/network/vpn/APIDeleteVpcVpnConnectionLocalMsg.java |
| 10 | `APIDeleteVpcVpnConnectionRemoteMsg` | premium/hybrid/src/main/java/org/zstack/header/hybrid/network/vpn/APIDeleteVpcVpnConnectionRemoteMsg.java |
| 11 | `APIDeleteVpcVpnGatewayLocalMsg` | premium/hybrid/src/main/java/org/zstack/header/hybrid/network/vpn/APIDeleteVpcVpnGatewayLocalMsg.java |
| 12 | `APIGetVpcVpnConfigurationFromRemoteMsg` | premium/hybrid/src/main/java/org/zstack/header/hybrid/network/vpn/APIGetVpcVpnConfigurationFromRemoteMsg.java |
| 13 | `APIQueryVpcIkeConfigFromLocalMsg` | premium/hybrid/src/main/java/org/zstack/header/hybrid/network/vpn/APIQueryVpcIkeConfigFromLocalMsg.java |
| 14 | `APIQueryVpcIpSecConfigFromLocalMsg` | premium/hybrid/src/main/java/org/zstack/header/hybrid/network/vpn/APIQueryVpcIpSecConfigFromLocalMsg.java |
| 15 | `APIQueryVpcUserVpnGatewayFromLocalMsg` | premium/hybrid/src/main/java/org/zstack/header/hybrid/network/vpn/APIQueryVpcUserVpnGatewayFromLocalMsg.java |
| 16 | `APIQueryVpcVpnConnectionFromLocalMsg` | premium/hybrid/src/main/java/org/zstack/header/hybrid/network/vpn/APIQueryVpcVpnConnectionFromLocalMsg.java |
| 17 | `APIQueryVpcVpnGatewayFromLocalMsg` | premium/hybrid/src/main/java/org/zstack/header/hybrid/network/vpn/APIQueryVpcVpnGatewayFromLocalMsg.java |
| 18 | `APISyncVpcUserVpnGatewayFromRemoteMsg` | premium/hybrid/src/main/java/org/zstack/header/hybrid/network/vpn/APISyncVpcUserVpnGatewayFromRemoteMsg.java |
| 19 | `APISyncVpcVpnConnectionFromRemoteMsg` | premium/hybrid/src/main/java/org/zstack/header/hybrid/network/vpn/APISyncVpcVpnConnectionFromRemoteMsg.java |
| 20 | `APISyncVpcVpnGatewayFromRemoteMsg` | premium/hybrid/src/main/java/org/zstack/header/hybrid/network/vpn/APISyncVpcVpnGatewayFromRemoteMsg.java |
| 21 | `APIUpdateVpcUserVpnGatewayMsg` | premium/hybrid/src/main/java/org/zstack/header/hybrid/network/vpn/APIUpdateVpcUserVpnGatewayMsg.java |
| 22 | `APIUpdateVpcVpnConnectionRemoteMsg` | premium/hybrid/src/main/java/org/zstack/header/hybrid/network/vpn/APIUpdateVpcVpnConnectionRemoteMsg.java |
| 23 | `APIUpdateVpcVpnGatewayMsg` | premium/hybrid/src/main/java/org/zstack/header/hybrid/network/vpn/APIUpdateVpcVpnGatewayMsg.java |

### `org.zstack.header.identityzone`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddIdentityZoneFromRemoteMsg` | premium/hybrid/src/main/java/org/zstack/header/identityzone/APIAddIdentityZoneFromRemoteMsg.java |
| 2 | `APIDeleteIdentityZoneInLocalMsg` | premium/hybrid/src/main/java/org/zstack/header/identityzone/APIDeleteIdentityZoneInLocalMsg.java |
| 3 | `APIGetIdentityZoneFromRemoteMsg` | premium/hybrid/src/main/java/org/zstack/header/identityzone/APIGetIdentityZoneFromRemoteMsg.java |
| 4 | `APIQueryIdentityZoneFromLocalMsg` | premium/hybrid/src/main/java/org/zstack/header/identityzone/APIQueryIdentityZoneFromLocalMsg.java |
| 5 | `APISyncIdentityFromRemoteMsg` | premium/hybrid/src/main/java/org/zstack/header/identityzone/APISyncIdentityFromRemoteMsg.java |

---

<a id="module-premiumiam1"></a>

## 模块：`premium/iam1`

### `org.zstack.iam1.api.accounts`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddAccountToGroupMsg` | premium/iam1/src/main/java/org/zstack/iam1/api/accounts/APIAddAccountToGroupMsg.java |
| 2 | `APIAttachRoleToAccountGroupMsg` | premium/iam1/src/main/java/org/zstack/iam1/api/accounts/APIAttachRoleToAccountGroupMsg.java |
| 3 | `APICreateAccountGroupMsg` | premium/iam1/src/main/java/org/zstack/iam1/api/accounts/APICreateAccountGroupMsg.java |
| 4 | `APIDeleteAccountGroupMsg` | premium/iam1/src/main/java/org/zstack/iam1/api/accounts/APIDeleteAccountGroupMsg.java |
| 5 | `APIDetachRoleFromAccountGroupMsg` | premium/iam1/src/main/java/org/zstack/iam1/api/accounts/APIDetachRoleFromAccountGroupMsg.java |
| 6 | `APIGetAccountGroupTreeMsg` | premium/iam1/src/main/java/org/zstack/iam1/api/accounts/APIGetAccountGroupTreeMsg.java |
| 7 | `APIGetResourceInAccountGroupMsg` | premium/iam1/src/main/java/org/zstack/iam1/api/accounts/APIGetResourceInAccountGroupMsg.java |
| 8 | `APIGetRolesForAccountGroupMsg` | premium/iam1/src/main/java/org/zstack/iam1/api/accounts/APIGetRolesForAccountGroupMsg.java |
| 9 | `APIMoveAccountGroupMsg` | premium/iam1/src/main/java/org/zstack/iam1/api/accounts/APIMoveAccountGroupMsg.java |
| 10 | `APIQueryAccountGroupMsg` | premium/iam1/src/main/java/org/zstack/iam1/api/accounts/APIQueryAccountGroupMsg.java |
| 11 | `APIRemoveAccountFromGroupMsg` | premium/iam1/src/main/java/org/zstack/iam1/api/accounts/APIRemoveAccountFromGroupMsg.java |
| 12 | `APIRevokeResourceSharingToGroupMsg` | premium/iam1/src/main/java/org/zstack/iam1/api/accounts/APIRevokeResourceSharingToGroupMsg.java |
| 13 | `APIShareResourceToGroupMsg` | premium/iam1/src/main/java/org/zstack/iam1/api/accounts/APIShareResourceToGroupMsg.java |
| 14 | `APIUpdateAccountGroupMsg` | premium/iam1/src/main/java/org/zstack/iam1/api/accounts/APIUpdateAccountGroupMsg.java |

### `org.zstack.iam1.api.ensemble`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIGetResourceEnsembleMembersMsg` | premium/iam1/src/main/java/org/zstack/iam1/api/ensemble/APIGetResourceEnsembleMembersMsg.java |
| 2 | `APIGetResourceSharingMsg` | premium/iam1/src/main/java/org/zstack/iam1/api/ensemble/APIGetResourceSharingMsg.java |

---

<a id="module-premiumiam2"></a>

## 模块：`premium/iam2`

### `org.zstack.iam2.api`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddAttributesToIAM2OrganizationMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APIAddAttributesToIAM2OrganizationMsg.java |
| 2 | `APIAddAttributesToIAM2ProjectMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APIAddAttributesToIAM2ProjectMsg.java |
| 3 | `APIAddAttributesToIAM2VirtualIDGroupMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APIAddAttributesToIAM2VirtualIDGroupMsg.java |
| 4 | `APIAddAttributesToIAM2VirtualIDMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APIAddAttributesToIAM2VirtualIDMsg.java |
| 5 | `APIAddIAM2AttributesMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APIAddIAM2AttributesMsg.java |
| 6 | `APIAddIAM2VirtualIDGroupToProjectsMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APIAddIAM2VirtualIDGroupToProjectsMsg.java |
| 7 | `APIAddIAM2VirtualIDsToGroupMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APIAddIAM2VirtualIDsToGroupMsg.java |
| 8 | `APIAddIAM2VirtualIDsToOrganizationMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APIAddIAM2VirtualIDsToOrganizationMsg.java |
| 9 | `APIAddIAM2VirtualIDsToProjectMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APIAddIAM2VirtualIDsToProjectMsg.java |
| 10 | `APIAddIAM2VirtualIDsToProjectsMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APIAddIAM2VirtualIDsToProjectsMsg.java |
| 11 | `APIAddResourceToIAM2ProjectMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APIAddResourceToIAM2ProjectMsg.java |
| 12 | `APIAddRolesToIAM2VirtualIDGroupMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APIAddRolesToIAM2VirtualIDGroupMsg.java |
| 13 | `APIAddRolesToIAM2VirtualIDMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APIAddRolesToIAM2VirtualIDMsg.java |
| 14 | `APIAttachIAM2ProjectToIAM2OrganizationMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APIAttachIAM2ProjectToIAM2OrganizationMsg.java |
| 15 | `APIBatchCreateIAM2VirtualIDFromConfigFileMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APIBatchCreateIAM2VirtualIDFromConfigFileMsg.java |
| 16 | `APIChangeIAM2OrganizationParentMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APIChangeIAM2OrganizationParentMsg.java |
| 17 | `APIChangeIAM2OrganizationStateMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APIChangeIAM2OrganizationStateMsg.java |
| 18 | `APIChangeIAM2ProjectStateMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APIChangeIAM2ProjectStateMsg.java |
| 19 | `APIChangeIAM2VirtualIDGroupStateMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APIChangeIAM2VirtualIDGroupStateMsg.java |
| 20 | `APIChangeIAM2VirtualIDStateMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APIChangeIAM2VirtualIDStateMsg.java |
| 21 | `APIChangeIAM2VirtualIDTypeMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APIChangeIAM2VirtualIDTypeMsg.java |
| 22 | `APICheckIAM2OrganizationAvailabilityMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APICheckIAM2OrganizationAvailabilityMsg.java |
| 23 | `APICheckIAM2VirtualIDConfigFileMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APICheckIAM2VirtualIDConfigFileMsg.java |
| 24 | `APICreateIAM2OrganizationMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APICreateIAM2OrganizationMsg.java |
| 25 | `APICreateIAM2ProjectFromTemplateMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APICreateIAM2ProjectFromTemplateMsg.java |
| 26 | `APICreateIAM2ProjectMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APICreateIAM2ProjectMsg.java |
| 27 | `APICreateIAM2ProjectRoleMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APICreateIAM2ProjectRoleMsg.java |
| 28 | `APICreateIAM2ProjectTemplateFromProjectMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APICreateIAM2ProjectTemplateFromProjectMsg.java |
| 29 | `APICreateIAM2ProjectTemplateMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APICreateIAM2ProjectTemplateMsg.java |
| 30 | `APICreateIAM2VirtualIDGroupMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APICreateIAM2VirtualIDGroupMsg.java |
| 31 | `APICreateIAM2VirtualIDMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APICreateIAM2VirtualIDMsg.java |
| 32 | `APIDeleteIAM2OrganizationMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APIDeleteIAM2OrganizationMsg.java |
| 33 | `APIDeleteIAM2ProjectMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APIDeleteIAM2ProjectMsg.java |
| 34 | `APIDeleteIAM2ProjectTemplateMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APIDeleteIAM2ProjectTemplateMsg.java |
| 35 | `APIDeleteIAM2VirtualIDGroupMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APIDeleteIAM2VirtualIDGroupMsg.java |
| 36 | `APIDeleteIAM2VirtualIDMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APIDeleteIAM2VirtualIDMsg.java |
| 37 | `APIDetachIAM2ProjectFromIAM2OrganizationMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APIDetachIAM2ProjectFromIAM2OrganizationMsg.java |
| 38 | `APIExpungeIAM2ProjectMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APIExpungeIAM2ProjectMsg.java |
| 39 | `APIGetIAM2OrganizationVirtualIDNumberMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APIGetIAM2OrganizationVirtualIDNumberMsg.java |
| 40 | `APIGetIAM2ProjectsOfVirtualIDMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APIGetIAM2ProjectsOfVirtualIDMsg.java |
| 41 | `APIGetIAM2SystemAttributesMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APIGetIAM2SystemAttributesMsg.java |
| 42 | `APIGetIAM2VirtualIDAPIPermissionMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APIGetIAM2VirtualIDAPIPermissionMsg.java |
| 43 | `APIGetIAM2VirtualIDInGroupMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APIGetIAM2VirtualIDInGroupMsg.java |
| 44 | `APIGetOrganizationQuotaUsageMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APIGetOrganizationQuotaUsageMsg.java |
| 45 | `APILoginIAM2PlatformMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APILoginIAM2PlatformMsg.java |
| 46 | `APILoginIAM2ProjectMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APILoginIAM2ProjectMsg.java |
| 47 | `APILoginIAM2VirtualIDMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APILoginIAM2VirtualIDMsg.java |
| 48 | `APIQueryIAM2OrganizationAttributeMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APIQueryIAM2OrganizationAttributeMsg.java |
| 49 | `APIQueryIAM2OrganizationMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APIQueryIAM2OrganizationMsg.java |
| 50 | `APIQueryIAM2OrganizationProjectRefMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APIQueryIAM2OrganizationProjectRefMsg.java |
| 51 | `APIQueryIAM2ProjectAccountRefMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APIQueryIAM2ProjectAccountRefMsg.java |
| 52 | `APIQueryIAM2ProjectAttributeMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APIQueryIAM2ProjectAttributeMsg.java |
| 53 | `APIQueryIAM2ProjectMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APIQueryIAM2ProjectMsg.java |
| 54 | `APIQueryIAM2ProjectRoleMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APIQueryIAM2ProjectRoleMsg.java |
| 55 | `APIQueryIAM2ProjectTemplateMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APIQueryIAM2ProjectTemplateMsg.java |
| 56 | `APIQueryIAM2VirtualIDAttributeMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APIQueryIAM2VirtualIDAttributeMsg.java |
| 57 | `APIQueryIAM2VirtualIDGroupAttributeMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APIQueryIAM2VirtualIDGroupAttributeMsg.java |
| 58 | `APIQueryIAM2VirtualIDGroupMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APIQueryIAM2VirtualIDGroupMsg.java |
| 59 | `APIQueryIAM2VirtualIDMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APIQueryIAM2VirtualIDMsg.java |
| 60 | `APIRecoverIAM2ProjectMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APIRecoverIAM2ProjectMsg.java |
| 61 | `APIRemoveAttributesFromIAM2OrganizationMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APIRemoveAttributesFromIAM2OrganizationMsg.java |
| 62 | `APIRemoveAttributesFromIAM2ProjectMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APIRemoveAttributesFromIAM2ProjectMsg.java |
| 63 | `APIRemoveAttributesFromIAM2VirtualIDGroupMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APIRemoveAttributesFromIAM2VirtualIDGroupMsg.java |
| 64 | `APIRemoveAttributesFromIAM2VirtualIDMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APIRemoveAttributesFromIAM2VirtualIDMsg.java |
| 65 | `APIRemoveIAM2ProjectLoginExpiredMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APIRemoveIAM2ProjectLoginExpiredMsg.java |
| 66 | `APIRemoveIAM2VirtualIDGroupFromProjectsMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APIRemoveIAM2VirtualIDGroupFromProjectsMsg.java |
| 67 | `APIRemoveIAM2VirtualIDsFromGroupMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APIRemoveIAM2VirtualIDsFromGroupMsg.java |
| 68 | `APIRemoveIAM2VirtualIDsFromOrganizationMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APIRemoveIAM2VirtualIDsFromOrganizationMsg.java |
| 69 | `APIRemoveIAM2VirtualIDsFromProjectMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APIRemoveIAM2VirtualIDsFromProjectMsg.java |
| 70 | `APIRemoveIAM2VirtualIDsFromProjectsMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APIRemoveIAM2VirtualIDsFromProjectsMsg.java |
| 71 | `APIRemoveRolesFromIAM2VirtualIDGroupMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APIRemoveRolesFromIAM2VirtualIDGroupMsg.java |
| 72 | `APIRemoveRolesFromIAM2VirtualIDMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APIRemoveRolesFromIAM2VirtualIDMsg.java |
| 73 | `APISetIAM2ProjectLoginExpiredMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APISetIAM2ProjectLoginExpiredMsg.java |
| 74 | `APISetIAM2ProjectRetirePolicyMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APISetIAM2ProjectRetirePolicyMsg.java |
| 75 | `APISetOrganizationOperationMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APISetOrganizationOperationMsg.java |
| 76 | `APISetOrganizationSupervisorMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APISetOrganizationSupervisorMsg.java |
| 77 | `APIStopAllResourcesInIAM2ProjectMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APIStopAllResourcesInIAM2ProjectMsg.java |
| 78 | `APIUpdateIAM2OrganizationAttributeMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APIUpdateIAM2OrganizationAttributeMsg.java |
| 79 | `APIUpdateIAM2OrganizationMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APIUpdateIAM2OrganizationMsg.java |
| 80 | `APIUpdateIAM2ProjectAttributeMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APIUpdateIAM2ProjectAttributeMsg.java |
| 81 | `APIUpdateIAM2ProjectMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APIUpdateIAM2ProjectMsg.java |
| 82 | `APIUpdateIAM2ProjectTemplateMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APIUpdateIAM2ProjectTemplateMsg.java |
| 83 | `APIUpdateIAM2VirtualIDAttributeMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APIUpdateIAM2VirtualIDAttributeMsg.java |
| 84 | `APIUpdateIAM2VirtualIDGroupAttributeMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APIUpdateIAM2VirtualIDGroupAttributeMsg.java |
| 85 | `APIUpdateIAM2VirtualIDGroupMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APIUpdateIAM2VirtualIDGroupMsg.java |
| 86 | `APIUpdateIAM2VirtualIDMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APIUpdateIAM2VirtualIDMsg.java |
| 87 | `APIUpdateOrganizationQuotaMsg` | premium/iam2/src/main/java/org/zstack/iam2/api/APIUpdateOrganizationQuotaMsg.java |

---

<a id="module-premiumimagereplicator"></a>

## 模块：`premium/imagereplicator`

### `org.zstack.imagereplicator`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddBackupStoragesToReplicationGroupMsg` | premium/imagereplicator/src/main/java/org/zstack/imagereplicator/APIAddBackupStoragesToReplicationGroupMsg.java |
| 2 | `APICreateImageReplicationGroupMsg` | premium/imagereplicator/src/main/java/org/zstack/imagereplicator/APICreateImageReplicationGroupMsg.java |
| 3 | `APIDeleteImageReplicationGroupMsg` | premium/imagereplicator/src/main/java/org/zstack/imagereplicator/APIDeleteImageReplicationGroupMsg.java |
| 4 | `APIQueryImageReplicationGroupMsg` | premium/imagereplicator/src/main/java/org/zstack/imagereplicator/APIQueryImageReplicationGroupMsg.java |

---

<a id="module-premiumlog"></a>

## 模块：`premium/log`

### `org.zstack.log`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddLogConfigurationMsg` | premium/log/src/main/java/org/zstack/log/APIAddLogConfigurationMsg.java |
| 2 | `APIDeleteLogConfigurationMsg` | premium/log/src/main/java/org/zstack/log/APIDeleteLogConfigurationMsg.java |
| 3 | `APIGetLogConfigurationMsg` | premium/log/src/main/java/org/zstack/log/APIGetLogConfigurationMsg.java |
| 4 | `APIUpdateLogConfigurationMsg` | premium/log/src/main/java/org/zstack/log/APIUpdateLogConfigurationMsg.java |

---

<a id="module-premiumlogincontrol"></a>

## 模块：`premium/loginControl`

### `org.zstack.loginControl.api`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddAccessControlRuleMsg` | premium/loginControl/src/main/java/org/zstack/loginControl/api/APIAddAccessControlRuleMsg.java |
| 2 | `APIDeleteAccessControlRuleMsg` | premium/loginControl/src/main/java/org/zstack/loginControl/api/APIDeleteAccessControlRuleMsg.java |
| 3 | `APIGetLoginCaptchaMsg` | premium/loginControl/src/main/java/org/zstack/loginControl/api/APIGetLoginCaptchaMsg.java |
| 4 | `APIQueryAccessControlRuleMsg` | premium/loginControl/src/main/java/org/zstack/loginControl/api/APIQueryAccessControlRuleMsg.java |
| 5 | `APIUnlockIdentityMsg` | premium/loginControl/src/main/java/org/zstack/loginControl/api/APIUnlockIdentityMsg.java |
| 6 | `APIUpdateAccessControlRuleMsg` | premium/loginControl/src/main/java/org/zstack/loginControl/api/APIUpdateAccessControlRuleMsg.java |
| 7 | `APIValidatePasswordMsg` | premium/loginControl/src/main/java/org/zstack/loginControl/api/APIValidatePasswordMsg.java |

---

<a id="module-premiummanagements"></a>

## 模块：`premium/managements`

### `org.zstack.managements.api.common`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIGetManagementNodesStatusMsg` | premium/managements/src/main/java/org/zstack/managements/api/common/APIGetManagementNodesStatusMsg.java |

### `org.zstack.managements.api.ha2`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIGetZSha2StatusMsg` | premium/managements/src/main/java/org/zstack/managements/api/ha2/APIGetZSha2StatusMsg.java |
| 2 | `APIZSha2DemoteMsg` | premium/managements/src/main/java/org/zstack/managements/api/ha2/APIZSha2DemoteMsg.java |

---

<a id="module-premiummevoco"></a>

## 模块：`premium/mevoco`

### `org.zstack.ha`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIDeleteVmInstanceHaLevelMsg` | premium/mevoco/src/main/java/org/zstack/ha/APIDeleteVmInstanceHaLevelMsg.java |
| 2 | `APIGetVmInstanceHaLevelMsg` | premium/mevoco/src/main/java/org/zstack/ha/APIGetVmInstanceHaLevelMsg.java |
| 3 | `APISetVmInstanceHaLevelMsg` | premium/mevoco/src/main/java/org/zstack/ha/APISetVmInstanceHaLevelMsg.java |
| 4 | `APIUpdateHaStrategyConditionMsg` | premium/mevoco/src/main/java/org/zstack/ha/APIUpdateHaStrategyConditionMsg.java |

### `org.zstack.header.affinitygroup`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddVmToAffinityGroupMsg` | premium/mevoco/src/main/java/org/zstack/header/affinitygroup/APIAddVmToAffinityGroupMsg.java |
| 2 | `APIChangeAffinityGroupStateMsg` | premium/mevoco/src/main/java/org/zstack/header/affinitygroup/APIChangeAffinityGroupStateMsg.java |
| 3 | `APICreateAffinityGroupMsg` | premium/mevoco/src/main/java/org/zstack/header/affinitygroup/APICreateAffinityGroupMsg.java |
| 4 | `APIDeleteAffinityGroupMsg` | premium/mevoco/src/main/java/org/zstack/header/affinitygroup/APIDeleteAffinityGroupMsg.java |
| 5 | `APIGetCandidateAffinityGroupForAttachingVmMsg` | premium/mevoco/src/main/java/org/zstack/header/affinitygroup/APIGetCandidateAffinityGroupForAttachingVmMsg.java |
| 6 | `APIGetCandidateAffinityGroupForCreatingVmMsg` | premium/mevoco/src/main/java/org/zstack/header/affinitygroup/APIGetCandidateAffinityGroupForCreatingVmMsg.java |
| 7 | `APIGetCandidateVMForAttachingAffinityGroupMsg` | premium/mevoco/src/main/java/org/zstack/header/affinitygroup/APIGetCandidateVMForAttachingAffinityGroupMsg.java |
| 8 | `APIQueryAffinityGroupMsg` | premium/mevoco/src/main/java/org/zstack/header/affinitygroup/APIQueryAffinityGroupMsg.java |
| 9 | `APIRemoveVmFromAffinityGroupMsg` | premium/mevoco/src/main/java/org/zstack/header/affinitygroup/APIRemoveVmFromAffinityGroupMsg.java |
| 10 | `APIUpdateAffinityGroupMsg` | premium/mevoco/src/main/java/org/zstack/header/affinitygroup/APIUpdateAffinityGroupMsg.java |

### `org.zstack.header.bonding`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAttachNicToBondingMsg` | premium/mevoco/src/main/java/org/zstack/header/bonding/APIAttachNicToBondingMsg.java |
| 2 | `APICreateBondingMsg` | premium/mevoco/src/main/java/org/zstack/header/bonding/APICreateBondingMsg.java |
| 3 | `APIDeleteBondingMsg` | premium/mevoco/src/main/java/org/zstack/header/bonding/APIDeleteBondingMsg.java |
| 4 | `APIDetachNicFromBondingMsg` | premium/mevoco/src/main/java/org/zstack/header/bonding/APIDetachNicFromBondingMsg.java |
| 5 | `APIUpdateBondingMsg` | premium/mevoco/src/main/java/org/zstack/header/bonding/APIUpdateBondingMsg.java |

### `org.zstack.header.bootstrap`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIBootstrapMiniHostMsg` | premium/mevoco/src/main/java/org/zstack/header/bootstrap/APIBootstrapMiniHostMsg.java |
| 2 | `APIGetCandidateMiniHostsMsg` | premium/mevoco/src/main/java/org/zstack/header/bootstrap/APIGetCandidateMiniHostsMsg.java |

### `org.zstack.header.cluster`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APICreateMiniClusterMsg` | premium/mevoco/src/main/java/org/zstack/header/cluster/APICreateMiniClusterMsg.java |

### `org.zstack.header.host`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddHostFromConfigFileMsg` | premium/mevoco/src/main/java/org/zstack/header/host/APIAddHostFromConfigFileMsg.java |
| 2 | `APIAddKVMHostFromConfigFileMsg` | premium/mevoco/src/main/java/org/zstack/header/host/APIAddKVMHostFromConfigFileMsg.java |
| 3 | `APIAllocateHostResourceMsg` | premium/mevoco/src/main/java/org/zstack/header/host/APIAllocateHostResourceMsg.java |
| 4 | `APIChangeHostPasswordMsg` | premium/mevoco/src/main/java/org/zstack/header/host/APIChangeHostPasswordMsg.java |
| 5 | `APICheckHostConfigFileMsg` | premium/mevoco/src/main/java/org/zstack/header/host/APICheckHostConfigFileMsg.java |
| 6 | `APICheckKVMHostConfigFileMsg` | premium/mevoco/src/main/java/org/zstack/header/host/APICheckKVMHostConfigFileMsg.java |
| 7 | `APIGetCandidateInterfaceVlanIdsMsg` | premium/mevoco/src/main/java/org/zstack/header/host/APIGetCandidateInterfaceVlanIdsMsg.java |
| 8 | `APIGetCandidateNetworkBondingsMsg` | premium/mevoco/src/main/java/org/zstack/header/host/APIGetCandidateNetworkBondingsMsg.java |
| 9 | `APIGetCandidateNetworkInterfacesMsg` | premium/mevoco/src/main/java/org/zstack/header/host/APIGetCandidateNetworkInterfacesMsg.java |
| 10 | `APIGetClusterHostNetworkFactsMsg` | premium/mevoco/src/main/java/org/zstack/header/host/APIGetClusterHostNetworkFactsMsg.java |
| 11 | `APIGetHostNetworkFactsMsg` | premium/mevoco/src/main/java/org/zstack/header/host/APIGetHostNetworkFactsMsg.java |
| 12 | `APIGetHostNUMATopologyMsg` | premium/mevoco/src/main/java/org/zstack/header/host/APIGetHostNUMATopologyMsg.java |
| 13 | `APIGetHostPhysicalMemoryFactsMsg` | premium/mevoco/src/main/java/org/zstack/header/host/APIGetHostPhysicalMemoryFactsMsg.java |
| 14 | `APIGetHostResourceAllocationMsg` | premium/mevoco/src/main/java/org/zstack/header/host/APIGetHostResourceAllocationMsg.java |
| 15 | `APIGetInterfaceServiceTypeStatisticMsg` | premium/mevoco/src/main/java/org/zstack/header/host/APIGetInterfaceServiceTypeStatisticMsg.java |
| 16 | `APIIdentifyHostMsg` | premium/mevoco/src/main/java/org/zstack/header/host/APIIdentifyHostMsg.java |
| 17 | `APILocateHostNetworkInterfaceMsg` | premium/mevoco/src/main/java/org/zstack/header/host/APILocateHostNetworkInterfaceMsg.java |
| 18 | `APIPowerOffHostMsg` | premium/mevoco/src/main/java/org/zstack/header/host/APIPowerOffHostMsg.java |
| 19 | `APIQueryHostNetworkBondingMsg` | premium/mevoco/src/main/java/org/zstack/header/host/APIQueryHostNetworkBondingMsg.java |
| 20 | `APIQueryHostNetworkInterfaceMsg` | premium/mevoco/src/main/java/org/zstack/header/host/APIQueryHostNetworkInterfaceMsg.java |
| 21 | `APIQueryHostPhysicalCpuMsg` | premium/mevoco/src/main/java/org/zstack/header/host/APIQueryHostPhysicalCpuMsg.java |
| 22 | `APIQueryHostPhysicalMemoryMsg` | premium/mevoco/src/main/java/org/zstack/header/host/APIQueryHostPhysicalMemoryMsg.java |
| 23 | `APISetIpOnHostNetworkBondingMsg` | premium/mevoco/src/main/java/org/zstack/header/host/APISetIpOnHostNetworkBondingMsg.java |
| 24 | `APISetIpOnHostNetworkInterfaceMsg` | premium/mevoco/src/main/java/org/zstack/header/host/APISetIpOnHostNetworkInterfaceMsg.java |
| 25 | `APISetServiceTypeOnHostNetworkBondingMsg` | premium/mevoco/src/main/java/org/zstack/header/host/APISetServiceTypeOnHostNetworkBondingMsg.java |
| 26 | `APISetServiceTypeOnHostNetworkInterfaceMsg` | premium/mevoco/src/main/java/org/zstack/header/host/APISetServiceTypeOnHostNetworkInterfaceMsg.java |
| 27 | `APIUpdateHostIscsiInitiatorNameMsg` | premium/mevoco/src/main/java/org/zstack/header/host/APIUpdateHostIscsiInitiatorNameMsg.java |
| 28 | `APIUpdateHostNetworkInterfaceMsg` | premium/mevoco/src/main/java/org/zstack/header/host/APIUpdateHostNetworkInterfaceMsg.java |

### `org.zstack.header.image`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIGetImageQgaMsg` | premium/mevoco/src/main/java/org/zstack/header/image/APIGetImageQgaMsg.java |
| 2 | `APISetImageQgaMsg` | premium/mevoco/src/main/java/org/zstack/header/image/APISetImageQgaMsg.java |
| 3 | `APISetImageSecurityLevelMsg` | premium/mevoco/src/main/java/org/zstack/header/image/APISetImageSecurityLevelMsg.java |

### `org.zstack.header.managementnode`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIGetFactoryModeStateMsg` | premium/mevoco/src/main/java/org/zstack/header/managementnode/APIGetFactoryModeStateMsg.java |
| 2 | `APIUpdateFactoryModeStateMsg` | premium/mevoco/src/main/java/org/zstack/header/managementnode/APIUpdateFactoryModeStateMsg.java |

### `org.zstack.header.securitymachine`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APISecurityMachineEncryptMsg` | premium/mevoco/src/main/java/org/zstack/header/securitymachine/APISecurityMachineEncryptMsg.java |

### `org.zstack.header.sriov`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIChangeVfNicHaStateMsg` | premium/mevoco/src/main/java/org/zstack/header/sriov/APIChangeVfNicHaStateMsg.java |
| 2 | `APIChangeVmNicTypeMsg` | premium/mevoco/src/main/java/org/zstack/header/sriov/APIChangeVmNicTypeMsg.java |
| 3 | `APIIsVfNicAvailableInL3NetworkMsg` | premium/mevoco/src/main/java/org/zstack/header/sriov/APIIsVfNicAvailableInL3NetworkMsg.java |

### `org.zstack.header.storage.snapshot`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APICreateVolumesSnapshotMsg` | premium/mevoco/src/main/java/org/zstack/header/storage/snapshot/APICreateVolumesSnapshotMsg.java |

### `org.zstack.header.storageDevice`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAttachScsiLunToVmInstanceMsg` | premium/mevoco/src/main/java/org/zstack/header/storageDevice/APIAttachScsiLunToVmInstanceMsg.java |
| 2 | `APICheckScsiLunClusterStatusMsg` | premium/mevoco/src/main/java/org/zstack/header/storageDevice/APICheckScsiLunClusterStatusMsg.java |
| 3 | `APIDetachScsiLunFromHostMsg` | premium/mevoco/src/main/java/org/zstack/header/storageDevice/APIDetachScsiLunFromHostMsg.java |
| 4 | `APIDetachScsiLunFromVmInstanceMsg` | premium/mevoco/src/main/java/org/zstack/header/storageDevice/APIDetachScsiLunFromVmInstanceMsg.java |
| 5 | `APIGetScsiLunCandidatesForAttachingVmMsg` | premium/mevoco/src/main/java/org/zstack/header/storageDevice/APIGetScsiLunCandidatesForAttachingVmMsg.java |
| 6 | `APIQueryScsiLunMsg` | premium/mevoco/src/main/java/org/zstack/header/storageDevice/APIQueryScsiLunMsg.java |
| 7 | `APIUpdateScsiLunMsg` | premium/mevoco/src/main/java/org/zstack/header/storageDevice/APIUpdateScsiLunMsg.java |

### `org.zstack.header.vipQos`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIDeleteVipQosMsg` | premium/mevoco/src/main/java/org/zstack/header/vipQos/APIDeleteVipQosMsg.java |
| 2 | `APIGetVipQosMsg` | premium/mevoco/src/main/java/org/zstack/header/vipQos/APIGetVipQosMsg.java |
| 3 | `APISetVipQosMsg` | premium/mevoco/src/main/java/org/zstack/header/vipQos/APISetVipQosMsg.java |

### `org.zstack.header.vm`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIChangeVmImageMsg` | premium/mevoco/src/main/java/org/zstack/header/vm/APIChangeVmImageMsg.java |
| 2 | `APIChangeVmPasswordMsg` | premium/mevoco/src/main/java/org/zstack/header/vm/APIChangeVmPasswordMsg.java |
| 3 | `APICloneVmInstanceMsg` | premium/mevoco/src/main/java/org/zstack/header/vm/APICloneVmInstanceMsg.java |
| 4 | `APICreateTemplatedVmInstanceFromVmInstanceMsg` | premium/mevoco/src/main/java/org/zstack/header/vm/APICreateTemplatedVmInstanceFromVmInstanceMsg.java |
| 5 | `APICreateVmInstanceFromTemplatedVmInstanceMsg` | premium/mevoco/src/main/java/org/zstack/header/vm/APICreateVmInstanceFromTemplatedVmInstanceMsg.java |
| 6 | `APIDeleteNicQosMsg` | premium/mevoco/src/main/java/org/zstack/header/vm/APIDeleteNicQosMsg.java |
| 7 | `APIDeleteVmUserDefinedXmlHookScriptMsg` | premium/mevoco/src/main/java/org/zstack/header/vm/APIDeleteVmUserDefinedXmlHookScriptMsg.java |
| 8 | `APIDeleteVmUserDefinedXmlMsg` | premium/mevoco/src/main/java/org/zstack/header/vm/APIDeleteVmUserDefinedXmlMsg.java |
| 9 | `APIGetImageCandidatesForVmToChangeMsg` | premium/mevoco/src/main/java/org/zstack/header/vm/APIGetImageCandidatesForVmToChangeMsg.java |
| 10 | `APIGetNicQosMsg` | premium/mevoco/src/main/java/org/zstack/header/vm/APIGetNicQosMsg.java |
| 11 | `APIGetVirtualizerInfoMsg` | premium/mevoco/src/main/java/org/zstack/header/vm/APIGetVirtualizerInfoMsg.java |
| 12 | `APIGetVmEmulatorPinningMsg` | premium/mevoco/src/main/java/org/zstack/header/vm/APIGetVmEmulatorPinningMsg.java |
| 13 | `APIGetVmInstanceFirstBootDeviceMsg` | premium/mevoco/src/main/java/org/zstack/header/vm/APIGetVmInstanceFirstBootDeviceMsg.java |
| 14 | `APIGetVmMonitorNumberMsg` | premium/mevoco/src/main/java/org/zstack/header/vm/APIGetVmMonitorNumberMsg.java |
| 15 | `APIGetVmNumaMsg` | premium/mevoco/src/main/java/org/zstack/header/vm/APIGetVmNumaMsg.java |
| 16 | `APIGetVmQgaMsg` | premium/mevoco/src/main/java/org/zstack/header/vm/APIGetVmQgaMsg.java |
| 17 | `APIGetVmRDPMsg` | premium/mevoco/src/main/java/org/zstack/header/vm/APIGetVmRDPMsg.java |
| 18 | `APIGetVmUsbRedirectMsg` | premium/mevoco/src/main/java/org/zstack/header/vm/APIGetVmUsbRedirectMsg.java |
| 19 | `APIGetVmvNUMATopologyMsg` | premium/mevoco/src/main/java/org/zstack/header/vm/APIGetVmvNUMATopologyMsg.java |
| 20 | `APIGetVmXmlHookScriptMsg` | premium/mevoco/src/main/java/org/zstack/header/vm/APIGetVmXmlHookScriptMsg.java |
| 21 | `APIGetVmXmlMsg` | premium/mevoco/src/main/java/org/zstack/header/vm/APIGetVmXmlMsg.java |
| 22 | `APIQueryVmSchedHistoryMsg` | premium/mevoco/src/main/java/org/zstack/header/vm/APIQueryVmSchedHistoryMsg.java |
| 23 | `APISetNicQosMsg` | premium/mevoco/src/main/java/org/zstack/header/vm/APISetNicQosMsg.java |
| 24 | `APISetVmCleanTrafficMsg` | premium/mevoco/src/main/java/org/zstack/header/vm/APISetVmCleanTrafficMsg.java |
| 25 | `APISetVmConsoleModeMsg` | premium/mevoco/src/main/java/org/zstack/header/vm/APISetVmConsoleModeMsg.java |
| 26 | `APISetVmEmulatorPinningMsg` | premium/mevoco/src/main/java/org/zstack/header/vm/APISetVmEmulatorPinningMsg.java |
| 27 | `APISetVmMonitorNumberMsg` | premium/mevoco/src/main/java/org/zstack/header/vm/APISetVmMonitorNumberMsg.java |
| 28 | `APISetVmNumaMsg` | premium/mevoco/src/main/java/org/zstack/header/vm/APISetVmNumaMsg.java |
| 29 | `APISetVmQgaMsg` | premium/mevoco/src/main/java/org/zstack/header/vm/APISetVmQgaMsg.java |
| 30 | `APISetVmRDPMsg` | premium/mevoco/src/main/java/org/zstack/header/vm/APISetVmRDPMsg.java |
| 31 | `APISetVmSecurityLevelMsg` | premium/mevoco/src/main/java/org/zstack/header/vm/APISetVmSecurityLevelMsg.java |
| 32 | `APISetVmUsbRedirectMsg` | premium/mevoco/src/main/java/org/zstack/header/vm/APISetVmUsbRedirectMsg.java |
| 33 | `APISetVmUserDefinedXmlHookScriptMsg` | premium/mevoco/src/main/java/org/zstack/header/vm/APISetVmUserDefinedXmlHookScriptMsg.java |
| 34 | `APISetVmUserDefinedXmlMsg` | premium/mevoco/src/main/java/org/zstack/header/vm/APISetVmUserDefinedXmlMsg.java |
| 35 | `APISyncVmClockMsg` | premium/mevoco/src/main/java/org/zstack/header/vm/APISyncVmClockMsg.java |
| 36 | `APIUpdateVmNicMacMsg` | premium/mevoco/src/main/java/org/zstack/header/vm/APIUpdateVmNicMacMsg.java |

### `org.zstack.header.vmscheduling`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddHostToHostSchedulingRuleGroupMsg` | premium/mevoco/src/main/java/org/zstack/header/vmscheduling/APIAddHostToHostSchedulingRuleGroupMsg.java |
| 2 | `APIAddVmToVmSchedulingRuleGroupMsg` | premium/mevoco/src/main/java/org/zstack/header/vmscheduling/APIAddVmToVmSchedulingRuleGroupMsg.java |
| 3 | `APIChangeVmSchedulingRuleStateMsg` | premium/mevoco/src/main/java/org/zstack/header/vmscheduling/APIChangeVmSchedulingRuleStateMsg.java |
| 4 | `APICreateHostSchedulingRuleGroupMsg` | premium/mevoco/src/main/java/org/zstack/header/vmscheduling/APICreateHostSchedulingRuleGroupMsg.java |
| 5 | `APICreateVmSchedulingRuleGroupMsg` | premium/mevoco/src/main/java/org/zstack/header/vmscheduling/APICreateVmSchedulingRuleGroupMsg.java |
| 6 | `APICreateVmSchedulingRuleMsg` | premium/mevoco/src/main/java/org/zstack/header/vmscheduling/APICreateVmSchedulingRuleMsg.java |
| 7 | `APIDeleteHostSchedulingRuleGroupMsg` | premium/mevoco/src/main/java/org/zstack/header/vmscheduling/APIDeleteHostSchedulingRuleGroupMsg.java |
| 8 | `APIDeleteVmSchedulingRuleGroupMsg` | premium/mevoco/src/main/java/org/zstack/header/vmscheduling/APIDeleteVmSchedulingRuleGroupMsg.java |
| 9 | `APIDetachHostFromHostSchedulingRuleGroupMsg` | premium/mevoco/src/main/java/org/zstack/header/vmscheduling/APIDetachHostFromHostSchedulingRuleGroupMsg.java |
| 10 | `APIDetachVmFromVmSchedulingRuleGroupMsg` | premium/mevoco/src/main/java/org/zstack/header/vmscheduling/APIDetachVmFromVmSchedulingRuleGroupMsg.java |
| 11 | `APIGetVmSchedulingRulesExecuteStateMsg` | premium/mevoco/src/main/java/org/zstack/header/vmscheduling/APIGetVmSchedulingRulesExecuteStateMsg.java |
| 12 | `APIGetVmsSchedulingStateFromSchedulingRuleMsg` | premium/mevoco/src/main/java/org/zstack/header/vmscheduling/APIGetVmsSchedulingStateFromSchedulingRuleMsg.java |
| 13 | `APIListVmSchedulingRulesFromExecuteStateMsg` | premium/mevoco/src/main/java/org/zstack/header/vmscheduling/APIListVmSchedulingRulesFromExecuteStateMsg.java |
| 14 | `APIListVmsFromSchedulingStateMsg` | premium/mevoco/src/main/java/org/zstack/header/vmscheduling/APIListVmsFromSchedulingStateMsg.java |
| 15 | `APIQueryHostSchedulingRuleGroupMsg` | premium/mevoco/src/main/java/org/zstack/header/vmscheduling/APIQueryHostSchedulingRuleGroupMsg.java |
| 16 | `APIQueryVmSchedulingRuleGroupMsg` | premium/mevoco/src/main/java/org/zstack/header/vmscheduling/APIQueryVmSchedulingRuleGroupMsg.java |
| 17 | `APIQueryVmSchedulingRuleMsg` | premium/mevoco/src/main/java/org/zstack/header/vmscheduling/APIQueryVmSchedulingRuleMsg.java |
| 18 | `APIRemoveVmSchedulingRuleMsg` | premium/mevoco/src/main/java/org/zstack/header/vmscheduling/APIRemoveVmSchedulingRuleMsg.java |
| 19 | `APIUpdateHostSchedulingRuleGroupMsg` | premium/mevoco/src/main/java/org/zstack/header/vmscheduling/APIUpdateHostSchedulingRuleGroupMsg.java |
| 20 | `APIUpdateVmSchedulingRuleGroupMsg` | premium/mevoco/src/main/java/org/zstack/header/vmscheduling/APIUpdateVmSchedulingRuleGroupMsg.java |
| 21 | `APIUpdateVmSchedulingRuleMsg` | premium/mevoco/src/main/java/org/zstack/header/vmscheduling/APIUpdateVmSchedulingRuleMsg.java |
| 22 | `APIValidateVmSchedulingRuleMsg` | premium/mevoco/src/main/java/org/zstack/header/vmscheduling/APIValidateVmSchedulingRuleMsg.java |

### `org.zstack.header.volume`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIDeleteVolumeQosMsg` | premium/mevoco/src/main/java/org/zstack/header/volume/APIDeleteVolumeQosMsg.java |
| 2 | `APIGetVolumeIoThreadPinMsg` | premium/mevoco/src/main/java/org/zstack/header/volume/APIGetVolumeIoThreadPinMsg.java |
| 3 | `APIGetVolumeQosMsg` | premium/mevoco/src/main/java/org/zstack/header/volume/APIGetVolumeQosMsg.java |
| 4 | `APIResizeDataVolumeMsg` | premium/mevoco/src/main/java/org/zstack/header/volume/APIResizeDataVolumeMsg.java |
| 5 | `APIResizeRootVolumeMsg` | premium/mevoco/src/main/java/org/zstack/header/volume/APIResizeRootVolumeMsg.java |
| 6 | `APISetVolumeIoThreadPinMsg` | premium/mevoco/src/main/java/org/zstack/header/volume/APISetVolumeIoThreadPinMsg.java |
| 7 | `APISetVolumeQosMsg` | premium/mevoco/src/main/java/org/zstack/header/volume/APISetVolumeQosMsg.java |
| 8 | `APIValidateVolumeSnapshotChainMsg` | premium/mevoco/src/main/java/org/zstack/header/volume/APIValidateVolumeSnapshotChainMsg.java |

### `org.zstack.header.volume.block`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APICreateBlockVolumeMsg` | premium/mevoco/src/main/java/org/zstack/header/volume/block/APICreateBlockVolumeMsg.java |
| 2 | `APIGetAccessPathMsg` | premium/mevoco/src/main/java/org/zstack/header/volume/block/APIGetAccessPathMsg.java |
| 3 | `APIQueryBlockVolumeMsg` | premium/mevoco/src/main/java/org/zstack/header/volume/block/APIQueryBlockVolumeMsg.java |
| 4 | `APIQueryExponBlockVolumeMsg` | premium/mevoco/src/main/java/org/zstack/header/volume/block/APIQueryExponBlockVolumeMsg.java |
| 5 | `APIQueryXskyBlockVolumeMsg` | premium/mevoco/src/main/java/org/zstack/header/volume/block/APIQueryXskyBlockVolumeMsg.java |
| 6 | `APIUpdateBlockVolumeMsg` | premium/mevoco/src/main/java/org/zstack/header/volume/block/APIUpdateBlockVolumeMsg.java |
| 7 | `APIUpdateXskyBlockVolumeMsg` | premium/mevoco/src/main/java/org/zstack/header/volume/block/APIUpdateXskyBlockVolumeMsg.java |

### `org.zstack.license`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIDeleteLicenseMsg` | premium/mevoco/src/main/java/org/zstack/license/APIDeleteLicenseMsg.java |
| 2 | `APIGetLicenseAddOnsMsg` | premium/mevoco/src/main/java/org/zstack/license/APIGetLicenseAddOnsMsg.java |
| 3 | `APIGetLicenseCapabilitiesMsg` | premium/mevoco/src/main/java/org/zstack/license/APIGetLicenseCapabilitiesMsg.java |
| 4 | `APIGetLicenseInfoMsg` | premium/mevoco/src/main/java/org/zstack/license/APIGetLicenseInfoMsg.java |
| 5 | `APIGetLicenseRecordsMsg` | premium/mevoco/src/main/java/org/zstack/license/APIGetLicenseRecordsMsg.java |
| 6 | `APIGetLicenseUKeyStatusMsg` | premium/mevoco/src/main/java/org/zstack/license/APIGetLicenseUKeyStatusMsg.java |
| 7 | `APIRegisterLicenseRequestedApplicationMsg` | premium/mevoco/src/main/java/org/zstack/license/APIRegisterLicenseRequestedApplicationMsg.java |
| 8 | `APIReloadLicenseMsg` | premium/mevoco/src/main/java/org/zstack/license/APIReloadLicenseMsg.java |
| 9 | `APIUpdateLicenseMsg` | premium/mevoco/src/main/java/org/zstack/license/APIUpdateLicenseMsg.java |

### `org.zstack.message`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIReplayMessageMsg` | premium/mevoco/src/main/java/org/zstack/message/APIReplayMessageMsg.java |

### `org.zstack.mevoco`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIQueryShareableVolumeVmInstanceRefMsg` | premium/mevoco/src/main/java/org/zstack/mevoco/APIQueryShareableVolumeVmInstanceRefMsg.java |

### `org.zstack.monitoring`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAttachMonitorTriggerActionToTriggerMsg` | premium/mevoco/src/main/java/org/zstack/monitoring/APIAttachMonitorTriggerActionToTriggerMsg.java |
| 2 | `APIChangeMonitorTriggerStateMsg` | premium/mevoco/src/main/java/org/zstack/monitoring/APIChangeMonitorTriggerStateMsg.java |
| 3 | `APICreateMonitorTriggerMsg` | premium/mevoco/src/main/java/org/zstack/monitoring/APICreateMonitorTriggerMsg.java |
| 4 | `APIDeleteAlertMsg` | premium/mevoco/src/main/java/org/zstack/monitoring/APIDeleteAlertMsg.java |
| 5 | `APIDeleteMonitorTriggerMsg` | premium/mevoco/src/main/java/org/zstack/monitoring/APIDeleteMonitorTriggerMsg.java |
| 6 | `APIDetachMonitorTriggerActionFromTriggerMsg` | premium/mevoco/src/main/java/org/zstack/monitoring/APIDetachMonitorTriggerActionFromTriggerMsg.java |
| 7 | `APIGetMonitorItemMsg` | premium/mevoco/src/main/java/org/zstack/monitoring/APIGetMonitorItemMsg.java |
| 8 | `APIQueryAlertMsg` | premium/mevoco/src/main/java/org/zstack/monitoring/APIQueryAlertMsg.java |
| 9 | `APIQueryMonitorTriggerMsg` | premium/mevoco/src/main/java/org/zstack/monitoring/APIQueryMonitorTriggerMsg.java |
| 10 | `APIUpdateMonitorTriggerMsg` | premium/mevoco/src/main/java/org/zstack/monitoring/APIUpdateMonitorTriggerMsg.java |

### `org.zstack.monitoring.actions`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIChangeMonitorTriggerActionStateMsg` | premium/mevoco/src/main/java/org/zstack/monitoring/actions/APIChangeMonitorTriggerActionStateMsg.java |
| 2 | `APICreateEmailMonitorTriggerActionMsg` | premium/mevoco/src/main/java/org/zstack/monitoring/actions/APICreateEmailMonitorTriggerActionMsg.java |
| 3 | `APICreateMonitorTriggerActionMsg` | premium/mevoco/src/main/java/org/zstack/monitoring/actions/APICreateMonitorTriggerActionMsg.java |
| 4 | `APIDeleteMonitorTriggerActionMsg` | premium/mevoco/src/main/java/org/zstack/monitoring/actions/APIDeleteMonitorTriggerActionMsg.java |
| 5 | `APIQueryEmailTriggerActionMsg` | premium/mevoco/src/main/java/org/zstack/monitoring/actions/APIQueryEmailTriggerActionMsg.java |
| 6 | `APIQueryMonitorTriggerActionMsg` | premium/mevoco/src/main/java/org/zstack/monitoring/actions/APIQueryMonitorTriggerActionMsg.java |
| 7 | `APIUpdateEmailMonitorTriggerActionMsg` | premium/mevoco/src/main/java/org/zstack/monitoring/actions/APIUpdateEmailMonitorTriggerActionMsg.java |

### `org.zstack.monitoring.media`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIChangeMediaStateMsg` | premium/mevoco/src/main/java/org/zstack/monitoring/media/APIChangeMediaStateMsg.java |
| 2 | `APICreateEmailMediaMsg` | premium/mevoco/src/main/java/org/zstack/monitoring/media/APICreateEmailMediaMsg.java |
| 3 | `APICreateMediaMsg` | premium/mevoco/src/main/java/org/zstack/monitoring/media/APICreateMediaMsg.java |
| 4 | `APIDeleteMediaMsg` | premium/mevoco/src/main/java/org/zstack/monitoring/media/APIDeleteMediaMsg.java |
| 5 | `APIQueryEmailMediaMsg` | premium/mevoco/src/main/java/org/zstack/monitoring/media/APIQueryEmailMediaMsg.java |
| 6 | `APIQueryMediaMsg` | premium/mevoco/src/main/java/org/zstack/monitoring/media/APIQueryMediaMsg.java |
| 7 | `APIUpdateEmailMediaMsg` | premium/mevoco/src/main/java/org/zstack/monitoring/media/APIUpdateEmailMediaMsg.java |

### `org.zstack.monitoring.prometheus`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIPrometheusQueryLabelValuesMsg` | premium/mevoco/src/main/java/org/zstack/monitoring/prometheus/APIPrometheusQueryLabelValuesMsg.java |
| 2 | `APIPrometheusQueryMetadataMsg` | premium/mevoco/src/main/java/org/zstack/monitoring/prometheus/APIPrometheusQueryMetadataMsg.java |
| 3 | `APIPrometheusQueryMsg` | premium/mevoco/src/main/java/org/zstack/monitoring/prometheus/APIPrometheusQueryMsg.java |
| 4 | `APIPrometheusQueryPassThroughMsg` | premium/mevoco/src/main/java/org/zstack/monitoring/prometheus/APIPrometheusQueryPassThroughMsg.java |
| 5 | `APIPrometheusQueryVmMonitoringDataMsg` | premium/mevoco/src/main/java/org/zstack/monitoring/prometheus/APIPrometheusQueryVmMonitoringDataMsg.java |

### `org.zstack.mttyDevice`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIGenerateSeMdevDevicesMsg` | premium/mevoco/src/main/java/org/zstack/mttyDevice/APIGenerateSeMdevDevicesMsg.java |
| 2 | `APIQueryMttyDeviceMsg` | premium/mevoco/src/main/java/org/zstack/mttyDevice/APIQueryMttyDeviceMsg.java |
| 3 | `APIUngenerateSeMdevDevicesMsg` | premium/mevoco/src/main/java/org/zstack/mttyDevice/APIUngenerateSeMdevDevicesMsg.java |

### `org.zstack.nas`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APICreateNasFileSystemMsg` | premium/mevoco/src/main/java/org/zstack/nas/APICreateNasFileSystemMsg.java |
| 2 | `APICreateNasMountTargetMsg` | premium/mevoco/src/main/java/org/zstack/nas/APICreateNasMountTargetMsg.java |
| 3 | `APIDeleteNasFileSystemMsg` | premium/mevoco/src/main/java/org/zstack/nas/APIDeleteNasFileSystemMsg.java |
| 4 | `APIDeleteNasMountTargetMsg` | premium/mevoco/src/main/java/org/zstack/nas/APIDeleteNasMountTargetMsg.java |
| 5 | `APIQueryNasFileSystemMsg` | premium/mevoco/src/main/java/org/zstack/nas/APIQueryNasFileSystemMsg.java |
| 6 | `APIQueryNasMountTargetMsg` | premium/mevoco/src/main/java/org/zstack/nas/APIQueryNasMountTargetMsg.java |
| 7 | `APIUpdateNasFileSystemMsg` | premium/mevoco/src/main/java/org/zstack/nas/APIUpdateNasFileSystemMsg.java |
| 8 | `APIUpdateNasMountTargetMsg` | premium/mevoco/src/main/java/org/zstack/nas/APIUpdateNasMountTargetMsg.java |

### `org.zstack.pciDevice`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAttachPciDeviceToVmMsg` | premium/mevoco/src/main/java/org/zstack/pciDevice/APIAttachPciDeviceToVmMsg.java |
| 2 | `APICreatePciDeviceOfferingMsg` | premium/mevoco/src/main/java/org/zstack/pciDevice/APICreatePciDeviceOfferingMsg.java |
| 3 | `APIDeletePciDeviceMsg` | premium/mevoco/src/main/java/org/zstack/pciDevice/APIDeletePciDeviceMsg.java |
| 4 | `APIDeletePciDeviceOfferingMsg` | premium/mevoco/src/main/java/org/zstack/pciDevice/APIDeletePciDeviceOfferingMsg.java |
| 5 | `APIDetachPciDeviceFromVmMsg` | premium/mevoco/src/main/java/org/zstack/pciDevice/APIDetachPciDeviceFromVmMsg.java |
| 6 | `APIGetHostIommuStateMsg` | premium/mevoco/src/main/java/org/zstack/pciDevice/APIGetHostIommuStateMsg.java |
| 7 | `APIGetHostIommuStatusMsg` | premium/mevoco/src/main/java/org/zstack/pciDevice/APIGetHostIommuStatusMsg.java |
| 8 | `APIGetPciDeviceCandidatesForAttachingVmMsg` | premium/mevoco/src/main/java/org/zstack/pciDevice/APIGetPciDeviceCandidatesForAttachingVmMsg.java |
| 9 | `APIGetPciDeviceCandidatesForNewCreateVmMsg` | premium/mevoco/src/main/java/org/zstack/pciDevice/APIGetPciDeviceCandidatesForNewCreateVmMsg.java |
| 10 | `APIQueryPciDeviceMsg` | premium/mevoco/src/main/java/org/zstack/pciDevice/APIQueryPciDeviceMsg.java |
| 11 | `APIQueryPciDeviceOfferingMsg` | premium/mevoco/src/main/java/org/zstack/pciDevice/APIQueryPciDeviceOfferingMsg.java |
| 12 | `APIQueryPciDevicePciDeviceOfferingMsg` | premium/mevoco/src/main/java/org/zstack/pciDevice/APIQueryPciDevicePciDeviceOfferingMsg.java |
| 13 | `APIUpdateHostIommuStateMsg` | premium/mevoco/src/main/java/org/zstack/pciDevice/APIUpdateHostIommuStateMsg.java |
| 14 | `APIUpdatePciDeviceMsg` | premium/mevoco/src/main/java/org/zstack/pciDevice/APIUpdatePciDeviceMsg.java |

### `org.zstack.pciDevice.gpu`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIQueryGpuDeviceMsg` | premium/mevoco/src/main/java/org/zstack/pciDevice/gpu/APIQueryGpuDeviceMsg.java |

### `org.zstack.pciDevice.specification.mdev`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddMdevDeviceSpecToVmInstanceMsg` | premium/mevoco/src/main/java/org/zstack/pciDevice/specification/mdev/APIAddMdevDeviceSpecToVmInstanceMsg.java |
| 2 | `APIGetMdevDeviceSpecCandidatesMsg` | premium/mevoco/src/main/java/org/zstack/pciDevice/specification/mdev/APIGetMdevDeviceSpecCandidatesMsg.java |
| 3 | `APIQueryMdevDeviceSpecMsg` | premium/mevoco/src/main/java/org/zstack/pciDevice/specification/mdev/APIQueryMdevDeviceSpecMsg.java |
| 4 | `APIQueryVmInstanceMdevDeviceSpecRefMsg` | premium/mevoco/src/main/java/org/zstack/pciDevice/specification/mdev/APIQueryVmInstanceMdevDeviceSpecRefMsg.java |
| 5 | `APIRemoveMdevDeviceSpecFromVmInstanceMsg` | premium/mevoco/src/main/java/org/zstack/pciDevice/specification/mdev/APIRemoveMdevDeviceSpecFromVmInstanceMsg.java |
| 6 | `APIUpdateMdevDeviceSpecMsg` | premium/mevoco/src/main/java/org/zstack/pciDevice/specification/mdev/APIUpdateMdevDeviceSpecMsg.java |

### `org.zstack.pciDevice.specification.pci`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddPciDeviceSpecToVmInstanceMsg` | premium/mevoco/src/main/java/org/zstack/pciDevice/specification/pci/APIAddPciDeviceSpecToVmInstanceMsg.java |
| 2 | `APIGetPciDeviceSpecCandidatesMsg` | premium/mevoco/src/main/java/org/zstack/pciDevice/specification/pci/APIGetPciDeviceSpecCandidatesMsg.java |
| 3 | `APIQueryPciDeviceSpecMsg` | premium/mevoco/src/main/java/org/zstack/pciDevice/specification/pci/APIQueryPciDeviceSpecMsg.java |
| 4 | `APIQueryVmInstancePciDeviceSpecRefMsg` | premium/mevoco/src/main/java/org/zstack/pciDevice/specification/pci/APIQueryVmInstancePciDeviceSpecRefMsg.java |
| 5 | `APIRemovePciDeviceSpecFromVmInstanceMsg` | premium/mevoco/src/main/java/org/zstack/pciDevice/specification/pci/APIRemovePciDeviceSpecFromVmInstanceMsg.java |
| 6 | `APIUpdatePciDeviceSpecMsg` | premium/mevoco/src/main/java/org/zstack/pciDevice/specification/pci/APIUpdatePciDeviceSpecMsg.java |

### `org.zstack.pciDevice.virtual`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIGenerateVirtualPciDevicesMsg` | premium/mevoco/src/main/java/org/zstack/pciDevice/virtual/APIGenerateVirtualPciDevicesMsg.java |
| 2 | `APIUngenerateVirtualPciDevicesMsg` | premium/mevoco/src/main/java/org/zstack/pciDevice/virtual/APIUngenerateVirtualPciDevicesMsg.java |

### `org.zstack.pciDevice.virtual.sr_iov`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIGenerateSriovPciDevicesMsg` | premium/mevoco/src/main/java/org/zstack/pciDevice/virtual/sr_iov/APIGenerateSriovPciDevicesMsg.java |
| 2 | `APIQueryEthernetVFMsg` | premium/mevoco/src/main/java/org/zstack/pciDevice/virtual/sr_iov/APIQueryEthernetVFMsg.java |
| 3 | `APIUngenerateSriovPciDevicesMsg` | premium/mevoco/src/main/java/org/zstack/pciDevice/virtual/sr_iov/APIUngenerateSriovPciDevicesMsg.java |

### `org.zstack.pciDevice.virtual.vfio_mdev`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAttachMdevDeviceToVmMsg` | premium/mevoco/src/main/java/org/zstack/pciDevice/virtual/vfio_mdev/APIAttachMdevDeviceToVmMsg.java |
| 2 | `APIDeleteMdevDeviceMsg` | premium/mevoco/src/main/java/org/zstack/pciDevice/virtual/vfio_mdev/APIDeleteMdevDeviceMsg.java |
| 3 | `APIDetachMdevDeviceFromVmMsg` | premium/mevoco/src/main/java/org/zstack/pciDevice/virtual/vfio_mdev/APIDetachMdevDeviceFromVmMsg.java |
| 4 | `APIGenerateMdevDevicesMsg` | premium/mevoco/src/main/java/org/zstack/pciDevice/virtual/vfio_mdev/APIGenerateMdevDevicesMsg.java |
| 5 | `APIGetMdevDeviceCandidatesMsg` | premium/mevoco/src/main/java/org/zstack/pciDevice/virtual/vfio_mdev/APIGetMdevDeviceCandidatesMsg.java |
| 6 | `APIQueryMdevDeviceMsg` | premium/mevoco/src/main/java/org/zstack/pciDevice/virtual/vfio_mdev/APIQueryMdevDeviceMsg.java |
| 7 | `APIUngenerateMdevDevicesMsg` | premium/mevoco/src/main/java/org/zstack/pciDevice/virtual/vfio_mdev/APIUngenerateMdevDevicesMsg.java |
| 8 | `APIUpdateMdevDeviceMsg` | premium/mevoco/src/main/java/org/zstack/pciDevice/virtual/vfio_mdev/APIUpdateMdevDeviceMsg.java |

### `org.zstack.scheduler`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddSchedulerJobGroupToSchedulerTriggerMsg` | premium/mevoco/src/main/java/org/zstack/scheduler/APIAddSchedulerJobGroupToSchedulerTriggerMsg.java |
| 2 | `APIAddSchedulerJobsToSchedulerJobGroupMsg` | premium/mevoco/src/main/java/org/zstack/scheduler/APIAddSchedulerJobsToSchedulerJobGroupMsg.java |
| 3 | `APIAddSchedulerJobToSchedulerTriggerMsg` | premium/mevoco/src/main/java/org/zstack/scheduler/APIAddSchedulerJobToSchedulerTriggerMsg.java |
| 4 | `APIChangeSchedulerStateMsg` | premium/mevoco/src/main/java/org/zstack/scheduler/APIChangeSchedulerStateMsg.java |
| 5 | `APICreateSchedulerJobGroupMsg` | premium/mevoco/src/main/java/org/zstack/scheduler/APICreateSchedulerJobGroupMsg.java |
| 6 | `APICreateSchedulerJobMsg` | premium/mevoco/src/main/java/org/zstack/scheduler/APICreateSchedulerJobMsg.java |
| 7 | `APICreateSchedulerTriggerMsg` | premium/mevoco/src/main/java/org/zstack/scheduler/APICreateSchedulerTriggerMsg.java |
| 8 | `APIDeleteSchedulerJobGroupMsg` | premium/mevoco/src/main/java/org/zstack/scheduler/APIDeleteSchedulerJobGroupMsg.java |
| 9 | `APIDeleteSchedulerJobMsg` | premium/mevoco/src/main/java/org/zstack/scheduler/APIDeleteSchedulerJobMsg.java |
| 10 | `APIDeleteSchedulerTriggerMsg` | premium/mevoco/src/main/java/org/zstack/scheduler/APIDeleteSchedulerTriggerMsg.java |
| 11 | `APIGetAvailableTriggersMsg` | premium/mevoco/src/main/java/org/zstack/scheduler/APIGetAvailableTriggersMsg.java |
| 12 | `APIGetNoTriggerSchedulerJobsMsg` | premium/mevoco/src/main/java/org/zstack/scheduler/APIGetNoTriggerSchedulerJobsMsg.java |
| 13 | `APIGetSchedulerExecutionReportMsg` | premium/mevoco/src/main/java/org/zstack/scheduler/APIGetSchedulerExecutionReportMsg.java |
| 14 | `APIQuerySchedulerJobGroupMsg` | premium/mevoco/src/main/java/org/zstack/scheduler/APIQuerySchedulerJobGroupMsg.java |
| 15 | `APIQuerySchedulerJobHistoryMsg` | premium/mevoco/src/main/java/org/zstack/scheduler/APIQuerySchedulerJobHistoryMsg.java |
| 16 | `APIQuerySchedulerJobMsg` | premium/mevoco/src/main/java/org/zstack/scheduler/APIQuerySchedulerJobMsg.java |
| 17 | `APIQuerySchedulerTriggerMsg` | premium/mevoco/src/main/java/org/zstack/scheduler/APIQuerySchedulerTriggerMsg.java |
| 18 | `APIRemoveSchedulerJobFromSchedulerTriggerMsg` | premium/mevoco/src/main/java/org/zstack/scheduler/APIRemoveSchedulerJobFromSchedulerTriggerMsg.java |
| 19 | `APIRemoveSchedulerJobGroupFromSchedulerTriggerMsg` | premium/mevoco/src/main/java/org/zstack/scheduler/APIRemoveSchedulerJobGroupFromSchedulerTriggerMsg.java |
| 20 | `APIRemoveSchedulerJobsFromSchedulerJobGroupMsg` | premium/mevoco/src/main/java/org/zstack/scheduler/APIRemoveSchedulerJobsFromSchedulerJobGroupMsg.java |
| 21 | `APIRunSchedulerTriggerMsg` | premium/mevoco/src/main/java/org/zstack/scheduler/APIRunSchedulerTriggerMsg.java |
| 22 | `APIUpdateSchedulerJobGroupMsg` | premium/mevoco/src/main/java/org/zstack/scheduler/APIUpdateSchedulerJobGroupMsg.java |
| 23 | `APIUpdateSchedulerJobMsg` | premium/mevoco/src/main/java/org/zstack/scheduler/APIUpdateSchedulerJobMsg.java |
| 24 | `APIUpdateSchedulerTriggerMsg` | premium/mevoco/src/main/java/org/zstack/scheduler/APIUpdateSchedulerTriggerMsg.java |

### `org.zstack.storage.backup.imagestore`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddDisasterImageStoreBackupStorageMsg` | premium/mevoco/src/main/java/org/zstack/storage/backup/imagestore/APIAddDisasterImageStoreBackupStorageMsg.java |
| 2 | `APIAddImageStoreBackupStorageMsg` | premium/mevoco/src/main/java/org/zstack/storage/backup/imagestore/APIAddImageStoreBackupStorageMsg.java |
| 3 | `APIGetImagesFromImageStoreBackupStorageMsg` | premium/mevoco/src/main/java/org/zstack/storage/backup/imagestore/APIGetImagesFromImageStoreBackupStorageMsg.java |
| 4 | `APIQueryImageStoreBackupStorageMsg` | premium/mevoco/src/main/java/org/zstack/storage/backup/imagestore/APIQueryImageStoreBackupStorageMsg.java |
| 5 | `APIReclaimSpaceFromImageStoreMsg` | premium/mevoco/src/main/java/org/zstack/storage/backup/imagestore/APIReclaimSpaceFromImageStoreMsg.java |
| 6 | `APIReconnectImageStoreBackupStorageMsg` | premium/mevoco/src/main/java/org/zstack/storage/backup/imagestore/APIReconnectImageStoreBackupStorageMsg.java |
| 7 | `APIRecoveryImageFromImageStoreBackupStorageMsg` | premium/mevoco/src/main/java/org/zstack/storage/backup/imagestore/APIRecoveryImageFromImageStoreBackupStorageMsg.java |
| 8 | `APISetImageStoreBackupStorageQuotaMsg` | premium/mevoco/src/main/java/org/zstack/storage/backup/imagestore/APISetImageStoreBackupStorageQuotaMsg.java |
| 9 | `APISyncImageFromImageStoreBackupStorageMsg` | premium/mevoco/src/main/java/org/zstack/storage/backup/imagestore/APISyncImageFromImageStoreBackupStorageMsg.java |
| 10 | `APISyncImageMsg` | premium/mevoco/src/main/java/org/zstack/storage/backup/imagestore/APISyncImageMsg.java |
| 11 | `APIUpdateImageStoreBackupStorageMsg` | premium/mevoco/src/main/java/org/zstack/storage/backup/imagestore/APIUpdateImageStoreBackupStorageMsg.java |

### `org.zstack.storage.migration.backup`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIBackupStorageMigrateImageMsg` | premium/mevoco/src/main/java/org/zstack/storage/migration/backup/APIBackupStorageMigrateImageMsg.java |
| 2 | `APIGetBackupStorageCandidatesForImageMigrationMsg` | premium/mevoco/src/main/java/org/zstack/storage/migration/backup/APIGetBackupStorageCandidatesForImageMigrationMsg.java |

### `org.zstack.storage.migration.primary`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIGetHostCandidatesForVmMigrationMsg` | premium/mevoco/src/main/java/org/zstack/storage/migration/primary/APIGetHostCandidatesForVmMigrationMsg.java |
| 2 | `APIGetPrimaryStorageCandidatesForVmMigrationMsg` | premium/mevoco/src/main/java/org/zstack/storage/migration/primary/APIGetPrimaryStorageCandidatesForVmMigrationMsg.java |
| 3 | `APIGetPrimaryStorageCandidatesForVolumeMigrationMsg` | premium/mevoco/src/main/java/org/zstack/storage/migration/primary/APIGetPrimaryStorageCandidatesForVolumeMigrationMsg.java |
| 4 | `APIPrimaryStorageMigrateVmMsg` | premium/mevoco/src/main/java/org/zstack/storage/migration/primary/APIPrimaryStorageMigrateVmMsg.java |
| 5 | `APIPrimaryStorageMigrateVolumeMsg` | premium/mevoco/src/main/java/org/zstack/storage/migration/primary/APIPrimaryStorageMigrateVolumeMsg.java |

### `org.zstack.templateConfig`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIApplyTemplateConfigMsg` | premium/mevoco/src/main/java/org/zstack/templateConfig/APIApplyTemplateConfigMsg.java |
| 2 | `APIQueryGlobalConfigTemplateMsg` | premium/mevoco/src/main/java/org/zstack/templateConfig/APIQueryGlobalConfigTemplateMsg.java |
| 3 | `APIQueryTemplateConfigMsg` | premium/mevoco/src/main/java/org/zstack/templateConfig/APIQueryTemplateConfigMsg.java |
| 4 | `APIResetTemplateConfigMsg` | premium/mevoco/src/main/java/org/zstack/templateConfig/APIResetTemplateConfigMsg.java |
| 5 | `APIRevertTemplateConfigMsg` | premium/mevoco/src/main/java/org/zstack/templateConfig/APIRevertTemplateConfigMsg.java |
| 6 | `APIUpdateTemplateConfigMsg` | premium/mevoco/src/main/java/org/zstack/templateConfig/APIUpdateTemplateConfigMsg.java |

### `org.zstack.usbDevice`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAttachUsbDeviceToVmMsg` | premium/mevoco/src/main/java/org/zstack/usbDevice/APIAttachUsbDeviceToVmMsg.java |
| 2 | `APIDetachUsbDeviceFromVmMsg` | premium/mevoco/src/main/java/org/zstack/usbDevice/APIDetachUsbDeviceFromVmMsg.java |
| 3 | `APIGetUsbDeviceCandidatesForAttachingVmMsg` | premium/mevoco/src/main/java/org/zstack/usbDevice/APIGetUsbDeviceCandidatesForAttachingVmMsg.java |
| 4 | `APIQueryUsbDeviceMsg` | premium/mevoco/src/main/java/org/zstack/usbDevice/APIQueryUsbDeviceMsg.java |
| 5 | `APIUpdateUsbDeviceMsg` | premium/mevoco/src/main/java/org/zstack/usbDevice/APIUpdateUsbDeviceMsg.java |

### `org.zstack.vmware`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddVCenterMsg` | premium/mevoco/src/main/java/org/zstack/vmware/APIAddVCenterMsg.java |
| 2 | `APIDeleteVCenterMsg` | premium/mevoco/src/main/java/org/zstack/vmware/APIDeleteVCenterMsg.java |
| 3 | `APIGetVCenterDVSwitchesMsg` | premium/mevoco/src/main/java/org/zstack/vmware/APIGetVCenterDVSwitchesMsg.java |
| 4 | `APIQueryVCenterBackupStorageMsg` | premium/mevoco/src/main/java/org/zstack/vmware/APIQueryVCenterBackupStorageMsg.java |
| 5 | `APIQueryVCenterClusterMsg` | premium/mevoco/src/main/java/org/zstack/vmware/APIQueryVCenterClusterMsg.java |
| 6 | `APIQueryVCenterDatacenterMsg` | premium/mevoco/src/main/java/org/zstack/vmware/APIQueryVCenterDatacenterMsg.java |
| 7 | `APIQueryVCenterMsg` | premium/mevoco/src/main/java/org/zstack/vmware/APIQueryVCenterMsg.java |
| 8 | `APIQueryVCenterPrimaryStorageMsg` | premium/mevoco/src/main/java/org/zstack/vmware/APIQueryVCenterPrimaryStorageMsg.java |
| 9 | `APIQueryVCenterResourcePoolMsg` | premium/mevoco/src/main/java/org/zstack/vmware/APIQueryVCenterResourcePoolMsg.java |
| 10 | `APISyncVCenterMsg` | premium/mevoco/src/main/java/org/zstack/vmware/APISyncVCenterMsg.java |
| 11 | `APIUpdateVCenterMsg` | premium/mevoco/src/main/java/org/zstack/vmware/APIUpdateVCenterMsg.java |

### `org.zstack.vrouterRoute`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddVRouterRouteEntryMsg` | premium/mevoco/src/main/java/org/zstack/vrouterRoute/APIAddVRouterRouteEntryMsg.java |
| 2 | `APIAttachVRouterRouteTableToVRouterMsg` | premium/mevoco/src/main/java/org/zstack/vrouterRoute/APIAttachVRouterRouteTableToVRouterMsg.java |
| 3 | `APICreateVRouterRouteTableMsg` | premium/mevoco/src/main/java/org/zstack/vrouterRoute/APICreateVRouterRouteTableMsg.java |
| 4 | `APIDeleteVRouterRouteEntryMsg` | premium/mevoco/src/main/java/org/zstack/vrouterRoute/APIDeleteVRouterRouteEntryMsg.java |
| 5 | `APIDeleteVRouterRouteTableMsg` | premium/mevoco/src/main/java/org/zstack/vrouterRoute/APIDeleteVRouterRouteTableMsg.java |
| 6 | `APIDetachVRouterRouteTableFromVRouterMsg` | premium/mevoco/src/main/java/org/zstack/vrouterRoute/APIDetachVRouterRouteTableFromVRouterMsg.java |
| 7 | `APIGetVRouterRouteTableMsg` | premium/mevoco/src/main/java/org/zstack/vrouterRoute/APIGetVRouterRouteTableMsg.java |
| 8 | `APIQueryVirtualRouterVRouterRouteTableRefMsg` | premium/mevoco/src/main/java/org/zstack/vrouterRoute/APIQueryVirtualRouterVRouterRouteTableRefMsg.java |
| 9 | `APIQueryVRouterRouteEntryMsg` | premium/mevoco/src/main/java/org/zstack/vrouterRoute/APIQueryVRouterRouteEntryMsg.java |
| 10 | `APIQueryVRouterRouteTableMsg` | premium/mevoco/src/main/java/org/zstack/vrouterRoute/APIQueryVRouterRouteTableMsg.java |
| 11 | `APIUpdateVRouterRouteTableMsg` | premium/mevoco/src/main/java/org/zstack/vrouterRoute/APIUpdateVRouterRouteTableMsg.java |

---

<a id="module-premiumplugin-premiumaliyunproxy"></a>

## 模块：`premium/plugin-premium/aliyunproxy`

### `org.zstack.aliyunproxy.vpc`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APICreateAliyunProxyVpcMsg` | premium/plugin-premium/aliyunproxy/src/main/java/org/zstack/aliyunproxy/vpc/APICreateAliyunProxyVpcMsg.java |
| 2 | `APICreateAliyunProxyVSwitchMsg` | premium/plugin-premium/aliyunproxy/src/main/java/org/zstack/aliyunproxy/vpc/APICreateAliyunProxyVSwitchMsg.java |
| 3 | `APIDeleteAliyunProxyVpcMsg` | premium/plugin-premium/aliyunproxy/src/main/java/org/zstack/aliyunproxy/vpc/APIDeleteAliyunProxyVpcMsg.java |
| 4 | `APIDeleteAliyunProxyVSwitchMsg` | premium/plugin-premium/aliyunproxy/src/main/java/org/zstack/aliyunproxy/vpc/APIDeleteAliyunProxyVSwitchMsg.java |
| 5 | `APIQueryAliyunProxyVpcMsg` | premium/plugin-premium/aliyunproxy/src/main/java/org/zstack/aliyunproxy/vpc/APIQueryAliyunProxyVpcMsg.java |
| 6 | `APIQueryAliyunProxyVSwitchMsg` | premium/plugin-premium/aliyunproxy/src/main/java/org/zstack/aliyunproxy/vpc/APIQueryAliyunProxyVSwitchMsg.java |
| 7 | `APIUpdateAliyunProxyVpcMsg` | premium/plugin-premium/aliyunproxy/src/main/java/org/zstack/aliyunproxy/vpc/APIUpdateAliyunProxyVpcMsg.java |
| 8 | `APIUpdateAliyunProxyVSwitchMsg` | premium/plugin-premium/aliyunproxy/src/main/java/org/zstack/aliyunproxy/vpc/APIUpdateAliyunProxyVSwitchMsg.java |

---

<a id="module-premiumplugin-premiumaliyun-storage"></a>

## 模块：`premium/plugin-premium/aliyun-storage`

### `org.zstack.aliyun.ebs.message`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddAliyunEbsBackupStorageMsg` | premium/plugin-premium/aliyun-storage/src/main/java/org/zstack/aliyun/ebs/message/APIAddAliyunEbsBackupStorageMsg.java |
| 2 | `APIAddAliyunEbsPrimaryStorageMsg` | premium/plugin-premium/aliyun-storage/src/main/java/org/zstack/aliyun/ebs/message/APIAddAliyunEbsPrimaryStorageMsg.java |
| 3 | `APIQueryAliyunEbsBackupStorageMsg` | premium/plugin-premium/aliyun-storage/src/main/java/org/zstack/aliyun/ebs/message/APIQueryAliyunEbsBackupStorageMsg.java |
| 4 | `APIQueryAliyunEbsPrimaryStorageMsg` | premium/plugin-premium/aliyun-storage/src/main/java/org/zstack/aliyun/ebs/message/APIQueryAliyunEbsPrimaryStorageMsg.java |
| 5 | `APIUpdateAliyunEbsBackupStorageMsg` | premium/plugin-premium/aliyun-storage/src/main/java/org/zstack/aliyun/ebs/message/APIUpdateAliyunEbsBackupStorageMsg.java |
| 6 | `APIUpdateAliyunEbsPrimaryStorageMsg` | premium/plugin-premium/aliyun-storage/src/main/java/org/zstack/aliyun/ebs/message/APIUpdateAliyunEbsPrimaryStorageMsg.java |

### `org.zstack.aliyun.nas.message`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddAliyunNasAccessGroupMsg` | premium/plugin-premium/aliyun-storage/src/main/java/org/zstack/aliyun/nas/message/APIAddAliyunNasAccessGroupMsg.java |
| 2 | `APIAddAliyunNasFileSystemMsg` | premium/plugin-premium/aliyun-storage/src/main/java/org/zstack/aliyun/nas/message/APIAddAliyunNasFileSystemMsg.java |
| 3 | `APIAddAliyunNasMountTargetMsg` | premium/plugin-premium/aliyun-storage/src/main/java/org/zstack/aliyun/nas/message/APIAddAliyunNasMountTargetMsg.java |
| 4 | `APICreateAliyunNasAccessGroupMsg` | premium/plugin-premium/aliyun-storage/src/main/java/org/zstack/aliyun/nas/message/APICreateAliyunNasAccessGroupMsg.java |
| 5 | `APICreateAliyunNasAccessGroupRuleMsg` | premium/plugin-premium/aliyun-storage/src/main/java/org/zstack/aliyun/nas/message/APICreateAliyunNasAccessGroupRuleMsg.java |
| 6 | `APICreateAliyunNasFileSystemMsg` | premium/plugin-premium/aliyun-storage/src/main/java/org/zstack/aliyun/nas/message/APICreateAliyunNasFileSystemMsg.java |
| 7 | `APICreateAliyunNasMountTargetMsg` | premium/plugin-premium/aliyun-storage/src/main/java/org/zstack/aliyun/nas/message/APICreateAliyunNasMountTargetMsg.java |
| 8 | `APIDeleteAliyunNasAccessGroupMsg` | premium/plugin-premium/aliyun-storage/src/main/java/org/zstack/aliyun/nas/message/APIDeleteAliyunNasAccessGroupMsg.java |
| 9 | `APIDeleteAliyunNasAccessGroupRuleMsg` | premium/plugin-premium/aliyun-storage/src/main/java/org/zstack/aliyun/nas/message/APIDeleteAliyunNasAccessGroupRuleMsg.java |
| 10 | `APIGetAliyunNasAccessGroupRemoteMsg` | premium/plugin-premium/aliyun-storage/src/main/java/org/zstack/aliyun/nas/message/APIGetAliyunNasAccessGroupRemoteMsg.java |
| 11 | `APIGetAliyunNasFileSystemRemoteMsg` | premium/plugin-premium/aliyun-storage/src/main/java/org/zstack/aliyun/nas/message/APIGetAliyunNasFileSystemRemoteMsg.java |
| 12 | `APIGetAliyunNasMountTargetRemoteMsg` | premium/plugin-premium/aliyun-storage/src/main/java/org/zstack/aliyun/nas/message/APIGetAliyunNasMountTargetRemoteMsg.java |
| 13 | `APIQueryAliyunNasAccessGroupMsg` | premium/plugin-premium/aliyun-storage/src/main/java/org/zstack/aliyun/nas/message/APIQueryAliyunNasAccessGroupMsg.java |
| 14 | `APIUpdateAliyunMountTargetMsg` | premium/plugin-premium/aliyun-storage/src/main/java/org/zstack/aliyun/nas/message/APIUpdateAliyunMountTargetMsg.java |
| 15 | `APIUpdateAliyunNasAccessGroupMsg` | premium/plugin-premium/aliyun-storage/src/main/java/org/zstack/aliyun/nas/message/APIUpdateAliyunNasAccessGroupMsg.java |

### `org.zstack.aliyun.nas.storage.message`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddAliyunNasPrimaryStorageMsg` | premium/plugin-premium/aliyun-storage/src/main/java/org/zstack/aliyun/nas/storage/message/APIAddAliyunNasPrimaryStorageMsg.java |

### `org.zstack.aliyun.pangu`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddAliyunPanguPartitionMsg` | premium/plugin-premium/aliyun-storage/src/main/java/org/zstack/aliyun/pangu/APIAddAliyunPanguPartitionMsg.java |
| 2 | `APIDeleteAliyunPanguPartitionMsg` | premium/plugin-premium/aliyun-storage/src/main/java/org/zstack/aliyun/pangu/APIDeleteAliyunPanguPartitionMsg.java |
| 3 | `APIQueryAliyunPanguPartitionMsg` | premium/plugin-premium/aliyun-storage/src/main/java/org/zstack/aliyun/pangu/APIQueryAliyunPanguPartitionMsg.java |
| 4 | `APIUpdateAliyunPanguPartitionMsg` | premium/plugin-premium/aliyun-storage/src/main/java/org/zstack/aliyun/pangu/APIUpdateAliyunPanguPartitionMsg.java |

---

<a id="module-premiumplugin-premiumblock-primary-storage"></a>

## 模块：`premium/plugin-premium/block-primary-storage`

### `org.zstack.storage.primary.block.message`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddBlockPrimaryStorageMsg` | premium/plugin-premium/block-primary-storage/src/main/java/org/zstack/storage/primary/block/message/APIAddBlockPrimaryStorageMsg.java |
| 2 | `APIGetBlockPrimaryStorageMetadataMsg` | premium/plugin-premium/block-primary-storage/src/main/java/org/zstack/storage/primary/block/message/APIGetBlockPrimaryStorageMetadataMsg.java |
| 3 | `APIQueryBlockPrimaryStorageMsg` | premium/plugin-premium/block-primary-storage/src/main/java/org/zstack/storage/primary/block/message/APIQueryBlockPrimaryStorageMsg.java |
| 4 | `APIUpdateBlockPrimaryStorageMsg` | premium/plugin-premium/block-primary-storage/src/main/java/org/zstack/storage/primary/block/message/APIUpdateBlockPrimaryStorageMsg.java |

---

<a id="module-premiumplugin-premiumdaho"></a>

## 模块：`premium/plugin-premium/daho`

### `org.zstack.header.daho.process`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APICreateDahoVllRemoteMsg` | premium/plugin-premium/daho/src/main/java/org/zstack/header/daho/process/APICreateDahoVllRemoteMsg.java |
| 2 | `APIDeleteDahoCloudConnectionMsg` | premium/plugin-premium/daho/src/main/java/org/zstack/header/daho/process/APIDeleteDahoCloudConnectionMsg.java |
| 3 | `APIDeleteDahoDataCenterConnectionMsg` | premium/plugin-premium/daho/src/main/java/org/zstack/header/daho/process/APIDeleteDahoDataCenterConnectionMsg.java |
| 4 | `APIDeleteDahoVllMsg` | premium/plugin-premium/daho/src/main/java/org/zstack/header/daho/process/APIDeleteDahoVllMsg.java |
| 5 | `APIQueryDahoCloudConnectionMsg` | premium/plugin-premium/daho/src/main/java/org/zstack/header/daho/process/APIQueryDahoCloudConnectionMsg.java |
| 6 | `APIQueryDahoDataCenterConnectionMsg` | premium/plugin-premium/daho/src/main/java/org/zstack/header/daho/process/APIQueryDahoDataCenterConnectionMsg.java |
| 7 | `APIQueryDahoVllMsg` | premium/plugin-premium/daho/src/main/java/org/zstack/header/daho/process/APIQueryDahoVllMsg.java |
| 8 | `APIQueryDahoVllVbrRefMsg` | premium/plugin-premium/daho/src/main/java/org/zstack/header/daho/process/APIQueryDahoVllVbrRefMsg.java |
| 9 | `APISyncDahoCloudConnectionMsg` | premium/plugin-premium/daho/src/main/java/org/zstack/header/daho/process/APISyncDahoCloudConnectionMsg.java |
| 10 | `APISyncDahoDataCenterConnectionMsg` | premium/plugin-premium/daho/src/main/java/org/zstack/header/daho/process/APISyncDahoDataCenterConnectionMsg.java |
| 11 | `APISyncDahoVllMsg` | premium/plugin-premium/daho/src/main/java/org/zstack/header/daho/process/APISyncDahoVllMsg.java |
| 12 | `APIUpdateDahoCloudConnectionMsg` | premium/plugin-premium/daho/src/main/java/org/zstack/header/daho/process/APIUpdateDahoCloudConnectionMsg.java |
| 13 | `APIUpdateDahoDataCenterConnectionMsg` | premium/plugin-premium/daho/src/main/java/org/zstack/header/daho/process/APIUpdateDahoDataCenterConnectionMsg.java |
| 14 | `APIUpdateDahoVllMsg` | premium/plugin-premium/daho/src/main/java/org/zstack/header/daho/process/APIUpdateDahoVllMsg.java |

---

<a id="module-premiumplugin-premiumflowmeter"></a>

## 模块：`premium/plugin-premium/flowMeter`

### `org.zstack.header.flowMeter`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddVRouterNetworksToFlowMeterMsg` | premium/plugin-premium/flowMeter/src/main/java/org/zstack/header/flowMeter/APIAddVRouterNetworksToFlowMeterMsg.java |
| 2 | `APICreateFlowCollectorMsg` | premium/plugin-premium/flowMeter/src/main/java/org/zstack/header/flowMeter/APICreateFlowCollectorMsg.java |
| 3 | `APICreateFlowMeterMsg` | premium/plugin-premium/flowMeter/src/main/java/org/zstack/header/flowMeter/APICreateFlowMeterMsg.java |
| 4 | `APIDeleteFlowCollectorMsg` | premium/plugin-premium/flowMeter/src/main/java/org/zstack/header/flowMeter/APIDeleteFlowCollectorMsg.java |
| 5 | `APIDeleteFlowMeterMsg` | premium/plugin-premium/flowMeter/src/main/java/org/zstack/header/flowMeter/APIDeleteFlowMeterMsg.java |
| 6 | `APIGetFlowMeterRouterIdMsg` | premium/plugin-premium/flowMeter/src/main/java/org/zstack/header/flowMeter/APIGetFlowMeterRouterIdMsg.java |
| 7 | `APIGetVpcAttachedNetflowMsg` | premium/plugin-premium/flowMeter/src/main/java/org/zstack/header/flowMeter/APIGetVpcAttachedNetflowMsg.java |
| 8 | `APIGetVRouterFlowCounterMsg` | premium/plugin-premium/flowMeter/src/main/java/org/zstack/header/flowMeter/APIGetVRouterFlowCounterMsg.java |
| 9 | `APIQueryFlowCollectorMsg` | premium/plugin-premium/flowMeter/src/main/java/org/zstack/header/flowMeter/APIQueryFlowCollectorMsg.java |
| 10 | `APIQueryFlowMeterMsg` | premium/plugin-premium/flowMeter/src/main/java/org/zstack/header/flowMeter/APIQueryFlowMeterMsg.java |
| 11 | `APIQueryVRouterFlowMeterNetworkMsg` | premium/plugin-premium/flowMeter/src/main/java/org/zstack/header/flowMeter/APIQueryVRouterFlowMeterNetworkMsg.java |
| 12 | `APIRemoveVRouterNetworksFromFlowMeterMsg` | premium/plugin-premium/flowMeter/src/main/java/org/zstack/header/flowMeter/APIRemoveVRouterNetworksFromFlowMeterMsg.java |
| 13 | `APISetFlowMeterRouterIdMsg` | premium/plugin-premium/flowMeter/src/main/java/org/zstack/header/flowMeter/APISetFlowMeterRouterIdMsg.java |
| 14 | `APIUpdateFlowCollectorMsg` | premium/plugin-premium/flowMeter/src/main/java/org/zstack/header/flowMeter/APIUpdateFlowCollectorMsg.java |
| 15 | `APIUpdateFlowMeterMsg` | premium/plugin-premium/flowMeter/src/main/java/org/zstack/header/flowMeter/APIUpdateFlowMeterMsg.java |

---

<a id="module-premiumplugin-premiumministorage"></a>

## 模块：`premium/plugin-premium/ministorage`

### `org.zstack.storage.primary.ministorage`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddMiniStorageMsg` | premium/plugin-premium/ministorage/src/main/java/org/zstack/storage/primary/ministorage/APIAddMiniStorageMsg.java |
| 2 | `APIQueryMiniStorageHostRefMsg` | premium/plugin-premium/ministorage/src/main/java/org/zstack/storage/primary/ministorage/APIQueryMiniStorageHostRefMsg.java |
| 3 | `APIQueryMiniStorageMsg` | premium/plugin-premium/ministorage/src/main/java/org/zstack/storage/primary/ministorage/APIQueryMiniStorageMsg.java |
| 4 | `APIQueryMiniStorageResourceReplicationMsg` | premium/plugin-premium/ministorage/src/main/java/org/zstack/storage/primary/ministorage/APIQueryMiniStorageResourceReplicationMsg.java |
| 5 | `APIRecoverResourceSplitBrainMsg` | premium/plugin-premium/ministorage/src/main/java/org/zstack/storage/primary/ministorage/APIRecoverResourceSplitBrainMsg.java |

---

<a id="module-premiumplugin-premiummulticast-router"></a>

## 模块：`premium/plugin-premium/multicast-router`

### `org.zstack.multicast.router.header`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddRendezvousPointToMulticastRouterMsg` | premium/plugin-premium/multicast-router/src/main/java/org/zstack/multicast/router/header/APIAddRendezvousPointToMulticastRouterMsg.java |
| 2 | `APIChangeMulticastRouterStateMsg` | premium/plugin-premium/multicast-router/src/main/java/org/zstack/multicast/router/header/APIChangeMulticastRouterStateMsg.java |
| 3 | `APICreateMulticastRouterMsg` | premium/plugin-premium/multicast-router/src/main/java/org/zstack/multicast/router/header/APICreateMulticastRouterMsg.java |
| 4 | `APIDeleteMulticastRouterMsg` | premium/plugin-premium/multicast-router/src/main/java/org/zstack/multicast/router/header/APIDeleteMulticastRouterMsg.java |
| 5 | `APIGetVpcMulticastRouteMsg` | premium/plugin-premium/multicast-router/src/main/java/org/zstack/multicast/router/header/APIGetVpcMulticastRouteMsg.java |
| 6 | `APIQueryMulticastRouterMsg` | premium/plugin-premium/multicast-router/src/main/java/org/zstack/multicast/router/header/APIQueryMulticastRouterMsg.java |
| 7 | `APIRemoveRendezvousPointFromMulticastRouterMsg` | premium/plugin-premium/multicast-router/src/main/java/org/zstack/multicast/router/header/APIRemoveRendezvousPointFromMulticastRouterMsg.java |

---

<a id="module-premiumplugin-premiumovf"></a>

## 模块：`premium/plugin-premium/ovf`

### `org.zstack.ovf.api`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APICreateVmInstanceFromOvfMsg` | premium/plugin-premium/ovf/src/main/java/org/zstack/ovf/api/APICreateVmInstanceFromOvfMsg.java |
| 2 | `APIDeleteImagePackageMsg` | premium/plugin-premium/ovf/src/main/java/org/zstack/ovf/api/APIDeleteImagePackageMsg.java |
| 3 | `APIExportVmOvaPackageMsg` | premium/plugin-premium/ovf/src/main/java/org/zstack/ovf/api/APIExportVmOvaPackageMsg.java |
| 4 | `APIParseOvfMsg` | premium/plugin-premium/ovf/src/main/java/org/zstack/ovf/api/APIParseOvfMsg.java |
| 5 | `APIQueryImagePackageMsg` | premium/plugin-premium/ovf/src/main/java/org/zstack/ovf/api/APIQueryImagePackageMsg.java |
| 6 | `APIUpdateImagePackageMsg` | premium/plugin-premium/ovf/src/main/java/org/zstack/ovf/api/APIUpdateImagePackageMsg.java |

---

<a id="module-premiumplugin-premiumpolicyroute"></a>

## 模块：`premium/plugin-premium/policyRoute`

### `org.zstack.policyRoute`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAttachPolicyRouteRuleSetToL3Msg` | premium/plugin-premium/policyRoute/src/main/java/org/zstack/policyRoute/APIAttachPolicyRouteRuleSetToL3Msg.java |
| 2 | `APICreatePolicyRouteRuleMsg` | premium/plugin-premium/policyRoute/src/main/java/org/zstack/policyRoute/APICreatePolicyRouteRuleMsg.java |
| 3 | `APICreatePolicyRouteRuleSetMsg` | premium/plugin-premium/policyRoute/src/main/java/org/zstack/policyRoute/APICreatePolicyRouteRuleSetMsg.java |
| 4 | `APICreatePolicyRouteTableMsg` | premium/plugin-premium/policyRoute/src/main/java/org/zstack/policyRoute/APICreatePolicyRouteTableMsg.java |
| 5 | `APICreatePolicyRouteTableRouteEntryMsg` | premium/plugin-premium/policyRoute/src/main/java/org/zstack/policyRoute/APICreatePolicyRouteTableRouteEntryMsg.java |
| 6 | `APIDeletePolicyRouteRuleMsg` | premium/plugin-premium/policyRoute/src/main/java/org/zstack/policyRoute/APIDeletePolicyRouteRuleMsg.java |
| 7 | `APIDeletePolicyRouteRuleSetMsg` | premium/plugin-premium/policyRoute/src/main/java/org/zstack/policyRoute/APIDeletePolicyRouteRuleSetMsg.java |
| 8 | `APIDeletePolicyRouteTableMsg` | premium/plugin-premium/policyRoute/src/main/java/org/zstack/policyRoute/APIDeletePolicyRouteTableMsg.java |
| 9 | `APIDeletePolicyRouteTableRouteEntryMsg` | premium/plugin-premium/policyRoute/src/main/java/org/zstack/policyRoute/APIDeletePolicyRouteTableRouteEntryMsg.java |
| 10 | `APIDetachPolicyRouteRuleSetFromL3Msg` | premium/plugin-premium/policyRoute/src/main/java/org/zstack/policyRoute/APIDetachPolicyRouteRuleSetFromL3Msg.java |
| 11 | `APIGetPolicyRouteRuleSetFromVirtualRouterMsg` | premium/plugin-premium/policyRoute/src/main/java/org/zstack/policyRoute/APIGetPolicyRouteRuleSetFromVirtualRouterMsg.java |
| 12 | `APIQueryPolicyRouteRuleMsg` | premium/plugin-premium/policyRoute/src/main/java/org/zstack/policyRoute/APIQueryPolicyRouteRuleMsg.java |
| 13 | `APIQueryPolicyRouteRuleSetL3RefMsg` | premium/plugin-premium/policyRoute/src/main/java/org/zstack/policyRoute/APIQueryPolicyRouteRuleSetL3RefMsg.java |
| 14 | `APIQueryPolicyRouteRuleSetMsg` | premium/plugin-premium/policyRoute/src/main/java/org/zstack/policyRoute/APIQueryPolicyRouteRuleSetMsg.java |
| 15 | `APIQueryPolicyRouteRuleSetVRouterRefMsg` | premium/plugin-premium/policyRoute/src/main/java/org/zstack/policyRoute/APIQueryPolicyRouteRuleSetVRouterRefMsg.java |
| 16 | `APIQueryPolicyRouteTableMsg` | premium/plugin-premium/policyRoute/src/main/java/org/zstack/policyRoute/APIQueryPolicyRouteTableMsg.java |
| 17 | `APIQueryPolicyRouteTableRouteEntryMsg` | premium/plugin-premium/policyRoute/src/main/java/org/zstack/policyRoute/APIQueryPolicyRouteTableRouteEntryMsg.java |
| 18 | `APIQueryPolicyRouteTableVRouterRefMsg` | premium/plugin-premium/policyRoute/src/main/java/org/zstack/policyRoute/APIQueryPolicyRouteTableVRouterRefMsg.java |
| 19 | `APIUpdatePolicyRouteRuleSetMsg` | premium/plugin-premium/policyRoute/src/main/java/org/zstack/policyRoute/APIUpdatePolicyRouteRuleSetMsg.java |

---

<a id="module-premiumplugin-premiumportmirror"></a>

## 模块：`premium/plugin-premium/portMirror`

### `org.zstack.header.portMirror`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIChangePortMirrorStateMsg` | premium/plugin-premium/portMirror/src/main/java/org/zstack/header/portMirror/APIChangePortMirrorStateMsg.java |
| 2 | `APICreatePortMirrorMsg` | premium/plugin-premium/portMirror/src/main/java/org/zstack/header/portMirror/APICreatePortMirrorMsg.java |
| 3 | `APICreatePortMirrorSessionMsg` | premium/plugin-premium/portMirror/src/main/java/org/zstack/header/portMirror/APICreatePortMirrorSessionMsg.java |
| 4 | `APIDeletePortMirrorMsg` | premium/plugin-premium/portMirror/src/main/java/org/zstack/header/portMirror/APIDeletePortMirrorMsg.java |
| 5 | `APIDeletePortMirrorSessionMsg` | premium/plugin-premium/portMirror/src/main/java/org/zstack/header/portMirror/APIDeletePortMirrorSessionMsg.java |
| 6 | `APIGetCandidateVmNicsForPortMirrorMsg` | premium/plugin-premium/portMirror/src/main/java/org/zstack/header/portMirror/APIGetCandidateVmNicsForPortMirrorMsg.java |
| 7 | `APIQueryPortMirrorMsg` | premium/plugin-premium/portMirror/src/main/java/org/zstack/header/portMirror/APIQueryPortMirrorMsg.java |
| 8 | `APIQueryPortMirrorNetworkUsedIpMsg` | premium/plugin-premium/portMirror/src/main/java/org/zstack/header/portMirror/APIQueryPortMirrorNetworkUsedIpMsg.java |
| 9 | `APIQueryPortMirrorSessionMsg` | premium/plugin-premium/portMirror/src/main/java/org/zstack/header/portMirror/APIQueryPortMirrorSessionMsg.java |
| 10 | `APIUpdatePortMirrorMsg` | premium/plugin-premium/portMirror/src/main/java/org/zstack/header/portMirror/APIUpdatePortMirrorMsg.java |

---

<a id="module-premiumplugin-premiumrouteprotocol"></a>

## 模块：`premium/plugin-premium/routeProtocol`

### `org.zstack.header.protocol`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddVRouterNetworksToOspfAreaMsg` | premium/plugin-premium/routeProtocol/src/main/java/org/zstack/header/protocol/APIAddVRouterNetworksToOspfAreaMsg.java |
| 2 | `APICreateVRouterOspfAreaMsg` | premium/plugin-premium/routeProtocol/src/main/java/org/zstack/header/protocol/APICreateVRouterOspfAreaMsg.java |
| 3 | `APIDeleteVRouterOspfAreaMsg` | premium/plugin-premium/routeProtocol/src/main/java/org/zstack/header/protocol/APIDeleteVRouterOspfAreaMsg.java |
| 4 | `APIGetVpcAttachedOspfMsg` | premium/plugin-premium/routeProtocol/src/main/java/org/zstack/header/protocol/APIGetVpcAttachedOspfMsg.java |
| 5 | `APIGetVRouterOspfNeighborMsg` | premium/plugin-premium/routeProtocol/src/main/java/org/zstack/header/protocol/APIGetVRouterOspfNeighborMsg.java |
| 6 | `APIGetVRouterRouterIdMsg` | premium/plugin-premium/routeProtocol/src/main/java/org/zstack/header/protocol/APIGetVRouterRouterIdMsg.java |
| 7 | `APIQueryVRouterOspfAreaMsg` | premium/plugin-premium/routeProtocol/src/main/java/org/zstack/header/protocol/APIQueryVRouterOspfAreaMsg.java |
| 8 | `APIQueryVRouterOspfNetworkMsg` | premium/plugin-premium/routeProtocol/src/main/java/org/zstack/header/protocol/APIQueryVRouterOspfNetworkMsg.java |
| 9 | `APIRemoveVRouterNetworksFromOspfAreaMsg` | premium/plugin-premium/routeProtocol/src/main/java/org/zstack/header/protocol/APIRemoveVRouterNetworksFromOspfAreaMsg.java |
| 10 | `APISetVRouterRouterIdMsg` | premium/plugin-premium/routeProtocol/src/main/java/org/zstack/header/protocol/APISetVRouterRouterIdMsg.java |
| 11 | `APIUpdateVRouterOspfAreaMsg` | premium/plugin-premium/routeProtocol/src/main/java/org/zstack/header/protocol/APIUpdateVRouterOspfAreaMsg.java |

---

<a id="module-premiumplugin-premiumsns-aliyun-sms"></a>

## 模块：`premium/plugin-premium/sns-aliyun-sms`

### `org.zstack.sns.platform.aliyunsms`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APICreateSNSAliyunSmsEndpointMsg` | premium/plugin-premium/sns-aliyun-sms/src/main/java/org/zstack/sns/platform/aliyunsms/APICreateSNSAliyunSmsEndpointMsg.java |
| 2 | `APIValidateSNSAliyunSmsEndpointMsg` | premium/plugin-premium/sns-aliyun-sms/src/main/java/org/zstack/sns/platform/aliyunsms/APIValidateSNSAliyunSmsEndpointMsg.java |

---

<a id="module-premiumplugin-premiumsoftware-package-plugin"></a>

## 模块：`premium/plugin-premium/software-package-plugin`

### `org.zstack.softwarePackage.header`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APICleanSoftwarePackageMsg` | premium/plugin-premium/software-package-plugin/src/main/java/org/zstack/softwarePackage/header/APICleanSoftwarePackageMsg.java |
| 2 | `APIGetDirectoryUsageMsg` | premium/plugin-premium/software-package-plugin/src/main/java/org/zstack/softwarePackage/header/APIGetDirectoryUsageMsg.java |
| 3 | `APIGetUploadSoftwarePackageJobDetailsMsg` | premium/plugin-premium/software-package-plugin/src/main/java/org/zstack/softwarePackage/header/APIGetUploadSoftwarePackageJobDetailsMsg.java |
| 4 | `APIInstallSoftwarePackageMsg` | premium/plugin-premium/software-package-plugin/src/main/java/org/zstack/softwarePackage/header/APIInstallSoftwarePackageMsg.java |
| 5 | `APIQuerySoftwarePackageMsg` | premium/plugin-premium/software-package-plugin/src/main/java/org/zstack/softwarePackage/header/APIQuerySoftwarePackageMsg.java |
| 6 | `APIUninstallSoftwarePackageMsg` | premium/plugin-premium/software-package-plugin/src/main/java/org/zstack/softwarePackage/header/APIUninstallSoftwarePackageMsg.java |
| 7 | `APIUploadSoftwarePackageMsg` | premium/plugin-premium/software-package-plugin/src/main/java/org/zstack/softwarePackage/header/APIUploadSoftwarePackageMsg.java |

---

<a id="module-premiumplugin-premiumsso-plugin"></a>

## 模块：`premium/plugin-premium/sso-plugin`

### `org.zstack.sso.header`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APICreateCasClientMsg` | premium/plugin-premium/sso-plugin/src/main/java/org/zstack/sso/header/APICreateCasClientMsg.java |
| 2 | `APICreateOAuthClientMsg` | premium/plugin-premium/sso-plugin/src/main/java/org/zstack/sso/header/APICreateOAuthClientMsg.java |
| 3 | `APICreateSSORedirectTemplateMsg` | premium/plugin-premium/sso-plugin/src/main/java/org/zstack/sso/header/APICreateSSORedirectTemplateMsg.java |
| 4 | `APIDeleteSSOClientMsg` | premium/plugin-premium/sso-plugin/src/main/java/org/zstack/sso/header/APIDeleteSSOClientMsg.java |
| 5 | `APIDeleteSSORedirectTemplateMsg` | premium/plugin-premium/sso-plugin/src/main/java/org/zstack/sso/header/APIDeleteSSORedirectTemplateMsg.java |
| 6 | `APIGetOAuth2TokenMsg` | premium/plugin-premium/sso-plugin/src/main/java/org/zstack/sso/header/APIGetOAuth2TokenMsg.java |
| 7 | `APIGetSSOClientMsg` | premium/plugin-premium/sso-plugin/src/main/java/org/zstack/sso/header/APIGetSSOClientMsg.java |
| 8 | `APIUpdateCasClientMsg` | premium/plugin-premium/sso-plugin/src/main/java/org/zstack/sso/header/APIUpdateCasClientMsg.java |
| 9 | `APIUpdateOAuthClientMsg` | premium/plugin-premium/sso-plugin/src/main/java/org/zstack/sso/header/APIUpdateOAuthClientMsg.java |
| 10 | `APIUpdateSSORedirectTemplateMsg` | premium/plugin-premium/sso-plugin/src/main/java/org/zstack/sso/header/APIUpdateSSORedirectTemplateMsg.java |

---

<a id="module-premiumplugin-premiumstorage-device"></a>

## 模块：`premium/plugin-premium/storage-device`

### `org.zstack.storage.device.fibreChannel`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIQueryFiberChannelLunMsg` | premium/plugin-premium/storage-device/src/main/java/org/zstack/storage/device/fibreChannel/APIQueryFiberChannelLunMsg.java |
| 2 | `APIQueryFiberChannelStorageMsg` | premium/plugin-premium/storage-device/src/main/java/org/zstack/storage/device/fibreChannel/APIQueryFiberChannelStorageMsg.java |
| 3 | `APIRefreshFiberChannelStorageMsg` | premium/plugin-premium/storage-device/src/main/java/org/zstack/storage/device/fibreChannel/APIRefreshFiberChannelStorageMsg.java |

### `org.zstack.storage.device.hba`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIQueryFcHbaDeviceMsg` | premium/plugin-premium/storage-device/src/main/java/org/zstack/storage/device/hba/APIQueryFcHbaDeviceMsg.java |

### `org.zstack.storage.device.iscsi`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddIscsiServerMsg` | premium/plugin-premium/storage-device/src/main/java/org/zstack/storage/device/iscsi/APIAddIscsiServerMsg.java |
| 2 | `APIAttachIscsiServerToClusterMsg` | premium/plugin-premium/storage-device/src/main/java/org/zstack/storage/device/iscsi/APIAttachIscsiServerToClusterMsg.java |
| 3 | `APIDeleteIscsiServerMsg` | premium/plugin-premium/storage-device/src/main/java/org/zstack/storage/device/iscsi/APIDeleteIscsiServerMsg.java |
| 4 | `APIDetachIscsiServerFromClusterMsg` | premium/plugin-premium/storage-device/src/main/java/org/zstack/storage/device/iscsi/APIDetachIscsiServerFromClusterMsg.java |
| 5 | `APIQueryIscsiLunMsg` | premium/plugin-premium/storage-device/src/main/java/org/zstack/storage/device/iscsi/APIQueryIscsiLunMsg.java |
| 6 | `APIQueryIscsiServerMsg` | premium/plugin-premium/storage-device/src/main/java/org/zstack/storage/device/iscsi/APIQueryIscsiServerMsg.java |
| 7 | `APIRefreshIscsiServerMsg` | premium/plugin-premium/storage-device/src/main/java/org/zstack/storage/device/iscsi/APIRefreshIscsiServerMsg.java |
| 8 | `APIUpdateIscsiServerMsg` | premium/plugin-premium/storage-device/src/main/java/org/zstack/storage/device/iscsi/APIUpdateIscsiServerMsg.java |

### `org.zstack.storage.device.localRaid`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIGetLocalRaidPhysicalDriveSmartMsg` | premium/plugin-premium/storage-device/src/main/java/org/zstack/storage/device/localRaid/APIGetLocalRaidPhysicalDriveSmartMsg.java |
| 2 | `APILocateLocalRaidPhysicalDriveMsg` | premium/plugin-premium/storage-device/src/main/java/org/zstack/storage/device/localRaid/APILocateLocalRaidPhysicalDriveMsg.java |
| 3 | `APIQueryLocalRaidControllerMsg` | premium/plugin-premium/storage-device/src/main/java/org/zstack/storage/device/localRaid/APIQueryLocalRaidControllerMsg.java |
| 4 | `APIQueryLocalRaidPhysicalDriveMsg` | premium/plugin-premium/storage-device/src/main/java/org/zstack/storage/device/localRaid/APIQueryLocalRaidPhysicalDriveMsg.java |
| 5 | `APIQueryPhysicalDriveSelfTestHistoryMsg` | premium/plugin-premium/storage-device/src/main/java/org/zstack/storage/device/localRaid/APIQueryPhysicalDriveSelfTestHistoryMsg.java |
| 6 | `APIRefreshLocalRaidMsg` | premium/plugin-premium/storage-device/src/main/java/org/zstack/storage/device/localRaid/APIRefreshLocalRaidMsg.java |
| 7 | `APISelfTestLocalRaidMsg` | premium/plugin-premium/storage-device/src/main/java/org/zstack/storage/device/localRaid/APISelfTestLocalRaidMsg.java |

### `org.zstack.storage.device.multipath`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIGetHostMultipathTopologyMsg` | premium/plugin-premium/storage-device/src/main/java/org/zstack/storage/device/multipath/APIGetHostMultipathTopologyMsg.java |

### `org.zstack.storage.device.nvme`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddNvmeServerMsg` | premium/plugin-premium/storage-device/src/main/java/org/zstack/storage/device/nvme/APIAddNvmeServerMsg.java |
| 2 | `APIAttachNvmeServerToClusterMsg` | premium/plugin-premium/storage-device/src/main/java/org/zstack/storage/device/nvme/APIAttachNvmeServerToClusterMsg.java |
| 3 | `APIDeleteNvmeServerMsg` | premium/plugin-premium/storage-device/src/main/java/org/zstack/storage/device/nvme/APIDeleteNvmeServerMsg.java |
| 4 | `APIDetachNvmeServerFromClusterMsg` | premium/plugin-premium/storage-device/src/main/java/org/zstack/storage/device/nvme/APIDetachNvmeServerFromClusterMsg.java |
| 5 | `APIQueryNvmeLunMsg` | premium/plugin-premium/storage-device/src/main/java/org/zstack/storage/device/nvme/APIQueryNvmeLunMsg.java |
| 6 | `APIQueryNvmeServerMsg` | premium/plugin-premium/storage-device/src/main/java/org/zstack/storage/device/nvme/APIQueryNvmeServerMsg.java |
| 7 | `APIQueryNvmeTargetMsg` | premium/plugin-premium/storage-device/src/main/java/org/zstack/storage/device/nvme/APIQueryNvmeTargetMsg.java |
| 8 | `APIRefreshNvmeServerMsg` | premium/plugin-premium/storage-device/src/main/java/org/zstack/storage/device/nvme/APIRefreshNvmeServerMsg.java |
| 9 | `APIRefreshNvmeTargetMsg` | premium/plugin-premium/storage-device/src/main/java/org/zstack/storage/device/nvme/APIRefreshNvmeTargetMsg.java |
| 10 | `APIUpdateNvmeServerMsg` | premium/plugin-premium/storage-device/src/main/java/org/zstack/storage/device/nvme/APIUpdateNvmeServerMsg.java |

---

<a id="module-premiumplugin-premiumticket"></a>

## 模块：`premium/plugin-premium/ticket`

### `org.zstack.ticket.api`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddTicketTypesToTicketFlowCollectionMsg` | premium/plugin-premium/ticket/src/main/java/org/zstack/ticket/api/APIAddTicketTypesToTicketFlowCollectionMsg.java |
| 2 | `APIChangeTicketFlowCollectionStateMsg` | premium/plugin-premium/ticket/src/main/java/org/zstack/ticket/api/APIChangeTicketFlowCollectionStateMsg.java |
| 3 | `APIChangeTicketStatusMsg` | premium/plugin-premium/ticket/src/main/java/org/zstack/ticket/api/APIChangeTicketStatusMsg.java |
| 4 | `APICreateTicketFlowMsg` | premium/plugin-premium/ticket/src/main/java/org/zstack/ticket/api/APICreateTicketFlowMsg.java |
| 5 | `APICreateTicketMsg` | premium/plugin-premium/ticket/src/main/java/org/zstack/ticket/api/APICreateTicketMsg.java |
| 6 | `APICreateTickFlowCollectionMsg` | premium/plugin-premium/ticket/src/main/java/org/zstack/ticket/api/APICreateTickFlowCollectionMsg.java |
| 7 | `APIDeleteTicketFlowCollectionMsg` | premium/plugin-premium/ticket/src/main/java/org/zstack/ticket/api/APIDeleteTicketFlowCollectionMsg.java |
| 8 | `APIDeleteTicketMsg` | premium/plugin-premium/ticket/src/main/java/org/zstack/ticket/api/APIDeleteTicketMsg.java |
| 9 | `APIQueryArchiveTicketHistoryMsg` | premium/plugin-premium/ticket/src/main/java/org/zstack/ticket/api/APIQueryArchiveTicketHistoryMsg.java |
| 10 | `APIQueryArchiveTicketMsg` | premium/plugin-premium/ticket/src/main/java/org/zstack/ticket/api/APIQueryArchiveTicketMsg.java |
| 11 | `APIQueryTicketFlowCollectionMsg` | premium/plugin-premium/ticket/src/main/java/org/zstack/ticket/api/APIQueryTicketFlowCollectionMsg.java |
| 12 | `APIQueryTicketFlowMsg` | premium/plugin-premium/ticket/src/main/java/org/zstack/ticket/api/APIQueryTicketFlowMsg.java |
| 13 | `APIQueryTicketHistoryMsg` | premium/plugin-premium/ticket/src/main/java/org/zstack/ticket/api/APIQueryTicketHistoryMsg.java |
| 14 | `APIQueryTicketMsg` | premium/plugin-premium/ticket/src/main/java/org/zstack/ticket/api/APIQueryTicketMsg.java |
| 15 | `APIQueryTicketTypeMsg` | premium/plugin-premium/ticket/src/main/java/org/zstack/ticket/api/APIQueryTicketTypeMsg.java |
| 16 | `APIRemoveTicketTypesFromTicketFlowCollectionMsg` | premium/plugin-premium/ticket/src/main/java/org/zstack/ticket/api/APIRemoveTicketTypesFromTicketFlowCollectionMsg.java |
| 17 | `APIUpdateTicketFlowCollectionMsg` | premium/plugin-premium/ticket/src/main/java/org/zstack/ticket/api/APIUpdateTicketFlowCollectionMsg.java |
| 18 | `APIUpdateTicketRequestMsg` | premium/plugin-premium/ticket/src/main/java/org/zstack/ticket/api/APIUpdateTicketRequestMsg.java |

### `org.zstack.ticket.iam2.api`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddIAM2TicketFlowMsg` | premium/plugin-premium/ticket/src/main/java/org/zstack/ticket/iam2/api/APIAddIAM2TicketFlowMsg.java |
| 2 | `APICreateIAM2TickFlowCollectionMsg` | premium/plugin-premium/ticket/src/main/java/org/zstack/ticket/iam2/api/APICreateIAM2TickFlowCollectionMsg.java |
| 3 | `APIDeleteIAM2TicketFlowMsg` | premium/plugin-premium/ticket/src/main/java/org/zstack/ticket/iam2/api/APIDeleteIAM2TicketFlowMsg.java |
| 4 | `APIUpdateIAM2TicketFlowCollectionMsg` | premium/plugin-premium/ticket/src/main/java/org/zstack/ticket/iam2/api/APIUpdateIAM2TicketFlowCollectionMsg.java |
| 5 | `APIUpdateIAM2TicketFlowMsg` | premium/plugin-premium/ticket/src/main/java/org/zstack/ticket/iam2/api/APIUpdateIAM2TicketFlowMsg.java |

---

<a id="module-premiumplugin-premiumvirtualswitchnetwork"></a>

## 模块：`premium/plugin-premium/virtualSwitchNetwork`

### `org.zstack.network.l2.virtualSwitch.header`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIBatchCreateHostKernelInterfaceMsg` | premium/plugin-premium/virtualSwitchNetwork/src/main/java/org/zstack/network/l2/virtualSwitch/header/APIBatchCreateHostKernelInterfaceMsg.java |
| 2 | `APICreateHostKernelInterfaceMsg` | premium/plugin-premium/virtualSwitchNetwork/src/main/java/org/zstack/network/l2/virtualSwitch/header/APICreateHostKernelInterfaceMsg.java |
| 3 | `APICreateL2PortGroupMsg` | premium/plugin-premium/virtualSwitchNetwork/src/main/java/org/zstack/network/l2/virtualSwitch/header/APICreateL2PortGroupMsg.java |
| 4 | `APICreateL2VirtualSwitchMsg` | premium/plugin-premium/virtualSwitchNetwork/src/main/java/org/zstack/network/l2/virtualSwitch/header/APICreateL2VirtualSwitchMsg.java |
| 5 | `APICreatePortGroupMsg` | premium/plugin-premium/virtualSwitchNetwork/src/main/java/org/zstack/network/l2/virtualSwitch/header/APICreatePortGroupMsg.java |
| 6 | `APIDeleteHostKernelInterfaceMsg` | premium/plugin-premium/virtualSwitchNetwork/src/main/java/org/zstack/network/l2/virtualSwitch/header/APIDeleteHostKernelInterfaceMsg.java |
| 7 | `APIDeletePortGroupMsg` | premium/plugin-premium/virtualSwitchNetwork/src/main/java/org/zstack/network/l2/virtualSwitch/header/APIDeletePortGroupMsg.java |
| 8 | `APIGetCandidateHostKernelInterfacesMsg` | premium/plugin-premium/virtualSwitchNetwork/src/main/java/org/zstack/network/l2/virtualSwitch/header/APIGetCandidateHostKernelInterfacesMsg.java |
| 9 | `APIQueryHostKernelInterfaceMsg` | premium/plugin-premium/virtualSwitchNetwork/src/main/java/org/zstack/network/l2/virtualSwitch/header/APIQueryHostKernelInterfaceMsg.java |
| 10 | `APIQueryL2PortGroupNetworkMsg` | premium/plugin-premium/virtualSwitchNetwork/src/main/java/org/zstack/network/l2/virtualSwitch/header/APIQueryL2PortGroupNetworkMsg.java |
| 11 | `APIQueryL2VirtualSwitchNetworkMsg` | premium/plugin-premium/virtualSwitchNetwork/src/main/java/org/zstack/network/l2/virtualSwitch/header/APIQueryL2VirtualSwitchNetworkMsg.java |
| 12 | `APIQueryPortGroupMsg` | premium/plugin-premium/virtualSwitchNetwork/src/main/java/org/zstack/network/l2/virtualSwitch/header/APIQueryPortGroupMsg.java |
| 13 | `APIQueryUplinkGroupMsg` | premium/plugin-premium/virtualSwitchNetwork/src/main/java/org/zstack/network/l2/virtualSwitch/header/APIQueryUplinkGroupMsg.java |
| 14 | `APIUpdateHostKernelInterfaceMsg` | premium/plugin-premium/virtualSwitchNetwork/src/main/java/org/zstack/network/l2/virtualSwitch/header/APIUpdateHostKernelInterfaceMsg.java |
| 15 | `APIUpdatePortGroupMsg` | premium/plugin-premium/virtualSwitchNetwork/src/main/java/org/zstack/network/l2/virtualSwitch/header/APIUpdatePortGroupMsg.java |
| 16 | `APIUpdateVirtualSwitchUplinkBondingsMsg` | premium/plugin-premium/virtualSwitchNetwork/src/main/java/org/zstack/network/l2/virtualSwitch/header/APIUpdateVirtualSwitchUplinkBondingsMsg.java |
| 17 | `APIUpdateVirtualSwitchUplinkGroupMsg` | premium/plugin-premium/virtualSwitchNetwork/src/main/java/org/zstack/network/l2/virtualSwitch/header/APIUpdateVirtualSwitchUplinkGroupMsg.java |

---

<a id="module-premiumplugin-premiumvpcfirewall"></a>

## 模块：`premium/plugin-premium/vpcFirewall`

### `org.zstack.vpcfirewall.api`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIApplyRuleSetChangesMsg` | premium/plugin-premium/vpcFirewall/src/main/java/org/zstack/vpcfirewall/api/APIApplyRuleSetChangesMsg.java |
| 2 | `APIAttachFirewallRuleSetToL3Msg` | premium/plugin-premium/vpcFirewall/src/main/java/org/zstack/vpcfirewall/api/APIAttachFirewallRuleSetToL3Msg.java |
| 3 | `APIChangeFirewallRuleStateMsg` | premium/plugin-premium/vpcFirewall/src/main/java/org/zstack/vpcfirewall/api/APIChangeFirewallRuleStateMsg.java |
| 4 | `APICheckFirewallRuleConfigFileMsg` | premium/plugin-premium/vpcFirewall/src/main/java/org/zstack/vpcfirewall/api/APICheckFirewallRuleConfigFileMsg.java |
| 5 | `APICreateFirewallIpSetTemplateMsg` | premium/plugin-premium/vpcFirewall/src/main/java/org/zstack/vpcfirewall/api/APICreateFirewallIpSetTemplateMsg.java |
| 6 | `APICreateFirewallRuleFromConfigFileMsg` | premium/plugin-premium/vpcFirewall/src/main/java/org/zstack/vpcfirewall/api/APICreateFirewallRuleFromConfigFileMsg.java |
| 7 | `APICreateFirewallRuleMsg` | premium/plugin-premium/vpcFirewall/src/main/java/org/zstack/vpcfirewall/api/APICreateFirewallRuleMsg.java |
| 8 | `APICreateFirewallRuleSetMsg` | premium/plugin-premium/vpcFirewall/src/main/java/org/zstack/vpcfirewall/api/APICreateFirewallRuleSetMsg.java |
| 9 | `APICreateFirewallRuleTemplateMsg` | premium/plugin-premium/vpcFirewall/src/main/java/org/zstack/vpcfirewall/api/APICreateFirewallRuleTemplateMsg.java |
| 10 | `APICreateVpcFirewallMsg` | premium/plugin-premium/vpcFirewall/src/main/java/org/zstack/vpcfirewall/api/APICreateVpcFirewallMsg.java |
| 11 | `APIDeleteFirewallIpSetTemplateMsg` | premium/plugin-premium/vpcFirewall/src/main/java/org/zstack/vpcfirewall/api/APIDeleteFirewallIpSetTemplateMsg.java |
| 12 | `APIDeleteFirewallMsg` | premium/plugin-premium/vpcFirewall/src/main/java/org/zstack/vpcfirewall/api/APIDeleteFirewallMsg.java |
| 13 | `APIDeleteFirewallRuleMsg` | premium/plugin-premium/vpcFirewall/src/main/java/org/zstack/vpcfirewall/api/APIDeleteFirewallRuleMsg.java |
| 14 | `APIDeleteFirewallRuleSetMsg` | premium/plugin-premium/vpcFirewall/src/main/java/org/zstack/vpcfirewall/api/APIDeleteFirewallRuleSetMsg.java |
| 15 | `APIDeleteFirewallRuleTemplateMsg` | premium/plugin-premium/vpcFirewall/src/main/java/org/zstack/vpcfirewall/api/APIDeleteFirewallRuleTemplateMsg.java |
| 16 | `APIDetachFirewallRuleSetFromL3Msg` | premium/plugin-premium/vpcFirewall/src/main/java/org/zstack/vpcfirewall/api/APIDetachFirewallRuleSetFromL3Msg.java |
| 17 | `APIQueryFirewallIpSetTemplateMsg` | premium/plugin-premium/vpcFirewall/src/main/java/org/zstack/vpcfirewall/api/APIQueryFirewallIpSetTemplateMsg.java |
| 18 | `APIQueryFirewallRuleMsg` | premium/plugin-premium/vpcFirewall/src/main/java/org/zstack/vpcfirewall/api/APIQueryFirewallRuleMsg.java |
| 19 | `APIQueryFirewallRuleSetL3RefMsg` | premium/plugin-premium/vpcFirewall/src/main/java/org/zstack/vpcfirewall/api/APIQueryFirewallRuleSetL3RefMsg.java |
| 20 | `APIQueryFirewallRuleSetMsg` | premium/plugin-premium/vpcFirewall/src/main/java/org/zstack/vpcfirewall/api/APIQueryFirewallRuleSetMsg.java |
| 21 | `APIQueryFirewallRuleTemplateMsg` | premium/plugin-premium/vpcFirewall/src/main/java/org/zstack/vpcfirewall/api/APIQueryFirewallRuleTemplateMsg.java |
| 22 | `APIQueryVpcFirewallMsg` | premium/plugin-premium/vpcFirewall/src/main/java/org/zstack/vpcfirewall/api/APIQueryVpcFirewallMsg.java |
| 23 | `APIQueryVpcFirewallVRouterRefMsg` | premium/plugin-premium/vpcFirewall/src/main/java/org/zstack/vpcfirewall/api/APIQueryVpcFirewallVRouterRefMsg.java |
| 24 | `APIRefreshFirewallMsg` | premium/plugin-premium/vpcFirewall/src/main/java/org/zstack/vpcfirewall/api/APIRefreshFirewallMsg.java |
| 25 | `APIUpdateFirewallIpSetTemplateMsg` | premium/plugin-premium/vpcFirewall/src/main/java/org/zstack/vpcfirewall/api/APIUpdateFirewallIpSetTemplateMsg.java |
| 26 | `APIUpdateFirewallRuleMsg` | premium/plugin-premium/vpcFirewall/src/main/java/org/zstack/vpcfirewall/api/APIUpdateFirewallRuleMsg.java |
| 27 | `APIUpdateFirewallRuleSetMsg` | premium/plugin-premium/vpcFirewall/src/main/java/org/zstack/vpcfirewall/api/APIUpdateFirewallRuleSetMsg.java |
| 28 | `APIUpdateFirewallRuleTemplateMsg` | premium/plugin-premium/vpcFirewall/src/main/java/org/zstack/vpcfirewall/api/APIUpdateFirewallRuleTemplateMsg.java |
| 29 | `APIUpdateVpcFirewallMsg` | premium/plugin-premium/vpcFirewall/src/main/java/org/zstack/vpcfirewall/api/APIUpdateVpcFirewallMsg.java |

---

<a id="module-premiumplugin-premiumzboxbackup"></a>

## 模块：`premium/plugin-premium/zboxbackup`

### `org.zstack.externalbackup.zbox`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APICreateZBoxBackupMsg` | premium/plugin-premium/zboxbackup/src/main/java/org/zstack/externalbackup/zbox/APICreateZBoxBackupMsg.java |
| 2 | `APIGetZBoxBackupDetailsMsg` | premium/plugin-premium/zboxbackup/src/main/java/org/zstack/externalbackup/zbox/APIGetZBoxBackupDetailsMsg.java |
| 3 | `APIQueryZBoxBackupMsg` | premium/plugin-premium/zboxbackup/src/main/java/org/zstack/externalbackup/zbox/APIQueryZBoxBackupMsg.java |

---

<a id="module-premiumplugin-premiumzce-x-plugin"></a>

## 模块：`premium/plugin-premium/zce-x-plugin`

### `org.zstack.zcex.api`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddZceXMsg` | premium/plugin-premium/zce-x-plugin/src/main/java/org/zstack/zcex/api/APIAddZceXMsg.java |
| 2 | `APICreateZceXAlertPlatformMsg` | premium/plugin-premium/zce-x-plugin/src/main/java/org/zstack/zcex/api/APICreateZceXAlertPlatformMsg.java |
| 3 | `APIDeleteZceXAlertPlatformMsg` | premium/plugin-premium/zce-x-plugin/src/main/java/org/zstack/zcex/api/APIDeleteZceXAlertPlatformMsg.java |
| 4 | `APIGetZceXCapabilityMsg` | premium/plugin-premium/zce-x-plugin/src/main/java/org/zstack/zcex/api/APIGetZceXCapabilityMsg.java |
| 5 | `APIQueryZceXMsg` | premium/plugin-premium/zce-x-plugin/src/main/java/org/zstack/zcex/api/APIQueryZceXMsg.java |
| 6 | `APIQueryZceXThirdPartyPlatformAlertRefMsg` | premium/plugin-premium/zce-x-plugin/src/main/java/org/zstack/zcex/api/APIQueryZceXThirdPartyPlatformAlertRefMsg.java |
| 7 | `APIRemoveZceXMsg` | premium/plugin-premium/zce-x-plugin/src/main/java/org/zstack/zcex/api/APIRemoveZceXMsg.java |
| 8 | `APIUpdateZceXClusterConfigMsg` | premium/plugin-premium/zce-x-plugin/src/main/java/org/zstack/zcex/api/APIUpdateZceXClusterConfigMsg.java |
| 9 | `APIZceXTestConnectionMsg` | premium/plugin-premium/zce-x-plugin/src/main/java/org/zstack/zcex/api/APIZceXTestConnectionMsg.java |

---

<a id="module-premiumplugin-premiumzops-plugin"></a>

## 模块：`premium/plugin-premium/zops-plugin`

### `org.zstack.zops.api`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APICheckCephHealthStatusMsg` | premium/plugin-premium/zops-plugin/src/main/java/org/zstack/zops/api/APICheckCephHealthStatusMsg.java |
| 2 | `APICheckNetworkReachableMsg` | premium/plugin-premium/zops-plugin/src/main/java/org/zstack/zops/api/APICheckNetworkReachableMsg.java |
| 3 | `APIGetChronyServersMsg` | premium/plugin-premium/zops-plugin/src/main/java/org/zstack/zops/api/APIGetChronyServersMsg.java |
| 4 | `APISyncChronyServersMsg` | premium/plugin-premium/zops-plugin/src/main/java/org/zstack/zops/api/APISyncChronyServersMsg.java |
| 5 | `APIUpdateChronyServersMsg` | premium/plugin-premium/zops-plugin/src/main/java/org/zstack/zops/api/APIUpdateChronyServersMsg.java |

---

<a id="module-premiumplugin-premiumzstone-plugin"></a>

## 模块：`premium/plugin-premium/zstone-plugin`

### `org.zstack.zstone.api`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddZStoneMsg` | premium/plugin-premium/zstone-plugin/src/main/java/org/zstack/zstone/api/APIAddZStoneMsg.java |
| 2 | `APIGetZStoneCapabilityMsg` | premium/plugin-premium/zstone-plugin/src/main/java/org/zstack/zstone/api/APIGetZStoneCapabilityMsg.java |
| 3 | `APIQueryZStoneMsg` | premium/plugin-premium/zstone-plugin/src/main/java/org/zstack/zstone/api/APIQueryZStoneMsg.java |
| 4 | `APIRemoveZStoneMsg` | premium/plugin-premium/zstone-plugin/src/main/java/org/zstack/zstone/api/APIRemoveZStoneMsg.java |
| 5 | `APIUpdateZStoneClusterConfigMsg` | premium/plugin-premium/zstone-plugin/src/main/java/org/zstack/zstone/api/APIUpdateZStoneClusterConfigMsg.java |
| 6 | `APIUpdateZStoneHostConfigMsg` | premium/plugin-premium/zstone-plugin/src/main/java/org/zstack/zstone/api/APIUpdateZStoneHostConfigMsg.java |
| 7 | `APIZStoneTestConnectionMsg` | premium/plugin-premium/zstone-plugin/src/main/java/org/zstack/zstone/api/APIZStoneTestConnectionMsg.java |

---

<a id="module-premiumplugin-premiumzsv"></a>

## 模块：`premium/plugin-premium/zsv`

### `org.zstack.zsv.core.api`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIGetNodeRolesMsg` | premium/plugin-premium/zsv/src/main/java/org/zstack/zsv/core/api/APIGetNodeRolesMsg.java |

### `org.zstack.zsv.storage.api`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APICheckCephPluginMsg` | premium/plugin-premium/zsv/src/main/java/org/zstack/zsv/storage/api/APICheckCephPluginMsg.java |

---

<a id="module-premiumsharedblock"></a>

## 模块：`premium/sharedblock`

### `org.zstack.storage.primary.sharedblock`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddSharedBlockGroupPrimaryStorageMsg` | premium/sharedblock/src/main/java/org/zstack/storage/primary/sharedblock/APIAddSharedBlockGroupPrimaryStorageMsg.java |
| 2 | `APIAddSharedBlockToSharedBlockGroupMsg` | premium/sharedblock/src/main/java/org/zstack/storage/primary/sharedblock/APIAddSharedBlockToSharedBlockGroupMsg.java |
| 3 | `APIGetSharedBlockCandidateMsg` | premium/sharedblock/src/main/java/org/zstack/storage/primary/sharedblock/APIGetSharedBlockCandidateMsg.java |
| 4 | `APIQuerySharedBlockGroupPrimaryStorageHostRefMsg` | premium/sharedblock/src/main/java/org/zstack/storage/primary/sharedblock/APIQuerySharedBlockGroupPrimaryStorageHostRefMsg.java |
| 5 | `APIQuerySharedBlockGroupPrimaryStorageMsg` | premium/sharedblock/src/main/java/org/zstack/storage/primary/sharedblock/APIQuerySharedBlockGroupPrimaryStorageMsg.java |
| 6 | `APIQuerySharedBlockMsg` | premium/sharedblock/src/main/java/org/zstack/storage/primary/sharedblock/APIQuerySharedBlockMsg.java |
| 7 | `APIRefreshSharedblockDeviceCapacityMsg` | premium/sharedblock/src/main/java/org/zstack/storage/primary/sharedblock/APIRefreshSharedblockDeviceCapacityMsg.java |
| 8 | `APIUpdateSharedBlockMsg` | premium/sharedblock/src/main/java/org/zstack/storage/primary/sharedblock/APIUpdateSharedBlockMsg.java |

---

<a id="module-premiumslb"></a>

## 模块：`premium/slb`

### `org.zstack.network.service.slb`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APICreateSlbGroupMsg` | premium/slb/src/main/java/org/zstack/network/service/slb/APICreateSlbGroupMsg.java |
| 2 | `APICreateSlbInstanceMsg` | premium/slb/src/main/java/org/zstack/network/service/slb/APICreateSlbInstanceMsg.java |
| 3 | `APICreateSlbOfferingMsg` | premium/slb/src/main/java/org/zstack/network/service/slb/APICreateSlbOfferingMsg.java |
| 4 | `APIDeleteSlbGroupMsg` | premium/slb/src/main/java/org/zstack/network/service/slb/APIDeleteSlbGroupMsg.java |
| 5 | `APIQuerySlbGroupMsg` | premium/slb/src/main/java/org/zstack/network/service/slb/APIQuerySlbGroupMsg.java |
| 6 | `APIQuerySlbOfferingMsg` | premium/slb/src/main/java/org/zstack/network/service/slb/APIQuerySlbOfferingMsg.java |
| 7 | `APIUpdateSlbGroupMsg` | premium/slb/src/main/java/org/zstack/network/service/slb/APIUpdateSlbGroupMsg.java |

---

<a id="module-premiumsnmp"></a>

## 模块：`premium/snmp`

### `org.zstack.snmp.agent`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APICreateSnmpAgentMsg` | premium/snmp/src/main/java/org/zstack/snmp/agent/APICreateSnmpAgentMsg.java |
| 2 | `APIQuerySnmpAgentMsg` | premium/snmp/src/main/java/org/zstack/snmp/agent/APIQuerySnmpAgentMsg.java |
| 3 | `APIStartSnmpAgentMsg` | premium/snmp/src/main/java/org/zstack/snmp/agent/APIStartSnmpAgentMsg.java |
| 4 | `APIStopSnmpAgentMsg` | premium/snmp/src/main/java/org/zstack/snmp/agent/APIStopSnmpAgentMsg.java |
| 5 | `APIUpdateSnmpAgentMsg` | premium/snmp/src/main/java/org/zstack/snmp/agent/APIUpdateSnmpAgentMsg.java |

---

<a id="module-premiumsns"></a>

## 模块：`premium/sns`

### `org.zstack.sns`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddSNSSmsReceiverMsg` | premium/sns/src/main/java/org/zstack/sns/APIAddSNSSmsReceiverMsg.java |
| 2 | `APIChangeSNSApplicationEndpointStateMsg` | premium/sns/src/main/java/org/zstack/sns/APIChangeSNSApplicationEndpointStateMsg.java |
| 3 | `APIChangeSNSApplicationPlatformStateMsg` | premium/sns/src/main/java/org/zstack/sns/APIChangeSNSApplicationPlatformStateMsg.java |
| 4 | `APIChangeSNSTopicStateMsg` | premium/sns/src/main/java/org/zstack/sns/APIChangeSNSTopicStateMsg.java |
| 5 | `APICreateSNSApplicationEndpointMsg` | premium/sns/src/main/java/org/zstack/sns/APICreateSNSApplicationEndpointMsg.java |
| 6 | `APICreateSNSApplicationPlatformMsg` | premium/sns/src/main/java/org/zstack/sns/APICreateSNSApplicationPlatformMsg.java |
| 7 | `APICreateSNSSmsEndpointMsg` | premium/sns/src/main/java/org/zstack/sns/APICreateSNSSmsEndpointMsg.java |
| 8 | `APICreateSNSTopicMsg` | premium/sns/src/main/java/org/zstack/sns/APICreateSNSTopicMsg.java |
| 9 | `APIDeleteSNSApplicationEndpointMsg` | premium/sns/src/main/java/org/zstack/sns/APIDeleteSNSApplicationEndpointMsg.java |
| 10 | `APIDeleteSNSApplicationPlatformMsg` | premium/sns/src/main/java/org/zstack/sns/APIDeleteSNSApplicationPlatformMsg.java |
| 11 | `APIDeleteSNSTopicMsg` | premium/sns/src/main/java/org/zstack/sns/APIDeleteSNSTopicMsg.java |
| 12 | `APIQuerySNSApplicationEndpointMsg` | premium/sns/src/main/java/org/zstack/sns/APIQuerySNSApplicationEndpointMsg.java |
| 13 | `APIQuerySNSApplicationPlatformMsg` | premium/sns/src/main/java/org/zstack/sns/APIQuerySNSApplicationPlatformMsg.java |
| 14 | `APIQuerySNSSmsEndpointMsg` | premium/sns/src/main/java/org/zstack/sns/APIQuerySNSSmsEndpointMsg.java |
| 15 | `APIQuerySNSTopicMsg` | premium/sns/src/main/java/org/zstack/sns/APIQuerySNSTopicMsg.java |
| 16 | `APIQuerySNSTopicSubscriberMsg` | premium/sns/src/main/java/org/zstack/sns/APIQuerySNSTopicSubscriberMsg.java |
| 17 | `APIRemoveSNSSmsReceiverMsg` | premium/sns/src/main/java/org/zstack/sns/APIRemoveSNSSmsReceiverMsg.java |
| 18 | `APISubscribeSNSTopicMsg` | premium/sns/src/main/java/org/zstack/sns/APISubscribeSNSTopicMsg.java |
| 19 | `APIUnsubscribeSNSTopicMsg` | premium/sns/src/main/java/org/zstack/sns/APIUnsubscribeSNSTopicMsg.java |
| 20 | `APIUpdateSNSApplicationEndpointMsg` | premium/sns/src/main/java/org/zstack/sns/APIUpdateSNSApplicationEndpointMsg.java |
| 21 | `APIUpdateSNSApplicationPlatformMsg` | premium/sns/src/main/java/org/zstack/sns/APIUpdateSNSApplicationPlatformMsg.java |
| 22 | `APIUpdateSNSTopicMsg` | premium/sns/src/main/java/org/zstack/sns/APIUpdateSNSTopicMsg.java |
| 23 | `APIValidateSNSSmsEndpointMsg` | premium/sns/src/main/java/org/zstack/sns/APIValidateSNSSmsEndpointMsg.java |

### `org.zstack.sns.platform.dingtalk`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddSNSDingTalkAtPersonMsg` | premium/sns/src/main/java/org/zstack/sns/platform/dingtalk/APIAddSNSDingTalkAtPersonMsg.java |
| 2 | `APICreateSNSDingTalkEndpointMsg` | premium/sns/src/main/java/org/zstack/sns/platform/dingtalk/APICreateSNSDingTalkEndpointMsg.java |
| 3 | `APIQuerySNSDingTalkAtPersonMsg` | premium/sns/src/main/java/org/zstack/sns/platform/dingtalk/APIQuerySNSDingTalkAtPersonMsg.java |
| 4 | `APIQuerySNSDingTalkEndpointMsg` | premium/sns/src/main/java/org/zstack/sns/platform/dingtalk/APIQuerySNSDingTalkEndpointMsg.java |
| 5 | `APIRemoveSNSDingTalkAtPersonMsg` | premium/sns/src/main/java/org/zstack/sns/platform/dingtalk/APIRemoveSNSDingTalkAtPersonMsg.java |
| 6 | `APISNSDingTalkTestConnectionMsg` | premium/sns/src/main/java/org/zstack/sns/platform/dingtalk/APISNSDingTalkTestConnectionMsg.java |
| 7 | `APIUpdateAtPersonOfAtDingTalkEndpointMsg` | premium/sns/src/main/java/org/zstack/sns/platform/dingtalk/APIUpdateAtPersonOfAtDingTalkEndpointMsg.java |
| 8 | `APIUpdateSNSDingTalkEndpointMsg` | premium/sns/src/main/java/org/zstack/sns/platform/dingtalk/APIUpdateSNSDingTalkEndpointMsg.java |

### `org.zstack.sns.platform.email`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddEmailAddressToSNSEmailEndpointMsg` | premium/sns/src/main/java/org/zstack/sns/platform/email/APIAddEmailAddressToSNSEmailEndpointMsg.java |
| 2 | `APICreateSNSEmailEndpointMsg` | premium/sns/src/main/java/org/zstack/sns/platform/email/APICreateSNSEmailEndpointMsg.java |
| 3 | `APICreateSNSEmailPlatformMsg` | premium/sns/src/main/java/org/zstack/sns/platform/email/APICreateSNSEmailPlatformMsg.java |
| 4 | `APIDeleteEmailAddressOfSNSEmailEndpointMsg` | premium/sns/src/main/java/org/zstack/sns/platform/email/APIDeleteEmailAddressOfSNSEmailEndpointMsg.java |
| 5 | `APIQuerySNSEmailAddressMsg` | premium/sns/src/main/java/org/zstack/sns/platform/email/APIQuerySNSEmailAddressMsg.java |
| 6 | `APIQuerySNSEmailEndpointMsg` | premium/sns/src/main/java/org/zstack/sns/platform/email/APIQuerySNSEmailEndpointMsg.java |
| 7 | `APIQuerySNSEmailPlatformMsg` | premium/sns/src/main/java/org/zstack/sns/platform/email/APIQuerySNSEmailPlatformMsg.java |
| 8 | `APISNSEmailTestConnectionMsg` | premium/sns/src/main/java/org/zstack/sns/platform/email/APISNSEmailTestConnectionMsg.java |
| 9 | `APIUpdateEmailAddressOfSNSEmailEndpointMsg` | premium/sns/src/main/java/org/zstack/sns/platform/email/APIUpdateEmailAddressOfSNSEmailEndpointMsg.java |
| 10 | `APIValidateSNSEmailPlatformMsg` | premium/sns/src/main/java/org/zstack/sns/platform/email/APIValidateSNSEmailPlatformMsg.java |

### `org.zstack.sns.platform.feishu`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddSNSFeiShuAtPersonMsg` | premium/sns/src/main/java/org/zstack/sns/platform/feishu/APIAddSNSFeiShuAtPersonMsg.java |
| 2 | `APICreateSNSFeiShuEndpointMsg` | premium/sns/src/main/java/org/zstack/sns/platform/feishu/APICreateSNSFeiShuEndpointMsg.java |
| 3 | `APIQuerySNSFeiShuAtPersonMsg` | premium/sns/src/main/java/org/zstack/sns/platform/feishu/APIQuerySNSFeiShuAtPersonMsg.java |
| 4 | `APIQuerySNSFeiShuEndpointMsg` | premium/sns/src/main/java/org/zstack/sns/platform/feishu/APIQuerySNSFeiShuEndpointMsg.java |
| 5 | `APIRemoveSNSFeiShuAtPersonMsg` | premium/sns/src/main/java/org/zstack/sns/platform/feishu/APIRemoveSNSFeiShuAtPersonMsg.java |
| 6 | `APISNSFeiShuTestConnectionMsg` | premium/sns/src/main/java/org/zstack/sns/platform/feishu/APISNSFeiShuTestConnectionMsg.java |
| 7 | `APIUpdateAtPersonOfAtFeiShuEndpointMsg` | premium/sns/src/main/java/org/zstack/sns/platform/feishu/APIUpdateAtPersonOfAtFeiShuEndpointMsg.java |
| 8 | `APIUpdateSNSFeiShuEndpointMsg` | premium/sns/src/main/java/org/zstack/sns/platform/feishu/APIUpdateSNSFeiShuEndpointMsg.java |

### `org.zstack.sns.platform.http`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APICreateSNSHttpEndpointMsg` | premium/sns/src/main/java/org/zstack/sns/platform/http/APICreateSNSHttpEndpointMsg.java |
| 2 | `APIQuerySNSHttpEndpointMsg` | premium/sns/src/main/java/org/zstack/sns/platform/http/APIQuerySNSHttpEndpointMsg.java |
| 3 | `APISNSHttpTestConnectionMsg` | premium/sns/src/main/java/org/zstack/sns/platform/http/APISNSHttpTestConnectionMsg.java |
| 4 | `APIUpdateSNSHttpEndpointMsg` | premium/sns/src/main/java/org/zstack/sns/platform/http/APIUpdateSNSHttpEndpointMsg.java |

### `org.zstack.sns.platform.microsoftteams`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APICreateSNSMicrosoftTeamsEndpointMsg` | premium/sns/src/main/java/org/zstack/sns/platform/microsoftteams/APICreateSNSMicrosoftTeamsEndpointMsg.java |
| 2 | `APIQuerySNSMicrosoftTeamsEndpointMsg` | premium/sns/src/main/java/org/zstack/sns/platform/microsoftteams/APIQuerySNSMicrosoftTeamsEndpointMsg.java |
| 3 | `APISNSMicrosoftTeamsTestConnectionMsg` | premium/sns/src/main/java/org/zstack/sns/platform/microsoftteams/APISNSMicrosoftTeamsTestConnectionMsg.java |
| 4 | `APIUpdateSNSMicrosoftTeamsEndpointMsg` | premium/sns/src/main/java/org/zstack/sns/platform/microsoftteams/APIUpdateSNSMicrosoftTeamsEndpointMsg.java |

### `org.zstack.sns.platform.snmp`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APICreateSNSSnmpEndpointMsg` | premium/sns/src/main/java/org/zstack/sns/platform/snmp/APICreateSNSSnmpEndpointMsg.java |
| 2 | `APICreateSNSSnmpPlatformMsg` | premium/sns/src/main/java/org/zstack/sns/platform/snmp/APICreateSNSSnmpPlatformMsg.java |
| 3 | `APIQuerySNSSnmpPlatformMsg` | premium/sns/src/main/java/org/zstack/sns/platform/snmp/APIQuerySNSSnmpPlatformMsg.java |
| 4 | `APISNSSnmpTestConnectionMsg` | premium/sns/src/main/java/org/zstack/sns/platform/snmp/APISNSSnmpTestConnectionMsg.java |
| 5 | `APIUpdateSNSSnmpPlatformMsg` | premium/sns/src/main/java/org/zstack/sns/platform/snmp/APIUpdateSNSSnmpPlatformMsg.java |

### `org.zstack.sns.platform.wecom`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddSNSWeComAtPersonMsg` | premium/sns/src/main/java/org/zstack/sns/platform/wecom/APIAddSNSWeComAtPersonMsg.java |
| 2 | `APICreateSNSWeComEndpointMsg` | premium/sns/src/main/java/org/zstack/sns/platform/wecom/APICreateSNSWeComEndpointMsg.java |
| 3 | `APIQuerySNSWeComAtPersonMsg` | premium/sns/src/main/java/org/zstack/sns/platform/wecom/APIQuerySNSWeComAtPersonMsg.java |
| 4 | `APIQuerySNSWeComEndpointMsg` | premium/sns/src/main/java/org/zstack/sns/platform/wecom/APIQuerySNSWeComEndpointMsg.java |
| 5 | `APIRemoveSNSWeComAtPersonMsg` | premium/sns/src/main/java/org/zstack/sns/platform/wecom/APIRemoveSNSWeComAtPersonMsg.java |
| 6 | `APISNSWeComTestConnectionMsg` | premium/sns/src/main/java/org/zstack/sns/platform/wecom/APISNSWeComTestConnectionMsg.java |
| 7 | `APIUpdateAtPersonOfAtWeComEndpointMsg` | premium/sns/src/main/java/org/zstack/sns/platform/wecom/APIUpdateAtPersonOfAtWeComEndpointMsg.java |
| 8 | `APIUpdateSNSWeComEndpointMsg` | premium/sns/src/main/java/org/zstack/sns/platform/wecom/APIUpdateSNSWeComEndpointMsg.java |

---

<a id="module-premiumtag2"></a>

## 模块：`premium/tag2`

### `org.zstack.tag2`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAttachTagToResourcesMsg` | premium/tag2/src/main/java/org/zstack/tag2/APIAttachTagToResourcesMsg.java |
| 2 | `APICreateTagMsg` | premium/tag2/src/main/java/org/zstack/tag2/APICreateTagMsg.java |
| 3 | `APIDetachTagFromResourcesMsg` | premium/tag2/src/main/java/org/zstack/tag2/APIDetachTagFromResourcesMsg.java |
| 4 | `APIQueryTagMsg` | premium/tag2/src/main/java/org/zstack/tag2/APIQueryTagMsg.java |
| 5 | `APIUpdateTagMsg` | premium/tag2/src/main/java/org/zstack/tag2/APIUpdateTagMsg.java |

---

<a id="module-premiumtwofactorauthentication"></a>

## 模块：`premium/twoFactorAuthentication`

### `org.zstack.twoFactorAuthentication`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIGetTwoFactorAuthenticationSecretMsg` | premium/twoFactorAuthentication/src/main/java/org/zstack/twoFactorAuthentication/APIGetTwoFactorAuthenticationSecretMsg.java |
| 2 | `APIGetTwoFactorAuthenticationStateMsg` | premium/twoFactorAuthentication/src/main/java/org/zstack/twoFactorAuthentication/APIGetTwoFactorAuthenticationStateMsg.java |
| 3 | `APIQueryTwoFactorAuthenticationMsg` | premium/twoFactorAuthentication/src/main/java/org/zstack/twoFactorAuthentication/APIQueryTwoFactorAuthenticationMsg.java |
| 4 | `APIResetTwoFactorAuthenticationSecretMsg` | premium/twoFactorAuthentication/src/main/java/org/zstack/twoFactorAuthentication/APIResetTwoFactorAuthenticationSecretMsg.java |

---

<a id="module-premiumvolumebackup"></a>

## 模块：`premium/volumebackup`

### `org.zstack.header.storage.database.backup`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APICreateDatabaseBackupMsg` | premium/volumebackup/src/main/java/org/zstack/header/storage/database/backup/APICreateDatabaseBackupMsg.java |
| 2 | `APIDeleteDatabaseBackupMsg` | premium/volumebackup/src/main/java/org/zstack/header/storage/database/backup/APIDeleteDatabaseBackupMsg.java |
| 3 | `APIDeleteExportedDatabaseBackupFromBackupStorageMsg` | premium/volumebackup/src/main/java/org/zstack/header/storage/database/backup/APIDeleteExportedDatabaseBackupFromBackupStorageMsg.java |
| 4 | `APIExportDatabaseBackupFromBackupStorageMsg` | premium/volumebackup/src/main/java/org/zstack/header/storage/database/backup/APIExportDatabaseBackupFromBackupStorageMsg.java |
| 5 | `APIGetDatabaseBackupFromImageStoreMsg` | premium/volumebackup/src/main/java/org/zstack/header/storage/database/backup/APIGetDatabaseBackupFromImageStoreMsg.java |
| 6 | `APIQueryDatabaseBackupMsg` | premium/volumebackup/src/main/java/org/zstack/header/storage/database/backup/APIQueryDatabaseBackupMsg.java |
| 7 | `APIRecoverDatabaseFromBackupMsg` | premium/volumebackup/src/main/java/org/zstack/header/storage/database/backup/APIRecoverDatabaseFromBackupMsg.java |
| 8 | `APISyncDatabaseBackupFromImageStoreBackupStorageMsg` | premium/volumebackup/src/main/java/org/zstack/header/storage/database/backup/APISyncDatabaseBackupFromImageStoreBackupStorageMsg.java |
| 9 | `APISyncDatabaseBackupMsg` | premium/volumebackup/src/main/java/org/zstack/header/storage/database/backup/APISyncDatabaseBackupMsg.java |

### `org.zstack.header.storage.volume.backup`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APICreateDataVolumeFromVolumeBackupMsg` | premium/volumebackup/src/main/java/org/zstack/header/storage/volume/backup/APICreateDataVolumeFromVolumeBackupMsg.java |
| 2 | `APICreateDataVolumeTemplateFromVolumeBackupMsg` | premium/volumebackup/src/main/java/org/zstack/header/storage/volume/backup/APICreateDataVolumeTemplateFromVolumeBackupMsg.java |
| 3 | `APICreateRootVolumeTemplateFromVolumeBackupMsg` | premium/volumebackup/src/main/java/org/zstack/header/storage/volume/backup/APICreateRootVolumeTemplateFromVolumeBackupMsg.java |
| 4 | `APICreateVmBackupMsg` | premium/volumebackup/src/main/java/org/zstack/header/storage/volume/backup/APICreateVmBackupMsg.java |
| 5 | `APICreateVmFromVmBackupMsg` | premium/volumebackup/src/main/java/org/zstack/header/storage/volume/backup/APICreateVmFromVmBackupMsg.java |
| 6 | `APICreateVmFromVolumeBackupMsg` | premium/volumebackup/src/main/java/org/zstack/header/storage/volume/backup/APICreateVmFromVolumeBackupMsg.java |
| 7 | `APICreateVolumeBackupMsg` | premium/volumebackup/src/main/java/org/zstack/header/storage/volume/backup/APICreateVolumeBackupMsg.java |
| 8 | `APIDeleteVmBackupMsg` | premium/volumebackup/src/main/java/org/zstack/header/storage/volume/backup/APIDeleteVmBackupMsg.java |
| 9 | `APIDeleteVolumeBackupMsg` | premium/volumebackup/src/main/java/org/zstack/header/storage/volume/backup/APIDeleteVolumeBackupMsg.java |
| 10 | `APIQueryVolumeBackupMsg` | premium/volumebackup/src/main/java/org/zstack/header/storage/volume/backup/APIQueryVolumeBackupMsg.java |
| 11 | `APIRecoverBackupFromImageStoreBackupStorageMsg` | premium/volumebackup/src/main/java/org/zstack/header/storage/volume/backup/APIRecoverBackupFromImageStoreBackupStorageMsg.java |
| 12 | `APIRecoverVmBackupFromImageStoreBackupStorageMsg` | premium/volumebackup/src/main/java/org/zstack/header/storage/volume/backup/APIRecoverVmBackupFromImageStoreBackupStorageMsg.java |
| 13 | `APIRevertVmFromVmBackupMsg` | premium/volumebackup/src/main/java/org/zstack/header/storage/volume/backup/APIRevertVmFromVmBackupMsg.java |
| 14 | `APIRevertVolumeFromVolumeBackupMsg` | premium/volumebackup/src/main/java/org/zstack/header/storage/volume/backup/APIRevertVolumeFromVolumeBackupMsg.java |
| 15 | `APISyncBackupFromImageStoreBackupStorageMsg` | premium/volumebackup/src/main/java/org/zstack/header/storage/volume/backup/APISyncBackupFromImageStoreBackupStorageMsg.java |
| 16 | `APISyncVmBackupFromImageStoreBackupStorageMsg` | premium/volumebackup/src/main/java/org/zstack/header/storage/volume/backup/APISyncVmBackupFromImageStoreBackupStorageMsg.java |
| 17 | `APISyncVmBackupMsg` | premium/volumebackup/src/main/java/org/zstack/header/storage/volume/backup/APISyncVmBackupMsg.java |
| 18 | `APISyncVolumeBackupMsg` | premium/volumebackup/src/main/java/org/zstack/header/storage/volume/backup/APISyncVolumeBackupMsg.java |

---

<a id="module-premiumvpc"></a>

## 模块：`premium/vpc`

### `org.zstack.header.vpc.ha`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIChangeVpcHaGroupMonitorIpsMsg` | premium/vpc/src/main/java/org/zstack/header/vpc/ha/APIChangeVpcHaGroupMonitorIpsMsg.java |
| 2 | `APICreateVpcHaGroupMsg` | premium/vpc/src/main/java/org/zstack/header/vpc/ha/APICreateVpcHaGroupMsg.java |
| 3 | `APIDeleteVpcHaGroupMsg` | premium/vpc/src/main/java/org/zstack/header/vpc/ha/APIDeleteVpcHaGroupMsg.java |
| 4 | `APIQueryVpcHaGroupMsg` | premium/vpc/src/main/java/org/zstack/header/vpc/ha/APIQueryVpcHaGroupMsg.java |
| 5 | `APIUpdateVpcHaGroupMsg` | premium/vpc/src/main/java/org/zstack/header/vpc/ha/APIUpdateVpcHaGroupMsg.java |

### `org.zstack.ipsec`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddRemoteCidrsToIPsecConnectionMsg` | premium/vpc/src/main/java/org/zstack/ipsec/APIAddRemoteCidrsToIPsecConnectionMsg.java |
| 2 | `APIAttachL3NetworksToIPsecConnectionMsg` | premium/vpc/src/main/java/org/zstack/ipsec/APIAttachL3NetworksToIPsecConnectionMsg.java |
| 3 | `APIChangeIPsecConnectionMsg` | premium/vpc/src/main/java/org/zstack/ipsec/APIChangeIPsecConnectionMsg.java |
| 4 | `APIChangeIPSecConnectionStateMsg` | premium/vpc/src/main/java/org/zstack/ipsec/APIChangeIPSecConnectionStateMsg.java |
| 5 | `APICreateIPsecConnectionMsg` | premium/vpc/src/main/java/org/zstack/ipsec/APICreateIPsecConnectionMsg.java |
| 6 | `APIDeleteIPsecConnectionMsg` | premium/vpc/src/main/java/org/zstack/ipsec/APIDeleteIPsecConnectionMsg.java |
| 7 | `APIDetachL3NetworksFromIPsecConnectionMsg` | premium/vpc/src/main/java/org/zstack/ipsec/APIDetachL3NetworksFromIPsecConnectionMsg.java |
| 8 | `APIGetCandidateL3NetworksForIpSecConnectionMsg` | premium/vpc/src/main/java/org/zstack/ipsec/APIGetCandidateL3NetworksForIpSecConnectionMsg.java |
| 9 | `APIGetVpcIPsecLogMsg` | premium/vpc/src/main/java/org/zstack/ipsec/APIGetVpcIPsecLogMsg.java |
| 10 | `APIQueryIPSecConnectionMsg` | premium/vpc/src/main/java/org/zstack/ipsec/APIQueryIPSecConnectionMsg.java |
| 11 | `APIReconnectIPsecConnectionMsg` | premium/vpc/src/main/java/org/zstack/ipsec/APIReconnectIPsecConnectionMsg.java |
| 12 | `APIRemoveRemoteCidrsFromIPsecConnectionMsg` | premium/vpc/src/main/java/org/zstack/ipsec/APIRemoveRemoteCidrsFromIPsecConnectionMsg.java |
| 13 | `APIUpdateIPsecConnectionMsg` | premium/vpc/src/main/java/org/zstack/ipsec/APIUpdateIPsecConnectionMsg.java |

### `org.zstack.vpc`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddDnsToVpcRouterMsg` | premium/vpc/src/main/java/org/zstack/vpc/APIAddDnsToVpcRouterMsg.java |
| 2 | `APICreateVpcVRouterMsg` | premium/vpc/src/main/java/org/zstack/vpc/APICreateVpcVRouterMsg.java |
| 3 | `APIGetAttachableVpcL3NetworkMsg` | premium/vpc/src/main/java/org/zstack/vpc/APIGetAttachableVpcL3NetworkMsg.java |
| 4 | `APIGetRouteTableVpcVRouterCandidateMsg` | premium/vpc/src/main/java/org/zstack/vpc/APIGetRouteTableVpcVRouterCandidateMsg.java |
| 5 | `APIGetVirtualRouterSoftwareVersionMsg` | premium/vpc/src/main/java/org/zstack/vpc/APIGetVirtualRouterSoftwareVersionMsg.java |
| 6 | `APIGetVpcAttachedEipMsg` | premium/vpc/src/main/java/org/zstack/vpc/APIGetVpcAttachedEipMsg.java |
| 7 | `APIGetVpcAttachedIpsecMsg` | premium/vpc/src/main/java/org/zstack/vpc/APIGetVpcAttachedIpsecMsg.java |
| 8 | `APIGetVpcAttachedLoadBalancerMsg` | premium/vpc/src/main/java/org/zstack/vpc/APIGetVpcAttachedLoadBalancerMsg.java |
| 9 | `APIGetVpcAttachedPortForwardingRulesMsg` | premium/vpc/src/main/java/org/zstack/vpc/APIGetVpcAttachedPortForwardingRulesMsg.java |
| 10 | `APIGetVpcAttachedVipMsg` | premium/vpc/src/main/java/org/zstack/vpc/APIGetVpcAttachedVipMsg.java |
| 11 | `APIGetVpcVRouterDistributedRoutingConnectionsMsg` | premium/vpc/src/main/java/org/zstack/vpc/APIGetVpcVRouterDistributedRoutingConnectionsMsg.java |
| 12 | `APIGetVpcVRouterDistributedRoutingEnabledMsg` | premium/vpc/src/main/java/org/zstack/vpc/APIGetVpcVRouterDistributedRoutingEnabledMsg.java |
| 13 | `APIGetVpcVRouterNetworkServiceStateMsg` | premium/vpc/src/main/java/org/zstack/vpc/APIGetVpcVRouterNetworkServiceStateMsg.java |
| 14 | `APIQueryVpcHaGroupNetworkServiceRefMsg` | premium/vpc/src/main/java/org/zstack/vpc/APIQueryVpcHaGroupNetworkServiceRefMsg.java |
| 15 | `APIQueryVpcRouterMsg` | premium/vpc/src/main/java/org/zstack/vpc/APIQueryVpcRouterMsg.java |
| 16 | `APIQueryVpcSnatStateMsg` | premium/vpc/src/main/java/org/zstack/vpc/APIQueryVpcSnatStateMsg.java |
| 17 | `APIRemoveDnsFromVpcRouterMsg` | premium/vpc/src/main/java/org/zstack/vpc/APIRemoveDnsFromVpcRouterMsg.java |
| 18 | `APISetVpcVRouterDistributedRoutingEnabledMsg` | premium/vpc/src/main/java/org/zstack/vpc/APISetVpcVRouterDistributedRoutingEnabledMsg.java |
| 19 | `APISetVpcVRouterNetworkServiceStateMsg` | premium/vpc/src/main/java/org/zstack/vpc/APISetVpcVRouterNetworkServiceStateMsg.java |
| 20 | `APIUpdateVirtualRouterSoftwareVersionMsg` | premium/vpc/src/main/java/org/zstack/vpc/APIUpdateVirtualRouterSoftwareVersionMsg.java |

---

<a id="module-premiumxdragon"></a>

## 模块：`premium/xdragon`

### `org.zstack.xdragon`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddXDragonHostMsg` | premium/xdragon/src/main/java/org/zstack/xdragon/APIAddXDragonHostMsg.java |

---

<a id="module-premiumzbox"></a>

## 模块：`premium/zbox`

### `org.zstack.zbox`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddZBoxMsg` | premium/zbox/src/main/java/org/zstack/zbox/APIAddZBoxMsg.java |
| 2 | `APIEjectZBoxMsg` | premium/zbox/src/main/java/org/zstack/zbox/APIEjectZBoxMsg.java |
| 3 | `APIQueryZBoxMsg` | premium/zbox/src/main/java/org/zstack/zbox/APIQueryZBoxMsg.java |
| 4 | `APISyncZBoxCapacityMsg` | premium/zbox/src/main/java/org/zstack/zbox/APISyncZBoxCapacityMsg.java |

---

<a id="module-premiumzwatch"></a>

## 模块：`premium/zwatch`

### `org.zstack.zwatch.alarm`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAckAlarmDataMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/alarm/APIAckAlarmDataMsg.java |
| 2 | `APIAckAlertDataMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/alarm/APIAckAlertDataMsg.java |
| 3 | `APIAckEventDataMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/alarm/APIAckEventDataMsg.java |
| 4 | `APIAddActionToAlarmMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/alarm/APIAddActionToAlarmMsg.java |
| 5 | `APIAddActionToEventSubscriptionMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/alarm/APIAddActionToEventSubscriptionMsg.java |
| 6 | `APIAddLabelToAlarmMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/alarm/APIAddLabelToAlarmMsg.java |
| 7 | `APIAddLabelToEventSubscriptionMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/alarm/APIAddLabelToEventSubscriptionMsg.java |
| 8 | `APIChangeAlarmStateMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/alarm/APIChangeAlarmStateMsg.java |
| 9 | `APIChangeEventSubscriptionStateMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/alarm/APIChangeEventSubscriptionStateMsg.java |
| 10 | `APICreateAlarmMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/alarm/APICreateAlarmMsg.java |
| 11 | `APIDeleteAlarmMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/alarm/APIDeleteAlarmMsg.java |
| 12 | `APIGetTextTemplateArgMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/alarm/APIGetTextTemplateArgMsg.java |
| 13 | `APIQueryAlarmMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/alarm/APIQueryAlarmMsg.java |
| 14 | `APIQueryAlertDataAckMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/alarm/APIQueryAlertDataAckMsg.java |
| 15 | `APIQueryEventSubscriptionMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/alarm/APIQueryEventSubscriptionMsg.java |
| 16 | `APIRemoveActionFromAlarmMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/alarm/APIRemoveActionFromAlarmMsg.java |
| 17 | `APIRemoveActionFromEventSubscriptionMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/alarm/APIRemoveActionFromEventSubscriptionMsg.java |
| 18 | `APIRemoveLabelFromAlarmMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/alarm/APIRemoveLabelFromAlarmMsg.java |
| 19 | `APIRemoveLabelFromEventSubscriptionMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/alarm/APIRemoveLabelFromEventSubscriptionMsg.java |
| 20 | `APISubscribeEventMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/alarm/APISubscribeEventMsg.java |
| 21 | `APIUnsubscribeEventMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/alarm/APIUnsubscribeEventMsg.java |
| 22 | `APIUpdateAlarmLabelMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/alarm/APIUpdateAlarmLabelMsg.java |
| 23 | `APIUpdateAlarmMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/alarm/APIUpdateAlarmMsg.java |
| 24 | `APIUpdateAlertDataAckMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/alarm/APIUpdateAlertDataAckMsg.java |
| 25 | `APIUpdateEventSubscriptionLabelMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/alarm/APIUpdateEventSubscriptionLabelMsg.java |
| 26 | `APIUpdateSubscribeEventMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/alarm/APIUpdateSubscribeEventMsg.java |

### `org.zstack.zwatch.alarm.activealarm.api`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIChangeActiveAlarmStateMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/alarm/activealarm/api/APIChangeActiveAlarmStateMsg.java |
| 2 | `APIGetActiveAlarmStatusMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/alarm/activealarm/api/APIGetActiveAlarmStatusMsg.java |
| 3 | `APIQueryActiveAlarmMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/alarm/activealarm/api/APIQueryActiveAlarmMsg.java |
| 4 | `APIQueryActiveAlarmTemplateMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/alarm/activealarm/api/APIQueryActiveAlarmTemplateMsg.java |
| 5 | `APIUpdateActiveAlarmTemplateMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/alarm/activealarm/api/APIUpdateActiveAlarmTemplateMsg.java |

### `org.zstack.zwatch.alarm.sns`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APICreateSNSTextTemplateMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/alarm/sns/APICreateSNSTextTemplateMsg.java |
| 2 | `APIDeleteSNSTextTemplateMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/alarm/sns/APIDeleteSNSTextTemplateMsg.java |
| 3 | `APIQuerySNSTextTemplateMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/alarm/sns/APIQuerySNSTextTemplateMsg.java |
| 4 | `APIUpdateSNSTextTemplateMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/alarm/sns/APIUpdateSNSTextTemplateMsg.java |

### `org.zstack.zwatch.alarm.sns.template.aliyunsms`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APICreateAliyunSmsSNSTextTemplateMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/alarm/sns/template/aliyunsms/APICreateAliyunSmsSNSTextTemplateMsg.java |
| 2 | `APIQueryAliyunSmsSNSTextTemplateMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/alarm/sns/template/aliyunsms/APIQueryAliyunSmsSNSTextTemplateMsg.java |
| 3 | `APIUpdateAliyunSmsSNSTextTemplateMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/alarm/sns/template/aliyunsms/APIUpdateAliyunSmsSNSTextTemplateMsg.java |

### `org.zstack.zwatch.api`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APICreateMetricDataHttpReceiverMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/api/APICreateMetricDataHttpReceiverMsg.java |
| 2 | `APICreateMetricTemplateMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/api/APICreateMetricTemplateMsg.java |
| 3 | `APIDeleteMetricDataHttpReceiverMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/api/APIDeleteMetricDataHttpReceiverMsg.java |
| 4 | `APIDeleteMetricDataMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/api/APIDeleteMetricDataMsg.java |
| 5 | `APIDeleteMetricTemplateMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/api/APIDeleteMetricTemplateMsg.java |
| 6 | `APIGetAlarmDataMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/api/APIGetAlarmDataMsg.java |
| 7 | `APIGetAllEventMetadataMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/api/APIGetAllEventMetadataMsg.java |
| 8 | `APIGetAllMetricMetadataMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/api/APIGetAllMetricMetadataMsg.java |
| 9 | `APIGetAuditDataMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/api/APIGetAuditDataMsg.java |
| 10 | `APIGetEventDataMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/api/APIGetEventDataMsg.java |
| 11 | `APIGetManagementNodeDirCapacityMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/api/APIGetManagementNodeDirCapacityMsg.java |
| 12 | `APIGetMetricDataMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/api/APIGetMetricDataMsg.java |
| 13 | `APIGetMetricLabelValueMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/api/APIGetMetricLabelValueMsg.java |
| 14 | `APIGetPrometheusMetricLabelValueMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/api/APIGetPrometheusMetricLabelValueMsg.java |
| 15 | `APIGetZWatchAlertHistogramMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/api/APIGetZWatchAlertHistogramMsg.java |
| 16 | `APIPutMetricDataMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/api/APIPutMetricDataMsg.java |
| 17 | `APIQueryAlarmRecordMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/api/APIQueryAlarmRecordMsg.java |
| 18 | `APIQueryAuditMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/api/APIQueryAuditMsg.java |
| 19 | `APIQueryEventRecordMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/api/APIQueryEventRecordMsg.java |
| 20 | `APIQueryMetricDataHttpReceiverMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/api/APIQueryMetricDataHttpReceiverMsg.java |
| 21 | `APIQueryMetricTemplateMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/api/APIQueryMetricTemplateMsg.java |
| 22 | `APIUpdateAlarmDataMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/api/APIUpdateAlarmDataMsg.java |
| 23 | `APIUpdateEventDataMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/api/APIUpdateEventDataMsg.java |

### `org.zstack.zwatch.monitorgroup.api`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddEventRuleTemplateMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/monitorgroup/api/APIAddEventRuleTemplateMsg.java |
| 2 | `APIAddInstanceToMonitorGroupMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/monitorgroup/api/APIAddInstanceToMonitorGroupMsg.java |
| 3 | `APIAddMetricRuleTemplateMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/monitorgroup/api/APIAddMetricRuleTemplateMsg.java |
| 4 | `APIApplyMonitorTemplateToMonitorGroupMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/monitorgroup/api/APIApplyMonitorTemplateToMonitorGroupMsg.java |
| 5 | `APICloneMonitorTemplateMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/monitorgroup/api/APICloneMonitorTemplateMsg.java |
| 6 | `APICreateMonitorGroupMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/monitorgroup/api/APICreateMonitorGroupMsg.java |
| 7 | `APICreateMonitorTemplateMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/monitorgroup/api/APICreateMonitorTemplateMsg.java |
| 8 | `APIDeleteEventRuleTemplateMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/monitorgroup/api/APIDeleteEventRuleTemplateMsg.java |
| 9 | `APIDeleteMetricRuleTemplateMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/monitorgroup/api/APIDeleteMetricRuleTemplateMsg.java |
| 10 | `APIDeleteMonitorGroupMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/monitorgroup/api/APIDeleteMonitorGroupMsg.java |
| 11 | `APIDeleteMonitorTemplateMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/monitorgroup/api/APIDeleteMonitorTemplateMsg.java |
| 12 | `APIQueryEventRuleTemplateMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/monitorgroup/api/APIQueryEventRuleTemplateMsg.java |
| 13 | `APIQueryMetricRuleTemplateMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/monitorgroup/api/APIQueryMetricRuleTemplateMsg.java |
| 14 | `APIQueryMonitorGroupAlarmMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/monitorgroup/api/APIQueryMonitorGroupAlarmMsg.java |
| 15 | `APIQueryMonitorGroupEventSubscriptionMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/monitorgroup/api/APIQueryMonitorGroupEventSubscriptionMsg.java |
| 16 | `APIQueryMonitorGroupInstanceMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/monitorgroup/api/APIQueryMonitorGroupInstanceMsg.java |
| 17 | `APIQueryMonitorGroupMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/monitorgroup/api/APIQueryMonitorGroupMsg.java |
| 18 | `APIQueryMonitorGroupTemplateRefMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/monitorgroup/api/APIQueryMonitorGroupTemplateRefMsg.java |
| 19 | `APIQueryMonitorTemplateMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/monitorgroup/api/APIQueryMonitorTemplateMsg.java |
| 20 | `APIRemoveInstanceFromMonitorGroupMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/monitorgroup/api/APIRemoveInstanceFromMonitorGroupMsg.java |
| 21 | `APIRevokeMonitorTemplateFromMonitorGroupMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/monitorgroup/api/APIRevokeMonitorTemplateFromMonitorGroupMsg.java |
| 22 | `APIUpdateEventRuleTemplateMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/monitorgroup/api/APIUpdateEventRuleTemplateMsg.java |
| 23 | `APIUpdateMetricRuleTemplateMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/monitorgroup/api/APIUpdateMetricRuleTemplateMsg.java |
| 24 | `APIUpdateMonitorGroupMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/monitorgroup/api/APIUpdateMonitorGroupMsg.java |
| 25 | `APIUpdateMonitorTemplateMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/monitorgroup/api/APIUpdateMonitorTemplateMsg.java |

### `org.zstack.zwatch.thirdparty.api`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddThirdpartyPlatformMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/thirdparty/api/APIAddThirdpartyPlatformMsg.java |
| 2 | `APIDeleteThirdpartyPlatformMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/thirdparty/api/APIDeleteThirdpartyPlatformMsg.java |
| 3 | `APIQuerySNSEndpointThirdpartyAlertHistoryMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/thirdparty/api/APIQuerySNSEndpointThirdpartyAlertHistoryMsg.java |
| 4 | `APIQueryThirdpartyAlertMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/thirdparty/api/APIQueryThirdpartyAlertMsg.java |
| 5 | `APIQueryThirdpartyPlatformMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/thirdparty/api/APIQueryThirdpartyPlatformMsg.java |
| 6 | `APIUpdateThirdpartyAlertsMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/thirdparty/api/APIUpdateThirdpartyAlertsMsg.java |
| 7 | `APIUpdateThirdpartyPlatformMsg` | premium/zwatch/src/main/java/org/zstack/zwatch/thirdparty/api/APIUpdateThirdpartyPlatformMsg.java |

---

<a id="module-resourceconfig"></a>

## 模块：`resourceconfig`

### `org.zstack.resourceconfig`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIDeleteResourceConfigMsg` | resourceconfig/src/main/java/org/zstack/resourceconfig/APIDeleteResourceConfigMsg.java |
| 2 | `APIGetResourceBindableConfigMsg` | resourceconfig/src/main/java/org/zstack/resourceconfig/APIGetResourceBindableConfigMsg.java |
| 3 | `APIGetResourceConfigMsg` | resourceconfig/src/main/java/org/zstack/resourceconfig/APIGetResourceConfigMsg.java |
| 4 | `APIGetResourceConfigsMsg` | resourceconfig/src/main/java/org/zstack/resourceconfig/APIGetResourceConfigsMsg.java |
| 5 | `APIQueryResourceConfigMsg` | resourceconfig/src/main/java/org/zstack/resourceconfig/APIQueryResourceConfigMsg.java |
| 6 | `APIUpdateResourceConfigMsg` | resourceconfig/src/main/java/org/zstack/resourceconfig/APIUpdateResourceConfigMsg.java |
| 7 | `APIUpdateResourceConfigsMsg` | resourceconfig/src/main/java/org/zstack/resourceconfig/APIUpdateResourceConfigsMsg.java |

---

<a id="module-search"></a>

## 模块：`search`

### `org.zstack.query`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIBatchQueryMsg` | search/src/main/java/org/zstack/query/APIBatchQueryMsg.java |
| 2 | `APIZQLQueryMsg` | search/src/main/java/org/zstack/query/APIZQLQueryMsg.java |

### `org.zstack.search`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIRefreshSearchIndexesMsg` | search/src/main/java/org/zstack/search/APIRefreshSearchIndexesMsg.java |

---

<a id="module-simulatorsimulatorheader"></a>

## 模块：`simulator/simulatorHeader`

### `org.zstack.header.simulator`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddSimulatorHostMsg` | simulator/simulatorHeader/src/main/java/org/zstack/header/simulator/APIAddSimulatorHostMsg.java |

### `org.zstack.header.simulator.storage.backup`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddSimulatorBackupStorageMsg` | simulator/simulatorHeader/src/main/java/org/zstack/header/simulator/storage/backup/APIAddSimulatorBackupStorageMsg.java |

### `org.zstack.header.simulator.storage.primary`

| # | API 消息类 | 源文件路径 |
|---|-----------|-----------|
| 1 | `APIAddSimulatorPrimaryStorageMsg` | simulator/simulatorHeader/src/main/java/org/zstack/header/simulator/storage/primary/APIAddSimulatorPrimaryStorageMsg.java |

---


