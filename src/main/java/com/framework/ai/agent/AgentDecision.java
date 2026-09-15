package com.framework.ai.agent;

import com.framework.ai.codegeneration.EvidenceItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Phase 8 Step 2: an immutable, typed PROPOSAL produced by a future agent reasoning step.
 *
 * PURE DATA CONTRACT. An {@code AgentDecision} never executes {@link #getAction()}, never calls an
 * AI provider, never touches Playwright, and never modifies a test, a file, or Git — it is a value
 * object a future orchestrator (not implemented in this step) would produce, and a future
 * execution guard (also not implemented in this step) would evaluate before anything could ever
 * happen as a result of it. It cannot approve itself: {@link #isRequiresApproval()} defaults to
 * {@code true} and nothing in this class ever flips it based on its own contents.
 *
 * EVIDENCE INTEGRITY (hard requirement carried over from Phase 4.1/5/7): {@link #getConfidence()}
 * is the agent's own reasoning confidence and is completely independent from the
 * {@code EvidenceStatus} carried by each {@link #getEvidenceItems()} entry. A high confidence value
 * never upgrades, implies, or substitutes for verified evidence — exactly as an AI's
 * {@code confidenceScore} never upgrades a {@code LocatorCandidate}'s {@code EvidenceStatus}
 * elsewhere in this codebase. This class contains no method that could perform such a conversion.
 *
 * BLOCKED SEMANTICS: a blocked decision remains visible rather than being silently discarded (per
 * the Phase 8 Step 1 architecture). To keep that invariant deterministic and enforced at the single
 * point objects of this type are created, {@link AgentState#BLOCKED} may only be paired with
 * {@link AgentAction#NONE} — {@link Builder#build()} throws {@link IllegalStateException} for any
 * other combination, rather than silently correcting it.
 */
public final class AgentDecision {

    private final AgentState state;
    private final AgentAction action;
    private final String reason;
    private final String rationale;
    private final double confidence;
    private final List<EvidenceItem> evidenceItems;
    private final boolean requiresApproval;

    private AgentDecision(Builder builder) {
        this.state = Objects.requireNonNull(builder.state, "state must not be null");
        this.action = Objects.requireNonNull(builder.action, "action must not be null");
        if (this.state == AgentState.BLOCKED && this.action != AgentAction.NONE) {
            throw new IllegalStateException(
                    "A BLOCKED AgentDecision must carry action=NONE; a blocked decision is never also a "
                            + "specific proposed action. Got action=" + this.action);
        }
        this.reason = builder.reason != null ? builder.reason : "";
        this.rationale = builder.rationale != null ? builder.rationale : "";
        this.confidence = clamp(builder.confidence);
        this.evidenceItems = builder.evidenceItems != null
                ? Collections.unmodifiableList(
                        builder.evidenceItems.stream().filter(Objects::nonNull).collect(Collectors.toList()))
                : Collections.emptyList();
        this.requiresApproval = builder.requiresApproval;
    }

    private static double clamp(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }

    /** Never null. */
    public AgentState getState() {
        return state;
    }

    /** Never null. Always {@link AgentAction#NONE} when {@link #getState()} is {@link AgentState#BLOCKED}. */
    public AgentAction getAction() {
        return action;
    }

    /** Never null; empty if not supplied. Human-readable explanation, e.g. why a decision was blocked. */
    public String getReason() {
        return reason;
    }

    /** Never null; empty if not supplied. The agent's own free-text reasoning — advisory only, never evidence. */
    public String getRationale() {
        return rationale;
    }

    /** Always in [0.0, 1.0]; NaN/infinite input is normalized to 0.0. Reasoning confidence only — never evidence status. */
    public double getConfidence() {
        return confidence;
    }

    /** Never null; empty when no evidence was supplied. Unmodifiable. Every status here was determined elsewhere. */
    public List<EvidenceItem> getEvidenceItems() {
        return evidenceItems;
    }

    /** Defaults to {@code true}. This decision can never set this to {@code false} on its own initiative. */
    public boolean isRequiresApproval() {
        return requiresApproval;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private AgentState state;
        private AgentAction action;
        private String reason;
        private String rationale;
        private double confidence;
        private List<EvidenceItem> evidenceItems;
        private boolean requiresApproval = true;

        public Builder state(AgentState state) {
            this.state = state;
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

        public Builder rationale(String rationale) {
            this.rationale = rationale;
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

        public AgentDecision build() {
            return new AgentDecision(this);
        }
    }
}
