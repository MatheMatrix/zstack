package org.zstack.network.l2.vxlan.vxlanNetwork;

import org.zstack.header.configuration.PythonClass;

/**
 * Created by weiwang on 02/03/2017.
 */
@PythonClass
public class VxlanNetworkConstant {
    @PythonClass
    public static final String VXLAN_NETWORK_TYPE = "VxlanNetwork";

    public static final int MIN_VNI = 0; // Maximum VNI value (24 bits)
    public static final int MAX_VNI = 16777215; // Maximum VNI value (24 bits)

    public static final int MIN_VLAN = 1; // Maximum VNI value (24 bits)
    public static final int MAX_VLAN = 4094; // Maximum VNI value (24 bits)
}
