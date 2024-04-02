package org.zstack.storage.zbs;

import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.header.core.Completion;
import org.zstack.header.errorcode.OperationFailureException;
import org.zstack.header.rest.RESTFacade;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;
import org.zstack.utils.ssh.Ssh;
import org.zstack.utils.ssh.SshException;
import org.zstack.utils.ssh.SshResult;

import static org.zstack.core.Platform.operr;

/**
 * @author Xingwei Yu
 * @date 2024/4/2 11:40
 */
@Configurable(preConstruction = true, autowire = Autowire.BY_TYPE, dependencyCheck = true)
public abstract class ZbsMdsBase {
    private static final CLogger logger = Utils.getLogger(ZbsMdsBase.class);

    @Autowired
    private DatabaseFacade dbf;
    @Autowired
    protected RESTFacade restf;

    protected ZbsAddonInfo.Mds mds;

    public abstract void connect(Completion completion);

    protected void checkTools() {
        Ssh ssh = new Ssh();
        try {
            ssh.setHostname(mds.getHostname()).setUsername(mds.getSshUsername()).setPassword(mds.getSshPassword()).setPort(mds.getSshPort())
                    .checkTool("curveadm", "zbs").setTimeout(60).runErrorByExceptionAndClose();
        } catch (SshException e) {
            throw new OperationFailureException(operr("The problem may be caused by an incorrect user name or password or SSH port or unstable network environment"));
        }
    }

    protected void checkHealth() {
        Ssh ssh = new Ssh();
        SshResult ret = null;
        try {
            ret = ssh.setHostname(mds.getHostname()).setUsername(mds.getSshUsername()).setPassword(mds.getSshPassword()).setPort(mds.getSshPort())
                    .shell("zbs status cluster | grep \"Cluster health is:\" | awk -F ': ' '{print $2}'").setTimeout(60).runAndClose();
        } catch (SshException e) {
            throw new OperationFailureException(operr("The problem may be caused by an incorrect user name or password or SSH port or unstable network environment"));
        }

        if (ret.getReturnCode() != 0) {
            ret.setSshFailure(true);
            throw new SshException(ret.getStderr());
        }

        String stdout = ret.getStdout();
        if (stdout.contains("error")) {
            throw new SshException(stdout);
        }
    }
}
