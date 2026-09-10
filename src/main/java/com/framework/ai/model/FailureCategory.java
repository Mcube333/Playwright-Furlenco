package com.framework.ai.model;

/**
 * Standardized failure classification categories for AI-assisted root-cause analysis.
 * Provider-agnostic.
 */
public enum FailureCategory {
    APPLICATION_BUG,
    LOCATOR_CHANGED,
    TIMEOUT,
    NETWORK_FAILURE,
    API_FAILURE,
    DATA_ISSUE,
    AUTHENTICATION_FAILURE,
    ENVIRONMENT_FAILURE,
    TEST_FAILURE,
    UNCERTAIN,
    UNKNOWN;

    /**
     * Safely parses a string into a FailureCategory, defaulting to UNKNOWN.
     *
     * @param value raw string value
     * @return matching FailureCategory or UNKNOWN
     */
    public static FailureCategory fromString(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        try {
            return FailureCategory.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}
