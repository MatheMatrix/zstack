package org.zstack.test.core.errorcode;

import org.junit.Test;
import org.zstack.header.errorcode.ErrorCategory;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.errorcode.ErrorCodeList;
import org.zstack.header.errorcode.SysErrors;
import org.zstack.utils.gson.JSONObjectUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class ErrorCodeEnvelopeTest {

    @Test
    public void testEnvelopeFieldsExistAndRoundTrip() {
        ErrorCode err = new ErrorCode("SYS.1000", "test");
        err.setCategory("INTERNAL");
        err.setMessageKey("ERR.001");
        Map<String, String> params = new HashMap<>();
        params.put("k1", "v1");
        err.setParams(params);
        err.setLocalizedMessage("localized");
        err.setRetryable(true);
        err.setHttpStatus(500);

        assertEquals("INTERNAL", err.getCategory());
        assertEquals("ERR.001", err.getMessageKey());
        assertEquals("localized", err.getLocalizedMessage());
        assertTrue(err.isRetryable());
        assertEquals(500, err.getHttpStatus());

        params.put("k2", "v2");
        Map<String, String> readParams = err.getParams();
        assertEquals(1, readParams.size());
        assertEquals("v1", readParams.get("k1"));
        assertFalse(readParams.containsKey("k2"));
        readParams.put("k3", "v3");
        assertFalse(err.getParams().containsKey("k3"));

        ErrorCode copy = new ErrorCode(err);
        assertEquals(err.getCategory(), copy.getCategory());
        assertEquals(err.getMessageKey(), copy.getMessageKey());
        assertEquals(err.getLocalizedMessage(), copy.getLocalizedMessage());
        assertEquals(err.isRetryable(), copy.isRetryable());
        assertEquals(err.getHttpStatus(), copy.getHttpStatus());
        assertEquals(err.getParams(), copy.getParams());
    }

    @Test
    public void testErrorCategoryMappings() {
        Map<String, ErrorCategory> sysMappings = new HashMap<>();
        sysMappings.put("SYS.1000", ErrorCategory.INTERNAL);
        sysMappings.put("SYS.1001", ErrorCategory.TIMEOUT);
        sysMappings.put("SYS.1002", ErrorCategory.OPERATION_ERROR);
        sysMappings.put("SYS.1003", ErrorCategory.RESOURCE_NOT_FOUND);
        sysMappings.put("SYS.1004", ErrorCategory.OPERATION_ERROR);
        sysMappings.put("SYS.1005", ErrorCategory.OPERATION_ERROR);
        sysMappings.put("SYS.1006", ErrorCategory.OPERATION_ERROR);
        sysMappings.put("SYS.1007", ErrorCategory.INVALID_ARGUMENT);
        sysMappings.put("SYS.1008", ErrorCategory.INTERNAL);
        sysMappings.put("SYS.1009", ErrorCategory.INTERNAL);
        sysMappings.put("SYS.1010", ErrorCategory.INTERNAL);
        sysMappings.put("SYS.1011", ErrorCategory.INTERNAL);
        sysMappings.put("SYS.1012", ErrorCategory.INTERNAL);
        sysMappings.put("SYS.1013", ErrorCategory.OPERATION_ERROR);
        sysMappings.put("SYS.1014", ErrorCategory.OPERATION_ERROR);
        sysMappings.put("SYS.1015", ErrorCategory.HTTP_ERROR);
        sysMappings.put("SYS.1016", ErrorCategory.IO_ERROR);
        sysMappings.put("SYS.1017", ErrorCategory.CANCEL_ERROR);

        for (Map.Entry<String, ErrorCategory> entry : sysMappings.entrySet()) {
            assertEquals(entry.getValue(), ErrorCategory.fromSysError(entry.getKey()));
        }
        assertEquals(ErrorCategory.OPERATION_ERROR, ErrorCategory.fromSysError("SYS.9999"));

        assertEquals(ErrorCategory.OPERATION_ERROR, ErrorCategory.fromCode(null));
        assertEquals(ErrorCategory.TIMEOUT, ErrorCategory.fromCode("SYS.1001"));
        assertEquals(ErrorCategory.TIMEOUT, ErrorCategory.fromCode("MY.TIMEOUT_ERROR"));
        assertEquals(ErrorCategory.AUTH_ERROR, ErrorCategory.fromCode("AUTH.UNAUTHORIZED"));
        assertEquals(ErrorCategory.RESOURCE_NOT_FOUND, ErrorCategory.fromCode("SOME.NOT_FOUND"));
        assertEquals(ErrorCategory.OPERATION_ERROR, ErrorCategory.fromCode("RANDOM.CODE"));
    }

    @Test
    public void testErrorCodeListRootCause() {
        ErrorCodeList empty = new ErrorCodeList();
        empty.setCode("SYS.1006");
        empty.setDescription("operation error");
        assertSame(empty, empty.getRootCause());

        ErrorCode singleCause = new ErrorCode("SYS.1001", "timeout");
        ErrorCodeList single = new ErrorCodeList();
        List<ErrorCode> singleList = new ArrayList<>();
        singleList.add(singleCause);
        single.setCauses(singleList);
        assertSame(singleCause, single.getRootCause());

        ErrorCode nestedCause = new ErrorCode("SYS.1003", "not found");
        ErrorCodeList nested = new ErrorCodeList();
        ErrorCodeList nestedInner = new ErrorCodeList();
        List<ErrorCode> innerList = new ArrayList<>();
        innerList.add(nestedCause);
        nestedInner.setCauses(innerList);
        List<ErrorCode> outerList = new ArrayList<>();
        outerList.add(nestedInner);
        nested.setCauses(outerList);
        assertSame(nestedCause, nested.getRootCause());

        ErrorCodeList level0 = new ErrorCodeList();
        ErrorCodeList level1 = new ErrorCodeList();
        ErrorCodeList level2 = new ErrorCodeList();
        ErrorCodeList level3 = new ErrorCodeList();
        ErrorCodeList level4 = new ErrorCodeList();
        ErrorCodeList level5 = new ErrorCodeList();
        ErrorCodeList level6 = new ErrorCodeList();
        List<ErrorCode> causes0 = new ArrayList<>();
        causes0.add(level1);
        level0.setCauses(causes0);
        List<ErrorCode> causes1 = new ArrayList<>();
        causes1.add(level2);
        level1.setCauses(causes1);
        List<ErrorCode> causes2 = new ArrayList<>();
        causes2.add(level3);
        level2.setCauses(causes2);
        List<ErrorCode> causes3 = new ArrayList<>();
        causes3.add(level4);
        level3.setCauses(causes3);
        List<ErrorCode> causes4 = new ArrayList<>();
        causes4.add(level5);
        level4.setCauses(causes4);
        List<ErrorCode> causes5 = new ArrayList<>();
        causes5.add(level6);
        level5.setCauses(causes5);
        List<ErrorCode> causes6 = new ArrayList<>();
        causes6.add(new ErrorCode("SYS.1000", "internal"));
        level6.setCauses(causes6);

        assertSame(level6, level0.getRootCause());
    }

    @Test
    public void testErrorCodeRootCauseChain() {
        ErrorCode root = new ErrorCode("SYS.1006", "root");
        assertSame(root, root.getRootCause());

        ErrorCode a = new ErrorCode("SYS.1006", "a");
        ErrorCode b = new ErrorCode("SYS.1001", "b");
        ErrorCode c = new ErrorCode("SYS.1003", "c");
        a.setCause(b);
        b.setCause(c);
        assertSame(c, a.getRootCause());
    }

    @Test
    public void testSerializationRoundTrip() {
        ErrorCode original = new ErrorCode("SYS.1006", "operation error");
        original.setCategory("OPERATION_ERROR");
        original.setMessageKey("ORG_ZSTACK_TEST_001");
        Map<String, String> params = new HashMap<>();
        params.put("resourceUuid", "abc-123");
        original.setParams(params);
        original.setLocalizedMessage("操作失败");
        original.setRetryable(false);
        original.setHttpStatus(400);

        String json = JSONObjectUtil.toJsonString(original);
        ErrorCode deserialized = JSONObjectUtil.toObject(json, ErrorCode.class);

        assertEquals(original.getCode(), deserialized.getCode());
        assertEquals(original.getDescription(), deserialized.getDescription());
        assertEquals(original.getCategory(), deserialized.getCategory());
        assertEquals(original.getMessageKey(), deserialized.getMessageKey());
        assertEquals(original.getParams(), deserialized.getParams());
        assertEquals(original.getLocalizedMessage(), deserialized.getLocalizedMessage());
        assertEquals(original.isRetryable(), deserialized.isRetryable());
        assertEquals(original.getHttpStatus(), deserialized.getHttpStatus());
    }

    @Test
    public void testErrorCodeCopyPreservesEnvelopeFields() {
        ErrorCode original = new ErrorCode("SYS.1007", "invalid argument");
        original.setCategory("INVALID_ARGUMENT");
        original.setMessageKey("ORG_ZSTACK_TEST_002");
        Map<String, String> params = new HashMap<>();
        params.put("field", "name");
        original.setParams(params);
        original.setLocalizedMessage("参数错误");
        original.setRetryable(false);
        original.setHttpStatus(400);

        ErrorCode copy = original.copy();

        assertEquals(original.getCategory(), copy.getCategory());
        assertEquals(original.getMessageKey(), copy.getMessageKey());
        assertEquals(original.getParams(), copy.getParams());
        assertEquals(original.getLocalizedMessage(), copy.getLocalizedMessage());
        assertEquals(original.isRetryable(), copy.isRetryable());
        assertEquals(original.getHttpStatus(), copy.getHttpStatus());
    }

    @Test
    public void testErrorCodeEqualityIgnoresEnvelopeFields() {
        ErrorCode cause = new ErrorCode("SYS.1001", "timeout");
        ErrorCode first = new ErrorCode("SYS.1006", "operation error", "details");
        first.setCause(cause);
        first.putToOpaque("key", "value");
        first.setCategory("OPERATION_ERROR");
        first.setMessageKey("MSG.001");
        first.setRetryable(true);
        first.setHttpStatus(500);

        ErrorCode second = new ErrorCode("SYS.1006", "operation error", "details");
        second.setCause(cause);
        second.putToOpaque("key", "value");
        second.setCategory("INTERNAL");
        second.setMessageKey("MSG.002");
        second.setRetryable(false);
        second.setHttpStatus(400);

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    public void testSysErrorsToStringValues() {
        assertEquals("SYS.1000", SysErrors.INTERNAL.toString());
        assertEquals("SYS.1001", SysErrors.TIMEOUT.toString());
        assertEquals("SYS.1002", SysErrors.CREATE_RESOURCE_ERROR.toString());
        assertEquals("SYS.1003", SysErrors.RESOURCE_NOT_FOUND.toString());
        assertEquals("SYS.1004", SysErrors.DELETE_RESOURCE_ERROR.toString());
        assertEquals("SYS.1005", SysErrors.CHANGE_RESOURCE_STATE_ERROR.toString());
        assertEquals("SYS.1006", SysErrors.OPERATION_ERROR.toString());
        assertEquals("SYS.1007", SysErrors.INVALID_ARGUMENT_ERROR.toString());
        assertEquals("SYS.1008", SysErrors.UNKNOWN_MESSAGE_ERROR.toString());
        assertEquals("SYS.1009", SysErrors.NO_ROUTE_ERROR.toString());
        assertEquals("SYS.1010", SysErrors.NOT_READY_ERROR.toString());
        assertEquals("SYS.1011", SysErrors.UNDELIVERABLE_ERROR.toString());
        assertEquals("SYS.1012", SysErrors.MANAGEMENT_NODE_UNAVAILABLE_ERROR.toString());
        assertEquals("SYS.1013", SysErrors.NO_CAPABILITY_ERROR.toString());
        assertEquals("SYS.1014", SysErrors.UNIMPLEMENTED_OPERATION_ERROR.toString());
        assertEquals("SYS.1015", SysErrors.HTTP_ERROR.toString());
        assertEquals("SYS.1016", SysErrors.IO_ERROR.toString());
        assertEquals("SYS.1017", SysErrors.CANCEL_ERROR.toString());
    }
}
