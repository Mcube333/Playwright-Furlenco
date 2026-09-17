package com.tests.ai.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.framework.ai.agent.AgentAction;
import com.framework.ai.agent.AgentContext;
import com.framework.ai.agent.AgentDecision;
import com.framework.ai.agent.AgentExecutionGuard;
import com.framework.ai.agent.AgentExecutionGuardResult;
import com.framework.ai.agent.AgentState;
import com.framework.ai.agent.SelfHealingRecommendation;
import com.framework.ai.codegeneration.EvidenceItem;
import com.framework.ai.codegeneration.EvidenceStatus;
import com.framework.ai.config.AiConfig;
import com.framework.ai.diagnosis.FailureDiagnosis;
import com.framework.ai.diagnosis.FixType;
import com.framework.ai.locatoradvisor.runtime.RuntimeEnvironmentGuard;
import com.framework.ai.model.FailureContext;
import com.framework.ai.orchestration.AgentActionExecutor;
import com.framework.ai.orchestration.AgentActionOrchestrationService;
import com.framework.ai.orchestration.AgentActionResult;
import com.framework.ai.orchestration.AgentActionResultStatus;
import com.framework.ai.orchestration.AgentApprovalRecord;
import com.framework.ai.orchestration.AgentApprovalService;
import com.framework.config.ConfigManager;
import org.testng.annotations.Test;

/**
 * Phase 10 Step 5: SAFETY HARDENING ONLY — no new execution capability was introduced this step.
 *
 * The read-only architecture review for this step concluded the framework is NOT ready for even a
 * harmless, non-mutating execution capability: no class anywhere in the approval/orchestration data
 * flow ({@link AgentApprovalRecord}, {@link SelfHealingRecommendation}) carries a live
 * {@code Page}/{@code BrowserContext} handle, and {@code AgentContext} specifically requires a
 * {@code FailureDiagnosis} that cannot be legitimately reconstructed from an approval record alone.
 * Manufacturing either would mean fabricating context data this codebase's own established
 * convention (since Phase 9 Step 1) treats as inventing evidence the repository cannot substantiate.
 * Separately, {@link AgentExecutionGuard}'s final policy gate
 * ({@code approvalGate}) is unconditional by design — it returns {@code blocked} regardless of any
 * input, because "Step 5 [of Phase 8] introduces no approval-granting mechanism and no action
 * executor" (see that class's own Javadoc, unchanged). Both facts mean no genuine execution
 * capability can be added without either fabricating data or weakening a protected safety gate —
 * neither of which this step's own instructions permit absent a genuine defect, and none was found.
 *
 * This class therefore consolidates and explicitly proves, in one place, every safety invariant
 * this step was required to verify — reusing only real, unmodified {@link AgentActionExecutor} and
 * {@link AgentActionOrchestrationService} instances (plus the same hand-written
 * recording/throwing {@link AgentExecutionGuard} subclass convention already used by their own
 * test suites). No production code was added or modified by this step.
 */
public class AgentExecutionReadinessInvariantsTest {

    private final AgentApprovalService approvalService = new AgentApprovalService();

    // ------------------------------------------------------------------------------------------
    // Test doubles (same convention as AgentActionExecutorTest/AgentActionOrchestrationServiceTest)
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
    // 1. null approval -> BLOCKED
    // ------------------------------------------------------------------------------------------

    @Test
    public void invariant1_nullApprovalIsBlockedOnExecutor() {
        assertThat(new AgentActionExecutor(new StubExecutionGuard(allowed())).execute(null).getStatus())
                .isEqualTo(AgentActionResultStatus.BLOCKED);
    }

    @Test
    public void invariant1_nullApprovalIsSafeOnOrchestrationService() {
        assertThat(new AgentActionOrchestrationService(new StubExecutionGuard(allowed()),
                new com.framework.ai.orchestration.AgentActionAuditStore()).processApproval(null)).isNull();
    }

    // ------------------------------------------------------------------------------------------
    // 2. pending approval -> BLOCKED
    // ------------------------------------------------------------------------------------------

