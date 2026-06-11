package org.zstack.sdnController.znsproxy;

import org.apache.commons.io.FileUtils;
import org.springframework.util.StringUtils;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.header.exception.CloudRuntimeException;
import org.zstack.kvm.KVMHostVO;
import org.zstack.sdnController.SdnControllerSystemTags;
import org.zstack.tag.SystemTagCreator;
import org.zstack.utils.path.PathUtil;
import org.zstack.utils.ssh.Ssh;
import org.zstack.utils.ssh.SshResult;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.zstack.sdnController.ZnsProxyGlobalProperty.PACKAGE_PATH;
import static org.zstack.sdnController.ZnsProxyGlobalProperty.PACKAGE_REMOTE_PATH;
import static org.zstack.sdnController.ZnsProxyGlobalProperty.PACKAGE_REPOSITORY_PATH;
import static org.zstack.sdnController.ZnsProxyGlobalProperty.PROXY_PACKAGE_NAME;

public class ZnsProxyInstaller {
    private static final int DEFAULT_PROXY_LISTEN_PORT = 7890;

    private final DatabaseFacade dbf;

    public ZnsProxyInstaller(DatabaseFacade dbf) {
        this.dbf = dbf;
    }

    public void install(ZnsProxyPrepareServiceCmd cmd) {
        if (cmd == null) {
            throw new CloudRuntimeException("prepare zns-proxy service failed: command is empty");
        }
        List<String> hostUuids = normalizeHostUuids(cmd.hostUuids, "prepare");

        File localPackage = resolvePackage(cmd);
        for (String hostUuid : hostUuids) {
            installOnHost(hostUuid, localPackage);
        }
    }

    public void cleanup(ZnsProxyUnprepareServiceCmd cmd) {
        if (cmd == null) {
            throw new CloudRuntimeException("unprepare zns-proxy service failed: command is empty");
        }
        List<String> hostUuids = normalizeHostUuids(cmd.hostUuids, "unprepare");
        for (String hostUuid : hostUuids) {
            cleanupOnHost(hostUuid);
        }
    }

    public void reinstallPreparedHost(String hostUuid) {
        installOnHost(hostUuid, resolvePackage(new ZnsProxyPrepareServiceCmd()));
    }

    private void installOnHost(String hostUuid, File localPackage) {
        KVMHostVO host = dbf.findByUuid(hostUuid, KVMHostVO.class);
        if (host == null) {
            throw new CloudRuntimeException(String.format("prepare zns-proxy service failed: host %s not found", hostUuid));
        }
        if (StringUtils.isEmpty(host.getManagementIp())) {
            throw new CloudRuntimeException(String.format("prepare zns-proxy service failed: host %s has empty management ip", hostUuid));
        }
        if (StringUtils.isEmpty(host.getUsername())) {
            throw new CloudRuntimeException(String.format("prepare zns-proxy service failed: host %s has empty username", hostUuid));
        }
        if (StringUtils.isEmpty(host.getPassword())) {
            throw new CloudRuntimeException(String.format("prepare zns-proxy service failed: host %s has empty password", hostUuid));
        }

        String remotePackage = "/tmp/zns-proxy-" + UUID.randomUUID() + ".bin";

        Ssh ssh = new Ssh()
                .setHostname(host.getManagementIp())
                .setUsername(host.getUsername())
                .setPassword(host.getPassword())
                .setPort(host.getPort() == null || host.getPort() <= 0 ? 22 : host.getPort())
                .setTimeout(10)
                .setExecTimeout(600);

        SshResult ret = ssh
                .scpUpload(localPackage.getAbsolutePath(), remotePackage)
                .sudoCommand(
                        "mkdir -p " + shellQuote(PACKAGE_REMOTE_PATH),
                        "mv " + shellQuote(remotePackage) + " " + shellQuote(PACKAGE_PATH),
                        "chmod 0755 " + shellQuote(PACKAGE_PATH),
                        buildInstallCommand(PACKAGE_PATH),
                        "curl -fsS " + shellQuote(healthUrl())
                )
                .runAndClose();
        if (ret == null || ret.getReturnCode() != 0) {
            String stderr = ret == null ? "no ssh result returned" : ret.getStderr();
            throw new CloudRuntimeException(String.format(
                    "prepare zns-proxy service failed on host %s[%s]: %s",
                    hostUuid, host.getManagementIp(), stderr));
        }
        markHostPrepared(hostUuid);
    }

