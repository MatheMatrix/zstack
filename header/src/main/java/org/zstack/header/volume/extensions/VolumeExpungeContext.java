package org.zstack.header.volume.extensions;

import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.volume.VolumeInventory;

import java.util.HashMap;
import java.util.Map;

public class VolumeExpungeContext {
    private VolumeInventory volume;
    private String accountUuid;
    private ErrorCode error;
    private Map<String, Object> additionalData = new HashMap<>();

    public VolumeInventory getVolume() { return volume; }
    public void setVolume(VolumeInventory volume) { this.volume = volume; }
    public String getAccountUuid() { return accountUuid; }
    public void setAccountUuid(String accountUuid) { this.accountUuid = accountUuid; }
    public ErrorCode getError() { return error; }
    public void setError(ErrorCode error) { this.error = error; }
    public Map<String, Object> getAdditionalData() { return additionalData; }
    public void putData(String key, Object value) { additionalData.put(key, value); }
    @SuppressWarnings("unchecked")
    public <T> T getData(String key) { return (T) additionalData.get(key); }
}
