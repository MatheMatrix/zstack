package org.zstack.sdnController;

import org.zstack.header.network.l2.L2NetworkVO;
import org.zstack.header.tag.TagDefinition;
import org.zstack.tag.PatternedSystemTag;

@TagDefinition
public class SdnControllerSystemTags {
    public static String L2_NETWORK_OVN_UUID_TOKEN = "OvnControllerUuid";
    public static PatternedSystemTag L2_NETWORK_OVN_UUID = new PatternedSystemTag(String.format("OvnControllerUuid::{%s}", L2_NETWORK_OVN_UUID_TOKEN), L2NetworkVO.class);
}
