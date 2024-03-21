package org.zstack.storage.zbs;

import java.util.List;

/**
 * @author Xingwei Yu
 * @date 2024/4/2 11:13
 */
public class ZbsConfig {
    private List<String> mdsUrls;

    public List<String> getMdsUrls() {
        return mdsUrls;
    }

    public void setMdsUrls(List<String> mdsUrls) {
        this.mdsUrls = mdsUrls;
    }
}
