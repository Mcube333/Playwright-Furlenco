package com.tests.ai.diagnosis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.framework.ai.client.AiClient;
import com.framework.ai.codegeneration.EvidenceStatus;
import com.framework.ai.config.AiConfig;
import com.framework.ai.diagnosis.FailureDiagnosis;
import com.framework.ai.diagnosis.FailureDiagnosisHelper;
import com.framework.ai.diagnosis.FailureDiagnosisReporter;
import com.framework.ai.diagnosis.FailureDiagnosisService;
import com.framework.ai.locatoradvisor.LocatorAnalysisService;
import com.framework.ai.locatoradvisor.ValidationType;
import com.framework.ai.locatoradvisor.runtime.RuntimeEnvironmentGuard;
import com.framework.ai.locatoradvisor.runtime.RuntimeLocatorValidator;
import com.framework.ai.model.AiRequest;
import com.framework.ai.model.AiResponse;
import com.framework.ai.service.FailureAnalysisService;
import com.framework.config.ConfigManager;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.tests.ai.locatoradvisor.runtime.RuntimeLocatorTestDouble;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.testng.ITestResult;
import org.testng.annotations.Test;

/**
 * Phase 7 Step 7: controlled end-to-end validation of the complete, explicitly-invoked diagnosis
 * chain —
 *
 * <pre>
 * ITestResult -&gt; FailureDiagnosisHelper -&gt; FailureContext -&gt; FailureDiagnosisService
 *   -&gt; FailureAnalysisService -&gt; (LocatorAnalysisService) -&gt; (RuntimeLocatorValidator)
 *   -&gt; FailureDiagnosis -&gt; FailureDiagnosisReporter -&gt; Markdown
 * </pre>
 *
 * Unlike {@code FailureDiagnosisServiceTest} (which drives {@code FailureDiagnosisService}
 * directly from a hand-built {@code FailureContext}) and {@code FailureDiagnosisReporterTest}
 * (which drives {@code FailureDiagnosisReporter} directly from hand-built fakes), this file is the
 * only place that exercises the REAL {@link FailureDiagnosisHelper} wired to the REAL, unmodified
 * {@link FailureDiagnosisService} and {@link FailureDiagnosisReporter} together, starting from an
 * {@link ITestResult} and a {@link Page} — proving the full chain those two classes' own test
 * suites already validate in isolation actually composes correctly end-to-end. It deliberately does
 * NOT re-assert everything those 333 existing tests already cover; it adds only what a
 * component-level test cannot: that the pieces fit together when entered through the one explicit,
 * opt-in entry point (the Helper) a QA engineer would actually use.
 *
 * Hermetic throughout: only {@link AiClient} is a hand-written stub (same convention as
 * {@code FailureDiagnosisServiceTest}'s {@code MockAiClient}); {@code Page}/{@code Locator} are JDK
 * {@link Proxy} test doubles (same convention as the existing Phase 6/Step 6 doubles) combining the
 * read-only methods needed by both {@link FailureDiagnosisHelper} ({@code isClosed/url/title/content})
 * and {@link RuntimeLocatorValidator} ({@code isClosed/url/locator/count/isVisible/isEnabled}) — no
 * existing test double file is modified to add this combined shape, avoiding any change to Phase 6
 * or Step 6 test infrastructure. No real network call, no real browser, no Mockito.
 */
public class FailureDiagnosisEndToEndTest {

    private static final String SAFE_URL = "https://www.stag.furlenco.com/cart";
    private static final String PROD_URL = "https://www.furlenco.com/cart";

    // ------------------------------------------------------------------------------------------
    // Hermetic AiClient stub — same convention as FailureDiagnosisServiceTest.MockAiClient.
    // ------------------------------------------------------------------------------------------

    private static class StubAiClient implements AiClient {
        private final AiResponse response;
        private final RuntimeException throwsException;
        private final AtomicInteger sharedCounter;
        String capturedPrompt;

        StubAiClient(AiResponse response) {
            this(response, null, null);
        }

        StubAiClient(AiResponse response, AtomicInteger sharedCounter) {
            this(response, null, sharedCounter);
        }

        StubAiClient(RuntimeException throwsException) {
            this(null, throwsException, null);
        }

