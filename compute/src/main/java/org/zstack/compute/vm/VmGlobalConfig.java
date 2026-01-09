package org.zstack.compute.vm;

import org.zstack.core.config.GlobalConfig;
import org.zstack.core.config.GlobalConfigDef;
import org.zstack.core.config.GlobalConfigDefinition;
import org.zstack.core.config.GlobalConfigValidation;
import org.zstack.header.cluster.ClusterVO;
import org.zstack.header.vm.VmNicVO;
import org.zstack.resourceconfig.BindResourceConfig;
import org.zstack.header.vm.VmInstanceConstant;
import org.zstack.header.vm.VmInstanceVO;

@GlobalConfigDefinition
public class VmGlobalConfig {
    public static final String CATEGORY = "vm";

    @GlobalConfigValidation
    public static GlobalConfig DELETE_DATA_VOLUME_ON_VM_DESTROY = new GlobalConfig(CATEGORY, "dataVolume.deleteOnVmDestroy");
    @GlobalConfigValidation
    public static GlobalConfig UPDATE_INSTANCE_OFFERING_TO_NULL_WHEN_DELETING = new GlobalConfig(CATEGORY, "instanceOffering.setNullWhenDeleting");
    @GlobalConfigValidation(validValues = {"Direct","Delay", "Never"})
    public static GlobalConfig VM_DELETION_POLICY = new GlobalConfig(CATEGORY, "deletionPolicy");
    @GlobalConfigValidation(numberGreaterThan = 0)
    public static GlobalConfig VM_EXPUNGE_PERIOD = new GlobalConfig(CATEGORY, "expungePeriod");
    @GlobalConfigValidation(numberGreaterThan = 1)
    public static GlobalConfig VM_EXPUNGE_INTERVAL = new GlobalConfig(CATEGORY, "expungeInterval");
    @GlobalConfigValidation(validValues = {"true", "false"})
    public static GlobalConfig VM_CLEAN_TRAFFIC = new GlobalConfig(CATEGORY, "cleanTraffic");
    @GlobalConfigValidation(validValues = {"cirrus","vga", "qxl", "virtio"})
    @BindResourceConfig(value = {VmInstanceVO.class, ClusterVO.class})
    public static GlobalConfig VM_VIDEO_TYPE = new GlobalConfig(CATEGORY, "videoType");
    @GlobalConfigValidation(validValues = {"ich6","ich9", "ac97"})
    @BindResourceConfig(value = {VmInstanceVO.class, ClusterVO.class})
    public static GlobalConfig VM_SOUND_TYPE = new GlobalConfig(CATEGORY, "soundType");
    @GlobalConfigValidation(validValues = {"off","all", "filter"})
    @BindResourceConfig(value = {VmInstanceVO.class})
    public static GlobalConfig VM_SPICE_STREAMING_MODE= new GlobalConfig(CATEGORY, "spiceStreamingMode");
    @GlobalConfigValidation
    @BindResourceConfig(value = {VmInstanceVO.class, ClusterVO.class})
    public static GlobalConfig NUMA = new GlobalConfig(CATEGORY, "numa");
    @GlobalConfigValidation
    @BindResourceConfig(value = {VmInstanceVO.class, ClusterVO.class})
    public static GlobalConfig VM_MAX_VCPU = new GlobalConfig(CATEGORY, "vm.max.vcpu");
    @GlobalConfigValidation
    public static GlobalConfig VM_BOOT_MENU = new GlobalConfig(CATEGORY, "bootMenu");
    @GlobalConfigValidation(numberGreaterThan = 0, numberLessThan = 65535)
    @BindResourceConfig(value = {VmInstanceVO.class})
    public static GlobalConfig VM_BOOT_MENU_SPLASH_TIMEOUT = new GlobalConfig(CATEGORY, "bootMenuSplashTimeout");
    @GlobalConfigValidation(validValues = {"true", "false"})
    @BindResourceConfig(value = {VmInstanceVO.class, ClusterVO.class})
    public static GlobalConfig KVM_HIDDEN_STATE = new GlobalConfig(CATEGORY, "kvmHiddenState");
    @GlobalConfigValidation(validValues = {"true", "false"})
    @BindResourceConfig(value = {VmInstanceVO.class})
    public static GlobalConfig VM_PORT_OFF = new GlobalConfig(CATEGORY, "vmPortOff");
    @GlobalConfigValidation(validValues = {"true", "false"})
    @BindResourceConfig(value = {VmInstanceVO.class, ClusterVO.class})
    public static GlobalConfig EMULATE_HYPERV = new GlobalConfig(CATEGORY, "emulateHyperV");
    @GlobalConfigValidation(validValues = {"true", "false"})
    public static GlobalConfig ADDITIONAL_QMP = new GlobalConfig(CATEGORY, "additionalQmp");

