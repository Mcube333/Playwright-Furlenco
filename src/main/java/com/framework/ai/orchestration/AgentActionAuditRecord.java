package com.framework.ai.orchestration;

import java.time.Instant;
import java.util.Objects;

/**
 * Phase 10 Step 2: an immutable audit record connecting one {@link AgentApprovalRecord} (Phase 9's
 * human decision) to one {@link AgentActionResult} (what happened, or explicitly did not, to the
 * approved action).
 *
 * PURE HISTORICAL METADATA — this class does not mutate {@link #getApprovalRecord()}, does not
 * change {@link AgentApprovalStatus}, does not add a new status value, and does not itself decide
 * or infer what {@link #getActionResult()} should be. It only pairs two already-produced,
 * already-immutable objects with a recording timestamp and an optional actor, exactly as
 * {@link AgentApprovalRecord} itself pairs a {@link com.framework.ai.agent.SelfHealingRecommendation}
 * with a human decision.
 *
 * NO AUTOMATIC TRANSITION. Constructing this record never changes
 * {@link AgentApprovalRecord#getStatus()}, and an {@link AgentApprovalStatus#APPROVED} approval
 * paired here with an {@link AgentActionResultStatus#NOT_EXECUTED} result is the expected,
 * normal shape — this class draws no connection implying that an {@code APPROVED} approval should,
 * would, or must eventually produce an {@link AgentActionResultStatus#EXECUTED} result. Any pairing
 * of statuses supplied by a caller is accepted and recorded as-is.
 */
public final class AgentActionAuditRecord {

    private final AgentApprovalRecord approvalRecord;
    private final AgentActionResult actionResult;
    private final Instant recordedAt;
    private final String actor;

    private AgentActionAuditRecord(Builder builder) {
        this.approvalRecord = Objects.requireNonNull(builder.approvalRecord, "approvalRecord must not be null");
        this.actionResult = Objects.requireNonNull(builder.actionResult, "actionResult must not be null");
        this.recordedAt = builder.recordedAt != null ? builder.recordedAt : Instant.now();
        this.actor = builder.actor != null ? builder.actor : "";
    }

    /** Never null; the exact, unmodified Phase 9 approval decision this audit entry is about. */
    public AgentApprovalRecord getApprovalRecord() {
        return approvalRecord;
    }

    /** Never null; the exact, unmodified result being recorded — never recomputed or reinterpreted here. */
    public AgentActionResult getActionResult() {
        return actionResult;
    }

    /** Never null. When this audit entry was created — not when the approval or the result themselves occurred. */
    public Instant getRecordedAt() {
        return recordedAt;
    }

    /** Never null; empty if not supplied. Free-text, caller-provided metadata only — never authorization. */
    public String getActor() {
        return actor;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private AgentApprovalRecord approvalRecord;
        private AgentActionResult actionResult;
        private Instant recordedAt;
        private String actor;

        public Builder approvalRecord(AgentApprovalRecord approvalRecord) {
            this.approvalRecord = approvalRecord;
            return this;
        }

        public Builder actionResult(AgentActionResult actionResult) {
            this.actionResult = actionResult;
            return this;
        }

        public Builder recordedAt(Instant recordedAt) {
            this.recordedAt = recordedAt;
            return this;
        }

        public Builder actor(String actor) {
            this.actor = actor;
            return this;
        }

        /** @throws NullPointerException if {@code approvalRecord} or {@code actionResult} was never set. */
        public AgentActionAuditRecord build() {
            return new AgentActionAuditRecord(this);
        }
    }
}
