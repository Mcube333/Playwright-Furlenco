package com.framework.ai.agent;

import com.framework.ai.client.AiClient;
import com.framework.ai.client.GeminiApiClient;
import com.framework.ai.codegeneration.EvidenceItem;
import com.framework.ai.config.AiConfig;
import com.framework.ai.diagnosis.FailureDiagnosis;
import com.framework.ai.diagnosis.SuggestedFix;
import com.framework.ai.model.AiRequest;
import com.framework.ai.model.AiResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Phase 8 Step 3: controlled agent reasoning orchestrator.
 *
 * <pre>
 * AgentContext -&gt; AgentObservation -&gt; AI reasoning -&gt; AgentReasoningResponse -&gt; AgentDecision -&gt; STOP
 * </pre>
 *
 * REASONING ONLY. This class never executes the {@link AgentAction} it proposes — there is no
 * {@code execute()}/{@code apply()}/{@code run()} method anywhere in this class or the models it
 * produces. It performs no Playwright call, no file/test/source modification, and no Git
 * operation. It is explicitly invoked only: nothing registers this class with {@code TestListener}
 * or any TestNG lifecycle hook, and nothing calls it automatically.
 *
 * Mirrors {@code FailureAnalysisService}'s existing orchestration shape exactly: an
 * {@link AiConfig} gate, a constructor-injected {@link AiClient}, an outer try/catch that turns any
 * unexpected exception into a safe fallback, and a defensive-only relationship with its AI
 * provider — the AI response is always treated as untrusted and independently re-validated by
 * {@link AgentReasoningResponse#parse(String)} before any part of it is trusted.
 *
 * DOES NOT rerun Phase 7 failure analysis: it consumes an already-produced {@link FailureDiagnosis}
 * from the supplied {@link AgentContext} and never calls {@code FailureAnalysisService},
 * {@code LocatorAnalysisService}, {@code RuntimeLocatorValidator}, or
 * {@code FailureDiagnosisService} itself.
 */
public class AgentReasoningService {

    private static final Logger LOGGER = LogManager.getLogger(AgentReasoningService.class);

    private final AiConfig aiConfig;
    private final AiClient aiClient;

    public AgentReasoningService() {
        this(new AiConfig(), new GeminiApiClient());
    }

    public AgentReasoningService(AiConfig aiConfig, AiClient aiClient) {
        this.aiConfig = Objects.requireNonNull(aiConfig, "AiConfig must not be null");
        this.aiClient = Objects.requireNonNull(aiClient, "AiClient must not be null");
    }

    /**
     * Builds an {@link AgentObservation} from the {@link FailureDiagnosis} already carried by
     * {@code context}, reasons about it via the existing AI client, and returns a structured
     * {@link AgentDecision}. Never throws, never executes anything, and never calls the AI
     * provider when {@code ai.enabled=false} — in every case where reasoning cannot be trusted
     * (AI disabled/unavailable, no response, malformed response, an exception), this returns a
     * deterministic {@code BLOCKED}/{@code NONE} decision rather than propagating the failure or
     * guessing.
     */
    public AgentDecision analyze(AgentContext context) {
        if (context == null) {
            LOGGER.debug("AgentReasoningService.analyze() called with a null AgentContext");
            return blocked("No agent context supplied.");
        }

        try {
            AgentObservation observation = buildObservation(context.getFailureDiagnosis());

            if (!aiConfig.isAiEnabled()) {
                LOGGER.debug("Agent reasoning skipped: AI is disabled (ai.enabled=false).");
                return blocked("AI is disabled; no agent reasoning was performed.");
            }
            if (!aiClient.isAvailable()) {
                LOGGER.debug("Agent reasoning skipped: AI client is unavailable or unconfigured.");
                return blocked("AI client is unavailable; no agent reasoning was performed.");
            }

            String prompt = AgentReasoningPrompt.buildPrompt(observation);
            AiRequest request = AiRequest.builder()
                    .systemInstruction(AgentReasoningPrompt.SYSTEM_INSTRUCTION)
                    .prompt(prompt)
                    .temperature(0.2)
                    .maxTokens(1024)
                    .build();

            AiResponse response;
            try {
                response = aiClient.generate(request);
            } catch (Exception e) {
                LOGGER.warn("Agent reasoning AI client threw an exception: {}", e.getMessage());
                return blocked("Agent reasoning AI provider failed: " + e.getMessage());
            }

            if (!response.isSuccess()) {
                LOGGER.warn("Agent reasoning AI generation failed: {}", response.getErrorMessage());
                return blocked("Agent reasoning AI generation failed.");
            }

            AgentReasoningResponse parsed = AgentReasoningResponse.parse(response.getContent());
            if (parsed == null) {
                LOGGER.warn("Agent reasoning AI response could not be safely validated; falling back to BLOCKED.");
                return blocked("Agent reasoning AI response was malformed or proposed an unsupported action/state.");
            }

            return toDecision(parsed, observation);

        } catch (Exception e) {
            // Absolute boundary: agent reasoning must never throw into the caller.
            LOGGER.warn("Unexpected error during agent reasoning: {}", e.getMessage());
            return blocked("Unexpected error during agent reasoning: " + e.getMessage());
        }
    }

    /**
     * Builds an {@link AgentObservation} from {@code diagnosis} by reusing exactly the
     * {@link EvidenceItem}s Phase 7 already attached to each {@link SuggestedFix} — no new
     * evidence is computed, fabricated, or upgraded here. Never null; an empty/absent diagnosis
     * yields an observation with no evidence items rather than an exception.
     */
    public AgentObservation buildObservation(FailureDiagnosis diagnosis) {
        AgentObservation.Builder builder = AgentObservation.builder().failureDiagnosis(diagnosis);
        if (diagnosis != null) {
            List<EvidenceItem> evidence = new ArrayList<>();
            for (SuggestedFix fix : diagnosis.getSuggestedFixes()) {
                evidence.addAll(fix.getEvidenceItems());
            }
            builder.evidenceItems(evidence);
        }
        return builder.build();
    }

    private AgentDecision toDecision(AgentReasoningResponse parsed, AgentObservation observation) {
        AgentDecision.Builder builder = AgentDecision.builder()
                .state(parsed.getState())
                .action(parsed.getAction())
                .reason(parsed.getReason())
                .rationale(parsed.getRationale())
                .confidence(parsed.getConfidence())
                .evidenceItems(observation.getEvidenceItems())
                // Deterministic safety in code, not reliant on the AI: a decision can never
                // approve itself. Whatever the raw AI response claimed for this field is ignored.
                .requiresApproval(true);
        return builder.build();
    }

    private AgentDecision blocked(String reason) {
        return AgentDecision.builder()
                .state(AgentState.BLOCKED)
                .action(AgentAction.NONE)
                .reason(reason)
                .requiresApproval(true)
                .build();
    }
}