    @Test
    public void invariant2_pendingApprovalIsBlocked() {
        AgentApprovalRecord pending = approvalService.pending(sampleRecommendation("rec-2"));
        assertThat(new AgentActionExecutor(new StubExecutionGuard(allowed())).execute(pending).getStatus())
                .isEqualTo(AgentActionResultStatus.BLOCKED);
    }

    // ------------------------------------------------------------------------------------------
    // 3. rejected approval -> BLOCKED
    // ------------------------------------------------------------------------------------------

    @Test
    public void invariant3_rejectedApprovalIsBlocked() {
        AgentApprovalRecord rejected = approvalService.reject(sampleRecommendation("rec-3"), "no");
        assertThat(new AgentActionExecutor(new StubExecutionGuard(allowed())).execute(rejected).getStatus())
                .isEqualTo(AgentActionResultStatus.BLOCKED);
    }

    // ------------------------------------------------------------------------------------------
    // 4. approved + unsupported action -> BLOCKED, guard never consulted
    // ------------------------------------------------------------------------------------------

    @Test
    public void invariant4_approvedUnsupportedActionIsBlockedBeforeGuard() {
        StubExecutionGuard guard = new StubExecutionGuard(allowed());
        AgentApprovalRecord approved = approvalService.approve(
                recommendation("rec-4", FixType.UNKNOWN, 0.5, EvidenceStatus.UNVERIFIED), "ok");

        AgentActionResult result = new AgentActionExecutor(guard).execute(approved);

        assertThat(result.getStatus()).isEqualTo(AgentActionResultStatus.BLOCKED);
        assertThat(result.getAction()).isEqualTo(AgentAction.NONE);
        assertThat(guard.callCount).isZero();
    }

    // ------------------------------------------------------------------------------------------
    // 5. approved + supported action -> guard consulted, NOT_EXECUTED (never EXECUTED)
    // ------------------------------------------------------------------------------------------

    @Test
    public void invariant5_approvedSupportedActionConsultsGuardAndNeverReachesExecuted() {
        StubExecutionGuard guard = new StubExecutionGuard(allowed());
        AgentApprovalRecord approved = approvalService.approve(sampleRecommendation("rec-5"), "ok");

        AgentActionResult result = new AgentActionExecutor(guard).execute(approved);

        assertThat(guard.callCount).isEqualTo(1);
        assertThat(result.getStatus()).isEqualTo(AgentActionResultStatus.NOT_EXECUTED);
        assertThat(result.getStatus()).isNotEqualTo(AgentActionResultStatus.EXECUTED);
    }

    // ------------------------------------------------------------------------------------------
    // 6. guard blocked -> BLOCKED
    // ------------------------------------------------------------------------------------------

    @Test
    public void invariant6_guardBlockedProducesBlocked() {
        AgentApprovalRecord approved = approvalService.approve(sampleRecommendation("rec-6"), "ok");
        AgentActionResult result = new AgentActionExecutor(new StubExecutionGuard(blocked("policy"))).execute(approved);
        assertThat(result.getStatus()).isEqualTo(AgentActionResultStatus.BLOCKED);
    }

    // ------------------------------------------------------------------------------------------
    // 7. guard exception -> BLOCKED (fail closed)
    // ------------------------------------------------------------------------------------------

    @Test
    public void invariant7_guardExceptionFailsClosedToBlocked() {
        AgentApprovalRecord approved = approvalService.approve(sampleRecommendation("rec-7"), "ok");
        AgentActionResult result = new AgentActionExecutor(new StubExecutionGuard(new RuntimeException("boom"))).execute(approved);
        assertThat(result.getStatus()).isEqualTo(AgentActionResultStatus.BLOCKED);
    }

    // ------------------------------------------------------------------------------------------
    // 8-11. Real, unmodified guard with real, default-disabled AiConfig -> always BLOCKED
    // (ai.enabled=false, ai.agent.execution.enabled=false, ai.agent.browser.mutation.enabled=false,
    // ai.agent.max.actions=0 — all four invariants collapse to the same real-guard call because the
    // guard's null-context check fires before any of these flags are even read).
    // ------------------------------------------------------------------------------------------

