package com.tests.ai.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.framework.ai.agent.AgentAction;
import com.framework.ai.agent.AgentContext;
import com.framework.ai.agent.AgentDecision;
import com.framework.ai.agent.AgentExecutionGuard;
import com.framework.ai.agent.AgentExecutionGuardResult;
import com.framework.ai.agent.SelfHealingRecommendation;
import com.framework.ai.codegeneration.EvidenceItem;
import com.framework.ai.codegeneration.EvidenceStatus;
import com.framework.ai.config.AiConfig;
import com.framework.ai.diagnosis.FixType;
import com.framework.ai.locatoradvisor.runtime.RuntimeEnvironmentGuard;
import com.framework.ai.orchestration.AgentActionExecutor;
import com.framework.ai.orchestration.AgentActionResult;
import com.framework.ai.orchestration.AgentActionResultStatus;
import com.framework.ai.orchestration.AgentApprovalRecord;
import com.framework.ai.orchestration.AgentApprovalService;
import com.framework.config.ConfigManager;
import java.lang.reflect.Method;
import org.testng.annotations.Test;

/**
 * Phase 10 Step 4: focused + adversarial tests for {@link AgentActionExecutor}. Fully hermetic —
 * no AI, no Playwright, no network. {@link AgentExecutionGuard} is either the real, unmodified
 * class or a hand-written recording/throwing subclass (same convention already used by
 * {@code AgentActionOrchestrationServiceTest}); no Mockito.
 */
public class AgentActionExecutorTest {

    private final AgentApprovalService approvalService = new AgentApprovalService();

    // ------------------------------------------------------------------------------------------
    // Test doubles
    // ------------------------------------------------------------------------------------------

    private static class StubExecutionGuard extends AgentExecutionGuard {
        private final AgentExecutionGuardResult result;
        private final RuntimeException throwsException;
        int callCount = 0;

        StubExecutionGuard(AgentExecutionGuardResult result) {
            this.result = result;
            this.throwsException = null;
        }

        StubExecutionGuard(RuntimeException throwsException) {
            this.result = null;
            this.throwsException = throwsException;
        }

        @Override
        public AgentExecutionGuardResult evaluate(AgentContext context, AgentDecision decision) {
            callCount++;
            if (throwsException != null) {
                throw throwsException;
            }
            return result;
        }
    }

    private AgentExecutionGuardResult allowed() {
        return AgentExecutionGuardResult.builder().allowed(true).build();
    }

    private AgentExecutionGuardResult blocked(String reason) {
        return AgentExecutionGuardResult.builder().allowed(false).reason(reason).build();
    }

    /** A real, unmodified AgentExecutionGuard, but backed by an AiConfig with every flag flipped on. */
    private AgentExecutionGuard fullyPermissiveRealGuard() {
        AiConfig permissive = new AiConfig(ConfigManager.getInstance()) {
            @Override public boolean isAiEnabled() { return true; }
            @Override public boolean isAgentExecutionEnabled() { return true; }
            @Override public boolean isAgentBrowserMutationEnabled() { return true; }
            @Override public int getAgentMaxActions() { return 5; }
        };
        return new AgentExecutionGuard(permissive, new RuntimeEnvironmentGuard(permissive));
    }

    // ------------------------------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------------------------------

    private SelfHealingRecommendation recommendation(String id, FixType fixType, double confidence, EvidenceStatus status) {
        return SelfHealingRecommendation.builder()
                .recommendationId(id).fixType(fixType).description("x").proposedLocator("#new")
                .confidence(confidence)
                .addEvidenceItem(EvidenceItem.builder().item("Locator").value("#new")
                        .status(status).source("test").confidence(confidence).build())
                .approvalRequired(true).build();
    }

    private SelfHealingRecommendation sampleRecommendation(String id) {
        return recommendation(id, FixType.LOCATOR, 0.5, EvidenceStatus.UNVERIFIED);
    }

    // ------------------------------------------------------------------------------------------
    // 1. null approval
    // ------------------------------------------------------------------------------------------

    @Test
    public void testNullApprovalReturnsBlockedSafely() {
        AgentActionExecutor executor = new AgentActionExecutor(new StubExecutionGuard(allowed()));

        AgentActionResult result = executor.execute(null);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(AgentActionResultStatus.BLOCKED);
    }

    // ------------------------------------------------------------------------------------------
    // 2. PENDING approval
    // ------------------------------------------------------------------------------------------