    private void cleanupOnHost(String hostUuid) {
        KVMHostVO host = dbf.findByUuid(hostUuid, KVMHostVO.class);
        if (host == null) {
            throw new CloudRuntimeException(String.format("unprepare zns-proxy service failed: host %s not found", hostUuid));
        }
        if (StringUtils.isEmpty(host.getManagementIp())) {
            throw new CloudRuntimeException(String.format("unprepare zns-proxy service failed: host %s has empty management ip", hostUuid));
        }
        if (StringUtils.isEmpty(host.getUsername())) {
            throw new CloudRuntimeException(String.format("unprepare zns-proxy service failed: host %s has empty username", hostUuid));
        }
        if (StringUtils.isEmpty(host.getPassword())) {
            throw new CloudRuntimeException(String.format("unprepare zns-proxy service failed: host %s has empty password", hostUuid));
        }

        Ssh ssh = new Ssh()
                .setHostname(host.getManagementIp())
                .setUsername(host.getUsername())
                .setPassword(host.getPassword())
                .setPort(host.getPort() == null || host.getPort() <= 0 ? 22 : host.getPort())
                .setTimeout(10)
                .setExecTimeout(600);

        SshResult ret = ssh
                .sudoCommand(buildCleanupCommand())
                .runAndClose();
        if (ret == null || ret.getReturnCode() != 0) {
            String stderr = ret == null ? "no ssh result returned" : ret.getStderr();
            throw new CloudRuntimeException(String.format(
                    "unprepare zns-proxy service failed on host %s[%s]: %s",
                    hostUuid, host.getManagementIp(), stderr));
        }
        clearHostPrepared(hostUuid);
    }

    public static String buildInstallCommand(String packagePath) {
        return shellQuote(packagePath) + " install --listen-address 0.0.0.0:" + DEFAULT_PROXY_LISTEN_PORT;
    }

    public static String buildCleanupCommand() {
        return "systemctl stop zstack-zns-agent.service || true; " +
                "systemctl disable zstack-zns-agent.service || true; " +
                "rm -f /usr/lib/systemd/system/zstack-zns-agent.service; " +
                "rm -f /etc/systemd/system/zstack-zns-agent.service; " +
                "rm -f /etc/zstack-zns/zns-agent.toml; " +
                "rm -f /etc/logrotate.d/zns-agent; " +
                "rm -rf /usr/local/zstack/zns-agent; " +
                "rm -rf /var/lib/zstack/zns-agent/package; " +
                "systemctl stop zstack-zns-proxy.service || true; " +
                "systemctl disable zstack-zns-proxy.service || true; " +
                "rm -f /usr/lib/systemd/system/zstack-zns-proxy.service; " +
                "rm -f /etc/systemd/system/zstack-zns-proxy.service; " +
                "rm -f /etc/zstack-zns/zns-proxy.toml; " +
                "rm -f /etc/logrotate.d/zns-proxy; " +
                "rm -rf /usr/local/zstack/zns-proxy; " +
                "rm -rf " + shellQuote(PACKAGE_REMOTE_PATH) + "; " +
                "systemctl daemon-reload";
    }

