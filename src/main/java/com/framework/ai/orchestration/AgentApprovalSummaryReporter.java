package com.framework.ai.orchestration;

import java.util.Objects;

/**
 * Phase 9 Step 8: a pure, read-only, in-memory human summary of the current state of an
 * {@link AgentApprovalRecordStore}.
 *
 * PURE PRESENTATION — this class computes nothing beyond simple counting and holds no state of its
 * own beyond the injected {@link AgentApprovalRecordStore} reference. Every number in
 * {@link #summarize()} is derived, on every call, from {@link AgentApprovalRecordStore#size()} and
 * {@link AgentApprovalRecordStore#findByStatus(AgentApprovalStatus)} — the store remains the single
 * source of truth, and this class never caches a count, never keeps its own collection of records,
 * and never re-derives a status from anything on the underlying recommendation (confidence,
 * {@code EvidenceStatus}, reason, actor, or description all play no part in what this class does).
 *
 * NO CACHING. There is no field here other than the store reference itself — no counter, no map, no
 * background thread, no scheduler. Calling {@link #summarize()} twice in a row with the store
 * unchanged in between queries the store twice and necessarily produces the same result; if the
 * store's content changes between two calls, the next {@link #summarize()} reflects that change
 * immediately, because there was never a cached value to go stale in the first place.
 *
 * NO OUTPUT CHANNEL. {@link #summarize()} returns an in-memory {@link String} only — there is no
 * file write, no network call, no database call, no Allure attachment, and no console print
 * anywhere in this class. A caller decides what to do with the returned string.
 */
public class AgentApprovalSummaryReporter {

    private final AgentApprovalRecordStore store;

    /**
     * @throws NullPointerException if {@code store} is {@code null} — matching the same
     *                               fail-fast-on-a-required-dependency convention already used by
     *                               {@link AgentRecommendationConsumer}'s own constructor, rather
     *                               than inventing a new null-handling policy for this class.
     */
    public AgentApprovalSummaryReporter(AgentApprovalRecordStore store) {
        this.store = Objects.requireNonNull(store, "AgentApprovalRecordStore must not be null");
    }

    /**
     * Builds a fresh, human-readable summary of the store's current approval counts. Never throws,
     * never executes anything, and never changes any stored {@link AgentApprovalStatus}.
     */
    public String summarize() {
        int total = store.size();
        int pending = store.findByStatus(AgentApprovalStatus.PENDING).size();
        int approved = store.findByStatus(AgentApprovalStatus.APPROVED).size();
        int rejected = store.findByStatus(AgentApprovalStatus.REJECTED).size();

        return "Agent Approval Summary\n"
                + "----------------------\n"
                + "Total: " + total + "\n"
                + "Pending: " + pending + "\n"
                + "Approved: " + approved + "\n"
                + "Rejected: " + rejected + "\n";
    }
}
