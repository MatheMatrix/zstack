package org.zstack.header.vm

import java.sql.Timestamp

doc {

	title "在这里输入结构的名称"

	field {
		name "uuid"
		desc "资源的UUID，唯一标示该资源"
		type "String"
		since "4.2.0"
	}
	field {
		name "name"
		desc "资源名称"
		type "String"
		since "4.2.0"
	}
	field {
		name "vmInstanceUuid"
		desc "云主机UUID"
		type "String"
		since "4.2.0"
	}
	field {
		name "originalType"
		desc ""
		type "String"
		since "4.2.0"
	}
	field {
		name "createDate"
		desc "创建时间"
		type "Timestamp"
		since "4.2.0"
	}
	field {
		name "lastOpDate"
		desc "最后一次修改时间"
		type "Timestamp"
		since "4.2.0"
	}
}
