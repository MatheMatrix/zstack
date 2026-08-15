package org.zstack.core.rest

import org.junit.Test
import org.zstack.header.rest.SyncHttpResponse

import javax.servlet.http.HttpServletResponse

import static org.mockito.Mockito.mock
import static org.mockito.Mockito.verify
import static org.mockito.Mockito.when

class Zcf5485RestStatusContractCase {
    @Test
    void statusAndBodyAreWrittenWithoutCollapsingNon2xxToTransportFailure() {
        HttpServletResponse response = mock(HttpServletResponse.class)
        StringWriter body = new StringWriter()
        when(response.writer).thenReturn(new PrintWriter(body))

        RESTFacadeImpl.writeSyncHttpResponse(
                new SyncHttpResponse(503, '{"condition":"receiver-overloaded"}'), response)

        verify(response).setStatus(503)
        verify(response).setHeader('Content-Type', 'application/json')
        assert body.toString() == '{"condition":"receiver-overloaded"}'
    }

    @Test
    void nullResponseIsAcceptedAsEmptyHttp200() {
        HttpServletResponse response = mock(HttpServletResponse.class)
        RESTFacadeImpl.writeSyncHttpResponse(null, response)
        verify(response).setStatus(200)
    }
}
