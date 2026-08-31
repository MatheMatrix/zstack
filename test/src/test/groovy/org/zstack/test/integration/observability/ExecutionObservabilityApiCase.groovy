package org.zstack.test.integration.observability

import org.springframework.web.util.UriComponentsBuilder
import org.zstack.core.Platform
import org.zstack.core.cloudbus.CloudBus
import org.zstack.core.cloudbus.CloudBusCallBack
import org.zstack.core.thread.ThreadFacade
import org.zstack.header.AbstractService
import org.zstack.header.message.Message
import org.zstack.header.message.MessageReply
import org.zstack.header.message.NeedReplyMessage
import org.zstack.header.rest.RESTFacade
import org.zstack.test.integration.ZStackTest
import org.zstack.testlib.EnvSpec
import org.zstack.testlib.SkipTestSuite
import org.zstack.testlib.SubCase

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * Integration cases for the execution observability query API. The suite is
 * kept opt-in until the CI integration profile enrolls the new package.
 */
@SkipTestSuite
class ExecutionObservabilityApiCase extends SubCase {
    EnvSpec env
    RESTFacade restf

    @Override
    void clean() {
        env?.delete()
    }

    @Override
    void setup() {
        useSpring(ZStackTest.springSpec)
    }

    @Override
    void environment() {
        env = env {}
    }

    @Override
    void test() {
        env.create {
            restf = bean(RESTFacade.class)

            testApiExecutionLookup()
            testApiExecutionTimelineIsReadOnly()
            testReadOnlyApiIsNotObserved()
            testScheduledTaskCreatesExecution()
            testContextFreeMessageCreatesExecution()
            testContextFreeMessageTimeoutIsObservable()
            testQueryRejectsAmbiguousSelectors()
        }
    }

    void testApiExecutionLookup() {
        String requestApiUuid = Platform.uuid
        String zoneUuid = Platform.uuid

        createZone {
            uuid = zoneUuid
            name = "execution-observability-${requestApiUuid}"
            apiId = requestApiUuid
            sessionId = adminSession()
        }

        try {
            Map summary = findSingleByApiUuid(requestApiUuid)
            assert summary.executionUuid
            assert summary.trigger.type == "API"
            assert summary.trigger.apiUuid == requestApiUuid
            assert summary.state
            assert summary.nodeUuid
            assert summary.observedAt

            Map detail = getExecution(summary.executionUuid as String, "summary")
            assert detail.executionUuid == summary.executionUuid
            assert detail.activeStages instanceof Collection
            assert detail.activeStages.every { it.containsKey("stageUuid") && !it.containsKey("stageId") }
            assert detail.containsKey("partial")
            assert detail.containsKey("sourceNodes")
        } finally {
            deleteZone {
                uuid = zoneUuid
                sessionId = adminSession()
            }
        }
    }

    void testApiExecutionTimelineIsReadOnly() {
        String requestApiUuid = Platform.uuid
        String zoneUuid = Platform.uuid

        createZone {
            uuid = zoneUuid
            name = "execution-observability-timeline-${requestApiUuid}"
            apiId = requestApiUuid
            sessionId = adminSession()
        }

        try {
            Map summaryBefore = findSingleByApiUuid(requestApiUuid)
            Map timeline = getExecution(summaryBefore.executionUuid as String, "timeline")
            assert timeline.executionUuid == summaryBefore.executionUuid
            assert timeline.events instanceof Collection
            assert timeline.events.every { !it.containsKey("stageId") }

            Map summaryAfter = getExecution(summaryBefore.executionUuid as String, "summary")
            assert summaryAfter.state == summaryBefore.state
            assert summaryAfter.executionUuid == summaryBefore.executionUuid
        } finally {
            deleteZone {
                uuid = zoneUuid
                sessionId = adminSession()
            }
        }
    }

    void testReadOnlyApiIsNotObserved() {
        String apiUuid = Platform.uuid
        String zoneUuid = Platform.uuid

        createZone {
            uuid = zoneUuid
            name = "execution-observability-read-only-${apiUuid}"
            apiId = apiUuid
            sessionId = adminSession()
        }

        try {
            assert queryZone { conditions = ["uuid=${zoneUuid}"] }
            retryInSecs {
                Map result = searchExecutions([
                        triggerName: "org.zstack.header.zone.APIQueryZoneMsg"
                ])
                assert result.inventories instanceof Collection
                assert result.inventories.empty
            }
        } finally {
            deleteZone {
                uuid = zoneUuid
                sessionId = adminSession()
            }
        }
    }

