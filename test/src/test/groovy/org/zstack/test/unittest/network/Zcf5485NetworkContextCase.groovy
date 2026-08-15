package org.zstack.test.unittest.network

import org.junit.Test
import org.zstack.header.network.NetworkConfigMutation
import org.zstack.header.network.l2.ExternalNetworkRef
import org.zstack.header.network.l2.NetworkCreateContext
import org.zstack.header.network.l2.NetworkDeletionContext
import org.zstack.header.network.l2.NetworkOperationOrigin

class Zcf5485NetworkContextCase {
    @Test
    void creationContextCarriesCommitAndContinuationEvidence() {
        def api = NetworkCreateContext.api()
        assert api.origin == NetworkOperationOrigin.API
        assert !api.remoteCommitted
        assert api.expectedConfigVersion == null
        assert api.continuationStep == null

        def committed = NetworkCreateContext.cloudCommit('operation-1', 7L, 'APPLY_LOCAL')
        assert committed.origin == NetworkOperationOrigin.CLOUD_COMMIT
        assert committed.remoteCommitted
        assert committed.expectedConfigVersion == 7L
        assert committed.continuationStep == 'APPLY_LOCAL'
        assert committed.operationStep == committed.continuationStep

        def projected = NetworkCreateContext.projection(
                NetworkOperationOrigin.ZNS_REFRESH,
                new ExternalNetworkRef('segment-1', 'account-1'),
                'operation-2', 9L, 'REPAIR_LOCAL')
        assert projected.projection
        assert projected.remoteWriteSuppressed
        assert projected.remoteCommitted
        assert projected.expectedConfigVersion == 9L
        assert projected.continuationStep == 'REPAIR_LOCAL'
    }

    @Test
    void deletionContextCarriesCommitAndContinuationEvidence() {
        def context = new NetworkDeletionContext(
                NetworkDeletionContext.Origin.WHOLE_L2_SEGMENT_DELETE,
                'operation-3', 'l2-1', 'L2NetworkVO')
        context.expectedConfigVersion = 11L
        context.remoteCommitted = true
        context.continuationStep = 'DELETE_LOCAL'

        assert context.expectedConfigVersion == 11L
        assert context.remoteCommitted
        assert context.continuationStep == 'DELETE_LOCAL'
    }

    @Test
    void metadataMutationCarriesExactBeforeLocalCommitTarget() {
        def mutation = NetworkConfigMutation.metadata(
                'l2-1', NetworkOperationOrigin.API, 'operation-4', 'account-1',
                'new-name', 'new-description')

        assert mutation.kind == NetworkConfigMutation.Kind.L2_METADATA
        assert mutation.l2Uuid == 'l2-1'
        assert mutation.operationUuid == 'operation-4'
        assert mutation.accountUuid == 'account-1'
        assert mutation.metadata.name == 'new-name'
        assert mutation.metadata.description == 'new-description'
    }
}
