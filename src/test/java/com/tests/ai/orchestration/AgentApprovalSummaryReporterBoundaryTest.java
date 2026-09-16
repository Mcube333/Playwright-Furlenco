package com.tests.ai.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import com.framework.ai.orchestration.AgentApprovalRecordStore;
import com.framework.ai.orchestration.AgentApprovalSummaryReporter;
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
 * Phase 9 Step 8: architectural boundary tests proving {@link AgentApprovalSummaryReporter} is
 * exactly what it claims to be — a pure, read-only, in-memory presentation layer over an
 * {@link AgentApprovalRecordStore}, with no path to Playwright, a direct AI provider, TestNG
 * lifecycle hooks, Git, MCP, shell execution, persistence, or dynamic/reflective dispatch, and no
 * mutable approval-state storage of its own.
 */
public class AgentApprovalSummaryReporterBoundaryTest {

    private static final Class<?> REPORTER_CLASS = AgentApprovalSummaryReporter.class;

    private static final String REPORTER_FILE_NAME = "AgentApprovalSummaryReporter.java";

    private static final List<String> FORBIDDEN_TYPE_NAMES = Arrays.asList(
            "com.microsoft.playwright.Page",
            "com.microsoft.playwright.Locator",
            "com.microsoft.playwright.Browser",
            "com.microsoft.playwright.BrowserContext",
            "com.microsoft.playwright.Playwright",
            "com.framework.driver.PlaywrightManager",
            "com.framework.ai.agent.ReadOnlyBrowserTool",
            "com.framework.ai.agent.AgentReasoningService",
            "com.framework.ai.agent.AgentExecutionGuard",
            "com.framework.ai.agent.AgentDecision",
            "com.framework.ai.agent.AgentAction",
            "com.framework.ai.client.AiClient",
            "com.framework.ai.client.GeminiApiClient",
            "com.framework.ai.config.AiConfig",
            "com.framework.listeners.TestListener",
            "com.framework.listeners.RetryAnalyzer",
            "com.framework.listeners.RetryTransformer",
            "org.testng.ITestResult",
            "java.util.concurrent.ConcurrentHashMap",
            "java.util.concurrent.ScheduledExecutorService",
            "java.util.concurrent.ExecutorService",
            "java.util.Timer");

    private static final List<String> FORBIDDEN_IMPORTS = Arrays.asList(
            "import com.microsoft.playwright.",
            "import com.framework.driver.PlaywrightManager;",
            "import com.framework.ai.agent.ReadOnlyBrowserTool;",
            "import com.framework.ai.agent.AgentReasoningService;",
            "import com.framework.ai.agent.AgentExecutionGuard;",
            "import com.framework.ai.agent.AgentDecision;",
            "import com.framework.ai.agent.AgentAction;",
            "import com.framework.ai.client.",
            "import com.framework.ai.config.AiConfig;",
            "import com.framework.listeners.",
            "import org.testng.",
            "import java.lang.ProcessBuilder",
            "import org.eclipse.jgit",
            "import java.io.FileWriter",
            "import java.io.FileOutputStream",
            "import java.io.ObjectOutputStream",
            "import java.nio.file.Files;",
            "import java.sql.",
            "import java.net.http.",
            "import java.util.concurrent.",
            "import java.util.Timer;");

    private static final List<String> FORBIDDEN_METHOD_NAMES = Arrays.asList(
            "click", "dblclick", "fill", "type", "press", "check", "uncheck", "selectOption",
            "setInputFiles", "navigate", "reload", "goBack", "goForward", "evaluate", "evaluateHandle",
            "addCookies", "clearCookies", "grantPermissions",
            "execute", "apply", "run", "dispatch", "runAction", "selfHeal", "heal", "retry",
            "approve", "reject", "write", "delete", "persist", "save", "add", "clear");

    private static final List<String> EXPECTED_PUBLIC_METHOD_NAMES = Arrays.asList("summarize");

    @Test
    public void testReporterDeclaresOnlyTheStoreAsADependency() {
        Field[] fields = REPORTER_CLASS.getDeclaredFields();
        assertThat(fields).hasSize(1);
        assertThat(fields[0].getType()).isEqualTo(AgentApprovalRecordStore.class);
    }

    @Test
    public void testReporterDeclaresNoForbiddenFieldOrMethodTypes() {
        for (Field field : REPORTER_CLASS.getDeclaredFields()) {
            assertThat(FORBIDDEN_TYPE_NAMES)
                    .describedAs("AgentApprovalSummaryReporter.%s must not be a forbidden type", field.getName())
                    .doesNotContain(field.getType().getName());
        }
        for (Method method : REPORTER_CLASS.getDeclaredMethods()) {
            assertThat(FORBIDDEN_TYPE_NAMES).doesNotContain(method.getReturnType().getName());
            for (Class<?> paramType : method.getParameterTypes()) {
                assertThat(FORBIDDEN_TYPE_NAMES).doesNotContain(paramType.getName());
            }
        }
    }

    @Test
    public void testReporterDeclaresNoMutationExecutionOrStoreWriteMethod() {
        for (Method method : REPORTER_CLASS.getDeclaredMethods()) {
            assertThat(FORBIDDEN_METHOD_NAMES)
                    .describedAs("AgentApprovalSummaryReporter.%s must not be a mutation/execution/store-write method",
                            method.getName())
                    .doesNotContain(method.getName());
        }
    }

