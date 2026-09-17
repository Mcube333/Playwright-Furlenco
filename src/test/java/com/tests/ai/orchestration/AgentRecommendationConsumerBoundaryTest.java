package com.tests.ai.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import com.framework.ai.orchestration.AgentRecommendationConsumer;
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
 * Phase 9 Step 4: architectural boundary tests proving {@link AgentRecommendationConsumer} is
 * exactly what it claims to be — a pure delegation wrapper with a single public method, no path to
 * Playwright, a direct AI provider, TestNG lifecycle hooks, Git, MCP, shell execution, source
 * modification, or dynamic/reflective dispatch, and no "approve"/"execute"/"heal" capability.
 */
public class AgentRecommendationConsumerBoundaryTest {

    private static final Class<?> CONSUMER_CLASS = AgentRecommendationConsumer.class;

    private static final String CONSUMER_FILE_NAME = "AgentRecommendationConsumer.java";

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
            "execute", "apply", "run", "runAction", "selfHeal", "heal", "approve", "accept",
            "write", "delete", "patch");

    /** Only the intended, single delegation entry point (plus Object's own methods) should be public. */
    private static final List<String> EXPECTED_PUBLIC_METHOD_NAMES = Arrays.asList("consume");

    @Test
    public void testConsumerClassDeclaresNoForbiddenFieldOrMethodTypes() {
        for (Field field : CONSUMER_CLASS.getDeclaredFields()) {
            assertThat(FORBIDDEN_TYPE_NAMES)
                    .describedAs("AgentRecommendationConsumer.%s must not be a forbidden type", field.getName())
                    .doesNotContain(field.getType().getName());
        }
        for (Method method : CONSUMER_CLASS.getDeclaredMethods()) {
            assertThat(FORBIDDEN_TYPE_NAMES).doesNotContain(method.getReturnType().getName());
            for (Class<?> paramType : method.getParameterTypes()) {
                assertThat(FORBIDDEN_TYPE_NAMES).doesNotContain(paramType.getName());
            }
        }
    }

    @Test
    public void testConsumerClassDeclaresNoMutationOrExecutionMethod() {
        for (Method method : CONSUMER_CLASS.getDeclaredMethods()) {
            assertThat(FORBIDDEN_METHOD_NAMES)
                    .describedAs("AgentRecommendationConsumer.%s must not be a mutation/execution/approval method", method.getName())
                    .doesNotContain(method.getName());
        }
    }

    @Test
    public void testConsumerClassExposesOnlyTheIntendedPublicApi() {
        List<String> publicDeclaredMethodNames = Arrays.stream(CONSUMER_CLASS.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .map(Method::getName)
                .distinct()
                .collect(Collectors.toList());

        assertThat(publicDeclaredMethodNames)
                .describedAs("AgentRecommendationConsumer must expose exactly one public entry point")
                .containsExactlyInAnyOrderElementsOf(EXPECTED_PUBLIC_METHOD_NAMES);
    }

    @Test
    public void testConsumerSourceFileImportsNoForbiddenDependency() throws IOException {
        Path file = consumerSourceFile();
        assertThat(Files.isRegularFile(file)).describedAs("Expected %s to exist", file).isTrue();

        List<String> importLines = Files.readAllLines(file).stream()
                .map(String::trim)
                .filter(line -> line.startsWith("import "))
                .toList();
        for (String importLine : importLines) {
            for (String forbidden : FORBIDDEN_IMPORTS) {
                assertThat(importLine)
                        .describedAs("AgentRecommendationConsumer.java must not import a forbidden dependency")
                        .doesNotStartWith(forbidden);
            }
        }
    }

    @Test
    public void testConsumerSourceFileContainsNoShellGitReflectionOrFileWriteApi() throws IOException {
        String content = Files.readString(consumerSourceFile());
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
                .doesNotContainIgnoringCase("mcp");
    }

    @Test
    public void testConsumerSourceFileContainsNoTestNgOrAiClientReference() throws IOException {
        String content = Files.readString(consumerSourceFile());
        assertThat(content)
                .doesNotContain("org.testng")
                .doesNotContain("ITestResult")
                .doesNotContain("AiClient")
                .doesNotContain("GeminiApiClient")
                .doesNotContain("com.microsoft.playwright");
    }

    @Test
    public void testConsumerClassIsNotRegisteredAsATestNgListenerOrAnnotationTransformer() {
        List<Class<?>> interfaces = Arrays.asList(CONSUMER_CLASS.getInterfaces());
        assertThat(interfaces).noneMatch(i -> i.getName().startsWith("org.testng"));
    }

    @Test
    public void testConsumerClassHasNoStaticInitializerSideEffectsBeyondItsLogger() {
        // A static initializer beyond the standard `private static final Logger LOGGER = ...`
        // pattern would be a red flag for a hidden auto-invocation path; declared field count and
        // types are asserted precisely to catch that.
        Field[] staticFields = Arrays.stream(CONSUMER_CLASS.getDeclaredFields())
                .filter(f -> Modifier.isStatic(f.getModifiers()))
                .toArray(Field[]::new);
        assertThat(staticFields).hasSize(1);
        assertThat(staticFields[0].getType().getName()).isEqualTo("org.apache.logging.log4j.Logger");
    }

    @Test
    public void testExistingLifecycleClassesDoNotReferenceTheNewConsumerClass() throws IOException {
        List<Path> lifecycleFiles = Arrays.asList(
                Paths.get("src", "main", "java", "com", "framework", "listeners", "TestListener.java"),
                Paths.get("src", "main", "java", "com", "framework", "listeners", "RetryAnalyzer.java"),
                Paths.get("src", "main", "java", "com", "framework", "listeners", "RetryTransformer.java"),
                Paths.get("src", "main", "java", "com", "framework", "driver", "PlaywrightManager.java"));

        for (Path file : lifecycleFiles) {
            assertThat(Files.isRegularFile(file)).describedAs("Expected %s to exist", file).isTrue();
            String content = Files.readString(file);
            assertThat(content)
                    .describedAs("%s must not reference AgentRecommendationConsumer", file.getFileName())
                    .doesNotContain("AgentRecommendationConsumer");
        }
    }

    @Test
    public void testNoApprovalOrExecutionClassWasIntroducedAlongsideTheConsumer() {
        assertThat(classExists("com.framework.ai.orchestration.AgentApproval")).isFalse();
        assertThat(classExists("com.framework.ai.orchestration.ApprovalGrant")).isFalse();
        // Phase 10 Step 4 legitimately introduced com.framework.ai.orchestration.AgentActionExecutor
        // as an explicit, non-executing execution-boundary CONTRACT (see AgentActionExecutorTest/
        // AgentActionExecutorBoundaryTest for its own extensive safety coverage) — narrowed to the
        // one path that was never authorized: a real executor living in the Phase 8 agent package.
        assertThat(classExists("com.framework.ai.agent.AgentActionExecutor")).isFalse();
    }

    private Path consumerSourceFile() {
        return Paths.get("src", "main", "java", "com", "framework", "ai", "orchestration", CONSUMER_FILE_NAME);
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
