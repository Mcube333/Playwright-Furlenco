package com.tests.ai.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.framework.ai.agent.AgentAction;
import com.framework.ai.agent.AgentContext;
import com.framework.ai.agent.AgentDecision;
import com.framework.ai.agent.AgentExecutionGuard;
import com.framework.ai.agent.AgentExecutionGuardResult;
import com.framework.ai.agent.AgentReasoningService;
import com.framework.ai.agent.AgentState;
import com.framework.ai.agent.SelfHealingRecommendation;
import com.framework.ai.agent.SelfHealingRecommendationReporter;
import com.framework.ai.agent.SelfHealingRecommendationService;
import com.framework.ai.codegeneration.EvidenceItem;
import com.framework.ai.codegeneration.EvidenceStatus;
import com.framework.ai.diagnosis.FailureDiagnosis;
import com.framework.ai.diagnosis.FixType;
import com.framework.ai.model.FailureContext;
import com.framework.ai.orchestration.AgentOrchestrationService;
import java.util.List;
import org.testng.annotations.Test;

/**
 * Phase 9 Step 3: focused tests for {@link AgentOrchestrationService#recommendAndReport(FailureDiagnosis)}
 * — the explicit, human-triggered reporting entry point added on top of the already-approved Step
 * 2 pipeline. Fully hermetic: every collaborator is a hand-written recording/throwing subclass
 * (same convention as {@code AgentOrchestrationServiceTest}); no Mockito, no {@code ITestResult},
 * no Playwright type, no live Allure test context required (the real
 * {@link SelfHealingRecommendationReporter#attachToAllure} already never throws outside one).
 */
public class AgentOrchestrationReportingTest {

    // ------------------------------------------------------------------------------------------
    // Test doubles
    // ------------------------------------------------------------------------------------------

    private static class StubReasoningService extends AgentReasoningService {
        private final AgentDecision decision;
        private final RuntimeException throwsException;
        int callCount = 0;

        StubReasoningService(AgentDecision decision) {
            this.decision = decision;
            this.throwsException = null;
        }

        StubReasoningService(RuntimeException throwsException) {
            this.decision = null;
            this.throwsException = throwsException;
        }

        @Override
        public AgentDecision analyze(AgentContext context) {
            callCount++;
            if (throwsException != null) {
                throw throwsException;
            }
            return decision;
        }
    }

    private static class StubExecutionGuard extends AgentExecutionGuard {
        private final AgentExecutionGuardResult result;
        int callCount = 0;

        StubExecutionGuard(AgentExecutionGuardResult result) {
            this.result = result;
        }

        @Override
        public AgentExecutionGuardResult evaluate(AgentContext context, AgentDecision decision) {
            callCount++;
            return result;
        }
    }

    private static class StubRecommendationService extends SelfHealingRecommendationService {
        private final List<SelfHealingRecommendation> toReturn;
        private final RuntimeException throwsException;
        int callCount = 0;

        StubRecommendationService(List<SelfHealingRecommendation> toReturn) {
            this.toReturn = toReturn;
            this.throwsException = null;
        }

        StubRecommendationService(RuntimeException throwsException) {
            this.toReturn = null;
            this.throwsException = throwsException;
        }

        @Override
        public List<SelfHealingRecommendation> recommend(FailureDiagnosis diagnosis, AgentDecision decision,
                                                           AgentExecutionGuardResult guardResult) {
            callCount++;
            if (throwsException != null) {
                throw throwsException;
            }
            return toReturn;
        }
    }

    private static class RecordingReporter extends SelfHealingRecommendationReporter {
        private final RuntimeException throwsException;
        int callCount = 0;
        List<SelfHealingRecommendation> captured;

        RecordingReporter() {
            this.throwsException = null;
        }

        RecordingReporter(RuntimeException throwsException) {
            this.throwsException = throwsException;
        }

        @Override
        public void attachToAllure(List<SelfHealingRecommendation> recommendations) {
            callCount++;
            captured = recommendations;
            if (throwsException != null) {
                throw throwsException;
            }
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
        return AgentDecision.builder().state(AgentState.PROPOSE).action(action)
                .confidence(0.8).requiresApproval(true).build();
    }