    @GlobalConfigValidation(validValues = {"true","false"})
    public static GlobalConfig MULTI_VNIC_SUPPORT = new GlobalConfig(CATEGORY, "multivNic.support");

    @GlobalConfigValidation(numberGreaterThan = 0, numberLessThan = VmInstanceConstant.MAXIMUM_CDROM_NUMBER)
    public static GlobalConfig VM_DEFAULT_CD_ROM_NUM = new GlobalConfig(CATEGORY, "vmDefaultCdRomNum");

    @GlobalConfigValidation(numberGreaterThan = 1, numberLessThan = VmInstanceConstant.MAXIMUM_CDROM_NUMBER)
    public static GlobalConfig MAXIMUM_CD_ROM_NUM = new GlobalConfig(CATEGORY, "maximumCdRomNum");

    @GlobalConfigValidation(inNumberRange = {0, 28})
    public static GlobalConfig PCIE_PORT_NUMS = new GlobalConfig(CATEGORY, "pciePortNums");

    @GlobalConfigValidation(validValues = {"Hard", "Soft"})
    @BindResourceConfig({VmInstanceVO.class})
    public static GlobalConfig RESOURCE_BINDING_STRATEGY = new GlobalConfig(CATEGORY, "resourceBinding.strategy");

    @GlobalConfigValidation(validValues = {"None", "Preserve","Reboot","Shutdown"})
    @BindResourceConfig({VmInstanceVO.class, ClusterVO.class})
    public static GlobalConfig VM_CRASH_STRATEGY = new GlobalConfig(CATEGORY, "crash.strategy");

    @GlobalConfigValidation(numberGreaterThan = 0)
    public static GlobalConfig VM_REBOOT_THRESHOLD_DURATION = new GlobalConfig(CATEGORY, "crash.rebootThreshold.duration");

    @GlobalConfigValidation(numberGreaterThan = 0)
    public static GlobalConfig VM_REBOOT_THRESHOLD_TIMES = new GlobalConfig(CATEGORY, "crash.rebootThreshold.times");

    @GlobalConfigValidation(validValues = {"Auto", "All"})
    @BindResourceConfig({VmInstanceVO.class, ClusterVO.class})
    public static GlobalConfig RESOURCE_BINDING_SCENE = new GlobalConfig(CATEGORY, "resourceBinding.Scene");

    @GlobalConfigValidation(inNumberRange = {1, 256})
    @BindResourceConfig({VmNicVO.class, VmInstanceVO.class, ClusterVO.class})
    public static GlobalConfig VM_NIC_MULTIQUEUE_NUM = new GlobalConfig(CATEGORY, "nicMultiQueueNum");

    @GlobalConfigValidation(numberGreaterThan = 1)
    public static GlobalConfig UNKNOWN_GC_INTERVAL = new GlobalConfig(CATEGORY, "set.unknown.gc.interval");

    @GlobalConfigDef(defaultValue = "Microsoft Hv", type = String.class, description = "set vendor_id")
    @BindResourceConfig(value = {VmInstanceVO.class, ClusterVO.class})
    public static GlobalConfig VENDOR_ID = new GlobalConfig(CATEGORY, "vendorId");

    @BindResourceConfig(value = {VmInstanceVO.class})
    @GlobalConfigValidation(validValues = {"guest", "host"})
    public static GlobalConfig VM_CLOCK_TRACK = new GlobalConfig(CATEGORY, "vm.clock.track");

