package com.framework.ai.locatoradvisor;

/**
 * Locator strategies in preferred resilience order (1 = most preferred).
 * The advisor evaluates DOM evidence before recommending a strategy; this
 * ordering is a tiebreaker/preference, not a rule that overrides evidence.
 */
public enum LocatorStrategy {
    TEST_ID(1),
    ROLE(2),
    LABEL(3),
    PLACEHOLDER(4),
    TEXT(5),
    CSS_ATTRIBUTE(6),
    CSS_STABLE(7),
    XPATH(8),
    POSITIONAL(9),
    UNKNOWN(99);

    private final int priorityRank;

    LocatorStrategy(int priorityRank) {
        this.priorityRank = priorityRank;
    }

    /** Lower value = more preferred/resilient strategy. */
    public int getPriorityRank() {
        return priorityRank;
    }

    public boolean isFragile() {
        return this == XPATH || this == POSITIONAL;
    }

    public static LocatorStrategy fromString(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        try {
            return LocatorStrategy.valueOf(value.trim().toUpperCase().replace('-', '_').replace(' ', '_'));
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}
