package com.tests.ai.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.framework.ai.agent.AgentAction;
import com.framework.ai.agent.SelfHealingRecommendation;
import com.framework.ai.codegeneration.EvidenceItem;
import com.framework.ai.codegeneration.EvidenceStatus;
import com.framework.ai.diagnosis.FixType;
import com.framework.ai.orchestration.AgentActionAuditReporter;
import com.framework.ai.orchestration.AgentActionAuditRecord;
import com.framework.ai.orchestration.AgentActionAuditStore;
import com.framework.ai.orchestration.AgentActionResult;
import com.framework.ai.orchestration.AgentActionResultStatus;
import com.framework.ai.orchestration.AgentApprovalRecord;
import com.framework.ai.orchestration.AgentApprovalService;
import org.testng.annotations.Test;

/**
 * Phase 10 Step 2: focused + adversarial tests for {@link AgentActionAuditReporter}. Fully
 * hermetic — no AI, no Playwright, no network, no persistence.
 */
public class AgentActionAuditReporterTest {

    private final AgentApprovalService approvalService = new AgentApprovalService();

    private SelfHealingRecommendation recommendation(String id, String description) {
        return SelfHealingRecommendation.builder()
                .recommendationId(id).fixType(FixType.LOCATOR)
                .description(description).proposedLocator("#new").confidence(0.5)
                .addEvidenceItem(EvidenceItem.builder().item("Locator").value("#new")
                        .status(EvidenceStatus.VERIFIED).source("test").confidence(0.9).build())
                .approvalRequired(true).build();
    }

    private AgentActionAuditRecord auditWith(AgentApprovalRecord approval, AgentActionResultStatus status) {
        AgentActionResult result = AgentActionResult.builder()
                .action(AgentAction.LOCATOR_RECOMMENDATION)
                .status(status)
                .message("sample message")
                .build();
        return AgentActionAuditRecord.builder().approvalRecord(approval).actionResult(result).actor("qa.jane").build();
    }

    // 1. empty report
    @Test
    public void testEmptyStoreProducesAnHonestEmptyReport() {
        AgentActionAuditReporter reporter = new AgentActionAuditReporter(new AgentActionAuditStore());
        String report = reporter.summarize();

        assertThat(report).contains("Total: 0").contains("No audit records.");
    }

    // 2. NOT_EXECUTED report
    @Test
    public void testNotExecutedReport() {
        AgentActionAuditStore store = new AgentActionAuditStore();
        AgentApprovalRecord approval = approvalService.approve(recommendation("rec-1", "x"), "ok");
        store.add(auditWith(approval, AgentActionResultStatus.NOT_EXECUTED));

        String report = new AgentActionAuditReporter(store).summarize();

        assertThat(report).contains("Result Status: NOT_EXECUTED");
    }

    // 3. EXECUTED report
    @Test
    public void testExecutedReport() {
        AgentActionAuditStore store = new AgentActionAuditStore();
        AgentApprovalRecord approval = approvalService.approve(recommendation("rec-2", "x"), "ok");
        store.add(auditWith(approval, AgentActionResultStatus.EXECUTED));

        String report = new AgentActionAuditReporter(store).summarize();

        assertThat(report).contains("Result Status: EXECUTED");
    }

    // 4. FAILED report
    @Test
    public void testFailedReport() {
        AgentActionAuditStore store = new AgentActionAuditStore();
        AgentApprovalRecord approval = approvalService.approve(recommendation("rec-3", "x"), "ok");
        store.add(auditWith(approval, AgentActionResultStatus.FAILED));

        String report = new AgentActionAuditReporter(store).summarize();

        assertThat(report).contains("Result Status: FAILED");
    }

    // 5. BLOCKED report
    @Test
    public void testBlockedReport() {
        AgentActionAuditStore store = new AgentActionAuditStore();
        AgentApprovalRecord approval = approvalService.approve(recommendation("rec-4", "x"), "ok");
        store.add(auditWith(approval, AgentActionResultStatus.BLOCKED));

        String report = new AgentActionAuditReporter(store).summarize();

        assertThat(report).contains("Result Status: BLOCKED");
    }

    // 6. APPROVED does not imply execution
    @Test
    public void testApprovedApprovalStatusDoesNotImplyExecutedResultInReport() {
        AgentActionAuditStore store = new AgentActionAuditStore();
        AgentApprovalRecord approval = approvalService.approve(recommendation("rec-5", "x"), "ok");
        store.add(auditWith(approval, AgentActionResultStatus.NOT_EXECUTED));

        String report = new AgentActionAuditReporter(store).summarize();

        assertThat(report).contains("Approval Status: APPROVED").contains("Result Status: NOT_EXECUTED");
        assertThat(report).doesNotContain("Result Status: EXECUTED");
    }