    private AgentExecutionGuardResult allowedGuardResult() {
        return AgentExecutionGuardResult.builder().allowed(true).action(AgentAction.LOCATOR_RECOMMENDATION).build();
    }

    private SelfHealingRecommendation sampleRecommendation() {
        return SelfHealingRecommendation.builder()
                .recommendationId("rec-1")
                .fixType(FixType.LOCATOR)
                .description("Review the proposed locator.")
                .currentLocator("#old")
                .proposedLocator("#new")
                .rationale("Static evidence supports this candidate.")
                .confidence(0.73)
                .addEvidenceItem(EvidenceItem.builder().item("Locator").value("#new")
                        .status(EvidenceStatus.VERIFIED).source("test").confidence(0.9).build())
                .validationType(com.framework.ai.locatoradvisor.ValidationType.RUNTIME_VALIDATED)
                .approvalRequired(true)
                .build();
    }

    private SelfHealingRecommendation sensitiveRecommendation() {
        return SelfHealingRecommendation.builder()
                .recommendationId("rec-2")
                .fixType(FixType.LOCATOR)
                .description("Review locator; authorization: Bearer abc123secretvalue")
                .currentLocator("#old")
                .proposedLocator("#new")
                .rationale("session=deadbeef1234567890; cookie: sid=abcdef123456; password=hunter2")
                .confidence(0.5)
                .addEvidenceItem(EvidenceItem.builder().item("Locator").value("#new")
                        .status(EvidenceStatus.INFERRED).source("test").confidence(0.5).build())
                .approvalRequired(true)
                .build();
    }

    private AgentOrchestrationService orchestrator(AgentReasoningService reasoning, AgentExecutionGuard guard,
                                                     SelfHealingRecommendationService recommendationService,
                                                     SelfHealingRecommendationReporter reporter) {
        return new AgentOrchestrationService(reasoning, guard, recommendationService, reporter);
    }

    // ------------------------------------------------------------------------------------------
    // 1. recommendAndReport with a valid diagnosis
    // ------------------------------------------------------------------------------------------

    @Test
    public void testRecommendAndReportWithValidDiagnosisReturnsRecommendations() {
        List<SelfHealingRecommendation> expected = List.of(sampleRecommendation());
        AgentOrchestrationService service = orchestrator(
                new StubReasoningService(proposeDecision(AgentAction.LOCATOR_RECOMMENDATION)),
                new StubExecutionGuard(allowedGuardResult()),
                new StubRecommendationService(expected),
                new RecordingReporter());

        List<SelfHealingRecommendation> result = service.recommendAndReport(sampleDiagnosis());

        assertThat(result).isEqualTo(expected);
    }

    // ------------------------------------------------------------------------------------------
    // 2. Reporter receives the exact recommendations returned by orchestration
    // ------------------------------------------------------------------------------------------

    @Test
    public void testReporterReceivesExactRecommendationsFromOrchestration() {
        List<SelfHealingRecommendation> expected = List.of(sampleRecommendation());
        RecordingReporter reporter = new RecordingReporter();
        AgentOrchestrationService service = orchestrator(
                new StubReasoningService(proposeDecision(AgentAction.LOCATOR_RECOMMENDATION)),
                new StubExecutionGuard(allowedGuardResult()),
                new StubRecommendationService(expected),
                reporter);

        service.recommendAndReport(sampleDiagnosis());

        assertThat(reporter.captured).isSameAs(expected);
    }

    // ------------------------------------------------------------------------------------------
    // 3. Returned list preserves recommendation identity (not copied/rebuilt)
    // ------------------------------------------------------------------------------------------

