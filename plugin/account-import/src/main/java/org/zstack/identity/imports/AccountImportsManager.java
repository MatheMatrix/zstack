package org.zstack.identity.imports;

import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.core.cloudbus.CloudBus;
import org.zstack.core.db.Q;
import org.zstack.core.thread.ThreadFacade;
import org.zstack.header.AbstractService;
import org.zstack.header.errorcode.ErrorableValue;
import org.zstack.header.message.Message;
import org.zstack.identity.imports.entity.AccountImportSourceVO;
import org.zstack.identity.imports.entity.AccountImportSourceVO_;
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
