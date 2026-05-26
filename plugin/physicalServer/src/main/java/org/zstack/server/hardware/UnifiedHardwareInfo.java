package org.zstack.server.hardware;

import org.zstack.header.server.PhysicalServerHardwareDiscoveryExtensionPoint.HardwareInfoCarrier;

/**
 * Flat DTO representing aggregated hardware information for a PhysicalServer.
 * Per role SPI PRD §2.5b NB-19 (2026-04-21).
 *
 * TODO(U15 / v1.1+): Add per-DIMM and per-disk List structures per FR-004.
 * Deferred fields block:
 *   private List<DimmInfo> memoryModules;   // per-DIMM: slot, size, speed, type, manufacturer
 *   private List<DiskInfo> disks;           // per-disk: device, size, model, rotational, serial
 *   private List<NicInfo> nics;             // per-NIC: name, mac, speed, pci
 *   private List<GpuInfo> gpus;             // per-GPU: model, vram, pci
 */
public class UnifiedHardwareInfo implements HardwareInfoCarrier {

    // System
    private String manufacturer;
    private String model;
    private String serialNumber;
    private String biosVersion;

    // CPU summary
    private String cpuModel;
    private Integer cpuSockets;
    private Integer cpuCores;
    private String cpuArchitecture;   // x86_64 / aarch64

    // Memory summary
    private Long totalMemoryBytes;

    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public String getBiosVersion() {
        return biosVersion;
    }

    public void setBiosVersion(String biosVersion) {
        this.biosVersion = biosVersion;
    }

    public String getCpuModel() {
        return cpuModel;
    }

    public void setCpuModel(String cpuModel) {
        this.cpuModel = cpuModel;
    }

    public Integer getCpuSockets() {
        return cpuSockets;
    }

    @Override
    public void setCpuSockets(Integer cpuSockets) {
        this.cpuSockets = cpuSockets;
    }

    public Integer getCpuCores() {
        return cpuCores;
    }

    @Override
    public void setCpuCores(Integer cpuCores) {
        this.cpuCores = cpuCores;
    }

    public String getCpuArchitecture() {
        return cpuArchitecture;
    }

    public void setCpuArchitecture(String cpuArchitecture) {
        this.cpuArchitecture = cpuArchitecture;
    }

    public Long getTotalMemoryBytes() {
        return totalMemoryBytes;
    }

    public void setTotalMemoryBytes(Long totalMemoryBytes) {
        this.totalMemoryBytes = totalMemoryBytes;
    }
}
