package org.zstack.storage.zbs;

public class ZbsCpuIsolationFact {
    private String isolationState;
    private String configuredCpuSet;
    private String effectiveCpuSet;
    private Long memory;
    private Integer coveredServiceCount;
    private Integer expectedServiceCount;
    private boolean serviceReady;

    public String getIsolationState() {
        return isolationState;
    }

    public void setIsolationState(String isolationState) {
        this.isolationState = isolationState;
    }

    public String getConfiguredCpuSet() {
        return configuredCpuSet;
    }

    public void setConfiguredCpuSet(String configuredCpuSet) {
        this.configuredCpuSet = configuredCpuSet;
    }

    public String getEffectiveCpuSet() {
        return effectiveCpuSet;
    }

    public void setEffectiveCpuSet(String effectiveCpuSet) {
        this.effectiveCpuSet = effectiveCpuSet;
    }

    public Long getMemory() {
        return memory;
    }

    public void setMemory(Long memory) {
        this.memory = memory;
    }

    public Integer getCoveredServiceCount() {
        return coveredServiceCount;
    }

    public void setCoveredServiceCount(Integer coveredServiceCount) {
        this.coveredServiceCount = coveredServiceCount;
    }

    public Integer getExpectedServiceCount() {
        return expectedServiceCount;
    }

    public void setExpectedServiceCount(Integer expectedServiceCount) {
        this.expectedServiceCount = expectedServiceCount;
    }

    public boolean isServiceReady() {
        return serviceReady;
    }

    public void setServiceReady(boolean serviceReady) {
        this.serviceReady = serviceReady;
    }

}
