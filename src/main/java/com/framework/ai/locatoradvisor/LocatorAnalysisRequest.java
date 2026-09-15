package com.framework.ai.locatoradvisor;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Input to the Locator Advisor. Every field is optional — the analyzer is
 * required to work with partial evidence (e.g. only a failing locator and
 * a failure message, with no DOM at all).
 */
public final class LocatorAnalysisRequest {

    private final String domSnapshot;
    private final String targetDescription;
    private final String targetText;
    private final String targetRole;
    private final String currentUrl;
    private final String pageName;
    private final String existingPageObjectContext;
    private final String failingLocator;
    private final String failureMessage;
    private final Map<String, String> metadata;

    private LocatorAnalysisRequest(Builder builder) {
        this.domSnapshot = nullToEmpty(builder.domSnapshot);
        this.targetDescription = nullToEmpty(builder.targetDescription);
        this.targetText = nullToEmpty(builder.targetText);
        this.targetRole = nullToEmpty(builder.targetRole);
        this.currentUrl = nullToEmpty(builder.currentUrl);
        this.pageName = nullToEmpty(builder.pageName);
        this.existingPageObjectContext = nullToEmpty(builder.existingPageObjectContext);
        this.failingLocator = nullToEmpty(builder.failingLocator);
        this.failureMessage = nullToEmpty(builder.failureMessage);
        this.metadata = Collections.unmodifiableMap(new HashMap<>(builder.metadata));
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }

    public String getDomSnapshot() {
        return domSnapshot;
    }

    public String getTargetDescription() {
        return targetDescription;
    }

    public String getTargetText() {
        return targetText;
    }

    public String getTargetRole() {
        return targetRole;
    }

    public String getCurrentUrl() {
        return currentUrl;
    }

    public String getPageName() {
        return pageName;
    }

    public String getExistingPageObjectContext() {
        return existingPageObjectContext;
    }

    public String getFailingLocator() {
        return failingLocator;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public boolean hasDomEvidence() {
        return !domSnapshot.isBlank() && !domSnapshot.equals("[DOM context unavailable]");
    }

    public boolean hasExistingPageObjectContext() {
        return !existingPageObjectContext.isBlank();
    }

    public boolean hasFailingLocator() {
        return !failingLocator.isBlank();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String domSnapshot;
        private String targetDescription;
        private String targetText;
        private String targetRole;
        private String currentUrl;
        private String pageName;
        private String existingPageObjectContext;
        private String failingLocator;
        private String failureMessage;
        private final Map<String, String> metadata = new HashMap<>();

        public Builder domSnapshot(String domSnapshot) {
            this.domSnapshot = domSnapshot;
            return this;
        }

        public Builder targetDescription(String targetDescription) {
            this.targetDescription = targetDescription;
            return this;
        }

        public Builder targetText(String targetText) {
            this.targetText = targetText;
            return this;
        }

        public Builder targetRole(String targetRole) {
            this.targetRole = targetRole;
            return this;
        }

        public Builder currentUrl(String currentUrl) {
            this.currentUrl = currentUrl;
            return this;
        }

        public Builder pageName(String pageName) {
            this.pageName = pageName;
            return this;
        }

        public Builder existingPageObjectContext(String existingPageObjectContext) {
            this.existingPageObjectContext = existingPageObjectContext;
            return this;
        }

        public Builder failingLocator(String failingLocator) {
            this.failingLocator = failingLocator;
            return this;
        }

        public Builder failureMessage(String failureMessage) {
            this.failureMessage = failureMessage;
            return this;
        }

        public Builder addMetadata(String key, String value) {
            if (key != null && value != null) {
                this.metadata.put(key, value);
            }
            return this;
        }

        public LocatorAnalysisRequest build() {
            return new LocatorAnalysisRequest(this);
        }
    }
}
