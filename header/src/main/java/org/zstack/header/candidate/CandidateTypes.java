package org.zstack.header.candidate;

public interface CandidateTypes {
    String HOST = "Host";
    String PRIMARY_STORAGE = "PrimaryStorage";
    String GPU_DEVICE_SPEC = "GpuDeviceSpec";
    String GPU_DEVICE = "GpuDevice";
    String PCI_DEVICE_SPEC = "PciDeviceSpec";
    String PCI_DEVICE = "PciDevice";
    String MDEV_DEVICE_SPEC = "MdevDeviceSpec";
    String MDEV_DEVICE = "MdevDevice";
    String NETWORK = "Network";
    String VOLUME = "Volume";
    String ISO = "Iso";

    String SELECTED = "SELECTED";
    String REJECTED = "REJECTED";
    String SKIPPED = "SKIPPED";
}
