package org.zstack.test.header.storage.addon;

import org.junit.Test;
import org.junit.Assert;
import org.zstack.header.storage.addon.primary.ExternalPrimaryStorageInventory;
import org.zstack.header.storage.addon.primary.ExternalPrimaryStorageVO;
import org.zstack.header.storage.primary.PrimaryStorageState;
import org.zstack.header.storage.primary.PrimaryStorageStatus;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ExternalPrimaryStorageInventoryTest {

    private ExternalPrimaryStorageVO createVO() {
        ExternalPrimaryStorageVO vo = new ExternalPrimaryStorageVO();
        vo.setState(PrimaryStorageState.Enabled);
        vo.setStatus(PrimaryStorageStatus.Connected);
        vo.setType("ZBS");
        vo.setAttachedClusterRefs(Collections.emptySet());
        return vo;
    }

    @Test
    public void testAddonInfoPasswordDesensitized() {
        ExternalPrimaryStorageVO vo = createVO();
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
        ExternalPrimaryStorageVO vo = createVO();
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
        Assert.assertEquals("root", mdsInfos.get(0).get("sshUsername"));
    }

    @Test
    public void testAddonInfoTopLevelPasswordMasked() {
        ExternalPrimaryStorageVO vo = createVO();
        vo.setAddonInfo("{"
                + "\"password\": \"top-level-secret\","
                + "\"clusterName\": \"prod\""
                + "}");

        ExternalPrimaryStorageInventory inv = ExternalPrimaryStorageInventory.valueOf(vo);

        Map addonInfo = inv.getAddonInfo();
        Assert.assertEquals("******", addonInfo.get("password"));
        Assert.assertEquals("prod", addonInfo.get("clusterName"));
    }

    @Test
    public void testAddonInfoNullSafe() {
        ExternalPrimaryStorageVO vo = createVO();
        vo.setAddonInfo(null);

        ExternalPrimaryStorageInventory inv = ExternalPrimaryStorageInventory.valueOf(vo);
        Assert.assertNull(inv.getAddonInfo());
    }

    @Test
    public void testAddonInfoNoMdsInfos() {
        ExternalPrimaryStorageVO vo = createVO();
        vo.setAddonInfo("{\"clusterInfo\": {\"name\": \"test\"}}");

        ExternalPrimaryStorageInventory inv = ExternalPrimaryStorageInventory.valueOf(vo);

        Map addonInfo = inv.getAddonInfo();
        Assert.assertNotNull(addonInfo);
        Assert.assertNotNull(addonInfo.get("clusterInfo"));
        Assert.assertNull(addonInfo.get("mdsInfos"));
    }

    @Test
    public void testExactWhitelistKeysMasked() {
        ExternalPrimaryStorageVO vo = createVO();
        vo.setAddonInfo("{"
                + "\"mdsInfos\": [{"
                + "\"password\": \"secret123\","
                + "\"sshPassword\": \"realSecret\","
                + "\"privateKey\": \"-----BEGIN RSA-----\","
                + "\"publicKey\": \"ssh-rsa AAA...\","
                + "\"adminPassword\": \"admin123\","
                + "\"mdsAddr\": \"10.0.0.10\","
                + "\"sshUsername\": \"operator\""
                + "}]}");

        ExternalPrimaryStorageInventory inv = ExternalPrimaryStorageInventory.valueOf(vo);

        Map addonInfo = inv.getAddonInfo();
        List<Map> mdsInfos = (List<Map>) addonInfo.get("mdsInfos");
        Map item = mdsInfos.get(0);

        // exact whitelist match (case-insensitive)
        Assert.assertEquals("******", item.get("password"));
        Assert.assertEquals("******", item.get("sshPassword"));
        Assert.assertEquals("******", item.get("privateKey"));
        // non-sensitive: not in whitelist (no suffix/contains matching)
        Assert.assertEquals("ssh-rsa AAA...", item.get("publicKey"));
        Assert.assertEquals("admin123", item.get("adminPassword"));
        Assert.assertEquals("10.0.0.10", item.get("mdsAddr"));
        Assert.assertEquals("operator", item.get("sshUsername"));
    }

    @Test
    public void testNonWhitelistKeysNotMasked() {
        ExternalPrimaryStorageVO vo = createVO();
        vo.setAddonInfo("{"
                + "\"mdsInfos\": [{"
                + "\"apiKey\": \"api-key-456\","
                + "\"secretKey\": \"sk-abc\","
                + "\"accessToken\": \"tok-abc\","
                + "\"routingKey\": \"route-123\","
                + "\"mdsAddr\": \"10.0.0.10\","
                + "\"sshUsername\": \"operator\""
                + "}]}");

        ExternalPrimaryStorageInventory inv = ExternalPrimaryStorageInventory.valueOf(vo);

        Map addonInfo = inv.getAddonInfo();
        List<Map> mdsInfos = (List<Map>) addonInfo.get("mdsInfos");
        Map item = mdsInfos.get(0);

        Assert.assertEquals("api-key-456", item.get("apiKey"));
        Assert.assertEquals("sk-abc", item.get("secretKey"));
        Assert.assertEquals("tok-abc", item.get("accessToken"));
        Assert.assertEquals("route-123", item.get("routingKey"));
        Assert.assertEquals("10.0.0.10", item.get("mdsAddr"));
        Assert.assertEquals("operator", item.get("sshUsername"));
    }

    @Test
    public void testWhitelistKeysMasked() {
        ExternalPrimaryStorageVO vo = createVO();
        vo.setAddonInfo("{"
                + "\"mdsInfos\": [{"
                + "\"credential\": \"cred-xyz\","
                + "\"apiSecret\": \"api-secret-123\","
                + "\"accessKey\": \"AKIA123\","
                + "\"token\": \"bearer-token\","
                + "\"secret\": \"shared-secret\","
                + "\"nextToken\": \"next-token-123\","
                + "\"mdsAddr\": \"10.0.0.10\""
                + "}]}");

        ExternalPrimaryStorageInventory inv = ExternalPrimaryStorageInventory.valueOf(vo);

        Map addonInfo = inv.getAddonInfo();
        List<Map> mdsInfos = (List<Map>) addonInfo.get("mdsInfos");
        Map item = mdsInfos.get(0);

        // exact whitelist match
        Assert.assertEquals("******", item.get("credential"));
        Assert.assertEquals("******", item.get("apiSecret"));
        Assert.assertEquals("******", item.get("accessKey"));
        Assert.assertEquals("******", item.get("token"));
        Assert.assertEquals("******", item.get("secret"));
        // non-whitelisted: not in exact match list
        Assert.assertEquals("next-token-123", item.get("nextToken"));
        Assert.assertEquals("10.0.0.10", item.get("mdsAddr"));
    }

    @Test
    public void testCaseInsensitivePasswordMasked() {
        ExternalPrimaryStorageVO vo = createVO();
        vo.setAddonInfo("{"
                + "\"mdsInfos\": [{"
                + "\"Password\": \"MixedCase1\","
                + "\"SSHPASSWORD\": \"UPPERCASE\","
                + "\"PrivateKey\": \"MixedCase2\","
                + "\"mdsAddr\": \"10.0.0.3\""
                + "}]}");

        ExternalPrimaryStorageInventory inv = ExternalPrimaryStorageInventory.valueOf(vo);

        Map addonInfo = inv.getAddonInfo();
        List<Map> mdsInfos = (List<Map>) addonInfo.get("mdsInfos");
        Map item = mdsInfos.get(0);

        Assert.assertEquals("******", item.get("Password"));
        Assert.assertEquals("******", item.get("SSHPASSWORD"));
        Assert.assertEquals("******", item.get("PrivateKey"));
        Assert.assertEquals("10.0.0.3", item.get("mdsAddr"));
    }

    @Test
    public void testAddonInfoPasswordInNestedMap() {
        ExternalPrimaryStorageVO vo = createVO();
        vo.setAddonInfo("{"
                + "\"mdsInfos\": [{"
                + "\"mdsAddr\": \"10.0.0.9\","
                + "\"auth\": {"
                + "\"password\": \"nested-secret\""
                + "}"
                + "}]}");

        ExternalPrimaryStorageInventory inv = ExternalPrimaryStorageInventory.valueOf(vo);

        Map addonInfo = inv.getAddonInfo();
        List<Map> mdsInfos = (List<Map>) addonInfo.get("mdsInfos");
        Map auth = (Map) mdsInfos.get(0).get("auth");
        Assert.assertEquals("******", auth.get("password"));
    }

    @Test
    public void testConfigMdsUrlsDesensitized() {
        ExternalPrimaryStorageVO vo = createVO();
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
    public void testConfigNullSafe() {
        ExternalPrimaryStorageVO vo = createVO();
        vo.setConfig(null);

        ExternalPrimaryStorageInventory inv = ExternalPrimaryStorageInventory.valueOf(vo);
        Assert.assertNull(inv.getConfig());
    }

    @Test
    public void testConfigNoMdsUrls() {
        ExternalPrimaryStorageVO vo = createVO();
        vo.setConfig("{\"logicalPoolName\": \"pool1\"}");

        ExternalPrimaryStorageInventory inv = ExternalPrimaryStorageInventory.valueOf(vo);

        Map config = inv.getConfig();
        Assert.assertNotNull(config);
        Assert.assertEquals("pool1", config.get("logicalPoolName"));
        Assert.assertNull(config.get("mdsUrls"));
    }

    @Test
    public void testConfigMdsInfosStringUrlFallback() {
        ExternalPrimaryStorageVO vo = createVO();
        vo.setConfig("{"
                + "\"mdsInfos\": ["
                + "\"http://admin:secret123@10.0.0.1:8080/path\","
                + "\"https://user:pass@10.0.0.2:9090\""
                + "]}");

        ExternalPrimaryStorageInventory inv = ExternalPrimaryStorageInventory.valueOf(vo);

        Map config = inv.getConfig();
        List mdsInfos = (List) config.get("mdsInfos");
        Assert.assertNotNull(mdsInfos);
        Assert.assertEquals(2, mdsInfos.size());
        Assert.assertTrue(((String) mdsInfos.get(0)).contains("***@"));
        Assert.assertTrue(((String) mdsInfos.get(1)).contains("***@"));
    }

    @Test
    public void testUrlInMapValueNotDesensitized() {
        ExternalPrimaryStorageVO vo = createVO();
        vo.setAddonInfo("{"
                + "\"endpoint\": \"http://admin:secret123@10.0.0.1:8080/api\","
                + "\"clusterName\": \"prod\""
                + "}");

        ExternalPrimaryStorageInventory inv = ExternalPrimaryStorageInventory.valueOf(vo);

        Map addonInfo = inv.getAddonInfo();
        Assert.assertEquals("http://admin:secret123@10.0.0.1:8080/api", addonInfo.get("endpoint"));
        Assert.assertEquals("prod", addonInfo.get("clusterName"));
    }

    @Test
    public void testSchemeLessUrlDesensitized() {
        ExternalPrimaryStorageVO vo = createVO();
        vo.setConfig("{"
                + "\"mdsUrls\": ["
                + "\"admin:secret123@10.0.0.1:8080/path\""
                + "],"
                + "\"logicalPoolName\": \"pool1\""
                + "}");

        ExternalPrimaryStorageInventory inv = ExternalPrimaryStorageInventory.valueOf(vo);

        Map config = inv.getConfig();
        List<String> mdsUrls = (List<String>) config.get("mdsUrls");
        Assert.assertEquals(1, mdsUrls.size());
        Assert.assertTrue(mdsUrls.get(0).startsWith("***@"));
        Assert.assertTrue(mdsUrls.get(0).endsWith("@10.0.0.1:8080/path"));
    }

    @Test
    public void testEmailNotDesensitized() {
        ExternalPrimaryStorageVO vo = createVO();
        vo.setAddonInfo("{"
                + "\"contactEmail\": \"admin@example.com\","
                + "\"clusterName\": \"prod\""
                + "}");

        ExternalPrimaryStorageInventory inv = ExternalPrimaryStorageInventory.valueOf(vo);

        Map addonInfo = inv.getAddonInfo();
        Assert.assertEquals("admin@example.com", addonInfo.get("contactEmail"));
        Assert.assertEquals("prod", addonInfo.get("clusterName"));
    }

    @Test
    public void testUrlWithoutCredentialsNotDesensitized() {
        ExternalPrimaryStorageVO vo = createVO();
        vo.setAddonInfo("{"
                + "\"endpoint\": \"http://10.0.0.1/path@tag\","
                + "\"clusterName\": \"prod\""
                + "}");

        ExternalPrimaryStorageInventory inv = ExternalPrimaryStorageInventory.valueOf(vo);

        Map addonInfo = inv.getAddonInfo();
        Assert.assertEquals("http://10.0.0.1/path@tag", addonInfo.get("endpoint"));
        Assert.assertEquals("prod", addonInfo.get("clusterName"));
    }

    @Test
    public void testUrlWithAtSignInFragmentNotDesensitized() {
        ExternalPrimaryStorageVO vo = createVO();
        vo.setAddonInfo("{"
                + "\"endpoint\": \"https://host.example.com/resource?q=@tag\","
                + "\"clusterName\": \"prod\""
                + "}");

        ExternalPrimaryStorageInventory inv = ExternalPrimaryStorageInventory.valueOf(vo);

        Map addonInfo = inv.getAddonInfo();
        Assert.assertEquals("https://host.example.com/resource?q=@tag", addonInfo.get("endpoint"));
        Assert.assertEquals("prod", addonInfo.get("clusterName"));
    }
}
