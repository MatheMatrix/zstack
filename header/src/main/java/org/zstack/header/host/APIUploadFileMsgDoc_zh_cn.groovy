package org.zstack.header.host

doc {
	title "UploadFile"

	category "host"

	desc """上传文件到主机"""

	rest {
		request {
			url "POST /v1/hosts/{uuid}/upload-file"

			header (Authorization: 'OAuth the-session-uuid')

			clz APIUploadFileToHostMsg.class

			desc """"""

			params {

				column {
					name "uuid"
					enclosedIn ""
					desc "主机的UUID"
					location "url"
					type "String"
					optional false
					since "4.10.20"
				}
				column {
					name "url"
					enclosedIn ""
					desc "文件的链接"
					location "body"
					type "String"
					optional true
					since "4.10.20"
				}
				column {
					name "installPath"
					enclosedIn ""
					desc "文件下载的路径"
					location "body"
					type "String"
					optional true
					since "4.10.20"
				}
				column {
					name "systemTags"
					enclosedIn ""
					desc "系统标签"
					location "body"
					type "List"
					optional true
					since "4.10.20"
				}
				column {
					name "userTags"
					enclosedIn ""
					desc "用户标签"
					location "body"
					type "List"
					optional true
					since "4.10.20"
				}
			}
		}

		response {
			clz APIUploadFileToHostEvent.class
		}
	}
}