    @BindResourceConfig(value = {VmInstanceVO.class})
    @GlobalConfigValidation(validValues = {"0", "60", "600", "1800", "3600", "7200", "21600", "43200", "86400"})
    @GlobalConfigDef(defaultValue = "0", type = Integer.class, description = "vm clock sync interval in seconds")
    public static GlobalConfig VM_CLOCK_SYNC_INTERVAL_IN_SECONDS = new GlobalConfig(CATEGORY, "vm.clock.sync.interval.in.seconds");

    @BindResourceConfig(value = {VmInstanceVO.class})
    @GlobalConfigValidation(validValues = {"true", "false"})
    @GlobalConfigDef(defaultValue = "false", type = Boolean.class, description = "sync clock after vm resume")
    public static GlobalConfig VM_CLOCK_SYNC_AFTER_VM_RESUME = new GlobalConfig(CATEGORY, "vm.clock.sync.after.vm.resume");

    @GlobalConfigValidation(validValues = {"true", "false"})
    public static GlobalConfig ENABLE_UEFI_SECURE_BOOT = new GlobalConfig(CATEGORY, "enable.uefi.secure.boot");

    @GlobalConfigValidation(validValues = {"true", "false"})
    public static GlobalConfig ENABLE_VM_DEVICE_ADDRESS_RECORDING = new GlobalConfig(CATEGORY, "enable.vm.address.recording");

    @GlobalConfigValidation(validValues = {"true", "false"})
    public static GlobalConfig ENABLE_VM_INTERNAL_IP_OVERWRITE = new GlobalConfig(CATEGORY, "enable.vm.internal.ip.overwrite");

    @GlobalConfigValidation(validValues = {"true", "false"})
    public static GlobalConfig UNIQUE_VM_NAME = new GlobalConfig(CATEGORY, "uniqueVmName");

    @BindResourceConfig(value = {VmInstanceVO.class, ClusterVO.class})
    @GlobalConfigValidation(validValues = {"true", "false"})
    @GlobalConfigDef(defaultValue = "true", type = Boolean.class, description = "vm.ha.across.clusters")
    public static GlobalConfig VM_HA_ACROSS_CLUSTERS = new GlobalConfig(CATEGORY, "vm.ha.across.clusters");
    @GlobalConfigDef(defaultValue = "AuthenticAMD", type = String.class, description = "set vm cpuid vendor")
    @GlobalConfigValidation(validValues = {"None", "AuthenticAMD"})
    @BindResourceConfig(value = {VmInstanceVO.class})
    public static GlobalConfig VM_CPUID_VENDOR = new GlobalConfig(CATEGORY, "vm.cpuid.vendor");

    @GlobalConfigValidation(numberGreaterThan = 1)
    public static GlobalConfig GC_INTERVAL = new GlobalConfig(CATEGORY, "deletion.gcInterval");

    @GlobalConfigValidation(validValues = {"true", "false"})
    public static GlobalConfig VM_METADATA = new GlobalConfig(CATEGORY, "vm.metadata");

    @GlobalConfigDef(defaultValue = "5", type = Integer.class,
            description = "Max concurrent metadata writes per primary storage per MN")
    @GlobalConfigValidation(numberGreaterThan = 0)
    public static GlobalConfig VM_METADATA_PS_MAX_CONCURRENT = new GlobalConfig(CATEGORY, "vm.metadata.ps.maxConcurrent");

    @GlobalConfigDef(defaultValue = "10", type = Integer.class,
            description = "Max concurrent VM metadata updates globally per MN")
    @GlobalConfigValidation(numberGreaterThan = 0)
    public static GlobalConfig VM_METADATA_GLOBAL_MAX_CONCURRENT = new GlobalConfig(CATEGORY, "vm.metadata.global.maxConcurrent");

    @GlobalConfigDef(defaultValue = "10", type = Integer.class,
            description = "Initial GC delay in seconds after API success")
    @GlobalConfigValidation(numberGreaterThan = 0)
    public static GlobalConfig VM_METADATA_GC_INITIAL_DELAY_SEC = new GlobalConfig(CATEGORY, "vm.metadata.gc.initialDelaySec");

    @GlobalConfigDef(defaultValue = "5", type = Integer.class,
            description = "Max retry count before giving up metadata flush")
    @GlobalConfigValidation(numberGreaterThan = 0)
    public static GlobalConfig VM_METADATA_MAX_RETRY = new GlobalConfig(CATEGORY, "vm.metadata.maxRetry");

