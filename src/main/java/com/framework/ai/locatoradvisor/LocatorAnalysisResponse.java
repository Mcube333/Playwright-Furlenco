package com.framework.ai.locatoradvisor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Aggregate output of the Locator Advisor: ranked candidates, accessibility
 * findings, Page Object reuse suggestions, and an explicit list of what
 * evidence was missing. Advisory only — nothing in this response is ever
 * applied to source automatically.
 */
public final class LocatorAnalysisResponse {

    private final boolean success;
    private final String errorMessage;
    private final String targetElement;
    private final List<LocatorCandidate> candidates;
    private final LocatorCandidate recommendedLocator;
    private final List<AccessibilityFinding> accessibilityFindings;
    private final List<PageObjectMatch> existingPageObjectMatches;
    private final List<String> assumptions;
    private final List<String> missingEvidence;
    private final double overallConfidence;
    private final boolean humanReviewRequired;
    private final boolean domTruncated;

    private LocatorAnalysisResponse(Builder builder) {
        this.success = builder.success;
        this.errorMessage = builder.errorMessage != null ? builder.errorMessage : "";
        this.targetElement = builder.targetElement != null ? builder.targetElement : "";
        this.candidates = Collections.unmodifiableList(new ArrayList<>(builder.candidates));
        this.recommendedLocator = builder.recommendedLocator;
        this.accessibilityFindings = Collections.unmodifiableList(new ArrayList<>(builder.accessibilityFindings));
        this.existingPageObjectMatches = Collections.unmodifiableList(new ArrayList<>(builder.existingPageObjectMatches));
        this.assumptions = Collections.unmodifiableList(new ArrayList<>(builder.assumptions));
        this.missingEvidence = Collections.unmodifiableList(new ArrayList<>(builder.missingEvidence));
        this.overallConfidence = builder.overallConfidence;
        this.humanReviewRequired = builder.humanReviewRequired;
        this.domTruncated = builder.domTruncated;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getTargetElement() {
        return targetElement;
    }

    public List<LocatorCandidate> getCandidates() {
        return candidates;
    }

    public LocatorCandidate getRecommendedLocator() {
        return recommendedLocator;
    }

    public List<AccessibilityFinding> getAccessibilityFindings() {
        return accessibilityFindings;
    }

    public List<PageObjectMatch> getExistingPageObjectMatches() {
        return existingPageObjectMatches;
    }

    public List<String> getAssumptions() {
        return assumptions;
    }

    public List<String> getMissingEvidence() {
        return missingEvidence;
    }

    public double getOverallConfidence() {
        return overallConfidence;
    }

    public boolean isHumanReviewRequired() {
        return humanReviewRequired;
    }

    public boolean isDomTruncated() {
        return domTruncated;
    }

    public static LocatorAnalysisResponse failure(String errorMessage) {
        return builder().success(false).errorMessage(errorMessage).humanReviewRequired(true).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private boolean success = true;
        private String errorMessage = "";
        private String targetElement = "";
        private List<LocatorCandidate> candidates = new ArrayList<>();
        private LocatorCandidate recommendedLocator;
        private List<AccessibilityFinding> accessibilityFindings = new ArrayList<>();
        private List<PageObjectMatch> existingPageObjectMatches = new ArrayList<>();
        private List<String> assumptions = new ArrayList<>();
        private List<String> missingEvidence = new ArrayList<>();
        private double overallConfidence = 0.0;
        private boolean humanReviewRequired = true;
        private boolean domTruncated = false;

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder targetElement(String targetElement) {
            this.targetElement = targetElement;
            return this;
        }

        public Builder candidates(List<LocatorCandidate> candidates) {
            if (candidates != null) this.candidates = new ArrayList<>(candidates);
            return this;
        }

        public Builder addCandidate(LocatorCandidate candidate) {
            this.candidates.add(candidate);
            return this;
        }

        public Builder recommendedLocator(LocatorCandidate recommendedLocator) {
            this.recommendedLocator = recommendedLocator;
            return this;
        }

        public Builder accessibilityFindings(List<AccessibilityFinding> findings) {
            if (findings != null) this.accessibilityFindings = new ArrayList<>(findings);
            return this;
        }

        public Builder addAccessibilityFinding(AccessibilityFinding finding) {
            this.accessibilityFindings.add(finding);
            return this;
        }

        public Builder existingPageObjectMatches(List<PageObjectMatch> matches) {
            if (matches != null) this.existingPageObjectMatches = new ArrayList<>(matches);
            return this;
        }

        public Builder addExistingPageObjectMatch(PageObjectMatch match) {
            this.existingPageObjectMatches.add(match);
            return this;
        }

        public Builder assumptions(List<String> assumptions) {
            if (assumptions != null) this.assumptions = new ArrayList<>(assumptions);
            return this;
        }

        public Builder addAssumption(String assumption) {
            this.assumptions.add(assumption);
            return this;
        }

        public Builder missingEvidence(List<String> missingEvidence) {
            if (missingEvidence != null) this.missingEvidence = new ArrayList<>(missingEvidence);
            return this;
        }

        public Builder addMissingEvidence(String missing) {
            this.missingEvidence.add(missing);
            return this;
        }

        public Builder overallConfidence(double overallConfidence) {
            this.overallConfidence = overallConfidence;
            return this;
        }

        public Builder humanReviewRequired(boolean humanReviewRequired) {
            this.humanReviewRequired = humanReviewRequired;
            return this;
        }

        public Builder domTruncated(boolean domTruncated) {
            this.domTruncated = domTruncated;
            return this;
        }

        public LocatorAnalysisResponse build() {
            return new LocatorAnalysisResponse(this);
        }
    }
}