        private StubAiClient(AiResponse response, RuntimeException throwsException, AtomicInteger sharedCounter) {
            this.response = response;
            this.throwsException = throwsException;
            this.sharedCounter = sharedCounter;
        }

        @Override
        public AiResponse generate(AiRequest request) {
            this.capturedPrompt = request.getPrompt();
            if (sharedCounter != null) {
                sharedCounter.incrementAndGet();
            }
            if (throwsException != null) {
                throw throwsException;
            }
            return response;
        }

        @Override public String getProviderName() { return "stub"; }
        @Override public boolean isAvailable() { return true; }
    }

    private AiConfig configWithRuntimeValidation(boolean runtimeEnabled) {
        return new AiConfig(ConfigManager.getInstance()) {
            @Override public boolean isAiEnabled() { return true; }
            @Override public String getApiKey() { return "fake-api-key"; }
            @Override public boolean isFailureAnalysisEnabled() { return true; }
            @Override public boolean isLocatorRuntimeValidationEnabled() { return runtimeEnabled; }
        };
    }

    private String locatorFailureJson(String suggestedLocator) {
        return "{\"summary\":\"Checkout button could not be clicked\","
                + "\"rootCause\":\"The locator did not resolve to any element\","
                + "\"category\":\"LOCATOR_CHANGED\","
                + "\"suggestedFix\":\"Update the locator to match the current DOM\","
                + "\"suggestedLocators\":[\"" + suggestedLocator + "\"],"
                + "\"jiraBugReport\":\"Summary: checkout button broken\","
                + "\"confidenceScore\":0.7}";
    }

    private String locatorAnalysisJsonWithCandidate(String locator) {
        return "{\"targetElement\":\"Checkout button\",\"candidates\":["
                + "{\"locator\":\"" + locator + "\",\"strategy\":\"TEST_ID\",\"rationale\":\"stable test hook\"}"
                + "],\"accessibilityFindings\":[],\"assumptions\":[],\"missingEvidence\":[]}";
    }

    private FailureDiagnosisHelper buildHelper(AiConfig cfg, AiClient failureAiClient, AiClient locatorAiClient) {
        FailureDiagnosisService service = new FailureDiagnosisService(
                cfg,
                new FailureAnalysisService(cfg, failureAiClient),
                new LocatorAnalysisService(cfg, locatorAiClient),
                new RuntimeLocatorValidator(cfg, new RuntimeEnvironmentGuard(cfg)));
        return new FailureDiagnosisHelper(cfg, service, new FailureDiagnosisReporter());
    }

    // ------------------------------------------------------------------------------------------
    // Combined Page/Locator test double: isClosed/url/title/content (needed by the Helper's own
    // FailureContext construction) + locator/count/isVisible/isEnabled (needed by
    // RuntimeLocatorValidator). Kept local to this file rather than added to either existing Page
    // double, so neither Phase 6's RuntimePageTestDouble nor Step 6's FailureDiagnosisPageTestDouble
    // is modified.
    // ------------------------------------------------------------------------------------------

    private static final class EndToEndPage {
        private boolean closed;
        private String url = "";
        private String title = "";
        private String content = "<html><body>no matching element</body></html>";
        private Locator locatorResult;
        private final List<String> invokedMethods = new ArrayList<>();

        EndToEndPage closed(boolean closed) { this.closed = closed; return this; }
        EndToEndPage url(String url) { this.url = url; return this; }
        EndToEndPage title(String title) { this.title = title; return this; }
        EndToEndPage content(String content) { this.content = content; return this; }
        EndToEndPage locatorResult(Locator locator) { this.locatorResult = locator; return this; }

        List<String> invokedMethods() {
            return invokedMethods;
        }

        Page build() {
            InvocationHandler handler = (proxy, method, args) -> {
                invokedMethods.add(method.getName());
                switch (method.getName()) {
                    case "isClosed":
                        return closed;
                    case "url":
                        return url;
                    case "title":
                        return title;
                    case "content":
                        return content;
                    case "locator":
                        if (locatorResult == null) {
                            throw new UnsupportedOperationException("No locator(...) result configured");
                        }
                        return locatorResult;
                    case "equals":
                        return proxy == args[0];
                    case "hashCode":
                        return System.identityHashCode(proxy);
                    case "toString":
                        return "FailureDiagnosisEndToEndTest.EndToEndPage";
                    default:
                        throw new UnsupportedOperationException(
                                "EndToEndPage does not support Page." + method.getName()
                                        + "() — only isClosed(), url(), title(), content(), and locator() are needed here.");
                }
            };
            return (Page) Proxy.newProxyInstance(Page.class.getClassLoader(), new Class<?>[] {Page.class}, handler);
        }
    }

