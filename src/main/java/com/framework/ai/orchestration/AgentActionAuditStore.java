package com.framework.ai.orchestration;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Phase 10 Step 2: a small, explicit, in-memory append-only audit trail of
 * {@link AgentActionAuditRecord}s.
 *
 * PURE DATA/QUERY STRUCTURE — this class stores and retrieves {@link AgentActionAuditRecord}
 * values only. It never creates one (that remains a caller's responsibility, exactly as
 * {@link AgentApprovalService} — not this store — is the only place an {@link AgentApprovalRecord}
 * is created), never inspects an {@link AgentActionResultStatus} to decide anything, and never
 * produces a result from an {@link AgentApprovalStatus}. There is no
 * {@code execute}/{@code apply}/{@code run}/{@code dispatch} method anywhere in this class.
 *
 * APPEND-ONLY, UNLIKE {@link AgentApprovalRecordStore}. {@link AgentApprovalRecordStore} keys by
 * recommendation ID and deliberately replaces an earlier record with a later one for the same ID,
 * because it represents *current decision state*. This store represents a *history* instead — an
 * audit trail must never silently lose an earlier entry just because a later one was recorded for
 * the same approval — so {@link #add(AgentActionAuditRecord)} always appends, and
 * {@link #findByApprovalId(String)} can legitimately return more than one entry for the same ID.
 *
 * IN-MEMORY ONLY. Backed by a single {@link CopyOnWriteArrayList}; nothing here reads or writes a
 * file, a database, or a network endpoint. The store's entire content disappears when this object
 * (or the JVM) is garbage collected.
 *
 * IDENTITY. "Approval ID" means {@link AgentApprovalRecord#getRecommendationId()} — the same
 * existing, Phase 8-assigned UUID {@link AgentApprovalRecordStore} already keys by. No second
 * identifier scheme is introduced.
 *
 * THREAD SAFETY. {@link CopyOnWriteArrayList} alone is sufficient here — every operation is a
 * single list read/write/snapshot, so no additional locking, executor, background worker, or
 * scheduler is introduced. This remains a data structure, not an execution engine.
 *
 * COLLECTION SAFETY. Every query method returns a new, unmodifiable snapshot list — never a live
 * view over the internal list — so a caller cannot mutate this store's state through a returned
 * collection.
 */
public class AgentActionAuditStore {

    private final List<AgentActionAuditRecord> records = new CopyOnWriteArrayList<>();

    /**
     * Appends {@code record} to the audit trail. Never replaces or removes an existing entry.
     *
     * @throws NullPointerException if {@code record} is {@code null} — matching
     *                               {@link AgentApprovalRecordStore#add(AgentApprovalRecord)}'s own
     *                               convention of failing loudly on a required value for a
     *                               deliberate, explicit API call.
     */
    public void add(AgentActionAuditRecord record) {
        Objects.requireNonNull(record, "record must not be null");
        records.add(record);
    }

    /**
     * Returns every audit entry recorded for the approval identified by {@code approvalId}
     * (matched against {@link AgentApprovalRecord#getRecommendationId()}), in the order they were
     * added. A {@code null} or unknown ID yields an empty list, never a fabricated entry.
     */
    public List<AgentActionAuditRecord> findByApprovalId(String approvalId) {
        if (approvalId == null) {
            return List.of();
        }
        return records.stream()
                .filter(r -> approvalId.equals(r.getApprovalRecord().getRecommendationId()))
                .collect(Collectors.toUnmodifiableList());
    }

    /** Every audit entry currently stored, in the order they were added. Never null; an unmodifiable snapshot. */
    public List<AgentActionAuditRecord> findAll() {
        return List.copyOf(records);
    }

    /** The number of audit entries currently stored. */
    public int size() {
        return records.size();
    }

    /** Removes every stored audit entry. The store itself remains usable afterward. */
    public void clear() {
        records.clear();
    }
}
