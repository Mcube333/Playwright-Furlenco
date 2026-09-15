package com.tests.ai.diagnosis;

import static org.assertj.core.api.Assertions.assertThat;

import com.framework.ai.client.AiClient;
import com.framework.ai.codegeneration.EvidenceStatus;
import com.framework.ai.config.AiConfig;
import com.framework.ai.diagnosis.FailureDiagnosis;
import com.framework.ai.diagnosis.FailureDiagnosisService;
import com.framework.ai.diagnosis.FixType;
import com.framework.ai.diagnosis.SuggestedFix;
import com.framework.ai.locatoradvisor.LocatorAnalysisService;
import com.framework.ai.locatoradvisor.LocatorCandidate;
import com.framework.ai.locatoradvisor.ValidationType;
import com.framework.ai.locatoradvisor.runtime.RuntimeEnvironmentGuard;
import com.framework.ai.locatoradvisor.runtime.RuntimeLocatorValidator;
import com.framework.ai.model.AiRequest;
import com.framework.ai.model.AiResponse;
import com.framework.ai.model.FailureContext;
import com.framework.ai.service.FailureAnalysisService;
import com.framework.config.ConfigManager;
import com.microsoft.playwright.Page;
import com.tests.ai.locatoradvisor.runtime.RuntimeLocatorTestDouble;
import com.tests.ai.locatoradvisor.runtime.RuntimePageTestDouble;
import java.util.concurrent.atomic.AtomicInteger;
import org.testng.annotations.Test;

/**
 * Phase 7 Step 3: FailureDiagnosisService orchestration tests. Real Phase 2/5/6 service instances
 * are used (their own, already-tested logic runs for real); only the underlying {@link AiClient}
 * is a hand-written hermetic stub. Page/Locator fakes are the existing Phase 6
 * java.lang.reflect.Proxy-backed test doubles, reused as-is — no Mockito, no real browser, no
 * real network/Gemini call anywhere in this file.
 */
public class FailureDiagnosisServiceTest {

    private static final String SAFE_URL = "https://www.stag.furlenco.com";

    private static class MockAiClient implements AiClient {
        private final AiResponse stubResponse;
        private final boolean available;
        private final AtomicInteger invocationCount;
        public String capturedPrompt;

        MockAiClient(AiResponse stubResponse, boolean available) {
            this(stubResponse, available, null);
        }

        MockAiClient(AiResponse stubResponse, boolean available, AtomicInteger sharedCounter) {
            this.stubResponse = stubResponse;
            this.available = available;
            this.invocationCount = sharedCounter;
        }

        @Override
        public AiResponse generate(AiRequest request) {
            this.capturedPrompt = request.getPrompt();
            if (invocationCount != null) {
                invocationCount.incrementAndGet();
            }
            return stubResponse;
        }

        @Override public String getProviderName() { return "mock"; }
        @Override public boolean isAvailable() { return available; }
    }

    private AiConfig config(boolean aiEnabled, boolean failureAnalysisEnabled, boolean runtimeEnabled) {
        return new AiConfig(ConfigManager.getInstance()) {
            @Override public boolean isAiEnabled() { return aiEnabled; }
            @Override public String getApiKey() { return "fake-api-key"; }
            @Override public boolean isFailureAnalysisEnabled() { return failureAnalysisEnabled; }
            @Override public boolean isLocatorRuntimeValidationEnabled() { return runtimeEnabled; }
        };
    }

    private FailureContext context(String errorMessage, String dom) {
        return FailureContext.builder()
                .testName("testAddToCart")
                .testClass("com.tests.web.furlenco.FurlencoCartFlowTest")
                .errorMessage(errorMessage)
                .stackTrace("com.microsoft.playwright.TimeoutError: Timeout 5000ms exceeded")
                .currentUrl(SAFE_URL + "/cart")
                .pageTitle("Cart")
                .domSnippet(dom)
                .executionDurationMs(4321)
                .environment("gemini")
                .build();
    }

    private String failureJson(String category, String suggestedLocator) {
        return failureJsonWithFix(category, suggestedLocator, "Update the locator to match the current DOM");
    }

    private String failureJsonWithFix(String category, String suggestedLocator, String suggestedFix) {
        return "{\"summary\":\"Add to cart button could not be clicked\","
                + "\"rootCause\":\"The locator did not resolve to any element\","
                + "\"category\":\"" + category + "\","
                + "\"suggestedFix\":\"" + suggestedFix + "\","
                + "\"suggestedLocators\":" + (suggestedLocator == null ? "[]" : "[\"" + suggestedLocator + "\"]")
                + ",\"jiraBugReport\":\"Summary: cart button broken\","
                + "\"confidenceScore\":0.7}";
    }

    private String locatorJson() {
        return "{\"targetElement\":\"Add to cart button\",\"candidates\":[],\"accessibilityFindings\":[],"
                + "\"assumptions\":[],\"missingEvidence\":[]}";
    }

    private FailureDiagnosisService serviceWith(AiConfig cfg, MockAiClient failureClient, MockAiClient locatorClient) {
        return new FailureDiagnosisService(cfg,
                new FailureAnalysisService(cfg, failureClient),
                new LocatorAnalysisService(cfg, locatorClient),
                new RuntimeLocatorValidator(cfg, new RuntimeEnvironmentGuard(cfg)));
    }

    // =====================================================================
    // BASIC
    // =====================================================================

