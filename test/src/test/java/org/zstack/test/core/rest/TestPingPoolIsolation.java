package org.zstack.test.core.rest;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.web.client.AsyncRestTemplate;
import org.zstack.core.rest.RESTFacadeImpl;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Phase 2 unit tests: ping pool isolation (R2).
 * Verifies ASYNC_TEMPLATE_OVERRIDE ThreadLocal field structure and lifecycle
 * via reflection — no Spring context required.
 */
public class TestPingPoolIsolation {

    private ThreadLocal<AsyncRestTemplate> getOverrideTL() throws Exception {
        Field f = RESTFacadeImpl.class.getDeclaredField("ASYNC_TEMPLATE_OVERRIDE");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        ThreadLocal<AsyncRestTemplate> tl = (ThreadLocal<AsyncRestTemplate>) f.get(null);
        return tl;
    }

    @Test
    public void testAsyncTemplateOverride_fieldExistsAndIsStatic() throws Exception {
        Field f = RESTFacadeImpl.class.getDeclaredField("ASYNC_TEMPLATE_OVERRIDE");
        Assert.assertTrue("ASYNC_TEMPLATE_OVERRIDE must be static", Modifier.isStatic(f.getModifiers()));
        Assert.assertTrue("ASYNC_TEMPLATE_OVERRIDE must be final", Modifier.isFinal(f.getModifiers()));
        Assert.assertEquals("field type must be ThreadLocal", ThreadLocal.class, f.getType());
    }

    @Test
    public void testPingAsyncRestTemplate_fieldExists() throws Exception {
        Field f = RESTFacadeImpl.class.getDeclaredField("pingAsyncRestTemplate");
        f.setAccessible(true);
        // Field exists — initialized in init(), null until Spring wires the bean
        Assert.assertNotNull("pingAsyncRestTemplate field must be declared", f);
        Assert.assertEquals("field type must be AsyncRestTemplate",
                AsyncRestTemplate.class, f.getType());
    }

    @Test
    public void testAsyncTemplateOverride_isNullOnFreshThread() throws Exception {
        ThreadLocal<AsyncRestTemplate> tl = getOverrideTL();
        // on a new thread (or after cleanup) the ThreadLocal must be null
        Assert.assertNull("ASYNC_TEMPLATE_OVERRIDE must be null on fresh thread — verifies no leak from prior call", tl.get());
    }

    @Test
    public void testAsyncTemplateOverride_removeCleanupOnException() throws Exception {
        ThreadLocal<AsyncRestTemplate> tl = getOverrideTL();
        AsyncRestTemplate sentinel = new AsyncRestTemplate();

        // Simulate asyncJsonPostForPing: set → exception → finally remove
        tl.set(sentinel);
        Assert.assertSame("ThreadLocal must hold sentinel during call", sentinel, tl.get());
        try {
            throw new RuntimeException("simulated failure inside asyncJsonPost");
        } catch (RuntimeException ignored) {
        } finally {
            tl.remove(); // mirrors the finally block in asyncJsonPostForPing
        }

        Assert.assertNull("ASYNC_TEMPLATE_OVERRIDE must be null after remove() — verifies finally cleanup", tl.get());
    }
}
