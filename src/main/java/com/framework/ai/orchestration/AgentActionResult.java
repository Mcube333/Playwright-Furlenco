package com.framework.ai.orchestration;

import com.framework.ai.agent.AgentAction;
import com.framework.ai.codegeneration.EvidenceItem;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Phase 10 Step 2: an immutable data representation of what happened — or explicitly did not
 * happen — to one {@link AgentAction}.
 *
 * PURE DATA CONTRACT — this class carries no method capable of executing {@link #getAction()},
 * and holds no reference to a {@code Page}/{@code Locator}/{@code Browser}/{@code WebDriver} or
 * any other execution-capable type. There is no {@code execute}/{@code apply}/{@code run} method
 * anywhere in this class. Nothing in this codebase constructs one of these with
 * {@link AgentActionResultStatus#EXECUTED} or {@link AgentActionResultStatus#FAILED} as the
 * consequence of an actual browser action — those two values exist only so this model can
 * represent a future executor's report; no such executor exists yet.
 *
 * NO INVENTED METADATA. {@link #getStartedAt()}/{@link #getCompletedAt()} default to {@code null},
 * never to {@link Instant#now()} — unlike {@link AgentApprovalRecord}'s creation timestamp (which
 * genuinely represents "when this record was created"), a result's start/completion time is not
 * something this class can honestly default, since defaulting it would fabricate evidence that
 * something started or completed when nothing here ever runs anything. A caller reporting
 * {@link AgentActionResultStatus#NOT_EXECUTED} is expected to simply leave both unset.
 *
 * NO CONFIDENCE FIELD. Deliberately excluded per this step's own instruction — confidence is an
 * {@link com.framework.ai.agent.AgentDecision}/{@link com.framework.ai.agent.SelfHealingRecommendation}
 * concept already, and adding a second one here would invite exactly the "confidence implies
 * execution" conflation this step exists to prevent.
 *
 * EVIDENCE IS REUSED, NEVER RECOMPUTED. {@link #getEvidenceItems()} carries the existing
 * {@link EvidenceItem}/{@link com.framework.ai.codegeneration.EvidenceStatus} model verbatim — no
 * second evidence vocabulary is introduced, and this class computes no evidence of its own.
 */
public final class AgentActionResult {

    private final AgentAction action;
    private final AgentActionResultStatus status;
    private final String message;
    private final String errorType;
    private final Instant startedAt;
    private final Instant completedAt;
    private final List<EvidenceItem> evidenceItems;

    private AgentActionResult(Builder builder) {
        this.action = Objects.requireNonNull(builder.action, "action must not be null");
        this.status = Objects.requireNonNull(builder.status, "status must not be null");
        this.message = builder.message != null ? builder.message : "";
        this.errorType = builder.errorType != null ? builder.errorType : "";
        this.startedAt = builder.startedAt;
        this.completedAt = builder.completedAt;
        this.evidenceItems = builder.evidenceItems != null
                ? Collections.unmodifiableList(
                        builder.evidenceItems.stream().filter(Objects::nonNull).collect(Collectors.toList()))
                : Collections.emptyList();
    }

    /** Never null; the action this result is about. */
    public AgentAction getAction() {
        return action;
    }

    /** Never null. See the class Javadoc: only {@link #getAction()}'s caller-supplied value — never inferred. */
    public AgentActionResultStatus getStatus() {
        return status;
    }

    /** Never null; empty if not supplied. Human-readable context only. */
    public String getMessage() {
        return message;
    }

    /** Never null; empty if not supplied. A caller-supplied error classification, not a stack trace. */
    public String getErrorType() {
        return errorType;
    }

    /** Nullable — {@code null} when the action was never started (e.g. {@code NOT_EXECUTED}/{@code BLOCKED}). */
    public Instant getStartedAt() {
        return startedAt;
    }

    /** Nullable — {@code null} when the action never completed. */
    public Instant getCompletedAt() {
        return completedAt;
    }

    /** Never null; empty when no evidence was supplied. Unmodifiable. Every status here was determined elsewhere. */
    public List<EvidenceItem> getEvidenceItems() {
        return evidenceItems;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private AgentAction action;
        private AgentActionResultStatus status;
        private String message;
        private String errorType;
        private Instant startedAt;
        private Instant completedAt;
        private List<EvidenceItem> evidenceItems;

        public Builder action(AgentAction action) {
            this.action = action;
            return this;
        }

        public Builder status(AgentActionResultStatus status) {
            this.status = status;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder errorType(String errorType) {
            this.errorType = errorType;
            return this;
        }

        public Builder startedAt(Instant startedAt) {
            this.startedAt = startedAt;
            return this;
        }

        public Builder completedAt(Instant completedAt) {
            this.completedAt = completedAt;
            return this;
        }

        public Builder evidenceItems(List<EvidenceItem> evidenceItems) {
            this.evidenceItems = evidenceItems;
            return this;
        }

        public Builder addEvidenceItem(EvidenceItem evidenceItem) {
            if (evidenceItem != null) {
                if (this.evidenceItems == null) {
                    this.evidenceItems = new ArrayList<>();
                } else if (!(this.evidenceItems instanceof ArrayList)) {
                    this.evidenceItems = new ArrayList<>(this.evidenceItems);
                }
                this.evidenceItems.add(evidenceItem);
            }
            return this;
        }

        /** @throws NullPointerException if {@code action} or {@code status} was never set. */
        public AgentActionResult build() {
            return new AgentActionResult(this);
        }
    }
}
