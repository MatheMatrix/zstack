package org.zstack.header.vm;

import org.zstack.header.network.l2.L2NetworkConstant;
import org.zstack.header.network.l2.VSwitchType;

import java.util.*;

public class VmNicType {
    public enum VmNicSubType {
        NONE,
        SRIOV,
        VHOSTUSER,
    }
    private static Map<String, VmNicType> types = Collections.synchronizedMap(new HashMap<>());
    /*
    * vswitchType                   VmNicType
    * LinuxBridge
    *   subType = SRIOV             VF
    *   subType = NONE              VNIC
    * OvsDpdk
    *   subType = VHOSTUSER         userdpdkvhostuserclient
    *   subType = NONE              vDPA
    * MacVlan                       MacVlan
    * Ovn-netdev                    OvnDpdkVhostUser         #####use for ovn dpdk
    * TfL2Network                   TfL2Network
    *  */
    private static Map<VSwitchType, List<VmNicType>> vswtichNicTypeMap = Collections.synchronizedMap(new HashMap<>());

    private final String typeName;
    private final VSwitchType vSwitchType;
    private VmNicSubType subType = VmNicSubType.NONE;
    private boolean hasAddon = false;

    public VmNicType(String typeName, VSwitchType vswitchType) {
        this.typeName = typeName;
        types.put(typeName, this);
        vSwitchType = vswitchType;
        vswtichNicTypeMap.computeIfAbsent(vswitchType, k -> new ArrayList<VmNicType>()).add(this);
    }

    public static VmNicType valueOf(String typeName) {
        VmNicType type = types.get(typeName);
        if (type == null) {
            throw new IllegalArgumentException("VmNicType type: " + typeName + " was not registered by any VmNicFactory");
        }
        return type;
    }

    public VmNicSubType getSubType() {
        return subType;
    }

    public void setSubType(VmNicSubType subType) {
        this.subType = subType;
    }

    public boolean isHasAddon() {
        return hasAddon;
    }

    public void setHasAddon(boolean hasAddon) {
        this.hasAddon = hasAddon;
    }


    public boolean isUseSRIOV() {
        if (vSwitchType.toString().equals(L2NetworkConstant.VSWITCH_TYPE_LINUX_BRIDGE)
                && subType == VmNicSubType.SRIOV) {
            return true;
        } else if (vSwitchType.toString().equals(L2NetworkConstant.VSWITCH_TYPE_OVS_DPDK)
                && subType == VmNicSubType.NONE) {
            /* vdpa need sriov pci device */
            return true;
        }

        return false;
    }

    public static VmNicType getNicType(VSwitchType vswitchType,  VmNicType.VmNicSubType subType) {
        List<VmNicType> nicTypes = vswtichNicTypeMap.get(vswitchType);
        if (nicTypes == null || nicTypes.isEmpty()) {
            throw new IllegalArgumentException(String.format("no VmNicType for vSwitchType:%s", vswitchType));
        }

        if (nicTypes.size() == 1) {
            return nicTypes.get(0);
        }

        if (vswitchType.toString().equals(L2NetworkConstant.VSWITCH_TYPE_LINUX_BRIDGE)) {
            if (subType != VmNicSubType.SRIOV) {
                subType = VmNicSubType.NONE;
            }
        }

        if (vswitchType.toString().equals(L2NetworkConstant.VSWITCH_TYPE_OVS_DPDK)) {
            if (subType != VmNicSubType.VHOSTUSER) {
                subType = VmNicSubType.NONE;
            }
        }

        for (VmNicType type : nicTypes) {
            if (type.subType == subType) {
                return type;
            }
        }

        throw new IllegalArgumentException(String.format("no VmNicType for vSwitchType:%s, subType: %s",
                vswitchType, subType));
    }


    @Override
    public String toString() {
        return typeName;
    }

    @Override
    public boolean equals(Object t) {
        if (!(t instanceof VmNicType)) {
            return false;
        }

        VmNicType type = (VmNicType) t;
        return type.toString().equals(typeName);
    }

    @Override
    public int hashCode() {
        return typeName.hashCode();
    }
}