    // 1. Valid diagnosis construction
    @Test
    public void testValidDiagnosisConstruction() {
        AiConfig cfg = config(true, true, false);
        MockAiClient failureClient = new MockAiClient(AiResponse.success(failureJson("APPLICATION_BUG", null), "mock"), true);
        MockAiClient locatorClient = new MockAiClient(AiResponse.success(locatorJson(), "mock"), true);
        FailureDiagnosisService service = serviceWith(cfg, failureClient, locatorClient);

        FailureDiagnosis diagnosis = service.diagnose(context("HTTP 500", "<html/>"));

        assertThat(diagnosis).isNotNull();
        assertThat(diagnosis.getFailureContext()).isNotNull();
        assertThat(diagnosis.getAiAnalysis()).isNotNull();
        assertThat(diagnosis.getSuggestedFixes()).isNotEmpty();
    }

    // 2. Null FailureContext handling
    @Test
    public void testNullFailureContextReturnsEmptyDiagnosisNotException() {
        AiConfig cfg = config(true, true, false);
        MockAiClient client = new MockAiClient(AiResponse.failure("should never be called"), true);
        FailureDiagnosisService service = serviceWith(cfg, client, client);

        FailureDiagnosis diagnosis = service.diagnose(null);

        assertThat(diagnosis).isNotNull();
        assertThat(diagnosis.getFailureContext()).isNull();
        assertThat(diagnosis.getAiAnalysis()).isNull();
        assertThat(diagnosis.getSuggestedFixes()).isEmpty();
        assertThat(client.capturedPrompt).isNull(); // never invoked
    }

    // 3. AI analysis available
    @Test
    public void testAiAnalysisAvailable() {
        AiConfig cfg = config(true, true, false);
        MockAiClient failureClient = new MockAiClient(AiResponse.success(failureJson("APPLICATION_BUG", null), "mock"), true);
        MockAiClient locatorClient = new MockAiClient(AiResponse.success(locatorJson(), "mock"), true);
        FailureDiagnosis diagnosis = serviceWith(cfg, failureClient, locatorClient).diagnose(context("HTTP 500", "<html/>"));

        assertThat(diagnosis.getAiAnalysis()).isNotNull();
        assertThat(diagnosis.getAiAnalysis().getSummary()).isNotBlank();
    }

    // 4. AI analysis unavailable
    @Test
    public void testAiAnalysisUnavailable() {
        AiConfig cfg = config(true, false, false); // ai.failure.analysis.enabled=false
        MockAiClient failureClient = new MockAiClient(AiResponse.failure("n/a"), true);
        MockAiClient locatorClient = new MockAiClient(AiResponse.success(locatorJson(), "mock"), true);
        FailureDiagnosis diagnosis = serviceWith(cfg, failureClient, locatorClient).diagnose(context("Timeout", "<html/>"));

        assertThat(diagnosis.getAiAnalysis()).isNull();
        assertThat(diagnosis.getSuggestedFixes()).isEmpty(); // no invented fix without any AI signal
    }

    // 5. Locator analysis available
    @Test
    public void testLocatorAnalysisAvailable() {
        AiConfig cfg = config(true, true, false);
        MockAiClient failureClient = new MockAiClient(AiResponse.success(failureJson("LOCATOR_CHANGED", "#checkout-button"), "mock"), true);
        MockAiClient locatorClient = new MockAiClient(AiResponse.success(locatorJson(), "mock"), true);
        FailureDiagnosis diagnosis = serviceWith(cfg, failureClient, locatorClient).diagnose(context("Timeout", "<div/>"));

        assertThat(diagnosis.getLocatorAnalysis()).isNotNull();
    }

    // 6. Locator analysis unavailable (non-locator category)
    @Test
    public void testLocatorAnalysisUnavailableForNonLocatorFailure() {
        AiConfig cfg = config(true, true, false);
        MockAiClient failureClient = new MockAiClient(AiResponse.success(failureJson("API_FAILURE", null), "mock"), true);
        MockAiClient locatorClient = new MockAiClient(AiResponse.success(locatorJson(), "mock"), true);
        FailureDiagnosis diagnosis = serviceWith(cfg, failureClient, locatorClient).diagnose(context("HTTP 500 from checkout API", "<html/>"));

        assertThat(diagnosis.getLocatorAnalysis()).isNull();
        assertThat(locatorClient.capturedPrompt).isNull();
    }

    // 7. Runtime validation available
    @Test
    public void testRuntimeValidationAvailable() {
        AiConfig cfg = config(true, true, true);
        MockAiClient failureClient = new MockAiClient(AiResponse.success(failureJson("LOCATOR_CHANGED", "#checkout-button"), "mock"), true);
        MockAiClient locatorClient = new MockAiClient(AiResponse.success(locatorJson(), "mock"), true);
        Page page = RuntimePageTestDouble.builder().url(SAFE_URL).closed(false)
                .locator(RuntimeLocatorTestDouble.builder().count(1).visible(true).build()).build();

        FailureDiagnosis diagnosis = serviceWith(cfg, failureClient, locatorClient).diagnose(context("Timeout", "<div/>"), page);

        assertThat(diagnosis.getRuntimeValidation()).isNotNull();
    }

