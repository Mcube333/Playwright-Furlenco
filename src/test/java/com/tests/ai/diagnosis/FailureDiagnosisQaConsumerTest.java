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
import com.framework.ai.model.AiRequest;
import com.framework.ai.model.AiResponse;
import com.framework.ai.service.FailureAnalysisService;
import com.framework.config.ConfigManager;
import org.testng.ITestResult;
import org.testng.annotations.Test;

/**
 * Phase 7 Step 10: controlled QA-consumer validation.
 *
 * Where {@code FailureDiagnosisEndToEndTest} (Step 7) proves the chain is architecturally wired
 * correctly and {@code FailureDiagnosisAdversarialTest} (Step 8) proves it behaves safely under
 * adversarial/incomplete evidence, this file proves the thing Step 10 actually asks for: that a QA
 * engineer holding a failed {@link ITestResult} can explicitly consume
 * {@link FailureDiagnosisHelper} exactly as documented and get back something a human can read and
 * trust — without anything happening unless they ask for it.
 *
 * Deliberately small. Reuses the Step 6 {@code RecordingFailureDiagnosisService}/
 * {@code RecordingFailureDiagnosisReporter} and the Step 6/7 {@code FailureDiagnosisTestResultTestDouble}
 * rather than reinventing test infrastructure. No real network call, no real browser, no Mockito.
 */
public class FailureDiagnosisQaConsumerTest {

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

    private String checkoutFailureJson() {
        return "{\"summary\":\"The checkout button could not be located on the cart page\","
                + "\"rootCause\":\"The locator used by the test no longer matches any element\","
                + "\"category\":\"LOCATOR_CHANGED\","
                + "\"suggestedFix\":\"Update the checkout button locator to match the current DOM\","
                + "\"suggestedLocators\":[\"[data-testid='checkout-button']\"],"
                + "\"jiraBugReport\":\"Summary: checkout button locator broken\","
                + "\"confidenceScore\":0.72}";
    }

    private String checkoutLocatorAnalysisJson() {
        return "{\"targetElement\":\"Checkout button\",\"candidates\":["
                + "{\"locator\":\"[data-testid='checkout-button']\",\"strategy\":\"TEST_ID\",\"rationale\":\"stable test hook\"}"
                + "],\"accessibilityFindings\":[],\"assumptions\":[],\"missingEvidence\":[]}";
    }

    private FailureDiagnosisHelper realHelper(AiConfig cfg, AiClient failureClient, AiClient locatorClient,
                                               FailureDiagnosisReporter reporter) {
        // Runtime validation is deliberately not exercised from this consumer-level file (it is
        // already thoroughly proven in Step 7/8) — no Page is ever bound in these scenarios, so the
        // real, unmodified RuntimeLocatorValidator simply reports "no Page supplied" safely.
        FailureDiagnosisService service = new FailureDiagnosisService(cfg,
                new FailureAnalysisService(cfg, failureClient),
                new LocatorAnalysisService(cfg, locatorClient),
                new com.framework.ai.locatoradvisor.runtime.RuntimeLocatorValidator(
                        cfg, new com.framework.ai.locatoradvisor.runtime.RuntimeEnvironmentGuard(cfg)));
        return new FailureDiagnosisHelper(cfg, service, reporter);
    }

    // =====================================================================
    // STEP 10 — the single most important property: nothing happens unless explicitly invoked
    // =====================================================================

    @Test
    public void testWithoutExplicitInvocationNoDiagnosisAndNoReportOccur() {
        RecordingFailureDiagnosisService recordingService = new RecordingFailureDiagnosisService();
        RecordingFailureDiagnosisReporter recordingReporter = new RecordingFailureDiagnosisReporter();
        // A QA consumer holds a helper (e.g. injected/constructed as part of their own tooling) but
        // simply has not chosen to call it yet — nothing must happen just from its existence.
        new FailureDiagnosisHelper(new AiConfig(ConfigManager.getInstance()), recordingService, recordingReporter);

        assertThat(recordingService.callCount()).isZero();
        assertThat(recordingReporter.callCount()).isZero();
    }

