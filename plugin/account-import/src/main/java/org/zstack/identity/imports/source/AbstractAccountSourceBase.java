package org.zstack.identity.imports.source;

import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.zstack.core.cloudbus.CloudBus;
import org.zstack.core.Platform;
import org.zstack.core.asyncbatch.While;
import org.zstack.core.cloudbus.CloudBusCallBack;
import org.zstack.core.cloudbus.MessageSafe;
import org.zstack.core.componentloader.PluginRegistry;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.core.db.SQL;
import org.zstack.core.thread.ChainTask;
import org.zstack.core.thread.SyncTaskChain;
import org.zstack.core.thread.ThreadFacade;
import org.zstack.header.core.Completion;
import org.zstack.header.core.ReturnValueCompletion;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.message.Message;
import org.zstack.identity.imports.UnbindingUserSourceExtensionPoint;
import org.zstack.identity.imports.entity.ImportAccountResult;
import org.zstack.identity.imports.entity.ThirdPartyAccountSourceVO;
import org.zstack.identity.imports.entity.ImportAccountSpec;
import org.zstack.identity.imports.entity.SyncCreatedAccountStrategy;
import org.zstack.identity.imports.entity.SyncDeletedAccountStrategy;
import org.zstack.identity.imports.entity.AccountSourceRefVO;
import org.zstack.identity.imports.message.BindThirdPartyAccountMsg;
import org.zstack.identity.imports.message.ImportThirdPartyAccountMsg;
import org.zstack.identity.imports.message.ImportThirdPartyUserReply;
import org.zstack.identity.imports.entity.AccountSourceRefVO_;
import org.zstack.identity.imports.message.*;
import org.zstack.resourceconfig.ResourceConfigFacade;
import org.zstack.utils.CollectionUtils;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import java.util.List;
import org.zstack.core.workflow.FlowChainBuilder;
import org.zstack.header.core.WhileDoneCompletion;
import org.zstack.header.core.workflow.Flow;
import org.zstack.header.core.workflow.FlowChain;
import org.zstack.header.core.workflow.FlowDoneHandler;
import org.zstack.header.core.workflow.FlowErrorHandler;
import org.zstack.header.core.workflow.FlowRollback;
import org.zstack.header.core.workflow.FlowTrigger;
import org.zstack.header.core.workflow.NoRollbackFlow;
import org.zstack.header.errorcode.ErrorCodeList;
import org.zstack.header.identity.AccountConstant;
import org.zstack.header.identity.AccountInventory;
import org.zstack.header.identity.AccountVO;
import org.zstack.header.identity.CreateAccountMsg;
import org.zstack.header.identity.CreateAccountReply;
import org.zstack.header.identity.DeleteAccountMsg;
import org.zstack.header.message.MessageReply;
import org.zstack.identity.imports.AccountImportExtensionPoint;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;

import static org.zstack.core.Platform.operr;
import static org.zstack.identity.imports.AccountImportsManager.userSourceQueueSyncSignature;
import static org.zstack.utils.CollectionUtils.*;
import static org.zstack.utils.CollectionUtils.transform;

/**
 * Created by Wenhao.Zhang on 2024/05/31
 */
@Configurable(preConstruction = true, autowire = Autowire.BY_TYPE)
public abstract class AbstractAccountSourceBase {
    private static final CLogger logger = Utils.getLogger(AbstractAccountSourceBase.class);

    @Autowired
    private CloudBus bus;
    @Autowired
    private DatabaseFacade databaseFacade;
    @Autowired
    private PluginRegistry pluginRegistry;
    @Autowired
    private ThreadFacade threadFacade;
    @Autowired
    private ResourceConfigFacade resourceConfigs;

    protected AbstractAccountSourceBase(ThirdPartyAccountSourceVO self) {
        this.self = Objects.requireNonNull(self);
    }

    protected ThirdPartyAccountSourceVO self;
    public abstract String type();

    @MessageSafe
    public void handleMessage(Message msg) {
        if (msg instanceof ImportThirdPartyAccountMsg) {
            handle(((ImportThirdPartyAccountMsg) msg));
        } else if (msg instanceof BindThirdPartyAccountMsg) {
            handle(((BindThirdPartyAccountMsg) msg));
        } else if (msg instanceof UnbindThirdPartyAccountMsg) {
            handle(((UnbindThirdPartyAccountMsg) msg));
        } else if (msg instanceof SyncThirdPartyAccountMsg) {
            handle(((SyncThirdPartyAccountMsg) msg));
        } else if (msg instanceof DestroyThirdPartyAccountSourceMsg) {
            handle(((DestroyThirdPartyAccountSourceMsg) msg));
        } else {
            bus.dealWithUnknownMessage(msg);
        }
    }