    void testScheduledTaskCreatesExecution() {
        ThreadFacade thdf = bean(ThreadFacade.class)
        String taskName = "execution-observability-periodic-${Platform.uuid}"
        CountDownLatch ran = new CountDownLatch(1)
        long startedAfter = System.currentTimeMillis()

        Future<Void> future = thdf.submitPeriodicTask(new ObservabilityPeriodicTaskCase(taskName, ran))

        try {
            assert ran.await(10, TimeUnit.SECONDS)

            retryInSecs {
                Map result = searchExecutions([
                        triggerType : "SCHEDULED_TASK",
                        triggerName : taskName,
                        startedAfter: startedAfter
                ])
                assert result.inventories instanceof Collection
                assert result.inventories.any { it.trigger?.name == taskName }
            }
        } finally {
            future.cancel(true)
        }
    }

    void testContextFreeMessageCreatesExecution() {
        CloudBus bus = bean(CloudBus.class)
        String serviceId = "execution-observability-message-${Platform.uuid}"
        CountDownLatch handled = new CountDownLatch(1)

        AbstractService service = new AbstractService() {
            @Override
            void handleMessage(Message msg) {
                bus.reply(msg, new MessageReply())
                handled.countDown()
            }

            @Override
            String getId() {
                return bus.makeLocalServiceId(serviceId)
            }

            @Override
            boolean start() {
                return true
            }

            @Override
            boolean stop() {
                return true
            }
        }

        bus.registerService(service)
        try {
            ContextFreeMessage msg = new ContextFreeMessage()
            bus.makeLocalServiceId(msg, serviceId)
            bus.send(msg, new CloudBusCallBack(null) {
                @Override
                void run(MessageReply reply) {
                    assert reply.success
                }
            })

            assert handled.await(10, TimeUnit.SECONDS)

            retryInSecs {
                Map result = searchExecutions([messageUuid: msg.id])
                assert result.inventories.size() == 1
                assert result.inventories[0].trigger.type == "MESSAGE"
                assert result.inventories[0].rootMessageUuid == msg.id
            }
        } finally {
            bus.unregisterService(service)
        }
    }

    void testQueryRejectsAmbiguousSelectors() {
        Throwable error
        try {
            searchExecutions([
                    apiUuid    : Platform.uuid,
                    messageUuid: Platform.uuid
            ])
            error = null
        } catch (Throwable t) {
            error = t
        }

        assert error != null
        assert error.message
    }

    void testContextFreeMessageTimeoutIsObservable() {
        CloudBus bus = bean(CloudBus.class)
        String serviceId = "execution-observability-timeout-${Platform.uuid}"
        CountDownLatch received = new CountDownLatch(1)

        AbstractService service = new AbstractService() {
            @Override
            void handleMessage(Message msg) {
                received.countDown()
                // Intentionally do not reply: the CloudBus timeout path should
                // close the execution as TIMEOUT without business instrumentation.
            }

            @Override
            String getId() {
                return bus.makeLocalServiceId(serviceId)
            }

            @Override
            boolean start() {
                return true
            }

            @Override
            boolean stop() {
                return true
            }
        }

        bus.registerService(service)
        try {
            ContextFreeMessage msg = new ContextFreeMessage()
            msg.timeout = 100
            bus.makeLocalServiceId(msg, serviceId)
            bus.send(msg, new CloudBusCallBack(null) {
                @Override
                void run(MessageReply reply) {
                    assert !reply.success
                }
            })

            assert received.await(10, TimeUnit.SECONDS)
            retryInSecs {
                Map result = searchExecutions([messageUuid: msg.id, state: "TIMEOUT"])
                assert result.inventories.size() == 1
                assert result.inventories[0].state == "TIMEOUT"
            }
        } finally {
            bus.unregisterService(service)
        }
    }

    private Map findSingleByApiUuid(String apiUuid) {
        Map result = searchExecutions([apiUuid: apiUuid])
        assert result.inventories instanceof Collection
        assert result.inventories.size() == 1
        return result.inventories[0] as Map
    }

    private Map searchExecutions(Map<String, Object> filters) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(restf.makeUrl("/v1/executions"))
        filters.each { String key, Object value ->
            if (value != null) {
                builder.queryParam(key, value)
            }
        }

        String url = builder.build().toUriString()
        return restf.syncJsonGet(url, "", authHeaders(), LinkedHashMap.class)
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

    static class ContextFreeMessage extends NeedReplyMessage {
    }
}
