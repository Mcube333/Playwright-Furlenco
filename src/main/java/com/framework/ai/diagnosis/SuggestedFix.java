package com.framework.ai.diagnosis;

import com.framework.ai.codegeneration.EvidenceItem;
import com.framework.ai.locatoradvisor.LocatorCandidate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable representation of a single possible fix, together with the evidence that supports it.
 *
 * Deliberately carries NO independent evidence-status field. Evidence classification remains
 * owned entirely by the existing deterministic mechanisms — {@link EvidenceItem#getStatus()},
 * a {@link LocatorCandidate}'s own {@code EvidenceStatus}, or a
 * {@code RuntimeValidationResult}'s own {@code EvidenceStatus}. This class only aggregates
 * references to that evidence; it never computes, upgrades, or reinterprets it.
 *
 * Pure data model: builds nothing, calls nothing, executes nothing.
 */
public final class SuggestedFix {

    private final String description;
    private final FixType fixType;
    private final LocatorCandidate relatedLocatorCandidate;
    private final List<EvidenceItem> evidenceItems;
    private final double confidence;

    private SuggestedFix(Builder builder) {
        this.description = builder.description != null ? builder.description : "";
        this.fixType = builder.fixType != null ? builder.fixType : FixType.UNKNOWN;
        this.relatedLocatorCandidate = builder.relatedLocatorCandidate;
        this.evidenceItems = builder.evidenceItems != null
                ? Collections.unmodifiableList(new ArrayList<>(builder.evidenceItems))
                : Collections.emptyList();
        this.confidence = clamp(builder.confidence);
    }

    private static double clamp(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }

    public String getDescription() {
        return description;
    }

    public FixType getFixType() {
        return fixType;
    }

    /** Nullable — a fix need not relate to any locator (e.g. an assertion or test-data fix). */
    public LocatorCandidate getRelatedLocatorCandidate() {
        return relatedLocatorCandidate;
    }

    /** Never null; empty when no evidence was supplied. Unmodifiable. */
    public List<EvidenceItem> getEvidenceItems() {
        return evidenceItems;
    }

    /** Always in [0.0, 1.0]; NaN/infinite input is normalized to 0.0. */
    public double getConfidence() {
        return confidence;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String description;
        private FixType fixType;
        private LocatorCandidate relatedLocatorCandidate;
        private List<EvidenceItem> evidenceItems;
        private double confidence;

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder fixType(FixType fixType) {
            this.fixType = fixType;
            return this;
        }

        public Builder relatedLocatorCandidate(LocatorCandidate relatedLocatorCandidate) {
            this.relatedLocatorCandidate = relatedLocatorCandidate;
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

        public Builder confidence(double confidence) {
            this.confidence = confidence;
            return this;
        }

        public SuggestedFix build() {
            return new SuggestedFix(this);
        }
    }
}
