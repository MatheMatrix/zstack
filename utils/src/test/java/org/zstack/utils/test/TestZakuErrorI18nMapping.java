package org.zstack.utils.test;

import junit.framework.Assert;
import org.junit.Test;
import org.zstack.utils.gson.JSONObjectUtil;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

public class TestZakuErrorI18nMapping {
    private static String resolveRepoPath(String relativePathFromRepoRoot) {
        // When tests run in the `utils` module, the working directory is typically `<repo>/utils`.
        // We try both `<repo>/<path>` (via ../) and `<cwd>/<path>` for robustness.
        if (Files.exists(Paths.get("../" + relativePathFromRepoRoot))) {
            return "../" + relativePathFromRepoRoot;
        }
        return relativePathFromRepoRoot;
    }

    private static Map<String, Object> loadJsonAsMap(String path) throws Exception {
        byte[] bytes = Files.readAllBytes(Paths.get(path));
        String json = new String(bytes, StandardCharsets.UTF_8);
        return JSONObjectUtil.toObject(json, Map.class);
    }

    @Test
    public void testZakuI18nKeysContainResyncHint() throws Exception {
        Map<String, Object> zh = loadJsonAsMap(resolveRepoPath("conf/i18n/globalErrorCodeMapping/global-error-zh_CN.json"));
        Map<String, Object> en = loadJsonAsMap(resolveRepoPath("conf/i18n/globalErrorCodeMapping/global-error-en_US.json"));

        String[] keys = new String[] {
                "ORG_ZSTACK_IAM2_CONTAINER_ZAKU_10000",
                "ORG_ZSTACK_IAM2_CONTAINER_ZAKU_10001",
                "ORG_ZSTACK_IAM2_CONTAINER_ZAKU_10002",
                "ORG_ZSTACK_IAM2_CONTAINER_ZAKU_10003",
        };

        for (String key : keys) {
            Assert.assertTrue("missing zh_CN mapping for " + key, zh.containsKey(key));
            Assert.assertTrue("missing en_US mapping for " + key, en.containsKey(key));

            String zhMsg = String.valueOf(zh.get(key));
            String enMsg = String.valueOf(en.get(key));

            Assert.assertTrue("zh_CN mapping should mention resync hint for " + key, zhMsg.contains("重新同步"));
            Assert.assertTrue("en_US mapping should mention re-sync hint for " + key, enMsg.toLowerCase().contains("re-sync"));
        }
    }
}

