package org.zstack.header.vm.extensions;

import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.vm.VmInstanceInventory;

import java.util.HashMap;
import java.util.Map;

public class VmExpungeContext {
    private VmInstanceInventory vm;
    private String accountUuid;
    private ErrorCode error;
    private Map<String, Object> additionalData = new HashMap<>();

    public VmInstanceInventory getVm() { return vm; }
    public void setVm(VmInstanceInventory vm) { this.vm = vm; }
    public String getAccountUuid() { return accountUuid; }
    public void setAccountUuid(String accountUuid) { this.accountUuid = accountUuid; }
    public ErrorCode getError() { return error; }
    public void setError(ErrorCode error) { this.error = error; }
    public Map<String, Object> getAdditionalData() { return additionalData; }
    public void putData(String key, Object value) { additionalData.put(key, value); }
    @SuppressWarnings("unchecked")
    public <T> T getData(String key) { return (T) additionalData.get(key); }
}
