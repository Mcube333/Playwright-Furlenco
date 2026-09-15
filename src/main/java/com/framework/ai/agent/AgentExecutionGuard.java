package com.framework.ai.agent;

import com.framework.ai.codegeneration.EvidenceItem;
import com.framework.ai.config.AiConfig;
import com.framework.ai.locatoradvisor.runtime.RuntimeEnvironmentGuard;
import com.framework.config.ConfigManager;
import java.util.List;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Phase 8 Step 5: the mandatory security/policy boundary between an {@link AgentDecision} and any
 * future action executor.
 *
 * <pre>
 * AgentDecision -&gt; AgentExecutionGuard -&gt; ALLOW / BLOCK -&gt; [no executor exists yet]
 * </pre>
 *
 * THIS CLASS NEVER EXECUTES AN ACTION. There is no {@code execute}/{@code apply}/{@code run}/
 * {@code click}/{@code fill}/{@code navigate} method anywhere in this class, and it holds no
 * reference to anything capable of one — no {@code Page}, no {@code Locator}, no
 * {@code ReadOnlyBrowserTool}, no {@code AiClient}, no {@code AgentReasoningService}. It is a pure,
 * deterministic, side-effect-free function from {@code (AgentContext, AgentDecision)} to
 * {@link AgentExecutionGuardResult}. AI is never the security boundary here: this class makes no
 * AI call of any kind — it only reads already-produced values.
 *
 * FAIL-CLOSED BY DESIGN. Every check below can only ever turn an {@code allowed} result into a
 * {@code blocked} one; none of them can turn a blocked evaluation into an allowed one. In the
 * current framework — with no approval-granting mechanism and no action executor — the final gate
 * ({@link #approvalGate}) unconditionally blocks, so {@link #evaluate} never actually returns
 * {@code allowed=true} today. This is intentional, not a defect: Step 5 establishes the permission
 * boundary only, and a conservative, always-blocked result is explicitly preferred until a future
 * step introduces real approval/execution infrastructure to plug into this same gate.
 *
 * ENVIRONMENT SAFETY reuses the existing, unmodified {@link RuntimeEnvironmentGuard}'s
 * environment-name check (and therefore its existing {@code ai.locator.runtime.allowed.environments}
 * configuration and its hardcoded, unconditional denial of "prod"/"production") rather than
 * duplicating that logic or introducing a second, competing environment allowlist. Deliberately
 * does NOT read {@code FailureContext.getEnvironment()} (which is a known, pre-existing,
 * previously-disclosed limitation — it actually holds the AI provider name, e.g. "gemini", not the
 * real runtime environment) — instead it reads the same {@code ConfigManager.getEnvironment()}
 * value {@code RuntimeLocatorValidator} already uses for exactly this purpose. Only the
 * environment-name check is reused; no live URL/host check is performed here (Step 5 has no
 * browser/URL access by design — see the class-level "NO BROWSER DEPENDENCY" rule), so this guard
 * is necessarily more conservative than Phase 6's full dual check, never less.
 */
public class AgentExecutionGuard {

    private static final Logger LOGGER = LogManager.getLogger(AgentExecutionGuard.class);

    private final AiConfig aiConfig;
    private final RuntimeEnvironmentGuard environmentGuard;

    public AgentExecutionGuard() {
        this(new AiConfig(), new RuntimeEnvironmentGuard());
    }

    public AgentExecutionGuard(AiConfig aiConfig, RuntimeEnvironmentGuard environmentGuard) {
        this.aiConfig = Objects.requireNonNull(aiConfig, "AiConfig must not be null");
        this.environmentGuard = Objects.requireNonNull(environmentGuard, "RuntimeEnvironmentGuard must not be null");
    }

    /**
     * Evaluates whether {@code decision}'s proposed action may ever be considered for execution.
     * Never throws: any null input, malformed value, or unexpected exception results in a
     * deterministic {@link AgentExecutionGuardResult} with {@code allowed=false}, never an escaped
     * exception that could let a caller assume permission by default.
     */
    public AgentExecutionGuardResult evaluate(AgentContext context, AgentDecision decision) {
        try {
            if (context == null) {
                return blocked(null, 0.0, List.of(), false, "No agent context supplied.");
            }
            if (decision == null) {
                return blocked(null, 0.0, List.of(), false, "No agent decision supplied.");
            }

            AgentAction action = decision.getAction();
            double confidence = decision.getConfidence();
            List<EvidenceItem> evidenceItems = decision.getEvidenceItems();
            boolean requiresApproval = decision.isRequiresApproval();

            if (action == null) {
                return blocked(null, confidence, evidenceItems, requiresApproval, "Decision carried no action.");
            }
            if (decision.getState() != AgentState.PROPOSE) {
                return blocked(action, confidence, evidenceItems, requiresApproval,
                        "Only a PROPOSE decision may be considered for execution; state was " + decision.getState() + ".");
            }
            if (action == AgentAction.NONE) {
                return blocked(action, confidence, evidenceItems, requiresApproval,
                        "No executable action was proposed.");
            }

            if (!aiConfig.isAiEnabled()) {
                return blocked(action, confidence, evidenceItems, requiresApproval,
                        "AI is disabled (ai.enabled=false); agent execution cannot be permitted.");
            }
            if (!aiConfig.isAgentExecutionEnabled()) {
                return blocked(action, confidence, evidenceItems, requiresApproval,
                        "Agent execution is disabled (ai.agent.execution.enabled=false).");
            }

            String environment;
            try {
                environment = ConfigManager.getInstance().getEnvironment();
            } catch (Exception e) {
                return blocked(action, confidence, evidenceItems, requiresApproval,
                        "Unable to determine the current environment: " + e.getMessage());
            }
            if (!environmentGuard.isEnvironmentAllowed(environment)) {
                return blocked(action, confidence, evidenceItems, requiresApproval,
                        "Environment '" + (environment == null ? "(none)" : environment)
                                + "' is not an allowed agent-execution environment.");
            }

            if (!aiConfig.isAgentBrowserMutationEnabled()) {
                return blocked(action, confidence, evidenceItems, requiresApproval,
                        "Browser-mutating agent actions are disabled (ai.agent.browser.mutation.enabled=false).");
            }

            if (evidenceItems == null || evidenceItems.isEmpty()) {
                return blocked(action, confidence, evidenceItems, requiresApproval,
                        "No evidence is available to support the proposed action.");
            }

            int maxActions = aiConfig.getAgentMaxActions();
            if (maxActions <= 0) {
                return blocked(action, confidence, evidenceItems, requiresApproval,
                        "The configured maximum number of agent actions is " + maxActions + ".");
            }

            // FINAL GATE: even when every prior check passes, Step 5 introduces no
            // approval-granting mechanism and no action executor. High confidence, VERIFIED
            // evidence, or requiresApproval=false do not — and never will, by this class's
            // design — substitute for an explicit grant that does not yet exist.
            return approvalGate(action, confidence, evidenceItems, requiresApproval);

        } catch (Exception e) {
            // Absolute boundary: the guard itself must never throw into the caller.
            LOGGER.warn("Unexpected error while evaluating agent execution permission: {}", e.getMessage());
            return blocked(null, 0.0, List.of(), false,
                    "Unexpected error while evaluating execution permission: " + e.getMessage());
        }
    }

    private AgentExecutionGuardResult approvalGate(AgentAction action, double confidence,
                                                     List<EvidenceItem> evidenceItems,
                                                     boolean requiresApproval) {
        String reason = requiresApproval
                ? "Explicit approval is required but no approval-granting mechanism exists yet."
                : "No action executor exists yet; Step 5 defines the permission boundary only.";
        return blocked(action, confidence, evidenceItems, requiresApproval, reason);
    }

    private AgentExecutionGuardResult blocked(AgentAction action, double confidence,
                                                List<EvidenceItem> evidenceItems,
                                                boolean requiresApproval, String reason) {
        return AgentExecutionGuardResult.builder()
                .allowed(false)
                .action(action)
                .confidence(confidence)
                .evidenceItems(evidenceItems)
                .requiresApproval(requiresApproval)
                .reason(reason)
                .build();
    }
}