    @Test
    public void testReturnedListPreservesRecommendationIdentity() {
        SelfHealingRecommendation recommendation = sampleRecommendation();
        List<SelfHealingRecommendation> expected = List.of(recommendation);
        AgentOrchestrationService service = orchestrator(
                new StubReasoningService(proposeDecision(AgentAction.LOCATOR_RECOMMENDATION)),
                new StubExecutionGuard(allowedGuardResult()),
                new StubRecommendationService(expected),
                new RecordingReporter());

        List<SelfHealingRecommendation> result = service.recommendAndReport(sampleDiagnosis());

        assertThat(result.get(0)).isSameAs(recommendation);
        assertThat(result.get(0).getRecommendationId()).isEqualTo("rec-1");
    }

    // ------------------------------------------------------------------------------------------
    // 4. Empty recommendation list
    // ------------------------------------------------------------------------------------------

    @Test
    public void testEmptyRecommendationListIsReportedAndReturnedAsIs() {
        RecordingReporter reporter = new RecordingReporter();
        AgentOrchestrationService service = orchestrator(
                new StubReasoningService(proposeDecision(AgentAction.NONE)),
                new StubExecutionGuard(allowedGuardResult()),
                new StubRecommendationService(List.of()),
                reporter);

        List<SelfHealingRecommendation> result = service.recommendAndReport(sampleDiagnosis());

        assertThat(result).isEmpty();
        assertThat(reporter.callCount).isEqualTo(1);
        assertThat(reporter.captured).isEmpty();
    }

    // ------------------------------------------------------------------------------------------
    // 5. Null FailureDiagnosis
    // ------------------------------------------------------------------------------------------

    @Test
    public void testNullFailureDiagnosisReturnsEmptyAndNeverReports() {
        StubReasoningService reasoning = new StubReasoningService(proposeDecision(AgentAction.NONE));
        StubExecutionGuard guard = new StubExecutionGuard(allowedGuardResult());
        StubRecommendationService recommendation = new StubRecommendationService(List.of());
        RecordingReporter reporter = new RecordingReporter();
        AgentOrchestrationService service = orchestrator(reasoning, guard, recommendation, reporter);

        List<SelfHealingRecommendation> result = service.recommendAndReport(null);

        assertThat(result).isEmpty();
        assertThat(reasoning.callCount).isZero();
        assertThat(guard.callCount).isZero();
        assertThat(recommendation.callCount).isZero();
        assertThat(reporter.callCount).isZero();
    }

    // ------------------------------------------------------------------------------------------
    // 6. Null recommendation result from the recommendation service
    // ------------------------------------------------------------------------------------------

    @Test
    public void testNullRecommendationResultIsNormalizedBeforeReporting() {
        RecordingReporter reporter = new RecordingReporter();
        AgentOrchestrationService service = orchestrator(
                new StubReasoningService(proposeDecision(AgentAction.LOCATOR_RECOMMENDATION)),
                new StubExecutionGuard(allowedGuardResult()),
                new StubRecommendationService((List<SelfHealingRecommendation>) null),
                reporter);

        List<SelfHealingRecommendation> result = service.recommendAndReport(sampleDiagnosis());

        assertThat(result).isNotNull().isEmpty();
        assertThat(reporter.captured).isNotNull().isEmpty();
    }

    // ------------------------------------------------------------------------------------------
    // 7. Reporter exception fails closed without discarding the recommendation result
    // ------------------------------------------------------------------------------------------

    @Test
    public void testReporterExceptionFailsClosedButStillReturnsRecommendations() {
        List<SelfHealingRecommendation> expected = List.of(sampleRecommendation());
        RecordingReporter reporter = new RecordingReporter(new RuntimeException("Allure attachment failed"));
        AgentOrchestrationService service = orchestrator(
                new StubReasoningService(proposeDecision(AgentAction.LOCATOR_RECOMMENDATION)),
                new StubExecutionGuard(allowedGuardResult()),
                new StubRecommendationService(expected),
                reporter);

        assertThatCode(() -> service.recommendAndReport(sampleDiagnosis()))
                .describedAs("reporting failure must never propagate")
                .doesNotThrowAnyException();

        List<SelfHealingRecommendation> result = service.recommendAndReport(sampleDiagnosis());

        assertThat(result).isEqualTo(expected);
        assertThat(reporter.callCount).isGreaterThanOrEqualTo(1);
    }

