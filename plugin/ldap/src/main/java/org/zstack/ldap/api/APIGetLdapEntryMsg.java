package org.zstack.ldap.api;

import org.springframework.http.HttpMethod;
import org.zstack.header.message.APIParam;
import org.zstack.header.message.APISyncCallMessage;
import org.zstack.header.rest.RestRequest;
import org.zstack.ldap.entity.LdapServerVO;

/**
 * Created by lining on 2017/11/03.
 */
@RestRequest(
        path = "/ldap/entry",
        method = HttpMethod.GET,
        responseClass = APIGetLdapEntryReply.class
)
public class APIGetLdapEntryMsg extends APISyncCallMessage {

    @APIParam
    private String ldapFilter;

    @APIParam(required = false, numberRange = {1, Integer.MAX_VALUE})
    private Integer limit = 2500;

    @APIParam(resourceType = LdapServerVO.class, required = false)
    private String ldapServerUuid;

    public String getLdapFilter() {
        return ldapFilter;
    }

    public void setLdapFilter(String ldapFilter) {
        this.ldapFilter = ldapFilter;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }

    public static APIGetLdapEntryMsg __example__() {
        APIGetLdapEntryMsg msg = new APIGetLdapEntryMsg();
        msg.setLdapFilter("(cn=user_xxx)");
        return msg;
    }

    public String getLdapServerUuid() {
        return ldapServerUuid;
    }

    public void setLdapServerUuid(String ldapServerUuid) {
        this.ldapServerUuid = ldapServerUuid;
    }
}
