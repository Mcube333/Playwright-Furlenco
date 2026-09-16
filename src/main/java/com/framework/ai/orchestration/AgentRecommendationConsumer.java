package com.framework.ai.orchestration;

import com.framework.ai.agent.SelfHealingRecommendation;
import com.framework.ai.diagnosis.FailureDiagnosis;
import java.util.List;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Phase 9 Step 4: the smallest possible consumer-facing wrapper demonstrating how QA/test code
 * intentionally requests recommendations from the already-approved Step 2 pipeline.
 *
 * PURE DELEGATION — this class implements no reasoning, no orchestration, no reporting, and no
 * recommendation logic of its own. Its entire body is one call to
 * {@link AgentOrchestrationService#recommend(FailureDiagnosis)}; every responsibility (AI
 * reasoning, the execution guard, recommendation generation) belongs entirely to
 * {@link AgentOrchestrationService}, which itself delegates to the existing, unmodified Phase 8
 * classes. There is no {@code execute}/{@code apply}/{@code approve}/{@code accept}/{@code heal}
 * method anywhere in this class.
 *
 * NO SEPARATE REPORTING METHOD. {@link AgentOrchestrationService#recommendAndReport(FailureDiagnosis)}
 * (Step 3) already provides the explicit reporting boundary a caller who wants reporting can use
 * directly on the same injected {@code orchestrationService} instance — duplicating that as a
 * {@code consumeAndReport(...)} method on this class would only forward the call with no added
 * value, so this class intentionally exposes a single method.
 *
 * EXPLICITLY INVOKED ONLY. Never referenced by {@code TestListener},
 * {@code RetryAnalyzer}/{@code RetryTransformer}, {@code PlaywrightManager}, or any TestNG
 * lifecycle annotation. A caller must construct an instance and call
 * {@link #consume(FailureDiagnosis)} directly.
 *
 * RESULT INTEGRITY. The list returned by {@link AgentOrchestrationService#recommend(FailureDiagnosis)}
 * is returned exactly as received — never reordered, filtered, rescored, or copied into new
 * {@link SelfHealingRecommendation} instances. Every recommendation therefore keeps whatever
 * {@code approvalRequired}, confidence, {@code EvidenceStatus}, {@code validationType}, proposed
 * locator, and rationale {@link AgentOrchestrationService} already gave it — this class has no
 * method capable of changing any of them.
 */
public class AgentRecommendationConsumer {

    private static final Logger LOGGER = LogManager.getLogger(AgentRecommendationConsumer.class);

    private final AgentOrchestrationService orchestrationService;

    public AgentRecommendationConsumer(AgentOrchestrationService orchestrationService) {
        this.orchestrationService = Objects.requireNonNull(orchestrationService, "AgentOrchestrationService must not be null");
    }

    /**
     * Requests recommendations for an already-produced {@code failureDiagnosis} by delegating
     * entirely to {@link AgentOrchestrationService#recommend(FailureDiagnosis)}. Never throws,
     * never executes anything, and never modifies {@code failureDiagnosis} or the returned list.
     *
     * @param failureDiagnosis an already-produced diagnosis; {@code null} is delegated as-is to
     *                         {@link AgentOrchestrationService#recommend(FailureDiagnosis)}, which
     *                         already treats it as "nothing to reason about," not an error
     * @return never null; exactly what {@link AgentOrchestrationService#recommend(FailureDiagnosis)}
     *         returned for this input, or an empty list if that call fails unexpectedly — this
     *         method performs no other normalization beyond what that existing, unmodified
     *         contract already guarantees
     */
    public List<SelfHealingRecommendation> consume(FailureDiagnosis failureDiagnosis) {
        try {
            List<SelfHealingRecommendation> recommendations = orchestrationService.recommend(failureDiagnosis);
            return recommendations != null ? recommendations : List.of();
        } catch (Exception e) {
            // Defense in depth: AgentOrchestrationService.recommend() already fails closed on its
            // own, but this boundary must hold independently of that guarantee — a consumer must
            // never propagate an orchestration failure, retry, or fabricate a recommendation.
            LOGGER.warn("Unexpected error while consuming agent recommendations: {}", e.getMessage());
            return List.of();
        }
    }
}
