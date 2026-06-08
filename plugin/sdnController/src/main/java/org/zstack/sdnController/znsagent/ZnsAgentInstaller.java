package org.zstack.sdnController.znsagent;

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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.zstack.sdnController.ZnsAgentGlobalProperty.AGENT_PACKAGE_NAME;
import static org.zstack.sdnController.ZnsAgentGlobalProperty.CONFIG_PATH;
import static org.zstack.sdnController.ZnsAgentGlobalProperty.LOG_PATH;
import static org.zstack.sdnController.ZnsAgentGlobalProperty.LISTEN_ADDRESS;
import static org.zstack.sdnController.ZnsAgentGlobalProperty.PACKAGE_PATH;
import static org.zstack.sdnController.ZnsAgentGlobalProperty.PACKAGE_REPOSITORY_PATH;
import static org.zstack.sdnController.ZnsAgentGlobalProperty.PACKAGE_REMOTE_PATH;
import static org.zstack.sdnController.ZnsAgentGlobalProperty.RECONNECT_SECONDS;
import static org.zstack.sdnController.ZnsAgentGlobalProperty.SERVICE_NAME;

public class ZnsAgentInstaller {
    private final DatabaseFacade dbf;

    public ZnsAgentInstaller(DatabaseFacade dbf) {
        this.dbf = dbf;
    }

    public void install(ZnsAgentPrepareServiceCmd cmd) {
        if (cmd == null) {
            throw new CloudRuntimeException("prepare zns-agent service failed: command is empty");
        }
        if (cmd.hostUuids == null || cmd.hostUuids.isEmpty()) {
            throw new CloudRuntimeException("prepare zns-agent service failed: hostUuids is empty");
        }

        List<String> controllerAddresses = normalizeControllerAddresses(cmd.controllerAddresses, cmd.controllerIps);
        if (controllerAddresses.isEmpty()) {
            throw new CloudRuntimeException("prepare zns-agent service failed: controllerAddresses is empty");
        }

        File localPackage = resolvePackage(cmd);

        for (String hostUuid : cmd.hostUuids) {
            installOnHost(hostUuid, controllerAddresses, localPackage);
        }
    }

