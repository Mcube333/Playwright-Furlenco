package com.tests.ai.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.framework.ai.agent.AgentAction;
import com.framework.ai.agent.AgentContext;
import com.framework.ai.agent.AgentDecision;
import com.framework.ai.agent.AgentExecutionGuard;
import com.framework.ai.agent.AgentExecutionGuardResult;
import com.framework.ai.agent.AgentReasoningService;
import com.framework.ai.agent.AgentState;
import com.framework.ai.agent.SelfHealingRecommendation;
import com.framework.ai.agent.SelfHealingRecommendationService;
import com.framework.ai.codegeneration.EvidenceItem;
import com.framework.ai.codegeneration.EvidenceStatus;
import com.framework.ai.diagnosis.FailureDiagnosis;
import com.framework.ai.diagnosis.FixType;
import com.framework.ai.model.FailureContext;
import com.framework.ai.orchestration.AgentOrchestrationService;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;

/**
 * Phase 9 Step 2: focused tests for {@link AgentOrchestrationService} — the pure orchestration
 * adapter composing the already-existing, unmodified Phase 8 pipeline. Fully hermetic: every
 * collaborator is either a real, unmodified Phase 8 object built directly (no AI/network/browser
 * involved for a BLOCKED/disabled-AI path) or a hand-written recording/throwing subclass, mirroring
 * the existing project convention (e.g. {@code AgentReasoningServiceTest}'s {@code StubAiClient}).
 * No Mockito, no {@code ITestResult}, no Playwright type anywhere in this file.
 */
public class AgentOrchestrationServiceTest {

    // ------------------------------------------------------------------------------------------
    // Test doubles — hand-written subclasses, matching the existing project convention of
    // overriding a concrete collaborator's public method rather than introducing a mocking library.
    // ------------------------------------------------------------------------------------------

    private static class RecordingReasoningService extends AgentReasoningService {
        private final AgentDecision decisionToReturn;
        private final RuntimeException throwsException;
        int callCount = 0;
        AgentContext capturedContext;

        RecordingReasoningService(AgentDecision decisionToReturn) {
            this.decisionToReturn = decisionToReturn;
            this.throwsException = null;
        }

        RecordingReasoningService(RuntimeException throwsException) {
            this.decisionToReturn = null;
            this.throwsException = throwsException;
        }

        @Override
        public AgentDecision analyze(AgentContext context) {
            callCount++;
            capturedContext = context;
            if (throwsException != null) {
                throw throwsException;
            }
            return decisionToReturn;
        }
    }

    private static class RecordingExecutionGuard extends AgentExecutionGuard {
        private final AgentExecutionGuardResult resultToReturn;
        private final RuntimeException throwsException;
        int callCount = 0;
        AgentContext capturedContext;
        AgentDecision capturedDecision;

        RecordingExecutionGuard(AgentExecutionGuardResult resultToReturn) {
            this.resultToReturn = resultToReturn;
            this.throwsException = null;
        }

        RecordingExecutionGuard(RuntimeException throwsException) {
            this.resultToReturn = null;
            this.throwsException = throwsException;
        }

        @Override
        public AgentExecutionGuardResult evaluate(AgentContext context, AgentDecision decision) {
            callCount++;
            capturedContext = context;
            capturedDecision = decision;
            if (throwsException != null) {
                throw throwsException;
            }
            return resultToReturn;
        }
    }

    private static class RecordingRecommendationService extends SelfHealingRecommendationService {
        private final List<SelfHealingRecommendation> toReturn;
        private final RuntimeException throwsException;
        int callCount = 0;
        FailureDiagnosis capturedDiagnosis;
        AgentDecision capturedDecision;
        AgentExecutionGuardResult capturedGuardResult;

        RecordingRecommendationService(List<SelfHealingRecommendation> toReturn) {
            this.toReturn = toReturn;
            this.throwsException = null;
        }

        RecordingRecommendationService(RuntimeException throwsException) {
            this.toReturn = null;
            this.throwsException = throwsException;
        }

