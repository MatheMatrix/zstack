package org.zstack.sdk;

import org.zstack.sdk.ContainerManagementEndpointInventory;

public class UpdateContainerManagementEndpointResult {
    public ContainerManagementEndpointInventory inventory;
    public void setInventory(ContainerManagementEndpointInventory inventory) {
        this.inventory = inventory;
    }
    public ContainerManagementEndpointInventory getInventory() {
        return this.inventory;
    }

}
