package org.zstack.test.core.rest;

import org.junit.Assert;
import org.junit.Test;
import org.zstack.header.rest.RESTFacade;
import org.zstack.header.rest.TimeoutRestTemplate;

/**
 * Phase 1 unit tests: connection pool configuration (R1).
 * Pure JUnit — no Spring context required.
 */
public class TestConnectionPoolConfig {

    @Test
    public void test4ParamCreateRestTemplate_returnsNonNull() {
        // R1: explicit pool params must succeed
        TimeoutRestTemplate tmpl = RESTFacade.createRestTemplate(60000, 3000, 50, 2);
        Assert.assertNotNull("createRestTemplate(4-param) must return non-null template", tmpl);
    }

    @Test
    public void test2ParamCreateRestTemplate_backwardCompat() {
        // old callers delegating to 4-param with (0, 0) must still work
        TimeoutRestTemplate tmpl = RESTFacade.createRestTemplate(60000, 3000);
        Assert.assertNotNull("createRestTemplate(2-param) backward-compat must return non-null", tmpl);
    }

    @Test
    public void testZeroPoolParams_fallsBackToApacheDefaults() {
        // maxTotal=0, maxPerRoute=0 → skip explicit pool; SSL path still applies
        TimeoutRestTemplate tmpl = RESTFacade.createRestTemplate(60000, 3000, 0, 0);
        Assert.assertNotNull("zero-pool fallback must return non-null template", tmpl);
    }

    @Test
    public void testPingPoolParams_smallPerRoute_largeTotalForCluster() {
        // P0 ping pool: maxPerRoute=1 (one ping/host), maxTotal=3000 (full cluster)
        TimeoutRestTemplate tmpl = RESTFacade.createRestTemplate(10000, 3000, 3000, 1);
        Assert.assertNotNull("ping-pool createRestTemplate must return non-null template", tmpl);
    }
}