    public void handle(ImportThirdPartyAccountMsg message) {
        ImportThirdPartyUserReply reply = new ImportThirdPartyUserReply();
        final String sourceUuid = message.getSourceUuid();

        threadFacade.chainSubmit(new ChainTask(message) {
            @Override
            public void run(SyncTaskChain chain) {
                importAccounts(message.getSpec(), new ReturnValueCompletion<List<ImportAccountResult>>(chain) {
                    @Override
                    public void success(List<ImportAccountResult> results) {
                        chain.next();
                        reply.setResults(results);
                        bus.reply(message, reply);
                    }

                    @Override
                    public void fail(ErrorCode errorCode) {
                        chain.next();
                        reply.setError(errorCode);
                        bus.reply(message, reply);
                    }
                });
            }

            @Override
            public String getSyncSignature() {
                return userSourceQueueSyncSignature(sourceUuid);
            }

            @Override
            public String getName() {
                return "import-accounts-from-source-" + sourceUuid;
            }
        });
    }

    @SuppressWarnings("rawtypes")
    public final void importAccounts(ImportAccountSpec batch, ReturnValueCompletion<List<ImportAccountResult>> completion) {
        final List<AccountImportExtensionPoint> extensions = pluginRegistry.getExtensionList(AccountImportExtensionPoint.class);
        List<AccountImportContext> contexts = new ArrayList<>(batch.getAccountList().size());

        FlowChain chain = FlowChainBuilder.newSimpleFlowChain();
        chain.setName(String.format("chain-with-importing-accounts-from-source-%s", batch.getSourceUuid()));
        chain.then(new NoRollbackFlow() {
            String __name__ = "pre-accounts-importing";
            @Override
            public void run(FlowTrigger trigger, Map data) {
                for (AccountImportExtensionPoint extension : extensions) {
                    final ErrorCode errorCode = extension.preAccountsImporting(batch);
                    if (errorCode != null) {
                        trigger.fail(errorCode);
                        return;
                    }
                }
                trigger.next();
            }
        }).then(new Flow() {
            String __name__ = "generate-accounts";
            @Override
            public void run(FlowTrigger trigger, Map data) {
                new While<>(batch.getAccountList()).each((spec, whileCompletion) -> {
                    final AccountImportContext context = new AccountImportContext().withSpec(spec);
                    contexts.add(context);

                    if (!spec.isCreateIfNotExist() && spec.getAccountUuid() == null) {
                        context.errorForCreatingAccount = operr("invalid account spec: accountUuid is null");
                        whileCompletion.done();
                        return;
                    }

                    String accountUuid = spec.getAccountUuid() == null ? Platform.getUuid() : spec.getAccountUuid();

                    AccountSourceRefVO ref = new AccountSourceRefVO();
                    ref.setUuid(Platform.getUuid());
                    ref.setCredentials(spec.getCredentials());
                    ref.setAccountSourceUuid(batch.getSourceUuid());
                    ref.setAccountUuid(accountUuid);
                    context.withRef(ref);

                    if (!spec.isCreateIfNotExist()) {
                        final AccountVO account = databaseFacade.findByUuid(spec.getAccountUuid(), AccountVO.class);
                        if (account == null) {
                            context.errorForCreatingAccount = operr("invalid account spec: failed to find account[uuid=%s]",
                                    spec.getAccountUuid());
                        } else {
                            context.withAccount(AccountInventory.valueOf(account));
                        }
                        whileCompletion.done();
                        return;
                    }

                    CreateAccountMsg message = new CreateAccountMsg();
                    message.setUuid(accountUuid);
                    message.setName(spec.getUsername());
                    message.setPassword(spec.getPassword());
                    message.setType(spec.getAccountType().toString());
                    bus.makeLocalServiceId(message, AccountConstant.SERVICE_ID);
                    bus.send(message, new CloudBusCallBack(whileCompletion) {
                        @Override
                        public void run(MessageReply reply) {
                            if (reply.isSuccess()) {
                                context.withAccount(((CreateAccountReply) reply).getInventory());
                            } else {
                                context.errorForCreatingAccount = reply.getError();
                            }
                            whileCompletion.done();
                        }
                    });
                }).run(new WhileDoneCompletion(trigger) {
                    @Override
                    public void done(ErrorCodeList errorCodeList) {
                        trigger.next();
                    }
                });
            }

            @Override
            public void rollback(FlowRollback trigger, Map data) {
                new While<>(contexts).each((chunk, whileCompletion) -> {
                    if (chunk.errorForCreatingAccount != null) {
                        whileCompletion.done();
                        return;
                    }

                    DeleteAccountMsg message = new DeleteAccountMsg();
                    message.setUuid(chunk.account.getUuid());
                    bus.makeLocalServiceId(message, AccountConstant.SERVICE_ID);
                    bus.send(message, new CloudBusCallBack(whileCompletion) {
                        @Override
                        public void run(MessageReply reply) {
                            if (!reply.isSuccess()) {
                                logger.warn(String.format("destroy account[uuid=%s, name=%s] failed",
                                        chunk.account.getUuid(), chunk.account.getName()));
                            }
                            whileCompletion.done();
                        }
                    });
                }).run(new WhileDoneCompletion(trigger) {
                    @Override
                    public void done(ErrorCodeList errorCodeList) {
                        trigger.rollback();
                    }
                });
            }
        }).then(new NoRollbackFlow() {
            String __name__ = "bind-accounts-with-import-source";
            @Override
            public void run(FlowTrigger trigger, Map data) {
                List<AccountImportContext> results = filter(contexts, chunk -> chunk.errorForCreatingAccount == null);

                if (results.isEmpty()) {
                    trigger.next();
                    return;
                }

                databaseFacade.persistCollection(transform(results, result -> result.ref));
                trigger.next();
            }
        }).then(new NoRollbackFlow() {
            String __name__ = "after-accounts-importing";
            @Override
            public void run(FlowTrigger trigger, Map data) {
                safeForEach(extensions, extension -> extension.afterAccountsImporting(contexts));
                trigger.next();
            }
        }).done(new FlowDoneHandler(completion) {
            @Override
            public void handle(Map data) {
                completion.success(transform(contexts, AccountImportContext::makeResult));
            }
        }).error(new FlowErrorHandler(completion) {
            @Override
            public void handle(ErrorCode errCode, Map data) {
                completion.fail(operr(errCode, "failed to import account from source[uuid=%s, type=%s]",
                        batch.getSourceUuid(), batch.getSourceType()));
            }
        }).start();
    }

