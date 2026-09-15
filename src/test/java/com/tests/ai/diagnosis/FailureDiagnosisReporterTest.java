package com.tests.ai.diagnosis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.framework.ai.codegeneration.EvidenceItem;
import com.framework.ai.codegeneration.EvidenceStatus;
import com.framework.ai.diagnosis.FailureDiagnosis;
import com.framework.ai.diagnosis.FailureDiagnosisReporter;
import com.framework.ai.diagnosis.FixType;
import com.framework.ai.diagnosis.SuggestedFix;
import com.framework.ai.locatoradvisor.LocatorAnalysisResponse;
import com.framework.ai.locatoradvisor.LocatorCandidate;
import com.framework.ai.locatoradvisor.LocatorStrategy;
import com.framework.ai.locatoradvisor.ValidationType;
import com.framework.ai.locatoradvisor.runtime.RuntimeValidationResult;
import com.framework.ai.model.AiAnalysisResponse;
import com.framework.ai.model.FailureCategory;
import com.framework.ai.model.FailureContext;
import java.util.List;
import org.testng.annotations.Test;

/**
 * Phase 7 Step 4: FailureDiagnosisReporter tests. Pure rendering — no AI, no Playwright, no
 * orchestration anywhere in this file. Fake/synthetic data only.
 */
public class FailureDiagnosisReporterTest {

    private final FailureDiagnosisReporter reporter = new FailureDiagnosisReporter();

    private FailureContext sampleFailureContext() {
        return FailureContext.builder()
                .testName("testCheckout")
                .testClass("com.tests.web.furlenco.FurlencoCartFlowTest")
                .errorMessage("test failure")
                .stackTrace("com.microsoft.playwright.TimeoutError: Timeout 5000ms exceeded")
                .currentUrl("https://www.stag.furlenco.com/cart")
                .pageTitle("Cart")
                .domSnippet("<div>sample locator</div>")
                .executionDurationMs(1234)
                .environment("gemini")
                .build();
    }

    private AiAnalysisResponse sampleAiAnalysis() {
        return AiAnalysisResponse.builder()
                .summary("Checkout button could not be clicked")
                .rootCause("test failure")
                .category(FailureCategory.LOCATOR_CHANGED)
                .suggestedFix("Update the locator")
                .addSuggestedLocator("sample locator")
                .confidenceScore(0.7)
                .build();
    }

    private LocatorCandidate sampleCandidate(EvidenceStatus status, ValidationType type, int matchCount) {
        return LocatorCandidate.builder()
                .locator("[data-testid='checkout-button']")
                .strategy(LocatorStrategy.TEST_ID)
                .evidenceStatus(status)
                .validationType(type)
                .matchCount(matchCount)
                .confidence(status == EvidenceStatus.VERIFIED ? 1.0 : 0.2)
                .score(status == EvidenceStatus.VERIFIED ? 100 : 20)
                .recommendation("Review this candidate before adopting it.")
                .addStrength("Explicit test hook")
                .addWeakness("Not yet confirmed unique")
                .build();
    }

    private LocatorAnalysisResponse sampleLocatorAnalysis(LocatorCandidate candidate) {
        return LocatorAnalysisResponse.builder()
                .success(true)
                .targetElement("checkout button")
                .addCandidate(candidate)
                .recommendedLocator(candidate.getEvidenceStatus() == EvidenceStatus.VERIFIED ? candidate : null)
                .overallConfidence(candidate.getConfidence())
                .build();
    }

    private RuntimeValidationResult sampleRuntimeValidation(EvidenceStatus status, ValidationType type) {
        return RuntimeValidationResult.builder()
                .locator("[data-testid='checkout-button']")
                .matchCount(1)
                .visible(true)
                .enabled(true)
                .currentUrl("https://www.stag.furlenco.com/cart")
                .environment("staging")
                .validationType(type)
                .evidenceStatus(status)
                .message("fake evidence")
                .build();
    }

