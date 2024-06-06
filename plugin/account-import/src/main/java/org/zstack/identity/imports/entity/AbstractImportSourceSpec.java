package org.zstack.identity.imports.entity;

import org.zstack.core.Platform;

/**
 * Created by Wenhao.Zhang on 2024/05/31
 */
public abstract class AbstractImportSourceSpec {
    public String uuid = Platform.getUuid();
    public String type;
    public String description;
}
