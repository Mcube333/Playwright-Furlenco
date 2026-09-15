package com.framework.ai.agent;

import com.framework.ai.codegeneration.EvidenceItem;
import com.framework.ai.diagnosis.FixType;
import com.framework.ai.locatoradvisor.ValidationType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Phase 8 Step 6: an immutable, non-executable proposal for a human to review and, if they agree,
 * apply themselves.
 *
 * PURE DATA CONTRACT — this class carries no method capable of applying itself, and deliberately
 * exposes no "executable" flag at all (a recommendation is inherently non-executable in Step 6;
 * a boolean that could only ever be {@code false} would invite exactly the misreading this class
 * exists to avoid). {@link #getConfidence()} is the agent's own reasoning confidence;
 * {@link #isApprovalRequired()} always reflects that a human must review this before anything
 * happens. Neither field, nor a {@link #getValidationType()} of {@code RUNTIME_VALIDATED}, nor an
 * {@link EvidenceItem} with status {@code VERIFIED}, means this recommendation has been applied,
 * approved, or is safe to execute — none of those concepts exist on this class at all.
 *
 * Reuses {@link FixType} (Phase 7), {@link EvidenceItem} (Phase 4.1), and {@link ValidationType}
 * (Phase 5) exactly as-is — no competing evidence/fix-type/validation vocabulary is introduced.
 */
public final class SelfHealingRecommendation {

    private final String recommendationId;
    private final FixType fixType;
    private final String description;
    private final String currentLocator;
    private final String proposedLocator;
    private final String currentAction;
    private final String proposedAction;
    private final String rationale;
    private final double confidence;
    private final List<EvidenceItem> evidenceItems;
    private final ValidationType validationType;
    private final boolean approvalRequired;

    private SelfHealingRecommendation(Builder builder) {
        this.recommendationId = builder.recommendationId != null ? builder.recommendationId : UUID.randomUUID().toString();
        this.fixType = builder.fixType != null ? builder.fixType : FixType.UNKNOWN;
        this.description = builder.description != null ? builder.description : "";
        this.currentLocator = builder.currentLocator != null ? builder.currentLocator : "";
        this.proposedLocator = builder.proposedLocator != null ? builder.proposedLocator : "";
        this.currentAction = builder.currentAction != null ? builder.currentAction : "";
        this.proposedAction = builder.proposedAction != null ? builder.proposedAction : "";
        this.rationale = builder.rationale != null ? builder.rationale : "";
        this.confidence = clamp(builder.confidence);
        this.evidenceItems = builder.evidenceItems != null
                ? Collections.unmodifiableList(
                        builder.evidenceItems.stream().filter(Objects::nonNull).collect(Collectors.toList()))
                : Collections.emptyList();
        this.validationType = builder.validationType != null ? builder.validationType : ValidationType.NOT_VALIDATED;
        this.approvalRequired = builder.approvalRequired;
    }

    private static double clamp(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }

    /** Never null; a stable, unique identifier for this recommendation instance (not derived from its content). */
    public String getRecommendationId() {
        return recommendationId;
    }

    public FixType getFixType() {
        return fixType;
    }

    /** Never null; a fixed, safe summary of what is being proposed — never AI free text verbatim. */
    public String getDescription() {
        return description;
    }

    /** Never null; empty when not applicable to this recommendation's {@link #getFixType()}. */
    public String getCurrentLocator() {
        return currentLocator;
    }

    /** Never null; empty when no replacement locator is being proposed. */
    public String getProposedLocator() {
        return proposedLocator;
    }

    /** Never null; empty when not applicable (e.g. locator recommendations). */
    public String getCurrentAction() {
        return currentAction;
    }

    /** Never null; empty when not applicable. */
    public String getProposedAction() {
        return proposedAction;
    }

    /** Never null; explanatory reasoning only — advisory, never evidence. */
    public String getRationale() {
        return rationale;
    }

    /** Always in [0.0, 1.0]; NaN/infinite input is normalized to 0.0. Advisory only — never approval, never permission. */
    public double getConfidence() {
        return confidence;
    }

    /** Never null; empty when no evidence was supplied. Unmodifiable. Every status here was determined elsewhere. */
    public List<EvidenceItem> getEvidenceItems() {
        return evidenceItems;
    }

    /** Never null; defaults to {@link ValidationType#NOT_VALIDATED} when no runtime/DOM validation applies. */
    public ValidationType getValidationType() {
        return validationType;
    }

    /** Always {@code true} in Step 6 — no approval-granting mechanism exists; a human must review this. */
    public boolean isApprovalRequired() {
        return approvalRequired;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String recommendationId;
        private FixType fixType;
        private String description;
        private String currentLocator;
        private String proposedLocator;
        private String currentAction;
        private String proposedAction;
        private String rationale;
        private double confidence;
        private List<EvidenceItem> evidenceItems;
        private ValidationType validationType;
        private boolean approvalRequired = true;

        public Builder recommendationId(String recommendationId) {
            this.recommendationId = recommendationId;
            return this;
        }

        public Builder fixType(FixType fixType) {
            this.fixType = fixType;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder currentLocator(String currentLocator) {
            this.currentLocator = currentLocator;
            return this;
        }

        public Builder proposedLocator(String proposedLocator) {
            this.proposedLocator = proposedLocator;
            return this;
        }

        public Builder currentAction(String currentAction) {
            this.currentAction = currentAction;
            return this;
        }

        public Builder proposedAction(String proposedAction) {
            this.proposedAction = proposedAction;
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

        public Builder validationType(ValidationType validationType) {
            this.validationType = validationType;
            return this;
        }

        public Builder approvalRequired(boolean approvalRequired) {
            this.approvalRequired = approvalRequired;
            return this;
        }

        public SelfHealingRecommendation build() {
            return new SelfHealingRecommendation(this);
        }
    }
}
