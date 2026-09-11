package com.framework.ai.codegeneration;

/**
 * Categorizes the level of evidence backing an AI-generated automation element
 * (locators, Page Object methods, analytics events, test data, assertions).
 */
public enum EvidenceStatus {
    /** Supported by actual framework classes, real DOM snapshot, or explicit requirement evidence */
    VERIFIED,
    /** Logically derived from requirement rules, but implementation details are not directly observed */
    INFERRED,
    /** AI-generated proposal without direct DOM or framework confirmation; requires QA validation */
    UNVERIFIED,
    /** Information required for implementation is missing from requirements or framework */
    MISSING;

    public static EvidenceStatus fromString(String value) {
        if (value == null || value.isBlank()) {
            return UNVERIFIED;
        }
        try {
            return EvidenceStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return UNVERIFIED;
        }
    }
}