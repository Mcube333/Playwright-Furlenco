package com.framework.ai.testgeneration;

/**
 * Categorization of QA test types for generated test scenarios.
 */
public enum TestCaseType {
    FUNCTIONAL,
    NEGATIVE,
    BOUNDARY,
    VALIDATION,
    ERROR_HANDLING,
    PERMISSION,
    AUTHENTICATION,
    DATA_INTEGRITY,
    UI,
    NAVIGATION,
    RESPONSIVE,
    API,
    REGRESSION,
    CROSS_BROWSER,
    UNKNOWN;

    public static TestCaseType fromString(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        String normalized = value.trim().toUpperCase().replace("-", "_").replace(" ", "_");
        try {
            return TestCaseType.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}