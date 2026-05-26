package org.zstack.server.hardware;

import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.core.componentloader.PluginRegistry;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.core.db.Q;
import org.zstack.header.server.PhysicalServerHardwareDiscoveryExtensionPoint;
import org.zstack.header.server.PhysicalServerHardwareInfoVO;
import org.zstack.header.server.PhysicalServerHardwareInfoVO_;
import org.zstack.header.server.PhysicalServerVO;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import java.sql.Timestamp;
import java.util.List;

/**
 * Orchestrates hardware discovery from multiple sources (IPMI FRU, KVM agent, K8s node info)
 * and merges results into a single {@link UnifiedHardwareInfo} per physical server.
 *
 * <p>U16 wires up the three private discover() methods. Each delegates to a registered
 * {@link PhysicalServerHardwareDiscoveryExtensionPoint} contributor matching the source
 * tag ("IPMI_FRU" / "KVM_AGENT" / "K8S_NODEINFO"). Cross-module coupling is therefore
 * resolved through the SPI rather than direct dependency on {@code premium/baremetal2},
 * {@code plugin/kvm} or {@code premium/plugin-premium/container} — keeping
 * {@code plugin/physicalServer}'s pom (compute-only) intact.</p>
 *
 * <p>Adapter classes that implement the SPI live in their respective modules and are
 * registered via that module's spring XML; until they land, discoverHardware() returns
 * an empty UnifiedHardwareInfo with no NPE.</p>
 */
public class PhysicalServerHardwareService {
    private static final CLogger logger = Utils.getLogger(PhysicalServerHardwareService.class);

    static final String SOURCE_IPMI_FRU = "IPMI_FRU";
    static final String SOURCE_KVM_AGENT = "KVM_AGENT";
    static final String SOURCE_K8S_NODEINFO = "K8S_NODEINFO";

    @Autowired
    private DatabaseFacade dbf;

    @Autowired
    private PluginRegistry pluginRgty;

    // rule: lazy getter — never @Autowired field-initialize an extension list
    private volatile List<PhysicalServerHardwareDiscoveryExtensionPoint> exts;

    private List<PhysicalServerHardwareDiscoveryExtensionPoint> getExts() {
        if (exts == null) {
            exts = pluginRgty.getExtensionList(PhysicalServerHardwareDiscoveryExtensionPoint.class);
        }
        return exts;
    }

    /**
     * Synchronous discovery: fuses hardware info from OOB (IPMI FRU), KVM agent, and K8s node.
     * Called from HardwareDiscoveryScheduler workers or directly by the
     * APIDiscoverPhysicalServerHardwareMsg handler (U17).
     */
    public UnifiedHardwareInfo discoverHardware(String serverUuid) {
        PhysicalServerVO server = dbf.findByUuid(serverUuid, PhysicalServerVO.class);
        if (server == null) {
            logger.warn(String.format("discoverHardware: PhysicalServer[uuid:%s] not found, skipping", serverUuid));
            return new UnifiedHardwareInfo();
        }

        UnifiedHardwareInfo merged = new UnifiedHardwareInfo();

        // P1-2: drop the per-source hasActiveRole() pre-check. The SPI's discover()
        // contract now requires each impl to resolve its own role uuid exactly once
        // and return false when the server is not applicable, so a separate PSR
        // existence query at the orchestrator level is redundant. The oobAddress
        // gate for IPMI_FRU is preserved — it short-circuits the SPI call when no
        // out-of-band link is configured at all (a server-level field, not a PSR
        // query) — but BM2's adapter still validates its own role row inside discover.
        if (server.getOobAddress() != null) {
            mergeNonNull(merged, runExt(SOURCE_IPMI_FRU, server));
        }
        mergeNonNull(merged, runExt(SOURCE_KVM_AGENT, server));
        mergeNonNull(merged, runExt(SOURCE_K8S_NODEINFO, server));

        persistHardwareInfo(serverUuid, merged);
        return merged;
    }

    /**
     * Returns persisted hardware info without triggering discovery.
     * Reads {@link PhysicalServerHardwareInfoVO} and projects into the flat DTO.
     */
    public UnifiedHardwareInfo getHardware(String serverUuid) {
        PhysicalServerHardwareInfoVO row = Q.New(PhysicalServerHardwareInfoVO.class)
                .eq(PhysicalServerHardwareInfoVO_.serverUuid, serverUuid)
                .find();
        UnifiedHardwareInfo info = new UnifiedHardwareInfo();
        if (row == null) {
            return info;
        }
        info.setManufacturer(row.getManufacturer());
        info.setModel(row.getModel());
        info.setSerialNumber(row.getSerialNumber());
        info.setBiosVersion(row.getBiosVersion());
        info.setCpuModel(row.getCpuModel());
        info.setCpuSockets(row.getCpuSockets());
        info.setCpuCores(row.getCpuCores());
        info.setCpuArchitecture(row.getCpuArchitecture());
        info.setTotalMemoryBytes(row.getTotalMemoryBytes());
        return info;
    }

    // ---- private discovery (SPI dispatch) ----

