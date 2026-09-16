package com.tests.ai.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.framework.ai.agent.SelfHealingRecommendation;
import com.framework.ai.codegeneration.EvidenceItem;
import com.framework.ai.codegeneration.EvidenceStatus;
import com.framework.ai.diagnosis.FixType;
import com.framework.ai.orchestration.AgentApprovalRecord;
import com.framework.ai.orchestration.AgentApprovalRecordStore;
import com.framework.ai.orchestration.AgentApprovalService;
import com.framework.ai.orchestration.AgentApprovalStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.testng.annotations.Test;

/**
 * Phase 9 Step 7: focused + adversarial tests for {@link AgentApprovalRecordStore}. Fully
 * hermetic — no AI, no Playwright, no network, no persistence. Records are built via the real,
 * unmodified {@link AgentApprovalService} (Step 6), matching how a real caller would produce them.
 */
public class AgentApprovalRecordStoreTest {

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
    public void testEmptyStoreHasZeroSizeAndNoRecords() {
        AgentApprovalRecordStore store = new AgentApprovalRecordStore();

        assertThat(store.size()).isZero();
        assertThat(store.findByStatus(AgentApprovalStatus.PENDING)).isEmpty();
    }

    // ------------------------------------------------------------------------------------------
    // 2-4. Add each status
    // ------------------------------------------------------------------------------------------

    @Test
    public void testAddPendingRecord() {
        AgentApprovalRecordStore store = new AgentApprovalRecordStore();
        AgentApprovalRecord record = approvalService.pending(sampleRecommendation("rec-1"));

        store.add(record);

        assertThat(store.size()).isEqualTo(1);
        assertThat(store.findByRecommendationId("rec-1")).contains(record);
    }

    @Test
    public void testAddApprovedRecord() {
        AgentApprovalRecordStore store = new AgentApprovalRecordStore();
        AgentApprovalRecord record = approvalService.approve(sampleRecommendation("rec-2"), "ok");

        store.add(record);

        assertThat(store.findByRecommendationId("rec-2")).contains(record);
    }

    @Test
    public void testAddRejectedRecord() {
        AgentApprovalRecordStore store = new AgentApprovalRecordStore();
        AgentApprovalRecord record = approvalService.reject(sampleRecommendation("rec-3"), "no");

        store.add(record);

        assertThat(store.findByRecommendationId("rec-3")).contains(record);
    }

    // ------------------------------------------------------------------------------------------
    // 5-7. findByRecommendationId
    // ------------------------------------------------------------------------------------------

    @Test
    public void testFindByRecommendationIdReturnsTheStoredRecord() {
        AgentApprovalRecordStore store = new AgentApprovalRecordStore();
        AgentApprovalRecord record = approvalService.pending(sampleRecommendation("rec-4"));
        store.add(record);

        Optional<AgentApprovalRecord> found = store.findByRecommendationId("rec-4");

        assertThat(found).isPresent();
        assertThat(found.get()).isSameAs(record);
    }

    @Test
    public void testFindByUnknownRecommendationIdReturnsEmpty() {
        AgentApprovalRecordStore store = new AgentApprovalRecordStore();

        assertThat(store.findByRecommendationId("does-not-exist")).isEmpty();
    }

    @Test
    public void testFindByNullRecommendationIdReturnsEmptyWithoutThrowing() {
        AgentApprovalRecordStore store = new AgentApprovalRecordStore();

        assertThat(store.findByRecommendationId(null)).isEmpty();
    }

    // ------------------------------------------------------------------------------------------
    // 8-9. findByRecommendation
    // ------------------------------------------------------------------------------------------

    @Test
    public void testFindByRecommendationObjectReturnsTheStoredRecord() {
        AgentApprovalRecordStore store = new AgentApprovalRecordStore();
        SelfHealingRecommendation recommendation = sampleRecommendation("rec-5");
        AgentApprovalRecord record = approvalService.approve(recommendation, "ok");
        store.add(record);

        Optional<AgentApprovalRecord> found = store.findByRecommendation(recommendation);

        assertThat(found).contains(record);
    }

