package org.zstack.header.network.l2;

import java.util.*;

public class VSwitchType {
    private static Map<String, VSwitchType> types = Collections.synchronizedMap(new HashMap<String, VSwitchType>());
    private final String typeName;
    private boolean exposed = true;
    private boolean attatchTohost = true;

    public VSwitchType(String typeName) {
        this.typeName = typeName;
        if (!types.containsKey(typeName)) {
            types.put(typeName, this);
        }
    }

    public VSwitchType(String typeName, boolean exposed) {
        this(typeName);
        if (!types.containsKey(typeName)) {
            types.put(typeName, this);
        }
    }

    public boolean isExposed() {
        return exposed;
    }

    public void setExposed(boolean exposed) {
        this.exposed = exposed;
    }


    public boolean isAttatchTohost() {
        return attatchTohost;
    }

    public void setAttatchTohost(boolean attatchTohost) {
        this.attatchTohost = attatchTohost;
    }

    public static VSwitchType valueOf(String typeName) {
        VSwitchType type = types.get(typeName);
        if (type == null) {
            throw new IllegalArgumentException("VSwitchType type: " + typeName + " was not registered by any L2NetworkFactory");
        }
        return type;
    }

    @Override
    public String toString() {
        return typeName;
    }

    @Override
    public boolean equals(Object t) {
        if (!(t instanceof VSwitchType)) {
            return false;
        }

        VSwitchType type = (VSwitchType) t;
        return type.toString().equals(typeName);
    }

    @Override
    public int hashCode() {
        return typeName.hashCode();
    }

    public static Set<String> getAllTypeNames() {
        HashSet<String> exposedTypes = new HashSet<String>();
        for (VSwitchType type : types.values()) {
            if (type.isExposed()) {
                exposedTypes.add(type.toString());
            }
        }
        return exposedTypes;
    }
}
