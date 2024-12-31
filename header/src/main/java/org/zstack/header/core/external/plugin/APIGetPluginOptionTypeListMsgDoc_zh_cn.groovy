package org.zstack.header.core.external.plugin

import org.zstack.header.core.external.plugin.APIGetPluginOptionTypeListReply

doc {
    title "GetPluginOptionTypeList"

    category "external.plugin"

    desc """获取插件选项类型列表"""

    rest {
        request {
			url "GET /v1/external/plugins/option/list"

			header (Authorization: 'OAuth the-session-uuid')

            clz APIGetPluginOptionTypeListMsg.class

            desc """"""
            
			params {

				column {
					name "pluginUuid"
					enclosedIn ""
					desc "插件驱动器uuid"
					location "query"
					type "String"
					optional true
					since "5.3.0"
				}
				column {
					name "systemTags"
					enclosedIn ""
					desc "系统标签"
					location "query"
					type "List"
					optional true
					since "5.3.0"
				}
				column {
					name "userTags"
					enclosedIn ""
					desc "用户标签"
					location "query"
					type "List"
					optional true
					since "5.3.0"
				}
			}
        }

        response {
            clz APIGetPluginOptionTypeListReply.class
        }
    }
}