    // ------------------------------------------------------------------------------------------
    // 8. Orchestration (reasoning) exception fails closed; reporter is still explicitly invoked
    //    with the resulting empty list — reporting never re-runs orchestration.
    // ------------------------------------------------------------------------------------------

    @Test
    public void testOrchestrationExceptionFailsClosedAndReportsAnEmptyResult() {
        RecordingReporter reporter = new RecordingReporter();
        StubExecutionGuard guard = new StubExecutionGuard(allowedGuardResult());
        StubRecommendationService recommendation = new StubRecommendationService(List.of());
        AgentOrchestrationService service = orchestrator(
                new StubReasoningService(new RuntimeException("reasoning boom")),
                guard, recommendation, reporter);

        List<SelfHealingRecommendation> result = service.recommendAndReport(sampleDiagnosis());

        assertThat(result).isEmpty();
        assertThat(guard.callCount).isZero();
        assertThat(recommendation.callCount).isZero();
        assertThat(reporter.callCount).isEqualTo(1);
        assertThat(reporter.captured).isEmpty();
    }

    // ------------------------------------------------------------------------------------------
    // 9. Reporter invoked exactly once per call
    // ------------------------------------------------------------------------------------------

    @Test
    public void testReporterIsInvokedExactlyOncePerCall() {
        RecordingReporter reporter = new RecordingReporter();
        AgentOrchestrationService service = orchestrator(
                new StubReasoningService(proposeDecision(AgentAction.LOCATOR_RECOMMENDATION)),
                new StubExecutionGuard(allowedGuardResult()),
                new StubRecommendationService(List.of(sampleRecommendation())),
                reporter);

        service.recommendAndReport(sampleDiagnosis());

        assertThat(reporter.callCount).isEqualTo(1);
    }

    // ------------------------------------------------------------------------------------------
    // 10. Orchestration (recommendation service) invoked exactly once per call
    // ------------------------------------------------------------------------------------------

    @Test
    public void testRecommendationServiceIsInvokedExactlyOncePerCall() {
        StubRecommendationService recommendation = new StubRecommendationService(List.of());
        AgentOrchestrationService service = orchestrator(
                new StubReasoningService(proposeDecision(AgentAction.LOCATOR_RECOMMENDATION)),
                new StubExecutionGuard(allowedGuardResult()),
                recommendation,
                new RecordingReporter());

        service.recommendAndReport(sampleDiagnosis());

        assertThat(recommendation.callCount).isEqualTo(1);
    }

    // ------------------------------------------------------------------------------------------
    // 11. No retry — a throwing reporter is called exactly once, never retried.
    // ------------------------------------------------------------------------------------------

    @Test
    public void testThrowingReporterIsNotRetried() {
        RecordingReporter reporter = new RecordingReporter(new RuntimeException("attach failed"));
        AgentOrchestrationService service = orchestrator(
                new StubReasoningService(proposeDecision(AgentAction.LOCATOR_RECOMMENDATION)),
                new StubExecutionGuard(allowedGuardResult()),
                new StubRecommendationService(List.of()),
                reporter);

        service.recommendAndReport(sampleDiagnosis());

        assertThat(reporter.callCount).isEqualTo(1);
    }

    // ------------------------------------------------------------------------------------------
    // 12. No recursion — recommendAndReport does not call itself, and a single top-level call
    //     produces exactly one pass through every collaborator including recommend()'s own chain.
    // ------------------------------------------------------------------------------------------

    @Test
    public void testSingleCallProducesExactlyOnePassThroughEveryCollaborator() {
        StubReasoningService reasoning = new StubReasoningService(proposeDecision(AgentAction.LOCATOR_RECOMMENDATION));
        StubExecutionGuard guard = new StubExecutionGuard(allowedGuardResult());
        StubRecommendationService recommendation = new StubRecommendationService(List.of());
        RecordingReporter reporter = new RecordingReporter();
        AgentOrchestrationService service = orchestrator(reasoning, guard, recommendation, reporter);

        service.recommendAndReport(sampleDiagnosis());

        assertThat(reasoning.callCount).isEqualTo(1);
        assertThat(guard.callCount).isEqualTo(1);
        assertThat(recommendation.callCount).isEqualTo(1);
        assertThat(reporter.callCount).isEqualTo(1);
    }