    // ------------------------------------------------------------------------------------------
    // 1. Full happy path: ITestResult -> Helper -> Service -> AI + Locator + Runtime -> Diagnosis
    //    -> Reporter -> Markdown, all through the real classes.
    // ------------------------------------------------------------------------------------------

    @Test
    public void testFullChainProducesCompleteDiagnosisAndMarkdownReport() {
        AiConfig cfg = configWithRuntimeValidation(true);
        StubAiClient failureClient = new StubAiClient(AiResponse.success(
                locatorFailureJson("[data-testid='checkout-button']"), "stub"));
        StubAiClient locatorClient = new StubAiClient(AiResponse.success(
                locatorAnalysisJsonWithCandidate("[data-testid='checkout-button']"), "stub"));

        EndToEndPage pageDouble = new EndToEndPage()
                .closed(false)
                .url(SAFE_URL)
                .title("Cart")
                .content("<div>no matching element in the static DOM snapshot</div>")
                .locatorResult(RuntimeLocatorTestDouble.builder().count(1).visible(true).enabled(true).build());
        Page page = pageDouble.build();

        FailureDiagnosisReporter reporter = new FailureDiagnosisReporter();
        FailureDiagnosisService service = new FailureDiagnosisService(
                cfg,
                new FailureAnalysisService(cfg, failureClient),
                new LocatorAnalysisService(cfg, locatorClient),
                new RuntimeLocatorValidator(cfg, new RuntimeEnvironmentGuard(cfg)));
        FailureDiagnosisHelper helper = new FailureDiagnosisHelper(cfg, service, reporter);

        ITestResult testResult = FailureDiagnosisTestResultTestDouble.builder()
                .methodName("testCheckoutButtonClick")
                .className("com.tests.web.furlenco.FurlencoCartFlowTest")
                .throwable(new com.microsoft.playwright.TimeoutError("Timeout 5000ms exceeded"))
                .startMillis(1_000L)
                .endMillis(6_000L)
                .build();

        // Bind the fake Page to the current thread the same way a real test's browser would be
        // bound, so the Helper's own PlaywrightManager.getPage() call resolves to our double.
        bindPage(page);
        FailureDiagnosis diagnosis;
        try {
            diagnosis = helper.diagnose(testResult);
        } finally {
            bindPage(null);
        }

        // FailureContext parity end-to-end
        assertThat(diagnosis.getFailureContext().getTestName()).isEqualTo("testCheckoutButtonClick");
        assertThat(diagnosis.getFailureContext().getTestClass()).isEqualTo("com.tests.web.furlenco.FurlencoCartFlowTest");
        assertThat(diagnosis.getFailureContext().getErrorMessage()).contains("Timeout 5000ms exceeded");
        assertThat(diagnosis.getFailureContext().getCurrentUrl()).isEqualTo(SAFE_URL);
        assertThat(diagnosis.getFailureContext().getPageTitle()).isEqualTo("Cart");
        assertThat(diagnosis.getFailureContext().getEnvironment()).isEqualTo(cfg.getProvider());
        assertThat(diagnosis.getFailureContext().getExecutionDurationMs()).isEqualTo(5_000L);

        // AI analysis
        assertThat(diagnosis.getAiAnalysis()).isNotNull();
        assertThat(diagnosis.getAiAnalysis().getCategory().name()).isEqualTo("LOCATOR_CHANGED");

        // Locator analysis (Phase 5, real LocatorDomMatcher runs for real)
        assertThat(diagnosis.getLocatorAnalysis()).isNotNull();
        assertThat(diagnosis.getLocatorAnalysis().getCandidates()).isNotEmpty();

        // Runtime validation (Phase 6, real RuntimeLocatorValidator/RuntimeEnvironmentGuard run for real)
        assertThat(diagnosis.getRuntimeValidation()).isNotNull();
        assertThat(diagnosis.getRuntimeValidation().getValidationType()).isEqualTo(ValidationType.RUNTIME_VALIDATED);
        assertThat(diagnosis.getRuntimeValidation().getEvidenceStatus()).isEqualTo(EvidenceStatus.VERIFIED);

        // Suggested fix
        assertThat(diagnosis.getSuggestedFixes()).isNotEmpty();

        // Reporter (Phase 7 Step 4, real FailureDiagnosisReporter renders for real)
        String markdown = reporter.buildMarkdownReport(diagnosis);
        assertThat(markdown).contains("## Failure", "## AI Analysis", "## Locator Analysis",
                "## Runtime Validation", "## Suggested Fixes", "## Evidence Disclaimer");
        assertThat(markdown).contains("VERIFIED", "RUNTIME_VALIDATED");

        // Explicit reporting must not throw even without a live Allure test context.
        assertThatCode(() -> helper.report(diagnosis)).doesNotThrowAnyException();

        // Read-only proof across the WHOLE chain (Helper + RuntimeLocatorValidator together) —
        // no click/fill/navigate/reload was ever attempted on the shared Page.
        assertThat(pageDouble.invokedMethods()).containsOnly("isClosed", "url", "title", "content", "locator");
    }

