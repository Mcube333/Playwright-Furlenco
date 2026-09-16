package com.tests.ai.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import com.framework.ai.orchestration.AgentApprovalRecordStore;
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
 * Phase 9 Step 7: architectural boundary tests proving {@link AgentApprovalRecordStore} is exactly
 * what it claims to be — a pure, in-memory data/query structure with no path to Playwright, a
 * direct AI provider, TestNG lifecycle hooks, Git, MCP, shell execution, persistence, or
 * dynamic/reflective dispatch, and no ability to create or reinterpret an approval decision.
 */
public class AgentApprovalRecordStoreBoundaryTest {

    private static final Class<?> STORE_CLASS = AgentApprovalRecordStore.class;

    private static final String STORE_FILE_NAME = "AgentApprovalRecordStore.java";

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
            "org.testng.ITestResult");

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
            "import java.net.http.");

    private static final List<String> FORBIDDEN_METHOD_NAMES = Arrays.asList(
            "click", "dblclick", "fill", "type", "press", "check", "uncheck", "selectOption",
            "setInputFiles", "navigate", "reload", "goBack", "goForward", "evaluate", "evaluateHandle",
            "addCookies", "clearCookies", "grantPermissions",
            "execute", "apply", "run", "dispatch", "runAction", "selfHeal", "heal", "retry",
            "approve", "reject", "write", "delete", "persist", "save");

    private static final List<String> EXPECTED_PUBLIC_METHOD_NAMES = Arrays.asList(
            "add", "findByRecommendationId", "findByRecommendation", "findByStatus", "size", "clear");

    @Test
    public void testStoreDeclaresNoForbiddenFieldOrMethodTypes() {
        for (Field field : STORE_CLASS.getDeclaredFields()) {
            assertThat(FORBIDDEN_TYPE_NAMES)
                    .describedAs("AgentApprovalRecordStore.%s must not be a forbidden type", field.getName())
                    .doesNotContain(field.getType().getName());
        }
        for (Method method : STORE_CLASS.getDeclaredMethods()) {
            assertThat(FORBIDDEN_TYPE_NAMES).doesNotContain(method.getReturnType().getName());
            for (Class<?> paramType : method.getParameterTypes()) {
                assertThat(FORBIDDEN_TYPE_NAMES).doesNotContain(paramType.getName());
            }
        }
    }

    @Test
    public void testStoreDeclaresNoMutationExecutionOrApprovalDecisionMethod() {
        for (Method method : STORE_CLASS.getDeclaredMethods()) {
            assertThat(FORBIDDEN_METHOD_NAMES)
                    .describedAs("AgentApprovalRecordStore.%s must not be a mutation/execution/approval-deciding method",
                            method.getName())
                    .doesNotContain(method.getName());
        }
    }

    @Test
    public void testStoreExposesOnlyItsIntendedPublicApi() {
        List<String> publicMethods = Arrays.stream(STORE_CLASS.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .map(Method::getName)
                .distinct()
                .collect(Collectors.toList());
        assertThat(publicMethods).containsExactlyInAnyOrderElementsOf(EXPECTED_PUBLIC_METHOD_NAMES);
    }

    @Test
    public void testStoreHasNoGenericExecuteOrToolArgumentsApi() {
        for (Method method : STORE_CLASS.getDeclaredMethods()) {
            for (Class<?> paramType : method.getParameterTypes()) {
                assertThat(paramType).isNotEqualTo(java.util.Map.class);
            }
        }
    }

    @Test
    public void testStoreSourceFileImportsNoForbiddenDependency() throws IOException {
        Path file = storeSourceFile();
        assertThat(Files.isRegularFile(file)).describedAs("Expected %s to exist", file).isTrue();

        List<String> importLines = Files.readAllLines(file).stream()
                .map(String::trim)
                .filter(line -> line.startsWith("import "))
                .toList();
        for (String importLine : importLines) {
            for (String forbidden : FORBIDDEN_IMPORTS) {
                assertThat(importLine)
                        .describedAs("AgentApprovalRecordStore.java must not import a forbidden dependency")
                        .doesNotStartWith(forbidden);
            }
        }
    }

    @Test
    public void testStoreSourceFileContainsNoShellGitReflectionOrPersistenceApi() throws IOException {
        String content = Files.readString(storeSourceFile());
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
                .doesNotContainIgnoringCase("mcp");
    }

    @Test
    public void testStoreSourceFileContainsNoTestNgOrAiClientReference() throws IOException {
        String content = Files.readString(storeSourceFile());
        assertThat(content)
                .doesNotContain("org.testng")
                .doesNotContain("ITestResult")
                .doesNotContain("AiClient")
                .doesNotContain("GeminiApiClient")
                .doesNotContain("com.microsoft.playwright");
    }

    @Test
    public void testStoreClassIsNotRegisteredAsATestNgListener() {
        List<Class<?>> interfaces = Arrays.asList(STORE_CLASS.getInterfaces());
        assertThat(interfaces).noneMatch(i -> i.getName().startsWith("org.testng"));
    }

    @Test
    public void testExistingLifecycleAndOrchestrationClassesDoNotReferenceTheStore() throws IOException {
        List<Path> files = Arrays.asList(
                Paths.get("src", "main", "java", "com", "framework", "listeners", "TestListener.java"),
                Paths.get("src", "main", "java", "com", "framework", "listeners", "RetryAnalyzer.java"),
                Paths.get("src", "main", "java", "com", "framework", "listeners", "RetryTransformer.java"),
                Paths.get("src", "main", "java", "com", "framework", "driver", "PlaywrightManager.java"),
                Paths.get("src", "main", "java", "com", "framework", "ai", "orchestration", "AgentOrchestrationService.java"),
                Paths.get("src", "main", "java", "com", "framework", "ai", "orchestration", "AgentRecommendationConsumer.java"),
                Paths.get("src", "main", "java", "com", "framework", "ai", "orchestration", "AgentApprovalService.java"));

        for (Path file : files) {
            assertThat(Files.isRegularFile(file)).describedAs("Expected %s to exist", file).isTrue();
            String content = Files.readString(file);
            assertThat(content)
                    .describedAs("%s must not reference AgentApprovalRecordStore", file.getFileName())
                    .doesNotContain("AgentApprovalRecordStore");
        }
    }

    @Test
    public void testStoreHasNoStaticInitializerSideEffectsBeyondItsMapField() {
        Field[] staticFields = Arrays.stream(STORE_CLASS.getDeclaredFields())
                .filter(f -> Modifier.isStatic(f.getModifiers()))
                .toArray(Field[]::new);
        assertThat(staticFields).isEmpty();
    }

    @Test
    public void testNoUnexpectedExecutionOrToolRegistryClassWasIntroducedAlongsideTheStore() {
        assertThat(classExists("com.framework.ai.orchestration.AgentActionExecutor")).isFalse();
        assertThat(classExists("com.framework.ai.orchestration.AgentToolRegistry")).isFalse();
        assertThat(classExists("com.framework.ai.orchestration.AgentApprovalRecordDatabase")).isFalse();
        assertThat(classExists("com.framework.ai.orchestration.AgentApprovalRecordRepository")).isFalse();
    }

    private Path storeSourceFile() {
        return Paths.get("src", "main", "java", "com", "framework", "ai", "orchestration", STORE_FILE_NAME);
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
