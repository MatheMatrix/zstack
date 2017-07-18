package org.zstack.appliancevm;

import org.zstack.header.network.l3.L3NetworkVO;
import org.zstack.header.tag.TagDefinition;
import org.zstack.tag.PatternedSystemTag;

/**
 * Created by weiwang on 17/05/2017.
 */
@TagDefinition
public class ApplianceVmSystemTag {

    public static final String CONFIG_DRIVE_TOKEN = "configDrive";
    public static PatternedSystemTag CONFIG_DRIVE = new PatternedSystemTag(
            String.format("configDrive::{%s}", CONFIG_DRIVE_TOKEN), L3NetworkVO.class
    );
}