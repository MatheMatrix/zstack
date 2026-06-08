package org.zstack.test.unittest.sdnController;

import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.zstack.sdnController.ZnsAgentGlobalProperty;
import org.zstack.sdnController.znsagent.ZnsAgentInstaller;
import org.zstack.sdnController.znsagent.ZnsAgentPrepareServiceCmd;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ZnsAgentInstallerTest {
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testNormalizeControllerIps() {
        List<String> ips = ZnsAgentInstaller.normalizeControllerIps(Arrays.asList(" 172.24.254.130 ", "", "172.24.191.175", "172.24.254.130"));
        assertEquals(Arrays.asList("172.24.254.130", "172.24.191.175"), ips);
    }

    @Test
    public void testNormalizeControllerAddressesPrefersAddresses() {
        List<String> addresses = ZnsAgentInstaller.normalizeControllerAddresses(
                Arrays.asList(" http://172.24.254.130:7278 ", "172.24.191.175:7278", "172.24.254.130"),
                Arrays.asList("172.24.245.225"));
        assertEquals(Arrays.asList(
                "http://172.24.254.130:7278",
                "http://172.24.191.175:7278"), addresses);
    }

    @Test
    public void testNormalizeControllerAddressesFallsBackToIps() {
        List<String> addresses = ZnsAgentInstaller.normalizeControllerAddresses(null,
                Arrays.asList("172.24.254.130", "172.24.191.175:9090"));
        assertEquals(Arrays.asList("http://172.24.254.130:7278", "http://172.24.191.175:9090"), addresses);
    }

    @Test
    public void testRenderConfigIncludesAgentSection() {
        String config = ZnsAgentInstaller.renderConfig("host-uuid-1", Arrays.asList("http://172.24.254.130:7278", "http://172.24.191.175:7278"));
        assertTrue(config.contains("[server]"));
        assertTrue(config.contains("[agent]"));
        assertTrue(config.contains("hostUuid = \"host-uuid-1\""));
        assertTrue(config.contains("\"http://172.24.254.130:7278\""));
        assertTrue(config.contains("\"http://172.24.191.175:7278\""));
    }

    @Test
    public void testResolvePackageByPackageName() throws Exception {
        File repo = tempFolder.newFolder("repo");
        ZnsAgentGlobalProperty.PACKAGE_REPOSITORY_PATH = repo.getAbsolutePath();
        File pkg = new File(repo, "zns-agent-1.2.3.bin");
        FileUtils.writeStringToFile(pkg, "agent", StandardCharsets.UTF_8);

        ZnsAgentPrepareServiceCmd cmd = new ZnsAgentPrepareServiceCmd();
        cmd.packageName = "zns-agent-1.2.3.bin";

        assertEquals(pkg.getAbsolutePath(), ZnsAgentInstaller.resolvePackage(cmd).getAbsolutePath());
    }

    @Test
    public void testResolvePackageByAgentVersion() throws Exception {
        File repo = tempFolder.newFolder("repo-version");
        ZnsAgentGlobalProperty.PACKAGE_REPOSITORY_PATH = repo.getAbsolutePath();
        File pkg = new File(repo, "zns-agent-1.2.3.bin");
        FileUtils.writeStringToFile(pkg, "agent", StandardCharsets.UTF_8);

        ZnsAgentPrepareServiceCmd cmd = new ZnsAgentPrepareServiceCmd();
        cmd.agentVersion = "1.2.3";

        assertEquals(pkg.getAbsolutePath(), ZnsAgentInstaller.resolvePackage(cmd).getAbsolutePath());
    }

    @Test(expected = RuntimeException.class)
    public void testResolvePackageRejectsPathTraversal() throws Exception {
        File repo = tempFolder.newFolder("repo-traversal");
        ZnsAgentGlobalProperty.PACKAGE_REPOSITORY_PATH = repo.getAbsolutePath();

        ZnsAgentPrepareServiceCmd cmd = new ZnsAgentPrepareServiceCmd();
        cmd.packageName = "../bad.bin";

        ZnsAgentInstaller.resolvePackage(cmd);
    }

    @Test(expected = RuntimeException.class)
    public void testResolvePackageFailsWhenMissing() throws Exception {
        File repo = tempFolder.newFolder("repo-missing");
        ZnsAgentGlobalProperty.PACKAGE_REPOSITORY_PATH = repo.getAbsolutePath();

        ZnsAgentPrepareServiceCmd cmd = new ZnsAgentPrepareServiceCmd();
        cmd.packageName = "missing.bin";

        ZnsAgentInstaller.resolvePackage(cmd);
    }

    @Test
    public void testResolvePackageDownloadsUrl() throws Exception {
        File repo = tempFolder.newFolder("repo-download");
        File src = tempFolder.newFile("zns-agent-url.bin");
        FileUtils.writeStringToFile(src, "agent-url", StandardCharsets.UTF_8);
        ZnsAgentGlobalProperty.PACKAGE_REPOSITORY_PATH = repo.getAbsolutePath();

        ZnsAgentPrepareServiceCmd cmd = new ZnsAgentPrepareServiceCmd();
        cmd.packageUrl = src.toURI().toURL().toString();

        File resolved = ZnsAgentInstaller.resolvePackage(cmd);
        assertEquals(new File(repo, "zns-agent-url.bin").getAbsolutePath(), resolved.getAbsolutePath());
        assertTrue(resolved.exists());
        assertFalse(resolved.length() == 0);
    }
}