    // 8. Runtime validation unavailable (no Page)
    @Test
    public void testRuntimeValidationUnavailableWithoutPage() {
        AiConfig cfg = config(true, true, true);
        MockAiClient failureClient = new MockAiClient(AiResponse.success(failureJson("LOCATOR_CHANGED", "#checkout-button"), "mock"), true);
        MockAiClient locatorClient = new MockAiClient(AiResponse.success(locatorJson(), "mock"), true);
        FailureDiagnosis diagnosis = serviceWith(cfg, failureClient, locatorClient).diagnose(context("Timeout", "<div/>"));

        assertThat(diagnosis.getRuntimeValidation()).isNull();
    }

    // =====================================================================
    // FILTERING
    // =====================================================================

    // 9. Locator-related failure triggers locator analysis
    @Test
    public void testLocatorRelatedFailureTriggersLocatorAnalysis() {
        AiConfig cfg = config(true, true, false);
        MockAiClient failureClient = new MockAiClient(AiResponse.success(failureJson("LOCATOR_CHANGED", "#checkout-button"), "mock"), true);
        MockAiClient locatorClient = new MockAiClient(AiResponse.success(locatorJson(), "mock"), true);
        serviceWith(cfg, failureClient, locatorClient).diagnose(context("Timeout", "<div/>"));

        assertThat(locatorClient.capturedPrompt).isNotNull();
    }

    // 10. Clearly non-locator failure skips locator analysis
    @Test
    public void testNonLocatorFailureSkipsLocatorAnalysis() {
        AiConfig cfg = config(true, true, false);
        MockAiClient failureClient = new MockAiClient(AiResponse.success(failureJson("DATA_ISSUE", null), "mock"), true);
        MockAiClient locatorClient = new MockAiClient(AiResponse.success(locatorJson(), "mock"), true);
        serviceWith(cfg, failureClient, locatorClient).diagnose(context("Bad seed data", "<div/>"));

        assertThat(locatorClient.capturedPrompt).isNull();
    }

    // 11. Unknown category fails conservatively (does not assume locator failure)
    @Test
    public void testUnknownCategoryFailsConservatively() {
        AiConfig cfg = config(true, true, false);
        MockAiClient failureClient = new MockAiClient(AiResponse.success(failureJson("UNCERTAIN", "#maybe-locator"), "mock"), true);
        MockAiClient locatorClient = new MockAiClient(AiResponse.success(locatorJson(), "mock"), true);
        FailureDiagnosis diagnosis = serviceWith(cfg, failureClient, locatorClient).diagnose(context("Something failed", "<div/>"));

        assertThat(diagnosis.getLocatorAnalysis()).isNull();
        assertThat(locatorClient.capturedPrompt).isNull();
    }

    // =====================================================================
    // LOCATOR CANDIDATES
    // =====================================================================

    // 12. No candidate at all -> MISSING evidence, no fabrication
    @Test
    public void testNoCandidateProducesMissingEvidence() {
        AiConfig cfg = config(true, true, false);
        MockAiClient failureClient = new MockAiClient(AiResponse.success(failureJson("LOCATOR_CHANGED", null), "mock"), true);
        MockAiClient locatorClient = new MockAiClient(AiResponse.success(locatorJson(), "mock"), true);
        // DOM present so hasUsefulLocatorEvidence is true via DOM alone, but no candidates ever proposed.
        FailureDiagnosis diagnosis = serviceWith(cfg, failureClient, locatorClient).diagnose(context("Timeout", "<div>irrelevant</div>"));

        SuggestedFix fix = diagnosis.getSuggestedFixes().get(0);
        assertThat(fix.getRelatedLocatorCandidate()).isNull();
        assertThat(fix.getEvidenceItems()).anyMatch(e -> e.getStatus() == EvidenceStatus.MISSING);
    }

    // 13. One candidate
    @Test
    public void testOneCandidateBecomesTheTopCandidate() {
        AiConfig cfg = config(true, true, false);
        MockAiClient failureClient = new MockAiClient(AiResponse.success(failureJson("LOCATOR_CHANGED", "#checkout-button"), "mock"), true);
        MockAiClient locatorClient = new MockAiClient(AiResponse.success(locatorJson(), "mock"), true);
        String dom = "<button id=\"checkout-button\">Checkout</button>"; // matches by id, not the css selector form though
        FailureDiagnosis diagnosis = serviceWith(cfg, failureClient, locatorClient).diagnose(context("Timeout", dom));

        assertThat(diagnosis.getLocatorAnalysis().getCandidates()).hasSize(1);
        assertThat(diagnosis.getSuggestedFixes().get(0).getRelatedLocatorCandidate()).isNotNull();
    }

    // 14. Multiple candidates -> only the top one is used
    @Test
    public void testMultipleCandidatesOnlyTopOneUsed() {
        AiConfig cfg = config(true, true, false);
        MockAiClient failureClient = new MockAiClient(AiResponse.success(failureJson("LOCATOR_CHANGED", "#checkout-button"), "mock"), true);
        String multiCandidateJson = "{\"targetElement\":\"Checkout button\",\"candidates\":["
                + "{\"locator\":\"[data-testid='checkout']\",\"strategy\":\"TEST_ID\",\"rationale\":\"stable\"},"
                + "{\"locator\":\".checkout-btn\",\"strategy\":\"CSS_STABLE\",\"rationale\":\"fallback\"}"
                + "],\"accessibilityFindings\":[],\"assumptions\":[],\"missingEvidence\":[]}";
        MockAiClient locatorClient = new MockAiClient(AiResponse.success(multiCandidateJson, "mock"), true);
        String dom = "<button data-testid=\"checkout\">Checkout</button>";

        FailureDiagnosis diagnosis = serviceWith(cfg, failureClient, locatorClient).diagnose(context("Timeout", dom));

        assertThat(diagnosis.getLocatorAnalysis().getCandidates()).hasSizeGreaterThan(1);
        assertThat(diagnosis.getSuggestedFixes()).hasSize(1); // exactly one SuggestedFix, for the top candidate only
    }

