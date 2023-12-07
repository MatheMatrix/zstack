package org.zstack.longjob;

import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.core.progress.ProgressGlobalConfig;
import org.zstack.core.thread.PeriodicTask;
import org.zstack.core.thread.ThreadFacade;
import org.zstack.header.Component;
import org.zstack.header.core.ExceptionSafe;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.longjob.LongJobState;
import org.zstack.header.longjob.LongJobStateEvent;
import org.zstack.header.longjob.LongJobVO;
import org.zstack.header.managementnode.ManagementNodeReadyExtensionPoint;
import org.zstack.header.storage.backup.BackupStorageErrors;
import org.zstack.utils.CollectionUtils;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import javax.persistence.Tuple;
import javax.persistence.TypedQuery;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.zstack.core.Platform.operr;
import static org.zstack.longjob.LongJobUtils.*;

public class LongJobProgressMonitor implements Component, ManagementNodeReadyExtensionPoint {
    private static final CLogger logger = Utils.getLogger(LongJobProgressMonitor.class);

    @Autowired
    private DatabaseFacade dbf;
    @Autowired
    private ThreadFacade thdf;
    @Autowired
    private LongJobManagerImpl impl;

    private Future<Void> monitorTask;
    private LinkedHashMap<String, Enum> errTagMap = new LinkedHashMap();

    @Override
    public boolean start() {
        ProgressGlobalConfig.MONITOR_INTERVAL.installUpdateExtension((oldConfig, newConfig) -> startTaskProgressMonitor());
        return true;
    }

    @Override
    public boolean stop() {
        if (monitorTask != null) {
            monitorTask.cancel(true);
        }
        return true;
    }

    @Override
    public void managementNodeReady() {
        startTaskProgressMonitor();
    }

    private final AtomicBoolean runonce = new AtomicBoolean(true);

    private synchronized void startTaskProgressMonitor() {
        errTagMap.put("ioHung", BackupStorageErrors.STORAGE_IO_ERROR);
        if (monitorTask != null) {
            monitorTask.cancel(true);
        }

        monitorTask = thdf.submitPeriodicTask(new PeriodicTask() {
            @Override
            public TimeUnit getTimeUnit() {
                return TimeUnit.SECONDS;
            }

            @Override
            public long getInterval() {
                return ProgressGlobalConfig.MONITOR_INTERVAL.value(Long.class);
            }

            @Override
            public String getName() {
                return "TaskProgressMonitor";
            }

            @Override
            public void run() {
                checkTaskProgress();
            }
        }, runonce.compareAndSet(true, false) ? 1 : 0);
    }

    @ExceptionSafe
    private void checkTaskProgress() {
        List<Tuple> tuples = getRunningLongJobProgressArguments();
        if (CollectionUtils.isEmpty(tuples)) {
            return;
        }

        tuples.parallelStream().forEach(t -> {
            String uuid = t.get(0, String.class);
            if (uuid.isEmpty()) {
                return;
            }
            String details = t.get(1, String.class);
            if (details.isEmpty()) {
                return;
            }

            LongJobVO job = dbf.findByUuid(uuid, LongJobVO.class);
            if (job.getState() != LongJobState.Running){
                return;
            }

            checkAndErrorJob(uuid, details, errTagMap.keySet());
        });

    }

    protected List<Tuple> getRunningLongJobProgressArguments() {
        String sql = "select job.uuid, progress.opaque from TaskProgressVO progress " +
                "inner join LongJobVO job on job.apiId = progress.apiId " +
                "where job.state =:longJobState and progress.id in " +
                "(select max(id) from TaskProgressVO group by progress.apiId)";
        TypedQuery<Tuple> q = dbf.getEntityManager().createQuery(sql, Tuple.class);
        q.setParameter("longJobState", LongJobState.Running);
        return q.getResultList();
    }

    protected void checkAndErrorJob(String jobUuid, String details, Set<String> tags) {
        tags.forEach(tag -> {
            if (!details.contains(tag)) {
                return;
            }

            changeState(jobUuid, LongJobStateEvent.suspend);
            ErrorCode errorCode = LongJobUtils.setupErr(errTagMap.get(tag), jobUuid,
                    operr(String.format("The job[uuid: %s] status is abnormal, details is %s.", jobUuid, details)));
            changeState(jobUuid, getEventOnError(errorCode), it -> {
                if (Strings.isEmpty(it.getJobResult())) {
                    it.setJobResult(ErrorCode.getJobResult(errorCode));
                }
            });
        });
    }
}
