package org.zstack.header.candidate;

import java.util.LinkedHashMap;
import java.util.Map;

public class CandidateRejectReason {
    private String code;
    private String category;
    private String message;
    private Map<String, Object> details;

    public static CandidateRejectReason of(String code, String category, String message) {
        CandidateRejectReason reason = new CandidateRejectReason();
        reason.setCode(code);
        reason.setCategory(category);
        reason.setMessage(message);
        return reason;
    }

    public CandidateRejectReason detail(String key, Object value) {
        CandidateReasonDetails.checkAllowed(key, value);
        if (details == null) {
            details = new LinkedHashMap<>();
        }
        details.put(key, value);
        return this;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public void setDetails(Map<String, Object> details) {
        this.details = details;
    }
}
