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
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.host.*;
import org.zstack.header.longjob.LongJobVO;
import org.zstack.header.longjob.LongJobVO_;
import org.zstack.header.message.MessageReply;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.zstack.core.Platform.err;
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

    public void runTrackTask(String apiId, String hostUuid, String installPath) {
        final int maxNumOfFailure = HostGlobalConfig.UPLOAD_FAILURE_TOLERANCE_COUNT.value(Integer.class);
        final long maxIdleSecond = HostGlobalConfig.UPLOAD_MAX_IDLE_IN_SECONDS.value(Long.class);

        thdf.submitCancelablePeriodicTask(new CancelablePeriodicTask() {
            private long numError = 0;
            private long createdTime = System.currentTimeMillis();
            boolean continuable = Q.New(LongJobVO.class).eq(LongJobVO_.apiId, apiId).isExists();

            private boolean overMaxIdleTime(long lastOpTimeInMills) {
                long latestTime = Long.max(lastOpTimeInMills, createdTime);
                return System.currentTimeMillis() - latestTime > TimeUnit.SECONDS.toMillis(maxIdleSecond);
            }

            private void markCompletion(final GetFileDownloadProgressReply dr) {
                UploadFileToHostReply r = new UploadFileToHostReply();
                r.setMd5sum(dr.getMd5sum());
                r.setApiId(apiId);
                r.setInstallPath(installPath);
                fireEvent(r, null);
            }

            private void markFailure(ErrorCode reason) {
                logger.error(String.format("upload file to host failed: %s", reason.toString()));

                fireEvent(null, reason);
                if (reason.isError(HostErrors.UPLOAD_FILE_INTERRUPTED) && continuable) {
                    return;
                }

                DeleteBitsMsg dmsg = new DeleteBitsMsg();
                dmsg.setHostUuid(hostUuid);
                dmsg.setPath(installPath);
                dmsg.setFolder(false);
                bus.makeTargetServiceIdByResourceUuid(dmsg, HostConstant.SERVICE_ID, hostUuid);
                bus.send(dmsg);
            }

            private void fireEvent(UploadFileToHostReply reply, ErrorCode error) {
                HostCanonicalEvents.FileTrackData data = new HostCanonicalEvents.FileTrackData();
                data.setReply(reply);
                data.setError(error);
                evtf.fire(HostCanonicalEvents.FILE_TRACK_RESULT_PATH, data);
            }

            @Override
            public boolean run() {
                final GetFileDownloadProgressReply reply = getImageDownloadProgress(apiId, hostUuid);
                if (!reply.isSuccess()) {
                    if (++numError <= maxNumOfFailure) {
                        return false;
                    }

                    markFailure(reply.getError());
                    return true;
                }

                if (reply.getDownloadSize() == 0 && overMaxIdleTime(createdTime)) {
                    markFailure(operr("upload session expired"));
                    return true;
                }

                boolean downloadingImageSuspendedTooLong = !reply.isDownloadComplete() && overMaxIdleTime(reply.getLastOpTime());
                if (downloadingImageSuspendedTooLong && reply.isSupportSuspend()) {
                    markFailure(err(HostErrors.UPLOAD_FILE_INTERRUPTED, reply.getError(),
                            "uploading has been inactive more than %d sec", maxIdleSecond));
                    return true;
                }

                // reset the error counter
                numError = 0;

                if (!reply.isCompleted()) {
                    doReportProgress(apiId, "uploading file", reply.getProgress());
                    return false;
                }

                // upload completed
                doReportProgress(apiId, "success to upload file", 100);
                markCompletion(reply);
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
                return String.format("tracking upload file [apiId: %s]", apiId);
            }
        });
    }

    private void doReportProgress(String apiId, String taskName, long progress) {
        ThreadContext.put(THREAD_CONTEXT_API, apiId);
        ThreadContext.put(THREAD_CONTEXT_TASK_NAME, taskName);
        reportProgress(String.valueOf(progress));
    }

    public GetFileDownloadProgressReply getImageDownloadProgress(String apiId, String hostUuid) {
        final GetFileDownloadProgressMsg dmsg = new GetFileDownloadProgressMsg();
        dmsg.setApiId(apiId);
        dmsg.setHostUuid(hostUuid);
        bus.makeTargetServiceIdByResourceUuid(dmsg, HostConstant.SERVICE_ID, hostUuid);
        final MessageReply reply = bus.call(dmsg);
        if (reply.isSuccess()) {
            return reply.castReply();
        } else {
            GetFileDownloadProgressReply r = new GetFileDownloadProgressReply();
            r.setError(reply.getError());
            return r;
        }
    }
}