    @Test
    public void testReporterExposesOnlyItsIntendedPublicApi() {
        List<String> publicMethods = Arrays.stream(REPORTER_CLASS.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .map(Method::getName)
                .distinct()
                .collect(Collectors.toList());
        assertThat(publicMethods).containsExactlyInAnyOrderElementsOf(EXPECTED_PUBLIC_METHOD_NAMES);
    }

    @Test
    public void testReporterSourceFileImportsNoForbiddenDependency() throws IOException {
        Path file = reporterSourceFile();
        assertThat(Files.isRegularFile(file)).describedAs("Expected %s to exist", file).isTrue();

        List<String> importLines = Files.readAllLines(file).stream()
                .map(String::trim)
                .filter(line -> line.startsWith("import "))
                .toList();
        for (String importLine : importLines) {
            for (String forbidden : FORBIDDEN_IMPORTS) {
                assertThat(importLine)
                        .describedAs("AgentApprovalSummaryReporter.java must not import a forbidden dependency")
                        .doesNotStartWith(forbidden);
            }
        }
    }

    @Test
    public void testReporterSourceFileContainsNoShellGitReflectionOrPersistenceApi() throws IOException {
        String content = Files.readString(reporterSourceFile());
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
                .doesNotContain("ObjectOutputStream")
                .doesNotContain("Serializable")
                .doesNotContain("jdbc")
                .doesNotContainIgnoringCase("redis")
                .doesNotContainIgnoringCase("http")
                .doesNotContainIgnoringCase("mcp")
                .doesNotContain("Thread(")
                .doesNotContain("Executor")
                .doesNotContain("Timer")
                .doesNotContain("Scheduled");
    }

    @Test
    public void testReporterSourceFileContainsNoTestNgOrAiClientReference() throws IOException {
        String content = Files.readString(reporterSourceFile());
        assertThat(content)
                .doesNotContain("org.testng")
                .doesNotContain("ITestResult")
                .doesNotContain("AiClient")
                .doesNotContain("GeminiApiClient")
                .doesNotContain("com.microsoft.playwright");
    }

    @Test
    public void testReporterHasNoMutableApprovalStateStorageOfItsOwn() throws IOException {
        String content = Files.readString(reporterSourceFile());
        assertThat(content)
                .doesNotContain("ConcurrentHashMap")
                .doesNotContain("HashMap")
                .doesNotContain("ArrayList")
                .doesNotContain("List<AgentApprovalRecord>");
    }

    @Test
    public void testReporterClassIsNotRegisteredAsATestNgListener() {
        List<Class<?>> interfaces = Arrays.asList(REPORTER_CLASS.getInterfaces());
        assertThat(interfaces).noneMatch(i -> i.getName().startsWith("org.testng"));
    }

    @Test
    public void testReporterDoesNotModifyAgentApprovalRecordStoreSourceFile() throws IOException {
        // A content-marker proxy for "unmodified": the store's own Step 7 class-level Javadoc
        // must still be present verbatim.
        Path storeFile = Paths.get("src", "main", "java", "com", "framework", "ai", "orchestration",
                "AgentApprovalRecordStore.java");
        String content = Files.readString(storeFile);
        assertThat(content)
                .contains("Phase 9 Step 7")
                .contains("public void add(AgentApprovalRecord record)")
                .contains("public int size()")
                .contains("public void clear()");
    }

    @Test
    public void testExistingLifecycleAndOrchestrationClassesDoNotReferenceTheReporter() throws IOException {
        List<Path> files = Arrays.asList(
                Paths.get("src", "main", "java", "com", "framework", "listeners", "TestListener.java"),
                Paths.get("src", "main", "java", "com", "framework", "listeners", "RetryAnalyzer.java"),
                Paths.get("src", "main", "java", "com", "framework", "listeners", "RetryTransformer.java"),
                Paths.get("src", "main", "java", "com", "framework", "driver", "PlaywrightManager.java"),
                Paths.get("src", "main", "java", "com", "framework", "ai", "orchestration", "AgentOrchestrationService.java"),
                Paths.get("src", "main", "java", "com", "framework", "ai", "orchestration", "AgentRecommendationConsumer.java"),
                Paths.get("src", "main", "java", "com", "framework", "ai", "orchestration", "AgentApprovalService.java"),
                Paths.get("src", "main", "java", "com", "framework", "ai", "orchestration", "AgentApprovalRecordStore.java"));

        for (Path file : files) {
            assertThat(Files.isRegularFile(file)).describedAs("Expected %s to exist", file).isTrue();
            String content = Files.readString(file);
            assertThat(content)
                    .describedAs("%s must not reference AgentApprovalSummaryReporter", file.getFileName())
                    .doesNotContain("AgentApprovalSummaryReporter");
        }
    }

    @Test
    public void testReporterHasNoStaticInitializerSideEffects() {
        Field[] staticFields = Arrays.stream(REPORTER_CLASS.getDeclaredFields())
                .filter(f -> Modifier.isStatic(f.getModifiers()))
                .toArray(Field[]::new);
        assertThat(staticFields).isEmpty();
    }

    private Path reporterSourceFile() {
        return Paths.get("src", "main", "java", "com", "framework", "ai", "orchestration", REPORTER_FILE_NAME);
    }
}
