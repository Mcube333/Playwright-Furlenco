package com.framework.ai.agent;

/**
 * Phase 8 Step 2: the safe, initial agent lifecycle state vocabulary.
 *
 * PURE VALUE TYPE — no transition engine, no execution behavior. Deliberately stops at
 * {@link #PROPOSE}/{@link #BLOCKED}; execution-adjacent states ({@code APPROVED},
 * {@code EXECUTING}, {@code SUCCEEDED}, {@code FAILED}) are out of scope until a future step
 * introduces an execution guard and explicit approval flow.
 */
public enum AgentState {
    /** Gathering facts about a diagnosis (and, in a future step, optionally live read-only Page state). */
    OBSERVE,
    /** Interpreting an observation to form candidate decisions. */
    ANALYZE,
    /** A decision has been formed and is offered for review; nothing has been approved or executed. */
    PROPOSE,
    /** The agent could not (or was not allowed to) proceed; the decision remains visible, never discarded. */
    BLOCKED
}
