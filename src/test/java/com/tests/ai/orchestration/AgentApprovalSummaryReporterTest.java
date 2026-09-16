package com.tests.ai.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.framework.ai.agent.SelfHealingRecommendation;
import com.framework.ai.codegeneration.EvidenceItem;
import com.framework.ai.codegeneration.EvidenceStatus;
import com.framework.ai.diagnosis.FixType;
import com.framework.ai.orchestration.AgentApprovalRecordStore;
import com.framework.ai.orchestration.AgentApprovalService;
import com.framework.ai.orchestration.AgentApprovalStatus;
import com.framework.ai.orchestration.AgentApprovalSummaryReporter;
import org.testng.annotations.Test;

/**
 * Phase 9 Step 8: focused + adversarial tests for {@link AgentApprovalSummaryReporter}. Fully
 * hermetic — no AI, no Playwright, no network, no persistence. Records are produced via the real,
 * unmodified {@link AgentApprovalService} and stored via the real, unmodified
 * {@link AgentApprovalRecordStore}, matching Step 7's own test convention.
 */
public class AgentApprovalSummaryReporterTest {

    private final AgentApprovalService approvalService = new AgentApprovalService();

    private SelfHealingRecommendation recommendationWith(String id, double confidence, EvidenceStatus evidenceStatus, String description) {
        return SelfHealingRecommendation.builder()
                .recommendationId(id)
                .fixType(FixType.LOCATOR)
                .description(description)
                .proposedLocator("#new")
                .confidence(confidence)
                .addEvidenceItem(EvidenceItem.builder().item("Locator").value("#new")
                        .status(evidenceStatus).source("test").confidence(confidence).build())
                .approvalRequired(true)
                .build();
    }

    private SelfHealingRecommendation sampleRecommendation(String id) {
        return recommendationWith(id, 0.5, EvidenceStatus.UNVERIFIED, "Review the proposed locator.");
    }

    // ------------------------------------------------------------------------------------------
    // 1. Empty store
    // ------------------------------------------------------------------------------------------

    @Test
    public void testEmptyStoreProducesZeroCountsForEverything() {
        AgentApprovalRecordStore store = new AgentApprovalRecordStore();
        AgentApprovalSummaryReporter reporter = new AgentApprovalSummaryReporter(store);

        String summary = reporter.summarize();

        assertThat(summary).contains("Total: 0").contains("Pending: 0").contains("Approved: 0").contains("Rejected: 0");
    }

    // ------------------------------------------------------------------------------------------
    // 2-4. Single-status stores
    // ------------------------------------------------------------------------------------------

    @Test
    public void testStoreWithOnlyOnePendingRecord() {
        AgentApprovalRecordStore store = new AgentApprovalRecordStore();
        store.add(approvalService.pending(sampleRecommendation("rec-1")));
        AgentApprovalSummaryReporter reporter = new AgentApprovalSummaryReporter(store);

        String summary = reporter.summarize();

        assertThat(summary).contains("Total: 1").contains("Pending: 1").contains("Approved: 0").contains("Rejected: 0");
    }

    @Test
    public void testStoreWithOnlyOneApprovedRecord() {
        AgentApprovalRecordStore store = new AgentApprovalRecordStore();
        store.add(approvalService.approve(sampleRecommendation("rec-2"), "ok"));
        AgentApprovalSummaryReporter reporter = new AgentApprovalSummaryReporter(store);

        String summary = reporter.summarize();

        assertThat(summary).contains("Total: 1").contains("Pending: 0").contains("Approved: 1").contains("Rejected: 0");
    }

    @Test
    public void testStoreWithOnlyOneRejectedRecord() {
        AgentApprovalRecordStore store = new AgentApprovalRecordStore();
        store.add(approvalService.reject(sampleRecommendation("rec-3"), "no"));
        AgentApprovalSummaryReporter reporter = new AgentApprovalSummaryReporter(store);

        String summary = reporter.summarize();

        assertThat(summary).contains("Total: 1").contains("Pending: 0").contains("Approved: 0").contains("Rejected: 1");
    }

