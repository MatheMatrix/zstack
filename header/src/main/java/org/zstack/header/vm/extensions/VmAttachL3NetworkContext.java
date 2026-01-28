package org.zstack.header.vm.extensions;

import org.zstack.header.network.l3.L3NetworkInventory;
import org.zstack.header.vm.VmInstanceInventory;
import org.zstack.header.vm.VmNicInventory;

import java.util.HashMap;
import java.util.Map;

/**
 * Context object for VM L3 Network attachment operations.
 *
 * <p>This context is passed through all lifecycle phases (pre/before/after/failedTo)
 * allowing extensions to share state between phases.</p>
 */
public class VmAttachL3NetworkContext {
    private VmInstanceInventory vm;
    private L3NetworkInventory l3;
    private VmNicInventory nic;
    private String accountUuid;
    private Map<String, Object> additionalData = new HashMap<>();

    public VmInstanceInventory getVm() {
        return vm;
    }

    public void setVm(VmInstanceInventory vm) {
        this.vm = vm;
    }

    public L3NetworkInventory getL3() {
        return l3;
    }

    public void setL3(L3NetworkInventory l3) {
        this.l3 = l3;
    }

    public VmNicInventory getNic() {
        return nic;
    }

    public void setNic(VmNicInventory nic) {
        this.nic = nic;
    }

    public String getAccountUuid() {
        return accountUuid;
    }

    public void setAccountUuid(String accountUuid) {
        this.accountUuid = accountUuid;
    }

    public Map<String, Object> getAdditionalData() {
        return additionalData;
    }

    public void putData(String key, Object value) {
        additionalData.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getData(String key) {
        return (T) additionalData.get(key);
    }
}
