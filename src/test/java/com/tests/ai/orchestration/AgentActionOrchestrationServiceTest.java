package com.tests.ai.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.framework.ai.agent.AgentContext;
import com.framework.ai.agent.AgentDecision;
import com.framework.ai.agent.AgentExecutionGuard;
import com.framework.ai.agent.AgentExecutionGuardResult;
import com.framework.ai.agent.SelfHealingRecommendation;
import com.framework.ai.codegeneration.EvidenceItem;
import com.framework.ai.codegeneration.EvidenceStatus;
import com.framework.ai.diagnosis.FixType;
import com.framework.ai.orchestration.AgentActionAuditRecord;
import com.framework.ai.orchestration.AgentActionAuditStore;
import com.framework.ai.orchestration.AgentActionOrchestrationService;
import com.framework.ai.orchestration.AgentActionResultStatus;
import com.framework.ai.orchestration.AgentApprovalRecord;
import com.framework.ai.orchestration.AgentApprovalService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.testng.annotations.Test;

/**
 * Phase 10 Step 3: focused + adversarial tests for {@link AgentActionOrchestrationService}. Fully
 * hermetic — no AI, no Playwright, no network. {@link AgentExecutionGuard} is either the real,
 * unmodified class or a hand-written recording/throwing subclass (same convention already used by
 * {@code AgentOrchestrationServiceTest}); no Mockito.
 */
public class AgentActionOrchestrationServiceTest {

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
    // 1. approved action -> NOT_EXECUTED (guard allows, still never EXECUTED)
    // ------------------------------------------------------------------------------------------

    @Test
    public void testApprovedActionWithGuardAllowedProducesNotExecuted() {
        AgentApprovalRecord approval = approvalService.approve(sampleRecommendation("rec-1"), "ok");
        AgentActionAuditStore store = new AgentActionAuditStore();
        AgentActionOrchestrationService service = new AgentActionOrchestrationService(
                new StubExecutionGuard(allowed()), store);

        AgentActionAuditRecord audit = service.processApproval(approval);

        assertThat(audit.getActionResult().getStatus()).isEqualTo(AgentActionResultStatus.NOT_EXECUTED);
    }

    // ------------------------------------------------------------------------------------------
    // 2. rejected approval -> BLOCKED
    // ------------------------------------------------------------------------------------------

    @Test
    public void testRejectedApprovalProducesBlocked() {
        AgentApprovalRecord rejected = approvalService.reject(sampleRecommendation("rec-2"), "no");
        AgentActionOrchestrationService service = new AgentActionOrchestrationService(
                new StubExecutionGuard(allowed()), new AgentActionAuditStore());

        AgentActionAuditRecord audit = service.processApproval(rejected);

        assertThat(audit.getActionResult().getStatus()).isEqualTo(AgentActionResultStatus.BLOCKED);
    }

    // ------------------------------------------------------------------------------------------
    // 3. pending approval -> BLOCKED
    // ------------------------------------------------------------------------------------------

    @Test
    public void testPendingApprovalProducesBlocked() {
        AgentApprovalRecord pending = approvalService.pending(sampleRecommendation("rec-3"));
        AgentActionOrchestrationService service = new AgentActionOrchestrationService(
                new StubExecutionGuard(allowed()), new AgentActionAuditStore());

        AgentActionAuditRecord audit = service.processApproval(pending);

        assertThat(audit.getActionResult().getStatus()).isEqualTo(AgentActionResultStatus.BLOCKED);
    }

    // ------------------------------------------------------------------------------------------
    // 4. null approval -> safe result (no exception, nothing stored)
    // ------------------------------------------------------------------------------------------

    @Test
    public void testNullApprovalRecordReturnsNullAndStoresNothing() {
        AgentActionAuditStore store = new AgentActionAuditStore();
        AgentActionOrchestrationService service = new AgentActionOrchestrationService(new AgentExecutionGuard(), store);

        AgentActionAuditRecord audit = service.processApproval(null);

        assertThat(audit).isNull();
        assertThat(store.size()).isZero();
    }

