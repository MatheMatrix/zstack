package org.zstack.compute.vm;

public enum VmMacAddressSchemaType {
    Random("random"),
    Ip("ip");

    private VmMacAddressSchemaType(String type) {
        this.type = type;
    }

    private final String type;

    public String getType() {
        return type;
    }
}
