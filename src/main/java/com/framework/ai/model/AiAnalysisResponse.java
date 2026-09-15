package com.framework.ai.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Structured, provider-independent model representing AI failure analysis output.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class AiAnalysisResponse {

    private final String summary;
    private final String rootCause;
    private final FailureCategory category;
    private final String suggestedFix;
    private final List<String> suggestedLocators;
    private final String jiraBugReport;
    private final double confidenceScore;
    private final Map<String, Object> metadata;

    @JsonCreator
    public AiAnalysisResponse(
            @JsonProperty("summary") String summary,
            @JsonProperty("rootCause") String rootCause,
            @JsonProperty("category") FailureCategory category,
            @JsonProperty("suggestedFix") String suggestedFix,
            @JsonProperty("suggestedLocators") List<String> suggestedLocators,
            @JsonProperty("jiraBugReport") String jiraBugReport,
            @JsonProperty("confidenceScore") Double confidenceScore,
            @JsonProperty("metadata") Map<String, Object> metadata) {
        this.summary = summary != null ? summary : "";
        this.rootCause = rootCause != null ? rootCause : "";
        this.category = category != null ? category : FailureCategory.UNKNOWN;
        this.suggestedFix = suggestedFix != null ? suggestedFix : "";
        this.suggestedLocators = suggestedLocators != null
                ? Collections.unmodifiableList(new ArrayList<>(suggestedLocators))
                : Collections.emptyList();
        this.jiraBugReport = jiraBugReport != null ? jiraBugReport : "";
        this.confidenceScore = confidenceScore != null ? confidenceScore : 0.0;
        this.metadata = metadata != null
                ? Collections.unmodifiableMap(new HashMap<>(metadata))
                : Collections.emptyMap();
    }

    public String getSummary() {
        return summary;
    }

    public String getRootCause() {
        return rootCause;
    }

    public FailureCategory getCategory() {
        return category;
    }

    public String getSuggestedFix() {
        return suggestedFix;
    }

    public List<String> getSuggestedLocators() {
        return suggestedLocators;
    }

    public String getJiraBugReport() {
        return jiraBugReport;
    }

    public double getConfidenceScore() {
        return confidenceScore;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String summary;
        private String rootCause;
        private FailureCategory category = FailureCategory.UNKNOWN;
        private String suggestedFix;
        private List<String> suggestedLocators = new ArrayList<>();
        private String jiraBugReport;
        private double confidenceScore = 0.0;
        private Map<String, Object> metadata = new HashMap<>();

        public Builder summary(String summary) {
            this.summary = summary;
            return this;
        }

        public Builder rootCause(String rootCause) {
            this.rootCause = rootCause;
            return this;
        }

        public Builder category(FailureCategory category) {
            this.category = category != null ? category : FailureCategory.UNKNOWN;
            return this;
        }

        public Builder suggestedFix(String suggestedFix) {
            this.suggestedFix = suggestedFix;
            return this;
        }

        public Builder suggestedLocators(List<String> suggestedLocators) {
            if (suggestedLocators != null) {
                this.suggestedLocators = new ArrayList<>(suggestedLocators);
            }
            return this;
        }

        public Builder addSuggestedLocator(String locator) {
            if (locator != null && !locator.isBlank()) {
                this.suggestedLocators.add(locator);
            }
            return this;
        }

        public Builder jiraBugReport(String jiraBugReport) {
            this.jiraBugReport = jiraBugReport;
            return this;
        }

        public Builder confidenceScore(double confidenceScore) {
            this.confidenceScore = confidenceScore;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            if (metadata != null) {
                this.metadata = new HashMap<>(metadata);
            }
            return this;
        }

        public Builder addMetadata(String key, Object value) {
            if (key != null && value != null) {
                this.metadata.put(key, value);
            }
            return this;
        }

        public AiAnalysisResponse build() {
            return new AiAnalysisResponse(
                    summary,
                    rootCause,
                    category,
                    suggestedFix,
                    suggestedLocators,
                    jiraBugReport,
                    confidenceScore,
                    metadata);
        }
    }

    @Override
    public String toString() {
        return "AiAnalysisResponse{" +
                "summary='" + summary + '\'' +
                ", category=" + category +
                ", confidenceScore=" + confidenceScore +
                ", suggestedLocatorsCount=" + suggestedLocators.size() +
                '}';
    }
}
