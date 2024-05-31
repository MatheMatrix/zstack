package org.zstack.identity.imports.source;

import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.zstack.core.Platform;
import org.zstack.core.asyncbatch.While;
import org.zstack.core.cloudbus.CloudBus;
import org.zstack.core.cloudbus.CloudBusCallBack;
import org.zstack.core.cloudbus.MessageSafe;
import org.zstack.core.componentloader.PluginRegistry;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.core.thread.ChainTask;
import org.zstack.core.thread.SyncTaskChain;
import org.zstack.core.thread.ThreadFacade;
import org.zstack.core.workflow.FlowChainBuilder;
import org.zstack.header.core.ReturnValueCompletion;
import org.zstack.header.core.WhileDoneCompletion;
import org.zstack.header.core.workflow.Flow;
import org.zstack.header.core.workflow.FlowChain;
import org.zstack.header.core.workflow.FlowDoneHandler;
import org.zstack.header.core.workflow.FlowErrorHandler;
import org.zstack.header.core.workflow.FlowRollback;
import org.zstack.header.core.workflow.FlowTrigger;
import org.zstack.header.core.workflow.NoRollbackFlow;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.errorcode.ErrorCodeList;
import org.zstack.header.identity.AccountConstant;
import org.zstack.header.identity.AccountInventory;
import org.zstack.header.identity.AccountVO;
import org.zstack.header.identity.CreateAccountMsg;
import org.zstack.header.identity.CreateAccountReply;
import org.zstack.header.identity.DeleteAccountMsg;
import org.zstack.header.message.Message;
import org.zstack.header.message.MessageReply;
import org.zstack.identity.imports.AccountImportExtensionPoint;
import org.zstack.identity.imports.entity.AccountImportChunk;
import org.zstack.identity.imports.entity.AccountImportSourceVO;
import org.zstack.identity.imports.entity.ImportAccountBatch;
import org.zstack.identity.imports.entity.ImportAccountRefVO;
import org.zstack.identity.imports.message.AttachAccountToImportSourceMsg;
import org.zstack.identity.imports.message.AttachAccountToImportSourceReply;
import org.zstack.identity.imports.message.ImportAccountsMsg;
import org.zstack.identity.imports.message.ImportAccountsReply;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.zstack.core.Platform.operr;
import static org.zstack.identity.imports.AccountImportsManager.importSourceQueueSyncSignature;
import static org.zstack.utils.CollectionUtils.*;
import static org.zstack.utils.CollectionUtils.transform;

/**
 * Created by Wenhao.Zhang on 2024/05/31
 */
@Configurable(preConstruction = true, autowire = Autowire.BY_TYPE)
public abstract class AbstractAccountImportSourceBase {
    private static final CLogger logger = Utils.getLogger(AbstractAccountImportSourceBase.class);

    @Autowired
    private CloudBus bus;
    @Autowired
    private DatabaseFacade databases;
    @Autowired
    private PluginRegistry plugins;
    @Autowired
    private ThreadFacade threads;

    protected AbstractAccountImportSourceBase(AccountImportSourceVO self) {
        this.self = Objects.requireNonNull(self);
    }

    protected AccountImportSourceVO self;
    public abstract String type();

    @MessageSafe
    public void handleMessage(Message msg) {
        if (msg instanceof ImportAccountsMsg) {
            handle(((ImportAccountsMsg) msg));
        } else if (msg instanceof AttachAccountToImportSourceMsg) {
            handle(((AttachAccountToImportSourceMsg) msg));
        } else {
            bus.dealWithUnknownMessage(msg);
        }
    }

