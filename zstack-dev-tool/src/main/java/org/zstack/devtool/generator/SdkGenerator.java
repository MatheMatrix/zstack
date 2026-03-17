package org.zstack.devtool.generator;

import org.zstack.devtool.model.ApiMessageInfo;
import org.zstack.devtool.model.ApiParamInfo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class SdkGenerator {

    public int generate(List<ApiMessageInfo> messages, Path sdkDir, boolean createOnly) {
        int created = 0;

        try {
            Files.createDirectories(sdkDir);
        } catch (IOException e) {
            System.err.println("[SDK] ERROR - cannot create SDK dir: " + sdkDir + ": " + e.getMessage());
            return 0;
        }

        for (ApiMessageInfo msg : messages) {
            created += generateAction(msg, sdkDir, createOnly);
            created += generateResult(msg, sdkDir, createOnly);
        }

        return created;
    }

    private int generateAction(ApiMessageInfo msg, Path sdkDir, boolean createOnly) {
        String actionName = msg.getActionName();
        Path actionFile = sdkDir.resolve(actionName + ".java");

        if (createOnly && Files.exists(actionFile)) {
            return 0;
        }

        try {
            String content = msg.isQuery()
                    ? generateQueryActionContent(msg)
                    : generateActionContent(msg);
            Files.write(actionFile, content.getBytes(StandardCharsets.UTF_8));
            System.out.println("  Created: " + actionFile.getFileName());
            return 1;
        } catch (IOException e) {
            System.err.println("  ERROR: Failed to write " + actionFile + ": " + e.getMessage());
            return 0;
        }
    }

    private int generateResult(ApiMessageInfo msg, Path sdkDir, boolean createOnly) {
        String resultName = msg.getResultName();
        Path resultFile = sdkDir.resolve(resultName + ".java");

        if (createOnly && Files.exists(resultFile)) {
            return 0;
        }

        try {
            String content = msg.isQuery()
                    ? generateQueryResultContent(msg)
                    : generateResultContent(msg);
            Files.write(resultFile, content.getBytes(StandardCharsets.UTF_8));
            System.out.println("  Created: " + resultFile.getFileName());
            return 1;
        } catch (IOException e) {
            System.err.println("  ERROR: Failed to write " + resultFile + ": " + e.getMessage());
            return 0;
        }
    }

    private String generateActionContent(ApiMessageInfo msg) {
        String actionName = msg.getActionName();
        String resultName = msg.getResultName();
        String fqResult = "org.zstack.sdk." + resultName;

        boolean needSession = !msg.isSuppressCredentialCheck();
        boolean needPoll = isWriteMethod(msg.getHttpMethod());
        String parameterName = msg.getParameterName() != null ? msg.getParameterName() : "";

        StringBuilder sb = new StringBuilder();
        sb.append("package org.zstack.sdk;\n\n");
        sb.append("import java.util.HashMap;\n");
        sb.append("import java.util.Map;\n");
        sb.append("import org.zstack.sdk.*;\n\n");
        sb.append("public class ").append(actionName).append(" extends AbstractAction {\n\n");
        sb.append("    private static final HashMap<String, Parameter> parameterMap = new HashMap<>();\n\n");
        sb.append("    private static final HashMap<String, Parameter> nonAPIParameterMap = new HashMap<>();\n\n");

        // Result inner class
        sb.append("    public static class Result {\n");
        sb.append("        public ErrorCode error;\n");
        sb.append("        public ").append(fqResult).append(" value;\n\n");
        sb.append("        public Result throwExceptionIfError() {\n");
        sb.append("            if (error != null) {\n");
        sb.append("                throw new ApiException(\n");
        sb.append("                    String.format(\"error[code: %s, description: %s, details: %s, globalErrorCode: %s]\", error.code, error.description, error.details, error.globalErrorCode)    \n");
        sb.append("                );\n");
        sb.append("            }\n");
        sb.append("            \n");
        sb.append("            return this;\n");
        sb.append("        }\n");
        sb.append("    }\n\n");

        // Own (non-inherited, non-noSee) params
        for (ApiParamInfo param : msg.getParams()) {
            if (param.isNoSee() || param.isInherited()) continue;
            sb.append(buildParamAnnotation(param));
            sb.append("    public ").append(param.getFieldType()).append(" ").append(param.getFieldName()).append(";\n\n");
        }

        // Framework fields
        sb.append("    @Param(required = false)\n");
        sb.append("    public java.util.List systemTags;\n\n");
        sb.append("    @Param(required = false)\n");
        sb.append("    public java.util.List userTags;\n\n");
        sb.append("    @Param(required = false)\n");
        sb.append("    public String sessionId;\n\n");
        sb.append("    @Param(required = false)\n");
        sb.append("    public String accessKeyId;\n\n");
        sb.append("    @Param(required = false)\n");
        sb.append("    public String accessKeySecret;\n\n");
        sb.append("    @Param(required = false)\n");
        sb.append("    public String requestIp;\n\n\n");

        // makeResult
        sb.append("    private Result makeResult(ApiResult res) {\n");
        sb.append("        Result ret = new Result();\n");
        sb.append("        if (res.error != null) {\n");
        sb.append("            ret.error = res.error;\n");
        sb.append("            return ret;\n");
        sb.append("        }\n");
        sb.append("        \n");
        sb.append("        ").append(fqResult).append(" value = res.getResult(").append(fqResult).append(".class);\n");
        sb.append("        ret.value = value == null ? new ").append(fqResult).append("() : value; \n\n");
        sb.append("        return ret;\n");
        sb.append("    }\n\n");

        // call()
        sb.append("    public Result call() {\n");
        sb.append("        ApiResult res = ZSClient.call(this);\n");
        sb.append("        return makeResult(res);\n");
        sb.append("    }\n\n");

        // call(Completion)
        sb.append("    public void call(final Completion<Result> completion) {\n");
        sb.append("        ZSClient.call(this, new InternalCompletion() {\n");
        sb.append("            @Override\n");
        sb.append("            public void complete(ApiResult res) {\n");
        sb.append("                completion.complete(makeResult(res));\n");
        sb.append("            }\n");
        sb.append("        });\n");
        sb.append("    }\n\n");

        // getParameterMap
        sb.append("    protected Map<String, Parameter> getParameterMap() {\n");
        sb.append("        return parameterMap;\n");
        sb.append("    }\n\n");

        // getNonAPIParameterMap
        sb.append("    protected Map<String, Parameter> getNonAPIParameterMap() {\n");
        sb.append("        return nonAPIParameterMap;\n");
        sb.append("    }\n\n");

        // getRestInfo
        sb.append("    protected RestInfo getRestInfo() {\n");
        sb.append("        RestInfo info = new RestInfo();\n");
        sb.append("        info.httpMethod = \"").append(msg.getHttpMethod()).append("\";\n");
        sb.append("        info.path = \"").append(msg.getPath()).append("\";\n");
        sb.append("        info.needSession = ").append(needSession).append(";\n");
        sb.append("        info.needPoll = ").append(needPoll).append(";\n");
        sb.append("        info.parameterName = \"").append(parameterName).append("\";\n");
        sb.append("        return info;\n");
        sb.append("    }\n\n");

        sb.append("}\n");
        return sb.toString();
    }

    private String generateQueryActionContent(ApiMessageInfo msg) {
        String actionName = msg.getActionName();
        String resultName = msg.getResultName();
        String fqResult = "org.zstack.sdk." + resultName;

        boolean needSession = !msg.isSuppressCredentialCheck();
        String parameterName = msg.getParameterName() != null ? msg.getParameterName() : "";

        StringBuilder sb = new StringBuilder();
        sb.append("package org.zstack.sdk;\n\n");
        sb.append("import java.util.HashMap;\n");
        sb.append("import java.util.Map;\n");
        sb.append("import org.zstack.sdk.*;\n\n");
        sb.append("public class ").append(actionName).append(" extends QueryAction {\n\n");
        sb.append("    private static final HashMap<String, Parameter> parameterMap = new HashMap<>();\n\n");
        sb.append("    private static final HashMap<String, Parameter> nonAPIParameterMap = new HashMap<>();\n\n");

        // Result inner class
        sb.append("    public static class Result {\n");
        sb.append("        public ErrorCode error;\n");
        sb.append("        public ").append(fqResult).append(" value;\n\n");
        sb.append("        public Result throwExceptionIfError() {\n");
        sb.append("            if (error != null) {\n");
        sb.append("                throw new ApiException(\n");
        sb.append("                    String.format(\"error[code: %s, description: %s, details: %s, globalErrorCode: %s]\", error.code, error.description, error.details, error.globalErrorCode)    \n");
        sb.append("                );\n");
        sb.append("            }\n");
        sb.append("            \n");
        sb.append("            return this;\n");
        sb.append("        }\n");
        sb.append("    }\n\n\n");

        // makeResult
        sb.append("    private Result makeResult(ApiResult res) {\n");
        sb.append("        Result ret = new Result();\n");
        sb.append("        if (res.error != null) {\n");
        sb.append("            ret.error = res.error;\n");
        sb.append("            return ret;\n");
        sb.append("        }\n");
        sb.append("        \n");
        sb.append("        ").append(fqResult).append(" value = res.getResult(").append(fqResult).append(".class);\n");
        sb.append("        ret.value = value == null ? new ").append(fqResult).append("() : value; \n\n");
        sb.append("        return ret;\n");
        sb.append("    }\n\n");

        // call()
        sb.append("    public Result call() {\n");
        sb.append("        ApiResult res = ZSClient.call(this);\n");
        sb.append("        return makeResult(res);\n");
        sb.append("    }\n\n");

        // call(Completion)
        sb.append("    public void call(final Completion<Result> completion) {\n");
        sb.append("        ZSClient.call(this, new InternalCompletion() {\n");
        sb.append("            @Override\n");
        sb.append("            public void complete(ApiResult res) {\n");
        sb.append("                completion.complete(makeResult(res));\n");
        sb.append("            }\n");
        sb.append("        });\n");
        sb.append("    }\n\n");

        // getParameterMap
        sb.append("    protected Map<String, Parameter> getParameterMap() {\n");
        sb.append("        return parameterMap;\n");
        sb.append("    }\n\n");

        // getNonAPIParameterMap
        sb.append("    protected Map<String, Parameter> getNonAPIParameterMap() {\n");
        sb.append("        return nonAPIParameterMap;\n");
        sb.append("    }\n\n");

        // getRestInfo
        sb.append("    protected RestInfo getRestInfo() {\n");
        sb.append("        RestInfo info = new RestInfo();\n");
        sb.append("        info.httpMethod = \"").append(msg.getHttpMethod()).append("\";\n");
        sb.append("        info.path = \"").append(msg.getPath()).append("\";\n");
        sb.append("        info.needSession = ").append(needSession).append(";\n");
        sb.append("        info.needPoll = false;\n");
        sb.append("        info.parameterName = \"").append(parameterName).append("\";\n");
        sb.append("        return info;\n");
        sb.append("    }\n\n");

        sb.append("}\n");
        return sb.toString();
    }

    private String generateResultContent(ApiMessageInfo msg) {
        // Non-query result: single 'value' field of the response type is already
        // handled by the Action wrapping — the Result class itself holds inventory
        // fields from the actual API response class. Since we don't have field info
        // for the response class here, we emit an empty result (matches pattern where
        // response fields come from the compiled response class at runtime).
        String resultName = msg.getResultName();

        StringBuilder sb = new StringBuilder();
        sb.append("package org.zstack.sdk;\n\n");
        sb.append("public class ").append(resultName).append(" {\n");
        sb.append("}\n");
        return sb.toString();
    }

    private String generateQueryResultContent(ApiMessageInfo msg) {
        String resultName = msg.getResultName();

        StringBuilder sb = new StringBuilder();
        sb.append("package org.zstack.sdk;\n\n");
        sb.append("public class ").append(resultName).append(" {\n");
        sb.append("    public java.util.List inventories;\n");
        sb.append("    public void setInventories(java.util.List inventories) {\n");
        sb.append("        this.inventories = inventories;\n");
        sb.append("    }\n");
        sb.append("    public java.util.List getInventories() {\n");
        sb.append("        return this.inventories;\n");
        sb.append("    }\n\n");
        sb.append("    public java.lang.Long total;\n");
        sb.append("    public void setTotal(java.lang.Long total) {\n");
        sb.append("        this.total = total;\n");
        sb.append("    }\n");
        sb.append("    public java.lang.Long getTotal() {\n");
        sb.append("        return this.total;\n");
        sb.append("    }\n\n");
        sb.append("}\n");
        return sb.toString();
    }

    private String buildParamAnnotation(ApiParamInfo param) {
        StringBuilder sb = new StringBuilder();
        sb.append("    @Param(required = ").append(param.isRequired());
        sb.append(", nonempty = ").append(param.isNonempty());
        sb.append(", nullElements = ").append(param.isNullElements());
        sb.append(", emptyString = ").append(param.isEmptyString());
        sb.append(", noTrim = ").append(param.isNoTrim());

        if (param.getMaxLength() > 0) {
            sb.append(", maxLength = ").append(param.getMaxLength());
        }
        if (param.getNumberRange() != null && param.getNumberRange().length == 2) {
            sb.append(", numberRange = {").append(param.getNumberRange()[0])
              .append("L, ").append(param.getNumberRange()[1]).append("L}");
        }
        if (param.getValidValues() != null && param.getValidValues().length > 0) {
            sb.append(", validValues = {");
            for (int i = 0; i < param.getValidValues().length; i++) {
                if (i > 0) sb.append(", ");
                sb.append("\"").append(param.getValidValues()[i]).append("\"");
            }
            sb.append("}");
        }
        if (param.getValidRegexValues() != null && !param.getValidRegexValues().isEmpty()) {
            sb.append(", validRegexValues = \"").append(param.getValidRegexValues()).append("\"");
        }

        sb.append(")\n");
        return sb.toString();
    }

    private boolean isWriteMethod(String httpMethod) {
        if (httpMethod == null) return false;
        String m = httpMethod.toUpperCase();
        return "POST".equals(m) || "PUT".equals(m) || "DELETE".equals(m);
    }
}
