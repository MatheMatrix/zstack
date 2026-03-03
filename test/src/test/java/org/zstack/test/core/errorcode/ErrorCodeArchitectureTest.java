package org.zstack.test.core.errorcode;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.AccessTarget;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchUnitRunner;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.zstack.core.errorcode.ErrorFacade;
import org.zstack.header.allocator.AbstractHostAllocatorFlow;
import org.zstack.header.core.FutureCompletion;
import org.zstack.header.core.FutureReturnValueCompletion;
import org.zstack.header.errorcode.ErrorCode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.AccessTarget.Predicates.constructor;
import static com.tngtech.archunit.core.domain.AccessTarget.Predicates.declaredIn;
import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.equivalentTo;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@RunWith(ArchUnitRunner.class)
@AnalyzeClasses(packages = "org.zstack")
public class ErrorCodeArchitectureTest {
    private static final Pattern GET_CAUSES_INDEX_ZERO = Pattern.compile("getCauses\\s*\\(\\s*\\)\\s*\\.\\s*get\\s*\\(\\s*0\\s*\\)");

    @ArchTest
    public static final ArchRule no_direct_error_code_construction =
            noClasses()
                    .that().resideOutsideOfPackages("org.zstack.header.errorcode..", "org.zstack.core.errorcode..", "..test..")
                    .and(not(allowedDirectErrorCodeConstruction()))
                    .should().callConstructorWhere(
                            target(declaredIn(ErrorCode.class).and(constructor()))
                    );

    @ArchTest
    public static final ArchRule no_direct_error_facade_calls =
            noClasses()
                    .that().resideOutsideOfPackages("org.zstack.core.errorcode..", "..test..")
                    .should().callMethodWhere(
                            callToErrorFacade("stringToOperationError")
                                    .or(callToErrorFacade("instantiateErrorCode"))
                                    .or(callToErrorFacade("throwableToOperationError"))
                                    .or(callToErrorFacade("throwableToInternalError"))
                                    .or(callToErrorFacade("stringToInternalError"))
                    );

    @Test
    public void no_get_causes_index_zero_pattern() throws IOException {
        List<String> violations = new ArrayList<>();
        Path root = Paths.get(System.getProperty("user.dir"));
        try {
            Files.walk(root)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.toString().contains("/target/"))
                    .filter(path -> !path.toString().contains("/.git/"))
                    .forEach(path -> scanFileForPattern(path, violations));
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }

        Assert.assertTrue("Found getCauses().get(0) usage:\n" + String.join("\n", violations), violations.isEmpty());
    }

    private static DescribedPredicate<JavaClass> allowedDirectErrorCodeConstruction() {
        return equivalentTo(FutureCompletion.class)
                .or(equivalentTo(FutureReturnValueCompletion.class))
                .or(equivalentTo(AbstractHostAllocatorFlow.class))
                .as("allowed direct ErrorCode construction");
    }

    private static DescribedPredicate<AccessTarget> errorFacadeMethod(String methodName) {
        return declaredIn(ErrorFacade.class)
                .and(name(methodName).forSubtype())
                .as("ErrorFacade.%s(..)", methodName);
    }

    private static DescribedPredicate<JavaMethodCall> callToErrorFacade(String methodName) {
        return target(errorFacadeMethod(methodName))
                .as("call ErrorFacade.%s(..)", methodName);
    }

    private static void scanFileForPattern(Path path, List<String> violations) {
        List<String> lines;
        try {
            lines = Files.readAllLines(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (GET_CAUSES_INDEX_ZERO.matcher(line).find()) {
                violations.add(path + ":" + (i + 1) + ":" + line.trim());
            }
        }
    }
}