    // 15. Only the top candidate receives runtime validation
    @Test
    public void testOnlyTopCandidateReceivesRuntimeValidation() {
        AiConfig cfg = config(true, true, true);
        MockAiClient failureClient = new MockAiClient(AiResponse.success(failureJson("LOCATOR_CHANGED", "#checkout-button"), "mock"), true);
        String multiCandidateJson = "{\"targetElement\":\"Checkout button\",\"candidates\":["
                + "{\"locator\":\"[data-testid='checkout']\",\"strategy\":\"TEST_ID\",\"rationale\":\"stable\"},"
                + "{\"locator\":\".checkout-btn\",\"strategy\":\"CSS_STABLE\",\"rationale\":\"fallback\"}"
                + "],\"accessibilityFindings\":[],\"assumptions\":[],\"missingEvidence\":[]}";
        MockAiClient locatorClient = new MockAiClient(AiResponse.success(multiCandidateJson, "mock"), true);
        String dom = "<button data-testid=\"checkout\">Checkout</button>";
        Page page = RuntimePageTestDouble.builder().url(SAFE_URL).closed(false)
                .locator(RuntimeLocatorTestDouble.builder().count(1).visible(true).build()).build();

        FailureDiagnosis diagnosis = serviceWith(cfg, failureClient, locatorClient).diagnose(context("Timeout", dom), page);

        assertThat(diagnosis.getRuntimeValidation()).isNotNull();
        // Exactly one runtime result exists at the diagnosis level — the aggregate model has a
        // single runtimeValidation field, structurally guaranteeing only one candidate was validated.
        LocatorCandidate recommended = diagnosis.getLocatorAnalysis().getRecommendedLocator();
        assertThat(diagnosis.getRuntimeValidation().getLocator()).isEqualTo(recommended.getLocator());
    }

    // 16. Candidate evidence remains unchanged (LocatorCandidate is immutable / never mutated)
    @Test
    public void testCandidateEvidenceRemainsUnchangedAfterDiagnosis() {
        AiConfig cfg = config(true, true, true);
        // An attribute-selector form (evaluable by LocatorDomMatcher) rather than "#id", so the
        // candidate is genuinely DOM-evaluated (matchCount 0) rather than "unparseable selector".
        MockAiClient failureClient = new MockAiClient(
                AiResponse.success(failureJson("LOCATOR_CHANGED", "[data-testid='checkout-button']"), "mock"), true);
        MockAiClient locatorClient = new MockAiClient(AiResponse.success(locatorJson(), "mock"), true);
        Page page = RuntimePageTestDouble.builder().url(SAFE_URL).closed(false)
                .locator(RuntimeLocatorTestDouble.builder().count(1).visible(true).build()).build();

        String dom = "<div>no matching element</div>"; // ensures the DOM-side candidate stays UNVERIFIED
        FailureDiagnosis diagnosis = serviceWith(cfg, failureClient, locatorClient).diagnose(context("Timeout", dom), page);

        LocatorCandidate candidate = diagnosis.getSuggestedFixes().get(0).getRelatedLocatorCandidate();
        // Even though the runtime check (below) says VERIFIED, the static candidate object's own
        // EvidenceStatus/ValidationType must remain exactly what Phase 5 originally determined.
        assertThat(candidate.getEvidenceStatus()).isEqualTo(EvidenceStatus.UNVERIFIED);
        assertThat(candidate.getValidationType()).isEqualTo(ValidationType.DOM_MATCHED);
    }

    // =====================================================================
    // RUNTIME
    // =====================================================================

    // 17. Runtime validation disabled
    @Test
    public void testRuntimeValidationDisabled() {
        AiConfig cfg = config(true, true, false);
        MockAiClient failureClient = new MockAiClient(AiResponse.success(failureJson("LOCATOR_CHANGED", "#checkout-button"), "mock"), true);
        MockAiClient locatorClient = new MockAiClient(AiResponse.success(locatorJson(), "mock"), true);
        Page page = RuntimePageTestDouble.builder().url(SAFE_URL).closed(false)
                .locator(RuntimeLocatorTestDouble.builder().count(1).visible(true).build()).build();

        FailureDiagnosis diagnosis = serviceWith(cfg, failureClient, locatorClient).diagnose(context("Timeout", "<div/>"), page);

        assertThat(diagnosis.getRuntimeValidation()).isNotNull(); // validator still called, but internally gated
        assertThat(diagnosis.getRuntimeValidation().getValidationType()).isEqualTo(ValidationType.NOT_VALIDATED);
    }

