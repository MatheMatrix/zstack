package org.zstack.server;

import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.core.CoreGlobalProperty;
import org.zstack.core.thread.Task;
import org.zstack.core.thread.ThreadFacade;
import org.zstack.header.core.Completion;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.server.PhysicalServerVO;
import org.zstack.utils.DebugUtils;
import org.zstack.utils.ShellUtils;
import org.zstack.utils.path.PathUtil;
import org.zstack.utils.ssh.SshCmdHelper;

import static org.zstack.core.Platform.operr;

public class PhysicalServerIpmiPowerExecutor {

    @Autowired
    private ThreadFacade thdf;

    public void powerOn(PhysicalServerVO server, Completion completion) {
        runAsync(server, "power-on", IPMIToolCaller::powerOn, completion);
    }

    public void powerOff(PhysicalServerVO server, Completion completion) {
        runAsync(server, "power-off", IPMIToolCaller::powerOff, completion);
    }

    public void powerReset(PhysicalServerVO server, Completion completion) {
        runAsync(server, "power-reset", IPMIToolCaller::powerReset, completion);
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

        // ipmitool blocks on remote BMC; submit to ThreadFacade so the dispatcher thread
        // (or an upstream callback thread) is never held during the IPMI round-trip.
        final String uuid = server.getUuid();
        final String oobAddress = server.getOobAddress();
        final IPMIToolCaller caller = IPMIToolCaller.fromPhysicalServer(server);
        thdf.submit(new Task<Void>() {
            @Override
            public String getName() {
                return String.format("ipmi-power-on-pxe-%s", uuid);
            }

            @Override
            public Void call() {
                int ret = caller.setBootPxe();
                if (ret != 0) {
                    completion.fail(operr("failed to set PXE bootdev for PhysicalServer[uuid:%s, oobAddress:%s]",
                            uuid, oobAddress));
                    return null;
                }
                ret = caller.powerReset();
                if (ret != 0) {
                    completion.fail(operr("failed to power-reset for PXE boot for PhysicalServer[uuid:%s, oobAddress:%s]",
                            uuid, oobAddress));
                    return null;
                }
                completion.success();
                return null;
            }
        });
    }

    private void runAsync(PhysicalServerVO server, String op, IpmiAction action, Completion completion) {
        ErrorCode validationErr = validate(server);
        if (validationErr != null) {
            completion.fail(validationErr);
            return;
        }

        if (CoreGlobalProperty.UNIT_TEST_ON) {
            completion.success();
            return;
        }

        // Move blocking ipmitool exec off the calling thread.
        final String uuid = server.getUuid();
        final String oobAddress = server.getOobAddress();
        final IPMIToolCaller caller = IPMIToolCaller.fromPhysicalServer(server);
        thdf.submit(new Task<Void>() {
            @Override
            public String getName() {
                return String.format("ipmi-%s-%s", op, uuid);
            }

            @Override
            public Void call() {
                int ret = action.run(caller);
                if (ret == 0) {
                    completion.success();
                    return null;
                }
                completion.fail(operr("IPMI %s failed for PhysicalServer[uuid:%s, oobAddress:%s]",
                        op, uuid, oobAddress));
                return null;
            }
        });
    }

    public boolean hasOobCredentials(PhysicalServerVO server) {
        return server != null
                && notEmpty(server.getOobAddress())
                && notEmpty(server.getOobUsername())
                && notEmpty(server.getOobPassword());
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