    // ------------------------------------------------------------------------------------------
    // 13. No browser interaction — asserted structurally in AgentOrchestrationBoundaryTest; this
    //     test asserts the behavioral corollary that reporting never requires a Page/browser input.
    // ------------------------------------------------------------------------------------------

    @Test
    public void testRecommendAndReportRequiresNoBrowserOrPageArgument() throws NoSuchMethodException {
        var method = AgentOrchestrationService.class.getMethod("recommendAndReport", FailureDiagnosis.class);
        assertThat(method.getParameterCount()).isEqualTo(1);
        assertThat(method.getParameterTypes()[0]).isEqualTo(FailureDiagnosis.class);
    }

    // ------------------------------------------------------------------------------------------
    // 14. No AI interaction added by reporting — the reporter never calls reasoning again.
    // ------------------------------------------------------------------------------------------

    @Test
    public void testReportingStepNeverTriggersAdditionalReasoningCall() {
        StubReasoningService reasoning = new StubReasoningService(proposeDecision(AgentAction.LOCATOR_RECOMMENDATION));
        AgentOrchestrationService service = orchestrator(
                reasoning, new StubExecutionGuard(allowedGuardResult()),
                new StubRecommendationService(List.of(sampleRecommendation())),
                new RecordingReporter());

        service.recommendAndReport(sampleDiagnosis());

        // Exactly one reasoning call for the entire recommendAndReport() invocation — the
        // reporting step itself performs zero additional reasoning calls.
        assertThat(reasoning.callCount).isEqualTo(1);
    }

    // ------------------------------------------------------------------------------------------
    // 15. Evidence status preserved through the reporting path
    // ------------------------------------------------------------------------------------------

    @Test
    public void testEvidenceStatusIsPreservedThroughReporting() {
        SelfHealingRecommendation recommendation = sampleRecommendation();
        RecordingReporter reporter = new RecordingReporter();
        AgentOrchestrationService service = orchestrator(
                new StubReasoningService(proposeDecision(AgentAction.LOCATOR_RECOMMENDATION)),
                new StubExecutionGuard(allowedGuardResult()),
                new StubRecommendationService(List.of(recommendation)),
                reporter);

        List<SelfHealingRecommendation> result = service.recommendAndReport(sampleDiagnosis());

        assertThat(result.get(0).getEvidenceItems().get(0).getStatus()).isEqualTo(EvidenceStatus.VERIFIED);
        assertThat(reporter.captured.get(0).getEvidenceItems().get(0).getStatus()).isEqualTo(EvidenceStatus.VERIFIED);
    }

    // ------------------------------------------------------------------------------------------
    // 16. Confidence preserved through the reporting path
    // ------------------------------------------------------------------------------------------

    @Test
    public void testConfidencePreservedThroughReporting() {
        SelfHealingRecommendation recommendation = sampleRecommendation();
        RecordingReporter reporter = new RecordingReporter();
        AgentOrchestrationService service = orchestrator(
                new StubReasoningService(proposeDecision(AgentAction.LOCATOR_RECOMMENDATION)),
                new StubExecutionGuard(allowedGuardResult()),
                new StubRecommendationService(List.of(recommendation)),
                reporter);

        List<SelfHealingRecommendation> result = service.recommendAndReport(sampleDiagnosis());

        assertThat(result.get(0).getConfidence()).isEqualTo(0.73);
        assertThat(reporter.captured.get(0).getConfidence()).isEqualTo(0.73);
    }

    // ------------------------------------------------------------------------------------------
    // 17. approvalRequired remains true through the reporting path
    // ------------------------------------------------------------------------------------------

