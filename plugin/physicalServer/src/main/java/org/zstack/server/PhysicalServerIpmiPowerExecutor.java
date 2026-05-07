package org.zstack.server;

import org.zstack.core.CoreGlobalProperty;
import org.zstack.header.core.Completion;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.server.PhysicalServerVO;
import org.zstack.utils.DebugUtils;
import org.zstack.utils.ShellUtils;
import org.zstack.utils.path.PathUtil;
import org.zstack.utils.ssh.SshCmdHelper;

import static org.zstack.core.Platform.operr;

public class PhysicalServerIpmiPowerExecutor {
    public void powerOn(PhysicalServerVO server, Completion completion) {
        complete(power(server, "power-on", IPMIToolCaller::powerOn), completion);
    }

    public void powerOff(PhysicalServerVO server, Completion completion) {
        complete(power(server, "power-off", IPMIToolCaller::powerOff), completion);
    }

    public void powerReset(PhysicalServerVO server, Completion completion) {
        complete(power(server, "power-reset", IPMIToolCaller::powerReset), completion);
    }

    public void powerOnPxe(PhysicalServerVO server, Completion completion) {
        ErrorCode err = validate(server);
        if (err != null) {
            completion.fail(err);
            return;
        }

        if (CoreGlobalProperty.UNIT_TEST_ON) {
            completion.success();
            return;
        }

        IPMIToolCaller caller = IPMIToolCaller.fromPhysicalServer(server);
        int ret = caller.setBootPxe();
        if (ret != 0) {
            completion.fail(operr("failed to set PXE bootdev for PhysicalServer[uuid:%s, oobAddress:%s]",
                    server.getUuid(), server.getOobAddress()));
            return;
        }

        ret = caller.powerReset();
        if (ret != 0) {
            completion.fail(operr("failed to power-reset for PXE boot for PhysicalServer[uuid:%s, oobAddress:%s]",
                    server.getUuid(), server.getOobAddress()));
            return;
        }

        completion.success();
    }

    public boolean hasOobCredentials(PhysicalServerVO server) {
        return server != null
                && notEmpty(server.getOobAddress())
                && notEmpty(server.getOobUsername())
                && notEmpty(server.getOobPassword());
    }

    private ErrorCode power(PhysicalServerVO server, String op, IpmiAction action) {
        ErrorCode err = validate(server);
        if (err != null) {
            return err;
        }

        if (CoreGlobalProperty.UNIT_TEST_ON) {
            return null;
        }

        int ret = action.run(IPMIToolCaller.fromPhysicalServer(server));
        if (ret == 0) {
            return null;
        }

        return operr("IPMI %s failed for PhysicalServer[uuid:%s, oobAddress:%s]",
                op, server.getUuid(), server.getOobAddress());
    }

    private ErrorCode validate(PhysicalServerVO server) {
        if (!hasOobCredentials(server)) {
            return operr("OOB credentials not configured for PhysicalServer[uuid:%s]", server == null ? null : server.getUuid());
        }
        if (server.getOobManagementType() != null && !"IPMI".equals(server.getOobManagementType())) {
            return operr("unsupported OOB management type[%s] for PhysicalServer[uuid:%s]",
                    server.getOobManagementType(), server.getUuid());
        }
        return null;
    }

    private void complete(ErrorCode err, Completion completion) {
        if (err == null) {
            completion.success();
        } else {
            completion.fail(err);
        }
    }

    private boolean notEmpty(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private interface IpmiAction {
        int run(IPMIToolCaller caller);
    }

    private static class IPMIToolCaller {
        private final String interfaceToUse = "lanplus";
        private String hostname;
        private int port;
        private String username;
        private String password;

        private static IPMIToolCaller fromPhysicalServer(PhysicalServerVO server) {
            IPMIToolCaller caller = new IPMIToolCaller();
            caller.hostname = server.getOobAddress();
            caller.port = server.getOobPort() == null ? 623 : server.getOobPort();
            caller.username = server.getOobUsername();
            caller.password = server.getOobPassword();
            return caller;
        }

        private int powerOn() {
            return runWithReturnCode("chassis power on");
        }

        private int powerOff() {
            return runWithReturnCode("chassis power off");
        }

        private int powerReset() {
            return runWithReturnCode("chassis power reset");
        }

        private int setBootPxe() {
            return runWithReturnCode("chassis bootdev pxe options=efiboot");
        }

        private int runWithReturnCode(String command) {
            DebugUtils.Assert(command != null, "command should be set before execution");
            String passFile = PathUtil.createTempFileWithContent(password);
            try {
                String base = String.format("ipmitool -I %s -H %s -p %d -U %s -f %s",
                        interfaceToUse,
                        SshCmdHelper.shellQuote(hostname),
                        port,
                        SshCmdHelper.shellQuote(username),
                        SshCmdHelper.shellQuote(passFile));
                return ShellUtils.runAndReturn(String.format("%s %s", base, command)).getRetCode();
            } finally {
                PathUtil.forceRemoveFile(passFile);
            }
        }
    }
}
