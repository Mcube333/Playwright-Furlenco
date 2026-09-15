package com.tests.ai.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.framework.ai.agent.AgentExecutionGuard;
import com.framework.ai.agent.AgentExecutionGuardResult;
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
 * Phase 8 Step 5: architectural boundary tests proving {@link AgentExecutionGuard} and
 * {@link AgentExecutionGuardResult} are exactly what they claim to be — a deterministic,
 * side-effect-free policy boundary with no path to Playwright, AI providers, TestNG lifecycle
 * hooks, Git, MCP, or dynamic/reflective execution.
 *
 * Scoped precisely to the two Step 5 files by filename (not a directory listing) so that
 * neighboring Step 3/4 files — which legitimately import Playwright ({@code ReadOnlyBrowserTool})
 * or the AI client ({@code AgentReasoningService}) — never cause a false positive here, per this
 * step's own instruction to scope precisely rather than weaken the security rule.
 */
public class AgentExecutionGuardBoundaryTest {

    private static final List<Class<?>> GUARD_CLASSES =
            Arrays.asList(AgentExecutionGuard.class, AgentExecutionGuardResult.class);

    private static final List<String> FORBIDDEN_TYPE_NAMES = Arrays.asList(
            "com.microsoft.playwright.Page",
            "com.microsoft.playwright.Locator",
            "com.microsoft.playwright.Browser",
            "com.microsoft.playwright.BrowserContext",
            "com.microsoft.playwright.Playwright",
            "com.framework.driver.PlaywrightManager",
            "com.framework.ai.agent.ReadOnlyBrowserTool",
            "com.framework.ai.agent.AgentBrowserObservation",
            "com.framework.ai.agent.AgentReasoningService",
            "com.framework.ai.client.AiClient",
            "com.framework.ai.client.GeminiApiClient",
            "com.framework.listeners.TestListener",
            "com.framework.listeners.RetryAnalyzer",
            "com.framework.listeners.RetryTransformer",
            "org.testng.ITestResult");

    private static final List<String> FORBIDDEN_IMPORTS = Arrays.asList(
            "import com.microsoft.playwright.",
            "import com.framework.driver.PlaywrightManager;",
            "import com.framework.ai.agent.ReadOnlyBrowserTool;",
            "import com.framework.ai.agent.AgentBrowserObservation;",
            "import com.framework.ai.agent.AgentReasoningService;",
            "import com.framework.ai.client.AiClient;",
            "import com.framework.ai.client.GeminiApiClient;",
            "import com.framework.listeners.",
            "import org.testng.ITestResult;",
            "import java.lang.ProcessBuilder",
            "import org.eclipse.jgit");

    // Deliberately excludes "evaluate": AgentExecutionGuard's own core, intended public API method
    // is named evaluate(AgentContext, AgentDecision) — a naming coincidence with Playwright's
    // page.evaluate(), not a Playwright call. The far more precise check for that real risk is
    // testGuardClassesAvoidForbiddenFieldAndMethodTypes() (Playwright TYPE references) plus the
    // "no Method.invoke" source scan below — both would catch an actual attempt to reach
    // Playwright's evaluate(), so excluding the bare name here does not weaken the security rule.
    private static final List<String> FORBIDDEN_METHOD_NAMES = Arrays.asList(
            "click", "dblclick", "fill", "type", "press", "check", "uncheck", "selectOption",
            "navigate", "reload", "goBack", "goForward", "evaluateHandle",
            "execute", "apply", "run", "runAction", "selfHeal", "approve");

    private static final List<String> STEP_5_FILE_NAMES =
            Arrays.asList("AgentExecutionGuard.java", "AgentExecutionGuardResult.java");

    @Test
    public void testGuardClassesAvoidForbiddenFieldAndMethodTypes() {
        for (Class<?> clazz : GUARD_CLASSES) {
            for (Field field : clazz.getDeclaredFields()) {
                assertThat(FORBIDDEN_TYPE_NAMES)
                        .describedAs("%s.%s must not be a forbidden type", clazz.getSimpleName(), field.getName())
                        .doesNotContain(field.getType().getName());
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
    public void testGuardClassesDeclareNoMutationOrExecutionMethod() {
        for (Class<?> clazz : GUARD_CLASSES) {
            for (Method method : clazz.getDeclaredMethods()) {
                assertThat(FORBIDDEN_METHOD_NAMES)
                        .describedAs("%s.%s must not be a mutation/execution method", clazz.getSimpleName(), method.getName())
                        .doesNotContain(method.getName());
            }
        }
    }

    @Test
    public void testStep5SourceFilesImportNoForbiddenDependency() throws IOException {
        Path agentSourceDir = Paths.get("src", "main", "java", "com", "framework", "ai", "agent");
        assertThat(Files.isDirectory(agentSourceDir)).isTrue();

        for (String fileName : STEP_5_FILE_NAMES) {
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
    public void testStep5SourceFilesContainNoShellOrGitInvocation() throws IOException {
        Path agentSourceDir = Paths.get("src", "main", "java", "com", "framework", "ai", "agent");

        for (String fileName : STEP_5_FILE_NAMES) {
            String content = Files.readString(agentSourceDir.resolve(fileName));
            assertThat(content)
                    .doesNotContain("ProcessBuilder")
                    .doesNotContain("Runtime.getRuntime().exec")
                    .doesNotContain("git ")
                    .doesNotContain("Method.invoke")
                    .doesNotContain(".invoke(")
                    .doesNotContain("getDeclaredMethod(")
                    .doesNotContain("Files.write")
                    .doesNotContain("Files.delete");
        }
    }

    @Test
    public void testAgentPolicyClassWasNotIntroduced() {
        // Step 5 deliberately folds policy checks directly into AgentExecutionGuard rather than
        // introducing a separate AgentPolicy framework, per the "no large policy framework"
        // instruction — confirmed here so a future step cannot silently reintroduce one unnoticed.
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
}
