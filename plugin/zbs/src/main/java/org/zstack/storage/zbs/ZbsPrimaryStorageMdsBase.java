package org.zstack.storage.zbs;

import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.core.Platform;
import org.zstack.core.ansible.*;
import org.zstack.core.cloudbus.CloudBusGlobalProperty;
import org.zstack.core.thread.ChainTask;
import org.zstack.core.thread.SyncTaskChain;
import org.zstack.core.thread.ThreadFacade;
import org.zstack.core.workflow.FlowChainBuilder;
import org.zstack.core.workflow.ShareFlow;
import org.zstack.header.core.Completion;
import org.zstack.header.core.ReturnValueCompletion;
import org.zstack.header.core.workflow.*;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.storage.addon.primary.ExternalPrimaryStorageVO;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;
import org.zstack.utils.path.PathUtil;

import java.util.Map;

/**
 * @author Xingwei Yu
 * @date 2024/4/2 11:48
 */
public class ZbsPrimaryStorageMdsBase extends ZbsMdsBase {
    private static final CLogger logger = Utils.getLogger(ZbsPrimaryStorageMdsBase.class);

    @Autowired
    private ThreadFacade thdf;

    private ExternalPrimaryStorageVO zbs;
    private String syncId;
    private ZbsAddonInfo.Mds mds;

    public static final String ECHO_PATH = "/zbs/primarystorage/echo";

    private void doConnect(final Completion completion) {
        final FlowChain chain = FlowChainBuilder.newShareFlowChain();
        chain.setName(String.format("connect-mds-%s-zbs-primary-storage-%s", mds.getHostname(), zbs.getUuid()));
        chain.allowEmptyFlow();
        chain.then(new ShareFlow() {
            @Override
            public void setup() {
                flow(new NoRollbackFlow() {
                    String __name__ = "check-tools";

                    @Override
                    public void run(FlowTrigger trigger, Map data) {
                        checkTools();
                        checkHealth();
                        trigger.next();
                    }
                });

                flow(new NoRollbackFlow() {
                    String __name__ = "deploy-agent";

                    @Override
                    public void run(FlowTrigger trigger, Map data) {
                        SshFileMd5Checker checker = new SshFileMd5Checker();
                        checker.setTargetIp(mds.getHostname());
                        checker.setUsername(mds.getSshUsername());
                        checker.setPassword(mds.getSshPassword());
                        checker.addSrcDestPair(SshFileMd5Checker.ZSTACKLIB_SRC_PATH, String.format("/var/lib/zstack/zbsp/package/%s", AnsibleGlobalProperty.ZSTACKLIB_PACKAGE_NAME));
                        checker.addSrcDestPair(PathUtil.findFileOnClassPath(String.format("ansible/zbsp/$s", ZbsGlobalProperty.PRIMARY_STORAGE_PACKAGE_NAME), true).getAbsolutePath(),
                                String.format("/var/lib/zstack/zbsp/package/%s", ZbsGlobalProperty.PRIMARY_STORAGE_PACKAGE_NAME));

                        SshChronyConfigChecker chronyChecker = new SshChronyConfigChecker();
                        chronyChecker.setTargetIp(mds.getHostname());
                        chronyChecker.setUsername(mds.getSshUsername());
                        chronyChecker.setPassword(mds.getSshPassword());
                        chronyChecker.setSshPort(mds.getSshPort());

                        SshYumRepoChecker repoChecker = new SshYumRepoChecker();
                        repoChecker.setTargetIp(mds.getHostname());
                        repoChecker.setUsername(mds.getSshUsername());
                        repoChecker.setPassword(mds.getSshPassword());
                        repoChecker.setSshPort(mds.getSshPort());

                        CallBackNetworkChecker callBackChecker = new CallBackNetworkChecker();
                        callBackChecker.setTargetIp(mds.getHostname());
                        callBackChecker.setUsername(mds.getSshUsername());
                        callBackChecker.setPassword(mds.getSshPassword());
                        callBackChecker.setPort(mds.getSshPort());
                        callBackChecker.setCallbackIp(Platform.getManagementServerIp());
                        callBackChecker.setCallBackPort(CloudBusGlobalProperty.HTTP_PORT);

                        AnsibleRunner runner = new AnsibleRunner();
                        runner.installChecker(checker);
                        runner.installChecker(chronyChecker);
                        runner.installChecker(repoChecker);
                        runner.installChecker(callBackChecker);
                        runner.setUsername(mds.getSshUsername());
                        runner.setPassword(mds.getSshPassword());
                        runner.setTargetIp(mds.getHostname());
                        runner.setTargetUuid(mds.getHostname());
                        runner.setAgentPort(ZbsGlobalProperty.PRIMARY_STORAGE_AGENT_PORT);
                        runner.setPlayBookName(ZbsGlobalProperty.PRIMARY_STORAGE_PLAYBOOK_NAME);

                        ZbsPrimaryStorageDeployArguments deployArguments = new ZbsPrimaryStorageDeployArguments();
                        runner.setDeployArguments(deployArguments);
                        runner.run(new ReturnValueCompletion<Boolean>(trigger) {
                            @Override
                            public void success(Boolean returnValue) {
                                trigger.next();
                            }

                            @Override
                            public void fail(ErrorCode errorCode) {
                                trigger.fail(errorCode);
                            }
                        });

                    }
                });

                flow(new NoRollbackFlow() {
                    String __name__ = "echo-agent";

                    @Override
                    public void run(FlowTrigger trigger, Map data) {
                        restf.echo(ZbsAgentUrl.primaryStorageUrl(mds.getHostname(), ECHO_PATH), new Completion(trigger) {
                            @Override
                            public void success() {
                                trigger.next();
                            }

                            @Override
                            public void fail(ErrorCode errorCode) {
                                trigger.fail(errorCode);
                            }
                        });
                    }
                });

                done(new FlowDoneHandler(completion) {
                    @Override
                    public void handle(Map data) {
                        completion.success();
                    }
                });

                error(new FlowErrorHandler(completion) {
                    @Override
                    public void handle(ErrorCode errCode, Map data) {
                        completion.fail(errCode);
                    }
                });
            }
        }).start();
    }

    @Override
    public void connect(Completion completion) {
        thdf.chainSubmit(new ChainTask(completion) {
            @Override
            public void run(SyncTaskChain chain) {
                doConnect(new Completion(completion, chain) {
                    @Override
                    public void success() {
                        completion.success();
                        chain.next();
                    }

                    @Override
                    public void fail(ErrorCode errorCode) {
                        completion.fail(errorCode);
                        chain.next();
                    }
                });
            }

            @Override
            public String getSyncSignature() {
                return syncId;
            }

            @Override
            public String getName() {
                return String.format("connect-zbs-primary-storage-mds-%s", mds.mdsAddr);
            }
        });
    }

    public ZbsPrimaryStorageMdsBase(ZbsAddonInfo.Mds mds) {
        this.mds = mds;
        this.syncId = String.format("sync-%s", mds.mdsAddr);
    }
}