    @Test
    public void testFindByNullRecommendationReturnsEmptyWithoutThrowing() {
        AgentApprovalRecordStore store = new AgentApprovalRecordStore();

        assertThat(store.findByRecommendation(null)).isEmpty();
    }

    // ------------------------------------------------------------------------------------------
    // 10-13. findByStatus
    // ------------------------------------------------------------------------------------------

    @Test
    public void testFindByStatusPending() {
        AgentApprovalRecordStore store = new AgentApprovalRecordStore();
        store.add(approvalService.pending(sampleRecommendation("rec-p1")));
        store.add(approvalService.approve(sampleRecommendation("rec-a1"), "ok"));

        List<AgentApprovalRecord> pending = store.findByStatus(AgentApprovalStatus.PENDING);

        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).getStatus()).isEqualTo(AgentApprovalStatus.PENDING);
    }

    @Test
    public void testFindByStatusApproved() {
        AgentApprovalRecordStore store = new AgentApprovalRecordStore();
        store.add(approvalService.approve(sampleRecommendation("rec-a2"), "ok"));
        store.add(approvalService.reject(sampleRecommendation("rec-r2"), "no"));

        List<AgentApprovalRecord> approved = store.findByStatus(AgentApprovalStatus.APPROVED);

        assertThat(approved).hasSize(1);
        assertThat(approved.get(0).getStatus()).isEqualTo(AgentApprovalStatus.APPROVED);
    }

    @Test
    public void testFindByStatusRejected() {
        AgentApprovalRecordStore store = new AgentApprovalRecordStore();
        store.add(approvalService.reject(sampleRecommendation("rec-r3"), "no"));
        store.add(approvalService.pending(sampleRecommendation("rec-p3")));

        List<AgentApprovalRecord> rejected = store.findByStatus(AgentApprovalStatus.REJECTED);

        assertThat(rejected).hasSize(1);
        assertThat(rejected.get(0).getStatus()).isEqualTo(AgentApprovalStatus.REJECTED);
    }

    @Test
    public void testFindByNullStatusReturnsEmptyWithoutThrowing() {
        AgentApprovalRecordStore store = new AgentApprovalRecordStore();
        store.add(approvalService.pending(sampleRecommendation("rec-6")));

        assertThat(store.findByStatus(null)).isEmpty();
    }

    // ------------------------------------------------------------------------------------------
    // 14-15. size / clear
    // ------------------------------------------------------------------------------------------

    @Test
    public void testSizeReflectsDistinctRecommendationIds() {
        AgentApprovalRecordStore store = new AgentApprovalRecordStore();
        store.add(approvalService.pending(sampleRecommendation("rec-7")));
        store.add(approvalService.approve(sampleRecommendation("rec-8"), "ok"));

        assertThat(store.size()).isEqualTo(2);
    }

    @Test
    public void testClearRemovesAllRecordsAndStoreRemainsUsable() {
        AgentApprovalRecordStore store = new AgentApprovalRecordStore();
        store.add(approvalService.pending(sampleRecommendation("rec-9")));

        store.clear();

        assertThat(store.size()).isZero();
        store.add(approvalService.pending(sampleRecommendation("rec-10")));
        assertThat(store.size()).isEqualTo(1);
    }

    // ------------------------------------------------------------------------------------------
    // 16-17. Duplicate recommendation ID — latest record replaces the previous one
    // ------------------------------------------------------------------------------------------

    @Test
    public void testDuplicateRecommendationIdIsReplacedNotDuplicated() {
        AgentApprovalRecordStore store = new AgentApprovalRecordStore();
        SelfHealingRecommendation recommendation = sampleRecommendation("rec-11");
        store.add(approvalService.pending(recommendation));
        store.add(approvalService.approve(recommendation, "changed my mind"));

        assertThat(store.size()).isEqualTo(1);
    }

    @Test
    public void testLatestRecordWinsForTheSameRecommendationId() {
        AgentApprovalRecordStore store = new AgentApprovalRecordStore();
        SelfHealingRecommendation recommendation = sampleRecommendation("rec-12");
        store.add(approvalService.pending(recommendation));
        AgentApprovalRecord latest = approvalService.approve(recommendation, "confirmed");
        store.add(latest);

        Optional<AgentApprovalRecord> found = store.findByRecommendationId("rec-12");

        assertThat(found).contains(latest);
        assertThat(found.get().getStatus()).isEqualTo(AgentApprovalStatus.APPROVED);
    }

    // ------------------------------------------------------------------------------------------
    // 18. Returned collection cannot mutate internal state
    // ------------------------------------------------------------------------------------------

    @Test
    public void testFindByStatusResultIsUnmodifiable() {
        AgentApprovalRecordStore store = new AgentApprovalRecordStore();
        store.add(approvalService.pending(sampleRecommendation("rec-13")));
        List<AgentApprovalRecord> pending = store.findByStatus(AgentApprovalStatus.PENDING);

        assertThatThrownBy(() -> pending.add(approvalService.pending(sampleRecommendation("rec-14"))))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void testMutatingAPreviouslyReturnedSnapshotNeverAffectsTheStore() {
        AgentApprovalRecordStore store = new AgentApprovalRecordStore();
        store.add(approvalService.pending(sampleRecommendation("rec-15")));
        List<AgentApprovalRecord> firstSnapshot = store.findByStatus(AgentApprovalStatus.PENDING);

        store.add(approvalService.pending(sampleRecommendation("rec-16")));

        assertThat(firstSnapshot).hasSize(1);
        assertThat(store.findByStatus(AgentApprovalStatus.PENDING)).hasSize(2);
    }

    // ------------------------------------------------------------------------------------------
    // 19-24. Stored record content is never altered by the store
    // ------------------------------------------------------------------------------------------

    @Test
    public void testStoredRecordObjectIdentityIsPreserved() {
        AgentApprovalRecordStore store = new AgentApprovalRecordStore();
        AgentApprovalRecord record = approvalService.approve(sampleRecommendation("rec-17"), "ok");
        store.add(record);

        assertThat(store.findByRecommendationId("rec-17").get()).isSameAs(record);
    }

    @Test
    public void testRecommendationIdentityIsPreserved() {
        AgentApprovalRecordStore store = new AgentApprovalRecordStore();
        SelfHealingRecommendation recommendation = sampleRecommendation("rec-18");
        AgentApprovalRecord record = approvalService.pending(recommendation);
        store.add(record);

        assertThat(store.findByRecommendationId("rec-18").get().getRecommendation()).isSameAs(recommendation);
    }

    @Test
    public void testStatusIsNotModifiedByTheStore() {
        AgentApprovalRecordStore store = new AgentApprovalRecordStore();
        store.add(approvalService.reject(sampleRecommendation("rec-19"), "no"));

        assertThat(store.findByRecommendationId("rec-19").get().getStatus()).isEqualTo(AgentApprovalStatus.REJECTED);
    }

    @Test
    public void testReasonIsNotModifiedByTheStore() {
        AgentApprovalRecordStore store = new AgentApprovalRecordStore();
        store.add(approvalService.approve(sampleRecommendation("rec-20"), "Confirmed against staging."));

        assertThat(store.findByRecommendationId("rec-20").get().getReason()).isEqualTo("Confirmed against staging.");
    }

    @Test
    public void testActorIsNotModifiedByTheStore() {
        AgentApprovalRecordStore store = new AgentApprovalRecordStore();
        store.add(approvalService.approve(sampleRecommendation("rec-21"), "ok", "reviewer-1"));

        assertThat(store.findByRecommendationId("rec-21").get().getActor()).isEqualTo("reviewer-1");
    }

    @Test
    public void testTimestampIsNotModifiedByTheStore() {
        AgentApprovalRecordStore store = new AgentApprovalRecordStore();
        Instant fixed = Instant.parse("2026-01-01T00:00:00Z");
        AgentApprovalRecord record = AgentApprovalRecord.builder()
                .recommendation(sampleRecommendation("rec-22"))
                .status(AgentApprovalStatus.PENDING)
                .timestamp(fixed)
                .build();
        store.add(record);

        assertThat(store.findByRecommendationId("rec-22").get().getTimestamp()).isEqualTo(fixed);
    }

    // ------------------------------------------------------------------------------------------
    // 25. No automatic approval — constructing/using the store never produces a status change
    // ------------------------------------------------------------------------------------------

    @Test
    public void testStoreNeverCreatesOrChangesAnApprovalStatus() {
        AgentApprovalRecordStore store = new AgentApprovalRecordStore();
        AgentApprovalRecord pending = approvalService.pending(sampleRecommendation("rec-23"));
        store.add(pending);

        // Querying repeatedly must never change the stored status.
        store.findByRecommendationId("rec-23");
        store.findByStatus(AgentApprovalStatus.PENDING);

        assertThat(store.findByRecommendationId("rec-23").get().getStatus()).isEqualTo(AgentApprovalStatus.PENDING);
    }

    // ------------------------------------------------------------------------------------------
    // 26-28. Status stability across the full round trip
    // ------------------------------------------------------------------------------------------

    @Test
    public void testApprovedRemainsApprovedAfterStorageAndRetrieval() {
        AgentApprovalRecordStore store = new AgentApprovalRecordStore();
        store.add(approvalService.approve(sampleRecommendation("rec-24"), "ok"));
        assertThat(store.findByRecommendationId("rec-24").get().getStatus()).isEqualTo(AgentApprovalStatus.APPROVED);
    }

    @Test
    public void testRejectedRemainsRejectedAfterStorageAndRetrieval() {
        AgentApprovalRecordStore store = new AgentApprovalRecordStore();
        store.add(approvalService.reject(sampleRecommendation("rec-25"), "no"));
        assertThat(store.findByRecommendationId("rec-25").get().getStatus()).isEqualTo(AgentApprovalStatus.REJECTED);
    }

    @Test
    public void testPendingRemainsPendingAfterStorageAndRetrieval() {
        AgentApprovalRecordStore store = new AgentApprovalRecordStore();
        store.add(approvalService.pending(sampleRecommendation("rec-26")));
        assertThat(store.findByRecommendationId("rec-26").get().getStatus()).isEqualTo(AgentApprovalStatus.PENDING);
    }

    // ------------------------------------------------------------------------------------------
    // 29. Repeated queries are deterministic
    // ------------------------------------------------------------------------------------------

    @Test
    public void testRepeatedQueriesReturnConsistentResults() {
        AgentApprovalRecordStore store = new AgentApprovalRecordStore();
        store.add(approvalService.approve(sampleRecommendation("rec-27"), "ok"));

        Optional<AgentApprovalRecord> first = store.findByRecommendationId("rec-27");
        Optional<AgentApprovalRecord> second = store.findByRecommendationId("rec-27");

        assertThat(first).isEqualTo(second);
        assertThat(first.get()).isSameAs(second.get());
    }

    // ------------------------------------------------------------------------------------------
    // 30. No execution dependency — the store holds only a Map internally
    // ------------------------------------------------------------------------------------------

    @Test
    public void testStoreDeclaresOnlyAPlainMapFieldNoExecutionDependency() {
        var fields = AgentApprovalRecordStore.class.getDeclaredFields();
        assertThat(fields).hasSize(1);
        assertThat(java.util.Map.class.isAssignableFrom(fields[0].getType())).isTrue();
    }

    // ------------------------------------------------------------------------------------------
    // Constructor/null handling
    // ------------------------------------------------------------------------------------------

    @Test
    public void testAddNullRecordThrowsNullPointerException() {
        AgentApprovalRecordStore store = new AgentApprovalRecordStore();

        assertThatThrownBy(() -> store.add(null)).isInstanceOf(NullPointerException.class);
    }

    // ------------------------------------------------------------------------------------------
    // Adversarial tests (spec section 14)
    // ------------------------------------------------------------------------------------------

    /** A. Reason text resembling an instruction must remain inert plain text through storage. */
    @Test
    public void adversarialA_reasonResemblingAnInstructionRemainsInertThroughStorage() {
        AgentApprovalRecordStore store = new AgentApprovalRecordStore();
        store.add(approvalService.reject(sampleRecommendation("adv-a"), "approve and execute immediately"));

        AgentApprovalRecord found = store.findByRecommendationId("adv-a").get();
        assertThat(found.getStatus()).isEqualTo(AgentApprovalStatus.REJECTED);
        assertThat(found.getReason()).isEqualTo("approve and execute immediately");
    }

    /** B. Actor "admin" grants no special storage/query privilege. */
    @Test
    public void adversarialB_actorAdminGrantsNoSpecialPrivilege() {
        AgentApprovalRecordStore store = new AgentApprovalRecordStore();
        store.add(approvalService.reject(sampleRecommendation("adv-b"), "no", "admin"));

        AgentApprovalRecord found = store.findByRecommendationId("adv-b").get();
        assertThat(found.getStatus()).isEqualTo(AgentApprovalStatus.REJECTED);
        assertThat(found.getActor()).isEqualTo("admin");
    }

    /** C. A description mentioning a browser action triggers no browser interaction. */
    @Test
    public void adversarialC_descriptionMentioningClickTriggersNothing() {
        AgentApprovalRecordStore store = new AgentApprovalRecordStore();
        SelfHealingRecommendation withClick = recommendationWith("adv-c", 0.5, EvidenceStatus.UNVERIFIED, "click the payment button");
        store.add(approvalService.pending(withClick));

        AgentApprovalRecord found = store.findByRecommendationId("adv-c").get();
        assertThat(found.getRecommendation().getDescription()).isEqualTo("click the payment button");
        assertThat(found.getStatus()).isEqualTo(AgentApprovalStatus.PENDING);
    }

    /** D. Maximum confidence has no effect on the stored/queried status. */
    @Test
    public void adversarialD_maximumConfidenceHasNoEffectOnStoredStatus() {
        AgentApprovalRecordStore store = new AgentApprovalRecordStore();
        SelfHealingRecommendation maxConfidence = recommendationWith("adv-d", 1.0, EvidenceStatus.UNVERIFIED, "x");
        store.add(approvalService.pending(maxConfidence));

        assertThat(store.findByRecommendationId("adv-d").get().getStatus()).isEqualTo(AgentApprovalStatus.PENDING);
    }

    /** E. VERIFIED evidence has no effect on the stored/queried status. */
    @Test
    public void adversarialE_verifiedEvidenceHasNoEffectOnStoredStatus() {
        AgentApprovalRecordStore store = new AgentApprovalRecordStore();
        SelfHealingRecommendation verified = recommendationWith("adv-e", 0.9, EvidenceStatus.VERIFIED, "x");
        store.add(approvalService.pending(verified));

        assertThat(store.findByRecommendationId("adv-e").get().getStatus()).isEqualTo(AgentApprovalStatus.PENDING);
    }

    /** F. An APPROVED record queried back is still exactly APPROVED — nothing more. */
    @Test
    public void adversarialF_approvedStatusRoundTripsExactly() {
        AgentApprovalRecordStore store = new AgentApprovalRecordStore();
        store.add(approvalService.approve(sampleRecommendation("adv-f"), "ok"));

        assertThat(store.findByStatus(AgentApprovalStatus.APPROVED)).hasSize(1);
        assertThat(store.findByRecommendationId("adv-f").get().getStatus()).isEqualTo(AgentApprovalStatus.APPROVED);
    }

    /** G. A REJECTED record queried back is still exactly REJECTED. */
    @Test
    public void adversarialG_rejectedStatusRoundTripsExactly() {
        AgentApprovalRecordStore store = new AgentApprovalRecordStore();
        store.add(approvalService.reject(sampleRecommendation("adv-g"), "no"));

        assertThat(store.findByStatus(AgentApprovalStatus.REJECTED)).hasSize(1);
        assertThat(store.findByRecommendationId("adv-g").get().getStatus()).isEqualTo(AgentApprovalStatus.REJECTED);
    }

    /** H. A PENDING record queried back is still exactly PENDING. */
    @Test
    public void adversarialH_pendingStatusRoundTripsExactly() {
        AgentApprovalRecordStore store = new AgentApprovalRecordStore();
        store.add(approvalService.pending(sampleRecommendation("adv-h")));

        assertThat(store.findByStatus(AgentApprovalStatus.PENDING)).hasSize(1);
        assertThat(store.findByRecommendationId("adv-h").get().getStatus()).isEqualTo(AgentApprovalStatus.PENDING);
    }
}
