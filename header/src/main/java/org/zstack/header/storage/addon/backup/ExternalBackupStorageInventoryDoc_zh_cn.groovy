package org.zstack.header.storage.addon.backup

import java.lang.Long
import java.sql.Timestamp

doc {

	title "在这里输入结构的名称"

	field {
		name "identity"
		desc ""
		type "String"
		since "4.7.11"
	}
	field {
		name "uuid"
		desc "资源的UUID，唯一标示该资源"
		type "String"
		since "4.7.11"
	}
	field {
		name "name"
		desc "资源名称"
		type "String"
		since "4.7.11"
	}
	field {
		name "url"
		desc ""
		type "String"
		since "4.7.11"
	}
	field {
		name "description"
		desc "资源的详细描述"
		type "String"
		since "4.7.11"
	}
	field {
		name "totalCapacity"
		desc ""
		type "Long"
		since "4.7.11"
	}
	field {
		name "availableCapacity"
		desc ""
		type "Long"
		since "4.7.11"
	}
	field {
		name "type"
		desc ""
		type "String"
		since "4.7.11"
	}
	field {
		name "state"
		desc ""
		type "String"
		since "4.7.11"
	}
	field {
		name "status"
		desc ""
		type "String"
		since "4.7.11"
	}
	field {
		name "createDate"
		desc "创建时间"
		type "Timestamp"
		since "4.7.11"
	}
	field {
		name "lastOpDate"
		desc "最后一次修改时间"
		type "Timestamp"
		since "4.7.11"
	}
	field {
		name "attachedZoneUuids"
		desc ""
		type "List"
		since "4.7.11"
	}
}