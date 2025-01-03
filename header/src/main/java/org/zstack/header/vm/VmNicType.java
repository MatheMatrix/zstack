package org.zstack.header.vm;

import java.util.*;

public class VmNicType {
    public enum VmNicSubType {
        NONE,
        SRIOV,
        VHOSTUSER,
    }
    private static Map<String, VmNicType> types = Collections.synchronizedMap(new HashMap<>());

    private final String typeName;
    private boolean hasAddon = false;
    private boolean useSRIOV = false;

    public VmNicType(String typeName) {
        this.typeName = typeName;
        types.put(typeName, this);
    }

    public static VmNicType valueOf(String typeName) {
        VmNicType type = types.get(typeName);
        if (type == null) {
            throw new IllegalArgumentException("VmNicType type: " + typeName + " was not registered by any VmNicFactory");
        }
        return type;
    }

    public boolean isHasAddon() {
        return hasAddon;
    }

    public void setHasAddon(boolean hasAddon) {
        this.hasAddon = hasAddon;
    }

    public boolean isUseSRIOV() {
        return useSRIOV;
    }

    public void setUseSRIOV(boolean useSRIOV) {
        this.useSRIOV = useSRIOV;
    }

    @Override
    public String toString() {
        return typeName;
    }

    @Override
    public boolean equals(Object t) {
        if (!(t instanceof VmNicType)) {
            return false;
        }

        VmNicType type = (VmNicType) t;
        return type.toString().equals(typeName);
    }

    @Override
    public int hashCode() {
        return typeName.hashCode();
    }
}
