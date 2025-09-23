package org.zstack.kvm;

import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.zstack.compute.host.UploadFileTracker;
import org.zstack.core.cloudbus.AutoOffEventCallback;
import org.zstack.core.cloudbus.CloudBus;
import org.zstack.core.cloudbus.CloudBusCallBack;
import org.zstack.core.cloudbus.EventFacade;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.header.core.NoErrorCompletion;
import org.zstack.header.core.ReturnValueCompletion;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.errorcode.SysErrors;
import org.zstack.header.host.*;
import org.zstack.header.image.*;
import org.zstack.header.longjob.LongJob;
import org.zstack.header.longjob.LongJobFor;
import org.zstack.header.longjob.LongJobVO;
import org.zstack.header.longjob.UseApiTimeout;
import org.zstack.header.message.APIEvent;
import org.zstack.header.message.MessageReply;
import org.zstack.longjob.LongJobGlobalConfig;
import org.zstack.longjob.LongJobUtils;
import org.zstack.utils.Utils;
import org.zstack.utils.gson.JSONObjectUtil;
import org.zstack.utils.logging.CLogger;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.zstack.core.Platform.operr;
import static org.zstack.longjob.LongJobUtils.*;
import static org.zstack.longjob.LongJobUtils.cancelErr;


@UseApiTimeout(APIUploadFileToHostMsg.class)
@LongJobFor(APIUploadFileToHostMsg.class)
@Configurable(preConstruction = true, autowire = Autowire.BY_TYPE)
public class UploadFileLongJob implements LongJob {
    private static final CLogger logger = Utils.getLogger(UploadFileLongJob.class);

    @Autowired
    protected CloudBus bus;
    @Autowired
    protected DatabaseFacade dbf;
    @Autowired
    protected EventFacade evtf;

    protected String auditResourceUuid;

    class UploadFileCompletion<T extends UploadFileToHostReply> extends ReturnValueCompletion<T> {
        APIUploadFileToHostEvent event;
        LongJobVO job;
        ReturnValueCompletion<APIEvent> completion;
        AtomicBoolean done = new AtomicBoolean(false);

        UploadFileCompletion(APIUploadFileToHostEvent event, LongJobVO job, ReturnValueCompletion<APIEvent> completion) {
            super(completion);
            this.job = job;
            this.event = event;
            this.completion = completion;
        }

        @Override
        public void success(UploadFileToHostReply reply) {
            if (done.compareAndSet(false, true)) {
                event.setMd5sum(reply.getMd5sum());
                job = setJobResult(job.getUuid(), event);
                completion.success(event);
            }
        }

        @Override
        public void fail(ErrorCode err) {
            if (done.compareAndSet(false, true)) {
                job = setJobError(job.getUuid(), err);
                completion.fail(err);
            }
        }

        public void track(UploadFileToHostReply reply) {
            if (!done.get()) {
                event.setMd5sum(reply.getMd5sum());
                job = setJobResult(job.getUuid(), event);
            }
        }

        void startTrack() {
            long offTime = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(LongJobGlobalConfig.LONG_JOB_DEFAULT_TIMEOUT.value(Long.class));
            evtf.on(HostCanonicalEvents.FILE_TRACK_RESULT_PATH, new AutoOffEventCallback() {
                @Override
                protected boolean run(Map tokens, Object d) {
                    HostCanonicalEvents.FileTrackData data = (HostCanonicalEvents.FileTrackData) d;
                    UploadFileToHostReply reply = data.getReply();
                    if (reply != null && job.getApiId().equals(reply.getApiId())) {
                        handleResult(data);
                        return true;
                    } else if (offTime < System.currentTimeMillis()) {
                        return true;
                    }

                    return false;
                }

                private void handleResult(HostCanonicalEvents.FileTrackData data) {
                    if (data.isSuccess()) {
                        success(data.getReply());
                    } else if (data.getError().isError(HostErrors.UPLOAD_FILE_INTERRUPTED)) {
                        fail(LongJobUtils.interruptedErr(job.getUuid(), data.getError()));
                    } else {
                        fail(data.getError());
                    }
                }
            });
        }
    }

