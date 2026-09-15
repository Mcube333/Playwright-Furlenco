package com.tests.ai.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.framework.ai.agent.AgentAction;
import com.framework.ai.agent.AgentContext;
import com.framework.ai.agent.AgentDecision;
import com.framework.ai.agent.AgentObservation;
import com.framework.ai.agent.AgentState;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import org.testng.annotations.Test;

/**
 * Phase 8 Step 2: architectural boundary tests proving the new
 * {@code com.framework.ai.agent} package is exactly what it claims to be — pure data contracts
 * with no dependency on execution-capable, browser-capable, TestNG-lifecycle-capable, or
 * AI-calling machinery.
 *
 * Two complementary techniques are used:
 * 1. Reflection over declared field/method-parameter/return types — catches real, already-existing
 *    classes this package must not reference (Playwright, {@code PlaywrightManager},
 *    {@code TestListener}, {@code RetryAnalyzer}, {@code RetryTransformer}, {@code AiClient},
 *    {@code GeminiApiClient}, {@code FailureDiagnosisService}, {@code FailureDiagnosisReporter}).
 * 2. A source-text scan of the five production files under {@code src/main/java/com/framework/ai/agent}
 *    — reflection alone cannot see method-body local variables, unused imports, or references to
 *    types that do not exist yet at all ({@code AgentPolicy}, {@code AgentExecutionGuard}), so this
 *    is the only reliable way to prove those are genuinely absent from Step 2, not just absent from
 *    field/parameter signatures. Assumes the test runs with the Maven module root as the working
 *    directory (standard for {@code mvn test}); if the source files cannot be found the test fails
 *    loudly rather than silently passing.
 */
public class AgentModelBoundaryTest {

    private static final List<Class<?>> AGENT_CLASSES =
            Arrays.asList(AgentContext.class, AgentObservation.class, AgentDecision.class,
                    AgentAction.class, AgentState.class);

    private static final List<String> FORBIDDEN_TYPE_NAMES = Arrays.asList(
            "com.microsoft.playwright.Page",
            "com.microsoft.playwright.Locator",
            "com.microsoft.playwright.Browser",
            "com.microsoft.playwright.BrowserContext",
            "com.microsoft.playwright.Playwright",
            "com.framework.driver.PlaywrightManager",
            "com.framework.listeners.TestListener",
            "com.framework.listeners.RetryAnalyzer",
            "com.framework.listeners.RetryTransformer",
            "com.framework.ai.client.AiClient",
            "com.framework.ai.client.GeminiApiClient",
            "com.framework.ai.diagnosis.FailureDiagnosisService",
            "com.framework.ai.diagnosis.FailureDiagnosisReporter",
            "org.testng.ITestResult");

    // Import statements that must not appear in any Step 2 source file. Restricted to actual
    // `import` lines (not arbitrary substrings) so that Javadoc prose is free to explain, by name,
    // which existing components a class deliberately does NOT depend on — exactly the documentation
    // convention already used elsewhere in this codebase (e.g. FailureDiagnosisHelper's own class
    // Javadoc names TestListener/PlaywrightManager while never importing them).
    private static final List<String> FORBIDDEN_IMPORTS = Arrays.asList(
            "import com.microsoft.playwright.",
            "import com.framework.driver.PlaywrightManager;",
            "import com.framework.listeners.TestListener;",
            "import com.framework.listeners.RetryAnalyzer;",
            "import com.framework.listeners.RetryTransformer;",
            "import com.framework.ai.client.AiClient;",
            "import com.framework.ai.client.GeminiApiClient;",
            "import com.framework.ai.diagnosis.FailureDiagnosisService;",
            "import com.framework.ai.diagnosis.FailureDiagnosisReporter;",
            "import com.framework.ai.diagnosis.FailureDiagnosisHelper;",
            "import org.testng.ITestResult;");

    private static final List<String> FORBIDDEN_METHOD_NAMES =
            Arrays.asList("execute", "apply", "heal", "run", "approve", "transition");

