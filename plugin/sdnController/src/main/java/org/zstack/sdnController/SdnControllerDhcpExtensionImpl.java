package org.zstack.sdnController;

import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.core.db.GLock;
import org.zstack.core.defer.Defer;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.network.l2.L2NetworkVO;
import org.zstack.header.network.l3.*;
import org.zstack.sdnController.header.SdnControllerVO;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class SdnControllerDhcpExtensionImpl implements AfterAddIpRangeExtensionPoint, IpRangeDeletionExtensionPoint {
    private static final CLogger logger = Utils.getLogger(SdnControllerDhcpExtensionImpl.class);

    @Autowired
    DatabaseFacade dbf;
    @Autowired
    SdnControllerManager sdnMgr;

    @Override
    public void afterAddIpRange(IpRangeInventory ipr, List<String> systemTags) {
        L3NetworkVO l3Vo = dbf.findByUuid(ipr.getL3NetworkUuid(), L3NetworkVO.class);
        L2NetworkVO l2Vo = dbf.findByUuid(l3Vo.getL2NetworkUuid(), L2NetworkVO.class);

        String sdnControllerUuid = SdnControllerHelper.getSdnControllerUuidFromL2Uuid(l2Vo.getUuid());
        if (sdnControllerUuid == null) {
            return;
        }

        SdnControllerVO vo = dbf.findByUuid(sdnControllerUuid, SdnControllerVO.class);
        SdnControllerFactory factory = sdnMgr.getSdnControllerFactory(vo.getVendorType());
        SdnControllerDhcp dhcp = factory.getSdnControllerDhcp(vo);
        if (dhcp == null) {
            return;
        }

        // ip range add/remove doesn't not run in queue, add a lock here
        GLock lock = new GLock(String.format("l3-%s-allocate-dhcp-ip", l3Uuid), TimeUnit.MINUTES.toSeconds(30));
        lock.lock();
        Defer.defer(lock::unlock);


        SdnControllerVO vo = dbf.findByUuid(sdnControllerUuid, SdnControllerVO.class);
        SdnControllerFactory factory = sdnMgr.getSdnControllerFactory(vo.getVendorType());
        SdnControllerDhcp dhcp = factory.getSdnControllerDhcp(vo);
        dhcp.addDHcpRange();
    }

    @Override
    public void preDeleteIpRange(IpRangeInventory ipRange) {

    }

    @Override
    public void beforeDeleteIpRange(IpRangeInventory ipRange) {

    }

    @Override
    public void afterDeleteIpRange(IpRangeInventory ipRange) {
        L3NetworkVO l3Vo = dbf.findByUuid(ipRange.getL3NetworkUuid(), L3NetworkVO.class);
        L2NetworkVO l2Vo = dbf.findByUuid(l3Vo.getL2NetworkUuid(), L2NetworkVO.class);

        String sdnControllerUuid = SdnControllerHelper.getSdnControllerUuidFromL2Uuid(l2Vo.getUuid());
        if (sdnControllerUuid == null) {
            return;
        }
    }

    @Override
    public void failedToDeleteIpRange(IpRangeInventory ipRange, ErrorCode errorCode) {

    }
}