    @Test
    public void testPendingApprovalIsBlockedAndGuardNotConsulted() {
        StubExecutionGuard guard = new StubExecutionGuard(allowed());
        AgentActionExecutor executor = new AgentActionExecutor(guard);
        AgentApprovalRecord pending = approvalService.pending(sampleRecommendation("rec-2"));

        AgentActionResult result = executor.execute(pending);

        assertThat(result.getStatus()).isEqualTo(AgentActionResultStatus.BLOCKED);
        assertThat(guard.callCount).isZero();
    }

    // ------------------------------------------------------------------------------------------
    // 3. REJECTED approval
    // ------------------------------------------------------------------------------------------

    @Test
    public void testRejectedApprovalIsBlockedAndGuardNotConsulted() {
        StubExecutionGuard guard = new StubExecutionGuard(allowed());
        AgentActionExecutor executor = new AgentActionExecutor(guard);
        AgentApprovalRecord rejected = approvalService.reject(sampleRecommendation("rec-3"), "no");

        AgentActionResult result = executor.execute(rejected);

        assertThat(result.getStatus()).isEqualTo(AgentActionResultStatus.BLOCKED);
        assertThat(guard.callCount).isZero();
    }

    // ------------------------------------------------------------------------------------------
    // 4. APPROVED approval (guard consulted)
    // ------------------------------------------------------------------------------------------

    @Test
    public void testApprovedApprovalConsultsTheGuard() {
        StubExecutionGuard guard = new StubExecutionGuard(allowed());
        AgentActionExecutor executor = new AgentActionExecutor(guard);
        AgentApprovalRecord approved = approvalService.approve(sampleRecommendation("rec-4"), "ok");

        executor.execute(approved);

        assertThat(guard.callCount).isEqualTo(1);
    }

    // ------------------------------------------------------------------------------------------
    // 5. approved + guard blocked
    // ------------------------------------------------------------------------------------------

    @Test
    public void testApprovedPlusGuardBlockedProducesBlocked() {
        AgentActionExecutor executor = new AgentActionExecutor(new StubExecutionGuard(blocked("Agent execution is disabled.")));
        AgentApprovalRecord approved = approvalService.approve(sampleRecommendation("rec-5"), "ok");

        AgentActionResult result = executor.execute(approved);

        assertThat(result.getStatus()).isEqualTo(AgentActionResultStatus.BLOCKED);
        assertThat(result.getMessage()).isEqualTo("Agent execution is disabled.");
    }

    // ------------------------------------------------------------------------------------------
    // 6. approved + guard allowed -> the mandatory central invariant test
    // ------------------------------------------------------------------------------------------

    /**
     * MANDATORY: APPROVED + guard allowed=true must still produce NOT_EXECUTED, never EXECUTED.
     * This is the single most important test in this file.
     */
    @Test
    public void testApprovedPlusGuardAllowedStillProducesNotExecuted() {
        AgentActionExecutor executor = new AgentActionExecutor(new StubExecutionGuard(allowed()));
        AgentApprovalRecord approved = approvalService.approve(sampleRecommendation("rec-6"), "ok");

        AgentActionResult result = executor.execute(approved);

        assertThat(result.getStatus()).isEqualTo(AgentActionResultStatus.NOT_EXECUTED);
        assertThat(result.getStatus()).isNotEqualTo(AgentActionResultStatus.EXECUTED);
    }

    // ------------------------------------------------------------------------------------------
    // 7. approved + VERIFIED evidence
    // ------------------------------------------------------------------------------------------

    @Test
    public void testApprovedWithVerifiedEvidenceStillProducesNotExecuted() {
        AgentActionExecutor executor = new AgentActionExecutor(new StubExecutionGuard(allowed()));
        AgentApprovalRecord approved = approvalService.approve(
                recommendation("rec-7", FixType.LOCATOR, 0.9, EvidenceStatus.VERIFIED), "ok");

        AgentActionResult result = executor.execute(approved);

        assertThat(result.getStatus()).isEqualTo(AgentActionResultStatus.NOT_EXECUTED);
    }

    // ------------------------------------------------------------------------------------------
    // 8. approved + confidence 1.0
    // ------------------------------------------------------------------------------------------

    @Test
    public void testApprovedWithMaximumConfidenceStillProducesNotExecuted() {
        AgentActionExecutor executor = new AgentActionExecutor(new StubExecutionGuard(allowed()));
        AgentApprovalRecord approved = approvalService.approve(
                recommendation("rec-8", FixType.LOCATOR, 1.0, EvidenceStatus.VERIFIED), "ok");

        AgentActionResult result = executor.execute(approved);

        assertThat(result.getStatus()).isNotEqualTo(AgentActionResultStatus.EXECUTED);
    }

