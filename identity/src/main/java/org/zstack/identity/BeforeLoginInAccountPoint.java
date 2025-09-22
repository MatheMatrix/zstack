package org.zstack.identity;

import org.zstack.header.identity.APISessionMessage;

public interface BeforeLoginInAccountPoint {
    void beforeLogin(APISessionMessage sessionMessage);
}