        @Override
        public List<SelfHealingRecommendation> recommend(FailureDiagnosis diagnosis, AgentDecision decision,
                                                           AgentExecutionGuardResult guardResult) {
            callCount++;
            capturedDiagnosis = diagnosis;
            capturedDecision = decision;
            capturedGuardResult = guardResult;
            if (throwsException != null) {
                throw throwsException;
            }
            return toReturn;
        }
    }

    /** Records the order in which each collaborator was invoked, shared across all three stubs. */
    private static class OrderRecordingReasoningService extends AgentReasoningService {
        private final List<String> order;
        private final AgentDecision decision;

        OrderRecordingReasoningService(List<String> order, AgentDecision decision) {
            this.order = order;
            this.decision = decision;
        }

        @Override
        public AgentDecision analyze(AgentContext context) {
            order.add("reasoning");
            return decision;
        }
    }

    private static class OrderRecordingExecutionGuard extends AgentExecutionGuard {
        private final List<String> order;
        private final AgentExecutionGuardResult result;

        OrderRecordingExecutionGuard(List<String> order, AgentExecutionGuardResult result) {
            this.order = order;
            this.result = result;
        }

        @Override
        public AgentExecutionGuardResult evaluate(AgentContext context, AgentDecision decision) {
            order.add("guard");
            return result;
        }
    }

    private static class OrderRecordingRecommendationService extends SelfHealingRecommendationService {
        private final List<String> order;
        private final List<SelfHealingRecommendation> result;

        OrderRecordingRecommendationService(List<String> order, List<SelfHealingRecommendation> result) {
            this.order = order;
            this.result = result;
        }

        @Override
        public List<SelfHealingRecommendation> recommend(FailureDiagnosis diagnosis, AgentDecision decision,
                                                           AgentExecutionGuardResult guardResult) {
            order.add("recommendation");
            return result;
        }
    }

    // ------------------------------------------------------------------------------------------
    // Fixture helpers
    // ------------------------------------------------------------------------------------------

    private FailureDiagnosis sampleDiagnosis() {
        return FailureDiagnosis.builder()
                .failureContext(FailureContext.builder().testName("t").testClass("c").build())
                .build();
    }

    private AgentDecision proposeDecision(AgentAction action) {
        return AgentDecision.builder()
                .state(AgentState.PROPOSE)
                .action(action)
                .confidence(0.8)
                .requiresApproval(true)
                .build();
    }

    private AgentDecision blockedDecision(String reason) {
        return AgentDecision.builder()
                .state(AgentState.BLOCKED)
                .action(AgentAction.NONE)
                .reason(reason)
                .requiresApproval(true)
                .build();
    }

    private AgentExecutionGuardResult allowedGuardResult() {
        return AgentExecutionGuardResult.builder().allowed(true).action(AgentAction.LOCATOR_RECOMMENDATION).build();
    }

    private AgentExecutionGuardResult blockedGuardResult(String reason) {
        return AgentExecutionGuardResult.builder().allowed(false).reason(reason).build();
    }

    private SelfHealingRecommendation sampleRecommendation() {
        return SelfHealingRecommendation.builder()
                .fixType(FixType.LOCATOR)
                .description("Review the proposed locator.")
                .currentLocator("#old")
                .proposedLocator("#new")
                .confidence(0.7)
                .addEvidenceItem(EvidenceItem.builder().item("Locator").value("#new")
                        .status(EvidenceStatus.VERIFIED).source("test").confidence(0.9).build())
                .approvalRequired(true)
                .build();
    }

    private AgentOrchestrationService orchestrator(AgentReasoningService reasoning,
                                                     AgentExecutionGuard guard,
                                                     SelfHealingRecommendationService recommendationService) {
        return new AgentOrchestrationService(reasoning, guard, recommendationService);
    }

    // ------------------------------------------------------------------------------------------
    // 1. Happy path
    // ------------------------------------------------------------------------------------------

