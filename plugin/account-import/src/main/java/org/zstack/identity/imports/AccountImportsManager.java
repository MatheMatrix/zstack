package org.zstack.identity.imports;

import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.core.cloudbus.CloudBus;
import org.zstack.core.db.Q;
import org.zstack.core.thread.ChainTask;
import org.zstack.core.thread.SyncTaskChain;
import org.zstack.core.thread.ThreadFacade;
import org.zstack.header.AbstractService;
import org.zstack.header.errorcode.ErrorableValue;
import org.zstack.header.message.Message;
import org.zstack.identity.imports.entity.AccountImportSourceVO;
import org.zstack.identity.imports.entity.AccountImportSourceVO_;
import org.zstack.identity.imports.message.CreateThirdPartyAccountSourceMsg;
import org.zstack.identity.imports.message.CreateThirdPartyAccountSourceReply;
import org.zstack.identity.imports.message.ImportSourceMessage;
import org.zstack.identity.imports.source.AccountImportSourceFactory;

import java.util.List;
import java.util.Objects;

import static org.zstack.core.Platform.operr;
import static org.zstack.identity.imports.AccountImportsConstant.*;

/**
 * Created by Wenhao.Zhang on 2024/05/31
 */
public class AccountImportsManager extends AbstractService {
    @Autowired
    private CloudBus bus;
    @Autowired
    private ThreadFacade threads;
    @Autowired
    private List<AccountImportSourceFactory> factories;

    @Override
    public boolean start() {
        return true;
    }

    @Override
    public boolean stop() {
        return true;
    }

    @Override
    public void handleMessage(Message msg) {
        if (msg instanceof ImportSourceMessage) {
            passThrough(((ImportSourceMessage) msg));
        } else if (msg instanceof CreateThirdPartyAccountSourceMsg) {
            handle(((CreateThirdPartyAccountSourceMsg) msg));
        } else {
            bus.dealWithUnknownMessage(msg);
        }
    }

    @Override
    public String getId() {
        return SERVICE_ID;
    }

    private ErrorableValue<AccountImportSourceFactory> findFactoryByType(String type) {
        for (AccountImportSourceFactory factory : factories) {
            if (Objects.equals(type, factory.type())) {
                return ErrorableValue.of(factory);
            }
        }
        return ErrorableValue.ofErrorCode(operr("failed to find account import source by type[%s]", type));
    }

    public static String importSourceQueueSyncSignature(String sourceUuid) {
        return AccountImportSourceVO.class.getSimpleName() + "-" + sourceUuid;
    }

    private void handle(CreateThirdPartyAccountSourceMsg message) {
        CreateThirdPartyAccountSourceReply reply = new CreateThirdPartyAccountSourceReply();

        final ErrorableValue<AccountImportSourceFactory> errorableValue = findFactoryByType(message.getType());
        if (!errorableValue.isSuccess()) {
            reply.setError(errorableValue.error);
            bus.reply(message, reply);
            return;
        }

        final AccountImportSourceFactory factory = errorableValue.result;
        threads.chainSubmit(new ChainTask(message) {
            @Override
            public void run(SyncTaskChain chain) {
                final ErrorableValue<AccountImportSourceVO> vo = factory.createAccountImportSource(message.getSpec());
                if (!vo.isSuccess()) {
                    reply.setError(vo.error);
                }
                bus.reply(message, reply);
            }

            @Override
            public String getSyncSignature() {
                return importSourceQueueSyncSignature(message.getSpec().uuid);
            }

            @Override
            public String getName() {
                return "create-import-source-" + message.getSpec().uuid;
            }
        });
    }

    private void passThrough(ImportSourceMessage msg) {
        final String type = Q.New(AccountImportSourceVO.class)
                .select(AccountImportSourceVO_.type)
                .eq(AccountImportSourceVO_.uuid, msg.getSourceUuid())
                .findValue();

        final ErrorableValue<AccountImportSourceFactory> factory = findFactoryByType(type);
        if (!factory.isSuccess()) {
            bus.replyErrorByMessageType((Message) msg, factory.error);
            return;
        }

        factory.result.createBase().handleMessage((Message) msg);
    }
}
