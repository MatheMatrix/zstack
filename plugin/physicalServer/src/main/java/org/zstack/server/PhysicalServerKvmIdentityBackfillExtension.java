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