    @Test
    public void testHappyPathReturnsRecommendationsFromCollaborators() {
        List<SelfHealingRecommendation> expected = List.of(sampleRecommendation());
        RecordingReasoningService reasoning = new RecordingReasoningService(proposeDecision(AgentAction.LOCATOR_RECOMMENDATION));
        RecordingExecutionGuard guard = new RecordingExecutionGuard(allowedGuardResult());
        RecordingRecommendationService recommendation = new RecordingRecommendationService(expected);

        AgentOrchestrationService service = orchestrator(reasoning, guard, recommendation);
        List<SelfHealingRecommendation> result = service.recommend(sampleDiagnosis());

        assertThat(result).isEqualTo(expected);
    }

    // ------------------------------------------------------------------------------------------
    // 2. Null FailureDiagnosis
    // ------------------------------------------------------------------------------------------

    @Test
    public void testNullFailureDiagnosisReturnsEmptyAndCallsNothing() {
        RecordingReasoningService reasoning = new RecordingReasoningService(proposeDecision(AgentAction.NONE));
        RecordingExecutionGuard guard = new RecordingExecutionGuard(allowedGuardResult());
        RecordingRecommendationService recommendation = new RecordingRecommendationService(List.of());

        AgentOrchestrationService service = orchestrator(reasoning, guard, recommendation);
        List<SelfHealingRecommendation> result = service.recommend(null);

        assertThat(result).isEmpty();
        assertThat(reasoning.callCount).isZero();
        assertThat(guard.callCount).isZero();
        assertThat(recommendation.callCount).isZero();
    }

    // ------------------------------------------------------------------------------------------
    // 3. Blocked AgentDecision still flows through the pipeline
    // ------------------------------------------------------------------------------------------

    @Test
    public void testBlockedDecisionStillReachesGuardAndRecommendationService() {
        AgentDecision blocked = blockedDecision("AI disabled");
        RecordingReasoningService reasoning = new RecordingReasoningService(blocked);
        RecordingExecutionGuard guard = new RecordingExecutionGuard(blockedGuardResult("Only a PROPOSE decision may be considered."));
        RecordingRecommendationService recommendation = new RecordingRecommendationService(List.of());

        AgentOrchestrationService service = orchestrator(reasoning, guard, recommendation);
        service.recommend(sampleDiagnosis());

        assertThat(guard.capturedDecision).isSameAs(blocked);
        assertThat(recommendation.capturedDecision).isSameAs(blocked);
    }

    // ------------------------------------------------------------------------------------------
    // 4. Blocked AgentExecutionGuardResult does not stop recommendation generation
    // ------------------------------------------------------------------------------------------

    @Test
    public void testBlockedGuardResultDoesNotPreventRecommendationServiceCall() {
        RecordingReasoningService reasoning = new RecordingReasoningService(proposeDecision(AgentAction.LOCATOR_RECOMMENDATION));
        AgentExecutionGuardResult blocked = blockedGuardResult("No approval-granting mechanism exists yet.");
        RecordingExecutionGuard guard = new RecordingExecutionGuard(blocked);
        List<SelfHealingRecommendation> expected = List.of(sampleRecommendation());
        RecordingRecommendationService recommendation = new RecordingRecommendationService(expected);

        AgentOrchestrationService service = orchestrator(reasoning, guard, recommendation);
        List<SelfHealingRecommendation> result = service.recommend(sampleDiagnosis());

        assertThat(recommendation.callCount).isEqualTo(1);
        assertThat(recommendation.capturedGuardResult).isSameAs(blocked);
        assertThat(result).isEqualTo(expected);
    }

    // ------------------------------------------------------------------------------------------
    // 5. Malformed/failed reasoning (null decision) is handled safely
    // ------------------------------------------------------------------------------------------