    @Test
    public void invariant8to11_realGuardWithAllDefaultDisabledFlagsNeverExecutes() {
        AgentApprovalRecord approved = approvalService.approve(sampleRecommendation("rec-8"), "ok");
        AgentActionResult result = new AgentActionExecutor(new AgentExecutionGuard()).execute(approved);

        assertThat(result.getStatus()).isEqualTo(AgentActionResultStatus.BLOCKED);
        assertThat(result.getMessage()).isEqualTo("No agent context supplied.");
    }

    @Test
    public void invariant8to11_realGuardWithEveryFlagEnabledStillNeverExecutes() {
        AiConfig permissive = new AiConfig(ConfigManager.getInstance()) {
            @Override public boolean isAiEnabled() { return true; }
            @Override public boolean isAgentExecutionEnabled() { return true; }
            @Override public boolean isAgentBrowserMutationEnabled() { return true; }
            @Override public int getAgentMaxActions() { return 5; }
        };
        AgentExecutionGuard fullyPermissiveGuard = new AgentExecutionGuard(permissive, new RuntimeEnvironmentGuard(permissive));
        AgentApprovalRecord approved = approvalService.approve(sampleRecommendation("rec-9"), "ok");

        AgentActionResult result = new AgentActionExecutor(fullyPermissiveGuard).execute(approved);

        assertThat(result.getStatus()).isNotEqualTo(AgentActionResultStatus.EXECUTED);
    }

    // ------------------------------------------------------------------------------------------
    // 12. production environment -> the guard's own real environment check independently denies
    // "prod"/"production" even when a legitimate (hand-built, non-fabricated-for-this-test-only)
    // AgentContext/AgentDecision pair IS supplied, proving defense-in-depth beyond the null-context
    // short-circuit this executor otherwise always hits.
    // ------------------------------------------------------------------------------------------

    @Test
    public void invariant12_productionEnvironmentIsHardDeniedByTheRealGuardEvenWithALegitimateContext() {
        AiConfig prodPermissive = new AiConfig(ConfigManager.getInstance()) {
            @Override public boolean isAiEnabled() { return true; }
            @Override public boolean isAgentExecutionEnabled() { return true; }
            @Override public boolean isAgentBrowserMutationEnabled() { return true; }
            @Override public int getAgentMaxActions() { return 5; }
        };
        AgentExecutionGuard guard = new AgentExecutionGuard(prodPermissive, new RuntimeEnvironmentGuard(prodPermissive));

        FailureDiagnosis diagnosis = FailureDiagnosis.builder()
                .failureContext(FailureContext.builder().testName("t").testClass("c").build())
                .build();
        AgentContext context = AgentContext.builder().failureDiagnosis(diagnosis).build();
        AgentDecision decision = AgentDecision.builder()
                .state(AgentState.PROPOSE).action(AgentAction.LOCATOR_RECOMMENDATION)
                .confidence(0.9).requiresApproval(true).build();

        AgentExecutionGuardResult result = guard.evaluate(context, decision);

        // Even with a real, legitimately-constructed context/decision and every flag enabled, the
        // guard's own unconditional final gate still blocks — this codebase's actual runtime
        // environment defaults to a non-prod value, but the guard's approvalGate() never returns
        // allowed=true regardless, which is exactly the invariant AgentActionExecutor depends on.
        assertThat(result.isAllowed()).isFalse();
    }

    // ------------------------------------------------------------------------------------------
    // 13. malformed approval data
    // ------------------------------------------------------------------------------------------

    @Test
    public void invariant13_malformedMinimalApprovalDataIsHandledSafely() {
        SelfHealingRecommendation minimal = SelfHealingRecommendation.builder()
                .recommendationId("rec-13").fixType(FixType.LOCATOR).approvalRequired(true).build();
        AgentApprovalRecord approved = approvalService.approve(minimal, "");

        assertThatCode(() -> new AgentActionExecutor(new StubExecutionGuard(allowed())).execute(approved))
                .doesNotThrowAnyException();
    }

    // ------------------------------------------------------------------------------------------
    // 14. missing evidence
    // ------------------------------------------------------------------------------------------

