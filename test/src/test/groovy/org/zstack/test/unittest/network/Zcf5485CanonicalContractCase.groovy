package org.zstack.test.unittest.network

import groovy.json.JsonSlurper
import org.junit.Test

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

class Zcf5485CanonicalContractCase {
    @Test
    void coreConsumesCanonicalSegmentCloudContract() {
        File fixture = new File('src/test/resources/zns/zcf5485/segment-cloud-contract.json')
        File checksums = new File('src/test/resources/zns/zcf5485/SHA256SUMS')
        assert fixture.isFile()
        assert checksums.isFile()
        assert sha256(fixture.bytes) == checksums.text.trim().split(/\s+/)[0]

        def contract = new JsonSlurper().parse(fixture)
        assert contract.contract == 'ZCF-5485'
        assert contract.revision == 'zcf5485-final-v3'
        assert contract.version == 3

        assert contract.cloud_api.pull.message == 'APIPullSdnControllerMsg'
        assert contract.cloud_api.pull.event == 'APIPullSdnControllerEvent'
        assert contract.cloud_api.pull.supported_resource_types == ['Segment', 'TenantRouter']
        assert contract.cloud_api.pull.resource_uuid_limit == 100
        assert contract.cloud_api.pull.full_refresh_when_uuids_empty
        assert contract.cloud_api.projection_query.side_effect_free
        assert contract.cloud_api.projection_query.required_inventory_fields.containsAll([
                'znsSegmentUuid', 'sdnControllerUuid', 'zoneUuid', 'l2NetworkUuid',
                'l2Network', 'state', 'createDate', 'lastOpDate'
        ])

        def digest = contract.digest_vectors[0]
        assert sha256(digest.canonical_request.getBytes(StandardCharsets.UTF_8)) == digest.sha256
        assert contract.child_operation_uuid_vectors*.derived_uuid_v3 == [
                '07c1e718-28c8-3e0a-b2cc-44212e14c3ad',
                '814a7757-32b3-3782-9fff-895abe559f06'
        ]
    }

    private static String sha256(byte[] bytes) {
        MessageDigest.getInstance('SHA-256').digest(bytes).collect {
            String.format('%02x', it & 0xff)
        }.join()
    }
}