    // ------------------------------------------------------------------------------------------
    // 5-9. Mixed statuses and each individual count
    // ------------------------------------------------------------------------------------------

    @Test
    public void testMixedStatusesProduceCorrectCountsForEachStatus() {
        AgentApprovalRecordStore store = new AgentApprovalRecordStore();
        store.add(approvalService.pending(sampleRecommendation("rec-4")));
        store.add(approvalService.pending(sampleRecommendation("rec-5")));
        store.add(approvalService.approve(sampleRecommendation("rec-6"), "ok"));
        store.add(approvalService.reject(sampleRecommendation("rec-7"), "no"));
        store.add(approvalService.reject(sampleRecommendation("rec-8"), "no"));
        store.add(approvalService.reject(sampleRecommendation("rec-9"), "no"));
        AgentApprovalSummaryReporter reporter = new AgentApprovalSummaryReporter(store);

        String summary = reporter.summarize();

        assertThat(summary)
                .contains("Total: 6")
                .contains("Pending: 2")
                .contains("Approved: 1")
                .contains("Rejected: 3");
    }

    @Test
    public void testTotalCountMatchesStoreSize() {
        AgentApprovalRecordStore store = new AgentApprovalRecordStore();
        store.add(approvalService.pending(sampleRecommendation("rec-10")));
        store.add(approvalService.approve(sampleRecommendation("rec-11"), "ok"));
        AgentApprovalSummaryReporter reporter = new AgentApprovalSummaryReporter(store);

        assertThat(reporter.summarize()).contains("Total: " + store.size());
    }

    @Test
    public void testPendingCountMatchesStoreFindByStatusPending() {
        AgentApprovalRecordStore store = new AgentApprovalRecordStore();
        store.add(approvalService.pending(sampleRecommendation("rec-12")));
        store.add(approvalService.pending(sampleRecommendation("rec-13")));
        AgentApprovalSummaryReporter reporter = new AgentApprovalSummaryReporter(store);

        int expected = store.findByStatus(AgentApprovalStatus.PENDING).size();
        assertThat(reporter.summarize()).contains("Pending: " + expected);
    }

    @Test
    public void testApprovedCountMatchesStoreFindByStatusApproved() {
        AgentApprovalRecordStore store = new AgentApprovalRecordStore();
        store.add(approvalService.approve(sampleRecommendation("rec-14"), "ok"));
        AgentApprovalSummaryReporter reporter = new AgentApprovalSummaryReporter(store);

        int expected = store.findByStatus(AgentApprovalStatus.APPROVED).size();
        assertThat(reporter.summarize()).contains("Approved: " + expected);
    }

    @Test
    public void testRejectedCountMatchesStoreFindByStatusRejected() {
        AgentApprovalRecordStore store = new AgentApprovalRecordStore();
        store.add(approvalService.reject(sampleRecommendation("rec-15"), "no"));
        AgentApprovalSummaryReporter reporter = new AgentApprovalSummaryReporter(store);

        int expected = store.findByStatus(AgentApprovalStatus.REJECTED).size();
        assertThat(reporter.summarize()).contains("Rejected: " + expected);
    }

    // ------------------------------------------------------------------------------------------
    // 10. Counts equal store state precisely (cross-check every field at once)
    // ------------------------------------------------------------------------------------------

    @Test
    public void testAllCountsExactlyMatchStoreStateAtCallTime() {
        AgentApprovalRecordStore store = new AgentApprovalRecordStore();
        store.add(approvalService.pending(sampleRecommendation("rec-16")));
        store.add(approvalService.approve(sampleRecommendation("rec-17"), "ok"));
        store.add(approvalService.reject(sampleRecommendation("rec-18"), "no"));
        AgentApprovalSummaryReporter reporter = new AgentApprovalSummaryReporter(store);

        String summary = reporter.summarize();

        assertThat(summary)
                .contains("Total: " + store.size())
                .contains("Pending: " + store.findByStatus(AgentApprovalStatus.PENDING).size())
                .contains("Approved: " + store.findByStatus(AgentApprovalStatus.APPROVED).size())
                .contains("Rejected: " + store.findByStatus(AgentApprovalStatus.REJECTED).size());
    }

