package org.zstack.ldap.message;

import org.zstack.header.message.NeedReplyMessage;

/**
 * Created by Wenhao.Zhang on 2024/06/04
 */
public class SyncAccountsFromLdapServerMsg extends NeedReplyMessage {
    private String uuid;

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }
}