    // ------------------------------------------------------------------------------------------
    // 2. Runtime validation gracefully unavailable when no Page is bound (e.g. an API-only test).
    // ------------------------------------------------------------------------------------------

    @Test
    public void testFullChainDegradesGracefullyWithNoPageBound() {
        AiConfig cfg = configWithRuntimeValidation(true);
        StubAiClient failureClient = new StubAiClient(AiResponse.success(
                locatorFailureJson("[data-testid='checkout-button']"), "stub"));
        StubAiClient locatorClient = new StubAiClient(AiResponse.success(
                locatorAnalysisJsonWithCandidate("[data-testid='checkout-button']"), "stub"));
        FailureDiagnosisHelper helper = buildHelper(cfg, failureClient, locatorClient);

        // No bindPage(...) call — PlaywrightManager.getPage() throws IllegalStateException exactly
        // as it would for a real API-only test, and the Helper must handle this safely.
        ITestResult testResult = FailureDiagnosisTestResultTestDouble.builder()
                .throwable(new RuntimeException("HTTP 500 from checkout API"))
                .build();

        FailureDiagnosis diagnosis = helper.diagnose(testResult);

        assertThat(diagnosis.getAiAnalysis()).isNotNull(); // AI path unaffected by missing Page
        assertThat(diagnosis.getRuntimeValidation()).isNull(); // runtime validation simply unavailable
        assertThat(diagnosis.getFailureContext().getCurrentUrl()).isEmpty();

        String markdown = new FailureDiagnosisReporter().buildMarkdownReport(diagnosis);
        assertThat(markdown).contains("Runtime validation: Not available");

        assertThatCode(() -> helper.report(diagnosis)).doesNotThrowAnyException();
    }

    // ------------------------------------------------------------------------------------------
    // 3. Failure isolation: an AI provider exception mid-chain must not break the overall workflow.
    // ------------------------------------------------------------------------------------------

    @Test
    public void testFullChainSurvivesAiClientException() {
        AiConfig cfg = configWithRuntimeValidation(false);
        StubAiClient throwingFailureClient = new StubAiClient(new RuntimeException("Simulated AI provider outage"));
        StubAiClient locatorClient = new StubAiClient(AiResponse.success(locatorAnalysisJsonWithCandidate("x"), "stub"));
        FailureDiagnosisHelper helper = buildHelper(cfg, throwingFailureClient, locatorClient);

        ITestResult testResult = FailureDiagnosisTestResultTestDouble.builder()
                .throwable(new RuntimeException("some failure"))
                .build();

        FailureDiagnosis diagnosis = helper.diagnoseAndReport(testResult);

        assertThat(diagnosis).isNotNull();
        assertThat(diagnosis.getAiAnalysis()).isNull(); // AI failed -> honestly absent, not fabricated
        assertThat(diagnosis.getSuggestedFixes()).isEmpty();
        assertThat(locatorClient.capturedPrompt).isNull(); // no locator call without AI analysis to gate on

        assertThatCode(() -> new FailureDiagnosisReporter().buildMarkdownReport(diagnosis)).doesNotThrowAnyException();
    }

