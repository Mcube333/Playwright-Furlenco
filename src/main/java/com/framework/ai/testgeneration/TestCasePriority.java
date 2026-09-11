package com.framework.ai.testgeneration;

/**
 * Priority levels for AI-generated test cases.
 */
public enum TestCasePriority {
    P0,
    P1,
    P2,
    P3,
    UNKNOWN;

    public static TestCasePriority fromString(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        try {
            return TestCasePriority.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}