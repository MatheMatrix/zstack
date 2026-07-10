package org.zstack.core.ansible;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.zstack.core.Platform;
import org.zstack.header.rest.RESTFacade;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;
import org.zstack.utils.network.IPv6NetworkUtils;
import org.zstack.utils.network.NetworkUtils;
import org.zstack.utils.ssh.Ssh;
import org.zstack.utils.ssh.SshResult;

import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by GuoYi on 2018-12-24.
 */
@Configurable(preConstruction = true, autowire = Autowire.BY_TYPE)
public class SshYumRepoChecker implements AnsibleChecker {
    @Autowired
    protected RESTFacade restf;

    private static final CLogger logger = Utils.getLogger(SshYumRepoChecker.class);
    private static final String ZSTACK_REPO = "/etc/yum.repos.d/zstack-mn.repo";
    private static final String QEMU_REPO = "/etc/yum.repos.d/qemu-kvm-ev-mn.repo";
    private static final String READ_REPO_BASEURLS = String.format(
            "grep -H -E '^[[:space:]]*baseurl[[:space:]]*=' %s %s", ZSTACK_REPO, QEMU_REPO);
    private String username;
    private String password;
    private String privateKey;
    private String targetIp;
    private int sshPort = 22;

    @Override
    public boolean needDeploy() {
        if (StringUtils.isEmpty(password)) {
            return true;
        }

        Ssh ssh = new Ssh();
        ssh.setUsername(username).setPrivateKey(privateKey)
                .setPassword(password).setPort(sshPort)
                .setHostname(targetIp);
        try {
            ssh.sudoCommand(repoReadCommand());
            SshResult ret = ssh.setTimeout(60).runAndClose();
            if (ret.getReturnCode() != 0) {
                logger.warn(String.format("exec ssh command failed, return code: %d, stdout: %s, stderr: %s",
                        ret.getReturnCode(), ret.getStdout(), ret.getStderr()));
                return true;
            }

            String deploymentHost = NetworkUtils.isIpAddress(targetIp) ?
                    Platform.getManagementServerIpForRemote(targetIp) : restf.getHostName();
            if (!repoOutputUsesEndpoint(ret.getStdout(), deploymentHost, restf.getPort())) {
                logger.debug(String.format("yum repo endpoint on target[%s] does not match management node[%s:%s]",
                        targetIp, deploymentHost, restf.getPort()));
                return true;
            }
        } catch (Exception e) {
            logger.warn(String.format("failed to check yum repo endpoint on target[%s]", targetIp), e);
            return true;
        } finally {
            ssh.close();
        }

        logger.debug("yum repo endpoints match the management node on target " + targetIp);
        return false;
    }

    static boolean repoOutputUsesEndpoint(String output, String expectedHost, int expectedPort) {
        if (StringUtils.isBlank(output) || StringUtils.isBlank(expectedHost)) {
            return false;
        }

        Set<String> expectedRepos = new HashSet<>(Arrays.asList(ZSTACK_REPO, QEMU_REPO));
        Set<String> matchedRepos = new HashSet<>();
        for (String line : output.split("\\r?\\n")) {
            int pathSeparator = line.indexOf(':');
            int valueSeparator = line.indexOf('=', pathSeparator + 1);
            if (pathSeparator <= 0 || valueSeparator <= pathSeparator ||
                    !"baseurl".equals(line.substring(pathSeparator + 1, valueSeparator).trim())) {
                return false;
            }

            String repo = line.substring(0, pathSeparator).trim();
            if (!expectedRepos.contains(repo)) {
                return false;
            }

            try {
                URI baseUrl = new URI(line.substring(valueSeparator + 1).trim());
                String actualHost = IPv6NetworkUtils.stripHostUrlBrackets(baseUrl.getHost());
                String normalizedExpectedHost = IPv6NetworkUtils.isIpv6Address(expectedHost) ?
                        IPv6NetworkUtils.normalizeIpv6(expectedHost) : expectedHost;
                String normalizedActualHost = IPv6NetworkUtils.isIpv6Address(actualHost) ?
                        IPv6NetworkUtils.normalizeIpv6(actualHost) : actualHost;
                if (normalizedActualHost == null ||
                        !normalizedExpectedHost.equalsIgnoreCase(normalizedActualHost) ||
                        baseUrl.getPort() != expectedPort) {
                    return false;
                }
            } catch (Exception e) {
                return false;
            }
            matchedRepos.add(repo);
        }

        return matchedRepos.containsAll(expectedRepos);
    }

    static String repoReadCommand() {
        return READ_REPO_BASEURLS;
    }

    @Override
    public void deleteDestFile() {

    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
    }

    public int getSshPort() {
        return sshPort;
    }

    public void setSshPort(int sshPort) {
        this.sshPort = sshPort;
    }

    public String getTargetIp() {
        return targetIp;
    }

    public void setTargetIp(String targetIp) {
        this.targetIp = targetIp;
    }
}
