package org.zstack.identity.imports.message;

import org.zstack.header.message.NeedReplyMessage;
import org.zstack.identity.imports.entity.AbstractAccountSourceSpec;

/**
 * Created by Wenhao.Zhang on 2024/05/31
 */
public abstract class CreateThirdPartyAccountSourceMsg extends NeedReplyMessage {
    private AbstractAccountSourceSpec spec;

    public String getType() {
        return spec == null ? null : spec.getType();
    }

    public AbstractAccountSourceSpec getSpec() {
        return spec;
    }

    public void setSpec(AbstractAccountSourceSpec spec) {
        this.spec = spec;
    }
}
