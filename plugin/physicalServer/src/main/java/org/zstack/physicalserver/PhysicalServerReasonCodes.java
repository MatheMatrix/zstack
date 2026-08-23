package org.zstack.physicalserver;

import org.zstack.header.errorcode.ErrorCode;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PhysicalServerReasonCodes {
    private static final Pattern REASON_CODE = Pattern.compile(
            "(?:^|[^A-Z0-9_])([A-Z][A-Z0-9_]+):");

    private PhysicalServerReasonCodes() {
    }

    public static String from(ErrorCode errorCode) {
        String details = errorCode == null ? null : errorCode.getDetails();
        return from(details);
    }

    public static String from(String details) {
        if (details == null) {
            return "RESOURCE_ASSIGNMENT_INVALID";
        }
        Matcher matcher = REASON_CODE.matcher(details);
        String reasonCode = null;
        while (matcher.find()) {
            reasonCode = matcher.group(1);
        }
        return reasonCode == null ? "RESOURCE_ASSIGNMENT_INVALID" : reasonCode;
    }
}
