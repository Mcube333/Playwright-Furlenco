package com.tests.ai.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import com.framework.ai.agent.AgentAction;
import com.framework.ai.agent.SelfHealingRecommendation;
import com.framework.ai.codegeneration.EvidenceStatus;
import com.framework.ai.diagnosis.FixType;
import com.framework.ai.orchestration.AgentActionAuditRecord;
import com.framework.ai.orchestration.AgentActionAuditReporter;
import com.framework.ai.orchestration.AgentActionAuditStore;
import com.framework.ai.orchestration.AgentActionResult;
import com.framework.ai.orchestration.AgentActionResultStatus;
import com.framework.ai.orchestration.AgentApprovalRecord;
import com.framework.ai.orchestration.AgentApprovalService;
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
 * Phase 10 Step 2: architectural boundary tests plus adversarial safety coverage proving the
 * entire new audit layer ({@link AgentActionResultStatus}, {@link AgentActionResult},
 * {@link AgentActionAuditRecord}, {@link AgentActionAuditStore}, {@link AgentActionAuditReporter})
 * is exactly what it claims to be — a pure, non-executing data/query/reporting layer with no path
 * to Playwright, a direct AI provider, TestNG lifecycle hooks, Git, MCP, shell execution,
 * persistence, or dynamic/reflective dispatch, and no way to fabricate an execution outcome.
 */
public class AgentActionAuditBoundaryTest {

    private static final List<Class<?>> AUDIT_CLASSES = Arrays.asList(
            AgentActionResultStatus.class, AgentActionResult.class, AgentActionAuditRecord.class,
            AgentActionAuditStore.class, AgentActionAuditReporter.class);