    private void handle(BindThirdPartyAccountMsg message) {
        BindThirdPartyAccountReply reply = new BindThirdPartyAccountReply();
        final String sourceUuid = message.getSourceUuid();

        ImportAccountSpec batch = new ImportAccountSpec();
        batch.setSourceUuid(sourceUuid);
        batch.setSourceType(this.type());

        ImportAccountSpec.AccountSpec spec = new ImportAccountSpec.AccountSpec();
        spec.setAccountUuid(message.getAccountUuid());
        spec.setCreateIfNotExist(false);
        spec.setCredentials(Objects.requireNonNull(message.getCredentials()));
        batch.getAccountList().add(spec);

        threadFacade.chainSubmit(new ChainTask(message) {
            @Override
            public void run(SyncTaskChain chain) {
                importAccounts(batch, new ReturnValueCompletion<List<ImportAccountResult>>(chain) {
                    @Override
                    public void success(List<ImportAccountResult> results) {
                        chain.next();
                        reply.setResults(results);
                        bus.reply(message, reply);
                    }

                    @Override
                    public void fail(ErrorCode errorCode) {
                        chain.next();
                        reply.setError(errorCode);
                        bus.reply(message, reply);
                    }
                });
            }

            @Override
            public String getSyncSignature() {
                return userSourceQueueSyncSignature(sourceUuid);
            }

            @Override
            public String getName() {
                return "bind-account-from-source-" + sourceUuid;
            }
        });
    }

    private void handle(UnbindThirdPartyAccountMsg message) {
        UnbindThirdPartyAccountReply reply = new UnbindThirdPartyAccountReply();
        threadFacade.chainSubmit(new ChainTask(message) {
            @Override
            public void run(SyncTaskChain chain) {
                unbindingAccount(message.getAccountUuid(), new Completion(chain) {
                    @Override
                    public void success() {
                        chain.next();
                        bus.reply(message, reply);
                    }

                    @Override
                    public void fail(ErrorCode errorCode) {
                        chain.next();
                        reply.setError(errorCode);
                        bus.reply(message, reply);
                    }
                });
            }

            @Override
            public String getSyncSignature() {
                return userSourceQueueSyncSignature(self.getUuid());
            }

            @Override
            public String getName() {
                return "unbinding-account-from-source-" + self.getUuid();
            }
        });
    }