    @Test
    public void testExplicitInvocationOfDiagnoseThenReportProducesExactlyOneEach() {
        RecordingFailureDiagnosisService recordingService = new RecordingFailureDiagnosisService();
        FailureDiagnosis canned = FailureDiagnosis.builder().build();
        recordingService.returnCanned(canned);
        RecordingFailureDiagnosisReporter recordingReporter = new RecordingFailureDiagnosisReporter();
        FailureDiagnosisHelper helper = new FailureDiagnosisHelper(
                new AiConfig(ConfigManager.getInstance()), recordingService, recordingReporter);

        ITestResult failedTest = FailureDiagnosisTestResultTestDouble.builder()
                .methodName("shouldProceedToCheckoutFromCart")
                .className("com.tests.web.furlenco.FurlencoCartFlowTest")
                .throwable(new com.microsoft.playwright.TimeoutError("Timeout 5000ms exceeded waiting for checkout button"))
                .build();

        // The consumer explicitly chooses to diagnose, then explicitly chooses to report.
        FailureDiagnosis diagnosis = helper.diagnose(failedTest);
        helper.report(diagnosis);

        assertThat(recordingService.callCount()).isEqualTo(1);
        assertThat(recordingReporter.callCount()).isEqualTo(1);
        assertThat(recordingReporter.lastDiagnosis()).isSameAs(canned);
    }

    // =====================================================================
    // Realistic consumer scenario: "the checkout button locator is suspected to have changed"
    // Mirrors the 9 QA-usability questions from the Step 10 brief directly in the assertions/comments.
    // =====================================================================

    @Test
    public void testQaConsumerCanDiagnoseAndUnderstandASuspectedCheckoutLocatorChange() {
        AiConfig cfg = config(true);
        StubAiClient failureClient = new StubAiClient(AiResponse.success(checkoutFailureJson(), "stub"));
        StubAiClient locatorClient = new StubAiClient(AiResponse.success(checkoutLocatorAnalysisJson(), "stub"));
        FailureDiagnosisReporter reporter = new FailureDiagnosisReporter();
        FailureDiagnosisHelper helper = realHelper(cfg, failureClient, locatorClient, reporter);

        ITestResult failedTest = FailureDiagnosisTestResultTestDouble.builder()
                .methodName("shouldProceedToCheckoutFromCart")
                .className("com.tests.web.furlenco.FurlencoCartFlowTest")
                .throwable(new com.microsoft.playwright.TimeoutError("Timeout 5000ms exceeded waiting for checkout button"))
                .startMillis(0L)
                .endMillis(5000L)
                .build();
        // No Page bound to this thread -> runtime validation will honestly be unavailable, exactly
        // as it would for a QA engineer diagnosing a failure after the browser session has ended.

        FailureDiagnosis diagnosis = helper.diagnose(failedTest);
        String report = reporter.buildMarkdownReport(diagnosis);

        // Q1/Q2 — Can QA understand why the test failed, and see what the AI thinks?
        assertThat(diagnosis.getAiAnalysis()).isNotNull();
        assertThat(report).contains("checkout button could not be located");

        // Q3 — Can QA see the locator candidate?
        assertThat(diagnosis.getLocatorAnalysis()).isNotNull();
        assertThat(diagnosis.getLocatorAnalysis().getCandidates()).isNotEmpty();
        assertThat(report).contains("[data-testid='checkout-button']");

        // Q4 — Can QA tell whether the locator is ACTUALLY verified (not just proposed)?
        // No DOM snapshot was supplied in this scenario (a QA engineer diagnosing after the fact,
        // with only the failure message and no captured DOM) -> the candidate is honestly UNVERIFIED.
        EvidenceStatus candidateStatus = diagnosis.getSuggestedFixes().get(0)
                .getRelatedLocatorCandidate().getEvidenceStatus();
        assertThat(candidateStatus).isEqualTo(EvidenceStatus.UNVERIFIED);
        assertThat(report).doesNotContain("Verified locator");
        assertThat(report).doesNotContain("This locator is confirmed correct");

        // Q5 — Can QA tell whether runtime validation happened? It did not (no Page) -> report must
        // say so plainly, never claim "Runtime validated."
        assertThat(diagnosis.getRuntimeValidation()).isNull();
        assertThat(report).contains("Runtime validation: Not available");
        assertThat(report).doesNotContain("Runtime validated.");

        // Q6 — Can QA see the suggested fix?
        assertThat(diagnosis.getSuggestedFixes()).isNotEmpty();
        assertThat(report).contains("## Suggested Fixes");

        // Q7 — Can QA distinguish AI inference from actual evidence? The candidate's evidence item
        // is labeled with its real, DOM-matcher-derived status, not the AI's confidence score.
        assertThat(diagnosis.getSuggestedFixes().get(0).getEvidenceItems())
                .allMatch(e -> e.getStatus() == EvidenceStatus.UNVERIFIED);

        // Q8/Q9 — Can QA see what's missing, and does the report avoid overstating certainty?
        assertThat(report).contains("## Evidence Disclaimer");
        assertThat(report).contains("advisory");
    }

