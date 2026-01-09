package org.zstack.header.storage.primary

import org.zstack.header.vm.VmInstanceInventory
import org.zstack.header.errorcode.ErrorCode

doc {

	title "注册虚拟机返回"

	ref {
		name "inventory"
		path "org.zstack.header.storage.primary.APIRegisterVmInstanceEvent.inventory"
		desc "null"
		type "VmInstanceInventory"
		since "4.10.0"
		clz VmInstanceInventory.class
	}
	field {
		name "success"
		desc ""
		type "boolean"
		since "4.10.0"
	}
	ref {
		name "error"
		path "org.zstack.header.storage.primary.APIRegisterVmInstanceEvent.error"
		desc "错误码，若不为null，则表示操作失败, 操作成功时该字段为null"
		type "ErrorCode"
		since "4.10.0"
		clz ErrorCode.class
	}
}
