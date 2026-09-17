package com.tests.ai.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.framework.ai.agent.AgentAction;
import com.framework.ai.agent.SelfHealingRecommendation;
import com.framework.ai.diagnosis.FixType;
import com.framework.ai.orchestration.AgentActionAuditRecord;
import com.framework.ai.orchestration.AgentActionAuditStore;
import com.framework.ai.orchestration.AgentActionResult;
import com.framework.ai.orchestration.AgentActionResultStatus;
import com.framework.ai.orchestration.AgentApprovalRecord;
import com.framework.ai.orchestration.AgentApprovalService;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.testng.annotations.Test;

/**
 * Phase 10 Step 2: focused tests for {@link AgentActionAuditStore}. Fully hermetic — no AI, no
 * Playwright, no network, no persistence.
 */
public class AgentActionAuditStoreTest {

    private final AgentApprovalService approvalService = new AgentApprovalService();

    private SelfHealingRecommendation recommendation(String id) {
        return SelfHealingRecommendation.builder()
                .recommendationId(id).fixType(FixType.LOCATOR)
                .description("x").proposedLocator("#new").confidence(0.5).approvalRequired(true).build();
    }

    private AgentActionAuditRecord auditFor(String recommendationId) {
        AgentApprovalRecord approval = approvalService.approve(recommendation(recommendationId), "ok");
        AgentActionResult result = AgentActionResult.builder()
                .action(AgentAction.LOCATOR_RECOMMENDATION).status(AgentActionResultStatus.NOT_EXECUTED).build();
        return AgentActionAuditRecord.builder().approvalRecord(approval).actionResult(result).build();
    }

    // 1. add record
    @Test
    public void testAddRecord() {
        AgentActionAuditStore store = new AgentActionAuditStore();
        store.add(auditFor("rec-1"));
        assertThat(store.size()).isEqualTo(1);
    }

    // 2. find by approval ID
    @Test
    public void testFindByApprovalId() {
        AgentActionAuditStore store = new AgentActionAuditStore();
        AgentActionAuditRecord record = auditFor("rec-2");
        store.add(record);

        assertThat(store.findByApprovalId("rec-2")).containsExactly(record);
    }

    @Test
    public void testFindByUnknownApprovalIdReturnsEmpty() {
        AgentActionAuditStore store = new AgentActionAuditStore();
        assertThat(store.findByApprovalId("does-not-exist")).isEmpty();
    }

    @Test
    public void testFindByNullApprovalIdReturnsEmptyWithoutThrowing() {
        AgentActionAuditStore store = new AgentActionAuditStore();
        store.add(auditFor("rec-3"));
        assertThat(store.findByApprovalId(null)).isEmpty();
    }

    // 3. find all
    @Test
    public void testFindAllReturnsEveryRecordInInsertionOrder() {
        AgentActionAuditStore store = new AgentActionAuditStore();
        AgentActionAuditRecord first = auditFor("rec-4");
        AgentActionAuditRecord second = auditFor("rec-5");
        store.add(first);
        store.add(second);

        assertThat(store.findAll()).containsExactly(first, second);
    }

    // 4. empty store
    @Test
    public void testEmptyStoreHasZeroSizeAndNoRecords() {
        AgentActionAuditStore store = new AgentActionAuditStore();
        assertThat(store.size()).isZero();
        assertThat(store.findAll()).isEmpty();
    }

    // 5. null record
    @Test
    public void testAddNullRecordThrowsNullPointerException() {
        AgentActionAuditStore store = new AgentActionAuditStore();
        assertThatThrownBy(() -> store.add(null)).isInstanceOf(NullPointerException.class);
    }

    // 6. defensive returned collection
    @Test
    public void testFindAllResultIsUnmodifiable() {
        AgentActionAuditStore store = new AgentActionAuditStore();
        store.add(auditFor("rec-6"));
        List<AgentActionAuditRecord> all = store.findAll();

        assertThatThrownBy(() -> all.add(auditFor("rec-7"))).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void testFindByApprovalIdResultIsUnmodifiable() {
        AgentActionAuditStore store = new AgentActionAuditStore();
        store.add(auditFor("rec-8"));
        List<AgentActionAuditRecord> byId = store.findByApprovalId("rec-8");

        assertThatThrownBy(() -> byId.add(auditFor("rec-9"))).isInstanceOf(UnsupportedOperationException.class);
    }

    // 7. clear
    @Test
    public void testClearRemovesAllRecordsAndStoreRemainsUsable() {
        AgentActionAuditStore store = new AgentActionAuditStore();
        store.add(auditFor("rec-10"));

        store.clear();

        assertThat(store.size()).isZero();
        store.add(auditFor("rec-11"));
        assertThat(store.size()).isEqualTo(1);
    }

    // 8. multiple records — including more than one entry for the same approval ID, which must
    // NOT be collapsed/replaced (append-only, unlike AgentApprovalRecordStore).
    @Test
    public void testMultipleRecordsForTheSameApprovalIdAreAllRetained() {
        AgentActionAuditStore store = new AgentActionAuditStore();
        AgentActionAuditRecord attempt1 = auditFor("rec-12");
        AgentActionAuditRecord attempt2 = auditFor("rec-12");
        store.add(attempt1);
        store.add(attempt2);

        assertThat(store.findByApprovalId("rec-12")).hasSize(2).containsExactly(attempt1, attempt2);
        assertThat(store.size()).isEqualTo(2);
    }

    // 9. deterministic retrieval
    @Test
    public void testRepeatedFindAllCallsReturnConsistentResults() {
        AgentActionAuditStore store = new AgentActionAuditStore();
        store.add(auditFor("rec-13"));

        assertThat(store.findAll()).isEqualTo(store.findAll());
    }

    // 10. thread-safe basic access
    @Test
    public void testConcurrentAddsFromMultipleThreadsAreAllRetained() throws InterruptedException {
        AgentActionAuditStore store = new AgentActionAuditStore();
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            int index = i;
            executor.submit(() -> {
                try {
                    store.add(auditFor("rec-concurrent-" + index));
                } finally {
                    latch.countDown();
                }
            });
        }

        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        assertThat(store.size()).isEqualTo(threadCount);
    }
}
