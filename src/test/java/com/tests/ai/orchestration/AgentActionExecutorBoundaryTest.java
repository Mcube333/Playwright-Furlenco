package com.tests.ai.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import com.framework.ai.orchestration.AgentActionExecutor;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.testng.annotations.Test;

/**
 * Phase 10 Step 4: architectural boundary tests proving {@link AgentActionExecutor} is exactly
 * what it claims to be — a pure, non-executing execution-boundary contract with no path to
 * Playwright, Appium, Selenium, a direct AI provider, TestNG lifecycle hooks, Git, MCP, shell
 * execution, persistence, network, or dynamic/reflective dispatch, and no way to fabricate an
 * {@code EXECUTED} result.
 */
public class AgentActionExecutorBoundaryTest {

    private static final Class<?> EXECUTOR_CLASS = AgentActionExecutor.class;

    private static final String EXECUTOR_FILE_NAME = "AgentActionExecutor.java";

    private static final List<String> FORBIDDEN_TYPE_NAMES = Arrays.asList(
            "com.microsoft.playwright.Page",
            "com.microsoft.playwright.Locator",
            "com.microsoft.playwright.Browser",
            "com.microsoft.playwright.BrowserContext",
            "com.microsoft.playwright.Playwright",
            "com.framework.driver.PlaywrightManager",
            "com.framework.ai.agent.ReadOnlyBrowserTool",
            "com.framework.ai.agent.AgentReasoningService",
            "com.framework.ai.client.AiClient",
            "com.framework.ai.client.GeminiApiClient",
            "com.framework.ai.config.AiConfig",
            "com.framework.listeners.TestListener",
            "com.framework.listeners.RetryAnalyzer",
            "com.framework.listeners.RetryTransformer",
            "org.testng.ITestResult",
            "io.appium.java_client.AppiumDriver",
            "org.openqa.selenium.WebDriver",
            "org.openqa.selenium.WebElement");

    private static final List<String> FORBIDDEN_IMPORTS = Arrays.asList(
            "import com.microsoft.playwright.",
            "import com.framework.driver.PlaywrightManager;",
            "import com.framework.ai.agent.ReadOnlyBrowserTool;",
            "import com.framework.ai.agent.AgentReasoningService;",
            "import com.framework.ai.client.",
            "import com.framework.ai.config.AiConfig;",
            "import com.framework.listeners.",
            "import org.testng.",
            "import io.appium.",
            "import org.openqa.selenium.",
            "import java.lang.ProcessBuilder",
            "import org.eclipse.jgit",
            "import java.io.FileWriter",
            "import java.io.FileOutputStream",
            "import java.io.BufferedWriter",
            "import java.nio.file.Files;",
            "import java.net.http.",
            "import java.net.Socket");

    private static final List<String> FORBIDDEN_METHOD_NAMES = Arrays.asList(
            "click", "dblclick", "fill", "type", "press", "check", "uncheck", "selectOption",
            "setInputFiles", "navigate", "goto", "reload", "goBack", "goForward", "evaluate", "evaluateHandle",
            "addCookies", "clearCookies", "grantPermissions",
            "apply", "run", "dispatch", "perform", "runAction", "selfHeal", "heal", "modify",
            "commit", "push", "retry", "write", "delete");

    private static final List<String> EXPECTED_PUBLIC_METHOD_NAMES = Arrays.asList("execute");

    @Test
    public void testExecutorDeclaresNoForbiddenFieldOrMethodTypes() {
        for (Field field : EXECUTOR_CLASS.getDeclaredFields()) {
            assertThat(FORBIDDEN_TYPE_NAMES)
                    .describedAs("AgentActionExecutor.%s must not be a forbidden type", field.getName())
                    .doesNotContain(field.getType().getName());
        }
        for (Method method : EXECUTOR_CLASS.getDeclaredMethods()) {
            assertThat(FORBIDDEN_TYPE_NAMES).doesNotContain(method.getReturnType().getName());
            for (Class<?> paramType : method.getParameterTypes()) {
                assertThat(FORBIDDEN_TYPE_NAMES).doesNotContain(paramType.getName());
            }
        }
    }

    @Test
    public void testExecutorDeclaresNoMutationOrExecutionMethod() {
        for (Method method : EXECUTOR_CLASS.getDeclaredMethods()) {
            assertThat(FORBIDDEN_METHOD_NAMES)
                    .describedAs("AgentActionExecutor.%s must not be a mutation/execution method", method.getName())
                    .doesNotContain(method.getName());
        }
    }

