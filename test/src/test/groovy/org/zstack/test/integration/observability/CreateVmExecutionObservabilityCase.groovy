package org.zstack.test.integration.observability

import org.springframework.web.util.UriComponentsBuilder
import org.zstack.core.Platform
import org.zstack.sdk.CreateVmInstanceAction
import org.zstack.sdk.ZSClient
import org.zstack.sdk.ZSConfig
import org.zstack.header.rest.RESTFacade
import org.zstack.test.integration.kvm.Env
import org.zstack.test.integration.kvm.KvmTest
import org.zstack.testlib.EnvSpec
import org.zstack.testlib.SkipTestSuite
import org.zstack.testlib.SubCase
import org.zstack.testlib.WebBeanConstructor

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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
        String vmUuid
        CountDownLatch completed = new CountDownLatch(1)
        CreateVmInstanceAction.Result actionResult

        try {
            ZSClient.configure(new ZSConfig.Builder()
                    .setHostname("localhost")
                    .setPort(WebBeanConstructor.port)
                    .setDefaultPollingInterval(100, TimeUnit.MILLISECONDS)
                    .setDefaultPollingTimeout(10, TimeUnit.MINUTES)
                    .setReadTimeout(10, TimeUnit.MINUTES)
                    .setWriteTimeout(10, TimeUnit.MINUTES)
                    .build())

            CreateVmInstanceAction action = new CreateVmInstanceAction()
            action.apiId = apiUuid
            action.name = "execution-observability-vm-${apiUuid}"
            action.instanceOfferingUuid = env.inventoryByName("instanceOffering").uuid
            action.imageUuid = env.inventoryByName("image1").uuid
            action.l3NetworkUuids = [env.inventoryByName("l3").uuid]
            action.sessionId = adminSession()
            action.call(new org.zstack.sdk.Completion<CreateVmInstanceAction.Result>() {
                @Override
                void complete(CreateVmInstanceAction.Result result) {
                    actionResult = result
                    vmUuid = result?.value?.inventory?.uuid
                    completed.countDown()
                }
            })

            long deadline = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(10)
            int snapshot = 0
            while (!completed.await(250, TimeUnit.MILLISECONDS)) {
                if (System.currentTimeMillis() >= deadline) {
                    assert false: "CreateVmInstance did not complete"
                }
                Map result = queryExecutions([apiUuid: apiUuid])
                if (result.inventories instanceof Collection && result.inventories.size() == 1) {
                    Map execution = result.inventories[0] as Map
                    Map timeline = getExecution(execution.executionUuid as String, "timeline")
                    printExecutionSnapshot(snapshot++, execution, timeline)
                }
            }

            assert completed.await(10, TimeUnit.SECONDS)
            assert actionResult?.error == null
            assert vmUuid

            Map result = queryExecutions([apiUuid: apiUuid])
            assert result.inventories instanceof Collection
            assert result.inventories.size() == 1
            Map execution = result.inventories[0] as Map
            assert execution.executionUuid
            assert execution.apiUuid == apiUuid
            assert execution.trigger?.type == "API"
            assert execution.trigger?.name
            assert execution.state == "SUCCEEDED"
            assert execution.nodeUuid
            assert execution.sourceNodes instanceof Collection

            Map timeline = getExecution(execution.executionUuid as String, "timeline")
            printExecutionSnapshot(snapshot, execution, timeline)
            assert timeline.executionUuid == execution.executionUuid
            assert timeline.events instanceof Collection
            assert timeline.events.every { !it.containsKey("stageId") }
            assert timeline.events.any { it.type == "API_ACCEPTED" }
            def httpEvents = timeline.events.findAll { it.type == "HTTP_REQUEST_SUCCEEDED" }
            assert httpEvents
            assert httpEvents.every {
                it.httpMethod && it.httpUrl && it.httpStatusCode == 200 && it.httpElapsedMs >= 0
            }
        } finally {
            if (vmUuid) {
                destroyVmInstance {
                    uuid = vmUuid
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
        return [("Authorization"): "OAuth " + adminSession()]
    }

    private void printExecutionSnapshot(int number, Map execution, Map timeline) {
        def events = timeline.events.collect { event ->
            [sequence: event.sequence, type: event.type, stageUuid: event.stageUuid,
             stageName: event.stageName, stageKind: event.stageKind,
             parentStageUuid: event.parentStageUuid, messageUuid: event.messageUuid,
             httpMethod: event.httpMethod, httpUrl: event.httpUrl,
             httpStatusCode: event.httpStatusCode, httpElapsedMs: event.httpElapsedMs]
        }
        def active = timeline.activeStages.collect { stage ->
            [stageUuid: stage.stageUuid, parentStageUuid: stage.parentStageUuid,
             name: stage.name, kind: stage.kind, state: stage.state,
             httpMethod: stage.httpMethod, httpUrl: stage.httpUrl]
        }
        println("EXECUTION_QUERY_SNAPSHOT ${number}: " + groovy.json.JsonOutput.toJson([
                executionUuid: execution.executionUuid, state: execution.state,
                elapsedMs: execution.elapsedMs, executionMs: execution.executionMs,
                downstreamWaitMs: execution.downstreamWaitMs, activeStages: active,
                events: events]))
    }
}