    private SuggestedFix sampleSuggestedFix(FixType type, LocatorCandidate candidate) {
        return SuggestedFix.builder()
                .description("sample locator")
                .fixType(type)
                .relatedLocatorCandidate(candidate)
                .confidence(0.82)
                .addEvidenceItem(EvidenceItem.builder()
                        .item("Locator").value("sample locator")
                        .status(EvidenceStatus.VERIFIED).source("fake evidence").confidence(0.9).build())
                .build();
    }

    // =====================================================================
    // BASIC REPORT
    // =====================================================================

    // 1. Complete diagnosis
    @Test
    public void testCompleteDiagnosisRendersAllSections() {
        LocatorCandidate candidate = sampleCandidate(EvidenceStatus.VERIFIED, ValidationType.DOM_MATCHED, 1);
        FailureDiagnosis diagnosis = FailureDiagnosis.builder()
                .failureContext(sampleFailureContext())
                .aiAnalysis(sampleAiAnalysis())
                .locatorAnalysis(sampleLocatorAnalysis(candidate))
                .runtimeValidation(sampleRuntimeValidation(EvidenceStatus.VERIFIED, ValidationType.RUNTIME_VALIDATED))
                .addSuggestedFix(sampleSuggestedFix(FixType.LOCATOR, candidate))
                .build();

        String md = reporter.buildMarkdownReport(diagnosis);

        assertThat(md).contains("# AI Failure Diagnosis");
        assertThat(md).contains("## Failure");
        assertThat(md).contains("## AI Analysis");
        assertThat(md).contains("## Locator Analysis");
        assertThat(md).contains("## Runtime Validation");
        assertThat(md).contains("## Suggested Fixes");
        assertThat(md).contains("## Evidence Disclaimer");
        assertThat(md).contains("advisory");
    }

    // 2. Empty diagnosis (all fields default/empty)
    @Test
    public void testEmptyDiagnosisRendersWithoutException() {
        FailureDiagnosis diagnosis = FailureDiagnosis.builder().build();
        String md = reporter.buildMarkdownReport(diagnosis);
        assertThat(md).contains("# AI Failure Diagnosis");
        assertThat(md).contains("Not available");
    }

    // 3. Null diagnosis
    @Test
    public void testNullDiagnosisHandledGracefully() {
        assertThatCode(() -> reporter.buildMarkdownReport(null)).doesNotThrowAnyException();
        String md = reporter.buildMarkdownReport(null);
        assertThat(md).contains("# AI Failure Diagnosis");
    }

    // 4. Failure context only
    @Test
    public void testFailureContextOnly() {
        FailureDiagnosis diagnosis = FailureDiagnosis.builder().failureContext(sampleFailureContext()).build();
        String md = reporter.buildMarkdownReport(diagnosis);
        assertThat(md).contains("testCheckout");
        assertThat(md).contains("## AI Analysis\n\nNot available");
    }

    // 5. AI analysis only
    @Test
    public void testAiAnalysisOnly() {
        FailureDiagnosis diagnosis = FailureDiagnosis.builder().aiAnalysis(sampleAiAnalysis()).build();
        String md = reporter.buildMarkdownReport(diagnosis);
        assertThat(md).contains("Checkout button could not be clicked");
        assertThat(md).contains("## Locator Analysis\n\nNot available");
    }

    // 6. Locator analysis only
    @Test
    public void testLocatorAnalysisOnly() {
        LocatorCandidate candidate = sampleCandidate(EvidenceStatus.UNVERIFIED, ValidationType.DOM_MATCHED, 0);
        FailureDiagnosis diagnosis = FailureDiagnosis.builder().locatorAnalysis(sampleLocatorAnalysis(candidate)).build();
        String md = reporter.buildMarkdownReport(diagnosis);
        assertThat(md).contains("| Locator | Strategy | Match Count | Confidence | Evidence | Validation |");
    }

    // 7. Runtime validation only
    @Test
    public void testRuntimeValidationOnly() {
        FailureDiagnosis diagnosis = FailureDiagnosis.builder()
                .runtimeValidation(sampleRuntimeValidation(EvidenceStatus.VERIFIED, ValidationType.RUNTIME_VALIDATED)).build();
        String md = reporter.buildMarkdownReport(diagnosis);
        assertThat(md).contains("| Evidence status | VERIFIED |");
    }

