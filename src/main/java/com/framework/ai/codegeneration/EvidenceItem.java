package com.framework.ai.codegeneration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * Tracks an individual automation element along with its evidence classification,
 * source, and confidence score to prevent AI hallucinations from masquerading as verified facts.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class EvidenceItem {

    private final String item;
    private final String value;
    private final EvidenceStatus status;
    private final String source;
    private final double confidence;

    @JsonCreator
    public EvidenceItem(
            @JsonProperty("item") String item,
            @JsonProperty("value") String value,
            @JsonProperty("status") Object status,
            @JsonProperty("source") String source,
            @JsonProperty("confidence") Double confidence) {
        this.item = item != null ? item : "";
        this.value = value != null ? value : "";
        this.status = status instanceof EvidenceStatus
                ? (EvidenceStatus) status
                : EvidenceStatus.fromString(String.valueOf(status));
        this.source = source != null ? source : "AI inference";
        this.confidence = confidence != null ? confidence : 0.5;
    }

    public String getItem() {
        return item;
    }

    public String getValue() {
        return value;
    }

    public EvidenceStatus getStatus() {
        return status;
    }

    public String getSource() {
        return source;
    }

    public double getConfidence() {
        return confidence;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String item;
        private String value;
        private EvidenceStatus status = EvidenceStatus.UNVERIFIED;
        private String source = "AI inference";
        private double confidence = 0.5;

        public Builder item(String item) {
            this.item = item;
            return this;
        }

        public Builder value(String value) {
            this.value = value;
            return this;
        }

        public Builder status(EvidenceStatus status) {
            this.status = status;
            return this;
        }

        public Builder source(String source) {
            this.source = source;
            return this;
        }

        public Builder confidence(double confidence) {
            this.confidence = confidence;
            return this;
        }

        public EvidenceItem build() {
            return new EvidenceItem(item, value, status, source, confidence);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EvidenceItem that = (EvidenceItem) o;
        return Double.compare(that.confidence, confidence) == 0 &&
                Objects.equals(item, that.item) &&
                Objects.equals(value, that.value) &&
                status == that.status &&
                Objects.equals(source, that.source);
    }

    @Override
    public int hashCode() {
        return Objects.hash(item, value, status, source, confidence);
    }
}