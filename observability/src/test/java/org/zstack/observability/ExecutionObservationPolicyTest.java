package org.zstack.observability;

import org.junit.Assert;
import org.junit.Test;
import org.apache.logging.log4j.ThreadContext;
import org.zstack.header.Constants;
import org.zstack.header.core.execution.APIQueryExecutionMsg;
import org.zstack.header.core.execution.ExecutionInventory;
import org.zstack.header.message.NeedReplyMessage;
import org.zstack.header.message.APIMessage;
import org.zstack.header.message.APIEvent;
import org.zstack.header.zone.APICreateZoneMsg;
import org.zstack.header.zone.APIGetZoneMsg;
import org.zstack.header.zone.APIQueryZoneMsg;

import java.util.List;

public class ExecutionObservationPolicyTest {
    private final ExecutionObservationPolicy policy = new ExecutionObservationPolicy();

    @Test
    public void queryAndGetApisAreNotRootExecutions() {
        Assert.assertFalse(policy.shouldObserveApi(new APIQueryExecutionMsg()));
        Assert.assertFalse(policy.shouldObserveApi(new APIQueryZoneMsg()));
        Assert.assertFalse(policy.shouldObserveApi(new APIGetZoneMsg()));
    }

    @Test
    public void mutatingApisRemainRootExecutions() {
        APIMessage create = new APICreateZoneMsg();
        Assert.assertTrue(policy.shouldObserveApi(create));
    }

    @Test
    public void ignoredApiContextDoesNotCreateChildExecution() {
        ExecutionObservabilityFacadeImpl recorder = new ExecutionObservabilityFacadeImpl();
        APIQueryZoneMsg queryApi = new APIQueryZoneMsg();

        recorder.recordApiRequest(queryApi);
        ThreadContext.put(Constants.THREAD_CONTEXT_API, queryApi.getId());
        recorder.recordMessageDelivery(new ChildMessage());

        APIQueryExecutionMsg query = new APIQueryExecutionMsg();
        query.setTriggerName(ChildMessage.class.getName());
        List<ExecutionInventory> inventories = recorder.queryLocal(query);
        Assert.assertTrue(inventories.isEmpty());
        ThreadContext.clearAll();
    }

    @Test
    public void readOnlyApisDoNotCreateRootExecutions() {
        ExecutionObservabilityFacadeImpl recorder = new ExecutionObservabilityFacadeImpl();
        APIMessage queryApi = new APIQueryZoneMsg();
        APIMessage getApi = new APIGetZoneMsg();

        recorder.recordApiRequest(queryApi);
        recorder.recordApiResponse(new APIEvent(queryApi.getId()));
        recorder.recordApiRequest(getApi);
        recorder.recordApiResponse(new APIEvent(getApi.getId()));

        APIQueryExecutionMsg query = new APIQueryExecutionMsg();
        Assert.assertTrue(recorder.queryLocal(query).isEmpty());
        ThreadContext.clearAll();
    }

    private static class ChildMessage extends NeedReplyMessage {
    }
}
