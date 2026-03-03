package org.zstack.header.errorcode;

import java.util.HashMap;
import java.util.Map;

/**
 * Categorizes error codes into broad categories.
 * Maps SysErrors string codes and provides prefix-based inference for unknown codes.
 */
public enum ErrorCategory {
    INTERNAL,
    TIMEOUT,
    OPERATION_ERROR,
    INVALID_ARGUMENT,
    RESOURCE_NOT_FOUND,
    AUTH_ERROR,
    IO_ERROR,
    HTTP_ERROR,
    CANCEL_ERROR;

    private static final Map<String, ErrorCategory> SYS_ERROR_MAP = new HashMap<>();

    static {
        // Map all 18 SysErrors values to ErrorCategory
        SYS_ERROR_MAP.put("SYS.1000", INTERNAL);              // INTERNAL
        SYS_ERROR_MAP.put("SYS.1001", TIMEOUT);               // TIMEOUT
        SYS_ERROR_MAP.put("SYS.1002", OPERATION_ERROR);       // CREATE_RESOURCE_ERROR
        SYS_ERROR_MAP.put("SYS.1003", RESOURCE_NOT_FOUND);    // RESOURCE_NOT_FOUND
        SYS_ERROR_MAP.put("SYS.1004", OPERATION_ERROR);       // DELETE_RESOURCE_ERROR
        SYS_ERROR_MAP.put("SYS.1005", OPERATION_ERROR);       // CHANGE_RESOURCE_STATE_ERROR
        SYS_ERROR_MAP.put("SYS.1006", OPERATION_ERROR);       // OPERATION_ERROR
        SYS_ERROR_MAP.put("SYS.1007", INVALID_ARGUMENT);      // INVALID_ARGUMENT_ERROR
        SYS_ERROR_MAP.put("SYS.1008", INTERNAL);              // UNKNOWN_MESSAGE_ERROR
        SYS_ERROR_MAP.put("SYS.1009", INTERNAL);              // NO_ROUTE_ERROR
        SYS_ERROR_MAP.put("SYS.1010", INTERNAL);              // NOT_READY_ERROR
        SYS_ERROR_MAP.put("SYS.1011", INTERNAL);              // UNDELIVERABLE_ERROR
        SYS_ERROR_MAP.put("SYS.1012", INTERNAL);              // MANAGEMENT_NODE_UNAVAILABLE_ERROR
        SYS_ERROR_MAP.put("SYS.1013", OPERATION_ERROR);       // NO_CAPABILITY_ERROR
        SYS_ERROR_MAP.put("SYS.1014", OPERATION_ERROR);       // UNIMPLEMENTED_OPERATION_ERROR
        SYS_ERROR_MAP.put("SYS.1015", HTTP_ERROR);            // HTTP_ERROR
        SYS_ERROR_MAP.put("SYS.1016", IO_ERROR);              // IO_ERROR
        SYS_ERROR_MAP.put("SYS.1017", CANCEL_ERROR);          // CANCEL_ERROR
    }

    /**
     * Maps a SysErrors string code to its ErrorCategory.
     * @param code The SysErrors string code (e.g., "SYS.1000")
     * @return The corresponding ErrorCategory, or OPERATION_ERROR if not found
     */
    public static ErrorCategory fromSysError(String code) {
        return SYS_ERROR_MAP.getOrDefault(code, OPERATION_ERROR);
    }

    /**
     * Infers ErrorCategory from a code string using prefix-based heuristics.
     * Handles non-SysErrors codes by checking prefixes.
     * @param code The error code string
     * @return The inferred ErrorCategory, or OPERATION_ERROR as default fallback
     */
    public static ErrorCategory fromCode(String code) {
        if (code == null) {
            return OPERATION_ERROR;
        }

        // Check if it's a SysError first
        if (code.startsWith("SYS.")) {
            return fromSysError(code);
        }

        // Prefix-based inference for other codes
        String upperCode = code.toUpperCase();

        if (upperCode.contains("TIMEOUT")) {
            return TIMEOUT;
        }
        if (upperCode.contains("NOT_FOUND") || upperCode.contains("NOTFOUND")) {
            return RESOURCE_NOT_FOUND;
        }
        if (upperCode.contains("AUTH") || upperCode.contains("UNAUTHORIZED") ||
            upperCode.contains("FORBIDDEN")) {
            return AUTH_ERROR;
        }
        if (upperCode.contains("IO") || upperCode.contains("IO_ERROR")) {
            return IO_ERROR;
        }
        if (upperCode.contains("HTTP")) {
            return HTTP_ERROR;
        }
        if (upperCode.contains("INVALID") || upperCode.contains("ARGUMENT")) {
            return INVALID_ARGUMENT;
        }
        if (upperCode.contains("CANCEL")) {
            return CANCEL_ERROR;
        }
        if (upperCode.contains("INTERNAL")) {
            return INTERNAL;
        }

        // Default fallback
        return OPERATION_ERROR;
    }
}