    // ------------------------------------------------------------------------------------------
    // 5. null/unmapped action -> safe deterministic result, never a crash
    // ------------------------------------------------------------------------------------------

    @Test
    public void testUnmappedFixTypeProducesNoneActionSafely() {
        AgentApprovalRecord approval = approvalService.approve(
                recommendation("rec-5", FixType.UNKNOWN, 0.5, EvidenceStatus.UNVERIFIED), "ok");
        AgentActionOrchestrationService service = new AgentActionOrchestrationService(
                new StubExecutionGuard(blocked("no execution")), new AgentActionAuditStore());

        AgentActionAuditRecord audit = service.processApproval(approval);

        assertThat(audit.getActionResult().getAction()).isEqualTo(com.framework.ai.agent.AgentAction.NONE);
        assertThat(audit.getActionResult().getStatus()).isEqualTo(AgentActionResultStatus.BLOCKED);
    }

    // ------------------------------------------------------------------------------------------
    // 6. invalid/minimal approval record (no evidence, blank fields) -> safe result
    // ------------------------------------------------------------------------------------------

    @Test
    public void testMinimalRecommendationWithNoEvidenceIsHandledSafely() {
        SelfHealingRecommendation minimal = SelfHealingRecommendation.builder()
                .recommendationId("rec-6").fixType(FixType.LOCATOR).approvalRequired(true).build();
        AgentApprovalRecord approval = approvalService.approve(minimal, "");

        AgentActionOrchestrationService service = new AgentActionOrchestrationService(
                new StubExecutionGuard(allowed()), new AgentActionAuditStore());

        assertThatCode(() -> service.processApproval(approval)).doesNotThrowAnyException();
        assertThat(service.processApproval(approval).getActionResult().getEvidenceItems()).isEmpty();
    }

    // ------------------------------------------------------------------------------------------
    // 7. execution guard blocks -> BLOCKED
    // ------------------------------------------------------------------------------------------

    @Test
    public void testExecutionGuardBlockProducesBlockedWithGuardReason() {
        AgentApprovalRecord approval = approvalService.approve(sampleRecommendation("rec-7"), "ok");
        AgentActionOrchestrationService service = new AgentActionOrchestrationService(
                new StubExecutionGuard(blocked("Agent execution is disabled.")), new AgentActionAuditStore());

        AgentActionAuditRecord audit = service.processApproval(approval);

        assertThat(audit.getActionResult().getStatus()).isEqualTo(AgentActionResultStatus.BLOCKED);
        assertThat(audit.getActionResult().getMessage()).isEqualTo("Agent execution is disabled.");
    }

    // ------------------------------------------------------------------------------------------
    // 8. execution guard exception -> fail closed (BLOCKED, no exception escapes)
    // ------------------------------------------------------------------------------------------

    @Test
    public void testExecutionGuardExceptionFailsClosedToBlocked() {
        AgentApprovalRecord approval = approvalService.approve(sampleRecommendation("rec-8"), "ok");
        AgentActionOrchestrationService service = new AgentActionOrchestrationService(
                new StubExecutionGuard(new RuntimeException("guard exploded")), new AgentActionAuditStore());

        AgentActionAuditRecord audit = service.processApproval(approval);

        assertThat(audit.getActionResult().getStatus()).isEqualTo(AgentActionResultStatus.BLOCKED);
    }

    // ------------------------------------------------------------------------------------------
    // 9. approved does not become EXECUTED (across guard allowed/blocked/exception)
    // ------------------------------------------------------------------------------------------

