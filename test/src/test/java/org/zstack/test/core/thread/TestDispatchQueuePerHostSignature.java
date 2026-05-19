package org.zstack.test.core.thread;

import junit.framework.Assert;
import org.junit.Before;
import org.junit.Test;
import org.zstack.core.componentloader.ComponentLoader;
import org.zstack.core.thread.SyncTask;
import org.zstack.core.thread.ThreadFacade;
import org.zstack.test.BeanConstructor;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class TestDispatchQueuePerHostSignature {
    private static final CLogger logger = Utils.getLogger(TestDispatchQueuePerHostSignature.class);
    ComponentLoader loader;
    ThreadFacade thdf;

    static class OrderedTask implements SyncTask<Integer> {
        final int id;
        final String signature;
        final List<Integer> executionOrder;
        final CountDownLatch started;
        final CountDownLatch letFinish;

        OrderedTask(int id, String signature, List<Integer> executionOrder,
                    CountDownLatch started, CountDownLatch letFinish) {
            this.id = id;
            this.signature = signature;
            this.executionOrder = executionOrder;
            this.started = started;
            this.letFinish = letFinish;
        }

        @Override
        public Integer call() throws Exception {
            executionOrder.add(id);
            started.countDown();
            letFinish.await(10, TimeUnit.SECONDS);
            return id;
        }

        @Override
        public String getSyncSignature() { return signature; }

        @Override
        public String getName() { return "OrderedTask-" + id; }

        @Override
        public int getSyncLevel() { return 2; }
    }

    @Before
    public void setUp() throws Exception {
        BeanConstructor con = new BeanConstructor();
        loader = con.build();
        thdf = loader.getComponent(ThreadFacade.class);
    }

    @Test
    public void sameSignatureSerializes() throws Exception {
        List<Integer> executionOrder = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch started1 = new CountDownLatch(1);
        CountDownLatch letFinish1 = new CountDownLatch(1);

        Future<Integer> f1 = thdf.syncSubmit(new OrderedTask(1, "same-sig", executionOrder, started1, letFinish1));
        started1.await(10, TimeUnit.SECONDS);

        CountDownLatch started2 = new CountDownLatch(1);
        CountDownLatch letFinish2 = new CountDownLatch(1);
        Future<Integer> f2 = thdf.syncSubmit(new OrderedTask(2, "same-sig", executionOrder, started2, letFinish2));

        // Task 2 should NOT start while Task 1 is still running (same signature = serial)
        Assert.assertFalse("task 2 should be blocked by same signature", started2.await(500, TimeUnit.MILLISECONDS));

        letFinish1.countDown();
        Assert.assertEquals(1, (int) f1.get(10, TimeUnit.SECONDS));

        started2.await(10, TimeUnit.SECONDS);
        Assert.assertEquals("task 1 must execute before task 2", (Integer) 1, executionOrder.get(0));
        Assert.assertEquals("task 2 must execute after task 1", (Integer) 2, executionOrder.get(1));

        letFinish2.countDown();
        Assert.assertEquals(2, (int) f2.get(10, TimeUnit.SECONDS));
    }

    @Test
    public void differentSignatureConcurrent() throws Exception {
        CountDownLatch started1 = new CountDownLatch(1);
        CountDownLatch started2 = new CountDownLatch(1);
        CountDownLatch letFinish = new CountDownLatch(1);

        List<Integer> executionOrder = Collections.synchronizedList(new ArrayList<>());

        thdf.syncSubmit(new OrderedTask(1, "per-host-sig-host-a", executionOrder, started1, letFinish));
        thdf.syncSubmit(new OrderedTask(2, "per-host-sig-host-b", executionOrder, started2, letFinish));

        // Both tasks should start concurrently (different signatures = different queues)
        Assert.assertTrue("task 1 should start", started1.await(10, TimeUnit.SECONDS));
        Assert.assertTrue("task 2 should start concurrently", started2.await(10, TimeUnit.SECONDS));

        letFinish.countDown();
    }
}
