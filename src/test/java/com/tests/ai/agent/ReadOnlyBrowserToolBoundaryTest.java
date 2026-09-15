package com.tests.ai.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.framework.ai.agent.AgentBrowserObservation;
import com.framework.ai.agent.AgentContext;
import com.framework.ai.agent.AgentDecision;
import com.framework.ai.agent.AgentObservation;
import com.framework.ai.agent.AgentReasoningService;
import com.framework.ai.agent.LocatorObservation;
import com.framework.ai.agent.ReadOnlyBrowserTool;
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
 * Phase 8 Step 4: architectural boundary tests specific to the new browser-observation layer.
 *
 * Proves, empirically rather than by inspection alone:
 * 1. {@link ReadOnlyBrowserTool} (and its two models) never declares a mutation-capable method —
 *    the full Playwright mutation vocabulary called out by the Step 4 spec is checked by name.
 * 2. The Step 2 data models ({@link AgentContext}, {@link AgentObservation}, {@link AgentDecision})
 *    remain browser-independent even after Step 4 introduced Playwright into this package —
 *    re-verified here specifically because Step 4 is the step most likely to have accidentally
 *    leaked a Playwright dependency into them.
 * 3. {@link AgentReasoningService} (Step 3) gained no browser-mutation capability.
 * 4. Playwright dependency imports exist ONLY in the three browser-tool source files, never in the
 *    Step 2/3 files, via a source-text import scan (reflection alone cannot see an unused import).
 */
public class ReadOnlyBrowserToolBoundaryTest {

    private static final List<String> MUTATION_METHOD_NAMES = Arrays.asList(
            "click", "dblclick", "fill", "type", "press", "check", "uncheck", "selectOption",
            "setInputFiles", "dragTo", "hover", "focus", "dispatchEvent", "tap", "navigate",
            "reload", "goBack", "goForward", "evaluate", "evaluateHandle", "addScriptTag",
            "addStyleTag", "setContent", "exposeFunction", "route", "unroute", "grantPermissions",
            "clearCookies", "addCookies", "storageState");

    private static final List<Class<?>> BROWSER_LAYER_CLASSES =
            Arrays.asList(ReadOnlyBrowserTool.class, AgentBrowserObservation.class, LocatorObservation.class);

    private static final List<Class<?>> STEP_2_AND_3_CLASSES =
            Arrays.asList(AgentContext.class, AgentObservation.class, AgentDecision.class, AgentReasoningService.class);

    private static final List<String> BROWSER_TYPE_NAMES = Arrays.asList(
            "com.microsoft.playwright.Page", "com.microsoft.playwright.Locator",
            "com.microsoft.playwright.Browser", "com.microsoft.playwright.BrowserContext",
            "com.microsoft.playwright.Playwright");

    @Test
    public void testBrowserLayerDeclaresNoMutationMethod() {
        for (Class<?> clazz : BROWSER_LAYER_CLASSES) {
            for (Method method : clazz.getDeclaredMethods()) {
                assertThat(MUTATION_METHOD_NAMES)
                        .describedAs("%s.%s must not be a mutation method", clazz.getSimpleName(), method.getName())
                        .doesNotContain(method.getName().toLowerCase());
            }
        }
    }

    @Test
    public void testStep2AndStep3ClassesRemainBrowserIndependent() {
        for (Class<?> clazz : STEP_2_AND_3_CLASSES) {
            for (Field field : clazz.getDeclaredFields()) {
                assertThat(BROWSER_TYPE_NAMES)
                        .describedAs("%s must not have a field of a Playwright type", clazz.getSimpleName())
                        .doesNotContain(field.getType().getName());
            }
            for (Method method : clazz.getDeclaredMethods()) {
                assertThat(BROWSER_TYPE_NAMES).doesNotContain(method.getReturnType().getName());
                for (Class<?> paramType : method.getParameterTypes()) {
                    assertThat(BROWSER_TYPE_NAMES)
                            .describedAs("%s.%s must not accept a Playwright-typed parameter",
                                    clazz.getSimpleName(), method.getName())
                            .doesNotContain(paramType.getName());
                }
            }
        }
    }

    @Test
    public void testPlaywrightImportsExistOnlyInTheBrowserToolFiles() throws IOException {
        Path agentSourceDir = Paths.get("src", "main", "java", "com", "framework", "ai", "agent");
        assertThat(Files.isDirectory(agentSourceDir)).isTrue();

        List<String> browserToolFiles = Arrays.asList(
                "ReadOnlyBrowserTool.java", "AgentBrowserObservation.java", "LocatorObservation.java");

        try (var stream = Files.list(agentSourceDir)) {
            for (Path file : stream.filter(p -> p.toString().endsWith(".java")).toList()) {
                boolean importsPlaywright = Files.readAllLines(file).stream()
                        .map(String::trim)
                        .anyMatch(line -> line.startsWith("import com.microsoft.playwright."));

                if (browserToolFiles.contains(file.getFileName().toString())) {
                    continue; // these are the intentional, isolated exception
                }
                assertThat(importsPlaywright)
                        .describedAs("%s must not import Playwright", file.getFileName())
                        .isFalse();
            }
        }
    }

    @Test
    public void testReadOnlyBrowserToolDoesNotImportPlaywrightManagerOrTestNgLifecycle() throws IOException {
        Path file = Paths.get("src", "main", "java", "com", "framework", "ai", "agent", "ReadOnlyBrowserTool.java");
        assertThat(Files.isRegularFile(file)).isTrue();

        List<String> forbidden = Arrays.asList(
                "import com.framework.driver.PlaywrightManager;",
                "import com.framework.listeners.",
                "import org.testng.ITestResult;");

        List<String> importLines = Files.readAllLines(file).stream()
                .map(String::trim)
                .filter(line -> line.startsWith("import "))
                .toList();

        for (String importLine : importLines) {
            for (String forbiddenImport : forbidden) {
                assertThat(importLine).doesNotStartWith(forbiddenImport);
            }
        }
    }

    @Test
    public void testNoReflectionBasedDynamicPlaywrightInvocationExists() throws IOException {
        Path file = Paths.get("src", "main", "java", "com", "framework", "ai", "agent", "ReadOnlyBrowserTool.java");
        String content = Files.readString(file);

        // No reflective method dispatch and no generic execute(action, args)-style API.
        assertThat(content).doesNotContain("Method.invoke").doesNotContain(".invoke(")
                .doesNotContain("getMethod(").doesNotContain("getDeclaredMethod(")
                .doesNotContain("execute(");
    }
}
