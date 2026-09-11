package com.framework.ai.locatoradvisor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.framework.ai.codegeneration.EvidenceStatus;

/**
 * An accessibility gap observed (or inferred) in the supplied DOM evidence,
 * e.g. a button with no accessible name.
 *
 * Per Phase 5 rules, a remediation recommendation is always classified as
 * {@link EvidenceStatus#INFERRED} — it is advice, not an observed DOM fact.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class AccessibilityFinding {

    private final String element;
    private final String issue;
    private final String recommendation;
    private final EvidenceStatus evidenceStatus;

    @JsonCreator
    public AccessibilityFinding(
            @JsonProperty("element") String element,
            @JsonProperty("issue") String issue,
            @JsonProperty("recommendation") String recommendation,
            @JsonProperty("evidenceStatus") Object evidenceStatus) {
        this.element = element != null ? element : "";
        this.issue = issue != null ? issue : "";
        this.recommendation = recommendation != null ? recommendation : "";
        this.evidenceStatus = evidenceStatus instanceof EvidenceStatus
                ? (EvidenceStatus) evidenceStatus
                : EvidenceStatus.INFERRED;
    }

    public String getElement() {
        return element;
    }

    public String getIssue() {
        return issue;
    }

    public String getRecommendation() {
        return recommendation;
    }

    public EvidenceStatus getEvidenceStatus() {
        return evidenceStatus;
    }

    public static AccessibilityFinding of(String element, String issue, String recommendation) {
        return new AccessibilityFinding(element, issue, recommendation, EvidenceStatus.INFERRED);
    }
}
