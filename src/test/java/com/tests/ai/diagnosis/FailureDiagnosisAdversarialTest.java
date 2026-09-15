package com.tests.ai.diagnosis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.framework.ai.client.AiClient;
import com.framework.ai.codegeneration.EvidenceStatus;
import com.framework.ai.config.AiConfig;
import com.framework.ai.diagnosis.FailureDiagnosis;
import com.framework.ai.diagnosis.FailureDiagnosisReporter;
import com.framework.ai.diagnosis.FailureDiagnosisService;
import com.framework.ai.diagnosis.FixType;
import com.framework.ai.locatoradvisor.LocatorAnalysisService;
import com.framework.ai.locatoradvisor.ValidationType;
import com.framework.ai.locatoradvisor.runtime.RuntimeEnvironmentGuard;
import com.framework.ai.locatoradvisor.runtime.RuntimeLocatorValidator;
import com.framework.ai.model.AiRequest;
import com.framework.ai.model.AiResponse;
import com.framework.ai.model.FailureContext;
import com.framework.ai.service.FailureAnalysisService;
import com.framework.config.ConfigManager;
import org.testng.annotations.Test;

/**
 * Phase 7 Step 8: adversarial / hallucination-resistance validation of the diagnosis pipeline.
 *
 * This file deliberately does NOT re-prove scenarios the existing 338 AI tests already cover —
 * {@code FailureDiagnosisServiceTest} (42 tests: locator evidence variants, multi-candidate
 * selection, runtime validation, environment guarding, evidence non-promotion, fix classification,
 * secret non-leakage, concurrency), {@code FailureDiagnosisReporterTest} (43 tests: every rendering
 * and sanitization case), and {@code FailureDiagnosisEndToEndTest} (5 tests: the full Helper-driven
 * chain). It adds ONLY the gaps identified by mapping the Step 8 adversarial matrix against that
 * existing coverage:
 *
 * <ul>
 *   <li>the literal {@code FailureCategory.TIMEOUT} category (every existing locator-relevance test
 *       uses {@code LOCATOR_CHANGED}; the TIMEOUT-inclusion design decision itself, flagged as a
 *       residual risk in the Phase 7 Step 3 review, was never empirically exercised with that literal
 *       category value) — both with and without useful evidence, and the known, previously-disclosed
 *       limitation that a backend-leaning TIMEOUT message still triggers locator analysis (the gate
 *       checks category only, not message content);</li>
 *   <li>a genuinely malformed (unparseable) AI JSON response flowing through the full
 *       {@code FailureDiagnosisService} (existing coverage tests this at the isolated
 *       {@code FailureAnalysisService} unit level only, in {@code com.tests.ai.FailureAnalysisServiceTest});</li>
 *   <li>the {@code LocatorAnalysisService}'s own {@code AiClient} throwing (existing coverage only
 *       exercises the {@code RuntimeLocatorValidator} collaborator throwing) — this also empirically
 *       confirms the already-disclosed Step 3 finding that {@code LOCATOR_CHANGED} has no explicit
 *       case in {@code classifyFixType()} and falls through to {@code UNKNOWN};</li>
 *   <li>{@code ai.enabled=false} specifically (existing coverage only exercises
 *       {@code ai.failure.analysis.enabled=false});</li>
 *   <li>ambiguity remaining visible in the final rendered Markdown, not just in the
 *       {@code LocatorAnalysisResponse} object (existing multi-candidate coverage stops at the
 *       Service layer);</li>
 *   <li>one consolidated test making the "prefer incomplete truthful diagnosis over unsupported
 *       certainty" principle explicit in a single, clearly-labeled scenario, per Step 8's request.</li>
 * </ul>
 *
 * Hermetic throughout: only {@link AiClient} is a hand-written stub (same convention as
 * {@code FailureDiagnosisServiceTest}); no real network call, no real browser, no Mockito.
 */
public class FailureDiagnosisAdversarialTest {

    private static class StubAiClient implements AiClient {
        private final AiResponse response;
        private final RuntimeException throwsException;
        String capturedPrompt;

        StubAiClient(AiResponse response) {
            this.response = response;
            this.throwsException = null;
        }

        StubAiClient(RuntimeException throwsException) {
            this.response = null;
            this.throwsException = throwsException;
        }

        @Override
        public AiResponse generate(AiRequest request) {
            this.capturedPrompt = request.getPrompt();
            if (throwsException != null) {
                throw throwsException;
            }
            return response;
        }

        @Override public String getProviderName() { return "stub"; }
        @Override public boolean isAvailable() { return true; }
    }

