package org.zstack.ldap.compute;

import org.apache.logging.log4j.ThreadContext;
import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.zstack.core.cloudbus.CloudBus;
import org.zstack.core.cloudbus.CloudBusCallBack;
import org.zstack.core.cloudbus.EventCallback;
import org.zstack.core.cloudbus.EventFacade;
import org.zstack.header.Constants;
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
import java.util.concurrent.atomic.AtomicBoolean;

import static org.zstack.core.Platform.operr;
import static org.zstack.core.progress.ProgressReportService.reportProgress;
import static org.zstack.longjob.LongJobUtils.*;

@LongJobFor(APISyncAccountsFromLdapServerMsg.class)
@Configurable(preConstruction = true, autowire = Autowire.BY_TYPE)
public class LdapSyncLongJob implements LongJob {
    private static final CLogger logger = Utils.getLogger(LdapSyncLongJob.class);

    private final SyncThirdPartyAccountMsg innerMsg = new SyncThirdPartyAccountMsg();
    private final AtomicBoolean done = new AtomicBoolean(false);
    private final Object lock = new Object();
    private final EventCallback<LdapSyncProgress> syncTrackListener = new EventCallback<LdapSyncProgress>() {
        @Override
        protected void run(Map<String, String> tokens, LdapSyncProgress data) {
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
        innerMsg.setSyncTrackPath(buildSyncEventPath());
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
                if (!reply.isSuccess()) {
                    completion.fail(reply.getError());
                    return;
                }

                SyncThirdPartyAccountReply castReply = reply.castReply();
                event.setResult(castReply.getResult());
                synchronized (lock) {
                    done.set(true);
                    setJobResult(job.getUuid(), castReply.getResult());
                }
                completion.success(event);
            }
        });
    }

    private void start() {
        done.set(false);
        eventFacade.on(buildSyncEventPath(), syncTrackListener);
    }

    private void onSyncTrack(LdapSyncProgress progress) {
        synchronized (lock) {
            if (done.get()) {
                return;
            }
            setJobResult(job.getUuid(), progress);
            reportProgress(Float.toString(progress.progress()));
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

    private String buildSyncEventPath() {
        String apiId = ThreadContext.get(Constants.THREAD_CONTEXT_API);
        return String.format("/ldap/sync/track/%s", apiId);
    }
}
