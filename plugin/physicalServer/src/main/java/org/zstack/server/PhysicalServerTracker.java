package org.zstack.server;

import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.core.db.Q;
import org.zstack.core.thread.ThreadFacade;
import org.zstack.core.thread.PeriodicTask;
import org.zstack.header.managementnode.ManagementNodeReadyExtensionPoint;
import org.zstack.header.server.*;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import java.util.concurrent.TimeUnit;

public class PhysicalServerTracker implements ManagementNodeReadyExtensionPoint {
    private static final CLogger logger = Utils.getLogger(PhysicalServerTracker.class);

    @Autowired
    private ThreadFacade thdf;

    private static final long TRACKER_INTERVAL_SECONDS = 60;

    @Override
    public void managementNodeReady() {
        startTracker();
    }

    private void startTracker() {
        thdf.submitPeriodicTask(new PeriodicTask() {
            @Override
            public TimeUnit getTimeUnit() {
                return TimeUnit.SECONDS;
            }

            @Override
            public long getInterval() {
                return TRACKER_INTERVAL_SECONDS;
            }

            @Override
            public String getName() {
                return "physical-server-status-tracker";
            }

            @Override
            public void run() {
                trackServerStatus();
            }
        });
    }

    private void trackServerStatus() {
        long connectingCount = Q.New(PhysicalServerVO.class)
                .eq(PhysicalServerAO_.state, PhysicalServerState.Enabled)
                .eq(PhysicalServerAO_.status, PhysicalServerStatus.Connecting)
                .count();

        if (connectingCount > 0) {
            logger.debug(String.format("PhysicalServerTracker: %d servers in Connecting state, waiting for role provider to confirm connectivity", connectingCount));
        }
    }
}
