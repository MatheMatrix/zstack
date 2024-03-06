package org.zstack.header.vm

import org.zstack.header.errorcode.ErrorCode
import org.zstack.header.vm.VmInstanceTemplateInventory
import org.zstack.header.volume.VolumeTemplateInventory

doc {

	title "在这里输入结构的名称"

	field {
		name "success"
		desc ""
		type "boolean"
		since "4.2.0"
	}
	ref {
		name "error"
		path "org.zstack.header.vm.APICreateVmInstanceTemplateEvent.error"
		desc "错误码，若不为null，则表示操作失败, 操作成功时该字段为null",false
		type "ErrorCode"
		since "4.2.0"
		clz ErrorCode.class
	}
	ref {
		name "vmTemplate"
		path "org.zstack.header.vm.APICreateVmInstanceTemplateEvent.vmTemplate"
		desc "null"
		type "VmInstanceTemplateInventory"
		since "4.2.0"
		clz VmInstanceTemplateInventory.class
	}
	ref {
		name "volumeTemplates"
		path "org.zstack.header.vm.APICreateVmInstanceTemplateEvent.volumeTemplates"
		desc "null"
		type "List"
		since "4.2.0"
		clz VolumeTemplateInventory.class
	}
}
