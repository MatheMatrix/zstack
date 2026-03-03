package org.zstack.header.vm.extensions;

import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.vm.VmInstanceInventory;
import org.zstack.header.vm.VmNicInventory;

import java.util.HashMap;
import java.util.Map;

/**
 * Context object for VM NIC detachment operations.
 *
 * <p>This context is passed through all lifecycle phases (pre/before/after/failedTo)
 * allowing extensions to share state between phases.</p>
 */
public class VmDetachNicContext {
    private VmInstanceInventory vm;
    private VmNicInventory nic;
    private String accountUuid;
    private ErrorCode error;
    private Map<String, Object> additionalData = new HashMap<>();

    public VmInstanceInventory getVm() {
        return vm;
    }

    public void setVm(VmInstanceInventory vm) {
        this.vm = vm;
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

    public ErrorCode getError() {
        return error;
    }

    public void setError(ErrorCode error) {
        this.error = error;
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
