package org.zstack.header.image

import java.lang.Integer
import java.sql.Timestamp
import org.zstack.header.image.ImageGroupRefInventory

doc {

	title "在这里输入结构的名称"

	field {
		name "uuid"
		desc "资源的UUID，唯一标示该资源"
		type "String"
		since "5.3.20"
	}
	field {
		name "imageCount"
		desc ""
		type "Integer"
		since "5.3.20"
	}
	field {
		name "name"
		desc "资源名称"
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
	ref {
		name "imageGroupRefs"
		path "org.zstack.header.image.ImageGroupInventory.imageGroupRefs"
		desc "null"
		type "List"
		since "5.3.20"
		clz ImageGroupRefInventory.class
	}
}
