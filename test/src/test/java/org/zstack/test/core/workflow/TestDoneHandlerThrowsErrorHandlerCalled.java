package org.zstack.test.core.workflow;

import org.junit.Assert;
import org.junit.Test;
import org.zstack.core.workflow.SimpleFlowChain;
import org.zstack.header.core.workflow.FlowTrigger;
import org.zstack.header.core.workflow.NoRollbackFlow;

import java.util.Map;

public class TestDoneHandlerThrowsErrorHandlerCalled {
    boolean errorHandlerCalled;
    boolean doneHandlerEntered;

    @Test
    public void testErrorHandlerCalledWhenDoneHandlerThrows() {
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

        Assert.assertTrue(doneHandlerEntered);
        Assert.assertTrue(errorHandlerCalled);
    }

    @Test
    public void testDoneHandlerErrorHandlerNotCalledTwice() {
        final boolean[] errorHandlerCalls = {false};
        new SimpleFlowChain()
                .then(new NoRollbackFlow() {
                    @Override
                    public void run(FlowTrigger chain, Map data) {
                        chain.next();
                    }
                })
                .done(data -> {
                    throw new RuntimeException("simulated done handler failure");
                })
                .error((errCode, data) -> {
                    Assert.assertFalse("error handler must not be called twice", errorHandlerCalls[0]);
                    errorHandlerCalls[0] = true;
                })
                .start();

        Assert.assertTrue(errorHandlerCalls[0]);
    }
}
