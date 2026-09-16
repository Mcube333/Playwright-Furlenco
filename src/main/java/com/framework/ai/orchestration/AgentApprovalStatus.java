package com.framework.ai.orchestration;

/**
 * Phase 9 Step 6: the closed, three-value vocabulary of a human's decision about a
 * {@link com.framework.ai.agent.SelfHealingRecommendation}.
 *
 * PURE REPRESENTATION — this enum has no behavior and no side effects, exactly like
 * {@link com.framework.ai.agent.AgentAction}/{@link com.framework.ai.agent.AgentState} in Phase 8.
 * Deliberately excludes any execution-state value ({@code EXECUTED}, {@code EXECUTING},
 * {@code FAILED}) and any automatic-decision value ({@code AUTO_APPROVED}, {@code AUTO_REJECTED})
 * — by design, not by omission. {@link #APPROVED} records only that a human decided to approve a
 * recommendation; it never means the recommendation was, is being, or ever will be executed. A
 * future, separately-reviewed execution phase would still have to pass
 * {@link com.framework.ai.agent.AgentExecutionGuard} independently of this status.
 */
public enum AgentApprovalStatus {
    /** A human has not yet decided. The default, safest status. */
    PENDING,
    /** A human explicitly approved the recommendation. Does not imply execution, safety, or guard approval. */
    APPROVED,
    /** A human explicitly rejected the recommendation. */
    REJECTED
}