    private void installOnHost(String hostUuid, List<String> controllerAddresses, File localPackage) {
        KVMHostVO host = dbf.findByUuid(hostUuid, KVMHostVO.class);
        if (host == null) {
            throw new CloudRuntimeException(String.format("prepare zns-agent service failed: host %s not found", hostUuid));
        }
        if (StringUtils.isEmpty(host.getManagementIp())) {
            throw new CloudRuntimeException(String.format("prepare zns-agent service failed: host %s has empty management ip", hostUuid));
        }
        if (StringUtils.isEmpty(host.getUsername())) {
            throw new CloudRuntimeException(String.format("prepare zns-agent service failed: host %s has empty username", hostUuid));
        }
        if (StringUtils.isEmpty(host.getPassword())) {
            throw new CloudRuntimeException(String.format("prepare zns-agent service failed: host %s has empty password", hostUuid));
        }

        File configFile = null;
        try {
            configFile = File.createTempFile("zns-agent-", ".toml");
            FileUtils.writeStringToFile(configFile,
                    renderConfig(hostUuid, controllerAddresses),
                    StandardCharsets.UTF_8);

            String remotePackage = "/tmp/zns-agent-" + UUID.randomUUID() + ".bin";
            String remoteConfig = "/tmp/zns-agent-" + UUID.randomUUID() + ".toml";

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
                            "pkill -x zns-agent > /dev/null 2>&1 || true",
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
                            "curl -fsS http://127.0.0.1:8090/zns-agent/api/v1/health"
                    )
                    .runAndClose();
            if (ret == null || ret.getReturnCode() != 0) {
                String stderr = ret == null ? "no ssh result returned" : ret.getStderr();
                throw new CloudRuntimeException(String.format(
                        "prepare zns-agent service failed on host %s[%s]: %s",
                        hostUuid, host.getManagementIp(), stderr));
            }
        } catch (IOException e) {
            throw new CloudRuntimeException(String.format(
                    "prepare zns-agent service failed on host %s[%s]: %s",
                    hostUuid, host.getManagementIp(), e.getMessage()), e);
        } finally {
            if (configFile != null && configFile.exists()) {
                // best effort cleanup of the local temp config file
                //noinspection ResultOfMethodCallIgnored
                configFile.delete();
            }
        }
    }

    public static String renderConfig(String hostUuid, List<String> controllerAddresses) {
        StringBuilder sb = new StringBuilder();
        sb.append("[server]\n");
        sb.append("address = \"").append(LISTEN_ADDRESS).append("\"\n");
        sb.append("readTimeoutSeconds = 30\n");
        sb.append("writeTimeoutSeconds = 30\n");
        sb.append("readHeaderTimeoutSeconds = 5\n");
        sb.append("idleTimeoutSeconds = 120\n");
        sb.append("maxHeaderBytes = 1048576\n\n");
        sb.append("[agent]\n");
        sb.append("hostUuid = \"").append(escapeTomlString(hostUuid)).append("\"\n");
        sb.append("controllers = [\n");
        for (int i = 0; i < controllerAddresses.size(); i++) {
            sb.append("  \"").append(escapeTomlString(controllerAddresses.get(i))).append("\"");
            if (i < controllerAddresses.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append("]\n");
        sb.append("reconnectSeconds = ").append(RECONNECT_SECONDS).append("\n");
        return sb.toString();
    }

    public static File resolvePackage(ZnsAgentPrepareServiceCmd cmd) {
        if (cmd == null) {
            throw new CloudRuntimeException("prepare zns-agent service failed: command is empty");
        }

        if (StringUtils.hasText(cmd.packageUrl)) {
            String packageName = StringUtils.hasText(cmd.packageName) ? cmd.packageName.trim() : basenameFromUrl(cmd.packageUrl);
            File target = packageInRepository(packageName);
            try {
                FileUtils.forceMkdir(target.getParentFile());
                FileUtils.copyURLToFile(new URL(cmd.packageUrl.trim()), target, 10000, 300000);
            } catch (IOException e) {
                throw new CloudRuntimeException(String.format(
                        "prepare zns-agent service failed: download package url %s failed: %s",
                        cmd.packageUrl, e.getMessage()), e);
            }
            verifySha256(target, cmd.sha256);
            return target;
        }

        String packageName;
        if (StringUtils.hasText(cmd.packageName)) {
            packageName = cmd.packageName.trim();
        } else if (StringUtils.hasText(cmd.agentVersion)) {
            packageName = "zns-agent-" + cmd.agentVersion.trim() + ".bin";
        } else {
            packageName = AGENT_PACKAGE_NAME;
        }

        File localPackage = packageInRepository(packageName);
        if (!localPackage.exists() || !localPackage.isFile()) {
            throw new CloudRuntimeException(String.format(
                    "prepare zns-agent service failed: package %s not found in %s",
                    packageName, PACKAGE_REPOSITORY_PATH));
        }
        verifySha256(localPackage, cmd.sha256);
        return localPackage;
    }

    public static List<String> normalizeControllerAddresses(List<String> controllerAddresses, List<String> controllerIps) {
        Set<String> dedup = new LinkedHashSet<>();
        appendControllerAddresses(dedup, controllerAddresses);
        if (dedup.isEmpty()) {
            appendControllerIps(dedup, controllerIps);
        }
        return new ArrayList<>(dedup);
    }

    public static List<String> normalizeControllerIps(List<String> controllerIps) {
        if (controllerIps == null || controllerIps.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> dedup = new LinkedHashSet<>();
        for (String ip : controllerIps) {
            if (!StringUtils.hasText(ip)) {
                continue;
            }
            dedup.add(ip.trim());
        }
        return new ArrayList<>(dedup);
    }

    private static void appendControllerAddresses(Set<String> dedup, List<String> controllerAddresses) {
        if (controllerAddresses == null) {
            return;
        }
        for (String address : controllerAddresses) {
            if (!StringUtils.hasText(address)) {
                continue;
            }
            dedup.add(normalizeControllerAddress(address.trim()));
        }
    }

    private static void appendControllerIps(Set<String> dedup, List<String> controllerIps) {
        if (controllerIps == null) {
            return;
        }
        for (String ip : controllerIps) {
            if (!StringUtils.hasText(ip)) {
                continue;
            }
            String value = ip.trim();
            if (value.contains(":")) {
                dedup.add("http://" + value);
            } else {
                dedup.add("http://" + value + ":7278");
            }
        }
    }

    private static String normalizeControllerAddress(String value) {
        if (value.startsWith("http://") || value.startsWith("https://")) {
            return value;
        }
        if (value.contains(":")) {
            return "http://" + value;
        }
        return "http://" + value + ":7278";
    }

    private static File packageInRepository(String packageName) {
        validatePackageName(packageName);
        return new File(PACKAGE_REPOSITORY_PATH, packageName);
    }

    private static void validatePackageName(String packageName) {
        if (!StringUtils.hasText(packageName)) {
            throw new CloudRuntimeException("prepare zns-agent service failed: packageName is empty");
        }
        String trimmed = packageName.trim();
        if (!trimmed.equals(new File(trimmed).getName()) || trimmed.contains("..")) {
            throw new CloudRuntimeException(String.format(
                    "prepare zns-agent service failed: invalid packageName %s",
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
                    "prepare zns-agent service failed: invalid packageUrl %s",
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
                    "prepare zns-agent service failed: sha256 mismatch for %s, expect=%s actual=%s",
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
                    "prepare zns-agent service failed: calculate sha256 for %s failed: %s",
                    file.getAbsolutePath(), e.getMessage()), e);
        }
    }

    private static String escapeTomlString(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
