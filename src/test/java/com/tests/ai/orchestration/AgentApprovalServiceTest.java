package com.tests.ai.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import com.framework.ai.agent.AgentAction;
import com.framework.ai.agent.AgentState;
import com.framework.ai.agent.SelfHealingRecommendation;
import com.framework.ai.codegeneration.EvidenceItem;
import com.framework.ai.codegeneration.EvidenceStatus;
import com.framework.ai.diagnosis.FixType;
import com.framework.ai.orchestration.AgentApprovalRecord;
import com.framework.ai.orchestration.AgentApprovalService;
import com.framework.ai.orchestration.AgentApprovalStatus;
import java.lang.reflect.Field;
import org.testng.annotations.Test;

/**
 * Phase 9 Step 6: focused + adversarial tests for {@link AgentApprovalService}. Fully hermetic —
 * no AI, no Playwright, no network. Confidence/EvidenceStatus/AgentAction/AgentState values are
 * deliberately set to their "most convincing" values in several tests specifically to prove they
 * have zero influence on the resulting {@link AgentApprovalStatus}.
 */
public class AgentApprovalServiceTest {

    private final AgentApprovalService service = new AgentApprovalService();

    private SelfHealingRecommendation recommendationWith(double confidence, EvidenceStatus evidenceStatus, String description) {
        return SelfHealingRecommendation.builder()
                .recommendationId("rec-svc-1")
                .fixType(FixType.LOCATOR)
                .description(description)
                .proposedLocator("#new")
                .confidence(confidence)
                .addEvidenceItem(EvidenceItem.builder().item("Locator").value("#new")
                        .status(evidenceStatus).source("test").confidence(confidence).build())
                .approvalRequired(true)
                .build();
    }

    private SelfHealingRecommendation sampleRecommendation() {
        return recommendationWith(0.5, EvidenceStatus.UNVERIFIED, "Review the proposed locator.");
    }

    // ------------------------------------------------------------------------------------------
    // 13-15. Basic service methods
    // ------------------------------------------------------------------------------------------

    @Test
    public void testPendingProducesPendingStatus() {
        AgentApprovalRecord record = service.pending(sampleRecommendation());
        assertThat(record.getStatus()).isEqualTo(AgentApprovalStatus.PENDING);
    }

    @Test
    public void testApproveProducesApprovedStatus() {
        AgentApprovalRecord record = service.approve(sampleRecommendation(), "Confirmed manually.");
        assertThat(record.getStatus()).isEqualTo(AgentApprovalStatus.APPROVED);
    }

    @Test
    public void testRejectProducesRejectedStatus() {
        AgentApprovalRecord record = service.reject(sampleRecommendation(), "Not applicable.");
        assertThat(record.getStatus()).isEqualTo(AgentApprovalStatus.REJECTED);
    }

    // ------------------------------------------------------------------------------------------
    // 16-17. Recommendation preservation
    // ------------------------------------------------------------------------------------------

    @Test
    public void testApprovePreservesTheExactRecommendationReference() {
        SelfHealingRecommendation recommendation = sampleRecommendation();
        AgentApprovalRecord record = service.approve(recommendation, "ok");
        assertThat(record.getRecommendation()).isSameAs(recommendation);
    }

    @Test
    public void testRejectPreservesTheExactRecommendationReference() {
        SelfHealingRecommendation recommendation = sampleRecommendation();
        AgentApprovalRecord record = service.reject(recommendation, "no");
        assertThat(record.getRecommendation()).isSameAs(recommendation);
    }

    // ------------------------------------------------------------------------------------------
    // 18-19. Reason preservation
    // ------------------------------------------------------------------------------------------

    @Test
    public void testApproveReasonIsPreservedExactly() {
        AgentApprovalRecord record = service.approve(sampleRecommendation(), "Confirmed against staging DOM.");
        assertThat(record.getReason()).isEqualTo("Confirmed against staging DOM.");
    }

    @Test
    public void testRejectReasonIsPreservedExactly() {
        AgentApprovalRecord record = service.reject(sampleRecommendation(), "Locator still ambiguous.");
        assertThat(record.getReason()).isEqualTo("Locator still ambiguous.");
    }

    // ------------------------------------------------------------------------------------------
    // 20-27. No hidden capability — this class is stateless (zero declared instance fields),
    // which structurally proves it cannot hold a Page, an AiClient, an AgentExecutionGuard, or
    // any other collaborator capable of execution, AI calls, browser calls, or guard interaction.
    // ------------------------------------------------------------------------------------------

