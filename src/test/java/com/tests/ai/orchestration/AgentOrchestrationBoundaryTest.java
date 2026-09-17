package com.tests.ai.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import com.framework.ai.orchestration.AgentOrchestrationService;
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
 * Phase 9 Step 2: architectural boundary tests proving {@link AgentOrchestrationService} is
 * exactly what it claims to be — a pure orchestration adapter with no path to Playwright, a
 * direct AI provider, TestNG lifecycle hooks, Git, MCP, shell execution, source modification, or
 * dynamic/reflective dispatch.
 *
 * Intentionally does NOT forbid importing {@code AgentReasoningService}, {@code AgentExecutionGuard},
 * {@code SelfHealingRecommendationService}, or (as of Phase 9 Step 3) {@code SelfHealingRecommendationReporter}
 * — those four are this class's entire, intended purpose (composing/reporting them), unlike
 * {@code AgentExecutionGuardBoundaryTest}'s stricter rule for the guard itself, which has no
 * reasoning dependency by design.
 */
public class AgentOrchestrationBoundaryTest {

    private static final Class<?> ORCHESTRATION_CLASS = AgentOrchestrationService.class;

    private static final String ORCHESTRATION_FILE_NAME = "AgentOrchestrationService.java";

    private static final List<String> FORBIDDEN_TYPE_NAMES = Arrays.asList(
            "com.microsoft.playwright.Page",
            "com.microsoft.playwright.Locator",
            "com.microsoft.playwright.Browser",
            "com.microsoft.playwright.BrowserContext",
            "com.microsoft.playwright.Playwright",
            "com.framework.driver.PlaywrightManager",
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
            "import com.framework.ai.client.",
            "import com.framework.listeners.",
            "import org.testng.",
            "import java.lang.ProcessBuilder",
            "import org.eclipse.jgit",
            "import java.io.FileWriter",
            "import java.io.FileOutputStream",
            "import java.nio.file.Files;");

    private static final List<String> FORBIDDEN_METHOD_NAMES = Arrays.asList(
            "click", "dblclick", "fill", "type", "press", "check", "uncheck", "selectOption",
            "setInputFiles", "navigate", "reload", "goBack", "goForward", "evaluate", "evaluateHandle",
            "addCookies", "clearCookies", "grantPermissions",
            "execute", "apply", "run", "runAction", "selfHeal", "approve", "write", "delete", "patch");

    /**
     * Only the intended orchestration entry points (plus Object's own methods) should be public.
     * Phase 9 Step 3 legitimately adds {@code recommendAndReport} as a second, explicit,
     * separately-named entry point alongside Step 2's {@code recommend} — this list is updated
     * accordingly rather than left stale, per Step 3's own instruction to make the smallest
     * targeted change when a new method is legitimately introduced.
     */
    private static final List<String> EXPECTED_PUBLIC_METHOD_NAMES = Arrays.asList("recommend", "recommendAndReport");

    @Test
    public void testOrchestrationClassDeclaresNoForbiddenFieldOrMethodTypes() {
        for (Field field : ORCHESTRATION_CLASS.getDeclaredFields()) {
            assertThat(FORBIDDEN_TYPE_NAMES)
                    .describedAs("AgentOrchestrationService.%s must not be a forbidden type", field.getName())
                    .doesNotContain(field.getType().getName());
        }
        for (Method method : ORCHESTRATION_CLASS.getDeclaredMethods()) {
            assertThat(FORBIDDEN_TYPE_NAMES).doesNotContain(method.getReturnType().getName());
            for (Class<?> paramType : method.getParameterTypes()) {
                assertThat(FORBIDDEN_TYPE_NAMES).doesNotContain(paramType.getName());
            }
        }
    }

    @Test
    public void testOrchestrationClassDeclaresNoMutationOrExecutionMethod() {
        for (Method method : ORCHESTRATION_CLASS.getDeclaredMethods()) {
            assertThat(FORBIDDEN_METHOD_NAMES)
                    .describedAs("AgentOrchestrationService.%s must not be a mutation/execution method", method.getName())
                    .doesNotContain(method.getName());
        }
    }

