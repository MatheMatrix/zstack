package org.zstack.identity.imports;

import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.core.cloudbus.CloudBus;
import org.zstack.core.componentloader.PluginRegistry;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.core.thread.ChainTask;
import org.zstack.core.thread.SyncTaskChain;
import org.zstack.core.thread.ThreadFacade;
import org.zstack.header.AbstractService;
import org.zstack.header.errorcode.ErrorableValue;
import org.zstack.header.message.Message;
import org.zstack.identity.imports.entity.ThirdPartyAccountSourceVO;
import org.zstack.identity.imports.message.CreateThirdPartyAccountSourceMsg;
import org.zstack.identity.imports.message.CreateThirdPartyAccountSourceReply;
import org.zstack.identity.imports.message.AccountSourceMessage;
import org.zstack.identity.imports.source.AccountSourceFactory;

import java.util.List;
import java.util.Objects;

import static org.zstack.core.Platform.operr;
import static org.zstack.identity.imports.AccountImportsConstant.*;

public class AccountImportsManager extends AbstractService {
    @Autowired
    private CloudBus bus;
    @Autowired
    private ThreadFacade thdf;
    @Autowired
    private DatabaseFacade databaseFacade;
    @Autowired
    private PluginRegistry pluginRegistry;

    private List<AccountSourceFactory> factories;

    @Override
    public boolean start() {
        buildAccountSourceFactoryList();
        return true;
    }

    private void buildAccountSourceFactoryList() {
        this.factories = pluginRegistry.getExtensionList(AccountSourceFactory.class);
    }

    @Override
    public boolean stop() {
        return true;
    }

    @Override
    public void handleMessage(Message msg) {
        if (msg instanceof AccountSourceMessage) {
            passThrough(((AccountSourceMessage) msg));
        } else if (msg instanceof CreateThirdPartyAccountSourceMsg) {
            handle(((CreateThirdPartyAccountSourceMsg) msg));
        } else {
            bus.dealWithUnknownMessage(msg);
        }
    }

    @Override
    public String getId() {
        return bus.makeLocalServiceId(SERVICE_ID);
    }

    private ErrorableValue<AccountSourceFactory> findFactoryByType(String type) {
        for (AccountSourceFactory factory : factories) {
            if (Objects.equals(type, factory.type())) {
                return ErrorableValue.of(factory);
            }
        }
        return ErrorableValue.ofErrorCode(operr("failed to find account import source by type[%s]", type));
    }

    public static String accountSourceSyncTaskSignature() {
        return ThirdPartyAccountSourceVO.class.getSimpleName() + "-syncing";
    }

    public static String accountSourceQueueSyncSignature(String sourceUuid) {
        return ThirdPartyAccountSourceVO.class.getSimpleName() + "-" + sourceUuid;
    }

    private void handle(CreateThirdPartyAccountSourceMsg message) {
        CreateThirdPartyAccountSourceReply reply = new CreateThirdPartyAccountSourceReply();

        final ErrorableValue<AccountSourceFactory> errorableValue = findFactoryByType(message.getType());
        if (!errorableValue.isSuccess()) {
            reply.setError(errorableValue.error);
            bus.reply(message, reply);
            return;
        }

        final AccountSourceFactory factory = errorableValue.result;
        thdf.chainSubmit(new ChainTask(message) {
            @Override
            public void run(SyncTaskChain chain) {
                final ErrorableValue<ThirdPartyAccountSourceVO> vo = factory.createAccountSource(message.getSpec());
                if (!vo.isSuccess()) {
                    reply.setError(vo.error);
                }
                bus.reply(message, reply);
            }

            @Override
            public String getSyncSignature() {
                return accountSourceQueueSyncSignature(message.getSpec().getUuid());
            }

            @Override
            public String getName() {
                return "create-import-source-" + message.getSpec().getUuid();
            }
        });
    }

    private void passThrough(AccountSourceMessage msg) {
        final ThirdPartyAccountSourceVO vo = databaseFacade.findByUuid(msg.getSourceUuid(), ThirdPartyAccountSourceVO.class);
        final ErrorableValue<AccountSourceFactory> factory = findFactoryByType(vo.getType());
        if (!factory.isSuccess()) {
            bus.replyErrorByMessageType((Message) msg, factory.error);
            return;
        }

        factory.result.createBase(vo).handleMessage((Message) msg);
    }
}