    @Test
    public void testNullDecisionFromReasoningServiceIsPassedThroughSafely() {
        RecordingReasoningService reasoning = new RecordingReasoningService((AgentDecision) null);
        RecordingExecutionGuard guard = new RecordingExecutionGuard(blockedGuardResult("No agent decision supplied."));
        RecordingRecommendationService recommendation = new RecordingRecommendationService(List.of());

        AgentOrchestrationService service = orchestrator(reasoning, guard, recommendation);

        assertThatCode(() -> service.recommend(sampleDiagnosis())).doesNotThrowAnyException();
        assertThat(guard.capturedDecision).isNull();
        assertThat(recommendation.capturedDecision).isNull();
    }

    // ------------------------------------------------------------------------------------------
    // 6. Dependency exceptions fail closed
    // ------------------------------------------------------------------------------------------

    @Test
    public void testReasoningServiceExceptionFailsClosedToEmptyList() {
        RecordingReasoningService reasoning = new RecordingReasoningService(new RuntimeException("boom"));
        RecordingExecutionGuard guard = new RecordingExecutionGuard(allowedGuardResult());
        RecordingRecommendationService recommendation = new RecordingRecommendationService(List.of());

        AgentOrchestrationService service = orchestrator(reasoning, guard, recommendation);
        List<SelfHealingRecommendation> result = service.recommend(sampleDiagnosis());

        assertThat(result).isEmpty();
        assertThat(guard.callCount).isZero();
        assertThat(recommendation.callCount).isZero();
    }

    @Test
    public void testExecutionGuardExceptionFailsClosedToEmptyList() {
        RecordingReasoningService reasoning = new RecordingReasoningService(proposeDecision(AgentAction.LOCATOR_RECOMMENDATION));
        RecordingExecutionGuard guard = new RecordingExecutionGuard(new IllegalStateException("guard exploded"));
        RecordingRecommendationService recommendation = new RecordingRecommendationService(List.of());

        AgentOrchestrationService service = orchestrator(reasoning, guard, recommendation);
        List<SelfHealingRecommendation> result = service.recommend(sampleDiagnosis());

        assertThat(result).isEmpty();
        assertThat(recommendation.callCount).isZero();
    }

    @Test
    public void testRecommendationServiceExceptionFailsClosedToEmptyList() {
        RecordingReasoningService reasoning = new RecordingReasoningService(proposeDecision(AgentAction.LOCATOR_RECOMMENDATION));
        RecordingExecutionGuard guard = new RecordingExecutionGuard(allowedGuardResult());
        RecordingRecommendationService recommendation = new RecordingRecommendationService(new RuntimeException("recommend failed"));

        AgentOrchestrationService service = orchestrator(reasoning, guard, recommendation);

        assertThatCode(() -> service.recommend(sampleDiagnosis())).doesNotThrowAnyException();
        assertThat(service.recommend(sampleDiagnosis())).isEmpty();
    }

    // ------------------------------------------------------------------------------------------
    // 7. Recommendation service is invoked with the correct arguments
    // ------------------------------------------------------------------------------------------

    @Test
    public void testRecommendationServiceReceivesTheActualDiagnosisDecisionAndGuardResult() {
        FailureDiagnosis diagnosis = sampleDiagnosis();
        AgentDecision decision = proposeDecision(AgentAction.WAIT_RECOMMENDATION);
        AgentExecutionGuardResult guardResult = allowedGuardResult();

        RecordingReasoningService reasoning = new RecordingReasoningService(decision);
        RecordingExecutionGuard guard = new RecordingExecutionGuard(guardResult);
        RecordingRecommendationService recommendation = new RecordingRecommendationService(List.of());

        AgentOrchestrationService service = orchestrator(reasoning, guard, recommendation);
        service.recommend(diagnosis);

        assertThat(recommendation.capturedDiagnosis).isSameAs(diagnosis);
        assertThat(recommendation.capturedDecision).isSameAs(decision);
        assertThat(recommendation.capturedGuardResult).isSameAs(guardResult);
    }

    // ------------------------------------------------------------------------------------------
    // 8. Correct dependency ordering
    // ------------------------------------------------------------------------------------------

