package org.zstack.header.volume

import org.zstack.header.errorcode.ErrorCode

doc {

	title "加载数据云盘到物理机返回"

	field {
		name "success"
		desc "加载数据云盘到物理机返回"
		type "boolean"
		since "4.5.0"
	}
	ref {
		name "error"
		path "org.zstack.header.volume.APIAttachDataVolumeToHostEvent.error"
		desc "错误码，若不为null，则表示操作失败, 操作成功时该字段为null",false
		type "ErrorCode"
		since "4.5.0"
		clz ErrorCode.class
	}
}
