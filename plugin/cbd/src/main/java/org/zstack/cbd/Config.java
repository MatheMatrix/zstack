package org.zstack.cbd;

import java.util.List;

/**
 * @author Xingwei Yu
 * @date 2024/4/2 11:13
 */
public class Config {
    private List<String> mdsUrls;
    private List<String> poolNames;

    public List<String> getMdsUrls() {
        return mdsUrls;
    }

    public void setMdsUrls(List<String> mdsUrls) {
        this.mdsUrls = mdsUrls;
    }

    public List<String> getPoolNames() {
        return poolNames;
    }

    public void setPoolNames(List<String> poolNames) {
        this.poolNames = poolNames;
    }
}
