package com.framework.ai.agent;

import com.framework.ai.codegeneration.EvidenceItem;
import com.framework.ai.diagnosis.FailureDiagnosis;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Phase 8 Step 2: an observation snapshot a future agent's reasoning step would consume.
 *
 * PURE DATA CONTRACT — this class does not read the browser, does not call {@code AiClient}, and
 * does not generate or upgrade evidence. It only carries an existing {@link FailureDiagnosis} plus
 * a list of {@link EvidenceItem} that were already established by deterministic/existing framework
 * mechanisms (e.g. the {@code LocatorCandidate}/{@code RuntimeValidationResult} evidence Phase 5/6
 * already produced). No {@code Page}/{@code Locator}/{@code PlaywrightManager} reference exists
 * here — live, read-only browser observation is explicitly deferred to a later step.
 *
 * {@code failureDiagnosis} is required and fails fast on construction, mirroring
 * {@link AgentContext}. {@code evidenceItems} follows this codebase's existing collection
 * convention (see {@link FailureDiagnosis#getSuggestedFixes()}): never null, defensively copied,
 * and a null list is treated as "none supplied" rather than an error; a {@code null} entry within
 * a supplied list is silently skipped rather than added, matching the existing
 * {@code addSuggestedFix}/{@code addEvidenceItem} builder convention elsewhere in this codebase.
 */
public final class AgentObservation {

    private final FailureDiagnosis failureDiagnosis;
    private final List<EvidenceItem> evidenceItems;

    private AgentObservation(Builder builder) {
        this.failureDiagnosis = Objects.requireNonNull(builder.failureDiagnosis,
                "failureDiagnosis must not be null");
        this.evidenceItems = builder.evidenceItems != null
                ? Collections.unmodifiableList(
                        builder.evidenceItems.stream().filter(Objects::nonNull).collect(Collectors.toList()))
                : Collections.emptyList();
    }

    /** Never null. */
    public FailureDiagnosis getFailureDiagnosis() {
        return failureDiagnosis;
    }

    /**
     * Never null; empty when no evidence was supplied. Unmodifiable, independent of the source
     * list passed in. Every status here was determined elsewhere (Phase 5/6/7) — this class never
     * computes or upgrades an {@code EvidenceStatus}.
     */
    public List<EvidenceItem> getEvidenceItems() {
        return evidenceItems;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private FailureDiagnosis failureDiagnosis;
        private List<EvidenceItem> evidenceItems;

        public Builder failureDiagnosis(FailureDiagnosis failureDiagnosis) {
            this.failureDiagnosis = failureDiagnosis;
            return this;
        }

        public Builder evidenceItems(List<EvidenceItem> evidenceItems) {
            this.evidenceItems = evidenceItems;
            return this;
        }

        public Builder addEvidenceItem(EvidenceItem evidenceItem) {
            if (evidenceItem != null) {
                if (this.evidenceItems == null) {
                    this.evidenceItems = new ArrayList<>();
                } else if (!(this.evidenceItems instanceof ArrayList)) {
                    this.evidenceItems = new ArrayList<>(this.evidenceItems);
                }
                this.evidenceItems.add(evidenceItem);
            }
            return this;
        }

        public AgentObservation build() {
            return new AgentObservation(this);
        }
    }
}
