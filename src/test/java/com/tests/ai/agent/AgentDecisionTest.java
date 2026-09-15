package com.tests.ai.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.framework.ai.agent.AgentAction;
import com.framework.ai.agent.AgentDecision;
import com.framework.ai.agent.AgentState;
import com.framework.ai.codegeneration.EvidenceItem;
import com.framework.ai.codegeneration.EvidenceStatus;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.testng.annotations.Test;

/**
 * Phase 8 Step 2: pure data-model tests for {@link AgentDecision} — the most important model in
 * this step. No AI, no Playwright, no Git, no execution anywhere in this file. Every test here
 * either proves immutability/defensive-copy behavior already established elsewhere in this
 * codebase, or proves the one hard, new invariant this class introduces: agent reasoning
 * confidence can never become, upgrade, or substitute for verified {@link EvidenceStatus}.
 */
public class AgentDecisionTest {

    private EvidenceItem sampleEvidence(EvidenceStatus status) {
        return EvidenceItem.builder()
                .item("Locator").value("[data-testid='checkout-button']")
                .status(status).source("LocatorAnalysisService").confidence(0.5)
                .build();
    }

    // =====================================================================
    // Valid PROPOSE decision
    // =====================================================================

    @Test
    public void testValidProposeDecision() {
        AgentDecision decision = AgentDecision.builder()
                .state(AgentState.PROPOSE)
                .action(AgentAction.LOCATOR_RECOMMENDATION)
                .reason("Checkout button locator appears stale")
                .rationale("The AI-suggested locator matches the DOM but was never runtime-verified")
                .confidence(0.86)
                .addEvidenceItem(sampleEvidence(EvidenceStatus.UNVERIFIED))
                .build();

        assertThat(decision.getState()).isEqualTo(AgentState.PROPOSE);
        assertThat(decision.getAction()).isEqualTo(AgentAction.LOCATOR_RECOMMENDATION);
        assertThat(decision.getConfidence()).isEqualTo(0.86);
        assertThat(decision.isRequiresApproval()).isTrue();
        assertThat(decision.getEvidenceItems()).hasSize(1);
    }

    // =====================================================================
    // Valid BLOCKED decision + BLOCKED/NONE semantics
    // =====================================================================

    @Test
    public void testValidBlockedDecisionCarriesActionNone() {
        AgentDecision decision = AgentDecision.builder()
                .state(AgentState.BLOCKED)
                .action(AgentAction.NONE)
                .reason("No useful evidence available to propose a recommendation")
                .build();

        assertThat(decision.getState()).isEqualTo(AgentState.BLOCKED);
        assertThat(decision.getAction()).isEqualTo(AgentAction.NONE);
        assertThat(decision.getReason()).isNotBlank();
    }

