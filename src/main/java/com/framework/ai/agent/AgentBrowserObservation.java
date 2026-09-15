package com.framework.ai.agent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Phase 8 Step 4: an immutable snapshot of what {@link ReadOnlyBrowserTool} actually observed on
 * an existing Playwright {@code Page} — nothing more.
 *
 * PURE OBSERVATION MODEL. This class computes nothing, verifies nothing, and never claims more
 * than what was directly read. In particular it never carries an {@code EvidenceStatus} — a
 * successful browser query is not, by itself, "evidence" in the Phase 5/6/7 sense; it is raw input
 * that a future step may choose to turn into evidence through the existing, unmodified mechanisms.
 * No field here is fabricated: when a piece of information could not be observed (page
 * unavailable, an individual read threw), the corresponding field is left unset/{@code null}
 * rather than defaulted to something that looks like a real observation.
 */
public final class AgentBrowserObservation {

    private final boolean pageAvailable;
    private final String url;
    private final String title;
    private final String domSnapshot;
    private final List<LocatorObservation> observedLocatorStates;
    private final String error;

    private AgentBrowserObservation(Builder builder) {
        this.pageAvailable = builder.pageAvailable;
        this.url = builder.url;
        this.title = builder.title;
        this.domSnapshot = builder.domSnapshot;
        this.observedLocatorStates = builder.observedLocatorStates != null
                ? Collections.unmodifiableList(
                        builder.observedLocatorStates.stream().filter(Objects::nonNull).collect(Collectors.toList()))
                : Collections.emptyList();
        this.error = builder.error != null ? builder.error : "";
    }

    /** {@code true} only when a usable (non-null, non-closed) Page was available to observe. */
    public boolean isPageAvailable() {
        return pageAvailable;
    }

    /** Nullable — {@code null} means the URL was not observed (page unavailable, or the read itself failed). */
    public String getUrl() {
        return url;
    }

    /** Nullable — {@code null} means the title was not observed. */
    public String getTitle() {
        return title;
    }

    /** Nullable — {@code null} means the DOM was not observed. Bounded by the existing DOM extraction limit when present. */
    public String getDomSnapshot() {
        return domSnapshot;
    }

    /** Never null; empty when no locator was observed. Unmodifiable, independent of the source list passed in. */
    public List<LocatorObservation> getObservedLocatorStates() {
        return observedLocatorStates;
    }

    /** Never null; empty when observation succeeded without incident. May describe a partial failure. */
    public String getError() {
        return error;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private boolean pageAvailable;
        private String url;
        private String title;
        private String domSnapshot;
        private List<LocatorObservation> observedLocatorStates;
        private String error;

        public Builder pageAvailable(boolean pageAvailable) {
            this.pageAvailable = pageAvailable;
            return this;
        }

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder domSnapshot(String domSnapshot) {
            this.domSnapshot = domSnapshot;
            return this;
        }

        public Builder observedLocatorStates(List<LocatorObservation> observedLocatorStates) {
            this.observedLocatorStates = observedLocatorStates;
            return this;
        }

        public Builder addObservedLocatorState(LocatorObservation locatorObservation) {
            if (locatorObservation != null) {
                if (this.observedLocatorStates == null) {
                    this.observedLocatorStates = new ArrayList<>();
                } else if (!(this.observedLocatorStates instanceof ArrayList)) {
                    this.observedLocatorStates = new ArrayList<>(this.observedLocatorStates);
                }
                this.observedLocatorStates.add(locatorObservation);
            }
            return this;
        }

        public Builder error(String error) {
            this.error = error;
            return this;
        }

        public AgentBrowserObservation build() {
            return new AgentBrowserObservation(this);
        }
    }
}
