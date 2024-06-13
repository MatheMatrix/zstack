package org.zstack.ldap.api;

import org.springframework.http.HttpMethod;
import org.zstack.header.identity.Action;
import org.zstack.header.message.APIEvent;
import org.zstack.header.message.APIMessage;
import org.zstack.header.message.APIParam;
import org.zstack.header.other.APIAuditor;
import org.zstack.header.other.APIMultiAuditor;
import org.zstack.header.rest.APINoSee;
import org.zstack.header.rest.RestRequest;
import org.zstack.header.vm.VmInstanceConstant;
import org.zstack.ldap.entity.LdapFilterRuleVO;
import org.zstack.ldap.entity.LdapServerVO;
import org.zstack.utils.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.zstack.utils.CollectionDSL.list;

@Action(category = VmInstanceConstant.ACTION_CATEGORY)
@RestRequest(
        path = "/ldap/filters",
        method = HttpMethod.DELETE,
        responseClass = APIRemoveLdapFilterRuleEvent.class
)
public class APIRemoveLdapFilterRuleMsg extends APIMessage implements APIMultiAuditor {
    @APIParam(resourceType = LdapFilterRuleVO.class, nonempty = true)
    private List<String> uuidList;

    @APINoSee
    private Map<String, String> ruleUuidLdapServerUuidMap;

    public List<String> getUuidList() {
        return uuidList;
    }

    public void setUuidList(List<String> uuidList) {
        this.uuidList = uuidList;
    }

    public Map<String, String> getRuleUuidLdapServerUuidMap() {
        return ruleUuidLdapServerUuidMap;
    }

    public void setRuleUuidLdapServerUuidMap(Map<String, String> ruleUuidLdapServerUuidMap) {
        this.ruleUuidLdapServerUuidMap = ruleUuidLdapServerUuidMap;
    }

    public static APIRemoveLdapFilterRuleMsg __example__() {
        APIRemoveLdapFilterRuleMsg msg = new APIRemoveLdapFilterRuleMsg();
        msg.setUuidList(list(uuid()));
        return msg;
    }

    @Override
    public List<APIAuditor.Result> multiAudit(APIMessage msg, APIEvent rsp) {
        if (!rsp.isSuccess()) {
            return Collections.emptyList();
        }

        return CollectionUtils.transform(((APIRemoveLdapFilterRuleEvent) rsp).getLdapServerUuidList(),
                uuid -> new APIAuditor.Result(uuid, LdapServerVO.class));
    }
}