    private AiConfig config(boolean aiEnabled) {
        return new AiConfig(ConfigManager.getInstance()) {
            @Override public boolean isAiEnabled() { return aiEnabled; }
            @Override public String getApiKey() { return "fake-api-key"; }
            @Override public boolean isFailureAnalysisEnabled() { return true; }
            @Override public boolean isLocatorRuntimeValidationEnabled() { return false; }
        };
    }

    private FailureContext context(String errorMessage, String dom) {
        return FailureContext.builder()
                .testName("testCheckout")
                .testClass("com.tests.web.furlenco.FurlencoCartFlowTest")
                .errorMessage(errorMessage)
                .stackTrace("com.microsoft.playwright.TimeoutError: Timeout 5000ms exceeded")
                .currentUrl("https://www.stag.furlenco.com/cart")
                .pageTitle("Cart")
                .domSnippet(dom)
                .executionDurationMs(5000)
                .environment("gemini")
                .build();
    }

    private String failureJson(String category, String suggestedLocator, String rootCause) {
        return "{\"summary\":\"Checkout button could not be clicked\","
                + "\"rootCause\":\"" + rootCause + "\","
                + "\"category\":\"" + category + "\","
                + "\"suggestedFix\":\"Investigate further\","
                + "\"suggestedLocators\":" + (suggestedLocator == null ? "[]" : "[\"" + suggestedLocator + "\"]")
                + ",\"jiraBugReport\":\"Summary: checkout button broken\","
                + "\"confidenceScore\":0.7}";
    }

    private String locatorJsonWithCandidate(String locator) {
        return "{\"targetElement\":\"Checkout button\",\"candidates\":["
                + "{\"locator\":\"" + locator + "\",\"strategy\":\"TEST_ID\",\"rationale\":\"stable\"}"
                + "],\"accessibilityFindings\":[],\"assumptions\":[],\"missingEvidence\":[]}";
    }

    private FailureDiagnosisService serviceWith(AiConfig cfg, AiClient failureClient, AiClient locatorClient) {
        return new FailureDiagnosisService(cfg,
                new FailureAnalysisService(cfg, failureClient),
                new LocatorAnalysisService(cfg, locatorClient),
                new RuntimeLocatorValidator(cfg, new RuntimeEnvironmentGuard(cfg)));
    }

    // =====================================================================
    // E / F / G — TIMEOUT category locator-relevance behavior
    // =====================================================================

    // E. TIMEOUT with useful DOM/suggested-locator evidence triggers locator analysis.
    @Test
    public void testTimeoutCategoryWithUsefulEvidenceTriggersLocatorAnalysis() {
        AiConfig cfg = config(true);
        StubAiClient failureClient = new StubAiClient(AiResponse.success(
                failureJson("TIMEOUT", "[data-testid='checkout-button']", "Timed out waiting for the checkout button"), "stub"));
        StubAiClient locatorClient = new StubAiClient(AiResponse.success(
                locatorJsonWithCandidate("[data-testid='checkout-button']"), "stub"));

        FailureDiagnosis diagnosis = serviceWith(cfg, failureClient, locatorClient)
                .diagnose(context("Timeout", "<button data-testid=\"checkout-button\">Checkout</button>"));

        assertThat(diagnosis.getAiAnalysis().getCategory().name()).isEqualTo("TIMEOUT");
        assertThat(locatorClient.capturedPrompt).isNotNull(); // locator analysis WAS invoked
        assertThat(diagnosis.getLocatorAnalysis()).isNotNull();
    }

    // F. TIMEOUT with no useful evidence (no DOM, no suggested locator) skips locator analysis and
    //    falls back to the classifier's own conservative TIMEOUT->WAIT default.
    @Test
    public void testTimeoutCategoryWithoutUsefulEvidenceSkipsLocatorAnalysisAndClassifiesAsWait() {
        AiConfig cfg = config(true);
        StubAiClient failureClient = new StubAiClient(AiResponse.success(
                failureJson("TIMEOUT", null, "The page took too long to respond"), "stub"));
        StubAiClient locatorClient = new StubAiClient(AiResponse.success(locatorJsonWithCandidate("irrelevant"), "stub"));

        FailureDiagnosis diagnosis = serviceWith(cfg, failureClient, locatorClient)
                .diagnose(context("Timeout", "")); // no DOM, no suggested locator -> no useful evidence

        assertThat(diagnosis.getLocatorAnalysis()).isNull();
        assertThat(locatorClient.capturedPrompt).isNull(); // never invoked
        assertThat(diagnosis.getSuggestedFixes()).hasSize(1);
        assertThat(diagnosis.getSuggestedFixes().get(0).getFixType()).isEqualTo(FixType.WAIT);
    }