    @SuppressWarnings("rawtypes")
    private void unbindingAccount(String accountUuid, Completion completion) {
        List<UnbindingUserSourceExtensionPoint> extensions = pluginRegistry.getExtensionList(UnbindingUserSourceExtensionPoint.class);

        FlowChain chain = FlowChainBuilder.newSimpleFlowChain();
        chain.setName(String.format("chain-with-unbinding-account-%s", accountUuid));
        chain.then(new NoRollbackFlow() {
            String __name__ = "pre-accounts-unbinding";
            @Override
            public void run(FlowTrigger trigger, Map data) {
                for (UnbindingUserSourceExtensionPoint extension : extensions) {
                    final ErrorCode errorCode = extension.preUnbindingUserSource(self.getUuid(), accountUuid);
                    if (errorCode != null) {
                        trigger.fail(errorCode);
                        return;
                    }
                }
                trigger.next();
            }
        }).then(new NoRollbackFlow() {
            String __name__ = "unbinding-user";
            @Override
            public void run(FlowTrigger trigger, Map data) {
                SQL.New(AccountSourceRefVO.class)
                        .eq(AccountSourceRefVO_.accountUuid, accountUuid)
                        .eq(AccountSourceRefVO_.accountSourceUuid, self.getUuid())
                        .delete();
                trigger.next();
            }
        }).then(new NoRollbackFlow() {
            String __name__ = "after-unbinding-user";
            @Override
            public void run(FlowTrigger trigger, Map data) {
                CollectionUtils.safeForEach(extensions,
                        extension -> extension.afterUnbindingUserSource(self.getUuid(), accountUuid));
                trigger.next();
            }
        }).done(new FlowDoneHandler(completion) {
            @Override
            public void handle(Map data) {
                completion.success();
            }
        }).error(new FlowErrorHandler(completion) {
            @Override
            public void handle(ErrorCode errCode, Map data) {
                completion.fail(operr(errCode, "failed to unbinding account[uuid=%s] from source[uuid=%s, type=%s]",
                        accountUuid, self.getUuid(), self.getType()));
            }
        }).start();
    }

    private void handle(SyncThirdPartyAccountMsg message) {
        SyncThirdPartyAccountReply reply = new SyncThirdPartyAccountReply();

        SyncCreatedAccountStrategy createStrategy = message.getCreateAccountStrategy() == null ?
                self.getCreateAccountStrategy() : message.getCreateAccountStrategy();
        SyncDeletedAccountStrategy deleteStrategy = message.getDeleteAccountStrategy() == null ?
                self.getDeleteAccountStrategy() : message.getDeleteAccountStrategy();

        ImportAccountSpec.SyncTaskSpec spec = new ImportAccountSpec.SyncTaskSpec()
                .withAccountSource(self.getUuid(), self.getType())
                .withCreateAccountStrategy(createStrategy)
                .withDeleteAccountStrategy(deleteStrategy);

        threadFacade.chainSubmit(new ChainTask(message) {
            @Override
            public void run(SyncTaskChain chain) {
                syncAccountsFromSource(spec, new Completion(chain) {
                    @Override
                    public void success() {
                        chain.next();
                        bus.reply(message, reply);
                    }

                    @Override
                    public void fail(ErrorCode errorCode) {
                        chain.next();
                        reply.setError(errorCode);
                        bus.reply(message, reply);
                    }
                });
            }

            @Override
            public String getSyncSignature() {
                return userSourceQueueSyncSignature(self.getUuid());
            }

            @Override
            public String getName() {
                return "sync-accounts-from-source-" + self.getUuid();
            }
        });
    }

    protected abstract void syncAccountsFromSource(ImportAccountSpec.SyncTaskSpec spec, Completion completion);

    private void handle(DestroyThirdPartyAccountSourceMsg message) {
        DestroyThirdPartyAccountSourceReply reply = new DestroyThirdPartyAccountSourceReply();
        threadFacade.chainSubmit(new ChainTask(message) {
            @Override
            public void run(SyncTaskChain chain) {
                destroySource(new Completion(chain) {
                    @Override
                    public void success() {
                        chain.next();
                        bus.reply(message, reply);
                    }

                    @Override
                    public void fail(ErrorCode errorCode) {
                        chain.next();
                        reply.setError(errorCode);
                        bus.reply(message, reply);
                    }
                });
            }

            @Override
            public String getSyncSignature() {
                return userSourceQueueSyncSignature(self.getUuid());
            }

            @Override
            public String getName() {
                return "destroy-source-" + self.getUuid();
            }
        });
    }

    protected abstract void destroySource(Completion completion);
}
