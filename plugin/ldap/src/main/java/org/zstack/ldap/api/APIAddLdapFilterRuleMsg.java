package org.zstack.ldap.api;

import org.springframework.http.HttpMethod;
import org.zstack.header.message.APIEvent;
import org.zstack.header.message.APIMessage;
import org.zstack.header.message.APIParam;
import org.zstack.header.other.APIAuditor;
import org.zstack.header.rest.RestRequest;
import org.zstack.ldap.entity.LdapServerVO;

import java.util.Arrays;
import java.util.List;

@RestRequest(
        path = "/ldap/filter",
        method = HttpMethod.POST,
        responseClass = APIAddLdapFilterRuleEvent.class,
        parameterName = "params"
)
public class APIAddLdapFilterRuleMsg extends APIMessage implements APIAuditor {
    @APIParam(resourceType = LdapServerVO.class)
    private String ldapServerUuid;

    @APIParam(nonempty = true)
    private List<String> rules;

    @APIParam(validValues = {"ACCEPT", "DENY"})
    private String policy = "ACCEPT";

    @APIParam(validValues = {"AddNew", "DeleteInvalid"})
    private String target = "AddNew";

    public String getLdapServerUuid() {
        return ldapServerUuid;
    }

    public void setLdapServerUuid(String uuid) {
        this.ldapServerUuid = uuid;
    }

    public List<String> getRules() {
        return rules;
    }

    public void setRules(List<String> rules) {
        this.rules = rules;
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

    public static APIAddLdapFilterRuleMsg __example__() {
        APIAddLdapFilterRuleMsg msg = new APIAddLdapFilterRuleMsg();
        msg.setLdapServerUuid(uuid());
        msg.setRules(Arrays.asList("cn=Micha Kops"));
        msg.setPolicy("ACCEPT");
        msg.setTarget("AddNew");

        return msg;
    }

    @Override
    public Result audit(APIMessage msg, APIEvent rsp) {
        return new Result(rsp.isSuccess() ? ((APIAddLdapFilterRuleMsg)msg).getLdapServerUuid() : "", LdapServerVO.class);
    }
}