    // ------------------------------------------------------------------------------------------
    // 9. approved + confidence 0
    // ------------------------------------------------------------------------------------------

    @Test
    public void testApprovedWithZeroConfidenceStillFollowsSameSafetyPath() {
        AgentActionExecutor executor = new AgentActionExecutor(new StubExecutionGuard(allowed()));
        AgentApprovalRecord approved = approvalService.approve(
                recommendation("rec-9", FixType.LOCATOR, 0.0, EvidenceStatus.MISSING), "ok");

        AgentActionResult result = executor.execute(approved);

        assertThat(result.getStatus()).isEqualTo(AgentActionResultStatus.NOT_EXECUTED);
    }

    // ------------------------------------------------------------------------------------------
    // 10. null/unsupported action -> BLOCKED, guard never consulted
    // ------------------------------------------------------------------------------------------

    @Test
    public void testUnsupportedActionIsBlockedBeforeGuardIsConsulted() {
        StubExecutionGuard guard = new StubExecutionGuard(allowed());
        AgentActionExecutor executor = new AgentActionExecutor(guard);
        AgentApprovalRecord approved = approvalService.approve(
                recommendation("rec-10", FixType.UNKNOWN, 0.5, EvidenceStatus.UNVERIFIED), "ok");

        AgentActionResult result = executor.execute(approved);

        assertThat(result.getStatus()).isEqualTo(AgentActionResultStatus.BLOCKED);
        assertThat(result.getAction()).isEqualTo(AgentAction.NONE);
        assertThat(guard.callCount).isZero();
    }

    // ------------------------------------------------------------------------------------------
    // 11. every existing AgentAction value: the 4 reachable via the FixType mapping, plus the
    // structural proof that SCREENSHOT/DOM_CAPTURE are honestly unreachable (not omitted).
    // ------------------------------------------------------------------------------------------

    @Test
    public void testLocatorFixTypeMapsToLocatorRecommendationAction() {
        AgentActionExecutor executor = new AgentActionExecutor(new StubExecutionGuard(allowed()));
        AgentApprovalRecord approved = approvalService.approve(
                recommendation("rec-11a", FixType.LOCATOR, 0.5, EvidenceStatus.UNVERIFIED), "ok");
        assertThat(executor.execute(approved).getAction()).isEqualTo(AgentAction.LOCATOR_RECOMMENDATION);
    }

    @Test
    public void testWaitFixTypeMapsToWaitRecommendationAction() {
        AgentActionExecutor executor = new AgentActionExecutor(new StubExecutionGuard(allowed()));
        AgentApprovalRecord approved = approvalService.approve(
                recommendation("rec-11b", FixType.WAIT, 0.5, EvidenceStatus.UNVERIFIED), "ok");
        assertThat(executor.execute(approved).getAction()).isEqualTo(AgentAction.WAIT_RECOMMENDATION);
    }

    @Test
    public void testAssertionAndTestDataFixTypesMapToAssertionRecommendationAction() {
        AgentActionExecutor executor = new AgentActionExecutor(new StubExecutionGuard(allowed()));
        AgentApprovalRecord assertionApproved = approvalService.approve(
                recommendation("rec-11c", FixType.ASSERTION, 0.5, EvidenceStatus.UNVERIFIED), "ok");
        AgentApprovalRecord testDataApproved = approvalService.approve(
                recommendation("rec-11d", FixType.TEST_DATA, 0.5, EvidenceStatus.UNVERIFIED), "ok");

        assertThat(executor.execute(assertionApproved).getAction()).isEqualTo(AgentAction.ASSERTION_RECOMMENDATION);
        assertThat(executor.execute(testDataApproved).getAction()).isEqualTo(AgentAction.ASSERTION_RECOMMENDATION);
    }

    @Test
    public void testUnmappedFixTypesProduceNoneAction() {
        AgentActionExecutor executor = new AgentActionExecutor(new StubExecutionGuard(allowed()));
        for (FixType unmapped : new FixType[] {FixType.API, FixType.ANALYTICS, FixType.APPLICATION_BEHAVIOR, FixType.UNKNOWN}) {
            AgentApprovalRecord approved = approvalService.approve(
                    recommendation("rec-11e-" + unmapped, unmapped, 0.5, EvidenceStatus.UNVERIFIED), "ok");
            assertThat(executor.execute(approved).getAction()).isEqualTo(AgentAction.NONE);
        }
    }