    // G. KNOWN, DISCLOSED LIMITATION (Step 3 review): the locator-relevance gate checks only the
    //    AI's chosen category, not the failure message content — so a TIMEOUT categorized failure
    //    whose own text reads as backend/API-shaped still triggers locator analysis if a locator and
    //    DOM are present. This test documents and pins down that EXISTING, accepted behavior; it is
    //    not a defect discovered by Step 8 and is not something this step fixes (fixing it would be
    //    a Phase 7 Step 3 production change, explicitly out of scope here).
    @Test
    public void testTimeoutCategoryStillTriggersLocatorAnalysisForBackendLeaningMessageKnownLimitation() {
        AiConfig cfg = config(true);
        StubAiClient failureClient = new StubAiClient(AiResponse.success(
                failureJson("TIMEOUT", "[data-testid='checkout-button']",
                        "The checkout API gateway did not respond before the timeout elapsed"), "stub"));
        StubAiClient locatorClient = new StubAiClient(AiResponse.success(
                locatorJsonWithCandidate("[data-testid='checkout-button']"), "stub"));

        FailureDiagnosis diagnosis = serviceWith(cfg, failureClient, locatorClient)
                .diagnose(context("Timeout", "<button data-testid=\"checkout-button\">Checkout</button>"));

        // Current, disclosed behavior: category alone gates locator relevance, so this backend-
        // leaning TIMEOUT still runs locator analysis. Documented here, not silently "fixed".
        assertThat(diagnosis.getLocatorAnalysis()).isNotNull();
    }

    // =====================================================================
    // K — malformed AI response through the FULL diagnosis chain
    // =====================================================================

    @Test
    public void testMalformedAiJsonResponseFlowsSafelyThroughFullDiagnosis() {
        AiConfig cfg = config(true);
        StubAiClient failureClient = new StubAiClient(AiResponse.success("{ this is not valid JSON at all", "stub"));
        StubAiClient locatorClient = new StubAiClient(AiResponse.failure("should never be called"));
        FailureDiagnosisService service = serviceWith(cfg, failureClient, locatorClient);
        FailureContext failureContext = context("Timeout", "<div/>");

        assertThatCode(() -> service.diagnose(failureContext))
                .describedAs("malformed AI JSON must never escape as an exception")
                .doesNotThrowAnyException();

        FailureDiagnosis diagnosis = service.diagnose(failureContext);

        assertThat(diagnosis.getAiAnalysis()).isNull(); // honest absence, not a fabricated analysis
        assertThat(diagnosis.getSuggestedFixes()).isEmpty();
        assertThat(locatorClient.capturedPrompt).isNull(); // never reached without a usable AI analysis
    }

    // =====================================================================
    // L — LocatorAnalysisService's own AiClient throwing is isolated
    // =====================================================================

    @Test
    public void testLocatorAnalysisAiClientThrowsIsIsolatedFromRestOfDiagnosis() {
        AiConfig cfg = config(true);
        StubAiClient failureClient = new StubAiClient(AiResponse.success(
                failureJson("LOCATOR_CHANGED", "[data-testid='checkout-button']", "The locator did not resolve"), "stub"));
        StubAiClient throwingLocatorClient = new StubAiClient(new RuntimeException("Simulated Phase 5 provider outage"));

        FailureDiagnosis diagnosis = serviceWith(cfg, failureClient, throwingLocatorClient)
                .diagnose(context("Timeout", "<button data-testid=\"checkout-button\">Checkout</button>"));

        // Phase 2's result is preserved even though Phase 5 failed.
        assertThat(diagnosis.getAiAnalysis()).isNotNull();
        // LocatorAnalysisService's own try/catch turns the thrown exception into a non-null,
        // unsuccessful response (never a fabricated candidate).
        assertThat(diagnosis.getLocatorAnalysis()).isNotNull();
        assertThat(diagnosis.getLocatorAnalysis().isSuccess()).isFalse();
        assertThat(diagnosis.getSuggestedFixes()).hasSize(1);
        assertThat(diagnosis.getSuggestedFixes().get(0).getRelatedLocatorCandidate()).isNull(); // no fabricated candidate
        // Confirms the already-disclosed Step 3 finding: LOCATOR_CHANGED has no explicit case in
        // classifyFixType()'s category switch, so it conservatively falls through to UNKNOWN here.
        assertThat(diagnosis.getSuggestedFixes().get(0).getFixType()).isEqualTo(FixType.UNKNOWN);
    }

    // =====================================================================
    // N — ai.enabled=false specifically (distinct from failure-analysis-disabled)
    // =====================================================================

