package org.zstack.test.unittest.core

import org.junit.Test
import org.zstack.core.timeout.ApiTimeoutManagerImpl
import org.zstack.core.timeout.Timer
import org.zstack.header.message.APIMessage
import org.zstack.utils.TaskContext

import java.sql.Timestamp

/**
 * Create by weiwang at 2018/11/26 
 */
class GetTimeoutCase {
    @Test
    public void testParseObjectToLong() {
        Double a = 100.00
        double b = 100.00
        Long c = 100
        long d = 100
        assert ApiTimeoutManagerImpl.parseObjectToLong(a).equals(c)
        assert ApiTimeoutManagerImpl.parseObjectToLong(b).equals(c)
        assert ApiTimeoutManagerImpl.parseObjectToLong(c).equals(c)
        assert ApiTimeoutManagerImpl.parseObjectToLong(d).equals(c)
    }

    @Test
    void testExistingMessageDeadlineIsNotExtended() {
        long now = 1_000_000L
        ApiTimeoutManagerImpl timeoutManager = new ApiTimeoutManagerImpl()
        Timer fixedTimer = [
                getCurrentTimeMillis: { now },
                getCurrentTimestamp: { new Timestamp(now) }
        ] as Timer
        timeoutManager.@timer = fixedTimer

        TimeoutApiMessage msg = new TimeoutApiMessage()
        msg.timeout = 10_000L
        msg.messageDeadline = now + 1_000L

        TaskContext.removeTaskContext()
        try {
            timeoutManager.setMessageTimeout(msg)
        } finally {
            TaskContext.removeTaskContext()
        }

        assert msg.timeout == 1_000L
        assert msg.messageDeadline == now + 1_000L
    }

    private static class TimeoutApiMessage extends APIMessage {
    }
}
