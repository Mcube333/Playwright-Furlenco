package com.tests.ai.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.framework.ai.agent.AgentExecutionGuard;
import com.framework.ai.agent.AgentReasoningService;
import com.framework.ai.agent.SelfHealingRecommendation;
import com.framework.ai.agent.SelfHealingRecommendationService;
import com.framework.ai.codegeneration.EvidenceItem;
import com.framework.ai.codegeneration.EvidenceStatus;
import com.framework.ai.diagnosis.FailureDiagnosis;
import com.framework.ai.diagnosis.FixType;
import com.framework.ai.locatoradvisor.ValidationType;
import com.framework.ai.model.FailureContext;
import com.framework.ai.orchestration.AgentOrchestrationService;
import com.framework.ai.orchestration.AgentRecommendationConsumer;
import java.util.List;
import org.testng.annotations.Test;

/**
 * Phase 9 Step 4: focused tests for {@link AgentRecommendationConsumer} — the pure delegation
 * wrapper on top of the already-approved {@link AgentOrchestrationService}. Fully hermetic: the
 * orchestration collaborator is a hand-written recording/throwing subclass of
 * {@link AgentOrchestrationService} (same convention as {@code AgentOrchestrationServiceTest}'s
 * subclassing of its own collaborators). No Mockito, no {@code ITestResult}, no Playwright type.
 */
public class AgentRecommendationConsumerTest {

    // ------------------------------------------------------------------------------------------
    // Test double
    // ------------------------------------------------------------------------------------------

    private static class RecordingOrchestrationService extends AgentOrchestrationService {
        private final List<SelfHealingRecommendation> toReturn;
        private final RuntimeException throwsException;
        int callCount = 0;
        FailureDiagnosis capturedDiagnosis;

        RecordingOrchestrationService(List<SelfHealingRecommendation> toReturn) {
            super(new AgentReasoningService(), new AgentExecutionGuard(), new SelfHealingRecommendationService());
            this.toReturn = toReturn;
            this.throwsException = null;
        }

        RecordingOrchestrationService(RuntimeException throwsException) {
            super(new AgentReasoningService(), new AgentExecutionGuard(), new SelfHealingRecommendationService());
            this.toReturn = null;
            this.throwsException = throwsException;
        }

