package com.tests.ai.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import com.framework.ai.orchestration.AgentActionOrchestrationService;
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
 * Phase 10 Step 3: architectural boundary tests proving {@link AgentActionOrchestrationService} is
 * exactly what it claims to be — a pure, non-executing orchestration adapter with no path to
 * Playwright, Appium, Selenium, a direct AI provider, TestNG lifecycle hooks, Git, MCP, shell
 * execution, persistence, or dynamic/reflective dispatch, and no way to fabricate an
 * {@code EXECUTED}/{@code FAILED} result.
 */
public class AgentActionOrchestrationServiceBoundaryTest {

    private static final Class<?> SERVICE_CLASS = AgentActionOrchestrationService.class;

    private static final String SERVICE_FILE_NAME = "AgentActionOrchestrationService.java";

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
            "import java.net.http.");

    private static final List<String> FORBIDDEN_METHOD_NAMES = Arrays.asList(
            "click", "dblclick", "fill", "type", "press", "check", "uncheck", "selectOption",
            "setInputFiles", "navigate", "goto", "reload", "goBack", "goForward", "evaluate", "evaluateHandle",
            "addCookies", "clearCookies", "grantPermissions",
            "execute", "apply", "run", "dispatch", "perform", "runAction", "selfHeal", "heal", "modify",
            "commit", "push", "retry", "write", "delete");

    private static final List<String> EXPECTED_PUBLIC_METHOD_NAMES = Arrays.asList("processApproval");

    @Test
    public void testServiceDeclaresNoForbiddenFieldOrMethodTypes() {
        for (Field field : SERVICE_CLASS.getDeclaredFields()) {
            assertThat(FORBIDDEN_TYPE_NAMES)
                    .describedAs("AgentActionOrchestrationService.%s must not be a forbidden type", field.getName())
                    .doesNotContain(field.getType().getName());
        }
        for (Method method : SERVICE_CLASS.getDeclaredMethods()) {
            assertThat(FORBIDDEN_TYPE_NAMES).doesNotContain(method.getReturnType().getName());
            for (Class<?> paramType : method.getParameterTypes()) {
                assertThat(FORBIDDEN_TYPE_NAMES).doesNotContain(paramType.getName());
            }
        }
    }

    @Test
    public void testServiceDeclaresNoMutationOrExecutionMethod() {
        for (Method method : SERVICE_CLASS.getDeclaredMethods()) {
            assertThat(FORBIDDEN_METHOD_NAMES)
                    .describedAs("AgentActionOrchestrationService.%s must not be a mutation/execution method", method.getName())
                    .doesNotContain(method.getName());
        }
    }

    @Test
    public void testServiceExposesOnlyItsIntendedPublicApi() {
        List<String> publicMethods = Arrays.stream(SERVICE_CLASS.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .map(Method::getName)
                .distinct()
                .collect(Collectors.toList());
        assertThat(publicMethods).containsExactlyInAnyOrderElementsOf(EXPECTED_PUBLIC_METHOD_NAMES);
    }

    @Test
    public void testServiceSourceFileImportsNoForbiddenDependency() throws IOException {
        Path file = serviceSourceFile();
        assertThat(Files.isRegularFile(file)).describedAs("Expected %s to exist", file).isTrue();

        List<String> importLines = Files.readAllLines(file).stream()
                .map(String::trim).filter(l -> l.startsWith("import ")).toList();
        for (String importLine : importLines) {
            for (String forbidden : FORBIDDEN_IMPORTS) {
                assertThat(importLine)
                        .describedAs("AgentActionOrchestrationService.java must not import a forbidden dependency")
                        .doesNotStartWith(forbidden);
            }
        }
    }

    @Test
    public void testServiceSourceFileContainsNoShellGitReflectionOrFileWriteApi() throws IOException {
        String content = Files.readString(serviceSourceFile());
        assertThat(content)
                .doesNotContain("ProcessBuilder")
                .doesNotContain("Runtime.getRuntime().exec")
                .doesNotContain("git ")
                .doesNotContain("Method.invoke")
                .doesNotContain("getDeclaredMethod(")
                .doesNotContain("getMethod(")
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
    public void testServiceSourceFileContainsNoTestNgOrAiClientReference() throws IOException {
        String content = Files.readString(serviceSourceFile());
        assertThat(content)
                .doesNotContain("org.testng")
                .doesNotContain("ITestResult")
                .doesNotContain("AiClient")
                .doesNotContain("GeminiApiClient")
                .doesNotContain("com.microsoft.playwright");
    }

    @Test
    public void testServiceClassIsNotRegisteredAsATestNgListener() {
        List<Class<?>> interfaces = Arrays.asList(SERVICE_CLASS.getInterfaces());
        assertThat(interfaces).noneMatch(i -> i.getName().startsWith("org.testng"));
    }

    @Test
    public void testExistingLifecycleAndPhase9ClassesDoNotReferenceTheNewService() throws IOException {
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
                Paths.get("src", "main", "java", "com", "framework", "ai", "orchestration", "AgentActionAuditReporter.java"));

        for (Path file : files) {
            assertThat(Files.isRegularFile(file)).describedAs("Expected %s to exist", file).isTrue();
            String content = Files.readString(file);
            assertThat(content)
                    .describedAs("%s must not reference AgentActionOrchestrationService", file.getFileName())
                    .doesNotContain("AgentActionOrchestrationService");
        }
    }

    @Test
    public void testServiceHasNoStaticInitializerSideEffectsBeyondItsLogger() {
        Field[] staticFields = Arrays.stream(SERVICE_CLASS.getDeclaredFields())
                .filter(f -> Modifier.isStatic(f.getModifiers()))
                .toArray(Field[]::new);
        assertThat(staticFields).hasSize(1);
        assertThat(staticFields[0].getType().getName()).isEqualTo("org.apache.logging.log4j.Logger");
    }

    @Test
    public void testExecutionGuardIsNeverModifiedMarkerCheck() throws IOException {
        // A content-marker proxy for "unmodified": AgentExecutionGuard.java's own Step 5 design
        // commentary must still be present verbatim.
        Path file = Paths.get("src", "main", "java", "com", "framework", "ai", "agent", "AgentExecutionGuard.java");
        String content = Files.readString(file);
        assertThat(content)
                .contains("FAIL-CLOSED BY DESIGN")
                .contains("Step 5: the mandatory security/policy boundary");
    }

    @Test
    public void testNoSecondPolicyOrExecutorClassWasIntroducedAlongsideTheService() {
        // Phase 10 Step 4 legitimately introduced com.framework.ai.orchestration.AgentActionExecutor
        // as an explicit, non-executing execution-boundary CONTRACT, deliberately standalone from
        // (not wired into) this Step 3 orchestrator — see AgentActionExecutorTest/
        // AgentActionExecutorBoundaryTest for its own extensive safety coverage. That one class name
        // is no longer asserted absent here; every other check in this method still applies.
        assertThat(classExists("com.framework.ai.orchestration.AgentExecutionPolicy")).isFalse();
        assertThat(classExists("com.framework.ai.agent.AgentActionExecutor")).isFalse();
    }

    private Path serviceSourceFile() {
        return Paths.get("src", "main", "java", "com", "framework", "ai", "orchestration", SERVICE_FILE_NAME);
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
