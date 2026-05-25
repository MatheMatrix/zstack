package org.zstack.test.core.workflow;

import junit.framework.Assert;
import org.junit.Test;
import org.zstack.core.workflow.SimpleFlowChain;
import org.zstack.header.core.workflow.FlowTrigger;
import org.zstack.header.core.workflow.NoRollbackFlow;

import java.util.Map;

/**
 * ZSTAC-84185: Verify error handler is called when done handler throws.
 * Previously the exception was swallowed, causing queue slot leaks.
 */
public class TestDoneHandlerThrowsErrorHandlerCalled {
    boolean errorHandlerCalled;
    boolean doneHandlerThrew;

    @Test
    public void test() {
        new SimpleFlowChain()
                .then(new NoRollbackFlow() {
                    @Override
                    public void run(FlowTrigger chain, Map data) {
                        chain.next();
                    }
                })
                .done(data -> {
                    doneHandlerThrew = true;
                    throw new RuntimeException("simulated done handler failure");
                })
                .error((errCode, data) -> {
                    errorHandlerCalled = true;
                })
                .start();

        Assert.assertTrue(doneHandlerThrew);
        Assert.assertTrue(errorHandlerCalled);
    }
}
