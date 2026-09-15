package com.framework.ai.diagnosis;

import com.framework.ai.locatoradvisor.LocatorAnalysisResponse;
import com.framework.ai.locatoradvisor.runtime.RuntimeValidationResult;
import com.framework.ai.model.AiAnalysisResponse;
import com.framework.ai.model.FailureContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable aggregate of everything known about one failed test's diagnosis.
 *
 * Pure composition of existing models — {@link FailureContext} (framework-captured facts),
 * {@link AiAnalysisResponse} (Phase 2), {@link LocatorAnalysisResponse} (Phase 5), and
 * {@link RuntimeValidationResult} (Phase 6) — plus the {@link SuggestedFix} list derived from
 * them. This class computes nothing and calls nothing; {@code aiAnalysis}, {@code locatorAnalysis},
 * and {@code runtimeValidation} may legitimately be null (no placeholder objects are manufactured
 * to avoid that), reflecting that AI analysis, locator analysis, and runtime validation are each
 * independently optional/unavailable in a given diagnosis.
 */
public final class FailureDiagnosis {

    private final FailureContext failureContext;
    private final AiAnalysisResponse aiAnalysis;
    private final LocatorAnalysisResponse locatorAnalysis;
    private final RuntimeValidationResult runtimeValidation;
    private final List<SuggestedFix> suggestedFixes;

    private FailureDiagnosis(Builder builder) {
        this.failureContext = builder.failureContext;
        this.aiAnalysis = builder.aiAnalysis;
        this.locatorAnalysis = builder.locatorAnalysis;
        this.runtimeValidation = builder.runtimeValidation;
        this.suggestedFixes = builder.suggestedFixes != null
                ? Collections.unmodifiableList(new ArrayList<>(builder.suggestedFixes))
                : Collections.emptyList();
    }

    /** Nullable in principle (builder enforces nothing), but expected to always be supplied in practice. */
    public FailureContext getFailureContext() {
        return failureContext;
    }

    /** Nullable — AI failure analysis may be disabled or unavailable. */
    public AiAnalysisResponse getAiAnalysis() {
        return aiAnalysis;
    }

    /** Nullable — locator analysis may not have been performed for this failure. */
    public LocatorAnalysisResponse getLocatorAnalysis() {
        return locatorAnalysis;
    }

    /** Nullable — runtime validation may not have been performed for this failure. */
    public RuntimeValidationResult getRuntimeValidation() {
        return runtimeValidation;
    }

    /** Never null; empty when no fixes were supplied. Unmodifiable, independent of the source list passed in. */
    public List<SuggestedFix> getSuggestedFixes() {
        return suggestedFixes;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private FailureContext failureContext;
        private AiAnalysisResponse aiAnalysis;
        private LocatorAnalysisResponse locatorAnalysis;
        private RuntimeValidationResult runtimeValidation;
        private List<SuggestedFix> suggestedFixes;

        public Builder failureContext(FailureContext failureContext) {
            this.failureContext = failureContext;
            return this;
        }

        public Builder aiAnalysis(AiAnalysisResponse aiAnalysis) {
            this.aiAnalysis = aiAnalysis;
            return this;
        }

        public Builder locatorAnalysis(LocatorAnalysisResponse locatorAnalysis) {
            this.locatorAnalysis = locatorAnalysis;
            return this;
        }

        public Builder runtimeValidation(RuntimeValidationResult runtimeValidation) {
            this.runtimeValidation = runtimeValidation;
            return this;
        }

        public Builder suggestedFixes(List<SuggestedFix> suggestedFixes) {
            this.suggestedFixes = suggestedFixes;
            return this;
        }

        public Builder addSuggestedFix(SuggestedFix suggestedFix) {
            if (suggestedFix != null) {
                if (this.suggestedFixes == null) {
                    this.suggestedFixes = new ArrayList<>();
                } else if (!(this.suggestedFixes instanceof ArrayList)) {
                    this.suggestedFixes = new ArrayList<>(this.suggestedFixes);
                }
                this.suggestedFixes.add(suggestedFix);
            }
            return this;
        }

        public FailureDiagnosis build() {
            return new FailureDiagnosis(this);
        }
    }
}