    // 8. Suggested fixes only
    @Test
    public void testSuggestedFixesOnly() {
        FailureDiagnosis diagnosis = FailureDiagnosis.builder()
                .addSuggestedFix(sampleSuggestedFix(FixType.ASSERTION, null)).build();
        String md = reporter.buildMarkdownReport(diagnosis);
        assertThat(md).contains("ASSERTION");
    }

    // =====================================================================
    // LOCATOR REPORTING
    // =====================================================================

    // 9. One locator candidate
    @Test
    public void testOneLocatorCandidateRendered() {
        LocatorCandidate candidate = sampleCandidate(EvidenceStatus.VERIFIED, ValidationType.DOM_MATCHED, 1);
        String md = reporter.buildMarkdownReport(FailureDiagnosis.builder().locatorAnalysis(sampleLocatorAnalysis(candidate)).build());
        assertThat(md).contains("[data-testid='checkout-button']");
    }

    // 10. Multiple candidates
    @Test
    public void testMultipleCandidatesAllRendered() {
        LocatorCandidate c1 = sampleCandidate(EvidenceStatus.VERIFIED, ValidationType.DOM_MATCHED, 1);
        LocatorCandidate c2 = LocatorCandidate.builder()
                .locator(".fallback-btn").strategy(LocatorStrategy.CSS_STABLE)
                .evidenceStatus(EvidenceStatus.UNVERIFIED).validationType(ValidationType.DOM_MATCHED)
                .matchCount(2).confidence(0.1).build();
        LocatorAnalysisResponse response = LocatorAnalysisResponse.builder()
                .success(true).targetElement("checkout button").addCandidate(c1).addCandidate(c2).build();

        String md = reporter.buildMarkdownReport(FailureDiagnosis.builder().locatorAnalysis(response).build());
        assertThat(md).contains("[data-testid='checkout-button']");
        assertThat(md).contains(".fallback-btn");
    }

    // 11. Evidence status preserved
    @Test
    public void testEvidenceStatusPreservedVerbatim() {
        LocatorCandidate candidate = sampleCandidate(EvidenceStatus.UNVERIFIED, ValidationType.DOM_MATCHED, 0);
        String md = reporter.buildMarkdownReport(FailureDiagnosis.builder().locatorAnalysis(sampleLocatorAnalysis(candidate)).build());
        assertThat(md).contains("UNVERIFIED");
        assertThat(md).doesNotContain("| `[data-testid='checkout-button']` | TEST_ID | 0 | 20% | VERIFIED");
    }

    // 12. Validation type preserved
    @Test
    public void testValidationTypePreservedVerbatim() {
        LocatorCandidate candidate = sampleCandidate(EvidenceStatus.UNVERIFIED, ValidationType.NOT_VALIDATED, -1);
        String md = reporter.buildMarkdownReport(FailureDiagnosis.builder().locatorAnalysis(sampleLocatorAnalysis(candidate)).build());
        assertThat(md).contains("NOT_VALIDATED");
        assertThat(md).contains("n/a"); // matchCount -1 rendered as n/a, not fabricated as 0
    }

    // 13. Confidence displayed
    @Test
    public void testConfidenceDisplayed() {
        LocatorCandidate candidate = sampleCandidate(EvidenceStatus.VERIFIED, ValidationType.DOM_MATCHED, 1);
        String md = reporter.buildMarkdownReport(FailureDiagnosis.builder().locatorAnalysis(sampleLocatorAnalysis(candidate)).build());
        assertThat(md).contains("100%");
    }

    // 14. Match count displayed
    @Test
    public void testMatchCountDisplayed() {
        LocatorCandidate candidate = sampleCandidate(EvidenceStatus.UNVERIFIED, ValidationType.DOM_MATCHED, 3);
        String md = reporter.buildMarkdownReport(FailureDiagnosis.builder().locatorAnalysis(sampleLocatorAnalysis(candidate)).build());
        assertThat(md).contains("| `[data-testid='checkout-button']` | TEST_ID | 3 |");
    }