    @Test
    public void testScreenshotAndDomCaptureActionsAreStructurallyUnreachableButStillExistInTheEnum() {
        // Honest structural fact, not a defect: this executor derives its action label from the
        // existing FixType on a SelfHealingRecommendation, and no FixType maps to these two
        // Phase 8 observation-only actions. They remain valid, untouched AgentAction values.
        assertThat(AgentAction.SCREENSHOT).isNotNull();
        assertThat(AgentAction.DOM_CAPTURE).isNotNull();
        assertThat(AgentAction.values()).contains(AgentAction.SCREENSHOT, AgentAction.DOM_CAPTURE);
    }

    // ------------------------------------------------------------------------------------------
    // 12. malformed/incomplete approval (minimal recommendation, no evidence)
    // ------------------------------------------------------------------------------------------

    @Test
    public void testMinimalRecommendationWithNoEvidenceIsHandledSafely() {
        SelfHealingRecommendation minimal = SelfHealingRecommendation.builder()
                .recommendationId("rec-12").fixType(FixType.LOCATOR).approvalRequired(true).build();
        AgentApprovalRecord approved = approvalService.approve(minimal, "");
        AgentActionExecutor executor = new AgentActionExecutor(new StubExecutionGuard(allowed()));

        assertThatCode(() -> executor.execute(approved)).doesNotThrowAnyException();
        assertThat(executor.execute(approved).getEvidenceItems()).isEmpty();
    }

    // ------------------------------------------------------------------------------------------
    // 13. execution disabled (real guard, default config)
    // ------------------------------------------------------------------------------------------

    @Test
    public void testRealUnmodifiedGuardWithDefaultDisabledConfigProducesBlockedNeverExecuted() {
        AgentActionExecutor executor = new AgentActionExecutor(new AgentExecutionGuard());
        AgentApprovalRecord approved = approvalService.approve(sampleRecommendation("rec-13"), "ok");

        AgentActionResult result = executor.execute(approved);

        assertThat(result.getStatus()).isEqualTo(AgentActionResultStatus.BLOCKED);
        assertThat(result.getMessage()).isEqualTo("No agent context supplied.");
    }

    // ------------------------------------------------------------------------------------------
    // 14. execution flag enabled but still NOT_EXECUTED
    // 15. browser mutation enabled but still NOT_EXECUTED
    // 16. max actions > 0 but still NOT_EXECUTED
    // ------------------------------------------------------------------------------------------

    /**
     * MANDATORY combined proof: APPROVED + guard allowed + every execution flag enabled (ai.enabled,
     * ai.agent.execution.enabled, ai.agent.browser.mutation.enabled=true, ai.agent.max.actions=5)
     * still yields NOT_EXECUTED. Since this executor never reads AiConfig itself — it only ever
     * sees the guard's boolean verdict — "flags enabled" is represented by a guard that would say
     * allowed=true; test 17 additionally proves the REAL guard (which does read these exact flags)
     * cannot itself produce allowed=true from this executor's necessarily-null context, making a
     * stub the only way to exercise this scenario at all, which is exactly what this test does.
     */
    @Test
    public void testExecutionFlagsEnabledStillProducesNotExecutedNeverExecuted() {
        AgentActionExecutor executor = new AgentActionExecutor(new StubExecutionGuard(allowed()));
        AgentApprovalRecord approved = approvalService.approve(
                recommendation("rec-14", FixType.LOCATOR, 1.0, EvidenceStatus.VERIFIED), "ok");

        AgentActionResult result = executor.execute(approved);

        assertThat(result.getStatus()).isEqualTo(AgentActionResultStatus.NOT_EXECUTED);
        assertThat(result.getStatus()).isNotEqualTo(AgentActionResultStatus.EXECUTED);
    }

    @Test
    public void testBrowserMutationFlagEnabledStillProducesNotExecuted() {
        // Same rationale as above — this executor has no AiConfig dependency to even read
        // ai.agent.browser.mutation.enabled from; "enabled" is represented via the guard's answer.
        AgentActionExecutor executor = new AgentActionExecutor(new StubExecutionGuard(allowed()));
        AgentApprovalRecord approved = approvalService.approve(sampleRecommendation("rec-15"), "ok");

        assertThat(executor.execute(approved).getStatus()).isEqualTo(AgentActionResultStatus.NOT_EXECUTED);
    }

    @Test
    public void testMaxActionsGreaterThanZeroStillProducesNotExecuted() {
        AgentActionExecutor executor = new AgentActionExecutor(new StubExecutionGuard(allowed()));
        AgentApprovalRecord approved = approvalService.approve(sampleRecommendation("rec-16"), "ok");

        assertThat(executor.execute(approved).getStatus()).isEqualTo(AgentActionResultStatus.NOT_EXECUTED);
    }