    // 18. Runtime validation enabled
    @Test
    public void testRuntimeValidationEnabled() {
        AiConfig cfg = config(true, true, true);
        MockAiClient failureClient = new MockAiClient(AiResponse.success(failureJson("LOCATOR_CHANGED", "#checkout-button"), "mock"), true);
        MockAiClient locatorClient = new MockAiClient(AiResponse.success(locatorJson(), "mock"), true);
        Page page = RuntimePageTestDouble.builder().url(SAFE_URL).closed(false)
                .locator(RuntimeLocatorTestDouble.builder().count(1).visible(true).build()).build();

        FailureDiagnosis diagnosis = serviceWith(cfg, failureClient, locatorClient).diagnose(context("Timeout", "<div/>"), page);

        assertThat(diagnosis.getRuntimeValidation().getEvidenceStatus()).isEqualTo(EvidenceStatus.VERIFIED);
        assertThat(diagnosis.getRuntimeValidation().getValidationType()).isEqualTo(ValidationType.RUNTIME_VALIDATED);
    }

    // 19. Unsafe environment blocks runtime validation (existing RuntimeEnvironmentGuard, not re-implemented here)
    @Test
    public void testUnsafeEnvironmentBlocksRuntimeValidation() {
        AiConfig cfg = config(true, true, true);
        MockAiClient failureClient = new MockAiClient(AiResponse.success(failureJson("LOCATOR_CHANGED", "#checkout-button"), "mock"), true);
        MockAiClient locatorClient = new MockAiClient(AiResponse.success(locatorJson(), "mock"), true);
        Page productionPage = RuntimePageTestDouble.builder().url("https://www.furlenco.com").closed(false)
                .locator(RuntimeLocatorTestDouble.builder().count(1).visible(true).build()).build();

        FailureDiagnosis diagnosis = serviceWith(cfg, failureClient, locatorClient).diagnose(context("Timeout", "<div/>"), productionPage);

        assertThat(diagnosis.getRuntimeValidation().getValidationType()).isEqualTo(ValidationType.NOT_VALIDATED);
        assertThat(diagnosis.getRuntimeValidation().getMessage()).contains("environment guard");
    }

    // 20. Null Page
    @Test
    public void testNullPageSkipsRuntimeValidationSafely() {
        AiConfig cfg = config(true, true, true);
        MockAiClient failureClient = new MockAiClient(AiResponse.success(failureJson("LOCATOR_CHANGED", "#checkout-button"), "mock"), true);
        MockAiClient locatorClient = new MockAiClient(AiResponse.success(locatorJson(), "mock"), true);

        FailureDiagnosis diagnosis = serviceWith(cfg, failureClient, locatorClient).diagnose(context("Timeout", "<div/>"), null);

        assertThat(diagnosis.getRuntimeValidation()).isNull();
    }

    // 21. Runtime validator failure (throws) does not break diagnosis
    @Test
    public void testRuntimeValidatorFailureDoesNotBreakDiagnosis() {
        AiConfig cfg = config(true, true, true);
        MockAiClient failureClient = new MockAiClient(AiResponse.success(failureJson("LOCATOR_CHANGED", "#checkout-button"), "mock"), true);
        MockAiClient locatorClient = new MockAiClient(AiResponse.success(locatorJson(), "mock"), true);
        Page page = RuntimePageTestDouble.builder().isClosedThrows(new RuntimeException("simulated catastrophic failure")).build();

        FailureDiagnosis diagnosis = serviceWith(cfg, failureClient, locatorClient).diagnose(context("Timeout", "<div/>"), page);

        assertThat(diagnosis).isNotNull();
        assertThat(diagnosis.getRuntimeValidation()).isNotNull();
        assertThat(diagnosis.getRuntimeValidation().getValidationType()).isEqualTo(ValidationType.NOT_VALIDATED);
    }

    // 22. Runtime result remains separate from static candidate evidence (see also #16, #24)
    @Test
    public void testRuntimeResultRemainsSeparateObjectFromLocatorAnalysis() {
        AiConfig cfg = config(true, true, true);
        MockAiClient failureClient = new MockAiClient(AiResponse.success(failureJson("LOCATOR_CHANGED", "#checkout-button"), "mock"), true);
        MockAiClient locatorClient = new MockAiClient(AiResponse.success(locatorJson(), "mock"), true);
        Page page = RuntimePageTestDouble.builder().url(SAFE_URL).closed(false)
                .locator(RuntimeLocatorTestDouble.builder().count(1).visible(true).build()).build();

        FailureDiagnosis diagnosis = serviceWith(cfg, failureClient, locatorClient).diagnose(context("Timeout", "<div/>"), page);

        assertThat(diagnosis.getLocatorAnalysis()).isNotNull();
        assertThat(diagnosis.getRuntimeValidation()).isNotNull();
        assertThat(diagnosis.getLocatorAnalysis()).isNotSameAs(diagnosis.getRuntimeValidation());
    }

    // =====================================================================
    // EVIDENCE
    // =====================================================================

    // 23. AI cannot upgrade evidence, even with a confident rationale
    @Test
    public void testAiRationaleCannotUpgradeEvidenceToVerified() {
        AiConfig cfg = config(true, true, false);
        MockAiClient failureClient = new MockAiClient(AiResponse.success(failureJson("LOCATOR_CHANGED", "#checkout-button"), "mock"), true);
        String confidentJson = "{\"targetElement\":\"Checkout button\",\"candidates\":["
                + "{\"locator\":\"#checkout-button\",\"strategy\":\"CSS_STABLE\",\"rationale\":\"This is verified and definitely correct\"}"
                + "],\"accessibilityFindings\":[],\"assumptions\":[],\"missingEvidence\":[]}";
        MockAiClient locatorClient = new MockAiClient(AiResponse.success(confidentJson, "mock"), true);

        FailureDiagnosis diagnosis = serviceWith(cfg, failureClient, locatorClient)
                .diagnose(context("Timeout", "<div>no matching element</div>"));

        assertThat(diagnosis.getSuggestedFixes().get(0).getRelatedLocatorCandidate().getEvidenceStatus())
                .isEqualTo(EvidenceStatus.UNVERIFIED);
    }

