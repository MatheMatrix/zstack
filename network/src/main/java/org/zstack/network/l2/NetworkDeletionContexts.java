package org.zstack.network.l2;

import org.zstack.core.cascade.CascadeAction;
import org.zstack.header.network.l2.NetworkDeletionContext;

public final class NetworkDeletionContexts {
    private static final String KEY_PREFIX = NetworkDeletionContext.class.getName() + ":";

    private NetworkDeletionContexts() {
    }

    public static NetworkDeletionContext get(CascadeAction action, String l2NetworkUuid) {
        return action.getContext(KEY_PREFIX + l2NetworkUuid);
    }

    public static void put(CascadeAction action, NetworkDeletionContext context) {
        action.putContext(KEY_PREFIX + context.getL2NetworkUuid(), context);
    }
}