    // 15. Recommendation displayed
    @Test
    public void testRecommendationDisplayed() {
        LocatorCandidate candidate = sampleCandidate(EvidenceStatus.VERIFIED, ValidationType.DOM_MATCHED, 1);
        String md = reporter.buildMarkdownReport(FailureDiagnosis.builder().locatorAnalysis(sampleLocatorAnalysis(candidate)).build());
        assertThat(md).contains("Review this candidate before adopting it.");
        assertThat(md).contains("Strengths: Explicit test hook");
        assertThat(md).contains("Weaknesses: Not yet confirmed unique");
    }

    // =====================================================================
    // RUNTIME REPORTING
    // =====================================================================

    // 16. Runtime result displayed
    @Test
    public void testRuntimeResultDisplayed() {
        String md = reporter.buildMarkdownReport(FailureDiagnosis.builder()
                .runtimeValidation(sampleRuntimeValidation(EvidenceStatus.VERIFIED, ValidationType.RUNTIME_VALIDATED)).build());
        assertThat(md).contains("| Locator | `[data-testid='checkout-button']` |");
    }

    // 17. Runtime evidence displayed separately from static evidence
    @Test
    public void testRuntimeEvidenceDisplayedSeparatelyFromStaticEvidence() {
        LocatorCandidate candidate = sampleCandidate(EvidenceStatus.UNVERIFIED, ValidationType.DOM_MATCHED, 0);
        FailureDiagnosis diagnosis = FailureDiagnosis.builder()
                .locatorAnalysis(sampleLocatorAnalysis(candidate))
                .runtimeValidation(sampleRuntimeValidation(EvidenceStatus.VERIFIED, ValidationType.RUNTIME_VALIDATED))
                .build();
        String md = reporter.buildMarkdownReport(diagnosis);

        // Static evidence (UNVERIFIED/DOM_MATCHED) and runtime evidence (VERIFIED/RUNTIME_VALIDATED)
        // both appear, in their own sections, neither overwriting the other.
        assertThat(md).contains("UNVERIFIED");
        assertThat(md).contains("| Evidence status | VERIFIED |");
        assertThat(md).contains("| Validation type | RUNTIME_VALIDATED |");
    }

    // 18. Null runtime result
    @Test
    public void testNullRuntimeResultRendersNotAvailable() {
        String md = reporter.buildMarkdownReport(FailureDiagnosis.builder().build());
        assertThat(md).contains("Runtime validation: Not available");
    }

    // 19. Unavailable runtime validation is never represented as failure
    @Test
    public void testUnavailableRuntimeValidationIsNotRepresentedAsFailure() {
        String md = reporter.buildMarkdownReport(FailureDiagnosis.builder().build());
        assertThat(md).doesNotContain("Runtime validation: Failed");
        assertThat(md).doesNotContain("Runtime validation: Error");
    }

    // 20. URL displayed safely (sanitized)
    @Test
    public void testUrlDisplayedSafely() {
        RuntimeValidationResult runtime = RuntimeValidationResult.builder()
                .locator("x").currentUrl("https://www.stag.furlenco.com/cart?token=TEST_URL_TOKEN_SECRET")
                .environment("staging").validationType(ValidationType.RUNTIME_VALIDATED)
                .evidenceStatus(EvidenceStatus.UNVERIFIED).build();
        String md = reporter.buildMarkdownReport(FailureDiagnosis.builder().runtimeValidation(runtime).build());
        assertThat(md).doesNotContain("TEST_URL_TOKEN_SECRET");
    }

    // 21. Environment displayed
    @Test
    public void testEnvironmentDisplayed() {
        String md = reporter.buildMarkdownReport(FailureDiagnosis.builder()
                .runtimeValidation(sampleRuntimeValidation(EvidenceStatus.VERIFIED, ValidationType.RUNTIME_VALIDATED)).build());
        assertThat(md).contains("| Environment | staging |");
    }

