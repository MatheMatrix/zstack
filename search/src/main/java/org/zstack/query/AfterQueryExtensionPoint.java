package org.zstack.query;

import org.zstack.zql.ZQLQueryReturn;

import java.util.List;

public interface AfterQueryExtensionPoint {
    void fileter(List<ZQLQueryReturn> rs, String zqlText);
}
