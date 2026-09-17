package com.tests.ai.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import com.framework.ai.orchestration.AgentApprovalRecord;
import com.framework.ai.orchestration.AgentApprovalService;
import com.framework.ai.orchestration.AgentApprovalStatus;
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
 * Phase 9 Step 6: architectural boundary tests proving {@link AgentApprovalRecord} and
 * {@link AgentApprovalService} are exactly what they claim to be — a pure, immutable human-decision
 * record and a pure factory for it, with no path to Playwright, a direct AI provider, TestNG
 * lifecycle hooks, Git, MCP, shell execution, source modification, or dynamic/reflective dispatch,
 * and no way to bypass or reinterpret {@code AgentExecutionGuard}.
 */
public class AgentApprovalBoundaryTest {

    private static final List<Class<?>> APPROVAL_CLASSES =
            Arrays.asList(AgentApprovalRecord.class, AgentApprovalService.class, AgentApprovalStatus.class);

    private static final List<String> APPROVAL_FILE_NAMES =
            Arrays.asList("AgentApprovalRecord.java", "AgentApprovalService.java", "AgentApprovalStatus.java");

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
            "com.framework.ai.agent.AgentExecutionGuard",
            "com.framework.ai.agent.AgentExecutionGuardResult",
            "com.framework.ai.agent.AgentDecision",
            "com.framework.ai.agent.AgentAction",
            "com.framework.ai.agent.AgentState",
            "com.framework.ai.client.AiClient",
            "com.framework.ai.client.GeminiApiClient",
            "com.framework.ai.config.AiConfig",
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
            "import com.framework.ai.agent.AgentExecutionGuard;",
            "import com.framework.ai.agent.AgentExecutionGuardResult;",
            "import com.framework.ai.agent.AgentDecision;",
            "import com.framework.ai.agent.AgentAction;",
            "import com.framework.ai.agent.AgentState;",
            "import com.framework.ai.client.",
            "import com.framework.ai.config.AiConfig;",
            "import com.framework.listeners.",
            "import org.testng.",
            "import java.lang.ProcessBuilder",
            "import org.eclipse.jgit",
            "import java.io.FileWriter",
            "import java.io.FileOutputStream",
            "import java.nio.file.Files;",
            "import java.util.Date;");

    private static final List<String> FORBIDDEN_METHOD_NAMES = Arrays.asList(
            "click", "dblclick", "fill", "type", "press", "check", "uncheck", "selectOption",
            "setInputFiles", "navigate", "reload", "goBack", "goForward", "evaluate", "evaluateHandle",
            "addCookies", "clearCookies", "grantPermissions",
            "execute", "apply", "run", "runAction", "selfHeal", "heal", "retry");

    private static final List<String> EXPECTED_RECORD_PUBLIC_METHOD_NAMES = Arrays.asList(
            "getRecommendation", "getRecommendationId", "getStatus", "getReason", "getActor",
            "getTimestamp", "toString", "builder");

    private static final List<String> EXPECTED_SERVICE_PUBLIC_METHOD_NAMES = Arrays.asList(
            "pending", "approve", "reject");

    @Test
    public void testApprovalClassesDeclareNoForbiddenFieldOrMethodTypes() {
        for (Class<?> clazz : APPROVAL_CLASSES) {
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
    public void testApprovalClassesDeclareNoMutationOrExecutionMethod() {
        for (Class<?> clazz : APPROVAL_CLASSES) {
            for (Method method : clazz.getDeclaredMethods()) {
                assertThat(FORBIDDEN_METHOD_NAMES)
                        .describedAs("%s.%s must not be a mutation/execution method", clazz.getSimpleName(), method.getName())
                        .doesNotContain(method.getName());
            }
        }
    }

    @Test
    public void testApprovalRecordExposesOnlyItsIntendedPublicApi() {
        List<String> publicMethods = Arrays.stream(AgentApprovalRecord.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .map(Method::getName)
                .distinct()
                .collect(Collectors.toList());
        assertThat(publicMethods).containsExactlyInAnyOrderElementsOf(EXPECTED_RECORD_PUBLIC_METHOD_NAMES);
    }

    @Test
    public void testApprovalServiceExposesOnlyItsIntendedPublicApi() {
        List<String> publicMethodNames = Arrays.stream(AgentApprovalService.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .map(Method::getName)
                .distinct()
                .collect(Collectors.toList());
        assertThat(publicMethodNames).containsExactlyInAnyOrderElementsOf(EXPECTED_SERVICE_PUBLIC_METHOD_NAMES);
    }

    @Test
    public void testApprovalServiceHasNoGenericExecuteOrToolArgumentsApi() {
        for (Method method : AgentApprovalService.class.getDeclaredMethods()) {
            assertThat(method.getName()).isNotEqualTo("execute");
            for (Class<?> paramType : method.getParameterTypes()) {
                assertThat(paramType).isNotEqualTo(java.util.Map.class);
            }
        }
    }

    @Test
    public void testApprovalSourceFilesImportNoForbiddenDependency() throws IOException {
        Path orchestrationDir = Paths.get("src", "main", "java", "com", "framework", "ai", "orchestration");
        assertThat(Files.isDirectory(orchestrationDir)).isTrue();

        for (String fileName : APPROVAL_FILE_NAMES) {
            Path file = orchestrationDir.resolve(fileName);
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
    public void testApprovalSourceFilesContainNoShellGitReflectionOrFileWriteApi() throws IOException {
        Path orchestrationDir = Paths.get("src", "main", "java", "com", "framework", "ai", "orchestration");

        for (String fileName : APPROVAL_FILE_NAMES) {
            String content = Files.readString(orchestrationDir.resolve(fileName));
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
    }

    @Test
    public void testApprovalSourceFilesContainNoTestNgOrAiClientReference() throws IOException {
        Path orchestrationDir = Paths.get("src", "main", "java", "com", "framework", "ai", "orchestration");

        for (String fileName : APPROVAL_FILE_NAMES) {
            String content = Files.readString(orchestrationDir.resolve(fileName));
            assertThat(content)
                    .doesNotContain("org.testng")
                    .doesNotContain("ITestResult")
                    .doesNotContain("AiClient")
                    .doesNotContain("GeminiApiClient")
                    .doesNotContain("com.microsoft.playwright");
        }
    }

    @Test
    public void testApprovalClassesAreNotRegisteredAsTestNgListeners() {
        for (Class<?> clazz : APPROVAL_CLASSES) {
            List<Class<?>> interfaces = Arrays.asList(clazz.getInterfaces());
            assertThat(interfaces).noneMatch(i -> i.getName().startsWith("org.testng"));
        }
    }

    @Test
    public void testExistingLifecycleAndOrchestrationClassesDoNotReferenceApprovalClasses() throws IOException {
        List<Path> files = Arrays.asList(
                Paths.get("src", "main", "java", "com", "framework", "listeners", "TestListener.java"),
                Paths.get("src", "main", "java", "com", "framework", "listeners", "RetryAnalyzer.java"),
                Paths.get("src", "main", "java", "com", "framework", "listeners", "RetryTransformer.java"),
                Paths.get("src", "main", "java", "com", "framework", "driver", "PlaywrightManager.java"),
                Paths.get("src", "main", "java", "com", "framework", "ai", "orchestration", "AgentOrchestrationService.java"),
                Paths.get("src", "main", "java", "com", "framework", "ai", "orchestration", "AgentRecommendationConsumer.java"));

        for (Path file : files) {
            assertThat(Files.isRegularFile(file)).describedAs("Expected %s to exist", file).isTrue();
            String content = Files.readString(file);
            assertThat(content)
                    .describedAs("%s must not reference the new approval classes", file.getFileName())
                    .doesNotContain("AgentApprovalRecord")
                    .doesNotContain("AgentApprovalService")
                    .doesNotContain("AgentApprovalStatus");
        }
    }

    @Test
    public void testAgentExecutionGuardSourceRemainsUnmodifiedMarkerCheck() throws IOException {
        // A content-marker proxy for "unmodified": AgentExecutionGuard.java's own Step 5 design
        // commentary must still be present verbatim — a genuine edit to that file would almost
        // certainly touch or remove this block.
        Path file = Paths.get("src", "main", "java", "com", "framework", "ai", "agent", "AgentExecutionGuard.java");
        String content = Files.readString(file);
        assertThat(content)
                .contains("FAIL-CLOSED BY DESIGN")
                .contains("Step 5: the mandatory security/policy boundary");
    }

    @Test
    public void testAgentReasoningServiceSourceRemainsUnmodifiedMarkerCheck() throws IOException {
        Path file = Paths.get("src", "main", "java", "com", "framework", "ai", "agent", "AgentReasoningService.java");
        String content = Files.readString(file);
        assertThat(content)
                .contains("REASONING ONLY.")
                .contains("Step 3: controlled agent reasoning orchestrator");
    }

    @Test
    public void testAgentApprovalStatusHasExactlyTheThreeAllowedValues() {
        assertThat(AgentApprovalStatus.values())
                .extracting(Enum::name)
                .containsExactlyInAnyOrder("PENDING", "APPROVED", "REJECTED");
    }

    @Test
    public void testNoExecutionOrToolRegistryClassWasIntroducedAlongsideApproval() {
        // Phase 10 Step 4 legitimately introduced com.framework.ai.orchestration.AgentActionExecutor
        // as an explicit, non-executing execution-boundary CONTRACT (see AgentActionExecutorTest/
        // AgentActionExecutorBoundaryTest for its own extensive safety coverage) — that one class
        // name is no longer asserted absent here; every other check in this method still applies.
        assertThat(classExists("com.framework.ai.orchestration.AgentToolRegistry")).isFalse();
        assertThat(classExists("com.framework.ai.orchestration.AgentManager")).isFalse();
        assertThat(classExists("com.framework.ai.orchestration.AgentController")).isFalse();
        assertThat(classExists("com.framework.ai.orchestration.AgentRunner")).isFalse();
        assertThat(classExists("com.framework.ai.orchestration.AgentEngine")).isFalse();
        assertThat(classExists("com.framework.ai.orchestration.AgentWorkflowEngine")).isFalse();
        assertThat(classExists("com.framework.ai.orchestration.AgentSelfHealingEngine")).isFalse();
        assertThat(classExists("com.framework.ai.orchestration.AgentPolicyEngine")).isFalse();
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
