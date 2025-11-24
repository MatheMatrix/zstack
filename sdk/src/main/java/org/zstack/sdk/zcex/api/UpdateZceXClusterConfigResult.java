package org.zstack.sdk.zcex.api;



public class UpdateZceXClusterConfigResult {
    private boolean clusterInitSuccess;
    public void setClusterInitSuccess(boolean clusterInitSuccess) {
        this.clusterInitSuccess = clusterInitSuccess;
    }
    public boolean isClusterInitSuccess() {
        return clusterInitSuccess;
    }

    private boolean tokenCreated;
    public void setTokenCreated(boolean tokenCreated) {
        this.tokenCreated = tokenCreated;
    }
    public boolean isTokenCreated() {
        return tokenCreated;
    }

}
