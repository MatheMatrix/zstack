package org.zstack.test.integration.observability

import org.springframework.http.HttpEntity
import org.zstack.core.Platform
import org.zstack.core.cloudbus.CloudBus
import org.zstack.core.cloudbus.CloudBusCallBack
import org.zstack.header.AbstractService
import org.zstack.header.errorcode.ErrorCode
import org.zstack.header.message.Message
import org.zstack.header.message.MessageReply
import org.zstack.header.message.NeedReplyMessage
import org.zstack.header.rest.AsyncRESTCallback
import org.zstack.header.rest.RESTFacade
import org.zstack.test.integration.ZStackTest
import org.zstack.testlib.EnvSpec
import org.zstack.testlib.SubCase
import org.zstack.testlib.WebBeanConstructor
import org.zstack.utils.URLBuilder

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Integration coverage for HTTP child stages exposed by the execution query API.
 */
class HttpExecutionObservabilityCase extends SubCase {
    EnvSpec env

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
            testHttpRequestIsObservable()
        }
    }

    void testHttpRequestIsObservable() {
        CloudBus bus = bean(CloudBus.class)
        RESTFacade rest = bean(RESTFacade.class)
        String serviceId = "execution-observability-http-${Platform.uuid}"
        String path = "/execution-observability-http-${Platform.uuid}"
        String url = URLBuilder.buildHttpUrl("127.0.0.1", WebBeanConstructor.port, path)
        CountDownLatch requestStarted = new CountDownLatch(1)
        CountDownLatch releaseRequest = new CountDownLatch(1)
        CountDownLatch messageCompleted = new CountDownLatch(1)

        env.simulator(path) { HttpEntity<String> entity ->
            requestStarted.countDown()
            assert releaseRequest.await(10, TimeUnit.SECONDS)
            return "ok"
        }

        AbstractService service = new AbstractService() {
            @Override
            void handleMessage(Message msg) {
                rest.asyncJsonPost(url, "{}", new AsyncRESTCallback(null) {
                    @Override
                    void success(HttpEntity<String> responseEntity) {
                        bus.reply(msg, new MessageReply())
                        messageCompleted.countDown()
                    }

                    @Override
                    void fail(ErrorCode err) {
                        bus.reply(msg, new MessageReply())
                        messageCompleted.countDown()
                    }
                }, TimeUnit.SECONDS, 10)
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

            assert requestStarted.await(10, TimeUnit.SECONDS)
            retryInSecs {
                List result = queryExecution {
                    messageUuid = msg.id
                }
                assert result.size() == 1
                def execution = result[0]
                def timeline = queryExecution {
                    delegate.executionUuid = execution.executionUuid
                    delegate.detail = "timeline"
                }[0]
                assert timeline.events.any { it.type == "HTTP_REQUEST_STARTED" }
                def http = timeline.events.find { it.type == "HTTP_REQUEST_STARTED" }
                assert http.httpMethod == "POST"
                assert http.httpUrl.contains(path)
                assert http.stageName == "HTTP POST ${http.httpUrl}"
                assert http.stageKind == "HTTP"
                assert http.parentStageUuid
                assert timeline.activeStages.any { it.kind == "HTTP" && it.state == "RUNNING" }
            }

            releaseRequest.countDown()
            assert messageCompleted.await(10, TimeUnit.SECONDS)
            retryInSecs {
                def execution = queryExecution {
                    messageUuid = msg.id
                }[0]
                def timeline = queryExecution {
                    delegate.executionUuid = execution.executionUuid
                    delegate.detail = "timeline"
                }[0]
                def http = timeline.events.find { it.type == "HTTP_REQUEST_SUCCEEDED" }
                assert http
                assert http.httpStatusCode == 200
                assert http.httpElapsedMs >= 0
            }
        } finally {
            releaseRequest.countDown()
            bus.unregisterService(service)
        }
    }

    static class ContextFreeMessage extends NeedReplyMessage {
    }
}
