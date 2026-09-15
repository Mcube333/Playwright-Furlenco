package com.tests.ai.diagnosis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.framework.ai.codegeneration.EvidenceItem;
import com.framework.ai.codegeneration.EvidenceStatus;
import com.framework.ai.diagnosis.FixType;
import com.framework.ai.diagnosis.SuggestedFix;
import com.framework.ai.locatoradvisor.LocatorCandidate;
import com.framework.ai.locatoradvisor.LocatorStrategy;
import com.framework.ai.locatoradvisor.ValidationType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;

/**
 * Phase 7 Step 2 (model-only): SuggestedFix tests. Pure data-model construction — no AI, no
 * Playwright, no orchestration involved anywhere in this file.
 */
public class SuggestedFixTest {

    private LocatorCandidate sampleCandidate() {
        return LocatorCandidate.builder()
                .locator("[data-testid='checkout-button']")
                .strategy(LocatorStrategy.TEST_ID)
                .evidenceStatus(EvidenceStatus.VERIFIED)
                .validationType(ValidationType.DOM_MATCHED)
                .matchCount(1)
                .score(100)
                .build();
    }

    private EvidenceItem sampleEvidence() {
        return EvidenceItem.builder()
                .item("Locator")
                .value("[data-testid='checkout-button']")
                .status(EvidenceStatus.VERIFIED)
                .source("fake evidence")
                .confidence(0.9)
                .build();
    }

    // 1. Valid construction
    @Test
    public void testValidConstruction() {
        SuggestedFix fix = SuggestedFix.builder()
                .description("Use the uniquely matched checkout button locator.")
                .fixType(FixType.LOCATOR)
                .relatedLocatorCandidate(sampleCandidate())
                .addEvidenceItem(sampleEvidence())
                .confidence(0.82)
                .build();

        assertThat(fix.getDescription()).isEqualTo("Use the uniquely matched checkout button locator.");
        assertThat(fix.getFixType()).isEqualTo(FixType.LOCATOR);
        assertThat(fix.getRelatedLocatorCandidate()).isNotNull();
        assertThat(fix.getEvidenceItems()).hasSize(1);
        assertThat(fix.getConfidence()).isEqualTo(0.82);
    }

    // 2. All FixType values can be represented
    @Test
    public void testAllFixTypeValuesCanBeRepresented() {
        for (FixType type : FixType.values()) {
            SuggestedFix fix = SuggestedFix.builder().fixType(type).description("test failure").build();
            assertThat(fix.getFixType()).isEqualTo(type);
        }
    }

    // 3. Nullable relatedLocatorCandidate (e.g. an assertion suggestion, not locator-related)
    @Test
    public void testNullableRelatedLocatorCandidate() {
        SuggestedFix fix = SuggestedFix.builder()
                .description("Update the assertion to match the observed checkout status.")
                .fixType(FixType.ASSERTION)
                .addEvidenceItem(sampleEvidence())
                .confidence(0.64)
                .build();

        assertThat(fix.getRelatedLocatorCandidate()).isNull();
        assertThat(fix.getFixType()).isEqualTo(FixType.ASSERTION);
    }

    // 4. Empty evidence list (never manufactured, never null)
    @Test
    public void testEmptyEvidenceListWhenNoneSupplied() {
        SuggestedFix fix = SuggestedFix.builder().description("sample locator").fixType(FixType.WAIT).build();
        assertThat(fix.getEvidenceItems()).isNotNull().isEmpty();
    }

    // 5. Defensive copy of evidence list
    @Test
    public void testDefensiveCopyOfEvidenceList() {
        List<EvidenceItem> source = new ArrayList<>();
        source.add(sampleEvidence());

        SuggestedFix fix = SuggestedFix.builder().description("sample locator").evidenceItems(source).build();
        source.add(sampleEvidence()); // mutate the original AFTER construction

        assertThat(fix.getEvidenceItems()).hasSize(1); // unaffected by the later mutation
    }

    // 6. Returned evidence list cannot be modified
    @Test
    public void testReturnedEvidenceListIsUnmodifiable() {
        SuggestedFix fix = SuggestedFix.builder().description("sample locator").addEvidenceItem(sampleEvidence()).build();
        assertThatThrownBy(() -> fix.getEvidenceItems().add(sampleEvidence()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // 7. Confidence 0.0
    @Test
    public void testConfidenceZero() {
        SuggestedFix fix = SuggestedFix.builder().description("sample locator").confidence(0.0).build();
        assertThat(fix.getConfidence()).isEqualTo(0.0);
    }

    // 8. Confidence 1.0
    @Test
    public void testConfidenceOne() {
        SuggestedFix fix = SuggestedFix.builder().description("sample locator").confidence(1.0).build();
        assertThat(fix.getConfidence()).isEqualTo(1.0);
    }

    // 9. Invalid confidence behavior: out-of-range clamps, NaN/infinite normalize to 0.0 (never invented mid-range)
    @Test
    public void testInvalidConfidenceIsClampedOrNormalizedNeverInvented() {
        assertThat(SuggestedFix.builder().confidence(1.5).build().getConfidence()).isEqualTo(1.0);
        assertThat(SuggestedFix.builder().confidence(-0.5).build().getConfidence()).isEqualTo(0.0);
        assertThat(SuggestedFix.builder().confidence(Double.NaN).build().getConfidence()).isEqualTo(0.0);
        assertThat(SuggestedFix.builder().confidence(Double.POSITIVE_INFINITY).build().getConfidence()).isEqualTo(0.0);
        assertThat(SuggestedFix.builder().confidence(Double.NEGATIVE_INFINITY).build().getConfidence()).isEqualTo(0.0);
    }

    // 10. Description handling: null becomes empty string (existing project null-safety convention), never invented text
    @Test
    public void testNullDescriptionBecomesEmptyStringNotInventedText() {
        SuggestedFix fix = SuggestedFix.builder().fixType(FixType.UNKNOWN).build(); // description never set
        assertThat(fix.getDescription()).isEqualTo("");
    }

    @Test
    public void testNullFixTypeDefaultsToUnknown() {
        SuggestedFix fix = SuggestedFix.builder().description("test failure").build(); // fixType never set
        assertThat(fix.getFixType()).isEqualTo(FixType.UNKNOWN);
    }
}
