package org.zstack.test.unittest.storage.zbs

import org.junit.Test
import org.zstack.header.errorcode.OperationFailureException
import org.zstack.header.rest.RESTFacade
import org.zstack.storage.zbs.MdsInfo
import org.zstack.storage.zbs.ZbsMdsBase
import org.zstack.storage.zbs.ZbsPrimaryStorageMdsBase

import java.util.concurrent.TimeUnit

import static org.zstack.core.Platform.operr

class ZbsMdsBaseCase {
    @Test
    void testSyncCallReturnsFailedResponseAfterRestException() {
        def mds = new TestMdsBase(mdsInfo("127.0.1.1"))

        def ret = mds.syncCall("/volume/clients", new ZbsMdsBase.AgentCommand(), TestResponse.class, TimeUnit.SECONDS, 1)

        assert !ret.isSuccess() : "ZBS syncCall should convert REST exception to failed response"
        assert ret.error.contains("simulated MDS network failure") : "ZBS syncCall returned wrong error: ${ret.error}"
    }

    private static MdsInfo mdsInfo(String addr) {
        def info = new MdsInfo()
        info.addr = addr
        info
    }

    private static class TestMdsBase extends ZbsPrimaryStorageMdsBase {
        TestMdsBase(MdsInfo mdsInfo) {
            super(mdsInfo)

            restf = [
                    syncJsonPost: { String url, Object body, Class retClass, TimeUnit unit, long timeout ->
                        throw new OperationFailureException(operr("TEST.1000", "simulated MDS network failure"))
                    }
            ] as RESTFacade
        }
    }

    private static class TestResponse extends ZbsMdsBase.AgentResponse {
    }
}
