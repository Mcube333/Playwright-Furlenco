package com.tests.ai.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.framework.ai.agent.AgentReasoningService;
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
 * Phase 8 Step 3: architectural boundary tests for the new reasoning components
 * ({@code AgentReasoningService}, {@code AgentReasoningPrompt}, {@code AgentReasoningResponse}).
 *
 * Unlike {@link AgentModelBoundaryTest} (Step 2, pure data models), these files ARE allowed — and
 * expected — to depend on the existing AI infrastructure ({@code AiClient}, {@code AiConfig},
 * {@code GeminiApiClient}) and on Phase 7's {@code FailureDiagnosis}/{@code SuggestedFix}, per
 * Step 3's own scope ("Step 3 may depend on existing AI infrastructure and Phase 7 models"). What
 * must still never appear is anything Playwright-capable, TestNG-lifecycle-capable, Git-capable,
 * or belonging to a not-yet-implemented later Phase 8 concept.
 */
public class AgentReasoningBoundaryTest {

    private static final List<Class<?>> REASONING_CLASSES = Arrays.asList(AgentReasoningService.class);

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
            "com.framework.ai.diagnosis.FailureDiagnosisService",
            "com.framework.ai.diagnosis.FailureDiagnosisReporter",
            "com.framework.ai.diagnosis.FailureDiagnosisHelper",
            "com.framework.ai.locatoradvisor.LocatorAnalysisService",
            "com.framework.ai.service.FailureAnalysisService",
            "com.framework.ai.locatoradvisor.runtime.RuntimeLocatorValidator",
            "org.testng.ITestResult");

    private static final List<String> FORBIDDEN_IMPORTS = Arrays.asList(
            "import com.microsoft.playwright.",
            "import com.framework.driver.PlaywrightManager;",
            "import com.framework.listeners.",
            "import com.framework.ai.diagnosis.FailureDiagnosisService;",
            "import com.framework.ai.diagnosis.FailureDiagnosisReporter;",
            "import com.framework.ai.diagnosis.FailureDiagnosisHelper;",
            "import com.framework.ai.locatoradvisor.LocatorAnalysisService;",
            "import com.framework.ai.service.FailureAnalysisService;",
            "import com.framework.ai.locatoradvisor.runtime.RuntimeLocatorValidator;",
            "import org.testng.ITestResult;",
            "import org.eclipse.jgit",
            "import java.lang.ProcessBuilder");

    private static final List<String> STEP_3_FILE_NAMES = Arrays.asList(
            "AgentReasoningService.java", "AgentReasoningPrompt.java", "AgentReasoningResponse.java");

    private static final List<String> FORBIDDEN_METHOD_NAMES =
            Arrays.asList("execute", "apply", "heal", "run", "approve", "transition", "click", "fill", "navigate");

    @Test
    public void testReasoningServiceFieldsAndMethodsAvoidForbiddenTypes() {
        for (Class<?> clazz : REASONING_CLASSES) {
            for (Field field : clazz.getDeclaredFields()) {
                assertThat(FORBIDDEN_TYPE_NAMES).doesNotContain(field.getType().getName());
            }
            for (Method method : clazz.getDeclaredMethods()) {
                assertThat(FORBIDDEN_TYPE_NAMES).doesNotContain(method.getReturnType().getName());
                for (Class<?> paramType : method.getParameterTypes()) {
                    assertThat(FORBIDDEN_TYPE_NAMES).doesNotContain(paramType.getName());
                }
            }
        }
    }

    @Test
    public void testReasoningServiceDeclaresNoExecutionMethod() {
        for (Class<?> clazz : REASONING_CLASSES) {
            for (Method method : clazz.getDeclaredMethods()) {
                assertThat(FORBIDDEN_METHOD_NAMES).doesNotContain(method.getName());
            }
        }
    }

    @Test
    public void testStep3SourceFilesImportNoForbiddenDependency() throws IOException {
        Path agentSourceDir = Paths.get("src", "main", "java", "com", "framework", "ai", "agent");
        assertThat(Files.isDirectory(agentSourceDir)).isTrue();

        for (String fileName : STEP_3_FILE_NAMES) {
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
    public void testNoLaterPhase8ComponentsExistYet() {
        // AgentExecutionGuard was deliberately removed from this list: it was always a planned
        // later step (Phase 8 Step 5), which has since legitimately implemented it — see
        // AgentExecutionGuardBoundaryTest for that class's own architecture checks. Everything
        // still listed here remains genuinely unimplemented as of Step 5.
        List<String> notYetImplemented = Arrays.asList(
                "com.framework.ai.agent.AgentPolicy",
                "com.framework.ai.agent.AgentActionExecutor",
                "com.framework.ai.agent.BrowserAgentTools",
                "com.framework.ai.agent.SelfHealingService");
        for (String fqcn : notYetImplemented) {
            assertThat(classExists(fqcn)).describedAs("%s must not exist yet", fqcn).isFalse();
        }
    }

    private boolean classExists(String fqcn) {
        try {
            Class.forName(fqcn);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
