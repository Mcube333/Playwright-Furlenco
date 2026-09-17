package com.framework.ai.orchestration;

import com.framework.ai.agent.AgentAction;
import com.framework.ai.agent.AgentExecutionGuard;
import com.framework.ai.agent.AgentExecutionGuardResult;
import com.framework.ai.agent.SelfHealingRecommendation;
import com.framework.ai.codegeneration.EvidenceItem;
import com.framework.ai.diagnosis.FixType;
import java.util.List;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Phase 10 Step 4: the smallest possible representation of "what a future executor would be
 * allowed to receive and what it would return" — a controlled execution boundary contract that
 * itself performs no execution.
 *
 * <pre>
 * AgentApprovalRecord -&gt; AgentExecutionGuard -&gt; [this class] -&gt; NOT_EXECUTED / BLOCKED
 * </pre>
 *
 * THIS CLASS NEVER EXECUTES AN ACTION. There is no {@code apply}/{@code run}/{@code dispatch}/
 * {@code perform}/{@code click}/{@code fill}/{@code navigate} method anywhere in this class, and it
 * holds no reference to anything capable of one — no {@code Page}, no {@code Locator}, no
 * {@code WebDriver}, no direct AI provider client, no shell/process/Git/tool-registry capability.
 *
 * CENTRAL SAFETY INVARIANT: {@code APPROVED + guard-allowed} STILL PRODUCES
 * {@link AgentActionResultStatus#NOT_EXECUTED}, NEVER {@link AgentActionResultStatus#EXECUTED}.
 * {@link #execute(AgentApprovalRecord)} has no production code path that ever constructs an
 * {@link AgentActionResult} with status {@code EXECUTED} — that value exists on
 * {@link AgentActionResultStatus} only so a genuine future executor could report it; this class is
 * deliberately not that executor. Confidence, {@code EvidenceStatus.VERIFIED}, and every existing
 * safety flag being enabled cannot change this — this class is stateless with respect to
 * {@code AiConfig} (it never reads it) and instead defers entirely, and exclusively, to the
 * unmodified {@link AgentExecutionGuard} for any policy verdict, then intentionally ignores an
 * {@code allowed=true} answer's implication beyond "not blocked."
 *
 * DISTINCT FROM {@link AgentActionOrchestrationService}. That Step 3 class already sequences
 * approval → guard → result → {@link AgentActionAuditRecord} → {@link AgentActionAuditStore}. This
 * class is deliberately not wired into it (Step 3's orchestrator is a protected file for this
 * step) — it exists standalone, representing the seam where a genuine future executor would one
 * day be substituted, without that substitution having happened yet.
 *
 * WHY {@link AgentExecutionGuard} IS CONSULTED WITH {@code (null, null)}. Identical reasoning to
 * {@link AgentActionOrchestrationService}: {@link AgentApprovalRecord} carries a
 * {@link SelfHealingRecommendation}, not an {@code AgentContext}/{@code AgentDecision} — neither
 * exists in this data flow, and {@code AgentContext} specifically requires a {@code FailureDiagnosis}
 * this class has no legitimate way to reconstruct. Rather than fabricate either object, this class
 * calls the real, unmodified guard with the same input pair its own code already handles
 * deterministically (a real, non-fabricated {@code blocked} result with an honest reason). The
 * guard is never bypassed, never weakened, and never re-implemented.
 *
 * ACTION VALIDATION. The {@link AgentAction} associated with an approval is derived from the
 * existing, already-present {@link FixType} on the underlying {@link SelfHealingRecommendation} —
 * the same deterministic, closed-enum mapping {@link AgentActionOrchestrationService} already uses
 * — never parsed from free-text description, never invented. A {@code null}/unmapped action
 * ({@link AgentAction#NONE}) is treated as unsupported and blocks before the guard is ever
 * consulted.
 */
public final class AgentActionExecutor {

    private static final Logger LOGGER = LogManager.getLogger(AgentActionExecutor.class);

    private final AgentExecutionGuard agentExecutionGuard;

    public AgentActionExecutor() {
        this(new AgentExecutionGuard());
    }

    public AgentActionExecutor(AgentExecutionGuard agentExecutionGuard) {
        this.agentExecutionGuard = Objects.requireNonNull(agentExecutionGuard, "AgentExecutionGuard must not be null");
    }

    /**
     * Evaluates {@code approvalRecord} against the current execution policy and returns a
     * deterministic {@link AgentActionResult}. Never executes anything, never throws for any
     * input, and never returns {@link AgentActionResultStatus#EXECUTED}.
     *
     * @param approvalRecord an already-produced Phase 9 approval decision; {@code null} is a safe,
     *                        deterministic {@code BLOCKED} result, not an exception
     * @return never null; {@link AgentActionResultStatus#BLOCKED} or
     *         {@link AgentActionResultStatus#NOT_EXECUTED} only
     */
    public AgentActionResult execute(AgentApprovalRecord approvalRecord) {
        if (approvalRecord == null) {
            return blocked(AgentAction.NONE, List.of(), "No approval record supplied.");
        }

        try {
            SelfHealingRecommendation recommendation = approvalRecord.getRecommendation();
            AgentAction action = actionFor(recommendation.getFixType());
            List<EvidenceItem> evidenceItems = recommendation.getEvidenceItems();

            switch (approvalRecord.getStatus()) {
                case REJECTED:
                    return blocked(action, evidenceItems,
                            "Approval was rejected; this action is not eligible for execution.");
                case PENDING:
                    return blocked(action, evidenceItems,
                            "Approval decision is still pending; this action is not eligible for execution.");
                case APPROVED:
                    return executeApproved(action, evidenceItems);
                default:
                    // Unreachable with the current closed AgentApprovalStatus enum, but a switch
                    // must not silently assume a branch for a value it does not recognize.
                    return blocked(action, evidenceItems, "Unrecognized approval status.");
            }
        } catch (Exception e) {
            // Absolute boundary: this class must never throw into the caller, and a failure here
            // must still produce an honest, non-fabricated BLOCKED result — never EXECUTED.
            LOGGER.warn("Unexpected error while evaluating an approval for execution: {}", e.getMessage());
            return blocked(AgentAction.NONE, List.of(),
                    "Unexpected error while evaluating this approval: " + e.getMessage());
        }
    }

    private AgentActionResult executeApproved(AgentAction action, List<EvidenceItem> evidenceItems) {
        if (action == null || action == AgentAction.NONE) {
            return blocked(AgentAction.NONE, evidenceItems,
                    "No supported action is associated with this approval; execution is not eligible.");
        }

        AgentExecutionGuardResult guardResult;
        try {
            guardResult = agentExecutionGuard.evaluate(null, null);
        } catch (Exception e) {
            LOGGER.warn("AgentExecutionGuard.evaluate() threw unexpectedly: {}", e.getMessage());
            return blocked(action, evidenceItems,
                    "Execution guard evaluation failed unexpectedly: " + e.getMessage());
        }

        if (guardResult == null || !guardResult.isAllowed()) {
            String reason = guardResult != null ? guardResult.getReason() : "Execution guard returned no result.";
            return blocked(action, evidenceItems, reason);
        }

        // CENTRAL SAFETY INVARIANT (see class Javadoc): guard-allowed still never becomes
        // EXECUTED. No concrete execution capability exists in this phase, regardless of anything
        // about the approval, the evidence, the confidence, or the guard's own verdict.
        return AgentActionResult.builder()
                .action(action)
                .status(AgentActionResultStatus.NOT_EXECUTED)
                .message("No execution capability is enabled in this phase.")
                .evidenceItems(evidenceItems)
                .build();
    }

    private AgentActionResult blocked(AgentAction action, List<EvidenceItem> evidenceItems, String message) {
        return AgentActionResult.builder()
                .action(action)
                .status(AgentActionResultStatus.BLOCKED)
                .message(message)
                .evidenceItems(evidenceItems)
                .build();
    }

    /**
     * A fixed, deterministic label mapping from the existing {@link FixType} to the existing,
     * closed {@link AgentAction} vocabulary — for validation/labeling only, identical mapping to
     * {@link AgentActionOrchestrationService}'s own. {@link AgentAction#SCREENSHOT}/
     * {@link AgentAction#DOM_CAPTURE} are Phase 8 observation-only actions with no corresponding
     * {@link FixType}; they are structurally unreachable through this mapping, which is an honest
     * reflection of the current recommendation model, not an omission.
     */
    private AgentAction actionFor(FixType fixType) {
        if (fixType == null) {
            return AgentAction.NONE;
        }
        return switch (fixType) {
            case LOCATOR -> AgentAction.LOCATOR_RECOMMENDATION;
            case WAIT -> AgentAction.WAIT_RECOMMENDATION;
            case ASSERTION, TEST_DATA -> AgentAction.ASSERTION_RECOMMENDATION;
            default -> AgentAction.NONE;
        };
    }
}
