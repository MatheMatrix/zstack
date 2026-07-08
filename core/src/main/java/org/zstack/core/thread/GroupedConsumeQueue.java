package org.zstack.core.thread;

import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import java.util.*;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by MaJin on 2020/5/25.
 */
public abstract class GroupedConsumeQueue<T> {
    private static final CLogger logger = Utils.getLogger(GroupedConsumeQueue.class);
    private static final AtomicInteger TIMER_SEQUENCE = new AtomicInteger(0);

    private final Queue<T> itemPool = new LinkedBlockingQueue<>();
    private final Map<String, DelayedQueue<T>> groupedQueue = new HashMap<>();
    private final Object timerLock = new Object();
    private Timer timer;

    private volatile int maxDelayedTime = 1;

    public GroupedConsumeQueue(int maxDelayedTime) {
        this.maxDelayedTime = maxDelayedTime;
    }

    public void offer(T item) {
        if (maxDelayedTime <= 0) {
            consume(Collections.singletonList(item));
            return;
        }

        itemPool.offer(item);
    }

    public void start() {
        synchronized (timerLock) {
            if (timer != null) {
                return;
            }

            timer = new Timer(String.format("grouped-consume-queue-%s", TIMER_SEQUENCE.incrementAndGet()), true);
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    try {
                        collectItems();
                    } catch (Throwable t) {
                        logger.warn("failed to collect grouped queue items", t);
                    }
                }
            }, 1000, 1000);
        }
    }

    public void stop() {
        synchronized (timerLock) {
            if (timer == null) {
                return;
            }

            timer.cancel();
            timer = null;
        }
    }

    public void setMaxDelayedTime(int maxDelayedTime) {
        this.maxDelayedTime = maxDelayedTime;
    }

    protected abstract void consume(List<T> groupedItems);

    protected abstract String getGroupId(T item);

    static class DelayedQueue<T> {
        List<T> items = new ArrayList<>();
        private int delayedTime = 0;

        void delay() {
            ++delayedTime;
        }

        int getDelayedTime() {
            return delayedTime;
        }
    }

    private void collectItems() {
        T item;
        while ((item = itemPool.poll()) != null) {
            groupedQueue.computeIfAbsent(getGroupId(item), k -> new DelayedQueue<>()).items.add(item);
        }

        for (Iterator<DelayedQueue<T>> it = groupedQueue.values().iterator(); it.hasNext();) {
            DelayedQueue<T> queue = it.next();
            queue.delay();
            if (queue.getDelayedTime() >= maxDelayedTime) {
                consume(queue.items);
                it.remove();
            }
        }
    }
}
