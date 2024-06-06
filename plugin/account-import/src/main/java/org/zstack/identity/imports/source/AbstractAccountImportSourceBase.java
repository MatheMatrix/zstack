package org.zstack.identity.imports.source;

import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.zstack.core.cloudbus.CloudBus;
import org.zstack.core.cloudbus.MessageSafe;
import org.zstack.core.componentloader.PluginRegistry;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.core.thread.ChainTask;
import org.zstack.core.thread.SyncTaskChain;
import org.zstack.core.thread.ThreadFacade;
import org.zstack.header.core.ReturnValueCompletion;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.message.Message;
import org.zstack.identity.imports.entity.AccountImportChunk;
import org.zstack.identity.imports.entity.AccountImportSourceVO;
import org.zstack.identity.imports.entity.ImportAccountBatch;
import org.zstack.identity.imports.message.BindThirdPartyAccountMsg;
import org.zstack.identity.imports.message.ImportThirdPartyAccountMsg;
import org.zstack.identity.imports.message.ImportThirdPartyAccountReply;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import java.util.List;
import java.util.Objects;

import static org.zstack.core.Platform.operr;
import static org.zstack.identity.imports.AccountImportsManager.importSourceQueueSyncSignature;

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
        if (msg instanceof ImportThirdPartyAccountMsg) {
            handle(((ImportThirdPartyAccountMsg) msg));
        } else if (msg instanceof BindThirdPartyAccountMsg) {
            handle(((BindThirdPartyAccountMsg) msg));
        } else {
            bus.dealWithUnknownMessage(msg);
        }
    }

    public void handle(ImportThirdPartyAccountMsg message) {
        ImportThirdPartyAccountReply reply = new ImportThirdPartyAccountReply();
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

    public void importAccounts(ImportAccountBatch batch, ReturnValueCompletion<List<AccountImportChunk>> completion) {
        completion.fail(operr("TODO importAccounts")); // TODO
    }

    private void handle(BindThirdPartyAccountMsg message) {
        bus.replyErrorByMessageType(message, "TODO"); // TODO
    }
}
