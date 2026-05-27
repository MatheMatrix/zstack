package org.zstack.test.header.storage.addon;

import org.junit.Test;
import org.junit.Assert;
import org.zstack.header.storage.addon.primary.ExternalPrimaryStorageInventory;
import org.zstack.header.storage.addon.primary.ExternalPrimaryStorageVO;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ExternalPrimaryStorageInventoryTest {

    @Test
    public void testAddonInfoPasswordDesensitized() {
        ExternalPrimaryStorageVO vo = new ExternalPrimaryStorageVO();
        vo.setAddonInfo("{"
                + "\"mdsInfos\": [{"
                + "\"addr\": \"10.0.0.1\","
                + "\"username\": \"admin\","
                + "\"password\": \"secret123\""
                + "}]}");

        ExternalPrimaryStorageInventory inv = ExternalPrimaryStorageInventory.valueOf(vo);

        Map addonInfo = inv.getAddonInfo();
        Assert.assertNotNull(addonInfo);

        List<Map> mdsInfos = (List<Map>) addonInfo.get("mdsInfos");
        Assert.assertNotNull(mdsInfos);
        Assert.assertEquals(1, mdsInfos.size());
        Assert.assertEquals("******", mdsInfos.get(0).get("password"));
        Assert.assertEquals("10.0.0.1", mdsInfos.get(0).get("addr"));
        Assert.assertEquals("admin", mdsInfos.get(0).get("username"));
    }

    @Test
    public void testAddonInfoSshPasswordDesensitized() {
        ExternalPrimaryStorageVO vo = new ExternalPrimaryStorageVO();
        vo.setAddonInfo("{"
                + "\"mdsInfos\": [{"
                + "\"mdsAddr\": \"10.0.0.2\","
                + "\"sshUsername\": \"root\","
                + "\"sshPassword\": \"realSecret\""
                + "}]}");

        ExternalPrimaryStorageInventory inv = ExternalPrimaryStorageInventory.valueOf(vo);

        Map addonInfo = inv.getAddonInfo();
        List<Map> mdsInfos = (List<Map>) addonInfo.get("mdsInfos");
        Assert.assertEquals("******", mdsInfos.get(0).get("sshPassword"));
        Assert.assertEquals("10.0.0.2", mdsInfos.get(0).get("mdsAddr"));
        Assert.assertEquals("******", mdsInfos.get(0).get("sshUsername"));
    }

    @Test
    public void testAddonInfoNullSafe() {
        ExternalPrimaryStorageVO vo = new ExternalPrimaryStorageVO();
        vo.setAddonInfo(null);

        ExternalPrimaryStorageInventory inv = ExternalPrimaryStorageInventory.valueOf(vo);
        Assert.assertNull(inv.getAddonInfo());
    }

    @Test
    public void testAddonInfoNoMdsInfos() {
        ExternalPrimaryStorageVO vo = new ExternalPrimaryStorageVO();
        vo.setAddonInfo("{\"clusterInfo\": {\"name\": \"test\"}}");

        ExternalPrimaryStorageInventory inv = ExternalPrimaryStorageInventory.valueOf(vo);

        Map addonInfo = inv.getAddonInfo();
        Assert.assertNotNull(addonInfo);
        Assert.assertNotNull(addonInfo.get("clusterInfo"));
        Assert.assertNull(addonInfo.get("mdsInfos"));
    }

    @Test
    public void testConfigMdsUrlsDesensitized() {
        ExternalPrimaryStorageVO vo = new ExternalPrimaryStorageVO();
        vo.setConfig("{"
                + "\"mdsUrls\": ["
                + "\"http://admin:secret123@10.0.0.1:8080/path\","
                + "\"https://user:pass@10.0.0.2:9090\""
                + "],"
                + "\"logicalPoolName\": \"pool1\""
                + "}");

        ExternalPrimaryStorageInventory inv = ExternalPrimaryStorageInventory.valueOf(vo);

        Map config = inv.getConfig();
        Assert.assertNotNull(config);
        Assert.assertEquals("pool1", config.get("logicalPoolName"));

        List<String> mdsUrls = (List<String>) config.get("mdsUrls");
        Assert.assertNotNull(mdsUrls);
        Assert.assertEquals(2, mdsUrls.size());
        Assert.assertTrue(mdsUrls.get(0).contains("***@"));
        Assert.assertTrue(mdsUrls.get(0).startsWith("http://"));
        Assert.assertTrue(mdsUrls.get(1).contains("***@"));
        Assert.assertTrue(mdsUrls.get(1).startsWith("https://"));
    }

    @Test
    public void testConfigWithoutMdsInfosDoesNotLeak() {
        ExternalPrimaryStorageVO vo = new ExternalPrimaryStorageVO();
        vo.setConfig("{"
                + "\"mdsUrls\": [\"http://admin:pass@10.0.0.1:8080\"],"
                + "\"mdsInfos\": [{"
                + "\"mdsAddr\": \"10.0.0.3\","
                + "\"sshPassword\": \"shouldBeMasked\""
                + "}]"
                + "}");

        ExternalPrimaryStorageInventory inv = ExternalPrimaryStorageInventory.valueOf(vo);

        Map config = inv.getConfig();
        Assert.assertNotNull(config);

        List<String> mdsUrls = (List<String>) config.get("mdsUrls");
        Assert.assertEquals(1, mdsUrls.size());
        Assert.assertTrue(mdsUrls.get(0).contains("***@"));

        Object mdsInfos = config.get("mdsInfos");
        if (mdsInfos instanceof List) {
            for (Object item : (List) mdsInfos) {
                if (item instanceof Map) {
                    Assert.assertNotEquals("shouldBeMasked", ((Map) item).get("sshPassword"));
                }
            }
        }
    }

    @Test
    public void testConfigNullSafe() {
        ExternalPrimaryStorageVO vo = new ExternalPrimaryStorageVO();
        vo.setConfig(null);

        ExternalPrimaryStorageInventory inv = ExternalPrimaryStorageInventory.valueOf(vo);
        Assert.assertNull(inv.getConfig());
    }

    @Test
    public void testConfigNoMdsUrls() {
        ExternalPrimaryStorageVO vo = new ExternalPrimaryStorageVO();
        vo.setConfig("{\"logicalPoolName\": \"pool1\"}");

        ExternalPrimaryStorageInventory inv = ExternalPrimaryStorageInventory.valueOf(vo);

        Map config = inv.getConfig();
        Assert.assertNotNull(config);
        Assert.assertEquals("pool1", config.get("logicalPoolName"));
        Assert.assertNull(config.get("mdsUrls"));
    }
}
