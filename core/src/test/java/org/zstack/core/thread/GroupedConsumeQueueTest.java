package org.zstack.core.thread;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class GroupedConsumeQueueTest {
    private static final String TIMER_THREAD_PREFIX = "grouped-consume-queue-";

    @Test
    public void startShouldBeIdempotent() throws Exception {
        int before = timerThreadCount();
        GroupedConsumeQueue<String> queue = newQueue(ignored -> {
        });

        try {
            for (int i = 0; i < 10; i++) {
                queue.start();
            }

            waitUntilTimerThreadCount(before + 1);
            TimeUnit.MILLISECONDS.sleep(2200);
            Assert.assertEquals(before + 1, timerThreadCount());
        } finally {
            queue.stop();
        }

        waitUntilTimerThreadCount(before);
    }

    @Test
    public void groupedItemsShouldStillBeConsumed() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        List<String> consumed = Collections.synchronizedList(new ArrayList<>());
        GroupedConsumeQueue<String> queue = newQueue(items -> {
            consumed.addAll(items);
            latch.countDown();
        });

        try {
            queue.offer("a-1");
            queue.offer("a-2");
            queue.start();

            Assert.assertTrue(latch.await(3, TimeUnit.SECONDS));
            List<String> sorted = new ArrayList<>(consumed);
            Collections.sort(sorted);
            Assert.assertEquals(Arrays.asList("a-1", "a-2"), sorted);
        } finally {
            queue.stop();
        }
    }

    private GroupedConsumeQueue<String> newQueue(GroupConsumer consumer) {
        return new GroupedConsumeQueue<String>(1) {
            @Override
            protected void consume(List<String> groupedItems) {
                consumer.consume(groupedItems);
            }

            @Override
            protected String getGroupId(String item) {
                return item.substring(0, 1);
            }
        };
    }

    private int timerThreadCount() {
        int count = 0;
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (thread.isAlive() && thread.getName().startsWith(TIMER_THREAD_PREFIX)) {
                count++;
            }
        }
        return count;
    }

    private void waitUntilTimerThreadCount(int expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(5);
        while (System.currentTimeMillis() < deadline) {
            if (timerThreadCount() == expected) {
                return;
            }
            TimeUnit.MILLISECONDS.sleep(100);
        }

        Assert.assertEquals(expected, timerThreadCount());
    }

    private interface GroupConsumer {
        void consume(List<String> items);
    }
}
