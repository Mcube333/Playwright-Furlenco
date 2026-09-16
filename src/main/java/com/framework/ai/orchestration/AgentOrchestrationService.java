package com.framework.ai.orchestration;

import com.framework.ai.agent.AgentContext;
import com.framework.ai.agent.AgentDecision;
import com.framework.ai.agent.AgentExecutionGuard;
import com.framework.ai.agent.AgentExecutionGuardResult;
import com.framework.ai.agent.AgentReasoningService;
import com.framework.ai.agent.SelfHealingRecommendation;
import com.framework.ai.agent.SelfHealingRecommendationReporter;
import com.framework.ai.agent.SelfHealingRecommendationService;
import com.framework.ai.diagnosis.FailureDiagnosis;
import java.util.List;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Phase 9 Step 2: the smallest possible orchestration adapter composing the already-existing,
 * already-approved Phase 8 pipeline into one explicit call.
 *
 * <pre>
 * FailureDiagnosis -&gt; AgentContext -&gt; AgentReasoningService -&gt; AgentDecision -&gt; AgentExecutionGuard
 *                                                                                       -&gt; SelfHealingRecommendationService
 * </pre>
 *
 * PURE ORCHESTRATION — this class implements no reasoning, no browser tooling, no self-healing
 * logic, no locator discovery, no evidence generation, no approval logic, and no execution. Every
 * step above delegates entirely to the existing, unmodified Phase 8 class named for it; this class
 * only sequences those calls and passes their outputs through unchanged. There is no
 * {@code execute}/{@code apply}/{@code run} method anywhere in this class.
 *
 * EXPLICITLY INVOKED ONLY. This class is never referenced by {@code TestListener},
 * {@code RetryAnalyzer}/{@code RetryTransformer}, {@code PlaywrightManager}, or any TestNG
 * lifecycle annotation — nothing in the existing test lifecycle calls it. A caller must construct
 * an instance and call {@link #recommend(FailureDiagnosis)} directly.
 *
 * GUARD IS NEVER BYPASSED, AND NEVER USED TO ENABLE ANYTHING. The {@link AgentDecision} returned by
 * {@link AgentReasoningService#analyze(AgentContext)} is passed into
 * {@link AgentExecutionGuard#evaluate(AgentContext, AgentDecision)} unmodified, and the resulting
 * {@link AgentExecutionGuardResult} is passed through to
 * {@link SelfHealingRecommendationService#recommend(FailureDiagnosis, AgentDecision, AgentExecutionGuardResult)}
 * exactly as that method already expects it. This class never reads the guard result's boolean
 * permission field and never branches on it — the existing, unmodified
 * {@code SelfHealingRecommendationService} already treats the guard result as correlation-only
 * (never a permission signal), and this class does not change that.
 *
 * FAIL-CLOSED. A {@code null} {@link FailureDiagnosis} short-circuits before any AI call, browser
 * call, or downstream service call, returning an empty list. Any unexpected exception thrown by a
 * collaborator is caught at this class's boundary and also results in an empty list — this class
 * never lets an internal failure escape into a caller's TestNG lifecycle, and never fabricates a
 * recommendation to compensate for one.
 *
 * Phase 9 Step 3 additionally exposes {@link #recommendAndReport(FailureDiagnosis)} — an explicit,
 * separate entry point that runs the exact same {@link #recommend(FailureDiagnosis)} pipeline and
 * then, only if that call is made, hands the resulting list to the existing, unmodified
 * {@link SelfHealingRecommendationReporter}. {@link #recommend(FailureDiagnosis)} itself is
 * unchanged by this addition: it never reports, and a caller who only wants recommendations (no
 * reporting side effect) keeps exactly the behavior Step 2 already gave them.
 */
public class AgentOrchestrationService {

    private static final Logger LOGGER = LogManager.getLogger(AgentOrchestrationService.class);

    private final AgentReasoningService agentReasoningService;
    private final AgentExecutionGuard agentExecutionGuard;
    private final SelfHealingRecommendationService selfHealingRecommendationService;
    private final SelfHealingRecommendationReporter selfHealingRecommendationReporter;

    public AgentOrchestrationService() {
        this(new AgentReasoningService(), new AgentExecutionGuard(), new SelfHealingRecommendationService(),
                new SelfHealingRecommendationReporter());
    }

    /**
     * Preserved exactly as Step 2 defined it, for full backward compatibility: a caller who never
     * intends to call {@link #recommendAndReport(FailureDiagnosis)} does not need to supply a
     * reporter. Internally delegates to the 4-arg constructor with a real, unmodified
     * {@link SelfHealingRecommendationReporter}.
     */
    public AgentOrchestrationService(AgentReasoningService agentReasoningService,
                                      AgentExecutionGuard agentExecutionGuard,
                                      SelfHealingRecommendationService selfHealingRecommendationService) {
        this(agentReasoningService, agentExecutionGuard, selfHealingRecommendationService,
                new SelfHealingRecommendationReporter());
    }

    public AgentOrchestrationService(AgentReasoningService agentReasoningService,
                                      AgentExecutionGuard agentExecutionGuard,
                                      SelfHealingRecommendationService selfHealingRecommendationService,
                                      SelfHealingRecommendationReporter selfHealingRecommendationReporter) {
        this.agentReasoningService = Objects.requireNonNull(agentReasoningService, "AgentReasoningService must not be null");
        this.agentExecutionGuard = Objects.requireNonNull(agentExecutionGuard, "AgentExecutionGuard must not be null");
        this.selfHealingRecommendationService = Objects.requireNonNull(selfHealingRecommendationService,
                "SelfHealingRecommendationService must not be null");
        this.selfHealingRecommendationReporter = Objects.requireNonNull(selfHealingRecommendationReporter,
                "SelfHealingRecommendationReporter must not be null");
    }

    /**
     * Runs the full Phase 8 pipeline against an already-produced {@code failureDiagnosis} and
     * returns whatever {@link SelfHealingRecommendationService} decides to produce from it. Never
     * throws, never executes anything, and never calls an AI provider or a browser directly —
     * every one of those responsibilities belongs entirely to the existing collaborator named for
     * it.
     *
     * @param failureDiagnosis an already-produced diagnosis (e.g. from
     *                         {@code FailureDiagnosisHelper}/{@code FailureDiagnosisService});
     *                         {@code null} is treated as "nothing to reason about," not an error
     * @return never null; empty when {@code failureDiagnosis} is null, when no recommendation
     *         could be produced, or when any collaborator failed unexpectedly
     */
    public List<SelfHealingRecommendation> recommend(FailureDiagnosis failureDiagnosis) {
        if (failureDiagnosis == null) {
            LOGGER.debug("AgentOrchestrationService.recommend() called with a null FailureDiagnosis; "
                    + "no reasoning or recommendation will be attempted.");
            return List.of();
        }

        try {
            AgentContext context = AgentContext.builder().failureDiagnosis(failureDiagnosis).build();

            AgentDecision decision = agentReasoningService.analyze(context);

            // The guard is always consulted — never skipped, never bypassed — even though the
            // existing SelfHealingRecommendationService never reads its allow/block value. This
            // preserves the full, auditable pipeline shape without granting the guard's result any
            // new meaning it does not already have.
            AgentExecutionGuardResult guardResult = agentExecutionGuard.evaluate(context, decision);

            List<SelfHealingRecommendation> recommendations =
                    selfHealingRecommendationService.recommend(failureDiagnosis, decision, guardResult);

            return recommendations != null ? recommendations : List.of();

        } catch (Exception e) {
            // Absolute boundary: orchestration must never throw into the caller, and must never
            // let a collaborator failure surface as a fabricated recommendation.
            LOGGER.warn("Unexpected error during agent orchestration: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Phase 9 Step 3: {@link #recommend(FailureDiagnosis)} followed by an explicit report of
     * whatever it returned, via the existing, unmodified {@link SelfHealingRecommendationReporter}.
     *
     * SEPARATE, EXPLICIT ENTRY POINT — never called by {@link #recommend(FailureDiagnosis)} itself,
     * and calling this method never changes what {@link #recommend(FailureDiagnosis)} alone would
     * have returned for the same input. Reporting is purely an additional, observational side
     * effect requested by the caller; it never regenerates, reinterprets, or fabricates a
     * recommendation, and never promotes any {@code EvidenceStatus} or confidence value —
     * {@link SelfHealingRecommendationReporter} only renders what {@link #recommend(FailureDiagnosis)}
     * already produced.
     *
     * FAILS CLOSED ON THE REPORTING STEP WITHOUT DISCARDING THE RESULT. If reporting itself throws
     * unexpectedly, the failure is caught and logged here — it never propagates, never triggers a
     * retry, and never re-runs orchestration — and this method still returns the exact
     * recommendation list {@link #recommend(FailureDiagnosis)} produced, so a reporting failure
     * never hides or discards an otherwise-successful recommendation result.
     *
     * @param failureDiagnosis same contract as {@link #recommend(FailureDiagnosis)}; {@code null}
     *                         short-circuits identically (no reasoning, no reporting)
     * @return the same list {@link #recommend(FailureDiagnosis)} would have returned for this input
     */
    public List<SelfHealingRecommendation> recommendAndReport(FailureDiagnosis failureDiagnosis) {
        List<SelfHealingRecommendation> recommendations = recommend(failureDiagnosis);

        if (failureDiagnosis == null) {
            // Mirrors recommend()'s own null short-circuit exactly: "nothing to reason about" also
            // means nothing to report — a null diagnosis never reaches the reporter either.
            return recommendations;
        }

        try {
            selfHealingRecommendationReporter.attachToAllure(recommendations);
        } catch (Exception e) {
            // Absolute boundary: a reporting failure must never discard an already-produced
            // recommendation result, never retry, and never re-run orchestration.
            LOGGER.warn("Unexpected error while reporting self-healing recommendations: {}", e.getMessage());
        }

        return recommendations;
    }
}
