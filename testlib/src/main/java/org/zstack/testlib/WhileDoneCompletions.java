package org.zstack.testlib;

import org.zstack.header.core.WhileDoneCompletion;
import org.zstack.header.errorcode.ErrorCodeList;

import java.util.concurrent.CountDownLatch;

final class WhileDoneCompletions {
    private WhileDoneCompletions() {
    }

    static WhileDoneCompletion countDownLatch(CountDownLatch latch) {
        return new WhileDoneCompletion(null) {
            @Override
            public void done(ErrorCodeList errorCodeList) {
                latch.countDown();
            }
        };
    }
}