    @Test
    public void testApprovedNeverBecomesExecutedRegardlessOfGuardOutcome() {
        AgentApprovalRecord approval1 = approvalService.approve(sampleRecommendation("rec-9a"), "ok");
        AgentApprovalRecord approval2 = approvalService.approve(sampleRecommendation("rec-9b"), "ok");
        AgentApprovalRecord approval3 = approvalService.approve(sampleRecommendation("rec-9c"), "ok");

        AgentActionAuditRecord r1 = new AgentActionOrchestrationService(new StubExecutionGuard(allowed()), new AgentActionAuditStore())
                .processApproval(approval1);
        AgentActionAuditRecord r2 = new AgentActionOrchestrationService(new StubExecutionGuard(blocked("x")), new AgentActionAuditStore())
                .processApproval(approval2);
        AgentActionAuditRecord r3 = new AgentActionOrchestrationService(new StubExecutionGuard(new RuntimeException("x")), new AgentActionAuditStore())
                .processApproval(approval3);

        assertThat(r1.getActionResult().getStatus()).isNotEqualTo(AgentActionResultStatus.EXECUTED);
        assertThat(r2.getActionResult().getStatus()).isNotEqualTo(AgentActionResultStatus.EXECUTED);
        assertThat(r3.getActionResult().getStatus()).isNotEqualTo(AgentActionResultStatus.EXECUTED);
    }

    // ------------------------------------------------------------------------------------------
    // 10. approved does not become FAILED (no operation is ever attempted)
    // ------------------------------------------------------------------------------------------

    @Test
    public void testApprovedNeverBecomesFailed() {
        AgentApprovalRecord approval = approvalService.approve(sampleRecommendation("rec-10"), "ok");
        AgentActionAuditRecord audit = new AgentActionOrchestrationService(
                new StubExecutionGuard(allowed()), new AgentActionAuditStore()).processApproval(approval);

        assertThat(audit.getActionResult().getStatus()).isNotEqualTo(AgentActionResultStatus.FAILED);
    }

    // ------------------------------------------------------------------------------------------
    // 11. VERIFIED evidence does not cause execution
    // ------------------------------------------------------------------------------------------

    @Test
    public void testVerifiedEvidenceDoesNotCauseExecution() {
        AgentApprovalRecord approval = approvalService.approve(
                recommendation("rec-11", FixType.LOCATOR, 0.9, EvidenceStatus.VERIFIED), "ok");
        AgentActionAuditRecord audit = new AgentActionOrchestrationService(
                new StubExecutionGuard(allowed()), new AgentActionAuditStore()).processApproval(approval);

        assertThat(audit.getActionResult().getStatus()).isEqualTo(AgentActionResultStatus.NOT_EXECUTED);
    }

    // ------------------------------------------------------------------------------------------
    // 12. confidence=1.0 does not cause execution
    // ------------------------------------------------------------------------------------------

    @Test
    public void testMaximumConfidenceDoesNotCauseExecution() {
        AgentApprovalRecord approval = approvalService.approve(
                recommendation("rec-12", FixType.LOCATOR, 1.0, EvidenceStatus.VERIFIED), "ok");
        AgentActionAuditRecord audit = new AgentActionOrchestrationService(
                new StubExecutionGuard(allowed()), new AgentActionAuditStore()).processApproval(approval);

        assertThat(audit.getActionResult().getStatus()).isNotEqualTo(AgentActionResultStatus.EXECUTED);
    }

    // ------------------------------------------------------------------------------------------
    // 13-14. audit record contains original approval + generated result
    // ------------------------------------------------------------------------------------------

    @Test
    public void testAuditRecordContainsTheExactOriginalApproval() {
        AgentApprovalRecord approval = approvalService.approve(sampleRecommendation("rec-13"), "ok");
        AgentActionAuditRecord audit = new AgentActionOrchestrationService(
                new StubExecutionGuard(allowed()), new AgentActionAuditStore()).processApproval(approval);

        assertThat(audit.getApprovalRecord()).isSameAs(approval);
    }

    @Test
    public void testAuditRecordContainsTheGeneratedResult() {
        AgentApprovalRecord approval = approvalService.approve(sampleRecommendation("rec-14"), "ok");
        AgentActionAuditRecord audit = new AgentActionOrchestrationService(
                new StubExecutionGuard(allowed()), new AgentActionAuditStore()).processApproval(approval);

        assertThat(audit.getActionResult()).isNotNull();
    }

