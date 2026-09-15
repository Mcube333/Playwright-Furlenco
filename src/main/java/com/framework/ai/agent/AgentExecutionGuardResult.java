package com.framework.ai.agent;

import com.framework.ai.codegeneration.EvidenceItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Phase 8 Step 5: the immutable outcome of {@link AgentExecutionGuard#evaluate(AgentContext, AgentDecision)}.
 *
 * PURE DATA CONTRACT — this class represents a permission decision only; it never executes
 * anything and carries no method capable of doing so. {@link #isAllowed()} is the ONLY field that
 * ever means "permitted to proceed" — {@link #getConfidence()} and {@link #getEvidenceItems()} are
 * carried through purely for transparency/traceability (so a caller/future executor can see what
 * the original {@link AgentDecision} looked like) and never influence, and are never influenced
 * by, {@link #isAllowed()}. In particular: a high {@link #getConfidence()} value, or an
 * {@code EvidenceItem} with status {@code VERIFIED}, appearing alongside {@code allowed=false} is
 * the expected, correct shape — this class draws no connection between the two at all.
 *
 * {@link #isRequiresApproval()} reflects what the originating {@link AgentDecision} asked for; it
 * is NOT evidence that approval was granted, and this class has no field or method representing an
 * approval grant — Step 5 introduces no approval-granting mechanism.
 */
public final class AgentExecutionGuardResult {

    private final boolean allowed;
    private final AgentAction action;
    private final String reason;
    private final double confidence;
    private final List<EvidenceItem> evidenceItems;
    private final boolean requiresApproval;

    private AgentExecutionGuardResult(Builder builder) {
        this.allowed = builder.allowed;
        this.action = builder.action != null ? builder.action : AgentAction.NONE;
        this.reason = builder.reason != null ? builder.reason : "";
        this.confidence = builder.confidence;
        this.evidenceItems = builder.evidenceItems != null
                ? Collections.unmodifiableList(
                        builder.evidenceItems.stream().filter(Objects::nonNull).collect(Collectors.toList()))
                : Collections.emptyList();
        this.requiresApproval = builder.requiresApproval;
    }

    /** The one and only permission signal. {@code false} unless every policy check passed. */
    public boolean isAllowed() {
        return allowed;
    }

    /** Never null; the action this result is about. {@link AgentAction#NONE} when no specific action applies. */
    public AgentAction getAction() {
        return action;
    }

    /** Never null; a deterministic, human-readable explanation — always populated when {@code allowed=false}. */
    public String getReason() {
        return reason;
    }

    /** The originating decision's reasoning confidence, carried through for transparency only — never a permission signal. */
    public double getConfidence() {
        return confidence;
    }

    /** Never null; empty if none was supplied. Unmodifiable. Carried through for transparency only — never a permission signal. */
    public List<EvidenceItem> getEvidenceItems() {
        return evidenceItems;
    }

    /** Whether the originating decision asked for approval. Does NOT mean approval was granted. */
    public boolean isRequiresApproval() {
        return requiresApproval;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private boolean allowed;
        private AgentAction action;
        private String reason;
        private double confidence;
        private List<EvidenceItem> evidenceItems;
        private boolean requiresApproval;

        public Builder allowed(boolean allowed) {
            this.allowed = allowed;
            return this;
        }

        public Builder action(AgentAction action) {
            this.action = action;
            return this;
        }

        public Builder reason(String reason) {
            this.reason = reason;
            return this;
        }

        public Builder confidence(double confidence) {
            this.confidence = confidence;
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

        public Builder requiresApproval(boolean requiresApproval) {
            this.requiresApproval = requiresApproval;
            return this;
        }

        public AgentExecutionGuardResult build() {
            return new AgentExecutionGuardResult(this);
        }
    }
}
