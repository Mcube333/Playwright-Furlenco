package com.tests.ai.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.framework.ai.agent.AgentAction;
import com.framework.ai.agent.SelfHealingRecommendation;
import com.framework.ai.diagnosis.FixType;
import com.framework.ai.orchestration.AgentActionAuditRecord;
import com.framework.ai.orchestration.AgentActionResult;
import com.framework.ai.orchestration.AgentActionResultStatus;
import com.framework.ai.orchestration.AgentApprovalRecord;
import com.framework.ai.orchestration.AgentApprovalService;
import com.framework.ai.orchestration.AgentApprovalStatus;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.Instant;
import org.testng.annotations.Test;

/**
 * Phase 10 Step 2: focused tests for the {@link AgentActionAuditRecord} immutable model. Fully
 * hermetic — no AI, no Playwright, no network. Approval records are produced via the real,
 * unmodified {@link AgentApprovalService} (Step 6), matching the existing project convention.
 */
public class AgentActionAuditRecordTest {

    private final AgentApprovalService approvalService = new AgentApprovalService();

    private SelfHealingRecommendation sampleRecommendation() {
        return SelfHealingRecommendation.builder()
                .recommendationId("rec-audit-1")
                .fixType(FixType.LOCATOR)
                .description("Review the proposed locator.")
                .proposedLocator("#new")
                .confidence(0.5)
                .approvalRequired(true)
                .build();
    }

    private AgentActionResult notExecutedResult() {
        return AgentActionResult.builder()
                .action(AgentAction.LOCATOR_RECOMMENDATION)
                .status(AgentActionResultStatus.NOT_EXECUTED)
                .message("No executor exists yet.")
                .build();
    }

    // 1. valid record creation
    @Test
    public void testValidRecordCreation() {
        AgentApprovalRecord approval = approvalService.approve(sampleRecommendation(), "ok");
        AgentActionResult result = notExecutedResult();

        AgentActionAuditRecord audit = AgentActionAuditRecord.builder()
                .approvalRecord(approval)
                .actionResult(result)
                .build();

        assertThat(audit).isNotNull();
    }

    // 2. approval record retained
    @Test
    public void testApprovalRecordRetainedByReference() {
        AgentApprovalRecord approval = approvalService.approve(sampleRecommendation(), "ok");
        AgentActionAuditRecord audit = AgentActionAuditRecord.builder()
                .approvalRecord(approval).actionResult(notExecutedResult()).build();

        assertThat(audit.getApprovalRecord()).isSameAs(approval);
    }

    // 3. action result retained
    @Test
    public void testActionResultRetainedByReference() {
        AgentActionResult result = notExecutedResult();
        AgentActionAuditRecord audit = AgentActionAuditRecord.builder()
                .approvalRecord(approvalService.approve(sampleRecommendation(), "ok"))
                .actionResult(result).build();

        assertThat(audit.getActionResult()).isSameAs(result);
    }

    // 4. immutable record
    @Test
    public void testAllDeclaredFieldsAreFinal() {
        for (Field field : AgentActionAuditRecord.class.getDeclaredFields()) {
            assertThat(Modifier.isFinal(field.getModifiers())).isTrue();
        }
    }

    @Test
    public void testNoSetterMethodsExist() {
        for (var method : AgentActionAuditRecord.class.getDeclaredMethods()) {
            assertThat(method.getName()).doesNotMatch("^set[A-Z].*");
        }
    }

    // 5. null approval record
    @Test
    public void testNullApprovalRecordRejected() {
        assertThatThrownBy(() -> AgentActionAuditRecord.builder().actionResult(notExecutedResult()).build())
                .isInstanceOf(NullPointerException.class);
    }

    // 6. null action result
    @Test
    public void testNullActionResultRejected() {
        assertThatThrownBy(() -> AgentActionAuditRecord.builder()
                .approvalRecord(approvalService.approve(sampleRecommendation(), "ok")).build())
                .isInstanceOf(NullPointerException.class);
    }