    // ------------------------------------------------------------------------------------------
    // 15-16. result stored in AgentActionAuditStore, exactly one record
    // ------------------------------------------------------------------------------------------

    @Test
    public void testResultIsStoredInTheInjectedAuditStore() {
        AgentActionAuditStore store = new AgentActionAuditStore();
        AgentApprovalRecord approval = approvalService.approve(sampleRecommendation("rec-15"), "ok");
        AgentActionAuditRecord audit = new AgentActionOrchestrationService(new StubExecutionGuard(allowed()), store)
                .processApproval(approval);

        assertThat(store.findAll()).containsExactly(audit);
    }

    @Test
    public void testStoreReceivesExactlyOneAuditRecordPerCall() {
        AgentActionAuditStore store = new AgentActionAuditStore();
        AgentApprovalRecord approval = approvalService.approve(sampleRecommendation("rec-16"), "ok");
        new AgentActionOrchestrationService(new StubExecutionGuard(allowed()), store).processApproval(approval);

        assertThat(store.size()).isEqualTo(1);
    }

    // ------------------------------------------------------------------------------------------
    // 17. repeated explicit invocation remains deterministic
    // ------------------------------------------------------------------------------------------

    @Test
    public void testRepeatedInvocationRemainsDeterministic() {
        AgentActionAuditStore store = new AgentActionAuditStore();
        AgentApprovalRecord approval = approvalService.approve(sampleRecommendation("rec-17"), "ok");
        AgentActionOrchestrationService service = new AgentActionOrchestrationService(new StubExecutionGuard(allowed()), store);

        AgentActionAuditRecord first = service.processApproval(approval);
        AgentActionAuditRecord second = service.processApproval(approval);

        assertThat(first.getActionResult().getStatus()).isEqualTo(second.getActionResult().getStatus());
        assertThat(store.size()).isEqualTo(2);
    }

    // ------------------------------------------------------------------------------------------
    // 18. no source modification
    // ------------------------------------------------------------------------------------------

    @Test
    public void testProcessingAnApprovalNeverModifiesTheProductionSourceFile() throws IOException {
        Path thisFile = Paths.get("src", "main", "java", "com", "framework", "ai", "orchestration",
                "AgentActionOrchestrationService.java");
        String before = Files.readString(thisFile);

        AgentApprovalRecord approval = approvalService.approve(sampleRecommendation("rec-18"), "ok");
        new AgentActionOrchestrationService(new StubExecutionGuard(allowed()), new AgentActionAuditStore())
                .processApproval(approval);

        assertThat(Files.readString(thisFile)).isEqualTo(before);
    }

    // ------------------------------------------------------------------------------------------
    // 19-20. no browser/network invocation — structural corollary (full proof in boundary test)
    // ------------------------------------------------------------------------------------------

    @Test
    public void testServiceHasNoPlaywrightOrNetworkCapableFields() {
        for (var field : AgentActionOrchestrationService.class.getDeclaredFields()) {
            assertThat(field.getType().getName()).doesNotContain("playwright").doesNotContain("Socket");
        }
    }

    // ------------------------------------------------------------------------------------------
    // 21. no Git/MCP invocation — structural corollary
    // ------------------------------------------------------------------------------------------

    @Test
    public void testSourceContainsNoGitOrMcpReference() throws IOException {
        Path file = Paths.get("src", "main", "java", "com", "framework", "ai", "orchestration",
                "AgentActionOrchestrationService.java");
        String content = Files.readString(file);
        assertThat(content).doesNotContainIgnoringCase("mcp").doesNotContainIgnoringCase("jgit");
    }

    // ------------------------------------------------------------------------------------------
    // 22-23. no TestListener/RetryAnalyzer integration
    // ------------------------------------------------------------------------------------------

