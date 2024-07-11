package org.zstack.storage.zbs;

import org.zstack.header.core.NonCloneable;
import org.zstack.header.tag.TagDefinition;
import org.zstack.header.volume.VolumeVO;
import org.zstack.tag.PatternedSystemTag;

/**
 * @author Xingwei Yu
 * @date 2024/7/16 14:31
 */
@TagDefinition
public class ZbsSystemTags {
    public static final String USE_ZBS_PRIMARY_STORAGE_POOL_TOKEN = "poolName";
    @NonCloneable
    public static PatternedSystemTag USE_ZBS_PRIMARY_STORAGE_POOL = new PatternedSystemTag(String.format("zbs::pool::{%s}", USE_ZBS_PRIMARY_STORAGE_POOL_TOKEN), VolumeVO.class);
}