    @Test
    public void testBlockedDecisionWithNonNoneActionIsRejected() {
        assertThatThrownBy(() -> AgentDecision.builder()
                .state(AgentState.BLOCKED)
                .action(AgentAction.LOCATOR_RECOMMENDATION)
                .build())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void testBlockedDecisionRemainsVisibleWithExistingEvidencePreserved() {
        EvidenceItem existingEvidence = sampleEvidence(EvidenceStatus.MISSING);

        AgentDecision decision = AgentDecision.builder()
                .state(AgentState.BLOCKED)
                .action(AgentAction.NONE)
                .reason("Policy denied further action")
                .addEvidenceItem(existingEvidence)
                .build();

        // A blocked decision is not silently discarded — it still carries whatever evidence existed.
        assertThat(decision.getEvidenceItems()).containsExactly(existingEvidence);
        assertThat(decision.getEvidenceItems().get(0).getStatus()).isEqualTo(EvidenceStatus.MISSING);
    }

    // =====================================================================
    // requiresApproval defaults/behavior
    // =====================================================================

    @Test
    public void testRequiresApprovalDefaultsToTrue() {
        AgentDecision decision = AgentDecision.builder()
                .state(AgentState.PROPOSE)
                .action(AgentAction.NONE)
                .build();

        assertThat(decision.isRequiresApproval()).isTrue();
    }

    @Test
    public void testRequiresApprovalCanBeExplicitlySetFalse() {
        AgentDecision decision = AgentDecision.builder()
                .state(AgentState.PROPOSE)
                .action(AgentAction.NONE)
                .requiresApproval(false)
                .build();

        assertThat(decision.isRequiresApproval()).isFalse();
    }

    // =====================================================================
    // Confidence boundaries
    // =====================================================================

    @Test
    public void testConfidenceZero() {
        assertThat(proposeWithConfidence(0.0).getConfidence()).isEqualTo(0.0);
    }

    @Test
    public void testConfidenceOne() {
        assertThat(proposeWithConfidence(1.0).getConfidence()).isEqualTo(1.0);
    }

    @Test
    public void testConfidenceBelowZeroIsClamped() {
        assertThat(proposeWithConfidence(-5.0).getConfidence()).isEqualTo(0.0);
    }

    @Test
    public void testConfidenceAboveOneIsClamped() {
        assertThat(proposeWithConfidence(5.0).getConfidence()).isEqualTo(1.0);
    }

    @Test
    public void testConfidenceNaNNormalizesToZero() {
        assertThat(proposeWithConfidence(Double.NaN).getConfidence()).isEqualTo(0.0);
    }

    @Test
    public void testConfidenceInfiniteNormalizesToZero() {
        assertThat(proposeWithConfidence(Double.POSITIVE_INFINITY).getConfidence()).isEqualTo(0.0);
        assertThat(proposeWithConfidence(Double.NEGATIVE_INFINITY).getConfidence()).isEqualTo(0.0);
    }

    private AgentDecision proposeWithConfidence(double confidence) {
        return AgentDecision.builder()
                .state(AgentState.PROPOSE)
                .action(AgentAction.NONE)
                .confidence(confidence)
                .build();
    }

    // =====================================================================
    // Defensive evidence list
    // =====================================================================

    @Test
    public void testDefensiveCopyOfEvidenceList() {
        List<EvidenceItem> source = new ArrayList<>();
        source.add(sampleEvidence(EvidenceStatus.UNVERIFIED));

        AgentDecision decision = AgentDecision.builder()
                .state(AgentState.PROPOSE).action(AgentAction.NONE)
                .evidenceItems(source)
                .build();
        source.add(sampleEvidence(EvidenceStatus.VERIFIED)); // mutate original AFTER construction

        assertThat(decision.getEvidenceItems()).hasSize(1); // unaffected
    }

    @Test
    public void testReturnedEvidenceListIsUnmodifiable() {
        AgentDecision decision = AgentDecision.builder()
                .state(AgentState.PROPOSE).action(AgentAction.NONE)
                .addEvidenceItem(sampleEvidence(EvidenceStatus.INFERRED))
                .build();

        assertThatThrownBy(() -> decision.getEvidenceItems().add(sampleEvidence(EvidenceStatus.VERIFIED)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void testListContainingNullIsFilteredNotRejected() {
        List<EvidenceItem> withNull = Arrays.asList(sampleEvidence(EvidenceStatus.UNVERIFIED), null);

        AgentDecision decision = AgentDecision.builder()
                .state(AgentState.PROPOSE).action(AgentAction.NONE)
                .evidenceItems(withNull)
                .build();

        assertThat(decision.getEvidenceItems()).hasSize(1);
    }

    // =====================================================================
    // Null / invalid input handling
    // =====================================================================

    @Test
    public void testNullStateFailsFast() {
        assertThatThrownBy(() -> AgentDecision.builder().state(null).action(AgentAction.NONE).build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void testNullActionFailsFast() {
        assertThatThrownBy(() -> AgentDecision.builder().state(AgentState.PROPOSE).action(null).build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void testNullReasonDefaultsToEmptyStringRatherThanFailing() {
        AgentDecision decision = AgentDecision.builder()
                .state(AgentState.PROPOSE).action(AgentAction.NONE)
                .reason(null)
                .build();

        assertThat(decision.getReason()).isEmpty();
    }

    @Test
    public void testNullRationaleDefaultsToEmptyStringRatherThanFailing() {
        AgentDecision decision = AgentDecision.builder()
                .state(AgentState.PROPOSE).action(AgentAction.NONE)
                .rationale(null)
                .build();

        assertThat(decision.getRationale()).isEmpty();
    }

    @Test
    public void testNullEvidenceListIsTreatedAsNoneSuppliedNotAnError() {
        AgentDecision decision = AgentDecision.builder()
                .state(AgentState.PROPOSE).action(AgentAction.NONE)
                .evidenceItems(null)
                .build();

        assertThat(decision.getEvidenceItems()).isEmpty();
    }

    // =====================================================================
    // EVIDENCE INTEGRITY — the hard requirement
    // =====================================================================

    @Test
    public void testHighConfidenceDoesNotAlterUnverifiedEvidenceStatus() {
        AgentDecision decision = AgentDecision.builder()
                .state(AgentState.PROPOSE)
                .action(AgentAction.LOCATOR_RECOMMENDATION)
                .confidence(0.99) // maximally confident agent reasoning
                .addEvidenceItem(sampleEvidence(EvidenceStatus.UNVERIFIED))
                .build();

        assertThat(decision.getConfidence()).isEqualTo(0.99);
        // UNVERIFIED remains UNVERIFIED regardless of how confident the reasoning step was.
        assertThat(decision.getEvidenceItems().get(0).getStatus()).isEqualTo(EvidenceStatus.UNVERIFIED);
    }

    @Test
    public void testHighConfidenceDoesNotAlterMissingEvidenceStatus() {
        AgentDecision decision = AgentDecision.builder()
                .state(AgentState.PROPOSE)
                .action(AgentAction.LOCATOR_RECOMMENDATION)
                .confidence(1.0)
                .addEvidenceItem(sampleEvidence(EvidenceStatus.MISSING))
                .build();

        assertThat(decision.getEvidenceItems().get(0).getStatus()).isEqualTo(EvidenceStatus.MISSING);
    }

    @Test
    public void testLowConfidenceDoesNotDowngradeVerifiedEvidenceStatus() {
        AgentDecision decision = AgentDecision.builder()
                .state(AgentState.PROPOSE)
                .action(AgentAction.LOCATOR_RECOMMENDATION)
                .confidence(0.01)
                .addEvidenceItem(sampleEvidence(EvidenceStatus.VERIFIED))
                .build();

        // Confidence and evidence status are independent in both directions.
        assertThat(decision.getEvidenceItems().get(0).getStatus()).isEqualTo(EvidenceStatus.VERIFIED);
    }

    @Test
    public void testNoMethodExistsToConvertConfidenceIntoEvidenceStatus() {
        for (var method : AgentDecision.class.getDeclaredMethods()) {
            String lower = method.getName().toLowerCase();
            assertThat(lower).doesNotContain("verify").doesNotContain("promote").doesNotContain("upgrade");
        }
    }

    // =====================================================================
    // Architecture: no execution/approval methods, no setters
    // =====================================================================

    @Test
    public void testNoExecutionMethodsExist() {
        for (var method : AgentDecision.class.getDeclaredMethods()) {
            assertThat(Arrays.asList("execute", "apply", "heal", "run", "approve", "transition"))
                    .doesNotContain(method.getName());
        }
    }

    @Test
    public void testNoSetterMethodsExist() {
        for (var method : AgentDecision.class.getDeclaredMethods()) {
            assertThat(method.getName()).doesNotStartWith("set");
        }
    }
}
