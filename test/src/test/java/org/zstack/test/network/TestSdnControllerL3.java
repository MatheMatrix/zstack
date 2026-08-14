package org.zstack.test.network;

import org.junit.Assert;
import org.junit.Test;
import org.zstack.header.core.Completion;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.network.l3.IpRangeInventory;
import org.zstack.header.network.l3.L3NetworkInventory;
import org.zstack.header.network.l3.SdnControllerL3;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class TestSdnControllerL3 {
    @Test
    public void testDeleteLastIpRangePropagatesControllerFailure() {
        ErrorCode expected = new ErrorCode("SYS.1006", "Operation Error", "controller failed");
        AtomicReference<ErrorCode> actual = new AtomicReference<>();
        SdnControllerL3 controller = new SdnControllerL3() {
            @Override
            public void createL3Network(L3NetworkInventory inv, List<String> systemTags, Completion completion) {
            }

            @Override
            public void deleteL3Network(L3NetworkInventory inv, Completion completion) {
            }

            @Override
            public void createIpRange(IpRangeInventory inv, Completion completion) {
            }

            @Override
            public void deleteIpRange(IpRangeInventory inv, Completion completion) {
                completion.fail(expected);
            }
        };

        controller.deleteIpRange(null, true, new Completion(null) {
            @Override
            public void success() {
                Assert.fail("controller failure must not be converted to success");
            }

            @Override
            public void fail(ErrorCode errorCode) {
                actual.set(errorCode);
            }
        });

        Assert.assertSame(expected, actual.get());
    }
}
