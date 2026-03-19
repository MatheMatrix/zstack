package org.zstack.storage.zbs;

import org.zstack.header.storage.addon.primary.ExternalPrimaryStorageConfig;
import org.zstack.utils.gson.JSONObjectUtil;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Xingwei Yu
 * @date 2024/4/2 11:13
 */
public class Config implements ExternalPrimaryStorageConfig {
    public static class Pool {
        public String logicalName;
        public String aliasName;

        public Pool(String logicalName, String aliasName) {
            this.logicalName = logicalName;
            this.aliasName = aliasName;
        }

        public Pool() {}
    }

    private List<String> mdsUrls;

    @Override
    public String getIdentity() {
        return ZbsConstants.IDENTITY;
    }

    private List<Pool> pools;
    private String logicalPoolName;
    private transient List<String> poolNames;

    @Override
    public ExternalPrimaryStorageConfig desensitize() {
        Config copy = JSONObjectUtil.toObject(JSONObjectUtil.toJsonString(this), Config.class);
        if (copy.mdsUrls != null) {
            copy.mdsUrls = copy.mdsUrls.stream()
                    .map(Config::desensitizeUrl)
                    .collect(Collectors.toList());
        }
        return copy;
    }

    /**
     * Mask the password portion of a mdsUrl.
     * Format: username:password@hostname[:port][/?mdsPort=...]
     * Uses indexOf/lastIndexOf consistent with MdsUri parsing logic,
     * so passwords containing ':' or '@' are correctly handled.
     */
    private static String desensitizeUrl(String url) {
        if (url == null) {
            return null;
        }
        int at = url.lastIndexOf("@");
        if (at <= 0) {
            return url;
        }
        int colon = url.indexOf(":");
        if (colon < 0 || colon >= at) {
            return url;
        }
        return url.substring(0, colon) + ":*****" + url.substring(at);
    }

    public List<String> getMdsUrls() {
        return mdsUrls;
    }

    public void setMdsUrls(List<String> mdsUrls) {
        this.mdsUrls = mdsUrls;
    }

    public String getLogicalPoolName() {
        return logicalPoolName;
    }

    public void setLogicalPoolName(String logicalPoolName) {
        this.logicalPoolName = logicalPoolName;
    }

    public void setPools(List<Pool> pools) {
        this.pools = pools;
        poolNames = getPoolNames();
    }

    public List<Pool> getPools() {
        return pools;
    }

    public List<String> getPoolNames() {
        if (poolNames == null) {
            poolNames = pools == null ? Collections.emptyList() : pools.stream().map(pool -> pool.logicalName).collect(Collectors.toList());
        }
        return poolNames;
    }
}