    @GlobalConfigDef(defaultValue = "5", type = Long.class,
            description = "Dirty poller interval in seconds")
    @GlobalConfigValidation(numberGreaterThan = 0)
    public static GlobalConfig VM_METADATA_DIRTY_POLL_INTERVAL = new GlobalConfig(CATEGORY, "vm.metadata.dirty.pollIntervalSec");

    @GlobalConfigDef(defaultValue = "20", type = Integer.class,
            description = "Max dirty rows to claim per poller cycle")
    @GlobalConfigValidation(numberGreaterThan = 0)
    public static GlobalConfig VM_METADATA_DIRTY_BATCH_SIZE = new GlobalConfig(CATEGORY, "vm.metadata.dirty.batchSize");

    @GlobalConfigDef(defaultValue = "300", type = Long.class,
            description = "Path fingerprint check interval in seconds")
    @GlobalConfigValidation(numberGreaterThan = 0)
    public static GlobalConfig VM_METADATA_PATH_CHECK_INTERVAL = new GlobalConfig(CATEGORY, "vm.metadata.pathCheck.intervalSec");

    @GlobalConfigDef(defaultValue = "500", type = Integer.class,
            description = "Path fingerprint check keyset pagination batch size")
    @GlobalConfigValidation(numberGreaterThan = 0)
    public static GlobalConfig VM_METADATA_PATH_CHECK_BATCH_SIZE = new GlobalConfig(CATEGORY, "vm.metadata.pathCheck.batchSize");

    @GlobalConfigDef(defaultValue = "600", type = Long.class,
            description = "Delay in seconds before full refresh after upgrade, waiting for rolling upgrade to complete")
    @GlobalConfigValidation(numberGreaterThan = 0)
    public static GlobalConfig VM_METADATA_UPGRADE_REFRESH_DELAY = new GlobalConfig(CATEGORY, "vm.metadata.upgrade.refreshDelaySec");

    @GlobalConfigDef(defaultValue = "1000", type = Integer.class,
            description = "Upgrade full refresh SQL batch size")
    @GlobalConfigValidation(numberGreaterThan = 0)
    public static GlobalConfig VM_METADATA_UPGRADE_REFRESH_BATCH_SIZE = new GlobalConfig(CATEGORY, "vm.metadata.upgrade.refreshBatchSize");

    @GlobalConfigDef(defaultValue = "5", type = Long.class,
            description = "Delay in seconds after nodeLeft before takeover, reduces zombie MN race condition")
    @GlobalConfigValidation(numberGreaterThan = 0)
    public static GlobalConfig VM_METADATA_NODE_LEFT_DELAY = new GlobalConfig(CATEGORY, "vm.metadata.nodeLeft.delaySec");

    @GlobalConfigDef(defaultValue = "1800", type = Long.class,
            description = "MetadataStaleRecoveryTask scan interval in seconds")
    @GlobalConfigValidation(numberGreaterThan = 0)
    public static GlobalConfig VM_METADATA_STALE_RECOVERY_INTERVAL = new GlobalConfig(CATEGORY, "vm.metadata.staleRecovery.intervalSec");

    @GlobalConfigDef(defaultValue = "100", type = Integer.class,
            description = "MetadataStaleRecoveryTask rows per scan batch")
    @GlobalConfigValidation(numberGreaterThan = 0)
    public static GlobalConfig VM_METADATA_STALE_RECOVERY_BATCH_SIZE = new GlobalConfig(CATEGORY, "vm.metadata.staleRecovery.batchSize");

    @GlobalConfigDef(defaultValue = "10", type = Integer.class,
            description = "Max consecutive stale recovery cycles per VM before circuit-break")
    @GlobalConfigValidation(numberGreaterThan = 0)
    public static GlobalConfig VM_METADATA_STALE_RECOVERY_MAX_CYCLES = new GlobalConfig(CATEGORY, "vm.metadata.staleRecovery.maxCycles");

    @GlobalConfigDef(defaultValue = "45", type = Long.class,
            description = "Pending API timeout cleanup threshold in minutes")
    @GlobalConfigValidation(numberGreaterThan = 0)
    public static GlobalConfig VM_METADATA_PENDING_API_TIMEOUT = new GlobalConfig(CATEGORY, "vm.metadata.pendingApi.timeoutMinutes");

