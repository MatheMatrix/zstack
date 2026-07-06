package org.zstack.kvm;

import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Configurable;
import org.zstack.core.ansible.AnsibleChecker;
import org.zstack.utils.RangeSet;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;
import org.zstack.utils.ssh.Ssh;
import org.zstack.utils.ssh.SshResult;

import java.util.Objects;

@Configurable(preConstruction = true, autowire = Autowire.BY_TYPE)
public class KvmHostConfigChecker implements AnsibleChecker {
    private static final CLogger logger = Utils.getLogger(KvmHostConfigChecker.class);
    private static final String HYGON_QEMU_CONF_CHECK_COMMAND = "grep -Eq '^[[:space:]]*cgroup_device_acl[[:space:]]*=' /etc/libvirt/qemu.conf && " +
            "grep -Fq '\"/dev/vfio/1\"' /etc/libvirt/qemu.conf && " +
            "grep -Fq '\"/dev/vfio/5000\"' /etc/libvirt/qemu.conf && " +
            "grep -Fq '\"/dev/vfio/vfio\"' /etc/libvirt/qemu.conf && " +
            "grep -Fq '\"/dev/hct_share\"' /etc/libvirt/qemu.conf";

    private String username;
    private String password;
    private String privateKey;
    private String targetIp;
    private String requireKsmCheck;
    private String requireReservePorts;
    private String requireHygonQemuConfAclCheck;
    private int sshPort = 22;

    @Override
    public boolean needDeploy() {
        return needDeployKsmCheck() || needDeployReservePorts() || needDeployHygonQemuConf();
    }

    public boolean needDeployHygonQemuConf() {
        if (!"true".equalsIgnoreCase(requireHygonQemuConfAclCheck)) {
            return false;
        }

        SshResult qemuConfRet = runSshCommand(HYGON_QEMU_CONF_CHECK_COMMAND);
        if (qemuConfRet.isSshFailure() || qemuConfRet.getReturnCode() != 0) {
            logger.debug(String.format("Hygon qemu.conf cgroup_device_acl is missing or incomplete on host[%s], need to re-deploy",
                    targetIp));
            return true;
        }

        return false;
    }

    protected SshResult runSshCommand(String command) {
        Ssh ssh = new Ssh();
        ssh.setUsername(username).setPrivateKey(privateKey)
                .setPassword(password).setPort(sshPort)
                .setHostname(targetIp);
        try {
            ssh.sudoCommand(command);
            return ssh.setTimeout(60).runAndClose();
        } finally {
            ssh.close();
        }
    }

    private boolean needDeployKsmCheck() {
        if ("none".equals(requireKsmCheck)) {
            return false;
        }

        Ssh ssh = new Ssh();
        ssh.setUsername(username).setPrivateKey(privateKey)
                .setPassword(password).setPort(sshPort)
                .setHostname(targetIp);
        try {
            ssh.sudoCommand("cat /sys/kernel/mm/ksm/run");
            SshResult ret = ssh.setTimeout(60).runAndClose();
            if (ret.getReturnCode() != 0) {
                logger.warn(String.format("exec ssh command failed, return code: %d, stdout: %s, stderr: %s",
                        ret.getReturnCode(), ret.getStdout(), ret.getStderr()));
                return true;
            }

            boolean ksmEnabledOnHost = "1".equals(ret.getStdout());
            if (ksmEnabledOnHost && "true".equals(requireKsmCheck)) {
                return false;
            }

            if (!ksmEnabledOnHost && "false".equals(requireKsmCheck)) {
                return false;
            }

            logger.debug(String.format("KSM status is %s (%s), but requireKsmCheck is %s, need to re-deploy",
                    ret.getStdout(),
                    ksmEnabledOnHost ? "enabled" : "disabled",
                    requireKsmCheck)
            );

            ssh.reset();
        } finally {
            ssh.close();
        }

        return true;
    }

    private boolean needDeployReservePorts() {
        if (Strings.isEmpty(requireReservePorts)) {
            return false;
        }

        Ssh ssh = new Ssh();
        ssh.setUsername(username).setPrivateKey(privateKey)
                .setPassword(password).setPort(sshPort)
                .setHostname(targetIp);
        try {
            ssh.sudoCommand("cat /proc/sys/net/ipv4/ip_local_reserved_ports");
            SshResult ret = ssh.setTimeout(60).runAndClose();
            if (ret.getReturnCode() != 0) {
                logger.warn(String.format("exec ssh command failed, return code: %d, stdout: %s, stderr: %s",
                        ret.getReturnCode(), ret.getStdout(), ret.getStderr()));
                return true;
            }
            String reservedPorts = ret.getStdout();
            RangeSet cur = RangeSet.valueOf(reservedPorts.trim());
            RangeSet expect = RangeSet.valueOf(requireReservePorts.trim());

            for (RangeSet.Range range : cur.getRanges()) {
                expect.closed(range.getStart(), range.getEnd());
            }

            cur.mergeAndSort();
            expect.mergeAndSort();

            if (!Objects.equals(cur.getRanges(), expect.getRanges())) {
                logger.debug(String.format("Reserved ports are not the same, need to deploy, current: %s, expect: %s",
                        cur.getRanges(), expect.getRanges()));
                return true;
            }
            return false;
        } finally {
            ssh.close();
        }
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

    public String getTargetIp() {
        return targetIp;
    }

    public void setTargetIp(String targetIp) {
        this.targetIp = targetIp;
    }

    public String getRequireKsmCheck() {
        return requireKsmCheck;
    }

    public void setRequireKsmCheck(String requireKsmCheck) {
        this.requireKsmCheck = requireKsmCheck;
    }

    public String getRequireReservePorts() {
        return requireReservePorts;
    }

    public void setRequireReservePorts(String requireReservePorts) {
        this.requireReservePorts = requireReservePorts;
    }

    public String getRequireHygonQemuConfAclCheck() {
        return requireHygonQemuConfAclCheck;
    }

    public void setRequireHygonQemuConfAclCheck(String requireHygonQemuConfAclCheck) {
        this.requireHygonQemuConfAclCheck = requireHygonQemuConfAclCheck;
    }

    public int getSshPort() {
        return sshPort;
    }

    public void setSshPort(int sshPort) {
        this.sshPort = sshPort;
    }
}
