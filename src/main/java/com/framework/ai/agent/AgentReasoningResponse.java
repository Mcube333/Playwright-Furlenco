package com.framework.ai.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Phase 8 Step 3: a strictly-validated parse of raw AI reasoning output.
 *
 * The AI response is UNTRUSTED INPUT. {@link #parse(String)} is the single place that decides
 * whether a raw AI response is trustworthy enough to become a real proposal at all — it returns
 * {@code null} for anything it cannot fully validate (malformed JSON, blank content, an
 * unrecognized {@code state}/{@code action} string, or a state/action combination that does not
 * make sense as a terminal decision), rather than manufacturing a partially-valid result. This
 * mirrors the existing {@code FailureAnalysisService.parseAndValidateResponse}/
 * {@code LocatorAnalysisService.parseAndValidate} convention of "return null on any doubt" — the
 * caller ({@link AgentReasoningService}) is solely responsible for turning a {@code null} parse
 * result into a safe {@code BLOCKED}/{@code NONE} {@link AgentDecision}.
 *
 * Only {@link AgentState#PROPOSE} or {@link AgentState#BLOCKED} are accepted as a terminal
 * decision state here — {@code OBSERVE}/{@code ANALYZE} are process states, not something the AI
 * should ever hand back as its final answer, so a response claiming either is rejected exactly
 * like an unrecognized string would be. Only the six existing {@link AgentAction} values are ever
 * recognized; anything else (including free text like {@code "EXECUTE_JAVASCRIPT"} or
 * {@code "CLICK"}) fails validation. {@link AgentState#BLOCKED} paired with any action other than
 * {@link AgentAction#NONE} also fails validation, rather than being silently corrected — an
 * invalid response is treated as fully untrustworthy, not partially salvageable.
 *
 * {@code requiresApproval} is parsed here for transparency/testability only. {@link AgentReasoningService}
 * never trusts this field when building the final {@link AgentDecision} — it always forces
 * {@code requiresApproval=true} itself, so an AI response can never "self-approve" a decision.
 */
public final class AgentReasoningResponse {

    private static final Logger LOGGER = LogManager.getLogger(AgentReasoningResponse.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AgentState state;
    private final AgentAction action;
    private final String reason;
    private final String rationale;
    private final double confidence;
    private final boolean requiresApproval;

    private AgentReasoningResponse(AgentState state, AgentAction action, String reason, String rationale,
                                    double confidence, boolean requiresApproval) {
        this.state = state;
        this.action = action;
        this.reason = reason;
        this.rationale = rationale;
        this.confidence = confidence;
        this.requiresApproval = requiresApproval;
    }

    public AgentState getState() {
        return state;
    }

    public AgentAction getAction() {
        return action;
    }

    public String getReason() {
        return reason;
    }

    public String getRationale() {
        return rationale;
    }

    public double getConfidence() {
        return confidence;
    }

    public boolean isRequiresApproval() {
        return requiresApproval;
    }

    /**
     * Parses and strictly validates raw AI content. Returns {@code null} for any malformed,
     * incomplete, or untrustworthy response — never throws, never guesses.
     */
    public static AgentReasoningResponse parse(String rawContent) {
        if (rawContent == null || rawContent.isBlank()) {
            LOGGER.warn("Agent reasoning AI returned empty content");
            return null;
        }

        JsonNode node;
        try {
            node = MAPPER.readTree(cleanJsonContent(rawContent));
        } catch (Exception e) {
            LOGGER.warn("Failed to parse agent reasoning AI response as JSON: {}", e.getMessage());
            return null;
        }

        AgentState state = safeState(node.path("state").asText(null));
        if (state == null || (state != AgentState.PROPOSE && state != AgentState.BLOCKED)) {
            LOGGER.warn("Agent reasoning AI response carried an unrecognized or non-terminal state: {}",
                    node.path("state").asText(""));
            return null;
        }

        AgentAction action = safeAction(node.path("action").asText(null));
        if (action == null) {
            LOGGER.warn("Agent reasoning AI response carried an unrecognized action: {}",
                    node.path("action").asText(""));
            return null;
        }

        if (state == AgentState.BLOCKED && action != AgentAction.NONE) {
            LOGGER.warn("Agent reasoning AI response combined BLOCKED with a non-NONE action ({}); rejecting", action);
            return null;
        }

        String reason = node.path("reason").asText("");
        String rationale = node.path("rationale").asText("");
        double confidence = node.path("confidence").asDouble(0.0);
        boolean requiresApproval = node.path("requiresApproval").asBoolean(true);

        return new AgentReasoningResponse(state, action, reason, rationale, confidence, requiresApproval);
    }

    private static AgentState safeState(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return AgentState.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static AgentAction safeAction(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return AgentAction.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String cleanJsonContent(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.trim();
    }
}