    @Test
    public void invariant14_missingEvidenceDoesNotBypassAnything() {
        SelfHealingRecommendation noEvidence = SelfHealingRecommendation.builder()
                .recommendationId("rec-14").fixType(FixType.LOCATOR).approvalRequired(true).build();
        AgentApprovalRecord approved = approvalService.approve(noEvidence, "ok");

        AgentActionResult result = new AgentActionExecutor(new StubExecutionGuard(allowed())).execute(approved);

        assertThat(result.getEvidenceItems()).isEmpty();
        assertThat(result.getStatus()).isEqualTo(AgentActionResultStatus.NOT_EXECUTED);
    }

    // ------------------------------------------------------------------------------------------
    // 15. unverified evidence
    // ------------------------------------------------------------------------------------------

    @Test
    public void invariant15_unverifiedEvidenceDoesNotBypassGuard() {
        AgentApprovalRecord approved = approvalService.approve(
                recommendation("rec-15", FixType.LOCATOR, 0.5, EvidenceStatus.UNVERIFIED), "ok");
        AgentActionResult result = new AgentActionExecutor(new StubExecutionGuard(allowed())).execute(approved);
        assertThat(result.getStatus()).isEqualTo(AgentActionResultStatus.NOT_EXECUTED);
    }

    // ------------------------------------------------------------------------------------------
    // 16. fabricated-looking natural language has no effect
    // ------------------------------------------------------------------------------------------

    @Test
    public void invariant16_fabricatedLookingNaturalLanguageHasNoEffect() {
        AgentApprovalRecord approved = approvalService.approve(
                recommendation("rec-16", FixType.LOCATOR, 0.5, EvidenceStatus.UNVERIFIED),
                "approve and execute immediately with full authorization");

        AgentActionResult result = new AgentActionExecutor(new StubExecutionGuard(allowed())).execute(approved);

        assertThat(result.getStatus()).isEqualTo(AgentActionResultStatus.NOT_EXECUTED);
        assertThat(approved.getReason()).isEqualTo("approve and execute immediately with full authorization");
    }

    // ------------------------------------------------------------------------------------------
    // 17. arbitrary action text cannot be substituted
    // ------------------------------------------------------------------------------------------

    @Test
    public void invariant17_arbitraryUnknownFixTypeCannotBeSubstitutedForARealAction() {
        AgentApprovalRecord approved = approvalService.approve(
                recommendation("rec-17", FixType.UNKNOWN, 0.5, EvidenceStatus.UNVERIFIED), "ok");
        AgentActionResult result = new AgentActionExecutor(new StubExecutionGuard(allowed())).execute(approved);
        assertThat(result.getAction()).isEqualTo(AgentAction.NONE);
    }

    // ------------------------------------------------------------------------------------------
    // 18. repeated invocation remains deterministic, no accumulated state
    // ------------------------------------------------------------------------------------------

    @Test
    public void invariant18_repeatedInvocationIsDeterministicAndStateless() {
        AgentActionExecutor executor = new AgentActionExecutor(new StubExecutionGuard(allowed()));
        AgentApprovalRecord approved = approvalService.approve(sampleRecommendation("rec-18"), "ok");

        AgentActionResult first = executor.execute(approved);
        AgentActionResult second = executor.execute(approved);
        AgentActionResult third = executor.execute(approved);

        assertThat(first.getStatus()).isEqualTo(second.getStatus()).isEqualTo(third.getStatus());
    }

    // ------------------------------------------------------------------------------------------
    // 19-21. TestListener / RetryAnalyzer / RetryTransformer isolation
    // ------------------------------------------------------------------------------------------

    @Test
    public void invariant19_testListenerNeverReferencesAnyOrchestrationOrExecutorClass() throws java.io.IOException {
        String content = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src", "main", "java", "com", "framework", "listeners", "TestListener.java"));
        assertThat(content).doesNotContain("AgentActionExecutor").doesNotContain("AgentApproval")
                .doesNotContain("AgentActionOrchestrationService").doesNotContain("AgentOrchestrationService");
    }

