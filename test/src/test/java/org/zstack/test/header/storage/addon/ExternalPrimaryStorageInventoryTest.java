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
}
