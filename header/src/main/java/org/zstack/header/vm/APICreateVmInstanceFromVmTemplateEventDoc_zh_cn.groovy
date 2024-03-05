package org.zstack.header.vm

import org.zstack.header.vm.VmInstanceInventory
import org.zstack.header.errorcode.ErrorCode

doc {

	title "在这里输入结构的名称"

	ref {
		name "inventory"
		path "org.zstack.header.vm.APICreateVmInstanceFromVmTemplateEvent.inventory"
		desc "null"
		type "VmInstanceInventory"
		since "4.2.0"
		clz VmInstanceInventory.class
	}
	field {
		name "success"
		desc ""
		type "boolean"
		since "4.2.0"
	}
	ref {
		name "error"
		path "org.zstack.header.vm.APICreateVmInstanceFromVmTemplateEvent.error"
		desc "错误码，若不为null，则表示操作失败, 操作成功时该字段为null",false
		type "ErrorCode"
		since "4.2.0"
		clz ErrorCode.class
	}
}
