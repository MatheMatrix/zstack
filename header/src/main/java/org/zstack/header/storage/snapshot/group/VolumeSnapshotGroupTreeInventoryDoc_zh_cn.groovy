package org.zstack.header.storage.snapshot.group

import java.sql.Timestamp

doc {

	title "虚拟机快照组树清单"

	field {
		name "uuid"
		desc "快照组UUID"
		type "String"
		since "5.0"
	}
	field {
		name "name"
		desc "快照组名称"
		type "String"
		since "5.0"
	}
	field {
		name "description"
		desc "快照组描述"
		type "String"
		since "5.0"
	}
	field {
		name "vmInstanceUuid"
		desc "虚拟机UUID"
		type "String"
		since "5.0"
	}
	field {
		name "createDate"
		desc "创建时间"
		type "Timestamp"
		since "5.0"
	}
	field {
		name "lastOpDate"
		desc "最后一次修改时间"
		type "Timestamp"
		since "5.0"
	}
	field {
		name "current"
		desc "是否是当前快照组（虚拟机维度）"
		type "boolean"
		since "5.0"
	}
	field {
		name "incomplete"
		desc "是否为残缺快照组（部分盘的快照已被删除）"
		type "boolean"
		since "5.0"
	}
	field {
		name "parentGroupUuid"
		desc "父快照组UUID"
		type "String"
		since "5.0"
	}
	ref {
		name "children"
		path "org.zstack.header.storage.snapshot.group.VolumeSnapshotGroupTreeInventory.children"
		desc "子快照组列表"
		type "List"
		since "5.0"
		clz VolumeSnapshotGroupTreeInventory.class
	}
	ref {
		name "refs"
		path "org.zstack.header.storage.snapshot.group.VolumeSnapshotGroupTreeInventory.refs"
		desc "快照组成员盘列表"
		type "List"
		since "5.0"
		clz VolumeSnapshotGroupTreeRefInventory.class
	}
}