    @Test
    public void testExecutorExposesOnlyItsIntendedPublicApi() {
        List<String> publicMethods = Arrays.stream(EXECUTOR_CLASS.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .map(Method::getName)
                .distinct()
                .collect(Collectors.toList());
        assertThat(publicMethods).containsExactlyInAnyOrderElementsOf(EXPECTED_PUBLIC_METHOD_NAMES);
    }

    @Test
    public void testExecutorSourceFileImportsNoForbiddenDependency() throws IOException {
        Path file = executorSourceFile();
        assertThat(Files.isRegularFile(file)).describedAs("Expected %s to exist", file).isTrue();

        List<String> importLines = Files.readAllLines(file).stream()
                .map(String::trim).filter(l -> l.startsWith("import ")).toList();
        for (String importLine : importLines) {
            for (String forbidden : FORBIDDEN_IMPORTS) {
                assertThat(importLine)
                        .describedAs("AgentActionExecutor.java must not import a forbidden dependency")
                        .doesNotStartWith(forbidden);
            }
        }
    }

    @Test
    public void testExecutorSourceFileContainsNoShellGitReflectionOrFileWriteApi() throws IOException {
        String content = Files.readString(executorSourceFile());
        assertThat(content)
                .doesNotContain("ProcessBuilder")
                .doesNotContain("Runtime.getRuntime().exec")
                .doesNotContain("git ")
                .doesNotContain("Method.invoke")
                .doesNotContain("getDeclaredMethod(")
                .doesNotContain("getMethod(")
                .doesNotContain("Class.forName")
                .doesNotContain("Files.write")
                .doesNotContain("Files.delete")
                .doesNotContain("FileWriter")
                .doesNotContain("FileOutputStream")
                .doesNotContain("BufferedWriter")
                .doesNotContainIgnoringCase("mcp")
                .doesNotContain("page.evaluate")
                .doesNotContain(".click(")
                .doesNotContain(".fill(")
                .doesNotContain(".goto(");
    }

    @Test
    public void testExecutorSourceFileContainsNoTestNgOrAiClientReference() throws IOException {
        String content = Files.readString(executorSourceFile());
        assertThat(content)
                .doesNotContain("org.testng")
                .doesNotContain("ITestResult")
                .doesNotContain("AiClient")
                .doesNotContain("GeminiApiClient")
                .doesNotContain("com.microsoft.playwright");
    }

    @Test
    public void testExecutorSourceFileContainsNoNetworkCapability() throws IOException {
        String content = Files.readString(executorSourceFile());
        assertThat(content).doesNotContainIgnoringCase("http").doesNotContain("Socket").doesNotContain("URLConnection");
    }

    @Test
    public void testExecutorClassIsNotRegisteredAsATestNgListener() {
        List<Class<?>> interfaces = Arrays.asList(EXECUTOR_CLASS.getInterfaces());
        assertThat(interfaces).noneMatch(i -> i.getName().startsWith("org.testng"));
    }

    @Test
    public void testExistingLifecycleAndOrchestrationClassesDoNotReferenceTheNewExecutor() throws IOException {
        List<Path> files = Arrays.asList(
                Paths.get("src", "main", "java", "com", "framework", "listeners", "TestListener.java"),
                Paths.get("src", "main", "java", "com", "framework", "listeners", "RetryAnalyzer.java"),
                Paths.get("src", "main", "java", "com", "framework", "listeners", "RetryTransformer.java"),
                Paths.get("src", "main", "java", "com", "framework", "driver", "PlaywrightManager.java"),
                Paths.get("src", "main", "java", "com", "framework", "ai", "orchestration", "AgentOrchestrationService.java"),
                Paths.get("src", "main", "java", "com", "framework", "ai", "orchestration", "AgentRecommendationConsumer.java"),
                Paths.get("src", "main", "java", "com", "framework", "ai", "orchestration", "AgentApprovalService.java"),
                Paths.get("src", "main", "java", "com", "framework", "ai", "orchestration", "AgentApprovalRecordStore.java"),
                Paths.get("src", "main", "java", "com", "framework", "ai", "orchestration", "AgentApprovalSummaryReporter.java"),
                Paths.get("src", "main", "java", "com", "framework", "ai", "orchestration", "AgentActionAuditStore.java"),
                Paths.get("src", "main", "java", "com", "framework", "ai", "orchestration", "AgentActionAuditReporter.java"),
                Paths.get("src", "main", "java", "com", "framework", "ai", "orchestration", "AgentActionOrchestrationService.java"));

        for (Path file : files) {
            assertThat(Files.isRegularFile(file)).describedAs("Expected %s to exist", file).isTrue();
            String content = Files.readString(file);
            assertThat(content)
                    .describedAs("%s must not reference AgentActionExecutor", file.getFileName())
                    .doesNotContain("AgentActionExecutor");
        }
    }

    @Test
    public void testExecutorHasNoStaticInitializerSideEffectsBeyondItsLogger() {
        Field[] staticFields = Arrays.stream(EXECUTOR_CLASS.getDeclaredFields())
                .filter(f -> Modifier.isStatic(f.getModifiers()))
                .toArray(Field[]::new);
        assertThat(staticFields).hasSize(1);
        assertThat(staticFields[0].getType().getName()).isEqualTo("org.apache.logging.log4j.Logger");
    }

    @Test
    public void testPomXmlWasNotModifiedByThisStep() throws IOException {
        String pom = Files.readString(Paths.get("pom.xml"));
        assertThat(pom).doesNotContain("<artifactId>agent-action-executor</artifactId>");
    }

    @Test
    public void testExecutionGuardIsNeverModifiedMarkerCheck() throws IOException {
        Path file = Paths.get("src", "main", "java", "com", "framework", "ai", "agent", "AgentExecutionGuard.java");
        String content = Files.readString(file);
        assertThat(content)
                .contains("FAIL-CLOSED BY DESIGN")
                .contains("Step 5: the mandatory security/policy boundary");
    }

    @Test
    public void testNoSecondGuardOrRealExecutorClassWasIntroducedAlongsideTheContract() {
        assertThat(classExists("com.framework.ai.orchestration.RealAgentActionExecutor")).isFalse();
        assertThat(classExists("com.framework.ai.orchestration.AgentExecutionPolicy")).isFalse();
        assertThat(classExists("com.framework.ai.agent.AgentActionExecutor")).isFalse();
    }

    private Path executorSourceFile() {
        return Paths.get("src", "main", "java", "com", "framework", "ai", "orchestration", EXECUTOR_FILE_NAME);
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
