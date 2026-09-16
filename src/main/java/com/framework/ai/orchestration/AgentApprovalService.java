package com.framework.ai.orchestration;

import com.framework.ai.agent.SelfHealingRecommendation;

/**
 * Phase 9 Step 6: the smallest possible factory for {@link AgentApprovalRecord}s.
 *
 * PURE CONSTRUCTION — every method here does nothing but build and return an
 * {@link AgentApprovalRecord}. There is no reasoning, no AI call, no Playwright call, no
 * {@code AgentExecutionGuard} interaction, no retry, no source-code or version-control operation,
 * and no execution of any kind anywhere in this class. Calling {@link #approve(SelfHealingRecommendation, String)}
 * only records that a human approved a recommendation — it never makes that recommendation
 * executable, never touches {@code AgentExecutionGuard}, and never changes
 * {@code ai.agent.execution.enabled}/{@code ai.agent.browser.mutation.enabled}/
 * {@code ai.agent.max.actions} or reads them.
 *
 * NO AUTOMATIC OR INFERRED APPROVAL. This class has no method that derives a status from an
 * {@code AgentDecision}, an {@code AgentAction}, an {@code EvidenceStatus}, a confidence value, or
 * an environment name — {@link AgentApprovalStatus#APPROVED} and
 * {@link AgentApprovalStatus#REJECTED} can only ever be produced by a caller explicitly invoking
 * {@link #approve(SelfHealingRecommendation, String)} or
 * {@link #reject(SelfHealingRecommendation, String)}. Nothing in this class, or anywhere else in
 * the codebase, calls either method automatically.
 *
 * STATELESS. No mutable instance or static state is held; every call is an independent,
 * side-effect-free construction.
 */
public class AgentApprovalService {

    /**
     * Records that a recommendation has not yet been decided on. Useful for a caller that wants to
     * represent "awaiting human review" as an explicit, typed value rather than the absence of one.
     *
     * @param recommendation the recommendation under review; must not be {@code null}
     * @throws NullPointerException if {@code recommendation} is {@code null} — a deliberate,
     *                               explicit API call is expected to supply one
     */
    public AgentApprovalRecord pending(SelfHealingRecommendation recommendation) {
        return AgentApprovalRecord.builder()
                .recommendation(recommendation)
                .status(AgentApprovalStatus.PENDING)
                .build();
    }

    /**
     * Records an explicit human approval. {@code reason} is free-text commentary only — it is
     * never interpreted, never parsed for intent, and never treated as authorization; the fact
     * that this method was called at all is the only thing that means anything.
     *
     * @param recommendation the recommendation being approved; must not be {@code null}
     * @param reason         optional human commentary; {@code null} is stored as empty
     */
    public AgentApprovalRecord approve(SelfHealingRecommendation recommendation, String reason) {
        return AgentApprovalRecord.builder()
                .recommendation(recommendation)
                .status(AgentApprovalStatus.APPROVED)
                .reason(reason)
                .build();
    }

    /** Same as {@link #approve(SelfHealingRecommendation, String)}, additionally recording who decided. */
    public AgentApprovalRecord approve(SelfHealingRecommendation recommendation, String reason, String actor) {
        return AgentApprovalRecord.builder()
                .recommendation(recommendation)
                .status(AgentApprovalStatus.APPROVED)
                .reason(reason)
                .actor(actor)
                .build();
    }

    /**
     * Records an explicit human rejection. Same non-interpretation guarantee as
     * {@link #approve(SelfHealingRecommendation, String)} applies to {@code reason} here.
     *
     * @param recommendation the recommendation being rejected; must not be {@code null}
     * @param reason         optional human commentary; {@code null} is stored as empty
     */
    public AgentApprovalRecord reject(SelfHealingRecommendation recommendation, String reason) {
        return AgentApprovalRecord.builder()
                .recommendation(recommendation)
                .status(AgentApprovalStatus.REJECTED)
                .reason(reason)
                .build();
    }

    /** Same as {@link #reject(SelfHealingRecommendation, String)}, additionally recording who decided. */
    public AgentApprovalRecord reject(SelfHealingRecommendation recommendation, String reason, String actor) {
        return AgentApprovalRecord.builder()
                .recommendation(recommendation)
                .status(AgentApprovalStatus.REJECTED)
                .reason(reason)
                .actor(actor)
                .build();
    }
}