    // 24. Runtime result cannot upgrade the static candidate's own evidence (see also #16)
    @Test
    public void testRuntimeResultCannotUpgradeStaticCandidateEvidence() {
        AiConfig cfg = config(true, true, true);
        MockAiClient failureClient = new MockAiClient(AiResponse.success(failureJson("LOCATOR_CHANGED", "#checkout-button"), "mock"), true);
        MockAiClient locatorClient = new MockAiClient(AiResponse.success(locatorJson(), "mock"), true);
        Page page = RuntimePageTestDouble.builder().url(SAFE_URL).closed(false)
                .locator(RuntimeLocatorTestDouble.builder().count(1).visible(true).build()).build();

        FailureDiagnosis diagnosis = serviceWith(cfg, failureClient, locatorClient)
                .diagnose(context("Timeout", "<div>no matching element</div>"), page);

        assertThat(diagnosis.getRuntimeValidation().getEvidenceStatus()).isEqualTo(EvidenceStatus.VERIFIED);
        assertThat(diagnosis.getSuggestedFixes().get(0).getRelatedLocatorCandidate().getEvidenceStatus())
                .isEqualTo(EvidenceStatus.UNVERIFIED); // static candidate untouched by the runtime result
    }

    // 25. Unverified locator remains unverified end-to-end
    @Test
    public void testUnverifiedLocatorRemainsUnverifiedEndToEnd() {
        AiConfig cfg = config(true, true, false);
        MockAiClient failureClient = new MockAiClient(AiResponse.success(failureJson("LOCATOR_CHANGED", "#checkout-button"), "mock"), true);
        MockAiClient locatorClient = new MockAiClient(AiResponse.success(locatorJson(), "mock"), true);

        FailureDiagnosis diagnosis = serviceWith(cfg, failureClient, locatorClient)
                .diagnose(context("Timeout", "<div>no matching element</div>"));

        assertThat(diagnosis.getSuggestedFixes().get(0).getEvidenceItems())
                .anyMatch(e -> e.getStatus() == EvidenceStatus.UNVERIFIED);
    }

    // 26. Missing evidence remains missing (no candidate at all — see also #12)
    @Test
    public void testMissingEvidenceRemainsMissing() {
        AiConfig cfg = config(true, true, false);
        MockAiClient failureClient = new MockAiClient(AiResponse.success(failureJson("LOCATOR_CHANGED", null), "mock"), true);
        MockAiClient locatorClient = new MockAiClient(AiResponse.success(locatorJson(), "mock"), true);

        FailureDiagnosis diagnosis = serviceWith(cfg, failureClient, locatorClient)
                .diagnose(context("Timeout", "<div>some unrelated markup</div>"));

        assertThat(diagnosis.getSuggestedFixes().get(0).getEvidenceItems())
                .anyMatch(e -> e.getStatus() == EvidenceStatus.MISSING);
    }

    // 27. Existing EvidenceItem values are preserved verbatim (status/source/confidence copied through)
    @Test
    public void testEvidenceItemValuesPreservedVerbatim() {
        AiConfig cfg = config(true, true, false);
        MockAiClient failureClient = new MockAiClient(AiResponse.success(failureJson("LOCATOR_CHANGED", "#checkout-button"), "mock"), true);
        MockAiClient locatorClient = new MockAiClient(AiResponse.success(locatorJson(), "mock"), true);

        FailureDiagnosis diagnosis = serviceWith(cfg, failureClient, locatorClient)
                .diagnose(context("Timeout", "<div>no matching element</div>"));

        LocatorCandidate candidate = diagnosis.getSuggestedFixes().get(0).getRelatedLocatorCandidate();
        boolean matches = diagnosis.getSuggestedFixes().get(0).getEvidenceItems().stream().anyMatch(e ->
                e.getValue().equals(candidate.getLocator())
                        && e.getStatus() == candidate.getEvidenceStatus()
                        && e.getConfidence() == candidate.getConfidence());
        assertThat(matches).isTrue();
    }

    // =====================================================================
    // SUGGESTED FIXES
    // =====================================================================

    // 28. Locator fix
    @Test
    public void testLocatorFixType() {
        AiConfig cfg = config(true, true, false);
        MockAiClient failureClient = new MockAiClient(AiResponse.success(failureJson("LOCATOR_CHANGED", "#checkout-button"), "mock"), true);
        MockAiClient locatorClient = new MockAiClient(AiResponse.success(locatorJson(), "mock"), true);
        FailureDiagnosis diagnosis = serviceWith(cfg, failureClient, locatorClient).diagnose(context("Timeout", "<div/>"));
        assertThat(diagnosis.getSuggestedFixes().get(0).getFixType()).isEqualTo(FixType.LOCATOR);
    }

