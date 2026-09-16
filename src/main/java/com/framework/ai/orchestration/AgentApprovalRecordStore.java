package com.framework.ai.orchestration;

import com.framework.ai.agent.SelfHealingRecommendation;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Phase 9 Step 7: a small, explicit, in-memory holder and query surface for already-created
 * {@link AgentApprovalRecord}s.
 *
 * PURE DATA/QUERY STRUCTURE — this class stores and retrieves {@link AgentApprovalRecord} values
 * only. It never creates one (that remains {@link AgentApprovalService}'s sole responsibility),
 * never inspects a record's {@link AgentApprovalStatus} to decide anything, and never reads
 * confidence, {@code EvidenceStatus}, {@code AgentDecision}, or {@code AgentAction} from the
 * underlying {@link SelfHealingRecommendation} — there is no method on this class capable of doing
 * so. There is no {@code execute}/{@code apply}/{@code run}/{@code dispatch} method anywhere in
 * this class.
 *
 * IN-MEMORY ONLY. Backed by a single {@link ConcurrentHashMap}; nothing here reads or writes a
 * file, a database, or a network endpoint. The store's entire content disappears when this object
 * (or the JVM) is garbage collected — there is no persistence of any kind.
 *
 * IDENTITY. Every record is keyed by {@link SelfHealingRecommendation#getRecommendationId()} (the
 * existing, Phase 8-assigned UUID reused via {@link AgentApprovalRecord#getRecommendationId()}) —
 * no second identifier scheme is introduced. {@link #add(AgentApprovalRecord)} for a recommendation
 * ID already present in the store deterministically **replaces** the previous record for that ID;
 * this is the only duplicate-handling behavior this class has, chosen because a later, explicit
 * human decision (e.g. changing a mind from {@code PENDING} to {@code APPROVED}) is expected to
 * supersede an earlier one for the same recommendation, and an approval store that silently kept
 * two different explicit records against one recommendation would be a worse, more ambiguous
 * default than a deterministic replace.
 *
 * THREAD SAFETY. {@link ConcurrentHashMap} alone is sufficient here — every operation is a single
 * map read/write/snapshot, so no additional locking, executor, background worker, or scheduler is
 * introduced. This remains a data structure, not an execution engine.
 *
 * COLLECTION SAFETY. {@link #findByStatus(AgentApprovalStatus)} returns a new, unmodifiable
 * snapshot list — never a live view over the internal map — so a caller cannot mutate this store's
 * state through the returned collection.
 */
public class AgentApprovalRecordStore {

    private final Map<String, AgentApprovalRecord> recordsByRecommendationId = new ConcurrentHashMap<>();

    /**
     * Stores {@code record}, keyed by its recommendation ID. If a record already exists for the
     * same recommendation ID, it is replaced by this one — see the class Javadoc for why that is
     * the chosen, deterministic duplicate behavior.
     *
     * @throws NullPointerException if {@code record} is {@code null} — matching
     *                               {@link AgentApprovalRecord.Builder#build()}'s own convention
     *                               of failing loudly on a required value for a deliberate,
     *                               explicit API call, rather than silently doing nothing
     */
    public void add(AgentApprovalRecord record) {
        Objects.requireNonNull(record, "record must not be null");
        recordsByRecommendationId.put(record.getRecommendationId(), record);
    }

    /**
     * Looks up the current record for {@code recommendationId}. Never throws: a {@code null} or
     * unknown ID simply yields an empty result, never a fabricated record.
     */
    public Optional<AgentApprovalRecord> findByRecommendationId(String recommendationId) {
        if (recommendationId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(recordsByRecommendationId.get(recommendationId));
    }

    /**
     * Convenience overload of {@link #findByRecommendationId(String)} that reads the ID from
     * {@code recommendation} itself. A {@code null} recommendation yields an empty result.
     */
    public Optional<AgentApprovalRecord> findByRecommendation(SelfHealingRecommendation recommendation) {
        if (recommendation == null) {
            return Optional.empty();
        }
        return findByRecommendationId(recommendation.getRecommendationId());
    }

    /**
     * Returns every currently stored record whose {@link AgentApprovalRecord#getStatus()} equals
     * {@code status} exactly — never reinterpreted, never upgraded, never inferred from anything
     * else. A {@code null} status yields an empty list. The returned list is an unmodifiable
     * snapshot; later changes to the store are not reflected in a previously returned list.
     */
    public List<AgentApprovalRecord> findByStatus(AgentApprovalStatus status) {
        if (status == null) {
            return List.of();
        }
        return recordsByRecommendationId.values().stream()
                .filter(record -> record.getStatus() == status)
                .collect(Collectors.toUnmodifiableList());
    }

    /** The number of distinct recommendation IDs currently stored. */
    public int size() {
        return recordsByRecommendationId.size();
    }

    /** Removes every stored record. The store itself remains usable afterward. */
    public void clear() {
        recordsByRecommendationId.clear();
    }
}
