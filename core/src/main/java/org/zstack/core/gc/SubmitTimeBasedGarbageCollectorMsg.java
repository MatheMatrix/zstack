package org.zstack.core.gc;

import org.zstack.header.message.NeedReplyMessage;

import java.util.concurrent.TimeUnit;

public class SubmitTimeBasedGarbageCollectorMsg extends NeedReplyMessage {
    private TimeBasedGarbageCollector gc;
    private Long gcInterval;
    private TimeUnit unit;

    public TimeBasedGarbageCollector getGc() {
        return gc;
    }

    public void setGc(TimeBasedGarbageCollector gc) {
        this.gc = gc;
    }

    public Long getGcInterval() {
        return gcInterval;
    }

    public void setGcInterval(Long gcInterval) {
        this.gcInterval = gcInterval;
    }

    public TimeUnit getUnit() {
        return unit;
    }

    public void setUnit(TimeUnit unit) {
        this.unit = unit;
    }
}