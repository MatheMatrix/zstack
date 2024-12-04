package org.zstack.sdnController.header;

import org.zstack.header.configuration.PythonClassInventory;
import org.zstack.header.host.HostInventory;
import org.zstack.header.log.NoLogging;
import org.zstack.header.network.l2.L2NetworkInventory;
import org.zstack.header.query.ExpandedQueries;
import org.zstack.header.query.ExpandedQuery;
import org.zstack.header.search.Inventory;
import org.zstack.header.search.Parent;
import org.zstack.network.l2.vxlan.vxlanNetwork.L2VxlanNetworkInventory;
import org.zstack.network.l2.vxlan.vxlanNetworkPool.VniRangeInventory;
import org.zstack.sdnController.h3cVcfc.H3cVcfcSdnControllerSystemTags;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@PythonClassInventory
@Inventory(mappingVOClass = SdnControllerHostRefVO.class)
@ExpandedQueries({
        @ExpandedQuery(expandedField = "host", inventoryClass = HostInventory.class,
                foreignKey = "uuid", expandedInventoryKey = "hostUuid"),
        @ExpandedQuery(expandedField = "sdnController", inventoryClass = SdnControllerInventory.class,
                foreignKey = "sdnControllerUuid", expandedInventoryKey = "uuid"),
})
public class SdnControllerHostRefInventory implements Serializable {
    private String sdnControllerUuid;
    private String hostUuid;
    private String vswitchType;
    private String vtepIp;
    private String physicalNics;

    public static SdnControllerHostRefInventory valueOf(SdnControllerHostRefVO vo) {
        SdnControllerHostRefInventory inv = new SdnControllerHostRefInventory();
        inv.setSdnControllerUuid(vo.getSdnControllerUuid());
        inv.setHostUuid(vo.getHostUuid());
        inv.setVswitchType(vo.getVswitchType());
        inv.setVtepIp(vo.getVtepIp());
        inv.setPhysicalNics(vo.getPhysicalNics());
        return inv;
    }

    public static List<SdnControllerHostRefInventory> valueOf(Collection<SdnControllerHostRefVO> vos) {
        List<SdnControllerHostRefInventory> lst = new ArrayList<SdnControllerHostRefInventory>(vos.size());
        for (SdnControllerHostRefVO vo : vos) {
            lst.add(SdnControllerHostRefInventory.valueOf(vo));
        }
        return lst;
    }

    public String getSdnControllerUuid() {
        return sdnControllerUuid;
    }

    public void setSdnControllerUuid(String sdnControllerUuid) {
        this.sdnControllerUuid = sdnControllerUuid;
    }

    public String getHostUuid() {
        return hostUuid;
    }

    public void setHostUuid(String hostUuid) {
        this.hostUuid = hostUuid;
    }

    public String getVswitchType() {
        return vswitchType;
    }

    public void setVswitchType(String vswitchType) {
        this.vswitchType = vswitchType;
    }

    public String getVtepIp() {
        return vtepIp;
    }

    public void setVtepIp(String vtepIp) {
        this.vtepIp = vtepIp;
    }

    public String getPhysicalNics() {
        return physicalNics;
    }

    public void setPhysicalNics(String physicalNics) {
        this.physicalNics = physicalNics;
    }
}