    @Override
    public void start(LongJobVO job, ReturnValueCompletion<APIEvent> completion) {
        UploadFileToHostMsg msg = JSONObjectUtil.toObject(job.getJobData(), UploadFileToHostMsg.class);
        auditResourceUuid = msg.getUuid();

        APIUploadFileToHostEvent evt = new APIUploadFileToHostEvent(job.getApiId());
        UploadFileCompletion comp = new UploadFileCompletion(evt, job, completion);
        if (msg.needTrack()) {
            comp.startTrack();
        }

        bus.makeTargetServiceIdByResourceUuid(msg, HostConstant.SERVICE_ID, msg.getUuid());
        bus.send(msg, new CloudBusCallBack(comp) {
            @Override
            public void run(MessageReply reply) {
                if (reply.isSuccess()) {
                    handleSuccess(reply);
                } else {

                    completion.fail(reply.getError());
                }
            }

            private void handleSuccess(MessageReply reply) {
                UploadFileToHostReply r = reply.castReply();
                if (jobCanceled(job.getUuid())) {
                    cleanImage(msg, comp, cancelErr(job.getUuid()));
                } else if (msg.needTrack()) {
                    comp.track(r);
                } else {
                    comp.success(r);
                }
            }
        });
    }

    @Override
    public void resume(LongJobVO job, ReturnValueCompletion<APIEvent> completion) {
        UploadFileToHostMsg msg = JSONObjectUtil.toObject(job.getJobData(), UploadFileToHostMsg.class);

        if (msg.needTrack()) {
            APIUploadFileToHostEvent evt = new APIUploadFileToHostEvent(job.getApiId());
            new UploadFileCompletion(evt, job, completion).startTrack();
            new UploadFileTracker().trackUpload(job.getApiId(), msg.getHostUuid(), msg.getInstallPath());
            return;
        }

        DeleteBitsMsg dmsg = buildDeletionMsg(msg);
        bus.send(dmsg, new CloudBusCallBack(null) {
            @Override
            public void run(MessageReply reply) {
                if (!reply.isSuccess()) {
                    logger.warn(String.format("delete file [%s] failed after management node restarted", msg.getInstallPath()));
                }

                completion.fail(operr("Failed because management node restarted."));
            }
        });
    }

    @Override
    public void cancel(LongJobVO job, ReturnValueCompletion<Boolean> completion) {
        UploadFileToHostMsg umsg = JSONObjectUtil.toObject(job.getJobData(), UploadFileToHostMsg.class);
        CancelHostTaskMsg cmsg = new CancelHostTaskMsg();
        cmsg.setHostUuid(umsg.getHostUuid());
        cmsg.setCancellationApiId(job.getApiId());
        bus.makeLocalServiceId(cmsg, HostConstant.SERVICE_ID);
        bus.send(cmsg, new CloudBusCallBack(completion) {
            @Override
            public void run(MessageReply reply) {
                if (reply.isSuccess()) {
                    completion.success(false);
                } else if (reply.getError().isError(SysErrors.RESOURCE_NOT_FOUND)) {
                    completion.success(true);
                } else {
                    completion.fail(reply.getError());
                }
            }
        });
    }

    @Override
    public void clean(LongJobVO job, NoErrorCompletion completion) {
        UploadFileToHostMsg umsg = JSONObjectUtil.toObject(job.getJobData(), UploadFileToHostMsg.class);
        DeleteBitsMsg dmsg = buildDeletionMsg(umsg);
        bus.send(dmsg, new CloudBusCallBack(completion) {
            @Override
            public void run(MessageReply reply) {
                completion.done();
            }
        });
    }

    @Override
    public Class getAuditType() {
        return HostVO.class;
    }

    @Override
    public String getAuditResourceUuid() {
        return auditResourceUuid;
    }

    private DeleteBitsMsg buildDeletionMsg(UploadFileToHostMsg msg) {
        DeleteBitsMsg dmsg = new DeleteBitsMsg();
        dmsg.setHostUuid(msg.getHostUuid());
        dmsg.setPath(msg.getInstallPath());
        dmsg.setFolder(false);
        bus.makeTargetServiceIdByResourceUuid(dmsg, HostConstant.SERVICE_ID, msg.getHostUuid());
        return dmsg;
    }

    private void cleanImage(UploadFileToHostMsg msg, UploadFileCompletion completion, ErrorCode err) {
        DeleteBitsMsg dmsg = buildDeletionMsg(msg);
        bus.send(dmsg, new CloudBusCallBack(completion) {
            @Override
            public void run(MessageReply reply) {
                completion.fail(err);
            }
        });
    }
}
