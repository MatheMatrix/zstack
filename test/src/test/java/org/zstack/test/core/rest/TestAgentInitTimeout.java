package org.zstack.test.core.rest;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.zstack.core.componentloader.ComponentLoader;
import org.zstack.header.exception.CloudRuntimeException;
import org.zstack.header.rest.RESTFacade;
import org.zstack.header.core.workflow.FlowTrigger;
import org.zstack.header.core.workflow.NoRollbackFlow;
import org.zstack.core.workflow.SimpleFlowChain;
import org.zstack.test.WebBeanConstructor;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public class TestAgentInitTimeout {
    WebBeanConstructor wbean;
    ComponentLoader loader;
    RESTFacade restf;
    boolean errorHandlerCalled;
    String errorDetails;

    @Before
    public void setUp() throws Exception {
        wbean = new WebBeanConstructor();
        wbean.addXml("PortalForUnitTest.xml").addXml("AccountManager.xml");
        loader = wbean.build();
        restf = loader.getComponent(RESTFacade.class);
    }

    @Test
    public void testSyncJsonPostTimeoutThrowsException() {
        String url = "http://127.0.0.1:1/nowhere";

        try {
            restf.syncJsonPost(url, "{}", Void.class, TimeUnit.SECONDS, 1);
            Assert.fail("should throw OperationFailureException on timeout");
        } catch (Exception e) {
            errorDetails = e.getMessage();
        }
        Assert.assertNotNull(errorDetails);
        Assert.assertTrue(errorDetails.contains("IO Error") || errorDetails.contains("Connection refused") || errorDetails.contains("connect"));
    }

    @Test
    public void testFlowChainExceptionTriggersErrorHandler() {
        errorHandlerCalled = false;

        new SimpleFlowChain()
                .then(new NoRollbackFlow() {
                    @Override
                    public void run(FlowTrigger chain, Map data) {
                        throw new RuntimeException("simulated syncJsonPost timeout");
                    }
                })
                .error((errCode, data) -> {
                    errorHandlerCalled = true;
                    errorDetails = errCode.getDescription();
                })
                .start();

        Assert.assertTrue("error handler must be called when flow run throws", errorHandlerCalled);
        Assert.assertNotNull(errorDetails);
        Assert.assertTrue(errorDetails.contains("simulated syncJsonPost timeout"));
    }
}
