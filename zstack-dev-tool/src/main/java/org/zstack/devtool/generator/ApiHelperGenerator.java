package org.zstack.devtool.generator;

import org.zstack.devtool.model.ApiMessageInfo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ApiHelperGenerator {

    public int generate(List<ApiMessageInfo> messages, Path apiHelperFile) {
        if (!Files.exists(apiHelperFile)) {
            System.out.println("[ApiHelper] WARN - ApiHelper.groovy not found at " + apiHelperFile);
            return 0;
        }

        String content;
        try {
            content = new String(Files.readAllBytes(apiHelperFile), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[ApiHelper] ERROR - Cannot read " + apiHelperFile + ": " + e.getMessage());
            return 0;
        }

        // Extract existing method names
        Set<String> existingMethods = new HashSet<>();
        Pattern pattern = Pattern.compile("def\\s+(\\w+)\\s*\\(");
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            existingMethods.add(matcher.group(1));
        }

        // Collect missing methods
        List<ApiMessageInfo> missing = new ArrayList<>();
        for (ApiMessageInfo msg : messages) {
            if (!existingMethods.contains(msg.getHelperMethodName())) {
                missing.add(msg);
            }
        }

        if (missing.isEmpty()) {
            return 0;
        }

        // Find insertion point: just before the closing brace of the class
        // ApiHelper.groovy ends with a closing "}" on its own line
        int insertionPoint = findInsertionPoint(content);
        if (insertionPoint < 0) {
            System.err.println("[ApiHelper] ERROR - Cannot find insertion point in " + apiHelperFile);
            return 0;
        }

        StringBuilder newMethods = new StringBuilder();
        for (ApiMessageInfo msg : missing) {
            newMethods.append(generateMethod(msg));
        }

        String newContent = content.substring(0, insertionPoint)
                + newMethods
                + content.substring(insertionPoint);

        try {
            Files.write(apiHelperFile, newContent.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
            System.out.println("[ApiHelper] Added " + missing.size() + " method(s)");
            for (ApiMessageInfo msg : missing) {
                System.out.println("  Added: " + msg.getHelperMethodName());
            }
        } catch (IOException e) {
            System.err.println("[ApiHelper] ERROR - Cannot write " + apiHelperFile + ": " + e.getMessage());
            return 0;
        }

        return missing.size();
    }

    private int findInsertionPoint(String content) {
        // Find the last closing brace which ends the class body
        int lastBrace = content.lastIndexOf('}');
        if (lastBrace < 0) return -1;
        // Walk back to find a newline before it
        int lineStart = content.lastIndexOf('\n', lastBrace);
        if (lineStart < 0) lineStart = 0;
        // Return the position of that newline (insert before last closing brace line)
        return lineStart + 1;
    }

    private String generateMethod(ApiMessageInfo msg) {
        String methodName = msg.getHelperMethodName();
        String actionFqn = "org.zstack.sdk." + msg.getActionName();

        StringBuilder sb = new StringBuilder();
        sb.append("\n\n");
        sb.append("    def ").append(methodName)
          .append("(@DelegatesTo(strategy = Closure.OWNER_FIRST, value = ")
          .append(actionFqn).append(".class) Closure c) {\n");
        sb.append("        def a = new ").append(actionFqn).append("()\n");
        sb.append("        a.sessionId = Test.currentEnvSpec?.session?.uuid\n");
        sb.append("        c.resolveStrategy = Closure.OWNER_FIRST\n");
        sb.append("        c.delegate = a\n");
        sb.append("        c()\n");
        sb.append("        \n");

        // Query actions need conditions coercion
        if (msg.isQuery()) {
            sb.append("        a.conditions = a.conditions.collect { it.toString() }\n");
        }

        sb.append("\n\n");
        sb.append("        if (System.getProperty(\"apipath\") != null) {\n");
        sb.append("            if (a.apiId == null) {\n");
        sb.append("                a.apiId = Platform.uuid\n");
        sb.append("            }\n");
        sb.append("    \n");
        sb.append("            def tracker = new ApiPathTracker(a.apiId)\n");
        sb.append("            def out = errorOut(a.call())\n");
        sb.append("            def path = tracker.getApiPath()\n");
        sb.append("            if (!path.isEmpty()) {\n");
        sb.append("                Test.apiPaths[a.class.name] = path.join(\" --->\\n\")\n");
        sb.append("            }\n");
        sb.append("        \n");
        sb.append("            return out\n");
        sb.append("        } else {\n");
        sb.append("            return errorOut(a.call())\n");
        sb.append("        }\n");
        sb.append("    }\n");

        return sb.toString();
    }
}
