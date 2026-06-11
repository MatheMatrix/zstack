package org.zstack.test.unittest.sdnController;

import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.zstack.sdnController.ZnsProxyGlobalProperty;
import org.zstack.sdnController.znsproxy.ZnsProxyInstaller;
import org.zstack.sdnController.znsproxy.ZnsProxyPrepareServiceCmd;

import java.io.File;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ZnsProxyInstallerTest {
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testBuildInstallCommandUsesPackageDefaults() {
        String command = ZnsProxyInstaller.buildInstallCommand("/var/lib/zstack/zns-proxy/package/zns-proxy.bin");
        assertEquals("'/var/lib/zstack/zns-proxy/package/zns-proxy.bin' install --listen-address 0.0.0.0:7890", command);
    }

    @Test
    public void testBuildCleanupCommandRemovesProxyAndAgent() {
        ZnsProxyGlobalProperty.PACKAGE_REMOTE_PATH = "/var/lib/zstack/zns-proxy/package";

        String command = ZnsProxyInstaller.buildCleanupCommand();
        assertTrue(command.contains("systemctl stop zstack-zns-agent.service || true"));
        assertTrue(command.contains("systemctl disable zstack-zns-agent.service || true"));
        assertTrue(command.contains("rm -rf /var/lib/zstack/zns-agent/package"));
        assertTrue(command.contains("systemctl stop zstack-zns-proxy.service || true"));
        assertTrue(command.contains("rm -rf '/var/lib/zstack/zns-proxy/package'"));
    }

    @Test
    public void testResolvePackageByPackageName() throws Exception {
        File repo = tempFolder.newFolder("repo");
        ZnsProxyGlobalProperty.PACKAGE_REPOSITORY_PATH = repo.getAbsolutePath();
        File pkg = new File(repo, "zns-proxy-1.2.3.bin");
        FileUtils.writeStringToFile(pkg, "proxy", StandardCharsets.UTF_8);

        ZnsProxyPrepareServiceCmd cmd = new ZnsProxyPrepareServiceCmd();
        cmd.packageName = "zns-proxy-1.2.3.bin";

        assertEquals(pkg.getAbsolutePath(), ZnsProxyInstaller.resolvePackage(cmd).getAbsolutePath());
    }

    @Test
    public void testResolvePackageByProxyVersion() throws Exception {
        File repo = tempFolder.newFolder("repo-version");
        ZnsProxyGlobalProperty.PACKAGE_REPOSITORY_PATH = repo.getAbsolutePath();
        File pkg = new File(repo, "zns-proxy-1.2.3.bin");
        FileUtils.writeStringToFile(pkg, "proxy", StandardCharsets.UTF_8);

        ZnsProxyPrepareServiceCmd cmd = new ZnsProxyPrepareServiceCmd();
        cmd.proxyVersion = "1.2.3";

        assertEquals(pkg.getAbsolutePath(), ZnsProxyInstaller.resolvePackage(cmd).getAbsolutePath());
    }

    @Test(expected = RuntimeException.class)
    public void testResolvePackageRejectsPathTraversal() throws Exception {
        File repo = tempFolder.newFolder("repo-traversal");
        ZnsProxyGlobalProperty.PACKAGE_REPOSITORY_PATH = repo.getAbsolutePath();

        ZnsProxyPrepareServiceCmd cmd = new ZnsProxyPrepareServiceCmd();
        cmd.packageName = "../bad.bin";

        ZnsProxyInstaller.resolvePackage(cmd);
    }

    @Test(expected = RuntimeException.class)
    public void testResolvePackageFailsWhenMissing() throws Exception {
        File repo = tempFolder.newFolder("repo-missing");
        ZnsProxyGlobalProperty.PACKAGE_REPOSITORY_PATH = repo.getAbsolutePath();

        ZnsProxyPrepareServiceCmd cmd = new ZnsProxyPrepareServiceCmd();
        cmd.packageName = "missing.bin";

        ZnsProxyInstaller.resolvePackage(cmd);
    }

    @Test
    public void testResolvePackageDownloadsUrl() throws Exception {
        File repo = tempFolder.newFolder("repo-download");
        File src = tempFolder.newFile("zns-proxy-url.bin");
        FileUtils.writeStringToFile(src, "proxy-url", StandardCharsets.UTF_8);
        ZnsProxyGlobalProperty.PACKAGE_REPOSITORY_PATH = repo.getAbsolutePath();

        ZnsProxyPrepareServiceCmd cmd = new ZnsProxyPrepareServiceCmd();
        cmd.packageUrl = src.toURI().toURL().toString();

        File resolved = ZnsProxyInstaller.resolvePackage(cmd);
        assertEquals(new File(repo, "zns-proxy-url.bin").getAbsolutePath(), resolved.getAbsolutePath());
        assertTrue(resolved.exists());
        assertTrue(resolved.length() > 0);
    }
}
