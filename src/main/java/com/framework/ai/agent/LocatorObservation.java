package com.framework.ai.agent;

/**
 * Phase 8 Step 4: the observed, read-only facts about a single locator on the current page.
 *
 * PURE OBSERVATION — this is deliberately NOT {@code LocatorCandidate}, {@code EvidenceItem}, or
 * {@code RuntimeValidationResult}, and carries no {@code EvidenceStatus}/{@code ValidationType} at
 * all. "Observed" is not the same claim as "verified": a locator matching one visible element on
 * the page today says nothing about whether it is safe to act on, stable, or the correct element —
 * that judgment belongs entirely to Phase 5/6's existing, unmodified evidence mechanisms. This
 * class exists so {@link ReadOnlyBrowserTool} has somewhere honest to put what it actually saw,
 * without ever being tempted to also decide what that observation means.
 *
 * Never null; {@code count=-1} and {@code visible}/{@code enabled}=null signal "could not be
 * determined" rather than "false" — a genuine unknown is never silently coerced into a negative
 * result.
 */
public final class LocatorObservation {

    private final String selector;
    private final int count;
    private final Boolean visible;
    private final Boolean enabled;
    private final String error;

    private LocatorObservation(Builder builder) {
        this.selector = builder.selector != null ? builder.selector : "";
        this.count = builder.count;
        this.visible = builder.visible;
        this.enabled = builder.enabled;
        this.error = builder.error != null ? builder.error : "";
    }

    /** Never null; the selector string this observation is about (as supplied, unmodified). */
    public String getSelector() {
        return selector;
    }

    /** Number of matching elements at observation time, or {@code -1} if it could not be determined. */
    public int getCount() {
        return count;
    }

    /** Nullable — {@code null} means "not determined" (e.g. not attempted, or the check itself failed). */
    public Boolean getVisible() {
        return visible;
    }

    /** Nullable — {@code null} means "not determined". Advisory only, mirrors Phase 6's own treatment of enabled-state. */
    public Boolean getEnabled() {
        return enabled;
    }

    /** Never null; empty when observation succeeded without incident. */
    public String getError() {
        return error;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String selector;
        private int count = -1;
        private Boolean visible;
        private Boolean enabled;
        private String error;

        public Builder selector(String selector) {
            this.selector = selector;
            return this;
        }

        public Builder count(int count) {
            this.count = count;
            return this;
        }

        public Builder visible(Boolean visible) {
            this.visible = visible;
            return this;
        }

        public Builder enabled(Boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder error(String error) {
            this.error = error;
            return this;
        }

        public LocatorObservation build() {
            return new LocatorObservation(this);
        }
    }
}
