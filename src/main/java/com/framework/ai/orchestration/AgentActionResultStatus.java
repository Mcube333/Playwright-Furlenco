package com.framework.ai.orchestration;

/**
 * Phase 10 Step 2: the closed, four-value vocabulary of what happened (or explicitly did not
 * happen) to an approved agent action.
 *
 * PURE REPRESENTATION — this enum has no behavior and no side effects, exactly like
 * {@link com.framework.ai.agent.AgentAction}/{@link com.framework.ai.agent.AgentState} in Phase 8
 * and {@link AgentApprovalStatus} in Phase 9. Deliberately excludes {@code APPROVED}/{@code PENDING}
 * (those are {@link AgentApprovalStatus} concepts, not result concepts), {@code EXECUTING}/
 * {@code SUCCEEDED}/{@code RETRYING}/{@code CANCELLED} (deferred, not yet needed by any real code
 * path), by design, not by omission.
 *
 * NONE OF THESE VALUES IS EVER SET BY THIS PHASE'S OWN CODE AS A RESULT OF RUNNING ANYTHING. This
 * enum, {@link AgentActionResult}, {@link AgentActionAuditRecord}, {@link AgentActionAuditStore},
 * and {@link AgentActionAuditReporter} only ever store and render a value a caller explicitly
 * supplied — there is no executor anywhere in this codebase that could ever produce
 * {@link #EXECUTED} or {@link #FAILED} from an actual browser action, because no such executor
 * exists.
 */
public enum AgentActionResultStatus {
    /** The action was never run. The default, safest status — and the only one any current caller can substantiate. */
    NOT_EXECUTED,
    /** Data representation only: a future executor could report this. No code in this codebase ever sets it from a real run. */
    EXECUTED,
    /** Data representation only: a future executor could report this. No code in this codebase ever sets it from a real run. */
    FAILED,
    /** The action was withheld — e.g. by policy, by {@code AgentExecutionGuard}, or by an explicit human decision. */
    BLOCKED
}
