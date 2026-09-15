package com.tests.ai.diagnosis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.framework.ai.codegeneration.EvidenceStatus;
import com.framework.ai.diagnosis.FailureDiagnosis;
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
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;

/**
 * Phase 7 Step 2 (model-only): FailureDiagnosis tests. Pure composition of existing Phase 2/5/6
 * response models — no AI, no Playwright, no orchestration involved anywhere in this file.
 */
public class FailureDiagnosisTest {

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
                .summary("checkout button could not be clicked")
                .rootCause("test failure")
                .category(FailureCategory.LOCATOR_CHANGED)
                .suggestedFix("Update the locator")
                .addSuggestedLocator("sample locator")
                .confidenceScore(0.7)
                .build();
    }

    private LocatorCandidate sampleCandidate() {
        return LocatorCandidate.builder()
                .locator("[data-testid='checkout-button']")
                .strategy(LocatorStrategy.TEST_ID)
                .evidenceStatus(EvidenceStatus.VERIFIED)
                .validationType(ValidationType.DOM_MATCHED)
                .matchCount(1)
                .build();
    }

    private LocatorAnalysisResponse sampleLocatorAnalysis() {
        return LocatorAnalysisResponse.builder()
                .success(true)
                .targetElement("checkout button")
                .addCandidate(sampleCandidate())
                .recommendedLocator(sampleCandidate())
                .build();
    }

    private RuntimeValidationResult sampleRuntimeValidation() {
        return RuntimeValidationResult.builder()
                .locator("[data-testid='checkout-button']")
                .matchCount(1)
                .visible(true)
                .currentUrl("https://www.stag.furlenco.com/cart")
                .environment("staging")
                .validationType(ValidationType.RUNTIME_VALIDATED)
                .evidenceStatus(EvidenceStatus.VERIFIED)
                .message("fake evidence")
                .build();
    }

    private SuggestedFix sampleSuggestedFix() {
        return SuggestedFix.builder()
                .description("sample locator")
                .fixType(FixType.LOCATOR)
                .relatedLocatorCandidate(sampleCandidate())
                .confidence(0.82)
                .build();
    }

    // 1. Valid construction
    @Test
    public void testValidConstruction() {
        FailureDiagnosis diagnosis = FailureDiagnosis.builder()
                .failureContext(sampleFailureContext())
                .aiAnalysis(sampleAiAnalysis())
                .locatorAnalysis(sampleLocatorAnalysis())
                .runtimeValidation(sampleRuntimeValidation())
                .addSuggestedFix(sampleSuggestedFix())
                .build();

        assertThat(diagnosis.getFailureContext()).isNotNull();
        assertThat(diagnosis.getAiAnalysis()).isNotNull();
        assertThat(diagnosis.getLocatorAnalysis()).isNotNull();
        assertThat(diagnosis.getRuntimeValidation()).isNotNull();
        assertThat(diagnosis.getSuggestedFixes()).hasSize(1);
    }

    // 2. Valid FailureContext
    @Test
    public void testValidFailureContext() {
        FailureContext context = sampleFailureContext();
        FailureDiagnosis diagnosis = FailureDiagnosis.builder().failureContext(context).build();
        assertThat(diagnosis.getFailureContext()).isSameAs(context);
        assertThat(diagnosis.getFailureContext().getTestName()).isEqualTo("testCheckout");
    }

    // 3. AI analysis present
    @Test
    public void testAiAnalysisPresent() {
        FailureDiagnosis diagnosis = FailureDiagnosis.builder()
                .failureContext(sampleFailureContext()).aiAnalysis(sampleAiAnalysis()).build();
        assertThat(diagnosis.getAiAnalysis().getCategory()).isEqualTo(FailureCategory.LOCATOR_CHANGED);
    }

    // 4. Locator analysis present
    @Test
    public void testLocatorAnalysisPresent() {
        FailureDiagnosis diagnosis = FailureDiagnosis.builder()
                .failureContext(sampleFailureContext()).locatorAnalysis(sampleLocatorAnalysis()).build();
        assertThat(diagnosis.getLocatorAnalysis().getTargetElement()).isEqualTo("checkout button");
    }

    // 5. Runtime validation present
    @Test
    public void testRuntimeValidationPresent() {
        FailureDiagnosis diagnosis = FailureDiagnosis.builder()
                .failureContext(sampleFailureContext()).runtimeValidation(sampleRuntimeValidation()).build();
        assertThat(diagnosis.getRuntimeValidation().getEvidenceStatus()).isEqualTo(EvidenceStatus.VERIFIED);
    }

    // 6. Nullable AI analysis — no placeholder manufactured
    @Test
    public void testNullableAiAnalysis() {
        FailureDiagnosis diagnosis = FailureDiagnosis.builder().failureContext(sampleFailureContext()).build();
        assertThat(diagnosis.getAiAnalysis()).isNull();
    }

    // 7. Nullable locator analysis — no placeholder manufactured
    @Test
    public void testNullableLocatorAnalysis() {
        FailureDiagnosis diagnosis = FailureDiagnosis.builder().failureContext(sampleFailureContext()).build();
        assertThat(diagnosis.getLocatorAnalysis()).isNull();
    }

    // 8. Nullable runtime validation — no placeholder manufactured
    @Test
    public void testNullableRuntimeValidation() {
        FailureDiagnosis diagnosis = FailureDiagnosis.builder().failureContext(sampleFailureContext()).build();
        assertThat(diagnosis.getRuntimeValidation()).isNull();
    }

    // 9. Empty suggested fixes (never manufactured, never null)
    @Test
    public void testEmptySuggestedFixesWhenNoneSupplied() {
        FailureDiagnosis diagnosis = FailureDiagnosis.builder().failureContext(sampleFailureContext()).build();
        assertThat(diagnosis.getSuggestedFixes()).isNotNull().isEmpty();
    }

    // 10. Defensive copy of suggested fixes
    @Test
    public void testDefensiveCopyOfSuggestedFixesList() {
        List<SuggestedFix> source = new ArrayList<>();
        source.add(sampleSuggestedFix());

        FailureDiagnosis diagnosis = FailureDiagnosis.builder()
                .failureContext(sampleFailureContext()).suggestedFixes(source).build();
        source.add(sampleSuggestedFix()); // mutate the original AFTER construction

        assertThat(diagnosis.getSuggestedFixes()).hasSize(1); // unaffected
    }

    // 11. Returned suggested-fixes list cannot be modified
    @Test
    public void testReturnedSuggestedFixesListIsUnmodifiable() {
        FailureDiagnosis diagnosis = FailureDiagnosis.builder()
                .failureContext(sampleFailureContext()).addSuggestedFix(sampleSuggestedFix()).build();
        assertThatThrownBy(() -> diagnosis.getSuggestedFixes().add(sampleSuggestedFix()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // 12. Multiple suggested fixes
    @Test
    public void testMultipleSuggestedFixes() {
        SuggestedFix assertionFix = SuggestedFix.builder()
                .description("sample locator").fixType(FixType.ASSERTION).confidence(0.64).build();

        FailureDiagnosis diagnosis = FailureDiagnosis.builder()
                .failureContext(sampleFailureContext())
                .addSuggestedFix(sampleSuggestedFix())
                .addSuggestedFix(assertionFix)
                .build();

        assertThat(diagnosis.getSuggestedFixes()).hasSize(2);
        assertThat(diagnosis.getSuggestedFixes()).extracting(SuggestedFix::getFixType)
                .containsExactly(FixType.LOCATOR, FixType.ASSERTION);
    }

    // 13. Model does not mutate after source list mutation (nullable analysis components remain unchanged too)
    @Test
    public void testModelDoesNotMutateAfterSourceListMutationOrExternalChange() {
        List<SuggestedFix> source = new ArrayList<>();
        source.add(sampleSuggestedFix());
        AiAnalysisResponse ai = sampleAiAnalysis();

        FailureDiagnosis diagnosis = FailureDiagnosis.builder()
                .failureContext(sampleFailureContext())
                .aiAnalysis(ai)
                .suggestedFixes(source)
                .build();

        source.clear(); // mutate original list after construction

        assertThat(diagnosis.getSuggestedFixes()).hasSize(1);
        assertThat(diagnosis.getAiAnalysis()).isSameAs(ai); // nullable component reference remains unchanged
    }
}