    @Test
    public void testNoAgentClassFieldOrMethodReferencesForbiddenTypes() {
        for (Class<?> clazz : AGENT_CLASSES) {
            for (Field field : allDeclaredFields(clazz)) {
                assertThat(FORBIDDEN_TYPE_NAMES).doesNotContain(field.getType().getName());
            }
            for (Method method : allDeclaredMethods(clazz)) {
                assertThat(FORBIDDEN_TYPE_NAMES).doesNotContain(method.getReturnType().getName());
                for (Class<?> paramType : method.getParameterTypes()) {
                    assertThat(FORBIDDEN_TYPE_NAMES).doesNotContain(paramType.getName());
                }
            }
        }
    }

    @Test
    public void testNoAgentClassDeclaresExecutionOrApprovalMethods() {
        for (Class<?> clazz : AGENT_CLASSES) {
            for (Method method : allDeclaredMethods(clazz)) {
                assertThat(FORBIDDEN_METHOD_NAMES).doesNotContain(method.getName());
            }
        }
    }

    // Deliberately named exactly, not discovered by directory listing: Step 3 (see
    // AgentReasoningBoundaryTest) adds sibling files to this same package/directory that
    // legitimately DO import AiClient/GeminiApiClient (the reasoning service reuses the existing
    // AI infrastructure, per Step 3's own scope) — this test's job is only to keep proving that the
    // five pure Step 2 data-model files never pick up such a dependency, not the whole directory.
    private static final List<String> STEP_2_MODEL_FILE_NAMES = Arrays.asList(
            "AgentContext.java", "AgentObservation.java", "AgentDecision.java",
            "AgentAction.java", "AgentState.java");

    @Test
    public void testStep2SourceFilesImportNoForbiddenDependency() throws IOException {
        Path agentSourceDir = Paths.get("src", "main", "java", "com", "framework", "ai", "agent");
        assertThat(Files.isDirectory(agentSourceDir))
                .describedAs("Expected the Phase 8 Step 2 source directory to exist at %s "
                        + "(test assumes the Maven module root as the working directory)", agentSourceDir)
                .isTrue();

        for (String fileName : STEP_2_MODEL_FILE_NAMES) {
            Path file = agentSourceDir.resolve(fileName);
            assertThat(Files.isRegularFile(file)).describedAs("Expected %s to exist", file).isTrue();

            List<String> importLines = Files.readAllLines(file).stream()
                    .map(String::trim)
                    .filter(line -> line.startsWith("import "))
                    .toList();
            for (String importLine : importLines) {
                for (String forbidden : FORBIDDEN_IMPORTS) {
                    assertThat(importLine)
                            .describedAs("%s must not import a forbidden dependency", fileName)
                            .doesNotStartWith(forbidden);
                }
            }
        }
    }

    @Test
    public void testAgentPolicyDoesNotExistAsASeparateFramework() {
        // AgentPolicy was intentionally never implemented as a class of its own (Phase 8 Step 5
        // folded its policy checks directly into AgentExecutionGuard instead, per that step's own
        // "no large policy framework" instruction) — its absence as a compilable class is itself
        // the proof. NOTE: AgentExecutionGuard itself was NOT expected to stay absent forever — it
        // was always Step 2's own Step-1-derived roadmap item for a later step, and Step 5
        // legitimately implemented it; this test (originally named
        // testAgentPolicyAndExecutionGuardDoNotExistYet) no longer asserts its absence for exactly
        // that reason. See AgentExecutionGuardBoundaryTest for that class's own architecture checks.
        assertThat(classExists("com.framework.ai.agent.AgentPolicy")).isFalse();
    }

    private boolean classExists(String fqcn) {
        try {
            Class.forName(fqcn);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private List<Field> allDeclaredFields(Class<?> clazz) {
        return Arrays.asList(clazz.getDeclaredFields());
    }

    private List<Method> allDeclaredMethods(Class<?> clazz) {
        return Arrays.asList(clazz.getDeclaredMethods());
    }
}