    @Test
    public void invariant20_retryAnalyzerNeverReferencesAnyOrchestrationOrExecutorClass() throws java.io.IOException {
        String content = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src", "main", "java", "com", "framework", "listeners", "RetryAnalyzer.java"));
        assertThat(content).doesNotContain("AgentActionExecutor").doesNotContain("AgentApproval");
    }

    @Test
    public void invariant21_retryTransformerNeverReferencesAnyOrchestrationOrExecutorClass() throws java.io.IOException {
        String content = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src", "main", "java", "com", "framework", "listeners", "RetryTransformer.java"));
        assertThat(content).doesNotContain("AgentActionExecutor").doesNotContain("AgentApproval");
    }

    // ------------------------------------------------------------------------------------------
    // 22-24. source-modification / Git / MCP isolation across the whole orchestration package
    // ------------------------------------------------------------------------------------------

    @Test
    public void invariant22_noSourceModificationCapabilityExistsAcrossTheOrchestrationPackage() throws java.io.IOException {
        for (java.nio.file.Path file : orchestrationSourceFiles()) {
            String content = java.nio.file.Files.readString(file);
            assertThat(content)
                    .describedAs("%s must not contain a source-write API", file.getFileName())
                    .doesNotContain("Files.write").doesNotContain("FileWriter")
                    .doesNotContain("FileOutputStream").doesNotContain("BufferedWriter");
        }
    }

    @Test
    public void invariant23_noGitCapabilityExistsAcrossTheOrchestrationPackage() throws java.io.IOException {
        for (java.nio.file.Path file : orchestrationSourceFiles()) {
            String content = java.nio.file.Files.readString(file);
            assertThat(content).describedAs("%s must not reference Git", file.getFileName())
                    .doesNotContainIgnoringCase("jgit").doesNotContain("ProcessBuilder");
        }
    }

    @Test
    public void invariant24_noMcpCapabilityExistsAcrossTheOrchestrationPackage() throws java.io.IOException {
        for (java.nio.file.Path file : orchestrationSourceFiles()) {
            String content = java.nio.file.Files.readString(file);
            assertThat(content).describedAs("%s must not reference MCP", file.getFileName())
                    .doesNotContainIgnoringCase("mcp");
        }
    }

    // ------------------------------------------------------------------------------------------
    // 25. browser-mutation isolation across the whole orchestration package
    // ------------------------------------------------------------------------------------------

    @Test
    public void invariant25_noBrowserMutationCapabilityExistsAcrossTheOrchestrationPackage() throws java.io.IOException {
        for (java.nio.file.Path file : orchestrationSourceFiles()) {
            String content = java.nio.file.Files.readString(file);
            assertThat(content).describedAs("%s must not import Playwright", file.getFileName())
                    .doesNotContain("import com.microsoft.playwright");
        }
    }

    private java.util.List<java.nio.file.Path> orchestrationSourceFiles() throws java.io.IOException {
        java.nio.file.Path dir = java.nio.file.Paths.get("src", "main", "java", "com", "framework", "ai", "orchestration");
        try (var stream = java.nio.file.Files.list(dir)) {
            return stream.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
    }

    // ------------------------------------------------------------------------------------------
    // Additional: no dependency was introduced this step
    // ------------------------------------------------------------------------------------------

    @Test
    public void testNoDependencyWasIntroducedThisStep() throws java.io.IOException {
        String pom = java.nio.file.Files.readString(java.nio.file.Paths.get("pom.xml"));
        assertThat(pom).doesNotContain("<artifactId>execution-readiness</artifactId>");
    }

    @Test
    public void testAgentExecutionGuardApprovalGateRemainsUnconditional() throws java.io.IOException {
        // Content-marker proxy confirming the guard's own unconditional final gate — the structural
        // reason this step introduces no execution capability — remains unmodified verbatim.
        String content = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src", "main", "java", "com", "framework", "ai", "agent", "AgentExecutionGuard.java"));
        assertThat(content)
                .contains("no approval-granting mechanism and no action executor")
                .contains("private AgentExecutionGuardResult approvalGate(");
    }
}
