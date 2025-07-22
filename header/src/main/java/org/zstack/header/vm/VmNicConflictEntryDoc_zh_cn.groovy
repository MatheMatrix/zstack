package org.zstack.header.vm

doc {

	title "网卡冲突信息"

	field {
		name "ip"
		desc "冲突的ip"
		type "String"
		since "4.10.16"
	}
	field {
		name "mac"
		desc "冲突的mac"
		type "String"
		since "4.10.16"
	}
	ref {
		name "vmNicInventory"
		path "org.zstack.header.vm.NetworkConflict.vmNicInventory"
		desc "null"
		type "VmNicInventory"
		since "4.10.16"
		clz VmNicInventory.class
	}
	field {
		name "vmInstanceUuid"
		desc "虚拟机UUID"
		type "String"
		since "4.10.16"
	}
	field {
		name "vmInstanceName"
		desc "虚拟机名称"
		type "String"
		since "4.10.16"
	}
}