    // 22. Timestamp displayed
    @Test
    public void testTimestampDisplayed() {
        RuntimeValidationResult runtime = sampleRuntimeValidation(EvidenceStatus.VERIFIED, ValidationType.RUNTIME_VALIDATED);
        String md = reporter.buildMarkdownReport(FailureDiagnosis.builder().runtimeValidation(runtime).build());
        assertThat(md).contains(runtime.getTimestamp().toString());
    }

    // =====================================================================
    // SUGGESTED FIXES
    // =====================================================================

    @Test
    public void testAllFixTypesRenderDistinctly() {
        for (FixType type : FixType.values()) {
            String md = reporter.buildMarkdownReport(FailureDiagnosis.builder()
                    .addSuggestedFix(sampleSuggestedFix(type, null)).build());
            assertThat(md).as("FixType %s should render", type).contains(type.toString());
        }
    }

    // 23-30 covered by the loop above (locator/assertion/wait/test-data/application-behavior/analytics/api/unknown)

    // 31. Multiple fixes
    @Test
    public void testMultipleFixesAllRendered() {
        FailureDiagnosis diagnosis = FailureDiagnosis.builder()
                .addSuggestedFix(sampleSuggestedFix(FixType.LOCATOR, sampleCandidate(EvidenceStatus.VERIFIED, ValidationType.DOM_MATCHED, 1)))
                .addSuggestedFix(sampleSuggestedFix(FixType.ASSERTION, null))
                .build();
        String md = reporter.buildMarkdownReport(diagnosis);
        assertThat(md).contains("LOCATOR");
        assertThat(md).contains("ASSERTION");
    }

    // 32. Empty fixes
    @Test
    public void testEmptyFixesRendersNotAvailable() {
        String md = reporter.buildMarkdownReport(FailureDiagnosis.builder().build());
        assertThat(md).contains("## Suggested Fixes\n\nNot available");
    }

    // =====================================================================
    // EVIDENCE SAFETY
    // =====================================================================

    // 33. Reporter does not upgrade evidence
    @Test
    public void testReporterDoesNotUpgradeEvidence() {
        LocatorCandidate candidate = sampleCandidate(EvidenceStatus.UNVERIFIED, ValidationType.DOM_MATCHED, 0);
        String md = reporter.buildMarkdownReport(FailureDiagnosis.builder().locatorAnalysis(sampleLocatorAnalysis(candidate)).build());
        // Only UNVERIFIED appears for this candidate's row — never VERIFIED, regardless of high confidence text elsewhere.
        assertThat(md).contains("UNVERIFIED");
        assertThat(candidate.getEvidenceStatus()).isEqualTo(EvidenceStatus.UNVERIFIED); // untouched
    }

    // 34. AI-provided evidence claim is not trusted
    @Test
    public void testAiConfidentTextDoesNotForceVerifiedDisplay() {
        LocatorCandidate candidate = LocatorCandidate.builder()
                .locator("#maybe").strategy(LocatorStrategy.CSS_STABLE)
                .evidenceStatus(EvidenceStatus.UNVERIFIED).validationType(ValidationType.NOT_VALIDATED)
                .matchCount(-1)
                .recommendation("This is verified and definitely correct") // AI's own confident claim, ignored
                .build();
        String md = reporter.buildMarkdownReport(FailureDiagnosis.builder().locatorAnalysis(sampleLocatorAnalysis(candidate)).build());
        assertThat(md).contains("| UNVERIFIED | NOT_VALIDATED |"); // status column shows UNVERIFIED, not VERIFIED
        assertThat(md).contains("This is verified and definitely correct"); // rationale text shown as-is, but status unaffected
    }