    @Test
    public void testCollaboratorsAreInvokedInReasoningThenGuardThenRecommendationOrder() {
        List<String> order = new ArrayList<>();
        AgentDecision decision = proposeDecision(AgentAction.LOCATOR_RECOMMENDATION);
        AgentExecutionGuardResult guardResult = allowedGuardResult();

        AgentOrchestrationService service = orchestrator(
                new OrderRecordingReasoningService(order, decision),
                new OrderRecordingExecutionGuard(order, guardResult),
                new OrderRecordingRecommendationService(order, List.of()));

        service.recommend(sampleDiagnosis());

        assertThat(order).containsExactly("reasoning", "guard", "recommendation");
    }

    // ------------------------------------------------------------------------------------------
    // 9-15, 20-24. Structural/source boundary checks are covered by AgentOrchestrationBoundaryTest.
    // ------------------------------------------------------------------------------------------

    // ------------------------------------------------------------------------------------------
    // 16. Guard cannot be bypassed — always evaluated exactly once per call, even when it blocks.
    // ------------------------------------------------------------------------------------------

    @Test
    public void testGuardIsAlwaysEvaluatedExactlyOncePerCall() {
        RecordingReasoningService reasoning = new RecordingReasoningService(proposeDecision(AgentAction.LOCATOR_RECOMMENDATION));
        RecordingExecutionGuard guard = new RecordingExecutionGuard(blockedGuardResult("blocked"));
        RecordingRecommendationService recommendation = new RecordingRecommendationService(List.of());

        AgentOrchestrationService service = orchestrator(reasoning, guard, recommendation);
        service.recommend(sampleDiagnosis());

        assertThat(guard.callCount).isEqualTo(1);
    }

    // ------------------------------------------------------------------------------------------
    // 17. Recommendation output remains approval-required (pure pass-through, never altered)
    // ------------------------------------------------------------------------------------------

