package org.zstack.sdk.crypto.ccs;

import org.zstack.sdk.crypto.ccs.CCSCertificateInventory;

public class AddCCSCertificateResult {
    public CCSCertificateInventory inventory;
    public void setInventory(CCSCertificateInventory inventory) {
        this.inventory = inventory;
    }
    public CCSCertificateInventory getInventory() {
        return this.inventory;
    }

}