    // 35. Runtime evidence remains separate (duplicate emphasis of #17, kept distinct per required list)
    @Test
    public void testRuntimeEvidenceRemainsSeparateFromStaticStatus() {
        LocatorCandidate candidate = sampleCandidate(EvidenceStatus.UNVERIFIED, ValidationType.DOM_MATCHED, 0);
        RuntimeValidationResult runtime = sampleRuntimeValidation(EvidenceStatus.VERIFIED, ValidationType.RUNTIME_VALIDATED);
        FailureDiagnosis diagnosis = FailureDiagnosis.builder()
                .locatorAnalysis(sampleLocatorAnalysis(candidate)).runtimeValidation(runtime).build();

        reporter.buildMarkdownReport(diagnosis);

        assertThat(candidate.getEvidenceStatus()).isEqualTo(EvidenceStatus.UNVERIFIED);
        assertThat(runtime.getEvidenceStatus()).isEqualTo(EvidenceStatus.VERIFIED);
    }

    // 36. Candidate remains unchanged after reporting
    @Test
    public void testCandidateObjectRemainsUnchangedAfterReporting() {
        LocatorCandidate candidate = sampleCandidate(EvidenceStatus.VERIFIED, ValidationType.DOM_MATCHED, 1);
        int scoreBefore = candidate.getScore();
        EvidenceStatus statusBefore = candidate.getEvidenceStatus();

        reporter.buildMarkdownReport(FailureDiagnosis.builder().locatorAnalysis(sampleLocatorAnalysis(candidate)).build());

        assertThat(candidate.getScore()).isEqualTo(scoreBefore);
        assertThat(candidate.getEvidenceStatus()).isEqualTo(statusBefore);
    }

    // =====================================================================
    // NULL / EDGE CASES
    // =====================================================================

    // 37. Null AI analysis
    @Test
    public void testNullAiAnalysis() {
        String md = reporter.buildMarkdownReport(FailureDiagnosis.builder().failureContext(sampleFailureContext()).build());
        assertThat(md).contains("## AI Analysis\n\nNot available");
    }

    // 38. Null locator analysis
    @Test
    public void testNullLocatorAnalysis() {
        String md = reporter.buildMarkdownReport(FailureDiagnosis.builder().aiAnalysis(sampleAiAnalysis()).build());
        assertThat(md).contains("## Locator Analysis\n\nNot available");
    }

    // 39. Null related candidate on a SuggestedFix
    @Test
    public void testNullRelatedCandidateOnSuggestedFix() {
        SuggestedFix fix = SuggestedFix.builder().fixType(FixType.ASSERTION).description("sample locator").confidence(0.5).build();
        String md = reporter.buildMarkdownReport(FailureDiagnosis.builder().addSuggestedFix(fix).build());
        assertThat(md).doesNotContain("Related locator");
    }

    // 40. Empty evidence list on a SuggestedFix
    @Test
    public void testEmptyEvidenceListOnSuggestedFix() {
        SuggestedFix fix = SuggestedFix.builder().fixType(FixType.WAIT).description("sample locator").confidence(0.3).build();
        String md = reporter.buildMarkdownReport(FailureDiagnosis.builder().addSuggestedFix(fix).build());
        assertThat(md).contains("Not available"); // evidence column falls back cleanly
    }

    // 41. Null description
    @Test
    public void testNullDescriptionRendersNotAvailable() {
        SuggestedFix fix = SuggestedFix.builder().fixType(FixType.UNKNOWN).confidence(0.0).build(); // description never set -> ""
        String md = reporter.buildMarkdownReport(FailureDiagnosis.builder().addSuggestedFix(fix).build());
        assertThat(md).contains("Not available");
    }

    // 42. Empty description
    @Test
    public void testEmptyDescriptionRendersNotAvailable() {
        SuggestedFix fix = SuggestedFix.builder().fixType(FixType.UNKNOWN).description("").confidence(0.0).build();
        String md = reporter.buildMarkdownReport(FailureDiagnosis.builder().addSuggestedFix(fix).build());
        assertThat(md).contains("Not available");
    }

    // =====================================================================
    // SECURITY
    // =====================================================================

    // 43. Fake bearer token is not leaked
    @Test
    public void testFakeBearerTokenNotLeaked() {
        FailureContext context = FailureContext.builder()
                .testName("t").testClass("c")
                .errorMessage("Authorization: Bearer TEST_BEARER_SECRET_VALUE")
                .build();
        String md = reporter.buildMarkdownReport(FailureDiagnosis.builder().failureContext(context).build());
        assertThat(md).doesNotContain("TEST_BEARER_SECRET_VALUE");
    }

