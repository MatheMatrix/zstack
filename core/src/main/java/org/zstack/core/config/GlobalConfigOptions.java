package org.zstack.core.config;

import java.util.List;

public class GlobalConfigOptions {
    private List<String> validValue;
    private Long numberGreaterThanOrEqual;
    private Long numberLessThanOrEqual;

    public List<String> getValidValue() {
        return validValue;
    }

    public void setValidValue(List<String> validValue) {
        this.validValue = validValue;
    }

    public Long getNumberGreaterThanOrEqual() {
        return numberGreaterThanOrEqual;
    }

    public void setNumberGreaterThanOrEqual(Long numberGreaterThanOrEqual) {
        this.numberGreaterThanOrEqual = numberGreaterThanOrEqual;
    }

    public Long getNumberLessThanOrEqual() {
        return numberLessThanOrEqual;
    }

    public void setNumberLessThanOrEqual(Long numberLessThanOrEqual) {
        this.numberLessThanOrEqual = numberLessThanOrEqual;
    }
}
