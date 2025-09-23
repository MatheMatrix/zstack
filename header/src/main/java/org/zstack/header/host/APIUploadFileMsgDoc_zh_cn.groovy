package org.zstack.header.host

import org.zstack.header.host.APIUploadFileEvent

doc {
	title "UploadFile"

	category "未知类别"

	desc """在这里填写API描述"""

	rest {
		request {
			url "POST /v1/host/{uuid}/upload-file"

			header (Authorization: 'OAuth the-session-uuid')

			clz APIUploadFileMsg.class

			desc """"""

			params {

				column {
					name "uuid"
					enclosedIn ""
					desc "资源的UUID，唯一标示该资源"
					location "url"
					type "String"
					optional false
					since "4.10.0"
				}
				column {
					name "url"
					enclosedIn ""
					desc ""
					location "body"
					type "String"
					optional true
					since "4.10.0"
				}
				column {
					name "installPath"
					enclosedIn ""
					desc ""
					location "body"
					type "String"
					optional true
					since "4.10.0"
				}
				column {
					name "systemTags"
					enclosedIn ""
					desc "系统标签"
					location "body"
					type "List"
					optional true
					since "4.10.0"
				}
				column {
					name "userTags"
					enclosedIn ""
					desc "用户标签"
					location "body"
					type "List"
					optional true
					since "4.10.0"
				}
			}
		}

		response {
			clz APIUploadFileEvent.class
		}
	}
}