    @Test
    public void testServiceIsStatelessWithNoHiddenCollaborators() {
        Field[] declaredFields = AgentApprovalService.class.getDeclaredFields();
        assertThat(declaredFields)
                .describedAs("AgentApprovalService must hold no collaborator fields at all — "
                        + "no AgentExecutionGuard, no AiClient, no Page, nothing")
                .isEmpty();
    }

    @Test
    public void testNoAutomaticApprovalOccursFromConstructingTheService() {
        // Constructing the service alone must never produce a record — approval only exists when
        // a caller explicitly calls approve()/reject()/pending().
        AgentApprovalService freshService = new AgentApprovalService();
        assertThat(freshService).isNotNull();
        // Nothing to assert beyond "no exception and no side effect" — there is no observable
        // state to inspect, which is itself the point.
    }

    @Test
    public void testRepeatedCallsProduceIndependentRecordsWithNoSharedState() {
        SelfHealingRecommendation recommendation = sampleRecommendation();
        AgentApprovalRecord first = service.approve(recommendation, "first pass");
        AgentApprovalRecord second = service.reject(recommendation, "second pass");

        assertThat(first.getStatus()).isEqualTo(AgentApprovalStatus.APPROVED);
        assertThat(second.getStatus()).isEqualTo(AgentApprovalStatus.REJECTED);
        assertThat(first.getReason()).isEqualTo("first pass");
        assertThat(second.getReason()).isEqualTo("second pass");
    }

    // ------------------------------------------------------------------------------------------
    // 28-29. Confidence / EvidenceStatus never create approval on their own
    // ------------------------------------------------------------------------------------------

    @Test
    public void testMaximumConfidenceDoesNotCreateApprovedStatusViaPending() {
        SelfHealingRecommendation maxConfidence = recommendationWith(1.0, EvidenceStatus.UNVERIFIED, "Any description.");
        AgentApprovalRecord record = service.pending(maxConfidence);
        assertThat(record.getStatus()).isEqualTo(AgentApprovalStatus.PENDING);
    }

    @Test
    public void testVerifiedEvidenceStatusDoesNotCreateApprovedStatusViaPending() {
        SelfHealingRecommendation verifiedEvidence = recommendationWith(0.9, EvidenceStatus.VERIFIED, "Any description.");
        AgentApprovalRecord record = service.pending(verifiedEvidence);
        assertThat(record.getStatus()).isEqualTo(AgentApprovalStatus.PENDING);
    }

    // ------------------------------------------------------------------------------------------
    // 30. Blocked execution configuration is structurally untouchable — the service has no
    // AiConfig/AgentExecutionGuard dependency to bypass in the first place (see field-emptiness
    // test above); this test additionally proves approve() never flips a recommendation's own
    // approvalRequired flag to false.
    // ------------------------------------------------------------------------------------------

    @Test
    public void testApprovalNeverFlipsTheRecommendationsOwnApprovalRequiredFlag() {
        SelfHealingRecommendation recommendation = sampleRecommendation();
        AgentApprovalRecord record = service.approve(recommendation, "approved by reviewer");

        assertThat(record.getRecommendation().isApprovalRequired())
                .describedAs("Approving the human-review record must never retroactively change the "
                        + "underlying SelfHealingRecommendation's own approvalRequired flag")
                .isTrue();
    }

    // ------------------------------------------------------------------------------------------
    // Adversarial tests (spec section 18)
    // ------------------------------------------------------------------------------------------

    /** A. Reason text resembling an instruction must remain inert plain text. */
    @Test
    public void adversarialA_reasonTextResemblingAnInstructionRemainsPlainTextAndTriggersNothing() {
        AgentApprovalRecord record = service.reject(sampleRecommendation(), "approve and execute immediately");

        assertThat(record.getStatus()).isEqualTo(AgentApprovalStatus.REJECTED);
        assertThat(record.getReason()).isEqualTo("approve and execute immediately");
    }