    // 7. actor retention
    @Test
    public void testActorIsRetainedExactly() {
        AgentActionAuditRecord audit = AgentActionAuditRecord.builder()
                .approvalRecord(approvalService.approve(sampleRecommendation(), "ok"))
                .actionResult(notExecutedResult())
                .actor("qa.jane")
                .build();

        assertThat(audit.getActor()).isEqualTo("qa.jane");
    }

    @Test
    public void testNullActorBecomesEmptyString() {
        AgentActionAuditRecord audit = AgentActionAuditRecord.builder()
                .approvalRecord(approvalService.approve(sampleRecommendation(), "ok"))
                .actionResult(notExecutedResult())
                .build();

        assertThat(audit.getActor()).isNotNull().isEmpty();
    }

    // 8. timestamp retention
    @Test
    public void testExplicitRecordedAtIsPreservedExactly() {
        Instant fixed = Instant.parse("2026-01-01T00:00:00Z");
        AgentActionAuditRecord audit = AgentActionAuditRecord.builder()
                .approvalRecord(approvalService.approve(sampleRecommendation(), "ok"))
                .actionResult(notExecutedResult())
                .recordedAt(fixed)
                .build();

        assertThat(audit.getRecordedAt()).isEqualTo(fixed);
    }

    @Test
    public void testRecordedAtDefaultsToNowWhenNotSupplied() {
        Instant before = Instant.now();
        AgentActionAuditRecord audit = AgentActionAuditRecord.builder()
                .approvalRecord(approvalService.approve(sampleRecommendation(), "ok"))
                .actionResult(notExecutedResult())
                .build();
        Instant after = Instant.now();

        assertThat(audit.getRecordedAt()).isBetween(before, after);
    }

    // 9. APPROVED + NOT_EXECUTED remains NOT_EXECUTED
    @Test
    public void testApprovedPlusNotExecutedRemainsNotExecuted() {
        AgentApprovalRecord approval = approvalService.approve(sampleRecommendation(), "ok");
        AgentActionAuditRecord audit = AgentActionAuditRecord.builder()
                .approvalRecord(approval).actionResult(notExecutedResult()).build();

        assertThat(audit.getApprovalRecord().getStatus()).isEqualTo(AgentApprovalStatus.APPROVED);
        assertThat(audit.getActionResult().getStatus()).isEqualTo(AgentActionResultStatus.NOT_EXECUTED);
    }

    // 10. REJECTED + NOT_EXECUTED remains NOT_EXECUTED
    @Test
    public void testRejectedPlusNotExecutedRemainsNotExecuted() {
        AgentApprovalRecord rejected = approvalService.reject(sampleRecommendation(), "no");
        AgentActionAuditRecord audit = AgentActionAuditRecord.builder()
                .approvalRecord(rejected).actionResult(notExecutedResult()).build();

        assertThat(audit.getApprovalRecord().getStatus()).isEqualTo(AgentApprovalStatus.REJECTED);
        assertThat(audit.getActionResult().getStatus()).isEqualTo(AgentActionResultStatus.NOT_EXECUTED);
    }

    // 11. no automatic status transformation — building an audit record never mutates the
    // approval's own status, regardless of what result status is paired with it.
    @Test
    public void testBuildingAnAuditRecordNeverChangesTheApprovalStatus() {
        AgentApprovalRecord approval = approvalService.approve(sampleRecommendation(), "ok");
        AgentApprovalStatus before = approval.getStatus();

        AgentActionResult executedResult = AgentActionResult.builder()
                .action(AgentAction.LOCATOR_RECOMMENDATION)
                .status(AgentActionResultStatus.EXECUTED)
                .build();
        AgentActionAuditRecord.builder().approvalRecord(approval).actionResult(executedResult).build();

        assertThat(approval.getStatus()).isEqualTo(before);
    }
}
