package org.zstack.header.storage.snapshot.group

import org.zstack.header.storage.snapshot.group.APIGetVolumeSnapshotGroupTreeReply

doc {
	title "GetVolumeSnapshotGroupTree"

	category "snapshot.volume"

	desc """查询指定云主机的快照组树"""

	rest {
		request {
			url "GET /v1/volume-snapshots/group/trees"
			url "GET /v1/volume-snapshots/group/trees/{uuid}"

			header (Authorization: 'OAuth the-session-uuid')

			clz APIGetVolumeSnapshotGroupTreeMsg.class

			desc """"""

			params APIGetVolumeSnapshotGroupTreeMsg.class
		}

		response {
			clz APIGetVolumeSnapshotGroupTreeReply.class
		}
	}
}
