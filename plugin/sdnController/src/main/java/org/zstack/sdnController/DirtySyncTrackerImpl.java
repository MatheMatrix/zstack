package org.zstack.sdnController;

import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of DirtySyncTracker.
 * Thread-safe via ConcurrentHashMap.
 * Dirty state is ephemeral — lost on MN restart, which is acceptable because
 * the ping-triggered sync provides eventual consistency.
 */
public class DirtySyncTrackerImpl implements DirtySyncTracker {
    private static final CLogger logger = Utils.getLogger(DirtySyncTrackerImpl.class);

    private final Map<String, Boolean> needsSyncMap = new ConcurrentHashMap<>();

    @Override
    public void markNeedSync(String controllerUuid) {
        needsSyncMap.put(controllerUuid, Boolean.TRUE);
        logger.debug(String.format("[sync-tracker] marked controller[uuid:%s] needsSync", controllerUuid));
    }

    @Override
    public boolean needsSync(String controllerUuid) {
        return Boolean.TRUE.equals(needsSyncMap.get(controllerUuid));
    }

    @Override
    public boolean clearNeedSync(String controllerUuid) {
        Boolean prev = needsSyncMap.remove(controllerUuid);
        if (Boolean.TRUE.equals(prev)) {
            logger.info(String.format("[sync-tracker] cleared needsSync for controller[uuid:%s]", controllerUuid));
        }
        return Boolean.TRUE.equals(prev);
    }
}
