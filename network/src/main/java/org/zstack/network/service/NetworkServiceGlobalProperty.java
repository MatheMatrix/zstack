package org.zstack.network.service;

import org.zstack.core.GlobalProperty;
import org.zstack.core.GlobalPropertyDefinition;

/**
 * Created by weiwang on 13/05/2017.
 */
@GlobalPropertyDefinition
public class NetworkServiceGlobalProperty {

    @GlobalProperty(name="DefaultDhcpMtu.l2VlanNetwork", defaultValue = "1500")
    public static Integer DHCP_MTU_NO_VLAN;
    @GlobalProperty(name="DefaultDhcpMtu.l2VlanNetwork", defaultValue = "1500")
    public static Integer DHCP_MTU_VLAN;
    @GlobalProperty(name="DefaultDhcpMtu.l2VxlanNetwork", defaultValue = "1500")
    public static Integer DHCP_MTU_VXLAN;
    @GlobalProperty(name="DefaultDhcpMtu.dummyNetwork", defaultValue = "1500")
    public static Integer DHCP_MTU_DUMMY;
}
