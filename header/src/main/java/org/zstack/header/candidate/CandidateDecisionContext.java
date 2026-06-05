package org.zstack.header.candidate;

import org.zstack.header.message.APIMessage;

import java.util.LinkedHashMap;
import java.util.Map;

public class CandidateDecisionContext {
    private String requestUuid;
    private String apiName;
    private String accountUuid;
    private String userUuid;
    private String resourceType;
    private Map<String, Object> requestScope = new LinkedHashMap<>();
    private Map<String, Object> metadata = new LinkedHashMap<>();

    public static CandidateDecisionContext fromApiMessage(APIMessage msg, String resourceType) {
        CandidateDecisionContext ctx = new CandidateDecisionContext();
        ctx.setRequestUuid(msg.getId());
        ctx.setApiName(msg.getClass().getSimpleName());
        ctx.setResourceType(resourceType);
        if (msg.getSession() != null) {
            ctx.setAccountUuid(msg.getSession().getAccountUuid());
            ctx.setUserUuid(msg.getSession().getUserUuid());
        }
        return ctx;
    }

    public String getRequestUuid() {
        return requestUuid;
    }

    public void setRequestUuid(String requestUuid) {
        this.requestUuid = requestUuid;
    }

    public String getApiName() {
        return apiName;
    }

    public void setApiName(String apiName) {
        this.apiName = apiName;
    }

    public String getAccountUuid() {
        return accountUuid;
    }

    public void setAccountUuid(String accountUuid) {
        this.accountUuid = accountUuid;
    }

    public String getUserUuid() {
        return userUuid;
    }

    public void setUserUuid(String userUuid) {
        this.userUuid = userUuid;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public Map<String, Object> getRequestScope() {
        return requestScope;
    }

    public void setRequestScope(Map<String, Object> requestScope) {
        this.requestScope = requestScope;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
