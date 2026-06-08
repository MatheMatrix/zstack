package org.zstack.sdnController.znsproxy;

import org.apache.commons.io.FileUtils;
import org.springframework.util.StringUtils;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.header.exception.CloudRuntimeException;
import org.zstack.kvm.KVMHostVO;
import org.zstack.utils.ssh.Ssh;
import org.zstack.utils.ssh.SshResult;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

import static org.zstack.sdnController.ZnsProxyGlobalProperty.CONFIG_PATH;
import static org.zstack.sdnController.ZnsProxyGlobalProperty.LISTEN_ADDRESS;
import static org.zstack.sdnController.ZnsProxyGlobalProperty.PACKAGE_PATH;
import static org.zstack.sdnController.ZnsProxyGlobalProperty.PACKAGE_REMOTE_PATH;
import static org.zstack.sdnController.ZnsProxyGlobalProperty.PACKAGE_REPOSITORY_PATH;
import static org.zstack.sdnController.ZnsProxyGlobalProperty.PROXY_PACKAGE_NAME;
import static org.zstack.sdnController.ZnsProxyGlobalProperty.SERVICE_NAME;

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
        if (!StringUtils.hasText(cmd.hostUuid)) {
            throw new CloudRuntimeException("prepare zns-proxy service failed: hostUuid is empty");
        }

        File localPackage = resolvePackage(cmd);
        installOnHost(cmd.hostUuid.trim(), normalizeProxyListenPort(cmd.proxyListenPort), localPackage);
    }

    private void installOnHost(String hostUuid, int proxyListenPort, File localPackage) {
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

        File configFile = null;
        try {
            configFile = File.createTempFile("zns-proxy-", ".toml");
            FileUtils.writeStringToFile(configFile, renderConfig(proxyListenPort), StandardCharsets.UTF_8);

            String remotePackage = "/tmp/zns-proxy-" + UUID.randomUUID() + ".bin";
            String remoteConfig = "/tmp/zns-proxy-" + UUID.randomUUID() + ".toml";

            Ssh ssh = new Ssh()
                    .setHostname(host.getManagementIp())
                    .setUsername(host.getUsername())
                    .setPassword(host.getPassword())
                    .setPort(host.getPort() == null || host.getPort() <= 0 ? 22 : host.getPort())
                    .setTimeout(10)
                    .setExecTimeout(600);

            SshResult ret = ssh
                    .scpUpload(localPackage.getAbsolutePath(), remotePackage)
                    .scpUpload(configFile.getAbsolutePath(), remoteConfig)
                    .sudoCommand(
                            "systemctl stop " + SERVICE_NAME + " > /dev/null 2>&1 || true",
                            "pkill -x zns-proxy > /dev/null 2>&1 || true",
                            "mkdir -p " + PACKAGE_REMOTE_PATH,
                            "mkdir -p /etc/zstack-zns",
                            "mv " + remotePackage + " " + PACKAGE_PATH,
                            "chmod 0755 " + PACKAGE_PATH,
                            "mv " + remoteConfig + " " + CONFIG_PATH,
                            "chmod 0644 " + CONFIG_PATH,
                            PACKAGE_PATH,
                            "systemctl daemon-reload",
                            "systemctl enable " + SERVICE_NAME,
                            "systemctl restart " + SERVICE_NAME,
                            "curl -fsS " + healthUrl(proxyListenPort)
                    )
                    .runAndClose();
            if (ret == null || ret.getReturnCode() != 0) {
                String stderr = ret == null ? "no ssh result returned" : ret.getStderr();
                throw new CloudRuntimeException(String.format(
                        "prepare zns-proxy service failed on host %s[%s]: %s",
                        hostUuid, host.getManagementIp(), stderr));
            }
        } catch (IOException e) {
            throw new CloudRuntimeException(String.format(
                    "prepare zns-proxy service failed on host %s[%s]: %s",
                    hostUuid, host.getManagementIp(), e.getMessage()), e);
        } finally {
            if (configFile != null && configFile.exists()) {
                // best effort cleanup of the local temp config file
                //noinspection ResultOfMethodCallIgnored
                configFile.delete();
            }
        }
    }

    public static String renderConfig(int proxyListenPort) {
        StringBuilder sb = new StringBuilder();
        sb.append("[server]\n");
        sb.append("address = \"").append(listenAddress(proxyListenPort)).append("\"\n");
        sb.append("readTimeoutSeconds = 30\n");
        sb.append("writeTimeoutSeconds = 30\n");
        sb.append("readHeaderTimeoutSeconds = 5\n");
        sb.append("idleTimeoutSeconds = 120\n");
        sb.append("maxHeaderBytes = 1048576\n");
        return sb.toString();
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
            throw new CloudRuntimeException(String.format(
                    "prepare zns-proxy service failed: package %s not found in %s",
                    packageName, PACKAGE_REPOSITORY_PATH));
        }
        verifySha256(localPackage, cmd.sha256);
        return localPackage;
    }

    private static int normalizeProxyListenPort(int proxyListenPort) {
        return proxyListenPort > 0 ? proxyListenPort : DEFAULT_PROXY_LISTEN_PORT;
    }

    private static String listenAddress(int proxyListenPort) {
        if (proxyListenPort <= 0) {
            return LISTEN_ADDRESS;
        }
        return "0.0.0.0:" + proxyListenPort;
    }

    private static String healthUrl(int proxyListenPort) {
        return "http://127.0.0.1:" + normalizeProxyListenPort(proxyListenPort) + "/zns-proxy/api/v1/health";
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
