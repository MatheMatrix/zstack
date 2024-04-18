package org.zstack.header.vm

import org.zstack.header.vm.TemplateVmInstanceInventory
import org.zstack.header.errorcode.ErrorCode

doc {

	title "在这里输入结构的名称"

	ref {
		name "inventories"
		path "org.zstack.header.vm.APIQueryTemplateVmInstanceReply.inventories"
		desc "null"
		type "List"
		since "4.2.0"
		clz TemplateVmInstanceInventory.class
	}
	field {
		name "success"
		desc ""
		type "boolean"
		since "4.2.0"
	}
	ref {
		name "error"
		path "org.zstack.header.vm.APIQueryTemplateVmInstanceReply.error"
		desc "错误码，若不为null，则表示操作失败, 操作成功时该字段为null",false
		type "ErrorCode"
		since "4.2.0"
		clz ErrorCode.class
	}
}