    public void handle(ImportAccountsMsg message) {
        ImportAccountsReply reply = new ImportAccountsReply();
        final String sourceUuid = message.getSourceUuid();

        threads.chainSubmit(new ChainTask(message) {
            @Override
            public void run(SyncTaskChain chain) {
                importAccounts(message.getBatch(), new ReturnValueCompletion<List<AccountImportChunk>>(chain) {
                    @Override
                    public void success(List<AccountImportChunk> results) {
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
                return importSourceQueueSyncSignature(sourceUuid);
            }

            @Override
            public String getName() {
                return "import-accounts-from-source-" + sourceUuid;
            }
        });
    }

    @SuppressWarnings("rawtypes")
    public void importAccounts(ImportAccountBatch batch, ReturnValueCompletion<List<AccountImportChunk>> completion) {
        final List<AccountImportExtensionPoint> extensions = plugins.getExtensionList(AccountImportExtensionPoint.class);
        List<AccountImportChunk> chunks = new ArrayList<>(batch.accountList.size());

        FlowChain chain = FlowChainBuilder.newSimpleFlowChain();
        chain.setName(String.format("chain-with-importing-accounts-from-source-%s", batch.sourceUuid));
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
                new While<>(batch.accountList).each((spec, whileCompletion) -> {
                    final AccountImportChunk chunk = new AccountImportChunk().withSpec(spec);
                    chunks.add(chunk);

                    if (!spec.createIfNotExist && spec.accountUuid == null) {
                        chunk.errorForCreatingAccount = operr("invalid account spec: accountUuid is null");
                        whileCompletion.done();
                        return;
                    }

                    String accountUuid = spec.accountUuid == null ? Platform.getUuid() : spec.accountUuid;

                    ImportAccountRefVO ref = new ImportAccountRefVO();
                    ref.setUuid(Platform.getUuid());
                    ref.setKeyFromImportSource(spec.keyFromSource);
                    ref.setImportSourceUuid(batch.sourceUuid);
                    ref.setAccountUuid(accountUuid);
                    chunk.withRef(ref);

                    if (!spec.createIfNotExist) {
                        final AccountVO account = databases.findByUuid(spec.accountUuid, AccountVO.class);
                        if (account == null) {
                            chunk.errorForCreatingAccount = operr("invalid account spec: failed to find account[uuid=%s]",
                                    spec.accountUuid);
                        } else {
                            chunk.withAccount(AccountInventory.valueOf(account));
                        }
                        whileCompletion.done();
                        return;
                    }

                    CreateAccountMsg message = new CreateAccountMsg();
                    message.setUuid(accountUuid);
                    message.setName(spec.username);
                    message.setPassword(spec.password);
                    message.setType(spec.accountType.toString());
                    bus.makeLocalServiceId(message, AccountConstant.SERVICE_ID);
                    bus.send(message, new CloudBusCallBack(whileCompletion) {
                        @Override
                        public void run(MessageReply reply) {
                            if (reply.isSuccess()) {
                                chunk.withAccount(((CreateAccountReply) reply).getInventory());
                            } else {
                                chunk.errorForCreatingAccount = reply.getError();
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
                new While<>(chunks).each((chunk, whileCompletion) -> {
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
                List<AccountImportChunk> results = filter(chunks, chunk -> chunk.errorForCreatingAccount == null);

                if (results.isEmpty()) {
                    trigger.next();
                    return;
                }

                databases.persistCollection(transform(results, result -> result.ref));
                trigger.next();
            }
        }).then(new NoRollbackFlow() {
            String __name__ = "after-accounts-importing";
            @Override
            public void run(FlowTrigger trigger, Map data) {
                safeForEach(extensions, extension -> extension.afterAccountsImporting(chunks));
                trigger.next();
            }
        }).done(new FlowDoneHandler(completion) {
            @Override
            public void handle(Map data) {
                completion.success(chunks);
            }
        }).error(new FlowErrorHandler(completion) {
            @Override
            public void handle(ErrorCode errCode, Map data) {
                completion.fail(operr(errCode, "failed to import account from source[uuid=%s, type=%s]",
                        batch.sourceUuid, batch.sourceType));
            }
        }).start();
    }

    private void handle(AttachAccountToImportSourceMsg message) {
        AttachAccountToImportSourceReply reply = new AttachAccountToImportSourceReply();
        final String sourceUuid = message.getSourceUuid();

        ImportAccountBatch batch = new ImportAccountBatch();
        batch.sourceUuid = sourceUuid;
        batch.sourceType = this.type();

        ImportAccountBatch.AccountSpec spec = new ImportAccountBatch.AccountSpec();
        spec.accountUuid = message.getAccountUuid();
        spec.createIfNotExist = false;
        spec.keyFromSource = Objects.requireNonNull(message.getKeyFromSource());
        batch.accountList.add(spec);

        threads.chainSubmit(new ChainTask(message) {
            @Override
            public void run(SyncTaskChain chain) {
                importAccounts(batch, new ReturnValueCompletion<List<AccountImportChunk>>(chain) {
                    @Override
                    public void success(List<AccountImportChunk> results) {
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
                return importSourceQueueSyncSignature(sourceUuid);
            }

            @Override
            public String getName() {
                return "bind-account-from-source-" + sourceUuid;
            }
        });
    }
}
