package com.framework.ai.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Diagnostic context captured at test failure time for AI-assisted root-cause analysis.
 * Provider-agnostic. Contains only diagnostic information required for analysis.
 * Designed so that sensitive credentials or PII can be easily stripped/excluded before analysis.
 */
public final class FailureContext {

    private final String testName;
    private final String testClass;
    private final String errorMessage;
    private final String stackTrace;
    private final String currentUrl;
    private final String pageTitle;
    private final String domSnippet;
    private final long executionDurationMs;
    private final String environment;
    private final Map<String, String> attributes;

    private FailureContext(Builder builder) {
        this.testName = builder.testName != null ? builder.testName : "";
        this.testClass = builder.testClass != null ? builder.testClass : "";
        this.errorMessage = builder.errorMessage != null ? builder.errorMessage : "";
        this.stackTrace = builder.stackTrace != null ? builder.stackTrace : "";
        this.currentUrl = builder.currentUrl != null ? builder.currentUrl : "";
        this.pageTitle = builder.pageTitle != null ? builder.pageTitle : "";
        this.domSnippet = builder.domSnippet != null ? builder.domSnippet : "";
        this.executionDurationMs = builder.executionDurationMs;
        this.environment = builder.environment != null ? builder.environment : "";
        this.attributes = Collections.unmodifiableMap(new HashMap<>(builder.attributes));
    }

    public String getTestName() {
        return testName;
    }

    public String getTestClass() {
        return testClass;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getStackTrace() {
        return stackTrace;
    }

    public String getCurrentUrl() {
        return currentUrl;
    }

    public String getPageTitle() {
        return pageTitle;
    }

    public String getDomSnippet() {
        return domSnippet;
    }

    public long getExecutionDurationMs() {
        return executionDurationMs;
    }

    public String getEnvironment() {
        return environment;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String testName;
        private String testClass;
        private String errorMessage;
        private String stackTrace;
        private String currentUrl;
        private String pageTitle;
        private String domSnippet;
        private long executionDurationMs;
        private String environment;
        private final Map<String, String> attributes = new HashMap<>();

        public Builder testName(String testName) {
            this.testName = testName;
            return this;
        }

        public Builder testClass(String testClass) {
            this.testClass = testClass;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder stackTrace(String stackTrace) {
            this.stackTrace = stackTrace;
            return this;
        }

        public Builder currentUrl(String currentUrl) {
            this.currentUrl = currentUrl;
            return this;
        }

        public Builder pageTitle(String pageTitle) {
            this.pageTitle = pageTitle;
            return this;
        }

        public Builder domSnippet(String domSnippet) {
            this.domSnippet = domSnippet;
            return this;
        }

        public Builder executionDurationMs(long executionDurationMs) {
            this.executionDurationMs = executionDurationMs;
            return this;
        }

        public Builder environment(String environment) {
            this.environment = environment;
            return this;
        }

        public Builder attribute(String key, String value) {
            if (key != null && value != null) {
                this.attributes.put(key, value);
            }
            return this;
        }

        public Builder attributes(Map<String, String> attributes) {
            if (attributes != null) {
                this.attributes.putAll(attributes);
            }
            return this;
        }

        public FailureContext build() {
            return new FailureContext(this);
        }
    }

    @Override
    public String toString() {
        return "FailureContext{" +
                "testClass='" + testClass + '\'' +
                ", testName='" + testName + '\'' +
                ", errorMessage='" + errorMessage + '\'' +
                ", currentUrl='" + currentUrl + '\'' +
                ", executionDurationMs=" + executionDurationMs +
                ", environment='" + environment + '\'' +
                '}';
    }
}