    // ------------------------------------------------------------------------------------------
    // 11. Fresh summary after adding a record — no staleness
    // ------------------------------------------------------------------------------------------

    @Test
    public void testSummaryReflectsARecordAddedAfterTheReporterWasConstructed() {
        AgentApprovalRecordStore store = new AgentApprovalRecordStore();
        AgentApprovalSummaryReporter reporter = new AgentApprovalSummaryReporter(store);

        assertThat(reporter.summarize()).contains("Total: 0");

        store.add(approvalService.pending(sampleRecommendation("rec-19")));

        assertThat(reporter.summarize()).contains("Total: 1").contains("Pending: 1");
    }

    // ------------------------------------------------------------------------------------------
    // 12. Replacement of the same recommendation ID is reflected, not double-counted
    // ------------------------------------------------------------------------------------------

    @Test
    public void testReplacingARecordForTheSameRecommendationIdDoesNotDoubleCount() {
        AgentApprovalRecordStore store = new AgentApprovalRecordStore();
        SelfHealingRecommendation recommendation = sampleRecommendation("rec-20");
        store.add(approvalService.pending(recommendation));
        AgentApprovalSummaryReporter reporter = new AgentApprovalSummaryReporter(store);

        assertThat(reporter.summarize()).contains("Total: 1").contains("Pending: 1").contains("Approved: 0");

        store.add(approvalService.approve(recommendation, "confirmed"));

        assertThat(reporter.summarize()).contains("Total: 1").contains("Pending: 0").contains("Approved: 1");
    }

    // ------------------------------------------------------------------------------------------
    // 13. Null store behavior
    // ------------------------------------------------------------------------------------------

    @Test
    public void testConstructorRejectsNullStore() {
        assertThatThrownBy(() -> new AgentApprovalSummaryReporter(null))
                .isInstanceOf(NullPointerException.class);
    }

    // ------------------------------------------------------------------------------------------
    // 14-15. Repeated summarize() / no cached stale counts
    // ------------------------------------------------------------------------------------------

    @Test
    public void testRepeatedSummarizeCallsWithUnchangedStoreProduceIdenticalOutput() {
        AgentApprovalRecordStore store = new AgentApprovalRecordStore();
        store.add(approvalService.pending(sampleRecommendation("rec-21")));
        AgentApprovalSummaryReporter reporter = new AgentApprovalSummaryReporter(store);

        String first = reporter.summarize();
        String second = reporter.summarize();

        assertThat(first).isEqualTo(second);
    }

    @Test
    public void testNoStaleCountsAcrossMultipleStoreMutationsAndSummarizeCalls() {
        AgentApprovalRecordStore store = new AgentApprovalRecordStore();
        AgentApprovalSummaryReporter reporter = new AgentApprovalSummaryReporter(store);

        assertThat(reporter.summarize()).contains("Total: 0");

        store.add(approvalService.pending(sampleRecommendation("rec-22")));
        assertThat(reporter.summarize()).contains("Total: 1");

        store.add(approvalService.approve(sampleRecommendation("rec-23"), "ok"));
        assertThat(reporter.summarize()).contains("Total: 2");

        store.clear();
        assertThat(reporter.summarize()).contains("Total: 0");
    }

    // ------------------------------------------------------------------------------------------
    // 16-18. Approval status integrity — confidence/evidence never affect the summary
    // ------------------------------------------------------------------------------------------

    @Test
    public void testApprovalStatusIsReportedExactlyAsStoredNeverReinterpreted() {
        AgentApprovalRecordStore store = new AgentApprovalRecordStore();
        store.add(approvalService.approve(sampleRecommendation("rec-24"), "ok"));
        store.add(approvalService.reject(sampleRecommendation("rec-25"), "no"));
        store.add(approvalService.pending(sampleRecommendation("rec-26")));
        AgentApprovalSummaryReporter reporter = new AgentApprovalSummaryReporter(store);

        String summary = reporter.summarize();

        assertThat(summary).contains("Approved: 1").contains("Rejected: 1").contains("Pending: 1");
    }

