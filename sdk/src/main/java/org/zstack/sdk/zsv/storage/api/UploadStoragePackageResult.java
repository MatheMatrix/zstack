package org.zstack.sdk.zsv.storage.api;

import org.zstack.sdk.zsv.storage.entity.StoragePackageInventory;

public class UploadStoragePackageResult {
    public StoragePackageInventory inventory;
    public void setInventory(StoragePackageInventory inventory) {
        this.inventory = inventory;
    }
    public StoragePackageInventory getInventory() {
        return this.inventory;
    }

}
