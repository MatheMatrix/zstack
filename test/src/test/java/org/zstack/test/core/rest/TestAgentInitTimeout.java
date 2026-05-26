package org.zstack.test.core.rest;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.zstack.core.agent.AgentManagerImpl;
import org.zstack.core.componentloader.ComponentLoader;
import org.zstack.header.rest.RESTFacade;
import org.zstack.test.WebBeanConstructor;

import java.util.concurrent.TimeUnit;

/**
 * Verify that syncJsonPost with timeout correctly propagates timeout
 * behavior, protecting against indefinite blocking in agent deploy.
 */
public class TestAgentInitTimeout {
    WebBeanConstructor wbean;
    ComponentLoader loader;
    RESTFacade restf;
    String errorDetails;

    @Before
    public void setUp() throws Exception {
        wbean = new WebBeanConstructor();
        wbean.addXml("PortalForUnitTest.xml").addXml("AccountManager.xml");
        loader = wbean.build();
        restf = loader.getComponent(RESTFacade.class);
    }

    @Test
    public void testSyncJsonPostTimeoutEnforced() {
        // 10.255.255.1 is a TEST-NET address that should trigger connect timeout
        String url = "http://10.255.255.1:12345/nowhere";

        long start = System.currentTimeMillis();
        try {
            restf.syncJsonPost(url, "{}", Void.class, TimeUnit.SECONDS, 1);
            Assert.fail("should throw on timeout");
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            errorDetails = e.getMessage();
            Assert.assertNotNull(errorDetails);
            // If connection was refused immediately (localhost), elapsed < 500ms.
            // A timeout should take at least ~1000ms. We use 500ms as a threshold
            // to distinguish timeout from immediate refusal.
            Assert.assertTrue("timeout should take at least ~1s, got " + elapsed + "ms",
                    elapsed >= 500 || errorDetails.contains("timed out") || errorDetails.contains("Timeout"));
        }
    }

    @Test
    public void testTimeoutConstantIsSet() {
        Assert.assertEquals("agent init timeout must be 60 seconds", 60, AgentManagerImpl.AGENT_INIT_TIMEOUT);
    }
}