    @Test
    public void testAiEnabledFalseProducesNoAnalysisAndNoAiCallAtDiagnosisLevel() {
        AiConfig cfg = config(false); // ai.enabled=false
        StubAiClient failureClient = new StubAiClient(AiResponse.failure("must never be called"));
        StubAiClient locatorClient = new StubAiClient(AiResponse.failure("must never be called"));

        FailureDiagnosis diagnosis = serviceWith(cfg, failureClient, locatorClient)
                .diagnose(context("Timeout", "<div/>"));

        assertThat(diagnosis.getAiAnalysis()).isNull();
        assertThat(diagnosis.getSuggestedFixes()).isEmpty();
        assertThat(failureClient.capturedPrompt).isNull(); // no external AI call was made
        assertThat(locatorClient.capturedPrompt).isNull();
    }

    // =====================================================================
    // D — ambiguity remains visible in the FINAL RENDERED MARKDOWN, not just the response object
    // =====================================================================

    @Test
    public void testAmbiguousMultipleLocatorsRemainVisibleInFinalMarkdown() {
        AiConfig cfg = config(true);
        StubAiClient failureClient = new StubAiClient(AiResponse.success(
                failureJson("LOCATOR_CHANGED", "[data-testid='checkout']", "Ambiguous match"), "stub"));
        String multiCandidateJson = "{\"targetElement\":\"Checkout button\",\"candidates\":["
                + "{\"locator\":\"[data-testid='checkout']\",\"strategy\":\"TEST_ID\",\"rationale\":\"stable\"},"
                + "{\"locator\":\".checkout-btn\",\"strategy\":\"CSS_STABLE\",\"rationale\":\"fallback\"}"
                + "],\"accessibilityFindings\":[],\"assumptions\":[],\"missingEvidence\":[]}";
        StubAiClient locatorClient = new StubAiClient(AiResponse.success(multiCandidateJson, "stub"));
        String dom = "<button data-testid=\"checkout\">Checkout</button><button class=\"checkout-btn\">Checkout</button>";

        FailureDiagnosis diagnosis = serviceWith(cfg, failureClient, locatorClient).diagnose(context("Timeout", dom));

        assertThat(diagnosis.getLocatorAnalysis().getCandidates()).hasSizeGreaterThan(1);

        String markdown = new FailureDiagnosisReporter().buildMarkdownReport(diagnosis);
        // Both candidates must be individually visible in the rendered report — ambiguity is shown,
        // never silently collapsed into a single confident claim.
        assertThat(markdown).contains("[data-testid='checkout']");
        assertThat(markdown).contains(".checkout-btn");
    }

    // =====================================================================
    // STEP 4 — explicit, consolidated hallucination-resistance assertions
    // =====================================================================

    @Test
    public void testHallucinationResistancePrinciplesInOneScenario() {
        AiConfig cfg = config(true); // runtime validation disabled, no Page supplied -> no runtime check at all
        StubAiClient failureClient = new StubAiClient(AiResponse.success(
                failureJson("LOCATOR_CHANGED", "[data-testid='checkout-button']", "The locator did not resolve"), "stub"));
        StubAiClient locatorClient = new StubAiClient(AiResponse.success(
                locatorJsonWithCandidate("[data-testid='checkout-button']"), "stub"));

        // DOM present but does not contain the suggested locator -> genuinely unverifiable.
        FailureDiagnosis diagnosis = serviceWith(cfg, failureClient, locatorClient)
                .diagnose(context("Timeout", "<div>no matching element here</div>"));

        // Principle: never upgrade UNVERIFIED to VERIFIED merely because the AI proposed a locator.
        assertThat(diagnosis.getSuggestedFixes().get(0).getRelatedLocatorCandidate().getEvidenceStatus())
                .isEqualTo(EvidenceStatus.UNVERIFIED);
        assertThat(diagnosis.getSuggestedFixes().get(0).getRelatedLocatorCandidate().getValidationType())
                .isEqualTo(ValidationType.DOM_MATCHED); // evaluated, not "not validated" — genuinely checked and found absent

        // Principle: never claim runtime validation occurred when no runtime check was ever made.
        assertThat(diagnosis.getRuntimeValidation()).isNull();

        // Principle: the evidence item attached to the suggested fix reflects the SAME UNVERIFIED
        // status, never a more confident one manufactured for the fix description.
        assertThat(diagnosis.getSuggestedFixes().get(0).getEvidenceItems())
                .allMatch(e -> e.getStatus() != EvidenceStatus.VERIFIED);

        // Principle: the rendered report shows this honestly — no claim of certainty for an
        // unverified candidate, and no "Runtime Validation" content beyond "Not available".
        String markdown = new FailureDiagnosisReporter().buildMarkdownReport(diagnosis);
        assertThat(markdown).contains("Runtime validation: Not available");
        assertThat(markdown).doesNotContain("| VERIFIED |"); // no candidate/evidence row falsely claims VERIFIED
    }
}
