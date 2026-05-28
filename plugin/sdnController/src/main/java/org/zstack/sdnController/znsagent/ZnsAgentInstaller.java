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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.zstack.sdnController.ZnsAgentGlobalProperty.CONFIG_PATH;
import static org.zstack.sdnController.ZnsAgentGlobalProperty.LOG_PATH;
import static org.zstack.sdnController.ZnsAgentGlobalProperty.LISTEN_ADDRESS;
import static org.zstack.sdnController.ZnsAgentGlobalProperty.PACKAGE_PATH;
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

        List<String> controllerIps = normalizeControllerIps(cmd.controllerIps);
        if (controllerIps.isEmpty()) {
            throw new CloudRuntimeException("prepare zns-agent service failed: controllerIps is empty");
        }

        for (String hostUuid : cmd.hostUuids) {
            installOnHost(hostUuid, controllerIps);
        }
    }

    private void installOnHost(String hostUuid, List<String> controllerIps) {
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
                    renderConfig(hostUuid, controllerIps),
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
                    .scpUpload(PACKAGE_PATH, remotePackage)
                    .scpUpload(configFile.getAbsolutePath(), remoteConfig)
                    .sudoCommand(
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

    public static String renderConfig(String hostUuid, List<String> controllerIps) {
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
        for (int i = 0; i < controllerIps.size(); i++) {
            sb.append("  \"http://").append(controllerIps.get(i)).append(":7278\"");
            if (i < controllerIps.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append("]\n");
        sb.append("reconnectSeconds = ").append(RECONNECT_SECONDS).append("\n");
        return sb.toString();
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

    private static String escapeTomlString(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
