package org.zstack.ldap.api;

import org.springframework.http.HttpMethod;
import org.zstack.header.message.APIEvent;
import org.zstack.header.message.APIMessage;
import org.zstack.header.message.APIParam;
import org.zstack.header.other.APIAuditor;
import org.zstack.header.rest.APINoSee;
import org.zstack.header.rest.RestRequest;
import org.zstack.ldap.entity.LdapFilterRuleVO;
import org.zstack.ldap.entity.LdapServerVO;

@RestRequest(
        path = "/ldap/filter/{uuid}/actions",
        method = HttpMethod.PUT,
        isAction = true,
        responseClass = APIUpdateLdapFilterRuleEvent.class
)
public class APIUpdateLdapFilterRuleMsg extends APIMessage implements APIAuditor {
    @APIParam(resourceType = LdapFilterRuleVO.class)
    private String uuid;

    @APIParam(required = false)
    private String rule;

    @APIParam(validValues = {"ACCEPT", "DENY"}, required = false)
    private String policy;

    @APIParam(validValues = {"AddNew", "DeleteInvalid"}, required = false)
    private String target;

    @APINoSee
    private String ldapServerUuid;

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getRule() {
        return rule;
    }

    public void setRule(String rule) {
        this.rule = rule;
    }

    public String getPolicy() {
        return policy;
    }

    public void setPolicy(String policy) {
        this.policy = policy;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getLdapServerUuid() {
        return ldapServerUuid;
    }

    public void setLdapServerUuid(String ldapServerUuid) {
        this.ldapServerUuid = ldapServerUuid;
    }

    public static APIUpdateLdapFilterRuleMsg __example__() {
        APIUpdateLdapFilterRuleMsg msg = new APIUpdateLdapFilterRuleMsg();
        msg.setUuid(uuid());
        msg.setRule("cn=Micha Kops");
        msg.setPolicy("ACCEPT");
        msg.setTarget("AddNew");

        return msg;
    }

    @Override
    public Result audit(APIMessage msg, APIEvent rsp) {
        // LdapFilterRuleVO is not a ResourceVO, so find and return ldapServerUuid
        return new Result(ldapServerUuid == null ? "" : ldapServerUuid, LdapServerVO.class);
    }
}