    /**
     * Invoke every registered SPI impl whose source tag matches and return the
     * (possibly empty) carrier. The new SPI contract collapses isApplicable +
     * discover into one call so each adapter does at most one PSR query per
     * pass; a {@code false} return signals "not applicable" and the carrier is
     * left untouched.
     */
    private UnifiedHardwareInfo runExt(String source, PhysicalServerVO server) {
        UnifiedHardwareInfo carrier = new UnifiedHardwareInfo();
        for (PhysicalServerHardwareDiscoveryExtensionPoint ext : getExts()) {
            if (!source.equals(ext.getDiscoverSource())) {
                continue;
            }
            try {
                ext.discover(server, carrier);
            } catch (Exception e) {
                // Per SPI contract impls should not throw; defensive net so a misbehaving
                // adapter does not abort the merge for the other two sources. JVM-fatal
                // Errors (OOM, StackOverflow, LinkageError) propagate.
                logger.warn(String.format(
                        "hardware discovery extension[source:%s] threw for server[uuid:%s]: %s",
                        source, server.getUuid(), e.getMessage()));
            }
        }
        return carrier;
    }

    // ---- private helpers ----

    /**
     * Merges non-null fields from {@code source} into {@code target}.
     * Returns true iff at least one field was actually copied (used to assign the
     * "first non-empty source" tag for {@code discoverSource}).
     */
    boolean mergeNonNull(UnifiedHardwareInfo target, UnifiedHardwareInfo source) {
        if (source == null) {
            return false;
        }
        boolean changed = false;
        if (target.getManufacturer() == null && source.getManufacturer() != null) {
            target.setManufacturer(source.getManufacturer());
            changed = true;
        }
        if (target.getModel() == null && source.getModel() != null) {
            target.setModel(source.getModel());
            changed = true;
        }
        if (target.getSerialNumber() == null && source.getSerialNumber() != null) {
            target.setSerialNumber(source.getSerialNumber());
            changed = true;
        }
        if (target.getBiosVersion() == null && source.getBiosVersion() != null) {
            target.setBiosVersion(source.getBiosVersion());
            changed = true;
        }
        if (target.getCpuModel() == null && source.getCpuModel() != null) {
            target.setCpuModel(source.getCpuModel());
            changed = true;
        }
        if (target.getCpuSockets() == null && source.getCpuSockets() != null) {
            target.setCpuSockets(source.getCpuSockets());
            changed = true;
        }
        if (target.getCpuCores() == null && source.getCpuCores() != null) {
            target.setCpuCores(source.getCpuCores());
            changed = true;
        }
        if (target.getCpuArchitecture() == null && source.getCpuArchitecture() != null) {
            target.setCpuArchitecture(source.getCpuArchitecture());
            changed = true;
        }
        if (target.getTotalMemoryBytes() == null && source.getTotalMemoryBytes() != null) {
            target.setTotalMemoryBytes(source.getTotalMemoryBytes());
            changed = true;
        }
        return changed;
    }

    /**
     * Upsert merged hardware info. Existing row's non-null columns are preserved when the
     * incoming value for the same column is null (mergeNonNull at the row level).
     */
    void persistHardwareInfo(String serverUuid, UnifiedHardwareInfo info) {
        PhysicalServerHardwareInfoVO existing = Q.New(PhysicalServerHardwareInfoVO.class)
                .eq(PhysicalServerHardwareInfoVO_.serverUuid, serverUuid)
                .find();
        Timestamp now = new Timestamp(System.currentTimeMillis());

        if (existing == null) {
            PhysicalServerHardwareInfoVO row = new PhysicalServerHardwareInfoVO();
            row.setServerUuid(serverUuid);
            applyNonNull(row, info);
            row.setLastDiscoverDate(now);
            row.setCreateDate(now);
            row.setLastOpDate(now);
            dbf.persist(row);
            logger.debug(String.format("persisted hardware info for server[uuid:%s]", serverUuid));
            return;
        }
        applyNonNull(existing, info);
        existing.setLastDiscoverDate(now);
        dbf.update(existing);
        logger.debug(String.format("updated hardware info for server[uuid:%s]", serverUuid));
    }

    /**
     * Per-field copy that NEVER overwrites a non-null target field with a null source value.
     * Distinct from {@link #mergeNonNull(UnifiedHardwareInfo, UnifiedHardwareInfo)} only
     * because target/source types differ (VO row vs DTO).
     */
    private void applyNonNull(PhysicalServerHardwareInfoVO row, UnifiedHardwareInfo info) {
        if (info.getManufacturer() != null) {
            row.setManufacturer(info.getManufacturer());
        }
        if (info.getModel() != null) {
            row.setModel(info.getModel());
        }
        if (info.getSerialNumber() != null) {
            row.setSerialNumber(info.getSerialNumber());
        }
        if (info.getBiosVersion() != null) {
            row.setBiosVersion(info.getBiosVersion());
        }
        if (info.getCpuModel() != null) {
            row.setCpuModel(info.getCpuModel());
        }
        if (info.getCpuSockets() != null) {
            row.setCpuSockets(info.getCpuSockets());
        }
        if (info.getCpuCores() != null) {
            row.setCpuCores(info.getCpuCores());
        }
        if (info.getCpuArchitecture() != null) {
            row.setCpuArchitecture(info.getCpuArchitecture());
        }
        if (info.getTotalMemoryBytes() != null) {
            row.setTotalMemoryBytes(info.getTotalMemoryBytes());
        }
    }
}
