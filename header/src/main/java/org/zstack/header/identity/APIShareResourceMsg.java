package org.zstack.header.identity;

import org.springframework.http.HttpMethod;
import org.zstack.header.message.APIMessage;
import org.zstack.header.message.APIParam;
import org.zstack.header.rest.RestRequest;
import org.zstack.header.vo.ResourceVO;

import java.util.List;

import static org.zstack.utils.CollectionDSL.list;

/**
 * Created by frank on 7/13/2015.
 */
@Action(category = AccountConstant.ACTION_CATEGORY)
@RestRequest(
        path = "/accounts/resources/actions",
        method = HttpMethod.PUT,
        responseClass = APIShareResourceEvent.class,
        isAction = true
)
public class APIShareResourceMsg extends APIMessage implements AccountMessage {
    @APIParam(resourceType = ResourceVO.class, nonempty = true, scope = APIParam.SCOPE_MUST_OWNER)
    private List<String> resourceUuids;
    @APIParam(resourceType = AccountVO.class, required = false)
    private List<String> accountUuids;
    @APIParam(required = false)
    private boolean toPublic;
    @APIParam(required = false, validEnums = {ShareResourcePermission.class})
    @Deprecated
    private String permission;

    public List<String> getResourceUuids() {
        return resourceUuids;
    }

    public void setResourceUuids(List<String> resourceUuids) {
        this.resourceUuids = resourceUuids;
    }

    public List<String> getAccountUuids() {
        return accountUuids;
    }

    public void setAccountUuids(List<String> accountUuids) {
        this.accountUuids = accountUuids;
    }

    public boolean isToPublic() {
        return toPublic;
    }

    public void setToPublic(boolean toPublic) {
        this.toPublic = toPublic;
    }

    @Deprecated
    public String getPermission() {
        return permission;
    }

    @Deprecated
    public void setPermission(String permission) {
        this.permission = permission;
    }

    @Override
    public String getAccountUuid() {
        return getSession().getAccountUuid();
    }
 
    public static APIShareResourceMsg __example__() {
        APIShareResourceMsg msg = new APIShareResourceMsg();
        msg.setAccountUuids(list(uuid(), uuid()));
        msg.setToPublic(false);
        msg.setResourceUuids(list(uuid(), uuid()));
        return msg;
    }

}
