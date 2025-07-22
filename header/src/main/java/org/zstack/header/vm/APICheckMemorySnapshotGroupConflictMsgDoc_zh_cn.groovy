package org.zstack.header.vm

doc {
	title "GetMemorySnapshotGroupReference"

	category "snapshot.volume"

	desc """检测内存快照组资源冲突"""

	rest {
		request {
			url "GET /v1/memory-snapshots/group/conflict-detection"

			header (Authorization: 'OAuth the-session-uuid')

			clz APICheckMemorySnapshotGroupConflictMsg.class

			desc """"""

			params {

				column {
					name "resourceUuid"
					enclosedIn ""
					desc "快照组UUID"
					location "query"
					type "String"
					optional false
					since "4.10.16"
				}
				column {
					name "systemTags"
					enclosedIn ""
					desc "系统标签"
					location "query"
					type "List"
					optional true
					since "3.14.24"
				}
				column {
					name "userTags"
					enclosedIn ""
					desc "用户标签"
					location "query"
					type "List"
					optional true
					since "3.14.24"
				}
			}
		}

		response {
			clz APICheckMemorySnapshotGroupConflictReply.class
		}
	}
}