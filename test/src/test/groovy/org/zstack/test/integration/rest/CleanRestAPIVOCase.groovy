package org.zstack.test.integration.rest

import com.google.gson.JsonParser
import org.zstack.core.Platform
import org.zstack.core.rest.RESTApiFacadeImpl
import org.zstack.core.rest.RESTApiGlobalProperty
import org.zstack.core.rest.RESTApiDecoder
import org.zstack.header.log.MaskSensitiveInfo
import org.zstack.header.log.NoLogging
import org.zstack.header.message.APIEvent
import org.zstack.header.rest.RestAPIState
import org.zstack.header.rest.RestAPIVO
import org.zstack.test.integration.ZStackTest
import org.zstack.testlib.EnvSpec
import org.zstack.testlib.SubCase

import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.EntityTransaction
import javax.persistence.Query
import java.nio.charset.StandardCharsets
import java.sql.Timestamp
import java.util.concurrent.TimeUnit;

class CleanRestAPIVOCase extends SubCase{
    EnvSpec env
    RESTApiFacadeImpl restApiImpl
    EntityManagerFactory entityManagerFactory
    EntityManager mgr
    EntityTransaction tran

    @Override
    void clean() {
        env.delete()
    }

    @Override
    void setup() {
        useSpring(ZStackTest.springSpec)
        spring {
            include("RESTFacade.xml")
        }
    }

    @Override
    void environment() {
        env = env {
        }
    }

    @Override
    void test() {
        env.create{
            restApiImpl = bean(RESTApiFacadeImpl.class)
            entityManagerFactory = restApiImpl.entityManagerFactory
            mgr = entityManagerFactory.createEntityManager()
            tran = mgr.getTransaction()

            testLongRestAPIResultKeepsCompleteJson()
            testLongMultibyteRestAPIResultTruncatesByUtf8Bytes()
            testAsyncRestAPIResultMasksSensitiveEvent()
            testCleanRestAPIVO()
            testNotCleanRestAPIVO()
            deleteRestAPIVO()
        }
    }

    void testLongRestAPIResultKeepsCompleteJson() {
        String apiId = Platform.uuid
        RestAPIVO vo = new RestAPIVO()
        vo.setUuid(apiId)
        vo.setApiMessageName("org.zstack.test.integration.rest.LongRestAPIEvent")
        vo.setState(RestAPIState.Processing)
        tran.begin()
        mgr.persist(vo)
        mgr.flush()
        mgr.refresh(vo)
        tran.commit()

        String payload = "x" * 70000
        LongRestAPIEvent event = new LongRestAPIEvent(apiId)
        event.largeResult = payload

        def processingRequests = RESTApiFacadeImpl.class.getDeclaredField("processingRequests")
        processingRequests.setAccessible(true)
        processingRequests.get(restApiImpl).add(apiId)

        restApiImpl.handleEvent(event)

        String result = restApiImpl.getResult(apiId).result
        assert result.length() > 64000
        assert JsonParser.parseString(result).asJsonObject.get(LongRestAPIEvent.class.name).asJsonObject.get("largeResult").asString == payload
        assert ((LongRestAPIEvent) RESTApiDecoder.loads(result)).largeResult == payload

        tran.begin()
        mgr.remove(mgr.find(RestAPIVO.class, apiId))
        tran.commit()
    }

    void testLongMultibyteRestAPIResultTruncatesByUtf8Bytes() {
        String apiId = Platform.uuid
        LongRestAPIEvent event = new LongRestAPIEvent(apiId)
        event.largeResult = "结果" * 300

        String fullResult = RESTApiDecoder.dump(event)
        int maxBytes = 512
        assert fullResult.contains("结果")
        assert fullResult.length() < fullResult.getBytes(StandardCharsets.UTF_8).length
        assert fullResult.getBytes(StandardCharsets.UTF_8).length > maxBytes

        def getApiResult = RESTApiFacadeImpl.class.getDeclaredMethod("getApiResult", APIEvent.class, Integer.TYPE)
        getApiResult.setAccessible(true)
        String truncated = getApiResult.invoke(null, event, maxBytes)

        assert truncated.length() < fullResult.length()
        assert truncated == fullResult.substring(0, truncated.length())
        assert truncated.getBytes(StandardCharsets.UTF_8).length <= maxBytes
        assert new String(truncated.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8) == truncated
        assert !truncated.contains("\uFFFD")
    }

