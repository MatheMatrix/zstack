package org.zstack.test.unittest.sdnController;

import org.junit.Test;
import org.zstack.sdnController.znsagent.ZnsAgentInstaller;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ZnsAgentInstallerTest {
    @Test
    public void testNormalizeControllerIps() {
        List<String> ips = ZnsAgentInstaller.normalizeControllerIps(Arrays.asList(" 172.24.254.130 ", "", "172.24.191.175", "172.24.254.130"));
        assertEquals(Arrays.asList("172.24.254.130", "172.24.191.175"), ips);
    }

    @Test
    public void testRenderConfigIncludesAgentSection() {
        String config = ZnsAgentInstaller.renderConfig("host-uuid-1", Arrays.asList("172.24.254.130", "172.24.191.175"));
        assertTrue(config.contains("[server]"));
        assertTrue(config.contains("[agent]"));
        assertTrue(config.contains("hostUuid = \"host-uuid-1\""));
        assertTrue(config.contains("\"http://172.24.254.130:7278\""));
        assertTrue(config.contains("\"http://172.24.191.175:7278\""));
    }
}
