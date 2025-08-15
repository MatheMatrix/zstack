package org.zstack.core.upgrade;

public enum UpgradeErrors {
    GRAY_SCALE_API_NOT_ALLOWED(2000);

    private String code;

    UpgradeErrors(int id) {
        code = String.format("UPGRADE.%s", id);
    }

    @Override
    public String toString() {
        return code;
    }
}