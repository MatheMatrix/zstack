package org.zstack.header.storage.snapshot

import org.zstack.header.errorcode.ErrorCode
import org.zstack.header.storage.snapshot.group.VolumeSnapshotGroupTreeInventory

doc {

	title "快照树清单"

	field {
		name "success"
		desc ""
		type "boolean"
		since "0.6"
	}
	ref {
		name "error"
		path "org.zstack.header.storage.snapshot.APIQueryVolumeSnapshotTreeReply.error"
		desc "错误码，若不为null，则表示操作失败, 操作成功时该字段为null",false
		type "ErrorCode"
		since "0.6"
		clz ErrorCode.class
	}
	ref {
		name "inventories"
		path "org.zstack.header.storage.snapshot.APIQueryVolumeSnapshotTreeReply.inventories"
		desc "null"
		type "List"
		since "0.6"
		clz VolumeSnapshotTreeInventory.class
	}
	ref {
		name "groupTrees"
		path "org.zstack.header.storage.snapshot.APIQueryVolumeSnapshotTreeReply.groupTrees"
		desc "虚拟机快照组树清单（仅当查询条件包含 volumeUuid eq <根云盘UUID> 时返回）"
		type "List"
		since "5.0"
		clz VolumeSnapshotGroupTreeInventory.class
	}
}