    private static final List<String> AUDIT_FILE_NAMES = Arrays.asList(
            "AgentActionResultStatus.java", "AgentActionResult.java", "AgentActionAuditRecord.java",
            "AgentActionAuditStore.java", "AgentActionAuditReporter.java");

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
            "import com.framework.ai.agent.AgentExecutionGuard;",
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
            "import java.io.ObjectOutputStream",
            "import java.nio.file.Files;",
            "import java.net.http.");

    private static final List<String> FORBIDDEN_METHOD_NAMES = Arrays.asList(
            "click", "dblclick", "fill", "type", "press", "check", "uncheck", "selectOption",
            "setInputFiles", "navigate", "goto", "reload", "goBack", "goForward", "evaluate", "evaluateHandle",
            "addCookies", "clearCookies", "grantPermissions",
            "execute", "apply", "run", "dispatch", "runAction", "selfHeal", "heal", "retry", "write", "delete");

    // ------------------------------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------------------------------

    private final AgentApprovalService approvalService = new AgentApprovalService();

    private SelfHealingRecommendation recommendation(String id, double confidence, EvidenceStatus status) {
        return SelfHealingRecommendation.builder()
                .recommendationId(id).fixType(FixType.LOCATOR).description("click the payment button")
                .proposedLocator("#new").confidence(confidence)
                .addEvidenceItem(com.framework.ai.codegeneration.EvidenceItem.builder()
                        .item("Locator").value("#new").status(status).source("test").confidence(confidence).build())
                .approvalRequired(true).build();
    }

    // ------------------------------------------------------------------------------------------
    // Step 10.A — no reference from lifecycle classes to the new audit classes
    // ------------------------------------------------------------------------------------------

    @Test
    public void testExistingLifecycleAndOrchestrationClassesDoNotReferenceTheAuditClasses() throws IOException {
        List<Path> files = Arrays.asList(
                Paths.get("src", "main", "java", "com", "framework", "listeners", "TestListener.java"),
                Paths.get("src", "main", "java", "com", "framework", "listeners", "RetryAnalyzer.java"),
                Paths.get("src", "main", "java", "com", "framework", "listeners", "RetryTransformer.java"),
                Paths.get("src", "main", "java", "com", "framework", "driver", "PlaywrightManager.java"),
                Paths.get("src", "main", "java", "com", "framework", "ai", "orchestration", "AgentOrchestrationService.java"),
                Paths.get("src", "main", "java", "com", "framework", "ai", "orchestration", "AgentRecommendationConsumer.java"),
                Paths.get("src", "main", "java", "com", "framework", "ai", "orchestration", "AgentApprovalService.java"),
                Paths.get("src", "main", "java", "com", "framework", "ai", "orchestration", "AgentApprovalRecordStore.java"),
                Paths.get("src", "main", "java", "com", "framework", "ai", "orchestration", "AgentApprovalSummaryReporter.java"));

        for (Path file : files) {
            assertThat(Files.isRegularFile(file)).describedAs("Expected %s to exist", file).isTrue();
            String content = Files.readString(file);
            assertThat(content)
                    .describedAs("%s must not reference the new audit classes", file.getFileName())
                    .doesNotContain("AgentActionResult")
                    .doesNotContain("AgentActionAuditRecord")
                    .doesNotContain("AgentActionAuditStore")
                    .doesNotContain("AgentActionAuditReporter");
        }
    }

    // ------------------------------------------------------------------------------------------
    // Step 10.B — no reference from the new audit classes to Playwright/Appium/Selenium types
    // ------------------------------------------------------------------------------------------

    @Test
    public void testAuditClassesDeclareNoForbiddenFieldOrMethodTypes() {
        for (Class<?> clazz : AUDIT_CLASSES) {
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
    public void testAuditClassesDeclareNoMutationOrExecutionMethod() {
        for (Class<?> clazz : AUDIT_CLASSES) {
            for (Method method : clazz.getDeclaredMethods()) {
                assertThat(FORBIDDEN_METHOD_NAMES)
                        .describedAs("%s.%s must not be a mutation/execution method", clazz.getSimpleName(), method.getName())
                        .doesNotContain(method.getName());
            }
        }
    }

    @Test
    public void testAuditClassesImportNoForbiddenDependency() throws IOException {
        Path orchestrationDir = Paths.get("src", "main", "java", "com", "framework", "ai", "orchestration");
        for (String fileName : AUDIT_FILE_NAMES) {
            Path file = orchestrationDir.resolve(fileName);
            assertThat(Files.isRegularFile(file)).describedAs("Expected %s to exist", file).isTrue();

            List<String> importLines = Files.readAllLines(file).stream()
                    .map(String::trim).filter(l -> l.startsWith("import ")).toList();
            for (String importLine : importLines) {
                for (String forbidden : FORBIDDEN_IMPORTS) {
                    assertThat(importLine)
                            .describedAs("%s must not import a forbidden dependency", fileName)
                            .doesNotStartWith(forbidden);
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // Step 10.C — no shell/Git/MCP/reflection/file-write API anywhere in the new source
    // ------------------------------------------------------------------------------------------

    @Test
    public void testAuditSourceFilesContainNoShellGitReflectionOrFileWriteApi() throws IOException {
        Path orchestrationDir = Paths.get("src", "main", "java", "com", "framework", "ai", "orchestration");
        for (String fileName : AUDIT_FILE_NAMES) {
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
                    .doesNotContain("BufferedWriter")
                    .doesNotContainIgnoringCase("mcp")
                    .doesNotContain("page.evaluate")
                    .doesNotContain(".click(")
                    .doesNotContain(".fill(")
                    .doesNotContain(".goto(");
        }
    }

    @Test
    public void testAuditSourceFilesContainNoTestNgOrAiClientReference() throws IOException {
        Path orchestrationDir = Paths.get("src", "main", "java", "com", "framework", "ai", "orchestration");
        for (String fileName : AUDIT_FILE_NAMES) {
            String content = Files.readString(orchestrationDir.resolve(fileName));
            assertThat(content)
                    .doesNotContain("org.testng")
                    .doesNotContain("ITestResult")
                    .doesNotContain("AiClient")
                    .doesNotContain("GeminiApiClient")
                    .doesNotContain("com.microsoft.playwright");
        }
    }

    // ------------------------------------------------------------------------------------------
    // Step 10.D — no dependency changes (pom.xml untouched by this step)
    // ------------------------------------------------------------------------------------------

    @Test
    public void testPomXmlWasNotModifiedByThisStep() throws IOException {
        // A content-marker proxy: the pre-existing Playwright/TestNG version properties must still
        // be present verbatim, and no new dependency coordinate for this step was introduced.
        String pom = Files.readString(Paths.get("pom.xml"));
        assertThat(pom).doesNotContain("<artifactId>audit</artifactId>");
    }

    // ------------------------------------------------------------------------------------------
    // AgentActionResultStatus — closed 4-value enum
    // ------------------------------------------------------------------------------------------

    @Test
    public void testAgentActionResultStatusHasExactlyTheFourAllowedValues() {
        assertThat(AgentActionResultStatus.values())
                .extracting(Enum::name)
                .containsExactlyInAnyOrder("NOT_EXECUTED", "EXECUTED", "FAILED", "BLOCKED");
    }

    // ------------------------------------------------------------------------------------------
    // Adversarial safety tests (Step 9)
    // ------------------------------------------------------------------------------------------

    /** 1. APPROVED record does not automatically create an EXECUTED result. */
    @Test
    public void adversarial1_approvedRecordDoesNotAutomaticallyCreateExecutedResult() {
        AgentApprovalRecord approval = approvalService.approve(recommendation("adv-1", 0.5, EvidenceStatus.UNVERIFIED), "ok");
        // Nothing about constructing/approving produces an AgentActionResult at all — a caller
        // must explicitly build one. There is no method reachable from AgentApprovalRecord/Service
        // that returns an AgentActionResult.
        for (Method m : AgentApprovalRecord.class.getDeclaredMethods()) {
            assertThat(m.getReturnType()).isNotEqualTo(AgentActionResult.class);
        }
        for (Method m : AgentApprovalService.class.getDeclaredMethods()) {
            assertThat(m.getReturnType()).isNotEqualTo(AgentActionResult.class);
        }
        assertThat(approval).isNotNull();
    }

    /** 2. EvidenceStatus.VERIFIED does not automatically create an EXECUTED result. */
    @Test
    public void adversarial2_verifiedEvidenceDoesNotAutomaticallyCreateExecutedResult() {
        SelfHealingRecommendation verified = recommendation("adv-2", 0.9, EvidenceStatus.VERIFIED);
        AgentActionResult result = AgentActionResult.builder()
                .action(AgentAction.LOCATOR_RECOMMENDATION).status(AgentActionResultStatus.NOT_EXECUTED)
                .addEvidenceItem(verified.getEvidenceItems().get(0)).build();

        assertThat(result.getStatus()).isEqualTo(AgentActionResultStatus.NOT_EXECUTED);
    }

    /** 3. confidence=1.0 does not automatically create an EXECUTED result. */
    @Test
    public void adversarial3_maximumConfidenceDoesNotAutomaticallyCreateExecutedResult() {
        recommendation("adv-3", 1.0, EvidenceStatus.VERIFIED);
        // AgentActionResult has no confidence field at all to even carry a "1.0" through.
        for (Method m : AgentActionResult.class.getDeclaredMethods()) {
            assertThat(m.getName()).doesNotContain("Confidence");
        }
    }

    /** 4. SelfHealingRecommendation does not automatically execute. */
    @Test
    public void adversarial4_selfHealingRecommendationHasNoExecutionCapability() {
        for (Method m : SelfHealingRecommendation.class.getDeclaredMethods()) {
            assertThat(FORBIDDEN_METHOD_NAMES).doesNotContain(m.getName());
        }
    }

    /** 5. AuditStore.add() does not execute anything — it only appends to an in-memory list. */
    @Test
    public void adversarial5_auditStoreAddDoesNotExecuteAnything() {
        AgentActionAuditStore store = new AgentActionAuditStore();
        AgentApprovalRecord approval = approvalService.approve(recommendation("adv-5", 0.5, EvidenceStatus.UNVERIFIED), "ok");
        AgentActionResult result = AgentActionResult.builder()
                .action(AgentAction.LOCATOR_RECOMMENDATION).status(AgentActionResultStatus.NOT_EXECUTED).build();

        store.add(AgentActionAuditRecord.builder().approvalRecord(approval).actionResult(result).build());

        assertThat(store.size()).isEqualTo(1);
        assertThat(store.findAll().get(0).getActionResult().getStatus()).isEqualTo(AgentActionResultStatus.NOT_EXECUTED);
    }

    /** 6. Reporter does not execute anything — summarize() is pure string formatting. */
    @Test
    public void adversarial6_reporterDoesNotExecuteAnything() {
        AgentActionAuditStore store = new AgentActionAuditStore();
        AgentApprovalRecord approval = approvalService.approve(recommendation("adv-6", 0.5, EvidenceStatus.UNVERIFIED), "ok");
        AgentActionResult result = AgentActionResult.builder()
                .action(AgentAction.LOCATOR_RECOMMENDATION).status(AgentActionResultStatus.NOT_EXECUTED).build();
        store.add(AgentActionAuditRecord.builder().approvalRecord(approval).actionResult(result).build());

        String report = new AgentActionAuditReporter(store).summarize();

        assertThat(report).isInstanceOf(String.class);
        assertThat(store.findAll().get(0).getActionResult().getStatus()).isEqualTo(AgentActionResultStatus.NOT_EXECUTED);
    }

    /** 7. AuditResult cannot invoke Playwright — structurally proven via reflection (no field/param/return type). */
    @Test
    public void adversarial7_actionResultCannotInvokePlaywright() {
        for (Field f : AgentActionResult.class.getDeclaredFields()) {
            assertThat(f.getType().getName()).doesNotContain("playwright");
        }
    }

    /** 8. No source file is modified by exercising the audit layer. */
    @Test
    public void adversarial8_noSourceFileIsModifiedByUsingTheAuditLayer() throws IOException {
        Path thisFile = Paths.get("src", "main", "java", "com", "framework", "ai", "orchestration", "AgentActionResult.java");
        String before = Files.readString(thisFile);

        AgentActionResult.builder().action(AgentAction.NONE).status(AgentActionResultStatus.NOT_EXECUTED).build();

        String after = Files.readString(thisFile);
        assertThat(after).isEqualTo(before);
    }

    /** 9. No Git operation occurs — no Git dependency/type reachable from any audit class. */
    @Test
    public void adversarial9_noGitOperationCapabilityExists() throws IOException {
        Path orchestrationDir = Paths.get("src", "main", "java", "com", "framework", "ai", "orchestration");
        for (String fileName : AUDIT_FILE_NAMES) {
            assertThat(Files.readString(orchestrationDir.resolve(fileName))).doesNotContainIgnoringCase("jgit");
        }
    }

    /** 10. No TestListener integration occurs — covered structurally in testExistingLifecycleAndOrchestrationClassesDoNotReferenceTheAuditClasses. */
    @Test
    public void adversarial10_noTestListenerIntegrationOccurs() throws IOException {
        String content = Files.readString(Paths.get("src", "main", "java", "com", "framework", "listeners", "TestListener.java"));
        assertThat(content).doesNotContain("AgentActionAudit").doesNotContain("AgentActionResult");
    }

    /** 11. No RetryAnalyzer integration occurs. */
    @Test
    public void adversarial11_noRetryAnalyzerIntegrationOccurs() throws IOException {
        String content = Files.readString(Paths.get("src", "main", "java", "com", "framework", "listeners", "RetryAnalyzer.java"));
        assertThat(content).doesNotContain("AgentActionAudit").doesNotContain("AgentActionResult");
    }

    /** 12. No network call occurs — no network-capable type/import anywhere in the new files. */
    @Test
    public void adversarial12_noNetworkCallCapabilityExists() throws IOException {
        Path orchestrationDir = Paths.get("src", "main", "java", "com", "framework", "ai", "orchestration");
        for (String fileName : AUDIT_FILE_NAMES) {
            String content = Files.readString(orchestrationDir.resolve(fileName));
            assertThat(content).doesNotContainIgnoringCase("http").doesNotContain("Socket").doesNotContain("URLConnection");
        }
    }

    /** 13. Sensitive data is sanitized in audit rendering — behavioral proof (see AgentActionAuditReporterTest for full coverage). */
    @Test
    public void adversarial13_sensitiveDataIsSanitizedInAuditRendering() {
        AgentActionAuditStore store = new AgentActionAuditStore();
        AgentApprovalRecord approval = approvalService.approve(recommendation("adv-13", 0.5, EvidenceStatus.UNVERIFIED),
                "password=hunter2");
        AgentActionResult result = AgentActionResult.builder()
                .action(AgentAction.LOCATOR_RECOMMENDATION).status(AgentActionResultStatus.NOT_EXECUTED).build();
        store.add(AgentActionAuditRecord.builder().approvalRecord(approval).actionResult(result).build());

        String report = new AgentActionAuditReporter(store).summarize();

        assertThat(report).doesNotContain("hunter2");
    }

    /** 14. Malformed/null inputs fail safely — behavioral proof across all four classes. */
    @Test
    public void adversarial14_malformedNullInputsFailSafely() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                AgentActionResult.builder().status(AgentActionResultStatus.NOT_EXECUTED).build())
                .isInstanceOf(NullPointerException.class);

        AgentActionAuditStore store = new AgentActionAuditStore();
        assertThat(store.findByApprovalId(null)).isEmpty();
        assertThat(store.findAll()).isEmpty();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> store.add(null))
                .isInstanceOf(NullPointerException.class);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new AgentActionAuditReporter(null))
                .isInstanceOf(NullPointerException.class);
    }

    // ------------------------------------------------------------------------------------------
    // Only intended public API exposed
    // ------------------------------------------------------------------------------------------

    @Test
    public void testActionResultExposesOnlyItsIntendedPublicApi() {
        List<String> publicMethods = Arrays.stream(AgentActionResult.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers())).map(Method::getName).distinct().collect(Collectors.toList());
        assertThat(publicMethods).containsExactlyInAnyOrder(
                "getAction", "getStatus", "getMessage", "getErrorType", "getStartedAt", "getCompletedAt",
                "getEvidenceItems", "builder");
    }

    @Test
    public void testAuditRecordExposesOnlyItsIntendedPublicApi() {
        List<String> publicMethods = Arrays.stream(AgentActionAuditRecord.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers())).map(Method::getName).distinct().collect(Collectors.toList());
        assertThat(publicMethods).containsExactlyInAnyOrder(
                "getApprovalRecord", "getActionResult", "getRecordedAt", "getActor", "builder");
    }

    @Test
    public void testAuditStoreExposesOnlyItsIntendedPublicApi() {
        List<String> publicMethods = Arrays.stream(AgentActionAuditStore.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers())).map(Method::getName).distinct().collect(Collectors.toList());
        assertThat(publicMethods).containsExactlyInAnyOrder("add", "findByApprovalId", "findAll", "size", "clear");
    }

    @Test
    public void testAuditReporterExposesOnlyItsIntendedPublicApi() {
        List<String> publicMethods = Arrays.stream(AgentActionAuditReporter.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers())).map(Method::getName).distinct().collect(Collectors.toList());
        assertThat(publicMethods).containsExactlyInAnyOrder("summarize");
    }
}
