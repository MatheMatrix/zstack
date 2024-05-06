package org.zstack.header.storage.addon;

/**
 * @author Xingwei Yu
 * @date 2024/4/29 17:52
 */
public class CbdRemoteTarget extends BlockRemoteTarget {
    private String resourceURI;

    @Override
    public String getResourceURI() {
        return resourceURI;
    }

    public void setResourceURI(String resourceURI) {
        this.resourceURI = resourceURI;
    }
}