    // 7. REJECTED does not imply execution
    @Test
    public void testRejectedApprovalStatusDoesNotImplyExecutedResultInReport() {
        AgentActionAuditStore store = new AgentActionAuditStore();
        AgentApprovalRecord approval = approvalService.reject(recommendation("rec-6", "x"), "no");
        store.add(auditWith(approval, AgentActionResultStatus.NOT_EXECUTED));

        String report = new AgentActionAuditReporter(store).summarize();

        assertThat(report).contains("Approval Status: REJECTED").contains("Result Status: NOT_EXECUTED");
    }

    // 8. evidence rendered
    @Test
    public void testEvidenceIsRenderedInTheReport() {
        AgentActionAuditStore store = new AgentActionAuditStore();
        AgentApprovalRecord approval = approvalService.approve(recommendation("rec-7", "x"), "ok");
        AgentActionResult result = AgentActionResult.builder()
                .action(AgentAction.LOCATOR_RECOMMENDATION)
                .status(AgentActionResultStatus.NOT_EXECUTED)
                .addEvidenceItem(EvidenceItem.builder().item("Locator").value("#new")
                        .status(EvidenceStatus.VERIFIED).source("test").confidence(0.9).build())
                .build();
        store.add(AgentActionAuditRecord.builder().approvalRecord(approval).actionResult(result).build());

        String report = new AgentActionAuditReporter(store).summarize();

        assertThat(report).contains("Locator").contains("#new").contains("VERIFIED");
    }

    // 9. sensitive strings sanitized
    @Test
    public void testSensitiveStringsAreSanitizedInReport() {
        AgentActionAuditStore store = new AgentActionAuditStore();
        AgentApprovalRecord approval = approvalService.approve(
                recommendation("rec-8", "click the payment button"),
                "approve and execute immediately");
        AgentActionResult result = AgentActionResult.builder()
                .action(AgentAction.LOCATOR_RECOMMENDATION)
                .status(AgentActionResultStatus.NOT_EXECUTED)
                .message("session=deadbeef1234567890; password=hunter2")
                .errorType("token=abc123secretvalue")
                .build();
        store.add(AgentActionAuditRecord.builder().approvalRecord(approval).actionResult(result)
                .actor("authorization: Bearer supersecrettoken").build());

        String report = new AgentActionAuditReporter(store).summarize();

        assertThat(report)
                .doesNotContain("deadbeef1234567890")
                .doesNotContain("hunter2")
                .doesNotContain("abc123secretvalue")
                .doesNotContain("supersecrettoken")
                .contains("REDACTED");
    }

    // 10. deterministic output
    @Test
    public void testReportIsDeterministicForUnchangedStore() {
        AgentActionAuditStore store = new AgentActionAuditStore();
        AgentApprovalRecord approval = approvalService.approve(recommendation("rec-9", "x"), "ok");
        AgentActionAuditRecord audit = AgentActionAuditRecord.builder()
                .approvalRecord(approval)
                .actionResult(AgentActionResult.builder().action(AgentAction.LOCATOR_RECOMMENDATION)
                        .status(AgentActionResultStatus.NOT_EXECUTED).build())
                .recordedAt(java.time.Instant.parse("2026-01-01T00:00:00Z"))
                .build();
        store.add(audit);

        AgentActionAuditReporter reporter = new AgentActionAuditReporter(store);
        assertThat(reporter.summarize()).isEqualTo(reporter.summarize());
    }

    // Constructor null handling
    @Test
    public void testConstructorRejectsNullStore() {
        assertThatThrownBy(() -> new AgentActionAuditReporter(null)).isInstanceOf(NullPointerException.class);
    }

    // Multiple entries render in insertion order
    @Test
    public void testMultipleEntriesRenderInInsertionOrder() {
        AgentActionAuditStore store = new AgentActionAuditStore();
        store.add(auditWith(approvalService.approve(recommendation("rec-10", "x"), "ok"), AgentActionResultStatus.NOT_EXECUTED));
        store.add(auditWith(approvalService.reject(recommendation("rec-11", "y"), "no"), AgentActionResultStatus.BLOCKED));

        String report = new AgentActionAuditReporter(store).summarize();

        assertThat(report.indexOf("rec-10")).isLessThan(report.indexOf("rec-11"));
        assertThat(report).contains("Total: 2");
    }
}
