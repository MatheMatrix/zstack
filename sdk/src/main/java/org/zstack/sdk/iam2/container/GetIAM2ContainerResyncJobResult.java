package org.zstack.sdk.iam2.container;

import org.zstack.sdk.iam2.container.IAM2ContainerResyncJobInventory;

public class GetIAM2ContainerResyncJobResult {
    public IAM2ContainerResyncJobInventory inventory;
    public void setInventory(IAM2ContainerResyncJobInventory inventory) {
        this.inventory = inventory;
    }
    public IAM2ContainerResyncJobInventory getInventory() {
        return this.inventory;
    }

}