    @Test
    public void testOrchestrationClassExposesOnlyTheIntendedPublicApi() {
        List<String> publicDeclaredMethodNames = Arrays.stream(ORCHESTRATION_CLASS.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .map(Method::getName)
                .distinct()
                .collect(Collectors.toList());

        assertThat(publicDeclaredMethodNames)
                .describedAs("AgentOrchestrationService must expose exactly one public orchestration entry point")
                .containsExactlyInAnyOrderElementsOf(EXPECTED_PUBLIC_METHOD_NAMES);
    }

    @Test
    public void testOrchestrationSourceFileImportsNoForbiddenDependency() throws IOException {
        Path file = orchestrationSourceFile();
        assertThat(Files.isRegularFile(file)).describedAs("Expected %s to exist", file).isTrue();

        List<String> importLines = Files.readAllLines(file).stream()
                .map(String::trim)
                .filter(line -> line.startsWith("import "))
                .toList();
        for (String importLine : importLines) {
            for (String forbidden : FORBIDDEN_IMPORTS) {
                assertThat(importLine)
                        .describedAs("AgentOrchestrationService.java must not import a forbidden dependency")
                        .doesNotStartWith(forbidden);
            }
        }
    }

    @Test
    public void testOrchestrationSourceFileContainsNoShellGitReflectionOrFileWriteApi() throws IOException {
        String content = Files.readString(orchestrationSourceFile());
        assertThat(content)
                .doesNotContain("ProcessBuilder")
                .doesNotContain("Runtime.getRuntime().exec")
                .doesNotContain("git ")
                .doesNotContain("Method.invoke")
                .doesNotContain(".invoke(")
                .doesNotContain("getDeclaredMethod(")
                .doesNotContain("getMethod(")
                .doesNotContain("Files.write")
                .doesNotContain("Files.delete")
                .doesNotContain("FileWriter")
                .doesNotContain("FileOutputStream")
                .doesNotContainIgnoringCase("mcp")
                .doesNotContain("isAllowed()");
    }

    @Test
    public void testOrchestrationSourceFileContainsNoTestNgOrAiClientReference() throws IOException {
        String content = Files.readString(orchestrationSourceFile());
        assertThat(content)
                .doesNotContain("org.testng")
                .doesNotContain("ITestResult")
                .doesNotContain("AiClient")
                .doesNotContain("GeminiApiClient")
                .doesNotContain("com.microsoft.playwright");
    }

    @Test
    public void testOrchestrationClassIsNotRegisteredAsATestNgListenerOrAnnotationTransformer() {
        List<Class<?>> interfaces = Arrays.asList(ORCHESTRATION_CLASS.getInterfaces());
        assertThat(interfaces).noneMatch(i -> i.getName().startsWith("org.testng"));
    }

    @Test
    public void testExistingLifecycleClassesDoNotReferenceTheNewOrchestrationClass() throws IOException {
        List<Path> lifecycleFiles = Arrays.asList(
                Paths.get("src", "main", "java", "com", "framework", "listeners", "TestListener.java"),
                Paths.get("src", "main", "java", "com", "framework", "listeners", "RetryAnalyzer.java"),
                Paths.get("src", "main", "java", "com", "framework", "listeners", "RetryTransformer.java"),
                Paths.get("src", "main", "java", "com", "framework", "driver", "PlaywrightManager.java"));

        for (Path file : lifecycleFiles) {
            assertThat(Files.isRegularFile(file)).describedAs("Expected %s to exist", file).isTrue();
            String content = Files.readString(file);
            assertThat(content)
                    .describedAs("%s must not reference AgentOrchestrationService", file.getFileName())
                    .doesNotContain("AgentOrchestrationService")
                    .doesNotContain("com.framework.ai.orchestration");
        }
    }

    @Test
    public void testAgentPolicyOrApprovalMechanismWasNotIntroduced() {
        assertThat(classExists("com.framework.ai.agent.AgentPolicy")).isFalse();
        assertThat(classExists("com.framework.ai.orchestration.AgentApproval")).isFalse();
        assertThat(classExists("com.framework.ai.orchestration.ApprovalGrant")).isFalse();
    }

    @Test
    public void testNoActionExecutorClassWasIntroducedAlongsideTheOrchestrator() {
        // Phase 10 Step 4 legitimately introduced com.framework.ai.orchestration.AgentActionExecutor
        // as an explicit, non-executing execution-boundary CONTRACT (see AgentActionExecutorTest/
        // AgentActionExecutorBoundaryTest for its own extensive safety coverage) — this assertion
        // is narrowed to the one path that was never authorized: a real executor living in the
        // Phase 8 agent package itself.
        assertThat(classExists("com.framework.ai.agent.AgentActionExecutor")).isFalse();
    }

    private Path orchestrationSourceFile() {
        return Paths.get("src", "main", "java", "com", "framework", "ai", "orchestration", ORCHESTRATION_FILE_NAME);
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
