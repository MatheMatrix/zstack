package org.zstack.header.identity;

import org.springframework.http.HttpMethod;
import org.zstack.header.log.NoLogging;
import org.zstack.header.message.APIMessage;
import org.zstack.header.message.APIParam;
import org.zstack.header.rest.RestRequest;

import java.io.Serializable;

@Action(category = AccountConstant.ACTION_CATEGORY, accountOnly = true)
@RestRequest(
        path = "/accounts/{uuid}",
        method = HttpMethod.PUT,
        isAction = true,
        responseClass = APIUpdateAccountEvent.class
)
public class APIUpdateAccountMsg extends APIMessage implements AccountMessage, Serializable {
    @APIParam(resourceType = AccountVO.class, checkAccount = true, operationTarget = true)
    private String uuid;
    @APIParam(maxLength = 255, required = false, password = true)
    @NoLogging
    private String password;
    @APIParam(maxLength = 255, required = false)
    private String name;
    @APIParam(maxLength = 2048, required = false)
    private String description;
    @APIParam(maxLength = 255, required = false)
    @NoLogging
    private String oldPassword;

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public String getAccountUuid() {
        return this.getSession().getAccountUuid();
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getOldPassword() {
        return oldPassword;
    }

    public void setOldPassword(String oldPassword) {
        this.oldPassword = oldPassword;
    }

    public static APIUpdateAccountMsg __example__() {
        APIUpdateAccountMsg msg = new APIUpdateAccountMsg();
        msg.setUuid(uuid());
        msg.setName("updatename");
        msg.setPassword("updatepassword");
        return msg;
    }
}
