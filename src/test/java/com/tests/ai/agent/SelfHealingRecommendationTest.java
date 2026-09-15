package com.tests.ai.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.framework.ai.agent.SelfHealingRecommendation;
import com.framework.ai.codegeneration.EvidenceItem;
import com.framework.ai.codegeneration.EvidenceStatus;
import com.framework.ai.diagnosis.FixType;
import com.framework.ai.locatoradvisor.ValidationType;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.testng.annotations.Test;

/**
 * Phase 8 Step 6: pure data-model tests for {@link SelfHealingRecommendation}. No AI, no
 * Playwright, no execution behavior anywhere in this file.
 */
public class SelfHealingRecommendationTest {

    private EvidenceItem sampleEvidence(EvidenceStatus status) {
        return EvidenceItem.builder().item("Locator").value("x").status(status).confidence(0.5).build();
    }

    @Test
    public void testValidConstruction() {
        SelfHealingRecommendation recommendation = SelfHealingRecommendation.builder()
                .fixType(FixType.LOCATOR)
                .description("Consider updating the locator.")
                .currentLocator("#checkout-button")
                .proposedLocator("[data-testid='checkout-submit']")
                .confidence(0.86)
                .validationType(ValidationType.DOM_MATCHED)
                .addEvidenceItem(sampleEvidence(EvidenceStatus.UNVERIFIED))
                .build();

        assertThat(recommendation.getFixType()).isEqualTo(FixType.LOCATOR);
        assertThat(recommendation.getCurrentLocator()).isEqualTo("#checkout-button");
        assertThat(recommendation.getProposedLocator()).isEqualTo("[data-testid='checkout-submit']");
        assertThat(recommendation.getValidationType()).isEqualTo(ValidationType.DOM_MATCHED);
        assertThat(recommendation.isApprovalRequired()).isTrue();
    }

    @Test
    public void testRecommendationIdGeneratedWhenNotSupplied() {
        SelfHealingRecommendation a = SelfHealingRecommendation.builder().build();
        SelfHealingRecommendation b = SelfHealingRecommendation.builder().build();

        assertThat(a.getRecommendationId()).isNotBlank();
        assertThat(a.getRecommendationId()).isNotEqualTo(b.getRecommendationId());
    }

    @Test
    public void testApprovalRequiredDefaultsToTrue() {
        assertThat(SelfHealingRecommendation.builder().build().isApprovalRequired()).isTrue();
    }

    @Test
    public void testValidationTypeDefaultsToNotValidated() {
        assertThat(SelfHealingRecommendation.builder().build().getValidationType())
                .isEqualTo(ValidationType.NOT_VALIDATED);
    }

    @Test
    public void testFixTypeDefaultsToUnknownWhenNotSupplied() {
        assertThat(SelfHealingRecommendation.builder().build().getFixType()).isEqualTo(FixType.UNKNOWN);
    }

    @Test
    public void testStringFieldsDefaultToEmptyNeverNull() {
        SelfHealingRecommendation recommendation = SelfHealingRecommendation.builder().build();

        assertThat(recommendation.getDescription()).isEmpty();
        assertThat(recommendation.getCurrentLocator()).isEmpty();
        assertThat(recommendation.getProposedLocator()).isEmpty();
        assertThat(recommendation.getCurrentAction()).isEmpty();
        assertThat(recommendation.getProposedAction()).isEmpty();
        assertThat(recommendation.getRationale()).isEmpty();
    }

    @Test
    public void testConfidenceZero() {
        assertThat(SelfHealingRecommendation.builder().confidence(0.0).build().getConfidence()).isEqualTo(0.0);
    }

    @Test
    public void testConfidenceOne() {
        assertThat(SelfHealingRecommendation.builder().confidence(1.0).build().getConfidence()).isEqualTo(1.0);
    }

    @Test
    public void testConfidenceAboveOneIsClamped() {
        assertThat(SelfHealingRecommendation.builder().confidence(5.0).build().getConfidence()).isEqualTo(1.0);
    }

    @Test
    public void testConfidenceBelowZeroIsClamped() {
        assertThat(SelfHealingRecommendation.builder().confidence(-5.0).build().getConfidence()).isEqualTo(0.0);
    }

    @Test
    public void testConfidenceNaNNormalizesToZero() {
        assertThat(SelfHealingRecommendation.builder().confidence(Double.NaN).build().getConfidence()).isEqualTo(0.0);
    }

    @Test
    public void testConfidenceInfiniteNormalizesToZero() {
        assertThat(SelfHealingRecommendation.builder().confidence(Double.POSITIVE_INFINITY).build().getConfidence()).isEqualTo(0.0);
        assertThat(SelfHealingRecommendation.builder().confidence(Double.NEGATIVE_INFINITY).build().getConfidence()).isEqualTo(0.0);
    }

    @Test
    public void testDefensiveCopyOfEvidenceList() {
        List<EvidenceItem> source = new ArrayList<>();
        source.add(sampleEvidence(EvidenceStatus.UNVERIFIED));

        SelfHealingRecommendation recommendation = SelfHealingRecommendation.builder().evidenceItems(source).build();
        source.add(sampleEvidence(EvidenceStatus.VERIFIED));

        assertThat(recommendation.getEvidenceItems()).hasSize(1);
    }

    @Test
    public void testReturnedEvidenceListIsUnmodifiable() {
        SelfHealingRecommendation recommendation = SelfHealingRecommendation.builder()
                .addEvidenceItem(sampleEvidence(EvidenceStatus.MISSING)).build();

        assertThatThrownBy(() -> recommendation.getEvidenceItems().add(sampleEvidence(EvidenceStatus.VERIFIED)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void testListContainingNullIsFiltered() {
        List<EvidenceItem> withNull = Arrays.asList(sampleEvidence(EvidenceStatus.UNVERIFIED), null);

        SelfHealingRecommendation recommendation = SelfHealingRecommendation.builder().evidenceItems(withNull).build();

        assertThat(recommendation.getEvidenceItems()).hasSize(1);
    }

    @Test
    public void testNoExecutableFieldExists() {
        for (Method method : SelfHealingRecommendation.class.getDeclaredMethods()) {
            assertThat(method.getName().toLowerCase()).doesNotContain("executable");
        }
    }

    @Test
    public void testNoSetterOrExecutionMethodsExist() {
        for (Method method : SelfHealingRecommendation.class.getDeclaredMethods()) {
            assertThat(method.getName()).doesNotStartWith("set");
            assertThat(Arrays.asList("execute", "apply", "run", "click", "fill", "navigate", "heal"))
                    .doesNotContain(method.getName().toLowerCase());
        }
    }
}
