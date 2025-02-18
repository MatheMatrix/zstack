package org.zstack.sdnController.header



doc {

	title "SDN控制器物理机清单"

	field {
		name "sdnControllerUuid"
		desc "SDN控制器Uuid"
		type "String"
		since "5.3.0"
	}
	field {
		name "hostUuid"
		desc "物理机UUID"
		type "String"
		since "5.3.0"
	}
	field {
		name "vSwitchType"
		desc "虚拟交换机类型"
		type "String"
		since "5.3.0"
	}
	field {
		name "vtepIp"
		desc "物理机VTEP IP"
		type "String"
		since "5.3.0"
	}
	field {
		name "nicPciAddresses"
		desc "物理机网卡和PCI地址映射"
		type "String"
		since "5.3.0"
	}
	field {
		name "nicDrivers"
		desc "物理机网卡和驱动类型映射"
		type "String"
		since "5.3.0"
	}
	field {
		name "netmask"
		desc "物理机VTEP IP掩码"
		type "String"
		since "5.3.0"
	}
	field {
		name "bondMode"
		desc "物理机网卡bond模式"
		type "String"
		since "5.3.0"
	}
	field {
		name "lacpMode"
		desc "物理机网卡LACP模式"
		type "String"
		since "5.3.0"
	}
}