    // =====================================================================
    // Convenience API: diagnoseAndReport must match the manual two-step path
    // =====================================================================

    @Test
    public void testDiagnoseAndReportConvenienceApiMatchesManualTwoStepWorkflow() {
        RecordingFailureDiagnosisService recordingService = new RecordingFailureDiagnosisService();
        FailureDiagnosis canned = FailureDiagnosis.builder().build();
        recordingService.returnCanned(canned);
        RecordingFailureDiagnosisReporter recordingReporter = new RecordingFailureDiagnosisReporter();
        FailureDiagnosisHelper helper = new FailureDiagnosisHelper(
                new AiConfig(ConfigManager.getInstance()), recordingService, recordingReporter);

        ITestResult failedTest = FailureDiagnosisTestResultTestDouble.builder().build();

        FailureDiagnosis result = helper.diagnoseAndReport(failedTest);

        assertThat(result).isSameAs(canned);
        assertThat(recordingService.callCount()).isEqualTo(1); // no duplicate diagnosis call
        assertThat(recordingReporter.callCount()).isEqualTo(1); // no duplicate report call
        assertThat(recordingReporter.lastDiagnosis()).isSameAs(canned);
    }

    // =====================================================================
    // AI-disabled consumer behavior
    // =====================================================================

    @Test
    public void testConsumerWorkflowRemainsSafeAndHonestWhenAiIsDisabled() {
        AiConfig cfg = config(false); // ai.enabled=false, globally unchanged by this test
        StubAiClient failureClient = new StubAiClient(AiResponse.failure("must never be called"));
        StubAiClient locatorClient = new StubAiClient(AiResponse.failure("must never be called"));
        FailureDiagnosisReporter reporter = new FailureDiagnosisReporter();
        FailureDiagnosisHelper helper = realHelper(cfg, failureClient, locatorClient, reporter);

        ITestResult failedTest = FailureDiagnosisTestResultTestDouble.builder()
                .throwable(new RuntimeException("Some failure"))
                .build();

        assertThatCode(() -> helper.diagnose(failedTest)).doesNotThrowAnyException();
        FailureDiagnosis diagnosis = helper.diagnose(failedTest);

        assertThat(diagnosis.getAiAnalysis()).isNull(); // no fabricated AI analysis
        assertThat(failureClient.capturedPrompt).isNull(); // confirms no external AI call occurred
        assertThat(locatorClient.capturedPrompt).isNull();

        String report = reporter.buildMarkdownReport(diagnosis);
        assertThatCode(() -> helper.report(diagnosis)).doesNotThrowAnyException();
        assertThat(report).contains("## AI Analysis");
    }

