package org.zstack.server;

import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.compute.host.HostSystemTags;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.core.db.Q;
import org.zstack.header.core.Completion;
import org.zstack.header.host.AbstractHostAddExtensionPoint;
import org.zstack.header.host.HostInventory;
import org.zstack.header.server.PhysicalServerRoleVO;
import org.zstack.header.server.PhysicalServerRoleVO_;
import org.zstack.header.server.PhysicalServerVO;
import org.zstack.header.server.ServerRoleType;

/**
 * QA gap (Confluence pageId=208903964 #2) — PRD §2.5.1 requires
 * {@code PhysicalServerVO.serialNumber / manufacturer / model} to be backfilled from
 * Connect-stage signals so {@code QueryPhysicalServer} reflects basic identity
 * without waiting for the async HardwareDiscoveryQueue.
 *
 * <p>The natural candidate ({@code InitPhysicalServerCapacityFlow}) runs at the head
 * of the AddHost chain (positions 2-5/10) because RoleVO + PSC must exist BEFORE
 * the connect flow runs (ADR-012 fail-loud ordering, NB-24). That is too early —
 * {@code saveGeneralHostHardwareFacts} writes {@code HostSystemTags.SYSTEM_*} only
 * after {@code send-connect-host-message} (position 7/10). Reading SystemTag from
 * InitFlow returns null and the backfill silently no-ops.
 *
 * <p>This extension hooks {@code call-after-add-host-extension} (position 10/10),
 * which fires after the connect flow and the SystemTag writes. By then the host
 * has been added, RoleVO + PSC are persisted, and the SystemTag tokens are
 * available. Null-only update preserves any value the user supplied on path-1
 * ({@code APICreatePhysicalServer}) or set out of band.
 *
 * <p>KVM only — BM2 / Container chassis have no top-level identity columns;
 * their backfill ships once the FRU / nodeInfo discovery adapters land.
 */
public class PhysicalServerKvmIdentityBackfillExtension extends AbstractHostAddExtensionPoint {
    @Autowired
    private DatabaseFacade dbf;

    @Override
    public void afterAddHost(HostInventory host, Completion completion) {
        String hostUuid = host.getUuid();
        PhysicalServerRoleVO role = Q.New(PhysicalServerRoleVO.class)
                .eq(PhysicalServerRoleVO_.roleUuid, hostUuid)
                .eq(PhysicalServerRoleVO_.roleType, ServerRoleType.KVM_HOST.toString())
                .find();
        if (role == null) {
            completion.success();
            return;
        }

        PhysicalServerVO ps = dbf.findByUuid(role.getServerUuid(), PhysicalServerVO.class);
        if (ps == null) {
            completion.success();
            return;
        }

        boolean changed = false;
        if (ps.getSerialNumber() == null) {
            String sn = HostSystemTags.SYSTEM_SERIAL_NUMBER.getTokenByResourceUuid(
                    hostUuid, HostSystemTags.SYSTEM_SERIAL_NUMBER_TOKEN);
            if (sn != null && !sn.isEmpty()) {
                ps.setSerialNumber(sn);
                changed = true;
            }
        }
        if (ps.getManufacturer() == null) {
            String mfr = HostSystemTags.SYSTEM_MANUFACTURER.getTokenByResourceUuid(
                    hostUuid, HostSystemTags.SYSTEM_MANUFACTURER_TOKEN);
            if (mfr != null && !mfr.isEmpty()) {
                ps.setManufacturer(mfr);
                changed = true;
            }
        }
        if (ps.getModel() == null) {
            String model = HostSystemTags.SYSTEM_PRODUCT_NAME.getTokenByResourceUuid(
                    hostUuid, HostSystemTags.SYSTEM_PRODUCT_NAME_TOKEN);
            if (model != null && !model.isEmpty()) {
                ps.setModel(model);
                changed = true;
            }
        }
        if (changed) {
            dbf.update(ps);
        }
        completion.success();
    }
}
