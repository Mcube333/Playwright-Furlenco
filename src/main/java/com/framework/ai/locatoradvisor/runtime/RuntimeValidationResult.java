package com.framework.ai.locatoradvisor.runtime;

import com.framework.ai.codegeneration.EvidenceStatus;
import com.framework.ai.locatoradvisor.ValidationType;
import java.time.Instant;

/**
 * Immutable result of a Phase 6 runtime locator check against a live Playwright {@code Page}.
 *
 * This is deliberately a SEPARATE object from {@link com.framework.ai.locatoradvisor.LocatorCandidate}
 * (Phase 5's offline DOM-string evidence) rather than a mutation of it — Phase 5 and Phase 6 evidence
 * must remain independently visible (e.g. a candidate can be Phase 5 {@code UNVERIFIED/DOM_MATCHED}
 * while its {@code RuntimeValidationResult} is {@code VERIFIED/RUNTIME_VALIDATED}, or vice versa).
 *
 * Reuses the existing {@link EvidenceStatus} and {@link ValidationType} enums as-is; no new evidence
 * enum is introduced.
 */
public final class RuntimeValidationResult {

    private final String locator;
    private final int matchCount;
    private final Boolean visible;
    private final Boolean enabled;
    private final String currentUrl;
    private final String environment;
    private final Instant timestamp;
    private final ValidationType validationType;
    private final EvidenceStatus evidenceStatus;
    private final String message;

    private RuntimeValidationResult(Builder builder) {
        this.locator = builder.locator != null ? builder.locator : "";
        this.matchCount = builder.matchCount;
        this.visible = builder.visible;
        this.enabled = builder.enabled;
        this.currentUrl = builder.currentUrl != null ? builder.currentUrl : "";
        this.environment = builder.environment != null ? builder.environment : "";
        this.timestamp = builder.timestamp != null ? builder.timestamp : Instant.now();
        this.validationType = builder.validationType != null ? builder.validationType : ValidationType.NOT_VALIDATED;
        this.evidenceStatus = builder.evidenceStatus != null ? builder.evidenceStatus : EvidenceStatus.UNVERIFIED;
        this.message = builder.message != null ? builder.message : "";
    }

    public String getLocator() {
        return locator;
    }

    /** -1 means "not evaluated" (e.g. blocked by the environment guard, invalid locator, or an exception) — never conflated with 0 ("evaluated, zero matches"). */
    public int getMatchCount() {
        return matchCount;
    }

    /** Null means "not evaluated" (e.g. matchCount != 1, or the visibility check itself failed). */
    public Boolean getVisible() {
        return visible;
    }

    /** Null means "not evaluated". Advisory only — never used to downgrade an otherwise-visible unique match. */
    public Boolean getEnabled() {
        return enabled;
    }

    public String getCurrentUrl() {
        return currentUrl;
    }

    public String getEnvironment() {
        return environment;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public ValidationType getValidationType() {
        return validationType;
    }

    public EvidenceStatus getEvidenceStatus() {
        return evidenceStatus;
    }

    public String getMessage() {
        return message;
    }

    /**
     * Safe fallback for any case where runtime validation could not proceed
     * (disabled, no Page, closed Page, guard denial, invalid locator, or an unexpected exception).
     * Always {@code NOT_VALIDATED}/{@code UNVERIFIED} — never claims runtime evidence that wasn't collected.
     */
    public static RuntimeValidationResult notValidated(String locator, String message) {
        return builder()
                .locator(locator)
                .matchCount(-1)
                .validationType(ValidationType.NOT_VALIDATED)
                .evidenceStatus(EvidenceStatus.UNVERIFIED)
                .message(message)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String locator = "";
        private int matchCount = -1;
        private Boolean visible;
        private Boolean enabled;
        private String currentUrl = "";
        private String environment = "";
        private Instant timestamp;
        private ValidationType validationType = ValidationType.NOT_VALIDATED;
        private EvidenceStatus evidenceStatus = EvidenceStatus.UNVERIFIED;
        private String message = "";

        public Builder locator(String locator) {
            this.locator = locator;
            return this;
        }

        public Builder matchCount(int matchCount) {
            this.matchCount = matchCount;
            return this;
        }

        public Builder visible(Boolean visible) {
            this.visible = visible;
            return this;
        }

        public Builder enabled(Boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder currentUrl(String currentUrl) {
            this.currentUrl = currentUrl;
            return this;
        }

        public Builder environment(String environment) {
            this.environment = environment;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder validationType(ValidationType validationType) {
            this.validationType = validationType;
            return this;
        }

        public Builder evidenceStatus(EvidenceStatus evidenceStatus) {
            this.evidenceStatus = evidenceStatus;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public RuntimeValidationResult build() {
            return new RuntimeValidationResult(this);
        }
    }
}
