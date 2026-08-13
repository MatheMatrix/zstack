package org.zstack.header.network.l2;

/** Additive context for local and projection network creation. */
public class NetworkCreateContext {
    private final NetworkOperationOrigin origin;
    private final ExternalNetworkRef externalRef;
    private final String operationUuid;
    private final String operationStep;

    private NetworkCreateContext(NetworkOperationOrigin origin, ExternalNetworkRef externalRef,
                                 String operationUuid, String operationStep) {
        this.origin = origin;
        this.externalRef = externalRef;
        this.operationUuid = operationUuid;
        this.operationStep = operationStep;
    }

    public static NetworkCreateContext api() {
        return new NetworkCreateContext(NetworkOperationOrigin.API, null, null, null);
    }

    public static NetworkCreateContext cloudCommit(String operationUuid) {
        if (operationUuid == null || operationUuid.isEmpty()) {
            throw new IllegalArgumentException("cloud commit context requires an operation UUID");
        }
        return new NetworkCreateContext(NetworkOperationOrigin.CLOUD_COMMIT, null,
                operationUuid, "APPLY_LOCAL");
    }

    public static NetworkCreateContext projection(NetworkOperationOrigin origin, ExternalNetworkRef ref) {
        return projection(origin, ref, null, null);
    }

    public static NetworkCreateContext projection(NetworkOperationOrigin origin, ExternalNetworkRef ref,
                                                  String operationUuid, String operationStep) {
        if (origin != NetworkOperationOrigin.ZNS_PROJECTION && origin != NetworkOperationOrigin.ZNS_REFRESH) {
            throw new IllegalArgumentException("projection context requires a ZNS projection origin");
        }
        if (ref == null || ref.getResourceUuid() == null || ref.getResourceUuid().isEmpty()) {
            throw new IllegalArgumentException("projection context requires an external resource identity");
        }
        return new NetworkCreateContext(origin, ref, operationUuid, operationStep);
    }

    public NetworkOperationOrigin getOrigin() { return origin; }
    public ExternalNetworkRef getExternalRef() { return externalRef; }
    public String getOperationUuid() { return operationUuid; }
    public String getOperationStep() { return operationStep; }
    public boolean isProjection() { return origin == NetworkOperationOrigin.ZNS_PROJECTION || origin == NetworkOperationOrigin.ZNS_REFRESH; }
    public boolean isRemoteWriteSuppressed() { return isProjection() || origin == NetworkOperationOrigin.CLOUD_COMMIT; }
}