    /** 17. Corollary proof using the REAL, unmodified guard with every flag genuinely flipped on. */
    @Test
    public void testRealGuardWithEveryFlagEnabledStillCannotProduceExecuted() {
        AgentActionExecutor executor = new AgentActionExecutor(fullyPermissiveRealGuard());
        AgentApprovalRecord approved = approvalService.approve(sampleRecommendation("rec-17"), "ok");

        AgentActionResult result = executor.execute(approved);

        // Even with every flag on, the real guard still blocks on the necessarily-null context —
        // and even if it did not, this class's own code has no path to EXECUTED regardless.
        assertThat(result.getStatus()).isNotEqualTo(AgentActionResultStatus.EXECUTED);
    }

    // ------------------------------------------------------------------------------------------
    // 18. guard exception -> fail closed
    // ------------------------------------------------------------------------------------------

    @Test
    public void testGuardExceptionFailsClosedToBlocked() {
        AgentActionExecutor executor = new AgentActionExecutor(new StubExecutionGuard(new RuntimeException("guard exploded")));
        AgentApprovalRecord approved = approvalService.approve(sampleRecommendation("rec-18"), "ok");

        AgentActionResult result = executor.execute(approved);

        assertThat(result.getStatus()).isEqualTo(AgentActionResultStatus.BLOCKED);
    }

    // ------------------------------------------------------------------------------------------
    // 19. repeated invocation remains deterministic
    // ------------------------------------------------------------------------------------------

    @Test
    public void testRepeatedInvocationRemainsDeterministic() {
        AgentActionExecutor executor = new AgentActionExecutor(new StubExecutionGuard(allowed()));
        AgentApprovalRecord approved = approvalService.approve(sampleRecommendation("rec-19"), "ok");

        AgentActionResult first = executor.execute(approved);
        AgentActionResult second = executor.execute(approved);

        assertThat(first.getStatus()).isEqualTo(second.getStatus());
        assertThat(first.getAction()).isEqualTo(second.getAction());
    }

    // ------------------------------------------------------------------------------------------
    // 20. no execution side effects
    // ------------------------------------------------------------------------------------------

    @Test
    public void testExecutingNeverModifiesTheApprovalRecordOrItsRecommendation() {
        AgentApprovalRecord approved = approvalService.approve(sampleRecommendation("rec-20"), "ok");
        var statusBefore = approved.getStatus();
        var confidenceBefore = approved.getRecommendation().getConfidence();

        new AgentActionExecutor(new StubExecutionGuard(allowed())).execute(approved);

        assertThat(approved.getStatus()).isEqualTo(statusBefore);
        assertThat(approved.getRecommendation().getConfidence()).isEqualTo(confidenceBefore);
    }

    // ------------------------------------------------------------------------------------------
    // 21. audit compatibility — the returned AgentActionResult can be wrapped in an
    // AgentActionAuditRecord exactly like AgentActionOrchestrationService already does, with no
    // schema changes required.
    // ------------------------------------------------------------------------------------------

    @Test
    public void testReturnedResultIsCompatibleWithTheExistingAuditRecordModel() {
        AgentApprovalRecord approved = approvalService.approve(sampleRecommendation("rec-21"), "ok");
        AgentActionResult result = new AgentActionExecutor(new StubExecutionGuard(allowed())).execute(approved);

        var audit = com.framework.ai.orchestration.AgentActionAuditRecord.builder()
                .approvalRecord(approved).actionResult(result).build();

        assertThat(audit.getActionResult()).isSameAs(result);
        assertThat(audit.getApprovalRecord()).isSameAs(approved);
    }

    // ------------------------------------------------------------------------------------------
    // Constructor integrity
    // ------------------------------------------------------------------------------------------

    @Test
    public void testConstructorRejectsNullGuard() {
        assertThatThrownBy(() -> new AgentActionExecutor(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    public void testNoArgConstructorWiresRealGuardWithoutThrowing() {
        assertThatCode(AgentActionExecutor::new).doesNotThrowAnyException();
    }

    // ------------------------------------------------------------------------------------------
    // No confidence field carried on AgentActionResult (structural corollary)
    // ------------------------------------------------------------------------------------------

    @Test
    public void testExecutorHasNoConfidenceReadingCapability() {
        for (Method m : AgentActionExecutor.class.getDeclaredMethods()) {
            assertThat(m.getName()).doesNotContain("Confidence");
        }
    }
}