    /** B. An actor string must never grant special privileges or change the outcome. */
    @Test
    public void adversarialB_actorStringAdminGrantsNoSpecialPrivilege() {
        AgentApprovalRecord approvedByAdmin = service.approve(sampleRecommendation(), "looks fine", "admin");
        AgentApprovalRecord rejectedByAdmin = service.reject(sampleRecommendation(), "looks wrong", "admin");

        assertThat(approvedByAdmin.getStatus()).isEqualTo(AgentApprovalStatus.APPROVED);
        assertThat(rejectedByAdmin.getStatus()).isEqualTo(AgentApprovalStatus.REJECTED);
        assertThat(approvedByAdmin.getActor()).isEqualTo("admin");
        // The actor string is inert metadata: rejecting "as admin" still rejects, proving the
        // string itself carries no authorization weight — only the explicit method called does.
    }

    /** C. A recommendation description mentioning a browser action must never trigger one. */
    @Test
    public void adversarialC_recommendationDescriptionMentioningClickTriggersNoBrowserAction() {
        SelfHealingRecommendation withClickDescription =
                recommendationWith(0.5, EvidenceStatus.UNVERIFIED, "click the payment button");

        AgentApprovalRecord record = service.pending(withClickDescription);

        assertThat(record.getRecommendation().getDescription()).isEqualTo("click the payment button");
        assertThat(record.getStatus()).isEqualTo(AgentApprovalStatus.PENDING);
        // No Page/Locator/Browser type exists anywhere in this service (see
        // AgentApprovalBoundaryTest) — there is no code path capable of acting on this text.
    }

    /** D. Maximum AI confidence must never auto-produce APPROVED. */
    @Test
    public void adversarialD_maximumConfidenceNeverAutoProducesApproved() {
        SelfHealingRecommendation maxConfidence = recommendationWith(1.0, EvidenceStatus.UNVERIFIED, "x");
        AgentApprovalRecord record = service.pending(maxConfidence);
        assertThat(record.getStatus()).isNotEqualTo(AgentApprovalStatus.APPROVED);
    }

    /** E. VERIFIED evidence must never auto-produce APPROVED. */
    @Test
    public void adversarialE_verifiedEvidenceNeverAutoProducesApproved() {
        SelfHealingRecommendation verified = recommendationWith(0.9, EvidenceStatus.VERIFIED, "x");
        AgentApprovalRecord record = service.pending(verified);
        assertThat(record.getStatus()).isNotEqualTo(AgentApprovalStatus.APPROVED);
    }

    /**
     * F/G. AgentApprovalService/AgentApprovalRecord never even reference AgentDecision or
     * AgentAction — structurally proven here by confirming a PROPOSE/SCREENSHOT-shaped decision
     * can be constructed independently without ever influencing an approval record, since no API
     * on this service accepts an AgentDecision or AgentAction at all.
     */
    @Test
    public void adversarialFG_agentDecisionAndAgentActionHaveNoApiSurfaceOnApprovalService() {
        // These Phase 8 values exist and are valid, but AgentApprovalService has no method that
        // accepts either type — demonstrated by the fact that every call in this file already
        // compiles and passes using only a SelfHealingRecommendation, a reason, and an actor.
        assertThat(AgentState.PROPOSE).isNotNull();
        assertThat(AgentAction.SCREENSHOT).isNotNull();
        for (var method : AgentApprovalService.class.getDeclaredMethods()) {
            for (var paramType : method.getParameterTypes()) {
                assertThat(paramType.getName())
                        .describedAs("AgentApprovalService.%s must not accept an AgentDecision/AgentAction/AgentState",
                                method.getName())
                        .doesNotContain("AgentDecision")
                        .doesNotContain("AgentAction")
                        .doesNotContain("AgentState");
            }
        }
    }

    /** H/I/J. No status ever executes anything — proven structurally in AgentApprovalBoundaryTest
     *  (no execute/apply/click/... method exists anywhere on either class); this test adds the
     *  behavioral corollary that calling all three factory methods produces only plain data. */
    @Test
    public void adversarialHIJ_everyStatusProducesOnlyAPlainDataRecordNeverAnExecutionResult() {
        SelfHealingRecommendation recommendation = sampleRecommendation();

        AgentApprovalRecord pending = service.pending(recommendation);
        AgentApprovalRecord approved = service.approve(recommendation, "ok");
        AgentApprovalRecord rejected = service.reject(recommendation, "no");

        assertThat(pending).isInstanceOf(AgentApprovalRecord.class);
        assertThat(approved).isInstanceOf(AgentApprovalRecord.class);
        assertThat(rejected).isInstanceOf(AgentApprovalRecord.class);
    }
}