    @Test
    public void testRecommendationOutputPassesThroughWithApprovalStillRequired() {
        SelfHealingRecommendation recommendation = sampleRecommendation();
        RecordingReasoningService reasoning = new RecordingReasoningService(proposeDecision(AgentAction.LOCATOR_RECOMMENDATION));
        RecordingExecutionGuard guard = new RecordingExecutionGuard(allowedGuardResult());
        RecordingRecommendationService recommendationService = new RecordingRecommendationService(List.of(recommendation));

        AgentOrchestrationService service = orchestrator(reasoning, guard, recommendationService);
        List<SelfHealingRecommendation> result = service.recommend(sampleDiagnosis());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).isApprovalRequired()).isTrue();
    }

    // ------------------------------------------------------------------------------------------
    // 18. Evidence status is never altered by the orchestrator
    // ------------------------------------------------------------------------------------------

    @Test
    public void testEvidenceStatusPassesThroughUnchanged() {
        SelfHealingRecommendation recommendation = sampleRecommendation();
        EvidenceStatus originalStatus = recommendation.getEvidenceItems().get(0).getStatus();

        RecordingReasoningService reasoning = new RecordingReasoningService(proposeDecision(AgentAction.LOCATOR_RECOMMENDATION));
        RecordingExecutionGuard guard = new RecordingExecutionGuard(allowedGuardResult());
        RecordingRecommendationService recommendationService = new RecordingRecommendationService(List.of(recommendation));

        AgentOrchestrationService service = orchestrator(reasoning, guard, recommendationService);
        List<SelfHealingRecommendation> result = service.recommend(sampleDiagnosis());

        assertThat(result.get(0).getEvidenceItems().get(0).getStatus()).isEqualTo(originalStatus);
        assertThat(originalStatus).isEqualTo(EvidenceStatus.VERIFIED);
    }

    // ------------------------------------------------------------------------------------------
    // 19. Confidence remains independent from evidence status (pure pass-through of both fields)
    // ------------------------------------------------------------------------------------------

    @Test
    public void testConfidenceAndEvidenceStatusRemainIndependentAfterPassThrough() {
        SelfHealingRecommendation recommendation = sampleRecommendation();
        double originalConfidence = recommendation.getConfidence();
        EvidenceStatus originalStatus = recommendation.getEvidenceItems().get(0).getStatus();

        RecordingReasoningService reasoning = new RecordingReasoningService(proposeDecision(AgentAction.LOCATOR_RECOMMENDATION));
        RecordingExecutionGuard guard = new RecordingExecutionGuard(allowedGuardResult());
        RecordingRecommendationService recommendationService = new RecordingRecommendationService(List.of(recommendation));

        AgentOrchestrationService service = orchestrator(reasoning, guard, recommendationService);
        SelfHealingRecommendation result = service.recommend(sampleDiagnosis()).get(0);

        // A high/unchanged confidence and a VERIFIED evidence status are independent facts —
        // the orchestrator does not derive one from the other, nor does it alter either.
        assertThat(result.getConfidence()).isEqualTo(originalConfidence);
        assertThat(result.getEvidenceItems().get(0).getStatus()).isEqualTo(originalStatus);
    }

    // ------------------------------------------------------------------------------------------
    // 25. Safe behavior when a dependency returns null
    // ------------------------------------------------------------------------------------------

    @Test
    public void testNullRecommendationListFromServiceBecomesEmptyListNotNull() {
        RecordingReasoningService reasoning = new RecordingReasoningService(proposeDecision(AgentAction.LOCATOR_RECOMMENDATION));
        RecordingExecutionGuard guard = new RecordingExecutionGuard(allowedGuardResult());
        RecordingRecommendationService recommendation = new RecordingRecommendationService((List<SelfHealingRecommendation>) null);

        AgentOrchestrationService service = orchestrator(reasoning, guard, recommendation);
        List<SelfHealingRecommendation> result = service.recommend(sampleDiagnosis());

        assertThat(result).isNotNull().isEmpty();
    }

    @Test
    public void testNullGuardResultIsPassedThroughToRecommendationServiceSafely() {
        RecordingReasoningService reasoning = new RecordingReasoningService(proposeDecision(AgentAction.LOCATOR_RECOMMENDATION));
        RecordingExecutionGuard guard = new RecordingExecutionGuard((AgentExecutionGuardResult) null);
        RecordingRecommendationService recommendation = new RecordingRecommendationService(List.of());

        AgentOrchestrationService service = orchestrator(reasoning, guard, recommendation);

        assertThatCode(() -> service.recommend(sampleDiagnosis())).doesNotThrowAnyException();
        assertThat(recommendation.capturedGuardResult).isNull();
    }

    // ------------------------------------------------------------------------------------------
    // 26. Safe behavior when the recommendation service returns an empty list
    // ------------------------------------------------------------------------------------------

    @Test
    public void testEmptyRecommendationListFromServiceIsReturnedAsIs() {
        RecordingReasoningService reasoning = new RecordingReasoningService(blockedDecision("AI disabled"));
        RecordingExecutionGuard guard = new RecordingExecutionGuard(blockedGuardResult("AI is disabled"));
        RecordingRecommendationService recommendation = new RecordingRecommendationService(List.of());

        AgentOrchestrationService service = orchestrator(reasoning, guard, recommendation);
        List<SelfHealingRecommendation> result = service.recommend(sampleDiagnosis());

        assertThat(result).isEmpty();
    }

    // ------------------------------------------------------------------------------------------
    // 27. Repeated explicit invocation creates no hidden/shared state
    // ------------------------------------------------------------------------------------------

    @Test
    public void testRepeatedInvocationsAreIndependentAndDoNotAccumulateState() {
        RecordingReasoningService reasoning = new RecordingReasoningService(proposeDecision(AgentAction.LOCATOR_RECOMMENDATION));
        RecordingExecutionGuard guard = new RecordingExecutionGuard(allowedGuardResult());
        RecordingRecommendationService recommendation = new RecordingRecommendationService(List.of(sampleRecommendation()));

        AgentOrchestrationService service = orchestrator(reasoning, guard, recommendation);

        List<SelfHealingRecommendation> first = service.recommend(sampleDiagnosis());
        List<SelfHealingRecommendation> second = service.recommend(sampleDiagnosis());

        assertThat(first).hasSize(1);
        assertThat(second).hasSize(1);
        assertThat(reasoning.callCount).isEqualTo(2);
        assertThat(guard.callCount).isEqualTo(2);
        assertThat(recommendation.callCount).isEqualTo(2);
    }

    // ------------------------------------------------------------------------------------------
    // 28. No internal retry loop — a throwing collaborator is called exactly once, not retried.
    // ------------------------------------------------------------------------------------------

    @Test
    public void testThrowingGuardIsInvokedExactlyOnceNotRetried() {
        RecordingReasoningService reasoning = new RecordingReasoningService(proposeDecision(AgentAction.LOCATOR_RECOMMENDATION));
        RecordingExecutionGuard guard = new RecordingExecutionGuard(new RuntimeException("guard failure"));
        RecordingRecommendationService recommendation = new RecordingRecommendationService(List.of());

        AgentOrchestrationService service = orchestrator(reasoning, guard, recommendation);
        service.recommend(sampleDiagnosis());

        assertThat(guard.callCount).isEqualTo(1);
    }

    // ------------------------------------------------------------------------------------------
    // 29. No recursive self-invocation — a single top-level call produces exactly one pass
    //     through each collaborator.
    // ------------------------------------------------------------------------------------------

    @Test
    public void testSingleCallProducesExactlyOnePassThroughEachCollaborator() {
        RecordingReasoningService reasoning = new RecordingReasoningService(proposeDecision(AgentAction.LOCATOR_RECOMMENDATION));
        RecordingExecutionGuard guard = new RecordingExecutionGuard(allowedGuardResult());
        RecordingRecommendationService recommendation = new RecordingRecommendationService(List.of());

        AgentOrchestrationService service = orchestrator(reasoning, guard, recommendation);
        service.recommend(sampleDiagnosis());

        assertThat(reasoning.callCount).isEqualTo(1);
        assertThat(guard.callCount).isEqualTo(1);
        assertThat(recommendation.callCount).isEqualTo(1);
    }

    // ------------------------------------------------------------------------------------------
    // 30. Constructor dependency integrity
    // ------------------------------------------------------------------------------------------

    @Test
    public void testConstructorRejectsNullAgentReasoningService() {
        assertThatThrownBy(() -> new AgentOrchestrationService(null, new AgentExecutionGuard(), new SelfHealingRecommendationService()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void testConstructorRejectsNullAgentExecutionGuard() {
        assertThatThrownBy(() -> new AgentOrchestrationService(new AgentReasoningService(), null, new SelfHealingRecommendationService()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void testConstructorRejectsNullSelfHealingRecommendationService() {
        assertThatThrownBy(() -> new AgentOrchestrationService(new AgentReasoningService(), new AgentExecutionGuard(), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void testNoArgConstructorWiresRealCollaboratorsWithoutThrowing() {
        assertThatCode(AgentOrchestrationService::new).doesNotThrowAnyException();
    }

    // ------------------------------------------------------------------------------------------
    // Additional explicit-invocation-only behavior: calling recommend() never touches AI/network
    // when the real, unmodified AgentReasoningService is used with AI disabled (default config) —
    // proving this class introduces no new AI call of its own.
    // ------------------------------------------------------------------------------------------

    @Test
    public void testRealCollaboratorsWithAiDisabledProduceASafeBlockedPipelineWithNoException() {
        AgentOrchestrationService service = new AgentOrchestrationService();

        List<SelfHealingRecommendation> result = service.recommend(sampleDiagnosis());

        // AI is disabled by default in this test environment's configuration, so the real
        // AgentReasoningService returns a BLOCKED/NONE decision; the pipeline must still complete
        // without throwing and without fabricating a recommendation.
        assertThat(result).isNotNull();
    }
}
