package com.framework.ai.agent;

import com.framework.ai.diagnosis.FailureDiagnosis;
import java.util.Objects;

/**
 * Phase 8 Step 2: the complete context available to a future agent for one diagnosis/agent run.
 *
 * PURE DATA CONTRACT. This class computes nothing, calls nothing, and executes nothing — it only
 * carries a reference to an existing, already-produced {@link FailureDiagnosis} (Phase 7). It does
 * not call {@code AiClient}, does not touch Playwright, and is not aware of {@code ITestResult}.
 *
 * Deliberately minimal for this step: the Phase 8 Step 1 architecture also described an
 * {@code AgentPolicy} and an iteration/budget counter as conceptual parts of this context, but
 * both are explicitly out of scope until a later, separately-reviewed step — they are not added
 * here merely to satisfy that earlier conceptual description.
 *
 * {@code failureDiagnosis} is a required field: unlike some existing Phase 7 models that leave
 * their primary field nullable (e.g. {@link FailureDiagnosis#getFailureContext()}), this class
 * fails fast on construction, per the explicit Step 2 requirement that {@code AgentContext} carry
 * a non-null {@code FailureDiagnosis}.
 */
public final class AgentContext {

    private final FailureDiagnosis failureDiagnosis;

    private AgentContext(Builder builder) {
        this.failureDiagnosis = Objects.requireNonNull(builder.failureDiagnosis,
                "failureDiagnosis must not be null");
    }

    /** Never null. */
    public FailureDiagnosis getFailureDiagnosis() {
        return failureDiagnosis;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private FailureDiagnosis failureDiagnosis;

        public Builder failureDiagnosis(FailureDiagnosis failureDiagnosis) {
            this.failureDiagnosis = failureDiagnosis;
            return this;
        }

        public AgentContext build() {
            return new AgentContext(this);
        }
    }
}