    @Test
    public void testApprovalRequiredRemainsTrueThroughReporting() {
        SelfHealingRecommendation recommendation = sampleRecommendation();
        AgentOrchestrationService service = orchestrator(
                new StubReasoningService(proposeDecision(AgentAction.LOCATOR_RECOMMENDATION)),
                new StubExecutionGuard(allowedGuardResult()),
                new StubRecommendationService(List.of(recommendation)),
                new RecordingReporter());

        List<SelfHealingRecommendation> result = service.recommendAndReport(sampleDiagnosis());

        assertThat(result.get(0).isApprovalRequired()).isTrue();
    }

    // ------------------------------------------------------------------------------------------
    // 18. Sensitive data remains sanitized through the real reporter's Markdown rendering
    // ------------------------------------------------------------------------------------------

    @Test
    public void testSensitiveValuesAreSanitizedByTheRealReporterMarkdownRendering() {
        SelfHealingRecommendation withSecrets = sensitiveRecommendation();
        SelfHealingRecommendationReporter realReporter = new SelfHealingRecommendationReporter();

        String rendered = realReporter.buildMarkdownReport(List.of(withSecrets));

        assertThat(rendered)
                .doesNotContain("abc123secretvalue")
                .doesNotContain("deadbeef1234567890")
                .doesNotContain("abcdef123456")
                .doesNotContain("hunter2")
                .contains("REDACTED");
    }

    @Test
    public void testRecommendAndReportWithRealReporterNeverThrowsOnSensitiveContent() {
        SelfHealingRecommendation withSecrets = sensitiveRecommendation();
        AgentOrchestrationService service = orchestrator(
                new StubReasoningService(proposeDecision(AgentAction.LOCATOR_RECOMMENDATION)),
                new StubExecutionGuard(allowedGuardResult()),
                new StubRecommendationService(List.of(withSecrets)),
                new SelfHealingRecommendationReporter());

        assertThatCode(() -> service.recommendAndReport(sampleDiagnosis())).doesNotThrowAnyException();
    }

    // ------------------------------------------------------------------------------------------
    // 19. Existing recommend(...) behavior remains unchanged by this step
    // ------------------------------------------------------------------------------------------

    @Test
    public void testExistingRecommendMethodNeverInvokesTheReporter() {
        RecordingReporter reporter = new RecordingReporter();
        List<SelfHealingRecommendation> expected = List.of(sampleRecommendation());
        AgentOrchestrationService service = orchestrator(
                new StubReasoningService(proposeDecision(AgentAction.LOCATOR_RECOMMENDATION)),
                new StubExecutionGuard(allowedGuardResult()),
                new StubRecommendationService(expected),
                reporter);

        List<SelfHealingRecommendation> result = service.recommend(sampleDiagnosis());

        assertThat(result).isEqualTo(expected);
        assertThat(reporter.callCount).isZero();
    }

    @Test
    public void testThreeArgConstructorStillWorksAndDefaultsToARealReporter() {
        AgentOrchestrationService service = new AgentOrchestrationService(
                new StubReasoningService(proposeDecision(AgentAction.LOCATOR_RECOMMENDATION)),
                new StubExecutionGuard(allowedGuardResult()),
                new StubRecommendationService(List.of(sampleRecommendation())));

        assertThatCode(() -> service.recommend(sampleDiagnosis())).doesNotThrowAnyException();
        assertThatCode(() -> service.recommendAndReport(sampleDiagnosis())).doesNotThrowAnyException();
    }

    // ------------------------------------------------------------------------------------------
    // 20. Explicit reporting only — no lifecycle integration (structural checks live in
    //     AgentOrchestrationBoundaryTest; this asserts the behavioral corollary that constructing
    //     the class and never calling recommendAndReport() never reports anything).
    // ------------------------------------------------------------------------------------------

    @Test
    public void testConstructingTheServiceAloneNeverInvokesTheReporter() {
        RecordingReporter reporter = new RecordingReporter();
        AgentOrchestrationService service = orchestrator(
                new StubReasoningService(proposeDecision(AgentAction.NONE)),
                new StubExecutionGuard(allowedGuardResult()),
                new StubRecommendationService(List.of()),
                reporter);

        assertThat(service).isNotNull();
        assertThat(reporter.callCount).isZero();
    }
}
