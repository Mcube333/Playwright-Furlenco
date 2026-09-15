package com.framework.ai.diagnosis;

/**
 * Classifies what KIND of remediation a {@link SuggestedFix} represents.
 *
 * This is a pure classification value — it does not instruct anything to be automatically
 * performed. It is deliberately separate from {@link com.framework.ai.model.FailureCategory}
 * (Phase 2), which classifies WHY a test failed, not what kind of fix might address it.
 */
public enum FixType {
    LOCATOR,
    ASSERTION,
    WAIT,
    TEST_DATA,
    APPLICATION_BEHAVIOR,
    ANALYTICS,
    API,
    UNKNOWN;

    public static FixType fromString(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        try {
            return FixType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}