    @Test
    public void testTestListenerDoesNotReferenceTheNewOrchestrationClass() throws IOException {
        String content = Files.readString(Paths.get("src", "main", "java", "com", "framework", "listeners", "TestListener.java"));
        assertThat(content).doesNotContain("AgentActionOrchestrationService");
    }

    @Test
    public void testRetryAnalyzerDoesNotReferenceTheNewOrchestrationClass() throws IOException {
        String content = Files.readString(Paths.get("src", "main", "java", "com", "framework", "listeners", "RetryAnalyzer.java"));
        assertThat(content).doesNotContain("AgentActionOrchestrationService");
    }

    // ------------------------------------------------------------------------------------------
    // 24. no automatic retry — the guard is consulted exactly once per processApproval() call
    // ------------------------------------------------------------------------------------------

    @Test
    public void testGuardIsConsultedExactlyOncePerApprovedCallNeverRetried() {
        StubExecutionGuard guard = new StubExecutionGuard(blocked("x"));
        AgentApprovalRecord approval = approvalService.approve(sampleRecommendation("rec-24"), "ok");

        new AgentActionOrchestrationService(guard, new AgentActionAuditStore()).processApproval(approval);

        assertThat(guard.callCount).isEqualTo(1);
    }

    @Test
    public void testGuardIsNeverConsultedForRejectedOrPendingApprovals() {
        StubExecutionGuard guard = new StubExecutionGuard(allowed());
        AgentActionOrchestrationService service = new AgentActionOrchestrationService(guard, new AgentActionAuditStore());

        service.processApproval(approvalService.reject(sampleRecommendation("rec-24b"), "no"));
        service.processApproval(approvalService.pending(sampleRecommendation("rec-24c")));

        assertThat(guard.callCount).isZero();
    }

    // ------------------------------------------------------------------------------------------
    // 25. evidence remains unchanged (pass-through only)
    // ------------------------------------------------------------------------------------------

    @Test
    public void testEvidenceStatusAndConfidenceRemainUnchangedThroughProcessing() {
        SelfHealingRecommendation recommendation = recommendation("rec-25", FixType.LOCATOR, 0.73, EvidenceStatus.VERIFIED);
        AgentApprovalRecord approval = approvalService.approve(recommendation, "ok");

        AgentActionAuditRecord audit = new AgentActionOrchestrationService(
                new StubExecutionGuard(allowed()), new AgentActionAuditStore()).processApproval(approval);

        assertThat(audit.getActionResult().getEvidenceItems()).hasSize(1);
        assertThat(audit.getActionResult().getEvidenceItems().get(0).getStatus()).isEqualTo(EvidenceStatus.VERIFIED);
        assertThat(audit.getApprovalRecord().getRecommendation().getConfidence()).isEqualTo(0.73);
    }

    // ------------------------------------------------------------------------------------------
    // Constructor integrity
    // ------------------------------------------------------------------------------------------

    @Test
    public void testConstructorRejectsNullGuard() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                new AgentActionOrchestrationService(null, new AgentActionAuditStore()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void testConstructorRejectsNullStore() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                new AgentActionOrchestrationService(new AgentExecutionGuard(), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void testNoArgConstructorWiresRealCollaboratorsWithoutThrowing() {
        assertThatCode(AgentActionOrchestrationService::new).doesNotThrowAnyException();
    }

    // Real, unmodified AgentExecutionGuard end-to-end: with real config defaults (ai disabled),
    // the guard's own null-context check fires, and the result must be BLOCKED, never EXECUTED.
    @Test
    public void testRealUnmodifiedGuardEndToEndProducesBlockedNeverExecuted() {
        AgentApprovalRecord approval = approvalService.approve(sampleRecommendation("rec-real"), "ok");
        AgentActionOrchestrationService service = new AgentActionOrchestrationService();

        AgentActionAuditRecord audit = service.processApproval(approval);

        assertThat(audit.getActionResult().getStatus()).isEqualTo(AgentActionResultStatus.BLOCKED);
        assertThat(audit.getActionResult().getMessage()).isEqualTo("No agent context supplied.");
    }
}
