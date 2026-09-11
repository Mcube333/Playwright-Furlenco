package com.framework.ai.locatoradvisor;

/**
 * Distinguishes how a locator's match against the target element was established.
 *
 * This distinction exists specifically to prevent a critical class of hallucination:
 * claiming a locator was "executed" or "confirmed in the browser" when the advisor
 * only ever analyzed a static DOM snapshot string.
 */
public enum ValidationType {
    /** Not checked against any DOM evidence (no snapshot supplied, or strategy not verifiable offline). */
    NOT_VALIDATED,
    /** Matched against the supplied static DOM snapshot text via deterministic analysis. NOT a live browser check. */
    DOM_MATCHED,
    /** Reserved for a future phase: executed via Playwright locator.count()/isVisible() against a live page.
     *  This phase MUST NEVER produce this value. */
    RUNTIME_VALIDATED
}
