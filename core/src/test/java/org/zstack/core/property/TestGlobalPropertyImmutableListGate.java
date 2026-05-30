package org.zstack.core.property;

import org.junit.Test;
import org.zstack.core.GlobalProperty;
import org.zstack.core.GlobalPropertyDefinition;

import java.io.File;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.*;

/**
 * Gate check: all @GlobalProperty List fields must be marked immutable = true.
 * Fields that are intentionally mutable at runtime must be added to the MUTABLE_ALLOWLIST.
 *
 * If this test fails, either:
 * 1. Add immutable = true to your @GlobalProperty annotation, or
 * 2. Add the field to MUTABLE_ALLOWLIST with a comment explaining why it needs to be mutable.
 */
public class TestGlobalPropertyImmutableListGate {

    /**
     * Allowlist for List fields that are intentionally reassigned at runtime.
     * Format: "FullClassName.fieldName"
     */
    private static final Set<String> MUTABLE_ALLOWLIST = new HashSet<>(Arrays.asList(
            "org.zstack.core.CoreGlobalProperty.CHRONY_SERVERS",          // reassigned by ZOpsManagerImpl / ManagementNodeBackend
            "org.zstack.core.cloudbus.CloudBusGlobalProperty.SERVER_IPS"  // reassigned at runtime for cluster membership
    ));

    @Test
    public void allGlobalPropertyListFieldsMustBeImmutable() {
        List<String> violations = new ArrayList<>();

        for (Class<?> clz : findGlobalPropertyDefinitionClasses()) {
            for (Field f : clz.getDeclaredFields()) {
                GlobalProperty at = f.getAnnotation(GlobalProperty.class);
                if (at == null) {
                    continue;
                }

                if (!List.class.isAssignableFrom(f.getType())) {
                    continue;
                }

                String fieldKey = clz.getName() + "." + f.getName();
                if (MUTABLE_ALLOWLIST.contains(fieldKey)) {
                    continue;
                }

                if (!at.immutable()) {
                    violations.add(String.format(
                            "%s.%s: @GlobalProperty List field missing immutable=true. " +
                            "Add immutable=true or add to MUTABLE_ALLOWLIST with justification.",
                            clz.getSimpleName(), f.getName()));
                }
            }
        }

        if (!violations.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("%d @GlobalProperty List field(s) not marked immutable:\n", violations.size()));
            for (String v : violations) {
                sb.append("  - ").append(v).append("\n");
            }
            throw new AssertionError(sb.toString());
        }
    }

    private List<Class<?>> findGlobalPropertyDefinitionClasses() {
        List<Class<?>> result = new ArrayList<>();
        String[] basePackages = {"org.zstack"};

        for (String pkg : basePackages) {
            String path = pkg.replace('.', '/');
            try {
                Enumeration<URL> resources = Thread.currentThread().getContextClassLoader().getResources(path);
                while (resources.hasMoreElements()) {
                    URL resource = resources.nextElement();
                    if ("file".equals(resource.getProtocol())) {
                        scanDirectory(new File(resource.toURI()), pkg, result);
                    }
                }
            } catch (Exception e) {
                // skip unresolvable classpath entries
            }
        }

        return result;
    }

    private void scanDirectory(File dir, String packageName, List<Class<?>> result) {
        if (!dir.exists()) {
            return;
        }

        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                scanDirectory(file, packageName + "." + file.getName(), result);
            } else if (file.getName().endsWith(".class")) {
                String className = packageName + "." + file.getName().replace(".class", "");
                try {
                    Class<?> clz = Class.forName(className, false, Thread.currentThread().getContextClassLoader());
                    if (clz.isAnnotationPresent(GlobalPropertyDefinition.class)) {
                        result.add(clz);
                    }
                } catch (Throwable e) {
                    // skip classes that can't be loaded
                }
            }
        }
    }
}