    void testAsyncRestAPIResultMasksSensitiveEvent() {
        String apiId = Platform.uuid
        RestAPIVO vo = new RestAPIVO()
        vo.setUuid(apiId)
        vo.setApiMessageName("org.zstack.test.integration.rest.MaskedRestAPIEvent")
        vo.setState(RestAPIState.Processing)
        tran.begin()
        mgr.persist(vo)
        mgr.flush()
        mgr.refresh(vo)
        tran.commit()

        MaskedRestAPIEvent event = new MaskedRestAPIEvent(apiId)
        event.secretResult = "sensitive-result-value"
        event.visibleResult = "visible-result-value"

        def processingRequests = RESTApiFacadeImpl.class.getDeclaredField("processingRequests")
        processingRequests.setAccessible(true)
        processingRequests.get(restApiImpl).add(apiId)

        restApiImpl.handleEvent(event)

        String result = restApiImpl.getResult(apiId).result
        def json = JsonParser.parseString(result).asJsonObject.get(MaskedRestAPIEvent.class.name).asJsonObject
        String secretResult = json.get("secretResult").asString
        String visibleResult = json.get("visibleResult").asString
        assert secretResult == "*****" :
                "异步 REST API result 未脱敏 secretResult: expected=***** actual=${secretResult}"
        assert visibleResult == "visible-result-value" :
                "异步 REST API result 错误修改 visibleResult: expected=visible-result-value actual=${visibleResult}"
        assert !result.contains("sensitive-result-value") :
                "异步 REST API result 仍包含原始敏感值 sensitive-result-value"

        tran.begin()
        mgr.remove(mgr.find(RestAPIVO.class, apiId))
        tran.commit()
    }

    void testCleanRestAPIVO() {
        for (int i = 0; i < 1200; i++){
            RestAPIVO vo = new RestAPIVO()
            vo.setUuid(String.valueOf(i))
            vo.setApiMessageName("org.zstack.core.rest.DeleteRestAPpiVOMsg")
            vo.setState(RestAPIState.Processing)
            vo.setLastOpDate(new Timestamp(System.currentTimeMillis() - (1000 * 60 * 60 * 24)))
            tran.begin()
            mgr.persist(vo)
            mgr.flush()
            mgr.refresh(vo)
            tran.commit()
        }

        for (int i = 1200; i < 1400; i++){
            RestAPIVO vo = new RestAPIVO()
            vo.setUuid(String.valueOf(i))
            vo.setApiMessageName("org.zstack.core.rest.DeleteRestAPpiVOMsg")
            vo.setState(RestAPIState.Processing)
            vo.setLastOpDate(new Timestamp(System.currentTimeMillis()))
            tran.begin()
            mgr.persist(vo)
            mgr.flush()
            mgr.refresh(vo)
            tran.commit()
        }

        RESTApiGlobalProperty.CLEAN_RESTAPIVO_DELAY = 1
        RESTApiGlobalProperty.RESTAPIVO_RETENTION_DAY = 1
        restApiImpl.refreshIntervalClean()

        retryInSecs {
            tran.begin()
            String sql = "select count(*) from RestAPIVO"
            Query query = mgr.createQuery(sql)
            List result = query.resultList
            tran.commit()
            assert result.get(0) == 200
        }
    }

    void testNotCleanRestAPIVO() {
        RESTApiGlobalProperty.RESTAPIVO_RETENTION_DAY = -1
        restApiImpl.refreshIntervalClean()
        TimeUnit.SECONDS.sleep(3)
        retryInSecs {
            tran.begin()
            String sql = "select count(*) from RestAPIVO"
            Query query = mgr.createQuery(sql)
            List result = query.resultList
            tran.commit()
            assert result.get(0) == 200
        }
    }

    void deleteRestAPIVO() {
            tran.begin()
            String sql = "delete from RestAPIVO"
            Query query = mgr.createQuery(sql)
            int ret = query.executeUpdate()
            tran.commit()
            assert ret == 200
        mgr.close()
    }
}

class LongRestAPIEvent extends APIEvent {
    String largeResult

    LongRestAPIEvent() {
        super()
    }

    LongRestAPIEvent(String apiId) {
        super(apiId)
    }
}

@MaskSensitiveInfo
class MaskedRestAPIEvent extends APIEvent {
    @NoLogging
    String secretResult
    String visibleResult

    MaskedRestAPIEvent() {
        super()
    }

    MaskedRestAPIEvent(String apiId) {
        super(apiId)
    }
}
