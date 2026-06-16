package org.zstack.utils.test;

import org.junit.Assert;
import org.junit.Test;
import org.zstack.utils.TagUtils;

import java.util.Map;

public class TestTagUtils {
    @Test
    public void testIpv6CidrTokenAtEnd() {
        String format = "conversion::network::cidr::{conversionNetwork}";
        String tag = "conversion::network::cidr::fd66:6:6:6::/64";

        Assert.assertTrue(TagUtils.isMatch(format, tag));
        Map<String, String> tokens = TagUtils.parseIfMatch(format, tag);
        Assert.assertEquals("fd66:6:6:6::/64", tokens.get("conversionNetwork"));
    }

    @Test
    public void testBracedIpv6TokenBeforeStaticField() {
        String format = "resource::{cidr}::state::{state}";
        String tag = "resource::{fd66:6:6:6::/64}::state::enabled";

        Assert.assertTrue(TagUtils.isMatch(format, tag));
        Map<String, String> tokens = TagUtils.parseIfMatch(format, tag);
        Assert.assertEquals("{fd66:6:6:6::/64}", tokens.get("cidr"));
        Assert.assertEquals("enabled", tokens.get("state"));
    }
}
