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
 * Phase 10 Step 3: a controlled, explicit, non-executing boundary that connects an
 * {@link AgentApprovalRecord} to an {@link AgentActionAuditRecord} through a deterministic
 * {@link AgentActionResult} — without ever executing anything.
 *
 * <pre>
 * AgentApprovalRecord -&gt; [this class] -&gt; AgentActionResult -&gt; AgentActionAuditRecord -&gt; AgentActionAuditStore
 * </pre>
 *
 * THIS CLASS NEVER EXECUTES AN ACTION. There is no {@code execute}/{@code apply}/{@code run}/
 * {@code dispatch}/{@code perform} method anywhere in this class, and it holds no reference to
 * anything capable of one — no {@code Page}, no {@code Locator}, no {@code WebDriver}, no direct
 * AI provider client. {@link AgentActionResultStatus#EXECUTED} is a value this class's own status
 * enum can represent, but nothing in this class's logic ever assigns it — see
 * {@link #processApproval(AgentApprovalRecord)} below for exactly why.
 *
 * WHY THE APPROVED PATH PRODUCES {@code NOT_EXECUTED}, NEVER {@code EXECUTED}. There is no
 * executor anywhere in this codebase (confirmed by the Phase 10 Step 1 reconnaissance). This class
 * does not invent one. An {@link AgentApprovalStatus#APPROVED} record, even one the execution guard
 * would not block, still cannot honestly be reported as {@code EXECUTED} — nothing was run. This
 * class exists specifically to make that distinction explicit and auditable rather than leaving it
 * implicit.
 *
 * WHY {@link AgentExecutionGuard} IS CONSULTED WITH {@code (null, null)}. {@link AgentApprovalRecord}
 * carries a {@link SelfHealingRecommendation}, not an {@code AgentContext}/{@code AgentDecision} —
 * neither exists in this data flow, and {@code AgentContext} specifically requires a
 * {@code FailureDiagnosis} this class has no legitimate way to reconstruct. Rather than fabricate
 * either object (which this codebase's own conventions treat as inventing evidence/metadata the
 * repository cannot substantiate), this class calls the real, unmodified
 * {@link AgentExecutionGuard#evaluate(com.framework.ai.agent.AgentContext, com.framework.ai.agent.AgentDecision)}
 * with {@code (null, null)} — a input pair that class's own code already handles deterministically
 * (returning a real, non-fabricated {@code blocked} result with an honest reason). The guard is
 * never bypassed, never weakened, and never re-implemented; its real verdict is always the one
 * respected. In production, with the real, unmodified guard, this means the guard-consultation step
 * always yields {@code blocked}, honestly reflecting that this class has no legitimate execution
 * context to offer — not a workaround, a correct description of the current architecture.
 *
 * NO APPROVAL BYPASS, NO SECOND POLICY SYSTEM. This class introduces no evidence, confidence, or
 * approval logic of its own — {@link AgentApprovalStatus#REJECTED}/{@code PENDING} are never
 * eligible for processing regardless of anything else about the recommendation, and
 * {@link AgentApprovalStatus#APPROVED} is necessary but never sufficient for anything beyond
 * {@code NOT_EXECUTED}.
 */
public class AgentActionOrchestrationService {

    private static final Logger LOGGER = LogManager.getLogger(AgentActionOrchestrationService.class);

    private final AgentExecutionGuard agentExecutionGuard;
    private final AgentActionAuditStore auditStore;

    public AgentActionOrchestrationService() {
        this(new AgentExecutionGuard(), new AgentActionAuditStore());
    }

    public AgentActionOrchestrationService(AgentExecutionGuard agentExecutionGuard, AgentActionAuditStore auditStore) {
        this.agentExecutionGuard = Objects.requireNonNull(agentExecutionGuard, "AgentExecutionGuard must not be null");
        this.auditStore = Objects.requireNonNull(auditStore, "AgentActionAuditStore must not be null");
    }

    /**
     * Processes one {@code approvalRecord} against the current execution policy and records a
     * deterministic {@link AgentActionAuditRecord} in the injected {@link AgentActionAuditStore}.
     * Never executes anything, never throws for a well-formed non-null input, and never fabricates
     * an {@link AgentActionResultStatus#EXECUTED}/{@link AgentActionResultStatus#FAILED} result.
     *
     * @param approvalRecord an already-produced Phase 9 approval decision; {@code null} is treated
     *                        as "nothing to process," matching {@link AgentOrchestrationService}'s
     *                        and {@link AgentRecommendationConsumer}'s own established convention
     *                        for a missing primary input — not an error, and never stored, since an
     *                        {@link AgentActionAuditRecord} cannot honestly exist without a real
     *                        approval record to attach to
     * @return the stored audit record, or {@code null} if {@code approvalRecord} was {@code null}
     */
    public AgentActionAuditRecord processApproval(AgentApprovalRecord approvalRecord) {
        if (approvalRecord == null) {
            LOGGER.debug("AgentActionOrchestrationService.processApproval() called with a null AgentApprovalRecord; "
                    + "nothing to process, nothing recorded.");
            return null;
        }

        try {
            SelfHealingRecommendation recommendation = approvalRecord.getRecommendation();
            AgentAction action = actionFor(recommendation.getFixType());
            List<EvidenceItem> evidenceItems = recommendation.getEvidenceItems();

            AgentActionResult result;
            switch (approvalRecord.getStatus()) {
                case REJECTED:
                    result = blockedResult(action, evidenceItems, "Approval was rejected; the action is not eligible for processing.");
                    break;
                case PENDING:
                    result = blockedResult(action, evidenceItems, "Approval decision is still pending; the action is not eligible for processing.");
                    break;
                case APPROVED:
                    result = processApproved(action, evidenceItems);
                    break;
                default:
                    // Unreachable with the current closed AgentApprovalStatus enum, but a switch
                    // over an enum must be exhaustive without silently assuming a branch.
                    result = blockedResult(action, evidenceItems, "Unrecognized approval status.");
            }

            return storeAudit(approvalRecord, result);

        } catch (Exception e) {
            // Absolute boundary: this class must never throw into the caller, and a failure while
            // determining the result must still produce an honest, non-fabricated audit entry.
            LOGGER.warn("Unexpected error while processing an agent approval record: {}", e.getMessage());
            AgentActionResult failSafeResult = blockedResult(AgentAction.NONE, List.of(),
                    "Unexpected error while processing this approval: " + e.getMessage());
            return storeAudit(approvalRecord, failSafeResult);
        }
    }

    /**
     * The only branch where the execution guard is consulted — see the class Javadoc for exactly
     * why {@code (null, null)} is the honest input here, not a fabricated context/decision. Never
     * returns {@link AgentActionResultStatus#EXECUTED} regardless of the guard's answer: even an
     * {@code allowed=true} guard result (only reachable via a test double in this phase; the real,
     * unmodified guard cannot produce it for a null context) means only that nothing prevents a
     * future executor from running this action — not that anything ran now.
     */
    private AgentActionResult processApproved(AgentAction action, List<EvidenceItem> evidenceItems) {
        AgentExecutionGuardResult guardResult;
        try {
            guardResult = agentExecutionGuard.evaluate(null, null);
        } catch (Exception e) {
            LOGGER.warn("AgentExecutionGuard.evaluate() threw unexpectedly: {}", e.getMessage());
            return blockedResult(action, evidenceItems,
                    "Execution guard evaluation failed unexpectedly: " + e.getMessage());
        }

        if (guardResult == null || !guardResult.isAllowed()) {
            String reason = guardResult != null ? guardResult.getReason() : "Execution guard returned no result.";
            return blockedResult(action, evidenceItems, reason);
        }

        return AgentActionResult.builder()
                .action(action)
                .status(AgentActionResultStatus.NOT_EXECUTED)
                .message("Action approved and not blocked by the execution guard, but no execution capability "
                        + "exists in this phase.")
                .evidenceItems(evidenceItems)
                .build();
    }

    private AgentActionResult blockedResult(AgentAction action, List<EvidenceItem> evidenceItems, String message) {
        return AgentActionResult.builder()
                .action(action)
                .status(AgentActionResultStatus.BLOCKED)
                .message(message)
                .evidenceItems(evidenceItems)
                .build();
    }

    private AgentActionAuditRecord storeAudit(AgentApprovalRecord approvalRecord, AgentActionResult result) {
        AgentActionAuditRecord auditRecord = AgentActionAuditRecord.builder()
                .approvalRecord(approvalRecord)
                .actionResult(result)
                .build();
        auditStore.add(auditRecord);
        return auditRecord;
    }

    /**
     * A fixed, deterministic label mapping from the existing {@link FixType} (already present on
     * every {@link SelfHealingRecommendation}) to the existing, closed {@link AgentAction}
     * vocabulary — for audit labeling only. Never used to infer approval, never used to bypass the
     * guard, and never affects the resulting {@link AgentActionResultStatus}.
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