    public static File resolvePackage(ZnsProxyPrepareServiceCmd cmd) {
        if (cmd == null) {
            throw new CloudRuntimeException("prepare zns-proxy service failed: command is empty");
        }

        if (StringUtils.hasText(cmd.packageUrl)) {
            String packageName = StringUtils.hasText(cmd.packageName) ? cmd.packageName.trim() : basenameFromUrl(cmd.packageUrl);
            File target = packageInRepository(packageName);
            try {
                FileUtils.forceMkdir(target.getParentFile());
                FileUtils.copyURLToFile(new URL(cmd.packageUrl.trim()), target, 10000, 300000);
            } catch (IOException e) {
                throw new CloudRuntimeException(String.format(
                        "prepare zns-proxy service failed: download package url %s failed: %s",
                        cmd.packageUrl, e.getMessage()), e);
            }
            verifySha256(target, cmd.sha256);
            return target;
        }

        String packageName;
        if (StringUtils.hasText(cmd.packageName)) {
            packageName = cmd.packageName.trim();
        } else if (StringUtils.hasText(cmd.proxyVersion)) {
            packageName = "zns-proxy-" + cmd.proxyVersion.trim() + ".bin";
        } else {
            packageName = PROXY_PACKAGE_NAME;
        }

        File localPackage = packageInRepository(packageName);
        if (!localPackage.exists() || !localPackage.isFile()) {
            File classpathPackage = PathUtil.findFileOnClassPath("ansible/znsproxy/" + packageName);
            if (classpathPackage == null || !classpathPackage.exists() || !classpathPackage.isFile()) {
                throw new CloudRuntimeException(String.format(
                        "prepare zns-proxy service failed: package %s not found in %s or classpath ansible/znsproxy",
                        packageName, PACKAGE_REPOSITORY_PATH));
            }
            localPackage = classpathPackage;
        }
        verifySha256(localPackage, cmd.sha256);
        return localPackage;
    }

    private static String healthUrl() {
        return "http://127.0.0.1:" + DEFAULT_PROXY_LISTEN_PORT + "/zns-proxy/api/v1/health";
    }

    private void markHostPrepared(String hostUuid) {
        SystemTagCreator creator = SdnControllerSystemTags.ZNS_PROXY_PREPARED.newSystemTagCreator(hostUuid);
        creator.ignoreIfExisting = true;
        creator.recreate = false;
        creator.create();
    }

    private void clearHostPrepared(String hostUuid) {
        SdnControllerSystemTags.ZNS_PROXY_PREPARED.delete(hostUuid);
    }

    private static List<String> normalizeHostUuids(List<String> hostUuids, String action) {
        if (hostUuids == null || hostUuids.isEmpty()) {
            throw new CloudRuntimeException(action + " zns-proxy service failed: hostUuids is empty");
        }

        List<String> normalized = new ArrayList<String>(hostUuids.size());
        for (String hostUuid : hostUuids) {
            if (!StringUtils.hasText(hostUuid)) {
                throw new CloudRuntimeException(action + " zns-proxy service failed: hostUuids is empty");
            }
            normalized.add(hostUuid.trim());
        }
        return normalized;
    }

    private static String shellQuote(String value) {
        if (value == null) {
            return "''";
        }
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private static File packageInRepository(String packageName) {
        validatePackageName(packageName);
        return new File(PACKAGE_REPOSITORY_PATH, packageName);
    }

    private static void validatePackageName(String packageName) {
        if (!StringUtils.hasText(packageName)) {
            throw new CloudRuntimeException("prepare zns-proxy service failed: packageName is empty");
        }
        String trimmed = packageName.trim();
        if (!trimmed.equals(new File(trimmed).getName()) || trimmed.contains("..")) {
            throw new CloudRuntimeException(String.format(
                    "prepare zns-proxy service failed: invalid packageName %s",
                    packageName));
        }
    }

    private static String basenameFromUrl(String packageUrl) {
        try {
            String path = new URL(packageUrl.trim()).getPath();
            String name = new File(path).getName();
            validatePackageName(name);
            return name;
        } catch (MalformedURLException e) {
            throw new CloudRuntimeException(String.format(
                    "prepare zns-proxy service failed: invalid packageUrl %s",
                    packageUrl), e);
        }
    }

    private static void verifySha256(File file, String expected) {
        if (!StringUtils.hasText(expected)) {
            return;
        }
        String actual = sha256(file);
        if (!actual.equalsIgnoreCase(expected.trim())) {
            throw new CloudRuntimeException(String.format(
                    "prepare zns-proxy service failed: sha256 mismatch for %s, expect=%s actual=%s",
                    file.getAbsolutePath(), expected, actual));
        }
    }

    private static String sha256(File file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = FileUtils.readFileToByteArray(file);
            byte[] sum = digest.digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : sum) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new CloudRuntimeException(String.format(
                    "prepare zns-proxy service failed: calculate sha256 for %s failed: %s",
                    file.getAbsolutePath(), e.getMessage()), e);
        }
    }
}
