package org.zstack.longjob;

import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.core.progress.ProgressGlobalConfig;
import org.zstack.core.thread.PeriodicTask;
import org.zstack.core.thread.ThreadFacade;
import org.zstack.header.Component;
import org.zstack.header.core.ExceptionSafe;
import org.zstack.header.longjob.LongJobState;
import org.zstack.header.longjob.LongJobStateEvent;
import org.zstack.header.longjob.LongJobVO;
import org.zstack.header.managementnode.ManagementNodeReadyExtensionPoint;
import org.zstack.utils.CollectionUtils;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import javax.persistence.Tuple;
import javax.persistence.TypedQuery;
import java.util.ArrayList;
import java.util.List;
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
        List<String> tags = new ArrayList<>();
        tags.add("io-writes");
        tags.add("io-reads");

        List<Tuple> tuples = getRunningLongJobProgressArguments();
        if (CollectionUtils.isEmpty(tuples)) {
            return;
        }

        tuples.parallelStream().forEach(t -> {
            String uuid = t.get(0, String.class);
            if (uuid.isEmpty()) {
                return;
            }
            String arguments = t.get(1, String.class);
            if (arguments.isEmpty()) {
                return;
            }

            if (!isDetailsContainsAnyTag(arguments, tags)){
                return;
            }

            LongJobVO job = dbf.findByUuid(uuid, LongJobVO.class);
            if (job.getState() != LongJobState.Running){
                return;
            }
            changeState(uuid, LongJobStateEvent.suspend);
            impl.runLongJobCallBack(job, LongJobUtils.interruptedErr(uuid, operr(String.format("The job[uuid: %s] status is abnormal, details is %s.", uuid, arguments))));
        });

    }

    protected List<Tuple> getRunningLongJobProgressArguments() {
        String sql = "select job.uuid, progress.arguments from TaskProgressVO progress " +
                "inner join LongJobVO job on job.apiId = progress.apiId " +
                "where job.state =:longJobState and progress.id in " +
                "(select max(id) from TaskProgressVO group by progress.apiId)";
        TypedQuery<Tuple> q = dbf.getEntityManager().createQuery(sql, Tuple.class);
        q.setParameter("longJobState", LongJobState.Running);
        return q.getResultList();
    }

    protected boolean isDetailsContainsAnyTag(String details, List<String> tags) {
        return tags.stream().anyMatch(details::contains);

    }
}