    // =====================================================================
    // Sensitive data must never reach the consumer-facing report
    // =====================================================================

    @Test
    public void testConsumerFacingReportNeverExposesAnyOfFiveSyntheticSensitiveValueKinds() {
        AiConfig cfg = config(true);
        StubAiClient failureClient = new StubAiClient(AiResponse.success(checkoutFailureJson(), "stub"));
        StubAiClient locatorClient = new StubAiClient(AiResponse.success(checkoutLocatorAnalysisJson(), "stub"));
        FailureDiagnosisReporter reporter = new FailureDiagnosisReporter();
        FailureDiagnosisHelper helper = realHelper(cfg, failureClient, locatorClient, reporter);

        String errorWithSecrets = "Login failed: authorization=FAKE_AUTH_9f2; cookie=FAKE_COOKIE_9f2; "
                + "session=FAKE_SESSION_9f2; token=FAKE_TOKEN_9f2; password=FAKE_PASSWORD_9f2";
        ITestResult failedTest = FailureDiagnosisTestResultTestDouble.builder()
                .throwable(new RuntimeException(errorWithSecrets))
                .build();

        FailureDiagnosis diagnosis = helper.diagnose(failedTest);
        String report = reporter.buildMarkdownReport(diagnosis);

        assertThat(report).doesNotContain("FAKE_AUTH_9f2", "FAKE_COOKIE_9f2", "FAKE_SESSION_9f2",
                "FAKE_TOKEN_9f2", "FAKE_PASSWORD_9f2");
    }

    // =====================================================================
    // Realistic degraded scenario: AI analysis succeeds, locator analysis fails, runtime validation
    // unavailable — the consumer must still receive the maximum safe, honest diagnosis available.
    // =====================================================================

    @Test
    public void testPartialDiagnosisWhenLocatorAnalysisFailsStillProducesAUsableConsumerReport() {
        AiConfig cfg = config(true);
        StubAiClient failureClient = new StubAiClient(AiResponse.success(checkoutFailureJson(), "stub"));
        StubAiClient throwingLocatorClient = new StubAiClient(new RuntimeException("Simulated Phase 5 provider outage"));
        FailureDiagnosisReporter reporter = new FailureDiagnosisReporter();
        FailureDiagnosisHelper helper = realHelper(cfg, failureClient, throwingLocatorClient, reporter);

        ITestResult failedTest = FailureDiagnosisTestResultTestDouble.builder()
                .throwable(new com.microsoft.playwright.TimeoutError("Timeout 5000ms exceeded"))
                .build();

        FailureDiagnosis diagnosis = assertThatCodeReturning(() -> helper.diagnose(failedTest));
        String report = reporter.buildMarkdownReport(diagnosis);

        // Maximum safe diagnosis: Phase 2's AI analysis is preserved even though Phase 5 failed.
        assertThat(diagnosis.getAiAnalysis()).isNotNull();
        assertThat(report).contains("## AI Analysis");

        // No fabricated locator evidence and no fabricated runtime validation.
        assertThat(diagnosis.getSuggestedFixes()).allMatch(f -> f.getRelatedLocatorCandidate() == null);
        assertThat(report).contains("Runtime validation: Not available");

        // The report must clearly communicate the unavailable section rather than staying silent
        // about it or fabricating content.
        assertThat(report).contains("## Locator Analysis");
    }

    private static FailureDiagnosis assertThatCodeReturning(java.util.concurrent.Callable<FailureDiagnosis> call) {
        try {
            FailureDiagnosis result = call.call();
            assertThat(result).isNotNull();
            return result;
        } catch (Exception e) {
            throw new AssertionError("Diagnosis must never throw for a QA consumer", e);
        }
    }
}
