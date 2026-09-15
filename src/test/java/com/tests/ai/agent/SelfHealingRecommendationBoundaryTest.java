package com.tests.ai.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.framework.ai.agent.SelfHealingRecommendation;
import com.framework.ai.agent.SelfHealingRecommendationReporter;
import com.framework.ai.agent.SelfHealingRecommendationService;
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
 * Phase 8 Step 6: architectural boundary tests proving the self-healing recommendation layer
 * contains no Playwright mutation, no source-file writing, no shell/Git invocation, and no
 * TestNG-lifecycle integration. Scoped precisely to the three Step 6 files by filename so that
 * neighboring Step 3/4 files (which legitimately import Playwright or the AI client) never cause a
 * false positive here.
 */
public class SelfHealingRecommendationBoundaryTest {

    private static final List<Class<?>> STEP_6_CLASSES = Arrays.asList(
            SelfHealingRecommendation.class, SelfHealingRecommendationService.class, SelfHealingRecommendationReporter.class);

    private static final List<String> FORBIDDEN_TYPE_NAMES = Arrays.asList(
            "com.microsoft.playwright.Page", "com.microsoft.playwright.Locator",
            "com.microsoft.playwright.Browser", "com.microsoft.playwright.BrowserContext",
            "com.microsoft.playwright.Playwright", "com.framework.driver.PlaywrightManager",
            "com.framework.ai.client.AiClient", "com.framework.ai.client.GeminiApiClient",
            "com.framework.listeners.TestListener", "com.framework.listeners.RetryAnalyzer",
            "com.framework.listeners.RetryTransformer", "org.testng.ITestResult");

    private static final List<String> FORBIDDEN_IMPORTS = Arrays.asList(
            "import com.microsoft.playwright.", "import com.framework.driver.PlaywrightManager;",
            "import com.framework.ai.client.", "import com.framework.listeners.",
            "import org.testng.ITestResult;", "import java.io.FileOutputStream;",
            "import java.lang.ProcessBuilder", "import org.eclipse.jgit");

    private static final List<String> FORBIDDEN_METHOD_NAMES = Arrays.asList(
            "click", "dblclick", "fill", "type", "press", "check", "uncheck", "selectOption",
            "navigate", "reload", "goBack", "goForward", "evaluateHandle",
            "execute", "apply", "runaction", "selfheal", "approve", "retry");

    private static final List<String> STEP_6_FILE_NAMES = Arrays.asList(
            "SelfHealingRecommendation.java", "SelfHealingRecommendationService.java", "SelfHealingRecommendationReporter.java");

    @Test
    public void testStep6ClassesAvoidForbiddenFieldAndMethodTypes() {
        for (Class<?> clazz : STEP_6_CLASSES) {
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
    public void testStep6ClassesDeclareNoMutationOrExecutionMethod() {
        for (Class<?> clazz : STEP_6_CLASSES) {
            for (Method method : clazz.getDeclaredMethods()) {
                assertThat(FORBIDDEN_METHOD_NAMES)
                        .describedAs("%s.%s must not be a mutation/execution method", clazz.getSimpleName(), method.getName())
                        .doesNotContain(method.getName().toLowerCase());
            }
        }
    }

    @Test
    public void testStep6SourceFilesImportNoForbiddenDependency() throws IOException {
        Path agentSourceDir = Paths.get("src", "main", "java", "com", "framework", "ai", "agent");
        assertThat(Files.isDirectory(agentSourceDir)).isTrue();

        for (String fileName : STEP_6_FILE_NAMES) {
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
    public void testStep6SourceFilesContainNoFileWriteShellOrGitInvocation() throws IOException {
        Path agentSourceDir = Paths.get("src", "main", "java", "com", "framework", "ai", "agent");

        for (String fileName : STEP_6_FILE_NAMES) {
            String content = Files.readString(agentSourceDir.resolve(fileName));
            assertThat(content)
                    .doesNotContain("Files.write").doesNotContain("Files.delete")
                    .doesNotContain("FileOutputStream").doesNotContain("FileWriter")
                    .doesNotContain("ProcessBuilder").doesNotContain("Runtime.getRuntime().exec")
                    .doesNotContain("Thread.sleep")
                    .doesNotContain("git ")
                    .doesNotContain("Method.invoke").doesNotContain(".invoke(");
        }
    }

    @Test
    public void testNoExecutableFlagExistsOnTheRecommendationModel() {
        for (Method method : SelfHealingRecommendation.class.getDeclaredMethods()) {
            assertThat(method.getName().toLowerCase()).doesNotContain("executable");
        }
        for (Field field : SelfHealingRecommendation.class.getDeclaredFields()) {
            assertThat(field.getName().toLowerCase()).doesNotContain("executable");
        }
    }

    @Test
    public void testServiceNeverReadsGuardResultAllowedValue() throws IOException {
        // Empirical, source-level proof that AgentExecutionGuardResult.isAllowed() is never called
        // anywhere in the service — the strongest available guarantee that guard state cannot be
        // reinterpreted as permission, short of the behavioral test already covering this in
        // SelfHealingRecommendationServiceTest#test20GuardAllowedDoesNotExecuteAnything.
        Path file = Paths.get("src", "main", "java", "com", "framework", "ai", "agent", "SelfHealingRecommendationService.java");
        String content = Files.readString(file);

        assertThat(content).doesNotContain(".isAllowed()");
    }
}
