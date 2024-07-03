package org.zstack.ldap.compute;

import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.zstack.core.cloudbus.CloudBus;
import org.zstack.core.cloudbus.CloudBusCallBack;
import org.zstack.core.cloudbus.EventCallback;
import org.zstack.core.cloudbus.EventFacade;
import org.zstack.header.core.NoErrorCompletion;
import org.zstack.header.core.ReturnValueCompletion;
import org.zstack.header.longjob.LongJob;
import org.zstack.header.longjob.LongJobFor;
import org.zstack.header.longjob.LongJobVO;
import org.zstack.header.message.APIEvent;
import org.zstack.header.message.MessageReply;
import org.zstack.identity.imports.AccountImportsConstant;
import org.zstack.identity.imports.entity.SyncCreatedAccountStrategy;
import org.zstack.identity.imports.entity.SyncDeletedAccountStrategy;
import org.zstack.identity.imports.entity.ThirdPartyAccountSourceVO;
import org.zstack.identity.imports.message.SyncThirdPartyAccountMsg;
import org.zstack.identity.imports.message.SyncThirdPartyAccountReply;
import org.zstack.ldap.api.APISyncAccountsFromLdapServerEvent;
import org.zstack.ldap.api.APISyncAccountsFromLdapServerMsg;
import org.zstack.utils.Utils;
import org.zstack.utils.gson.JSONObjectUtil;
import org.zstack.utils.logging.CLogger;

import java.util.Map;
import java.util.Objects;

import static org.zstack.core.Platform.operr;
import static org.zstack.identity.imports.AccountImportsConstant.SYNC_TRACE_PATH;
import static org.zstack.longjob.LongJobUtils.*;

@LongJobFor(APISyncAccountsFromLdapServerMsg.class)
@Configurable(preConstruction = true, autowire = Autowire.BY_TYPE)
public class LdapSyncLongJob implements LongJob {
    private static final CLogger logger = Utils.getLogger(LdapSyncLongJob.class);

    private final SyncThirdPartyAccountMsg innerMsg = new SyncThirdPartyAccountMsg();
    private volatile boolean done = false;
    private final Object lock = new Object();
    private final EventCallback<LdapSyncTaskResult> syncTrackListener = new EventCallback<LdapSyncTaskResult>() {
        @Override
        protected void run(Map<String, String> tokens, LdapSyncTaskResult data) {
            if (!Objects.equals(data.getSourceUuid(), innerMsg.getSourceUuid())) {
                return;
            }
            onSyncTrack(data);
        }
    };

    LongJobVO job;

    @Autowired
    private CloudBus bus;
    @Autowired
    private EventFacade eventFacade;

    @Override
    public void start(LongJobVO job, ReturnValueCompletion<APIEvent> completion) {
        APISyncAccountsFromLdapServerMsg apiMessage =
                JSONObjectUtil.toObject(job.getJobData(), APISyncAccountsFromLdapServerMsg.class);
        APISyncAccountsFromLdapServerEvent event = new APISyncAccountsFromLdapServerEvent(job.getApiId());
        this.job = job;
        start();

        innerMsg.setSourceUuid(apiMessage.getUuid());
        if (apiMessage.getCreateAccountStrategy() != null) {
            innerMsg.setCreateAccountStrategy(SyncCreatedAccountStrategy.valueOf(apiMessage.getCreateAccountStrategy()));
        }
        if (apiMessage.getDeleteAccountStrategy() != null) {
            innerMsg.setDeleteAccountStrategy(SyncDeletedAccountStrategy.valueOf(apiMessage.getDeleteAccountStrategy()));
        }

        bus.makeTargetServiceIdByResourceUuid(innerMsg, AccountImportsConstant.SERVICE_ID, apiMessage.getUuid());
        bus.send(innerMsg, new CloudBusCallBack(completion) {
            @Override
            public void run(MessageReply reply) {
                synchronized (lock) {
                    done = true;
                }

                if (!reply.isSuccess()) {
                    completion.fail(reply.getError());
                    return;
                }

                SyncThirdPartyAccountReply castReply = reply.castReply();
                event.setResult(castReply.getResult());
                synchronized (lock) {
                    setJobResult(job.getUuid(), castReply.getResult());
                }
                completion.success(event);
            }
        });
    }

    private void start() {
        done = false;
        eventFacade.on(SYNC_TRACE_PATH, syncTrackListener);
    }

    private void onSyncTrack(LdapSyncTaskResult progress) {
        synchronized (lock) {
            if (done) {
                return;
            }
            setJobResult(job.getUuid(), progress);
        }
    }

    @Override
    public void cancel(LongJobVO job, ReturnValueCompletion<Boolean> completion) {
        completion.fail(operr("not support"));
    }

    @Override
    public void resume(LongJobVO job, ReturnValueCompletion<APIEvent> completion) {
        completion.fail(operr("not support"));
    }

    @Override
    public void clean(LongJobVO job, NoErrorCompletion completion) {
        eventFacade.off(syncTrackListener);
        completion.done();
    }

    @Override
    public Class<?> getAuditType() {
        return ThirdPartyAccountSourceVO.class;
    }

    @Override
    public String getAuditResourceUuid() {
        return innerMsg.getSourceUuid();
    }
}
