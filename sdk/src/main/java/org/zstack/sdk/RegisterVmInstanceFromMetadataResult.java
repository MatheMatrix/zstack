package org.zstack.sdk;

import org.zstack.sdk.VmInstanceInventory;

public class RegisterVmInstanceFromMetadataResult {
    public VmInstanceInventory inventory;
    public void setInventory(VmInstanceInventory inventory) {
        this.inventory = inventory;
    }
    public VmInstanceInventory getInventory() {
        return this.inventory;
    }

    public java.util.List warnings;
    public void setWarnings(java.util.List warnings) {
        this.warnings = warnings;
    }
    public java.util.List getWarnings() {
        return this.warnings;
    }

}
