package org.zstack.test.integration.observability

import org.springframework.web.util.UriComponentsBuilder
import org.zstack.core.Platform
import org.zstack.header.rest.RESTFacade
import org.zstack.sdk.VmInstanceInventory
import org.zstack.test.integration.kvm.Env
import org.zstack.test.integration.kvm.KvmTest
import org.zstack.testlib.EnvSpec
import org.zstack.testlib.SkipTestSuite
import org.zstack.testlib.SubCase

/**
 * Integration case for observing a real CreateVmInstance API. The suite is
 * kept opt-in until the CI integration profile enrolls the new package.
 */
@SkipTestSuite
class CreateVmExecutionObservabilityCase extends SubCase {
    EnvSpec env
    RESTFacade restf

    @Override
    void clean() {
        env?.delete()
    }

    @Override
    void setup() {
        useSpring(KvmTest.springSpec)
    }

    @Override
    void environment() {
        env = Env.noVmEnv()
    }

    @Override
    void test() {
        env.create {
            restf = bean(RESTFacade.class)
            testCreateVmExecutionLookup()
        }
    }

    void testCreateVmExecutionLookup() {
        String apiUuid = Platform.uuid
        VmInstanceInventory vm

        try {
            vm = createVmInstance {
                apiId = apiUuid
                name = "execution-observability-vm-${apiUuid}"
                instanceOfferingUuid = env.inventoryByName("instanceOffering").uuid
                imageUuid = env.inventoryByName("image1").uuid
                l3NetworkUuids = [env.inventoryByName("l3").uuid]
                sessionId = adminSession()
            }
            assert vm?.uuid

            retryInSecs {
                Map result = queryExecutions([apiUuid: apiUuid])
                assert result.inventories instanceof Collection
                assert result.inventories.size() == 1

                Map execution = result.inventories[0] as Map
                assert execution.executionUuid
                assert execution.apiUuid == apiUuid
                assert execution.trigger?.type == "API"
                assert execution.trigger?.name
                assert execution.state
                assert execution.nodeUuid
                assert execution.sourceNodes instanceof Collection

                Map timeline = getExecution(execution.executionUuid as String, "timeline")
                assert timeline.executionUuid == execution.executionUuid
                assert timeline.events instanceof Collection
                assert timeline.events.every { !it.containsKey("stageId") }
                assert timeline.events.any { it.type == "API_ACCEPTED" }
            }
        } finally {
            if (vm?.uuid) {
                deleteVmInstance {
                    uuid = vm.uuid
                    sessionId = adminSession()
                }
            }
        }
    }

    private Map queryExecutions(Map<String, Object> filters) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(restf.makeUrl("/v1/executions"))
        filters.each { String key, Object value ->
            if (value != null) {
                builder.queryParam(key, value)
            }
        }

        return restf.syncJsonGet(builder.build().toUriString(), "", authHeaders(), LinkedHashMap.class)
    }

    private Map getExecution(String executionUuid, String detail) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(
                restf.makeUrl("/v1/executions/${executionUuid}"))
        builder.queryParam("detail", detail)
        Map result = restf.syncJsonGet(builder.build().toUriString(), "", authHeaders(), LinkedHashMap.class)
        assert result.inventories instanceof Collection
        assert result.inventories.size() == 1
        return result.inventories[0] as Map
    }

    private Map<String, String> authHeaders() {
        return [("Authorization"): "OAuth ${adminSession()}"]
    }
}
