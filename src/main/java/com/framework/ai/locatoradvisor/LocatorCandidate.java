package com.framework.ai.locatoradvisor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.framework.ai.codegeneration.EvidenceStatus;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A single candidate locator with its evidence classification, DOM validation
 * outcome, and a quality score (0-100).
 *
 * Instances are always produced by {@link LocatorAnalysisService} after deterministic
 * DOM validation has run — never by copying an AI claim verbatim. See
 * {@link LocatorDomMatcher}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class LocatorCandidate {

    private final String locator;
    private final LocatorStrategy strategy;
    private final EvidenceStatus evidenceStatus;
    private final ValidationType validationType;
    private final int matchCount;
    private final double confidence;
    private final int score;
    private final List<String> strengths;
    private final List<String> weaknesses;
    private final String recommendation;

    @JsonCreator
    public LocatorCandidate(
            @JsonProperty("locator") String locator,
            @JsonProperty("strategy") Object strategy,
            @JsonProperty("evidenceStatus") Object evidenceStatus,
            @JsonProperty("validationType") Object validationType,
            @JsonProperty("matchCount") Integer matchCount,
            @JsonProperty("confidence") Double confidence,
            @JsonProperty("score") Integer score,
            @JsonProperty("strengths") List<String> strengths,
            @JsonProperty("weaknesses") List<String> weaknesses,
            @JsonProperty("recommendation") String recommendation) {
        this.locator = locator != null ? locator : "";
        this.strategy = strategy instanceof LocatorStrategy
                ? (LocatorStrategy) strategy
                : LocatorStrategy.fromString(String.valueOf(strategy));
        this.evidenceStatus = evidenceStatus instanceof EvidenceStatus
                ? (EvidenceStatus) evidenceStatus
                : EvidenceStatus.fromString(String.valueOf(evidenceStatus));
        this.validationType = validationType instanceof ValidationType
                ? (ValidationType) validationType
                : parseValidationType(validationType);
        this.matchCount = matchCount != null ? matchCount : -1;
        this.confidence = confidence != null ? clamp01(confidence) : 0.0;
        this.score = score != null ? clampScore(score) : 0;
        this.strengths = strengths != null ? Collections.unmodifiableList(new ArrayList<>(strengths)) : Collections.emptyList();
        this.weaknesses = weaknesses != null ? Collections.unmodifiableList(new ArrayList<>(weaknesses)) : Collections.emptyList();
        this.recommendation = recommendation != null ? recommendation : "";
    }

    private static ValidationType parseValidationType(Object value) {
        if (value == null) {
            return ValidationType.NOT_VALIDATED;
        }
        try {
            return ValidationType.valueOf(String.valueOf(value).trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ValidationType.NOT_VALIDATED;
        }
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    private static int clampScore(int v) {
        return Math.max(0, Math.min(100, v));
    }

    public String getLocator() {
        return locator;
    }

    public LocatorStrategy getStrategy() {
        return strategy;
    }

    public EvidenceStatus getEvidenceStatus() {
        return evidenceStatus;
    }

    public ValidationType getValidationType() {
        return validationType;
    }

    public int getMatchCount() {
        return matchCount;
    }

    public double getConfidence() {
        return confidence;
    }

    public int getScore() {
        return score;
    }

    public List<String> getStrengths() {
        return strengths;
    }

    public List<String> getWeaknesses() {
        return weaknesses;
    }

    public String getRecommendation() {
        return recommendation;
    }

    /** Human-readable quality tier for the score, per the Phase 5 scoring rubric. */
    public String getScoreTier() {
        if (score >= 95) return "Excellent";
        if (score >= 80) return "Recommended";
        if (score >= 60) return "Acceptable with caution";
        if (score >= 40) return "Weak";
        return "Avoid";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String locator = "";
        private LocatorStrategy strategy = LocatorStrategy.UNKNOWN;
        private EvidenceStatus evidenceStatus = EvidenceStatus.UNVERIFIED;
        private ValidationType validationType = ValidationType.NOT_VALIDATED;
        private int matchCount = -1;
        private double confidence = 0.0;
        private int score = 0;
        private List<String> strengths = new ArrayList<>();
        private List<String> weaknesses = new ArrayList<>();
        private String recommendation = "";

        public Builder locator(String locator) {
            this.locator = locator;
            return this;
        }

        public Builder strategy(LocatorStrategy strategy) {
            this.strategy = strategy;
            return this;
        }

        public Builder evidenceStatus(EvidenceStatus evidenceStatus) {
            this.evidenceStatus = evidenceStatus;
            return this;
        }

        public Builder validationType(ValidationType validationType) {
            this.validationType = validationType;
            return this;
        }

        public Builder matchCount(int matchCount) {
            this.matchCount = matchCount;
            return this;
        }

        public Builder confidence(double confidence) {
            this.confidence = confidence;
            return this;
        }

        public Builder score(int score) {
            this.score = score;
            return this;
        }

        public Builder strengths(List<String> strengths) {
            if (strengths != null) this.strengths = new ArrayList<>(strengths);
            return this;
        }

        public Builder addStrength(String strength) {
            this.strengths.add(strength);
            return this;
        }

        public Builder weaknesses(List<String> weaknesses) {
            if (weaknesses != null) this.weaknesses = new ArrayList<>(weaknesses);
            return this;
        }

        public Builder addWeakness(String weakness) {
            this.weaknesses.add(weakness);
            return this;
        }

        public Builder recommendation(String recommendation) {
            this.recommendation = recommendation;
            return this;
        }

        public LocatorCandidate build() {
            return new LocatorCandidate(locator, strategy, evidenceStatus, validationType,
                    matchCount, confidence, score, strengths, weaknesses, recommendation);
        }
    }
}