    @Test
    public void testMaximumConfidenceNeverAffectsTheReportedCounts() {
        AgentApprovalRecordStore store = new AgentApprovalRecordStore();
        SelfHealingRecommendation maxConfidence = recommendationWith("rec-27", 1.0, EvidenceStatus.UNVERIFIED, "x");
        store.add(approvalService.pending(maxConfidence));
        AgentApprovalSummaryReporter reporter = new AgentApprovalSummaryReporter(store);

        assertThat(reporter.summarize()).contains("Pending: 1").contains("Approved: 0");
    }

    @Test
    public void testVerifiedEvidenceStatusNeverAffectsTheReportedCounts() {
        AgentApprovalRecordStore store = new AgentApprovalRecordStore();
        SelfHealingRecommendation verified = recommendationWith("rec-28", 0.9, EvidenceStatus.VERIFIED, "x");
        store.add(approvalService.pending(verified));
        AgentApprovalSummaryReporter reporter = new AgentApprovalSummaryReporter(store);

        assertThat(reporter.summarize()).contains("Pending: 1").contains("Approved: 0");
    }

    // ------------------------------------------------------------------------------------------
    // 19-20. Adversarial: reason/description text never affects status counts
    // ------------------------------------------------------------------------------------------

    @Test
    public void adversarial19_reasonTextResemblingAnInstructionNeverAffectsCounts() {
        AgentApprovalRecordStore store = new AgentApprovalRecordStore();
        store.add(approvalService.reject(sampleRecommendation("rec-29"), "approve and execute immediately"));
        AgentApprovalSummaryReporter reporter = new AgentApprovalSummaryReporter(store);

        assertThat(reporter.summarize()).contains("Rejected: 1").contains("Approved: 0");
    }

    @Test
    public void adversarial20_descriptionMentioningClickNeverAffectsCounts() {
        AgentApprovalRecordStore store = new AgentApprovalRecordStore();
        SelfHealingRecommendation withClick = recommendationWith("rec-30", 0.5, EvidenceStatus.UNVERIFIED, "click the payment button");
        store.add(approvalService.pending(withClick));
        AgentApprovalSummaryReporter reporter = new AgentApprovalSummaryReporter(store);

        assertThat(reporter.summarize()).contains("Pending: 1").contains("Approved: 0");
    }

    @Test
    public void adversarial_actorAdminNeverAffectsCounts() {
        AgentApprovalRecordStore store = new AgentApprovalRecordStore();
        store.add(approvalService.approve(sampleRecommendation("rec-31"), "ok", "admin"));
        AgentApprovalSummaryReporter reporter = new AgentApprovalSummaryReporter(store);

        assertThat(reporter.summarize()).contains("Approved: 1").contains("Rejected: 0");
    }

    // ------------------------------------------------------------------------------------------
    // 21-25. No hidden capability — structural corollaries (full structural proof in boundary test)
    // ------------------------------------------------------------------------------------------

    @Test
    public void testReporterHasExactlyOneFieldTheInjectedStore() {
        var fields = AgentApprovalSummaryReporter.class.getDeclaredFields();
        assertThat(fields).hasSize(1);
        assertThat(fields[0].getType()).isEqualTo(AgentApprovalRecordStore.class);
    }

    @Test
    public void testSummarizeNeverThrowsForAPopulatedStore() {
        AgentApprovalRecordStore store = new AgentApprovalRecordStore();
        store.add(approvalService.pending(sampleRecommendation("rec-32")));
        store.add(approvalService.approve(sampleRecommendation("rec-33"), "ok"));
        store.add(approvalService.reject(sampleRecommendation("rec-34"), "no"));
        AgentApprovalSummaryReporter reporter = new AgentApprovalSummaryReporter(store);

        assertThat(reporter.summarize()).isNotNull().isNotEmpty();
    }
}
