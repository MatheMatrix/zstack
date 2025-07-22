package org.zstack.header.vm

import org.zstack.header.storage.snapshot.group.VolumeSnapshotGroupInventory
import org.zstack.header.errorcode.ErrorCode

doc {

	title "检测内存快照组资源冲突返回"

	ref {
		name "inventories"
		path "org.zstack.header.vm.APICheckMemorySnapshotGroupConflictReply.networksConflict"
		desc "网络冲突的列表"
		type "List"
		since "4.10.16"
		clz VolumeSnapshotGroupInventory.class
	}
	field {
		name "success"
		desc "请求是否成功"
		type "boolean"
		since "4.10.16"
	}
	ref {
		name "error"
		path "org.zstack.header.vm.APIGetMemorySnapshotGroupReferenceReply.error"
		desc "错误码，若不为null，则表示操作失败, 操作成功时该字段为null"
		type "ErrorCode"
		since "4.10.16"
		clz ErrorCode.class
	}
}
