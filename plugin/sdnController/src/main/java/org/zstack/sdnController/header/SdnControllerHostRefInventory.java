package org.zstack.sdnController.header;

import org.zstack.header.configuration.PythonClassInventory;
import org.zstack.header.host.HostInventory;
import org.zstack.header.query.ExpandedQueries;
import org.zstack.header.query.ExpandedQuery;
import org.zstack.header.search.Inventory;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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
    private String vSwitchType;
    private String vtepIp;
    private String nicPciAddresses;
    private String nicDrivers;

    public static SdnControllerHostRefInventory valueOf(SdnControllerHostRefVO vo) {
        SdnControllerHostRefInventory inv = new SdnControllerHostRefInventory();
        inv.setSdnControllerUuid(vo.getSdnControllerUuid());
        inv.setHostUuid(vo.getHostUuid());
        inv.setvSwitchType(vo.getvSwitchType());
        inv.setVtepIp(vo.getVtepIp());
        inv.setNicPciAddresses(vo.getNicPciAddresses());
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

    public String getvSwitchType() {
        return vSwitchType;
    }

    public void setvSwitchType(String vSwitchType) {
        this.vSwitchType = vSwitchType;
    }

    public String getVtepIp() {
        return vtepIp;
    }

    public void setVtepIp(String vtepIp) {
        this.vtepIp = vtepIp;
    }

    public String getNicPciAddresses() {
        return nicPciAddresses;
    }

    public void setNicPciAddresses(String nicPciAddresses) {
        this.nicPciAddresses = nicPciAddresses;
    }
}
