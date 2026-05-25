package org.zstack.test.core.workflow;

import org.junit.Assert;
import org.junit.Test;
import org.zstack.core.workflow.SimpleFlowChain;
import org.zstack.header.core.workflow.FlowTrigger;
import org.zstack.header.core.workflow.NoRollbackFlow;

import java.util.Map;

/**
 * Verify that when doneHandler throws after all flows succeed, the error
 * handler is NOT called (avoiding double-release risk on successfully
 * created resources). The chain errorCode is set for diagnostics and
 * afterDone/finally handlers still execute.
 */
public class TestDoneHandlerThrowsErrorHandlerCalled {
    boolean doneHandlerEntered;
    boolean errorHandlerCalled;

    @Test
    public void testErrorHandlerNotCalledWhenDoneHandlerThrows() {
        new SimpleFlowChain()
                .then(new NoRollbackFlow() {
                    @Override
                    public void run(FlowTrigger chain, Map data) {
                        chain.next();
                    }
                })
                .done(data -> {
                    doneHandlerEntered = true;
                    throw new RuntimeException("simulated done handler failure");
                })
                .error((errCode, data) -> {
                    errorHandlerCalled = true;
                })
                .start();

        Assert.assertTrue("done handler must be entered", doneHandlerEntered);
        Assert.assertFalse("error handler must NOT be called when all flows succeeded", errorHandlerCalled);
    }
}
