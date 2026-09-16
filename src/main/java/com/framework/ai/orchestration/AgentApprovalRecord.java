package com.framework.ai.orchestration;

import com.framework.ai.agent.SelfHealingRecommendation;
import com.framework.ai.sanitizer.SensitiveDataSanitizer;
import java.time.Instant;
import java.util.Objects;

/**
 * Phase 9 Step 6: an immutable record of a human's decision about one already-existing
 * {@link SelfHealingRecommendation}.
 *
 * PURE DATA CONTRACT — this class carries no method capable of executing, applying, approving, or
 * rejecting anything itself; it only stores a decision that was made elsewhere, by a human, before
 * this object was constructed. There is no {@code execute}/{@code apply}/{@code run} method
 * anywhere in this class.
 *
 * APPROVED DOES NOT MEAN EXECUTABLE. A {@link AgentApprovalStatus#APPROVED} record is not
 * executable, not guard-approved, not production-safe, and not automatically actionable — it is
 * exclusively a record of what a human decided. Any future, separately-reviewed execution
 * capability would still have to independently pass
 * {@link com.framework.ai.agent.AgentExecutionGuard}; nothing in this class changes, bypasses, or
 * is read by that guard.
 *
 * RECOMMENDATION IDENTITY IS PRESERVED, NEVER REGENERATED. The {@link SelfHealingRecommendation}
 * held here is the exact, unmodified object a human reviewed — this class stores a direct
 * reference to it (Phase 8's existing immutable value type), never a copy, a rewrite, or a
 * re-derived recommendation. {@link SelfHealingRecommendation#getRecommendationId()} (already a
 * stable, per-instance UUID assigned by Phase 8, reused here rather than duplicated) is exposed via
 * {@link #getRecommendationId()} purely as a convenience so a caller can correlate an approval
 * record back to "the recommendation with this ID" without needing to unwrap
 * {@link #getRecommendation()} first.
 *
 * NO IDENTITY/AUTHENTICATION SYSTEM. {@link #getActor()} is optional, caller-supplied, free-text
 * metadata only — exactly like {@link #getReason()} — and is never validated, never checked against
 * any allowlist, and never used to grant permission. A value like {@code "admin"} carries no special
 * meaning here; the only thing that means anything is {@link #getStatus()}, and even that never
 * means "safe to execute" (see above).
 */
public final class AgentApprovalRecord {

    private final SelfHealingRecommendation recommendation;
    private final AgentApprovalStatus status;
    private final String reason;
    private final String actor;
    private final Instant timestamp;

    private AgentApprovalRecord(Builder builder) {
        this.recommendation = Objects.requireNonNull(builder.recommendation, "recommendation must not be null");
        this.status = Objects.requireNonNull(builder.status, "status must not be null");
        this.reason = builder.reason != null ? builder.reason : "";
        this.actor = builder.actor != null ? builder.actor : "";
        this.timestamp = builder.timestamp != null ? builder.timestamp : Instant.now();
    }

    /** Never null; the exact, unmodified recommendation this record is about. */
    public SelfHealingRecommendation getRecommendation() {
        return recommendation;
    }

    /** Convenience accessor for {@code getRecommendation().getRecommendationId()}. Never null. */
    public String getRecommendationId() {
        return recommendation.getRecommendationId();
    }

    /** Never null. The human decision. See the class Javadoc: {@code APPROVED} never means executable. */
    public AgentApprovalStatus getStatus() {
        return status;
    }

    /** Never null; empty if not supplied. Free-text human commentary — advisory only, never a permission signal. */
    public String getReason() {
        return reason;
    }

    /** Never null; empty if not supplied. Free-text, caller-provided metadata only — never authorization. */
    public String getActor() {
        return actor;
    }

    /** Never null. When this record was created — not when the underlying recommendation was produced. */
    public Instant getTimestamp() {
        return timestamp;
    }

    /**
     * Safe, sanitized summary for logs/diagnostics. Re-sanitizes {@link #getReason()}/{@link #getActor()}
     * at render time via the existing, unmodified {@link SensitiveDataSanitizer} — the same
     * "sanitize at the point of display" convention {@code FailureDiagnosisReporter}/
     * {@code SelfHealingRecommendationReporter} already use — even though a reason/actor is
     * ordinary human commentary and not expected to carry secrets.
     */
    @Override
    public String toString() {
        return "AgentApprovalRecord{recommendationId=" + recommendation.getRecommendationId()
                + ", status=" + status
                + ", reason=" + SensitiveDataSanitizer.sanitize(reason)
                + ", actor=" + SensitiveDataSanitizer.sanitize(actor)
                + ", timestamp=" + timestamp
                + "}";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private SelfHealingRecommendation recommendation;
        private AgentApprovalStatus status;
        private String reason;
        private String actor;
        private Instant timestamp;

        public Builder recommendation(SelfHealingRecommendation recommendation) {
            this.recommendation = recommendation;
            return this;
        }

        public Builder status(AgentApprovalStatus status) {
            this.status = status;
            return this;
        }

        public Builder reason(String reason) {
            this.reason = reason;
            return this;
        }

        public Builder actor(String actor) {
            this.actor = actor;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        /**
         * @throws NullPointerException if {@code recommendation} or {@code status} was never set —
         *                               every {@link AgentApprovalStatus} value, including
         *                               {@code PENDING}, requires a recommendation; there is no
         *                               such thing as an approval record about nothing.
         */
        public AgentApprovalRecord build() {
            return new AgentApprovalRecord(this);
        }
    }
}
