package org.zstack.compute.host;

import org.apache.logging.log4j.ThreadContext;
import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.zstack.core.cloudbus.CloudBus;
import org.zstack.core.cloudbus.EventFacade;
import org.zstack.core.componentloader.PluginRegistry;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.core.db.Q;
import org.zstack.core.thread.CancelablePeriodicTask;
import org.zstack.core.thread.ThreadFacade;
import org.zstack.header.core.Completion;
import org.zstack.header.host.GetSoftwarePackageDownloadProgressMsg;
import org.zstack.header.host.GetSoftwareDownloadProgressReply;
import org.zstack.header.longjob.LongJobVO;
import org.zstack.header.longjob.LongJobVO_;
import org.zstack.header.message.MessageReply;
import org.zstack.header.storage.backup.BackupStorageConstant;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.zstack.core.Platform.operr;
import static org.zstack.core.progress.ProgressReportService.reportProgress;
import static org.zstack.header.Constants.THREAD_CONTEXT_API;
import static org.zstack.header.Constants.THREAD_CONTEXT_TASK_NAME;


@Configurable(preConstruction = true, autowire = Autowire.BY_TYPE)
public class UploadFileTracker {
    private static final CLogger logger = Utils.getLogger(UploadFileTracker.class);

    @Autowired
    private CloudBus bus;
    @Autowired
    DatabaseFacade dbf;
    @Autowired
    private ThreadFacade thdf;
    @Autowired
    private PluginRegistry pluginRgty;
    @Autowired
    private EventFacade evtf;

    private final String apiId = ThreadContext.get(THREAD_CONTEXT_API);
    private final boolean continuable = apiId != null && Q.New(LongJobVO.class).eq(LongJobVO_.apiId, apiId).isExists();

    public static class TrackContext {
        String url;
        String taskUuid;
        String hostname;
    }

    Map<String, TrackContext> ctxs = new HashMap<>();

    public void addTrackTask(String taskUuid, String hostname, String url) {
        TrackContext ctx = new TrackContext();
        ctx.taskUuid = taskUuid;
        ctx.hostname = hostname;
        ctx.url = url;
        ctxs.put(taskUuid, ctx);
    }

    public void runTrackTask(String taskUuid, Completion completion) {
        TrackContext ctx = ctxs.get(taskUuid);
        trackUpload(ctx.taskUuid, ctx.hostname, ctx.url, completion);
    }

    void trackUpload(String taskUuid, String hostname, String url, Completion completion) {
        final int maxNumOfFailure = 3;
        final long maxIdleSecond = 10;

        thdf.submitCancelablePeriodicTask(new CancelablePeriodicTask() {
            private long numError = 0;
            private long createdTime = System.currentTimeMillis();

            private boolean overMaxIdleTime(long lastOpTimeInMills) {
                long latestTime = Long.max(lastOpTimeInMills, createdTime);
                return System.currentTimeMillis() - latestTime > TimeUnit.SECONDS.toMillis(maxIdleSecond);
            }

            @Override
            public boolean run() {
                if (overMaxIdleTime(createdTime)) {
                    completion.fail(operr("upload session expired"));
                    return true;
                }

                final GetSoftwareDownloadProgressReply reply = getImageDownloadProgress(hostname);
                if (!reply.isSuccess()) {
                    if (++numError <= maxNumOfFailure) {
                        return false;
                    }
                    completion.fail(reply.getError());
                    return true;
                }
                if (reply.isCompleted()) {
                    completion.success();
                    return true;
                }

                return true;
            }

            @Override
            public TimeUnit getTimeUnit() {
                return TimeUnit.SECONDS;
            }

            @Override
            public long getInterval() {
                return 3;
            }

            @Override
            public String getName() {
                return String.format("tracking upload file [name: %s, taskUuid: %s]", url, taskUuid);
            }
        });
    }

    private void doReportProgress(String taskName, long progress) {
        ThreadContext.put(THREAD_CONTEXT_API, apiId);
        ThreadContext.put(THREAD_CONTEXT_TASK_NAME, taskName);
        reportProgress(String.valueOf(progress));
    }

    public GetSoftwareDownloadProgressReply getImageDownloadProgress(String hostname) {
        final GetSoftwarePackageDownloadProgressMsg dmsg = new GetSoftwarePackageDownloadProgressMsg();
        dmsg.setTaskUuid(apiId);
        dmsg.setHostname(hostname);
        bus.makeLocalServiceId(dmsg, BackupStorageConstant.SERVICE_ID);
        final MessageReply reply = bus.call(dmsg);
        if (reply.isSuccess()) {
            return reply.castReply();
        } else {
            GetSoftwareDownloadProgressReply r = new GetSoftwareDownloadProgressReply();
            r.setError(reply.getError());
            return r;
        }
    }
}