    @GlobalConfigDef(defaultValue = "10", type = Integer.class,
            description = "Exponential backoff base delay in seconds")
    @GlobalConfigValidation(numberGreaterThan = 0)
    public static GlobalConfig VM_METADATA_RETRY_BASE_DELAY = new GlobalConfig(CATEGORY, "vm.metadata.retry.baseDelaySeconds");

    @GlobalConfigDef(defaultValue = "10", type = Integer.class,
            description = "Exponential backoff max exponent")
    @GlobalConfigValidation(numberGreaterThan = 0)
    public static GlobalConfig VM_METADATA_RETRY_MAX_EXPONENT = new GlobalConfig(CATEGORY, "vm.metadata.retry.maxExponent");

    @GlobalConfigDef(defaultValue = "200", type = Integer.class,
            description = "Batch size per round when enabling metadata (false to true init)")
    @GlobalConfigValidation(numberGreaterThan = 0)
    public static GlobalConfig VM_METADATA_INIT_BATCH_SIZE = new GlobalConfig(CATEGORY, "vm.metadata.init.batchSize");

    @GlobalConfigDef(defaultValue = "5", type = Long.class,
            description = "Delay in seconds between init batches to prevent IO storm")
    @GlobalConfigValidation(numberGreaterThan = 0)
    public static GlobalConfig VM_METADATA_INIT_BATCH_DELAY = new GlobalConfig(CATEGORY, "vm.metadata.init.batchDelaySec");

    @GlobalConfigDef(defaultValue = "3600", type = Long.class,
            description = "Orphan metadata detection interval in seconds")
    @GlobalConfigValidation(numberGreaterThan = 0)
    public static GlobalConfig VM_METADATA_ORPHAN_CHECK_INTERVAL = new GlobalConfig(CATEGORY, "vm.metadata.orphanCheck.intervalSec");

    @GlobalConfigDef(defaultValue = "15", type = Long.class,
            description = "Zombie claim threshold in minutes: claimed dirty rows older than this are released")
    @GlobalConfigValidation(numberGreaterThan = 0)
    public static GlobalConfig VM_METADATA_ZOMBIE_CLAIM_THRESHOLD = new GlobalConfig(CATEGORY, "vm.metadata.zombieClaim.thresholdMinutes");

    @GlobalConfigDef(defaultValue = "30", type = Long.class,
            description = "Stale claim threshold in minutes for background recovery task")
    @GlobalConfigValidation(numberGreaterThan = 0)
    public static GlobalConfig VM_METADATA_STALE_CLAIM_THRESHOLD = new GlobalConfig(CATEGORY, "vm.metadata.staleClaim.thresholdMinutes");

    @GlobalConfigDef(defaultValue = "10", type = Long.class,
            description = "Inline stale claim takeover threshold in minutes for triggerFlushForVm hot path")
    @GlobalConfigValidation(numberGreaterThan = 0)
    public static GlobalConfig VM_METADATA_TRIGGER_FLUSH_STALE = new GlobalConfig(CATEGORY, "vm.metadata.triggerFlush.staleMinutes");

    @GlobalConfigDef(defaultValue = "3", type = Integer.class,
            description = "Max retry count for deleteMetadata in ExpungeVmInstanceFlow")
    @GlobalConfigValidation(numberGreaterThan = 0)
    public static GlobalConfig VM_METADATA_DELETE_MAX_RETRY = new GlobalConfig(CATEGORY, "vm.metadata.delete.maxRetry");

    @GlobalConfigDef(defaultValue = "30", type = Long.class,
            description = "Base delay in seconds for deleteMetadata retry backoff")
    @GlobalConfigValidation(numberGreaterThan = 0)
    public static GlobalConfig VM_METADATA_DELETE_BASE_DELAY = new GlobalConfig(CATEGORY, "vm.metadata.delete.baseDelaySec");

    @GlobalConfigDef(defaultValue = "", type = String.class,
            description = "Last completed upgrade refresh version, prevents duplicate triggers across MNs. Internal use only")
    public static GlobalConfig VM_METADATA_LAST_REFRESH_VERSION = new GlobalConfig(CATEGORY, "vm.metadata.lastRefreshVersion");
}
