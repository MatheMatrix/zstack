package org.zstack.identity.imports.message;

import org.zstack.header.message.NeedReplyMessage;
import org.zstack.identity.imports.entity.AbstractImportSourceSpec;

/**
 * Created by Wenhao.Zhang on 2024/05/31
 */
public abstract class CreateThirdPartyAccountSourceMsg extends NeedReplyMessage {
    private AbstractImportSourceSpec spec;

    public String getType() {
        return spec == null ? null : spec.type;
    }

    public AbstractImportSourceSpec getSpec() {
        return spec;
    }

    public void setSpec(AbstractImportSourceSpec spec) {
        this.spec = spec;
    }
}
