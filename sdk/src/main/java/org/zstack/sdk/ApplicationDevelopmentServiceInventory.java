package org.zstack.sdk;

import org.zstack.sdk.ModelServiceInventory;

public class ApplicationDevelopmentServiceInventory extends org.zstack.sdk.ModelServiceInstanceGroupInventory {

    public java.lang.String deploymentStatus;
    public void setDeploymentStatus(java.lang.String deploymentStatus) {
        this.deploymentStatus = deploymentStatus;
    }
    public java.lang.String getDeploymentStatus() {
        return this.deploymentStatus;
    }

    public ModelServiceInventory service;
    public void setService(ModelServiceInventory service) {
        this.service = service;
    }
    public ModelServiceInventory getService() {
        return this.service;
    }

}
