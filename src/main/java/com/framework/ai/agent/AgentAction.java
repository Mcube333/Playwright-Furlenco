package com.framework.ai.agent;

/**
 * Phase 8 Step 2: the closed, initial action vocabulary a future agent may PROPOSE.
 *
 * PURE REPRESENTATION — this enum has no behavior and no side effects. None of these values
 * execute anything by existing; they are labels a future {@code AgentDecision} may carry, subject
 * to a future execution guard (not implemented in Step 2) before any of them could ever be acted
 * upon. Deliberately closed (an enum, not a free-form String) so the action vocabulary can never
 * silently grow at runtime from AI-generated text.
 *
 * Intentionally excludes anything execution-capable or destructive
 * ({@code EXECUTE_JAVASCRIPT}, {@code RUN_SHELL_COMMAND}, {@code MODIFY_SOURCE},
 * {@code MODIFY_TEST}, {@code DELETE_FILE}, {@code CHANGE_CONFIG}, {@code GIT_COMMIT},
 * {@code GIT_PUSH}, {@code CREATE_PR}, {@code DISABLE_ASSERTION}, {@code DISABLE_TEST},
 * {@code BYPASS_AUTHENTICATION}) and anything belonging to a later, not-yet-approved scope
 * ({@code RETRY_OBSERVATION}, {@code NAVIGATION_RECOMMENDATION}) — by design, not by omission.
 */
public enum AgentAction {
    NONE,
    LOCATOR_RECOMMENDATION,
    WAIT_RECOMMENDATION,
    ASSERTION_RECOMMENDATION,
    SCREENSHOT,
    DOM_CAPTURE
}
