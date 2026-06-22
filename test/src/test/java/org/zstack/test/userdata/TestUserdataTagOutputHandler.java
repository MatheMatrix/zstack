package org.zstack.test.userdata;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.zstack.compute.vm.VmSystemTags;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class TestUserdataTagOutputHandler {
    private final VmSystemTags.UserdataTagOutputHandler handler = new VmSystemTags.UserdataTagOutputHandler();

    @Before
    public void setUp() throws NoSuchFieldException {
        VmSystemTags.USERDATA.annotation = VmSystemTags.class.getField("USERDATA").getAnnotation(org.zstack.tag.SensitiveTag.class);
    }

    @Test
    public void desensitizeMalformedCloudConfigMasksUserdata() {
        String userdata = "#cloud-config\nchpasswd:\n  list: |\nroot:word\nexpire: False\n";
        String maskedTag = handler.desensitizeTag(VmSystemTags.USERDATA, userdataTag(userdata));

        Assert.assertEquals("*****", VmSystemTags.USERDATA.getTokenByTag(maskedTag, VmSystemTags.USERDATA_TOKEN));
    }

    @Test
    public void desensitizeValidCloudConfigKeepsStructuredMask() {
        String userdata = "#cloud-config\nchpasswd:\n  list: |\n    root:word\n  expire: False\n";
        String maskedTag = handler.desensitizeTag(VmSystemTags.USERDATA, userdataTag(userdata));
        String maskedUserdata = new String(Base64.getDecoder().decode(
                VmSystemTags.USERDATA.getTokenByTag(maskedTag, VmSystemTags.USERDATA_TOKEN).getBytes()));

        Assert.assertTrue(maskedUserdata.contains("*****:*****"));
        Assert.assertFalse(maskedUserdata.contains("root:word"));
    }

    private String userdataTag(String userdata) {
        Map<String, String> tokens = new HashMap<>();
        tokens.put(VmSystemTags.USERDATA_TOKEN, new String(Base64.getEncoder().encode(userdata.getBytes())));
        return VmSystemTags.USERDATA.instantiateTag(tokens);
    }
}
