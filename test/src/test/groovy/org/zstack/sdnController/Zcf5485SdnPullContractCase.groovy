package org.zstack.sdnController

import org.junit.Test

class Zcf5485SdnPullContractCase {
    @Test
    void resourceUuidsAreValidatedAndDeduplicatedBeforeRouting() {
        String znsUuid = 'dc9905ec-e6fc-4122-8861-7f56decafcaa'
        String cloudUuid = 'dc9905ece6fc412288617f56decafcaa'

        assert SdnControllerApiInterceptor.normalizeResourceUuids(
                [znsUuid, znsUuid, cloudUuid]) == [znsUuid, cloudUuid]
        assert SdnControllerApiInterceptor.normalizeResourceUuids([]).isEmpty()
        assert SdnControllerApiInterceptor.normalizeResourceUuids(null) == null

        try {
            SdnControllerApiInterceptor.normalizeResourceUuids(['not-a-uuid'])
            assert false: 'invalid resource UUID must be rejected'
        } catch (IllegalArgumentException ignored) {
        }
    }

    @Test
    void duplicateUuidsDoNotConsumeTheOneHundredResourceLimit() {
        String uuid = 'dc9905ec-e6fc-4122-8861-7f56decafcaa'
        assert SdnControllerApiInterceptor.normalizeResourceUuids(
                Collections.nCopies(101, uuid)) == [uuid]
    }
}
