package org.zstack.header.image

import java.lang.Long
import java.lang.Boolean
import java.sql.Timestamp

doc {

	title "在这里输入结构的名称"

	field {
		name "imageUuid"
		desc "镜像UUID"
		type "String"
		since "5.3.20"
	}
	field {
		name "imageGroupUuid"
		desc ""
		type "String"
		since "5.3.20"
	}
	field {
		name "description"
		desc "资源的详细描述"
		type "String"
		since "5.3.20"
	}
	field {
		name "state"
		desc ""
		type "String"
		since "5.3.20"
	}
	field {
		name "status"
		desc ""
		type "String"
		since "5.3.20"
	}
	field {
		name "size"
		desc ""
		type "Long"
		since "5.3.20"
	}
	field {
		name "actualSize"
		desc ""
		type "Long"
		since "5.3.20"
	}
	field {
		name "md5Sum"
		desc ""
		type "String"
		since "5.3.20"
	}
	field {
		name "url"
		desc ""
		type "String"
		since "5.3.20"
	}
	field {
		name "mediaType"
		desc ""
		type "String"
		since "5.3.20"
	}
	field {
		name "guestOsType"
		desc ""
		type "String"
		since "5.3.20"
	}
	field {
		name "type"
		desc ""
		type "String"
		since "5.3.20"
	}
	field {
		name "platform"
		desc ""
		type "String"
		since "5.3.20"
	}
	field {
		name "architecture"
		desc ""
		type "String"
		since "5.3.20"
	}
	field {
		name "format"
		desc ""
		type "String"
		since "5.3.20"
	}
	field {
		name "system"
		desc ""
		type "Boolean"
		since "5.3.20"
	}
	field {
		name "virtio"
		desc ""
		type "Boolean"
		since "5.3.20"
	}
	field {
		name "createDate"
		desc "创建时间"
		type "Timestamp"
		since "5.3.20"
	}
	field {
		name "lastOpDate"
		desc "最后一次修改时间"
		type "Timestamp"
		since "5.3.20"
	}
}