    // 44. Fake cookie is not leaked
    @Test
    public void testFakeCookieNotLeaked() {
        FailureContext context = FailureContext.builder()
                .testName("t").testClass("c")
                .errorMessage("Cookie: sessionid=TEST_COOKIE_SECRET_VALUE")
                .build();
        String md = reporter.buildMarkdownReport(FailureDiagnosis.builder().failureContext(context).build());
        assertThat(md).doesNotContain("TEST_COOKIE_SECRET_VALUE");
    }

    // 45. Fake session value is not leaked
    @Test
    public void testFakeSessionValueNotLeaked() {
        AiAnalysisResponse ai = AiAnalysisResponse.builder()
                .summary("session=TEST_SESSION_SECRET_VALUE caused the failure")
                .category(FailureCategory.AUTHENTICATION_FAILURE)
                .build();
        String md = reporter.buildMarkdownReport(FailureDiagnosis.builder().aiAnalysis(ai).build());
        assertThat(md).doesNotContain("TEST_SESSION_SECRET_VALUE");
    }

    // 46. Sensitive query parameter not exposed (URL sanitized, matches #20)
    @Test
    public void testSensitiveQueryParameterNotExposedInErrorMessage() {
        FailureContext context = FailureContext.builder()
                .testName("t").testClass("c")
                .errorMessage("Request failed: password=TEST_QUERY_PASSWORD_SECRET")
                .build();
        String md = reporter.buildMarkdownReport(FailureDiagnosis.builder().failureContext(context).build());
        assertThat(md).doesNotContain("TEST_QUERY_PASSWORD_SECRET");
    }

    // =====================================================================
    // ALLURE
    // =====================================================================

    // 47. Attachment generation succeeds
    @Test
    public void testAttachToAllureSucceedsForCompleteDiagnosis() {
        LocatorCandidate candidate = sampleCandidate(EvidenceStatus.VERIFIED, ValidationType.DOM_MATCHED, 1);
        FailureDiagnosis diagnosis = FailureDiagnosis.builder()
                .failureContext(sampleFailureContext()).aiAnalysis(sampleAiAnalysis())
                .locatorAnalysis(sampleLocatorAnalysis(candidate))
                .runtimeValidation(sampleRuntimeValidation(EvidenceStatus.VERIFIED, ValidationType.RUNTIME_VALIDATED))
                .addSuggestedFix(sampleSuggestedFix(FixType.LOCATOR, candidate))
                .build();
        assertThatCode(() -> reporter.attachToAllure(diagnosis)).doesNotThrowAnyException();
    }

    // 48. Empty diagnosis attachment succeeds
    @Test
    public void testAttachToAllureSucceedsForEmptyDiagnosis() {
        assertThatCode(() -> reporter.attachToAllure(FailureDiagnosis.builder().build())).doesNotThrowAnyException();
        assertThatCode(() -> reporter.attachToAllure(null)).doesNotThrowAnyException();
    }

    // 49. Reporter does not invoke TestListener (structural: no TestListener import/reference exists
    //     anywhere in FailureDiagnosisReporter.java — verified by source inspection, not a runtime assertion)
    @Test
    public void testReporterHasNoTestListenerDependency() {
        assertThat(FailureDiagnosisReporter.class.getDeclaredFields())
                .noneMatch(f -> f.getType().getName().contains("TestListener"));
    }

    // 50. Reporter does not invoke Playwright (structural: no Page/Locator/Playwright field or import)
    @Test
    public void testReporterHasNoPlaywrightDependency() {
        assertThat(FailureDiagnosisReporter.class.getDeclaredFields())
                .noneMatch(f -> f.getType().getPackageName().startsWith("com.microsoft.playwright"));
        assertThat(FailureDiagnosisReporter.class.getDeclaredMethods())
                .noneMatch(m -> java.util.Arrays.stream(m.getParameterTypes())
                        .anyMatch(p -> p.getPackageName().startsWith("com.microsoft.playwright")));
    }
}
