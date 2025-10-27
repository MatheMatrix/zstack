package org.zstack.header.host

doc {
	title "UpdateHostNqn"

	category "host"

	desc """更新主机hostname"""

	rest {
		request {
			url "PUT /v1/hosts/hostname/{uuid}/actions"

			header (Authorization: 'OAuth the-session-uuid')

			clz APIUpdateHostNqnMsg.class

			desc """更新主机hostname"""

			params {

				column {
					name "uuid"
					enclosedIn "updateHostNqn"
					desc "主机UUID"
					location "url"
					type "String"
					optional false
					since "4.10.18"
				}
				column {
					name "hostname"
					enclosedIn "updateHostNqn"
					desc "主机hostname"
					location "body"
					type "String"
					optional false
					since "4.10.18"
				}
				column {
					name "systemTags"
					enclosedIn ""
					desc "系统标签"
					location "body"
					type "List"
					optional true
					since "4.10.18"
				}
				column {
					name "userTags"
					enclosedIn ""
					desc "用户标签"
					location "body"
					type "List"
					optional true
					since "4.10.18"
				}
			}
		}

		response {
			clz APIUpdateHostNqnEvent.class
		}
	}
}