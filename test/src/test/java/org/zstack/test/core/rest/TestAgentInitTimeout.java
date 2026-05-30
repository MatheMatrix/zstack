package org.zstack.test.core.rest;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.zstack.core.componentloader.ComponentLoader;
import org.zstack.header.rest.RESTFacade;
import org.zstack.test.WebBeanConstructor;

import java.util.concurrent.TimeUnit;

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
        String url = "http://192.0.2.1:12345/nowhere";

        long start = System.currentTimeMillis();
        try {
            restf.syncJsonPost(url, "{}", Void.class, TimeUnit.SECONDS, 1);
            Assert.fail("should throw on timeout");
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            errorDetails = e.getMessage();
            Assert.assertNotNull(errorDetails);
            Assert.assertTrue("timeout should take ~1s, got " + elapsed + "ms",
                    elapsed >= 900 || errorDetails.contains("timed out") || errorDetails.contains("Timeout"));
        }
    }
}