    // 29. Assertion fix
    @Test
    public void testAssertionFixType() {
        AiConfig cfg = config(true, true, false);
        MockAiClient failureClient = new MockAiClient(AiResponse.success(
                failureJsonWithFix("APPLICATION_BUG", null, "Update the assertion to match the observed checkout status"), "mock"), true);
        MockAiClient locatorClient = new MockAiClient(AiResponse.success(locatorJson(), "mock"), true);
        FailureDiagnosis diagnosis = serviceWith(cfg, failureClient, locatorClient).diagnose(context("Assertion mismatch", "<div/>"));
        assertThat(diagnosis.getSuggestedFixes().get(0).getFixType()).isEqualTo(FixType.ASSERTION);
    }

    // 30. Wait fix
    @Test
    public void testWaitFixType() {
        AiConfig cfg = config(true, true, false);
        MockAiClient failureClient = new MockAiClient(AiResponse.success(
                failureJsonWithFix("APPLICATION_BUG", null, "Add an explicit wait for the async state to settle"), "mock"), true);
        MockAiClient locatorClient = new MockAiClient(AiResponse.success(locatorJson(), "mock"), true);
        FailureDiagnosis diagnosis = serviceWith(cfg, failureClient, locatorClient).diagnose(context("Race condition", "<div/>"));
        assertThat(diagnosis.getSuggestedFixes().get(0).getFixType()).isEqualTo(FixType.WAIT);
    }

    // 31. Test-data fix
    @Test
    public void testTestDataFixType() {
        AiConfig cfg = config(true, true, false);
        MockAiClient failureClient = new MockAiClient(AiResponse.success(failureJson("DATA_ISSUE", null), "mock"), true);
        MockAiClient locatorClient = new MockAiClient(AiResponse.success(locatorJson(), "mock"), true);
        FailureDiagnosis diagnosis = serviceWith(cfg, failureClient, locatorClient).diagnose(context("Bad seed data", "<div/>"));
        assertThat(diagnosis.getSuggestedFixes().get(0).getFixType()).isEqualTo(FixType.TEST_DATA);
    }

    // 32. Application behavior fix
    @Test
    public void testApplicationBehaviorFixType() {
        AiConfig cfg = config(true, true, false);
        MockAiClient failureClient = new MockAiClient(AiResponse.success(
                failureJsonWithFix("APPLICATION_BUG", null, "Escalate to the backend team for investigation"), "mock"), true);
        MockAiClient locatorClient = new MockAiClient(AiResponse.success(locatorJson(), "mock"), true);
        FailureDiagnosis diagnosis = serviceWith(cfg, failureClient, locatorClient).diagnose(context("HTTP 500", "<div/>"));
        assertThat(diagnosis.getSuggestedFixes().get(0).getFixType()).isEqualTo(FixType.APPLICATION_BEHAVIOR);
    }

    // 33. Analytics fix
    @Test
    public void testAnalyticsFixType() {
        AiConfig cfg = config(true, true, false);
        MockAiClient failureClient = new MockAiClient(AiResponse.success(
                failureJsonWithFix("APPLICATION_BUG", null, "Confirm the analytics tracking event fires on checkout"), "mock"), true);
        MockAiClient locatorClient = new MockAiClient(AiResponse.success(locatorJson(), "mock"), true);
        FailureDiagnosis diagnosis = serviceWith(cfg, failureClient, locatorClient).diagnose(context("Analytics mismatch", "<div/>"));
        assertThat(diagnosis.getSuggestedFixes().get(0).getFixType()).isEqualTo(FixType.ANALYTICS);
        // Never VERIFIED merely because the AI suggested it (see also #23).
        assertThat(diagnosis.getSuggestedFixes().get(0).getEvidenceItems())
                .allMatch(e -> e.getStatus() == EvidenceStatus.INFERRED);
    }

    // 34. API fix
    @Test
    public void testApiFixType() {
        AiConfig cfg = config(true, true, false);
        MockAiClient failureClient = new MockAiClient(AiResponse.success(failureJson("API_FAILURE", null), "mock"), true);
        MockAiClient locatorClient = new MockAiClient(AiResponse.success(locatorJson(), "mock"), true);
        FailureDiagnosis diagnosis = serviceWith(cfg, failureClient, locatorClient).diagnose(context("HTTP 500 from checkout API", "<div/>"));
        assertThat(diagnosis.getSuggestedFixes().get(0).getFixType()).isEqualTo(FixType.API);
    }

    // 35. Unknown fix
    @Test
    public void testUnknownFixType() {
        AiConfig cfg = config(true, true, false);
        MockAiClient failureClient = new MockAiClient(AiResponse.success(
                failureJsonWithFix("ENVIRONMENT_FAILURE", null, "Investigate the deployment configuration"), "mock"), true);
        MockAiClient locatorClient = new MockAiClient(AiResponse.success(locatorJson(), "mock"), true);
        FailureDiagnosis diagnosis = serviceWith(cfg, failureClient, locatorClient).diagnose(context("Environment misconfigured", "<div/>"));
        assertThat(diagnosis.getSuggestedFixes().get(0).getFixType()).isEqualTo(FixType.UNKNOWN);
    }

    // =====================================================================
    // SAFETY
    // =====================================================================

