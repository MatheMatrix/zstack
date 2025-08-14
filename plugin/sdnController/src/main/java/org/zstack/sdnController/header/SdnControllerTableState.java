package org.zstack.sdnController.header;

public enum SdnControllerTableState {
    Enabled("Enabled"),
    Disabled("Disabled");

    public final String value;
    private SdnControllerTableState(String value) {
        this.value = value;
    }


    @Override
    public String toString() {
        return value;
    }

    public static SdnControllerTableState fromValue(String value) {
        if (value == null) {
            return null;
        }

        for (SdnControllerTableState state : SdnControllerTableState.values()) {
            if (state.value.equals(value)) {
                return state;
            }
        }

        throw new IllegalArgumentException("unknown: '" + value + "'");
    }
}