        @Override
        public List<SelfHealingRecommendation> recommend(FailureDiagnosis failureDiagnosis) {
            callCount++;
            capturedDiagnosis = failureDiagnosis;
            if (throwsException != null) {
                throw throwsException;
            }
            return toReturn;
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

    private SelfHealingRecommendation sampleRecommendation() {
        return SelfHealingRecommendation.builder()
                .recommendationId("rec-42")
                .fixType(FixType.LOCATOR)
                .description("Review the proposed locator.")
                .currentLocator("#old")
                .proposedLocator("#new")
                .rationale("Static evidence supports this candidate.")
                .confidence(0.66)
                .addEvidenceItem(EvidenceItem.builder().item("Locator").value("#new")
                        .status(EvidenceStatus.VERIFIED).source("test").confidence(0.9).build())
                .validationType(ValidationType.RUNTIME_VALIDATED)
                .approvalRequired(true)
                .build();
    }

    // ------------------------------------------------------------------------------------------
    // 1. Successful delegation
    // ------------------------------------------------------------------------------------------

    @Test
    public void testConsumeDelegatesToOrchestrationServiceAndReturnsItsResult() {
        List<SelfHealingRecommendation> expected = List.of(sampleRecommendation());
        RecordingOrchestrationService orchestration = new RecordingOrchestrationService(expected);
        AgentRecommendationConsumer consumer = new AgentRecommendationConsumer(orchestration);

        List<SelfHealingRecommendation> result = consumer.consume(sampleDiagnosis());

        assertThat(result).isEqualTo(expected);
        assertThat(orchestration.callCount).isEqualTo(1);
    }

    // ------------------------------------------------------------------------------------------
    // 2. Exact recommendation list identity
    // ------------------------------------------------------------------------------------------

    @Test
    public void testConsumeReturnsTheExactSameListInstanceFromOrchestration() {
        List<SelfHealingRecommendation> expected = List.of(sampleRecommendation());
        AgentRecommendationConsumer consumer = new AgentRecommendationConsumer(new RecordingOrchestrationService(expected));

        List<SelfHealingRecommendation> result = consumer.consume(sampleDiagnosis());

        assertThat(result).isSameAs(expected);
    }

    // ------------------------------------------------------------------------------------------
    // 3. Empty recommendation list
    // ------------------------------------------------------------------------------------------

    @Test
    public void testEmptyRecommendationListIsReturnedAsIs() {
        AgentRecommendationConsumer consumer = new AgentRecommendationConsumer(new RecordingOrchestrationService(List.of()));

        List<SelfHealingRecommendation> result = consumer.consume(sampleDiagnosis());

        assertThat(result).isEmpty();
    }

    // ------------------------------------------------------------------------------------------
    // 4. Null FailureDiagnosis
    // ------------------------------------------------------------------------------------------

    @Test
    public void testNullFailureDiagnosisIsDelegatedAsIsToOrchestration() {
        RecordingOrchestrationService orchestration = new RecordingOrchestrationService(List.of());
        AgentRecommendationConsumer consumer = new AgentRecommendationConsumer(orchestration);

        List<SelfHealingRecommendation> result = consumer.consume(null);

        assertThat(result).isEmpty();
        assertThat(orchestration.callCount).isEqualTo(1);
        assertThat(orchestration.capturedDiagnosis).isNull();
    }

    @Test
    public void testNullFailureDiagnosisWithRealOrchestrationServiceNeverThrows() {
        AgentRecommendationConsumer consumer = new AgentRecommendationConsumer(new AgentOrchestrationService());

        assertThatCode(() -> consumer.consume(null)).doesNotThrowAnyException();
        assertThat(consumer.consume(null)).isEmpty();
    }

    // ------------------------------------------------------------------------------------------
    // 5. Orchestration exception fails closed
    // ------------------------------------------------------------------------------------------

    @Test
    public void testOrchestrationExceptionFailsClosedToEmptyList() {
        RecordingOrchestrationService orchestration = new RecordingOrchestrationService(new RuntimeException("boom"));
        AgentRecommendationConsumer consumer = new AgentRecommendationConsumer(orchestration);

        List<SelfHealingRecommendation> result = consumer.consume(sampleDiagnosis());

        assertThat(result).isNotNull().isEmpty();
    }

    @Test
    public void testOrchestrationExceptionNeverPropagatesToTheCaller() {
        AgentRecommendationConsumer consumer = new AgentRecommendationConsumer(
                new RecordingOrchestrationService(new IllegalStateException("orchestration exploded")));

        assertThatCode(() -> consumer.consume(sampleDiagnosis())).doesNotThrowAnyException();
    }

    // ------------------------------------------------------------------------------------------
    // 6. Orchestration returning null is normalized
    // ------------------------------------------------------------------------------------------

    @Test
    public void testNullResultFromOrchestrationIsNormalizedToEmptyList() {
        RecordingOrchestrationService orchestration = new RecordingOrchestrationService((List<SelfHealingRecommendation>) null);
        AgentRecommendationConsumer consumer = new AgentRecommendationConsumer(orchestration);

        List<SelfHealingRecommendation> result = consumer.consume(sampleDiagnosis());

        assertThat(result).isNotNull().isEmpty();
    }

    // ------------------------------------------------------------------------------------------
    // 7-13. Recommendation fields remain unchanged (pure pass-through, field by field)
    // ------------------------------------------------------------------------------------------

    @Test
    public void testApprovalRequiredRemainsTrue() {
        SelfHealingRecommendation result = singleResultFrom(sampleRecommendation());
        assertThat(result.isApprovalRequired()).isTrue();
    }

    @Test
    public void testEvidenceStatusRemainsUnchanged() {
        SelfHealingRecommendation result = singleResultFrom(sampleRecommendation());
        assertThat(result.getEvidenceItems().get(0).getStatus()).isEqualTo(EvidenceStatus.VERIFIED);
    }

    @Test
    public void testValidationTypeRemainsUnchanged() {
        SelfHealingRecommendation result = singleResultFrom(sampleRecommendation());
        assertThat(result.getValidationType()).isEqualTo(ValidationType.RUNTIME_VALIDATED);
    }

    @Test
    public void testConfidenceRemainsUnchanged() {
        SelfHealingRecommendation result = singleResultFrom(sampleRecommendation());
        assertThat(result.getConfidence()).isEqualTo(0.66);
    }

    @Test
    public void testRationaleRemainsUnchanged() {
        SelfHealingRecommendation result = singleResultFrom(sampleRecommendation());
        assertThat(result.getRationale()).isEqualTo("Static evidence supports this candidate.");
    }

    @Test
    public void testProposedLocatorRemainsUnchanged() {
        SelfHealingRecommendation result = singleResultFrom(sampleRecommendation());
        assertThat(result.getProposedLocator()).isEqualTo("#new");
    }

    @Test
    public void testRecommendationIdAndFixTypeRemainUnchanged() {
        SelfHealingRecommendation result = singleResultFrom(sampleRecommendation());
        assertThat(result.getRecommendationId()).isEqualTo("rec-42");
        assertThat(result.getFixType()).isEqualTo(FixType.LOCATOR);
    }

    private SelfHealingRecommendation singleResultFrom(SelfHealingRecommendation recommendation) {
        AgentRecommendationConsumer consumer =
                new AgentRecommendationConsumer(new RecordingOrchestrationService(List.of(recommendation)));
        List<SelfHealingRecommendation> result = consumer.consume(sampleDiagnosis());
        assertThat(result).hasSize(1);
        return result.get(0);
    }

    // ------------------------------------------------------------------------------------------
    // 14-24. Structural/source boundary checks are covered by AgentRecommendationConsumerBoundaryTest.
    // ------------------------------------------------------------------------------------------

    // ------------------------------------------------------------------------------------------
    // 25. Repeated explicit calls have no hidden state
    // ------------------------------------------------------------------------------------------

    @Test
    public void testRepeatedExplicitCallsAreIndependentAndDoNotAccumulateState() {
        RecordingOrchestrationService orchestration = new RecordingOrchestrationService(List.of(sampleRecommendation()));
        AgentRecommendationConsumer consumer = new AgentRecommendationConsumer(orchestration);

        List<SelfHealingRecommendation> first = consumer.consume(sampleDiagnosis());
        List<SelfHealingRecommendation> second = consumer.consume(sampleDiagnosis());

        assertThat(first).hasSize(1);
        assertThat(second).hasSize(1);
        assertThat(orchestration.callCount).isEqualTo(2);
    }

    // ------------------------------------------------------------------------------------------
    // Constructor integrity
    // ------------------------------------------------------------------------------------------

    @Test
    public void testConstructorRejectsNullOrchestrationService() {
        assertThatThrownBy(() -> new AgentRecommendationConsumer(null))
                .isInstanceOf(NullPointerException.class);
    }

    // ------------------------------------------------------------------------------------------
    // No duplicate orchestration logic — the consumer never calls recommendAndReport() itself.
    // ------------------------------------------------------------------------------------------

    @Test
    public void testConsumerNeverInvokesRecommendAndReport() {
        RecordingOrchestrationServiceTrackingBothMethods orchestration =
                new RecordingOrchestrationServiceTrackingBothMethods(List.of(sampleRecommendation()));
        AgentRecommendationConsumer consumer = new AgentRecommendationConsumer(orchestration);

        consumer.consume(sampleDiagnosis());

        assertThat(orchestration.recommendCallCount).isEqualTo(1);
        assertThat(orchestration.recommendAndReportCallCount).isZero();
    }

    private static class RecordingOrchestrationServiceTrackingBothMethods extends AgentOrchestrationService {
        private final List<SelfHealingRecommendation> toReturn;
        int recommendCallCount = 0;
        int recommendAndReportCallCount = 0;

        RecordingOrchestrationServiceTrackingBothMethods(List<SelfHealingRecommendation> toReturn) {
            super(new AgentReasoningService(), new AgentExecutionGuard(), new SelfHealingRecommendationService());
            this.toReturn = toReturn;
        }

        @Override
        public List<SelfHealingRecommendation> recommend(FailureDiagnosis failureDiagnosis) {
            recommendCallCount++;
            return toReturn;
        }

        @Override
        public List<SelfHealingRecommendation> recommendAndReport(FailureDiagnosis failureDiagnosis) {
            recommendAndReportCallCount++;
            return super.recommendAndReport(failureDiagnosis);
        }
    }

    // ------------------------------------------------------------------------------------------
    // Real, unmodified AgentOrchestrationService end-to-end sanity check (AI disabled by default)
    // ------------------------------------------------------------------------------------------

    @Test
    public void testRealOrchestrationServiceEndToEndProducesNoExceptionWithAiDisabled() {
        AgentRecommendationConsumer consumer = new AgentRecommendationConsumer(new AgentOrchestrationService());

        List<SelfHealingRecommendation> result = consumer.consume(sampleDiagnosis());

        assertThat(result).isNotNull();
    }
}