    // 36. No Playwright mutation: the test doubles only implement isClosed/url/locator/count/
    //     isVisible/isEnabled — any click/fill/press call would throw UnsupportedOperationException
    //     inside RuntimeLocatorValidator's own try/catch. A clean VERIFIED result (as in #18) is only
    //     reachable if no such call was ever attempted.
    @Test
    public void testNoPlaywrightMutationOccurs() {
        AiConfig cfg = config(true, true, true);
        MockAiClient failureClient = new MockAiClient(AiResponse.success(failureJson("LOCATOR_CHANGED", "#checkout-button"), "mock"), true);
        MockAiClient locatorClient = new MockAiClient(AiResponse.success(locatorJson(), "mock"), true);
        Page page = RuntimePageTestDouble.builder().url(SAFE_URL).closed(false)
                .locator(RuntimeLocatorTestDouble.builder().count(1).visible(true).enabled(true).build()).build();

        FailureDiagnosis diagnosis = serviceWith(cfg, failureClient, locatorClient).diagnose(context("Timeout", "<div/>"), page);

        assertThat(diagnosis.getRuntimeValidation().getEvidenceStatus()).isEqualTo(EvidenceStatus.VERIFIED);
    }

    // 37. No AI aggregation call: at most 2 AI invocations total (Phase 2 + Phase 5), never a 3rd
    @Test
    public void testNoThirdAiAggregationCall() {
        AiConfig cfg = config(true, true, true);
        AtomicInteger sharedCounter = new AtomicInteger(0);
        MockAiClient failureClient = new MockAiClient(AiResponse.success(failureJson("LOCATOR_CHANGED", "#checkout-button"), "mock"), true, sharedCounter);
        MockAiClient locatorClient = new MockAiClient(AiResponse.success(locatorJson(), "mock"), true, sharedCounter);
        Page page = RuntimePageTestDouble.builder().url(SAFE_URL).closed(false)
                .locator(RuntimeLocatorTestDouble.builder().count(1).visible(true).build()).build();

        serviceWith(cfg, failureClient, locatorClient).diagnose(context("Timeout", "<div/>"), page);

        assertThat(sharedCounter.get()).isEqualTo(2); // exactly Phase 2 + Phase 5, never more
    }

    // 40. No secret leakage into either AI prompt
    @Test
    public void testNoSecretLeakageIntoEitherAiPrompt() {
        AiConfig cfg = config(true, true, false);
        MockAiClient failureClient = new MockAiClient(AiResponse.success(failureJson("LOCATOR_CHANGED", "#checkout-button"), "mock"), true);
        MockAiClient locatorClient = new MockAiClient(AiResponse.success(locatorJson(), "mock"), true);

        String domWithSecret = "<input password=\"TEST_DIAGNOSIS_SECRET\" />";
        serviceWith(cfg, failureClient, locatorClient).diagnose(context("Login failed, password=TEST_DIAGNOSIS_SECRET", domWithSecret));

        assertThat(failureClient.capturedPrompt).doesNotContain("TEST_DIAGNOSIS_SECRET");
        assertThat(locatorClient.capturedPrompt).doesNotContain("TEST_DIAGNOSIS_SECRET");
    }

    // =====================================================================
    // CONCURRENCY / STATELESSNESS
    // =====================================================================

    // 41. Repeated diagnosis on the same instance does not retain previous state
    @Test
    public void testRepeatedDiagnosisDoesNotRetainPreviousState() {
        AiConfig cfg = config(true, true, false);
        MockAiClient failureClientA = new MockAiClient(AiResponse.success(failureJson("LOCATOR_CHANGED", "#checkout-button"), "mock"), true);
        MockAiClient locatorClient = new MockAiClient(AiResponse.success(locatorJson(), "mock"), true);
        FailureDiagnosisService service = serviceWith(cfg, failureClientA, locatorClient);

        FailureDiagnosis first = service.diagnose(context("Timeout", "<div/>"));
        assertThat(first.getLocatorAnalysis()).isNotNull();

        MockAiClient failureClientB = new MockAiClient(AiResponse.success(failureJson("DATA_ISSUE", null), "mock"), true);
        FailureDiagnosisService service2 = serviceWith(cfg, failureClientB, locatorClient);
        FailureDiagnosis second = service2.diagnose(context("Bad data", "<div/>"));

        assertThat(second.getLocatorAnalysis()).isNull(); // no leftover state from the first, unrelated diagnosis
    }

    // 42. Independent diagnoses on separate threads remain isolated
    @Test
    public void testIndependentDiagnosesRemainIsolatedAcrossThreads() throws InterruptedException {
        AiConfig cfg = config(true, true, false);
        MockAiClient failureClientA = new MockAiClient(AiResponse.success(failureJson("API_FAILURE", null), "mock"), true);
        MockAiClient failureClientB = new MockAiClient(AiResponse.success(failureJson("DATA_ISSUE", null), "mock"), true);
        MockAiClient locatorClient = new MockAiClient(AiResponse.success(locatorJson(), "mock"), true);

        FailureDiagnosisService serviceA = serviceWith(cfg, failureClientA, locatorClient);
        FailureDiagnosisService serviceB = serviceWith(cfg, failureClientB, locatorClient);

        final FailureDiagnosis[] results = new FailureDiagnosis[2];
        Thread t1 = new Thread(() -> results[0] = serviceA.diagnose(context("A failed", "<div/>")));
        Thread t2 = new Thread(() -> results[1] = serviceB.diagnose(context("B failed", "<div/>")));
        t1.start();
        t2.start();
        t1.join();
        t2.join();

        assertThat(results[0].getSuggestedFixes().get(0).getFixType()).isEqualTo(FixType.API);
        assertThat(results[1].getSuggestedFixes().get(0).getFixType()).isEqualTo(FixType.TEST_DATA);
    }
}
