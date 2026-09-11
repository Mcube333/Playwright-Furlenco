package com.framework.ai.locatoradvisor;

import com.framework.ai.codegeneration.EvidenceStatus;

/**
 * Result of comparing the target element against an existing Page Object's
 * supplied source/context, per the "prefer reuse over new locators" rule.
 *
 * {@code matched=true} only when the method name was found verbatim in the
 * supplied {@code existingPageObjectContext} string (see {@link PageObjectContextMatcher}).
 * An AI-claimed method name that cannot be found in that context is never
 * surfaced as a match — it is discarded as a hallucination risk.
 */
public final class PageObjectMatch {

    private final boolean matched;
    private final String existingMethod;
    private final String pageObjectClass;
    private final String recommendation;
    private final EvidenceStatus evidenceStatus;

    private PageObjectMatch(boolean matched, String existingMethod, String pageObjectClass,
                             String recommendation, EvidenceStatus evidenceStatus) {
        this.matched = matched;
        this.existingMethod = existingMethod != null ? existingMethod : "";
        this.pageObjectClass = pageObjectClass != null ? pageObjectClass : "";
        this.recommendation = recommendation != null ? recommendation : "";
        this.evidenceStatus = evidenceStatus;
    }

    public boolean isMatched() {
        return matched;
    }

    public String getExistingMethod() {
        return existingMethod;
    }

    public String getPageObjectClass() {
        return pageObjectClass;
    }

    public String getRecommendation() {
        return recommendation;
    }

    public EvidenceStatus getEvidenceStatus() {
        return evidenceStatus;
    }

    /** An existing method was found verbatim in the supplied Page Object context. VERIFIED — it is a textual fact. */
    public static PageObjectMatch reuse(String existingMethod, String pageObjectClass) {
        return new PageObjectMatch(true, existingMethod, pageObjectClass,
                "Reuse " + (pageObjectClass.isBlank() ? "" : pageObjectClass + ".") + existingMethod + "() instead of a new direct locator.",
                EvidenceStatus.VERIFIED);
    }

    /** No matching existing method found (or an AI-claimed method could not be verified in the supplied context). INFERRED — a suggestion, not a fact. */
    public static PageObjectMatch recommendNew(String suggestedMethodName) {
        return new PageObjectMatch(false, "", "",
                "No matching existing Page Object method found. Consider adding a new method, e.g. " + suggestedMethodName + "().",
                EvidenceStatus.INFERRED);
    }
}
