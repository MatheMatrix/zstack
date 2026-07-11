package org.zstack.core.agent

import org.junit.Test
import org.zstack.core.ManagementServerIpSelection
import org.zstack.core.rest.RESTFacadeImpl
import org.zstack.header.rest.RESTFacade

class AgentManagerCommandUrlCase {
    private static final String IPV4 = "192.168.1.10"
    private static final String IPV6 = "2001:db8::1"
    private static final String REMOTE_IPV6 = "2001:db8::2"
    private static final int REST_PORT = 8080

    @Test
    void testStrictSelectionAndHostnameFallback() {
        RESTFacade restf = [
                getSendCommandUrl: { "http://${IPV4}:${REST_PORT}/zstack/command".toString() },
                buildSendCommandUrlForManagementHost: { String host ->
                    RESTFacadeImpl.buildSendCommandUrl(host, REST_PORT, "/zstack")
                }
        ] as RESTFacade
        ManagementServerIpSelection selection = ManagementServerIpSelection.success(
                IPV6, REMOTE_IPV6, 6, [IPV4, IPV6], IPV6)

        assert AgentManagerImpl.buildCommandUrl(restf, selection) ==
                "http://[${IPV6}]:${REST_PORT}/zstack/asyncrest/sendcommand"
        assert AgentManagerImpl.buildCommandUrl(restf, null) ==
                "http://${IPV4}:${REST_PORT}/zstack/command"
    }
}