    // ------------------------------------------------------------------------------------------
    // 4. Call-count guarantee holds when entered through the Helper (not just the Service directly).
    // ------------------------------------------------------------------------------------------

    @Test
    public void testFullChainNeverMakesThirdAiAggregationCall() {
        AiConfig cfg = configWithRuntimeValidation(true);
        AtomicInteger sharedCounter = new AtomicInteger(0);
        StubAiClient failureClient = new StubAiClient(
                AiResponse.success(locatorFailureJson("[data-testid='checkout-button']"), "stub"), sharedCounter);
        StubAiClient locatorClient = new StubAiClient(
                AiResponse.success(locatorAnalysisJsonWithCandidate("[data-testid='checkout-button']"), "stub"), sharedCounter);
        FailureDiagnosisHelper helper = buildHelper(cfg, failureClient, locatorClient);

        EndToEndPage pageDouble = new EndToEndPage()
                .closed(false)
                .url(SAFE_URL)
                .locatorResult(RuntimeLocatorTestDouble.builder().count(1).visible(true).build());
        bindPage(pageDouble.build());
        try {
            helper.diagnose(FailureDiagnosisTestResultTestDouble.builder()
                    .throwable(new RuntimeException("Timeout"))
                    .build());
        } finally {
            bindPage(null);
        }

        assertThat(sharedCounter.get()).isEqualTo(2); // exactly Phase 2 + Phase 5, never a third
    }

    // ------------------------------------------------------------------------------------------
    // 5. Security: a secret embedded in the raw failure data must not survive into the final,
    //    reporter-rendered Markdown when the ENTIRE chain (not just the reporter alone) is exercised.
    // ------------------------------------------------------------------------------------------

    @Test
    public void testFullChainNeverLeaksSecretIntoFinalMarkdown() {
        AiConfig cfg = configWithRuntimeValidation(false);
        StubAiClient failureClient = new StubAiClient(AiResponse.success(
                locatorFailureJson("[data-testid='checkout-button']"), "stub"));
        StubAiClient locatorClient = new StubAiClient(AiResponse.success(
                locatorAnalysisJsonWithCandidate("[data-testid='checkout-button']"), "stub"));
        FailureDiagnosisHelper helper = buildHelper(cfg, failureClient, locatorClient);

        EndToEndPage pageDouble = new EndToEndPage()
                .closed(false)
                .url(SAFE_URL + "?password=TEST_E2E_SECRET")
                .content("<input password=\"TEST_E2E_SECRET\" />");
        bindPage(pageDouble.build());

        ITestResult testResult = FailureDiagnosisTestResultTestDouble.builder()
                .throwable(new RuntimeException("Login failed, password=TEST_E2E_SECRET"))
                .build();

        FailureDiagnosis diagnosis;
        try {
            diagnosis = helper.diagnose(testResult);
        } finally {
            bindPage(null);
        }

        String markdown = new FailureDiagnosisReporter().buildMarkdownReport(diagnosis);
        assertThat(markdown).doesNotContain("TEST_E2E_SECRET");
        assertThat(failureClient.capturedPrompt).doesNotContain("TEST_E2E_SECRET");
    }

    // ------------------------------------------------------------------------------------------
    // Test scaffolding: binds/unbinds a fake Page on PlaywrightManager's private ThreadLocal,
    // exactly as FailureDiagnosisHelperTest already does for Step 6 — reads/writes only test-local
    // ThreadLocal state, never modifies PlaywrightManager.java.
    // ------------------------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static void bindPage(Page page) {
        try {
            java.lang.reflect.Field field = com.framework.driver.PlaywrightManager.class.getDeclaredField("PAGE");
            field.setAccessible(true);
            ThreadLocal<Page> threadLocal = (ThreadLocal<Page>) field.get(null);
            if (page == null) {
                threadLocal.remove();
            } else {
                threadLocal.set(page);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to bind test Page to PlaywrightManager for end-to-end test", e);
        }
    }
}
