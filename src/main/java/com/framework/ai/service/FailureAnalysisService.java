package com.framework.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.framework.ai.client.AiClient;
import com.framework.ai.client.GeminiApiClient;
import com.framework.ai.config.AiConfig;
import com.framework.ai.model.AiAnalysisResponse;
import com.framework.ai.model.AiRequest;
import com.framework.ai.model.AiResponse;
import com.framework.ai.model.FailureCategory;
import com.framework.ai.model.FailureContext;
import com.framework.ai.prompt.FailureAnalysisPrompt;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * High-level orchestration service for AI failure analysis.
 *
 * Coordinates:
 * 1. Building the failure analysis prompt (including deterministic sanitization).
 * 2. Calling the configured AI provider (default: GeminiApiClient).
 * 3. Validating and parsing the structured JSON output into AiAnalysisResponse.
 * 4. Ensuring that any AI failure, timeout, or parsing error returns a safe fallback
 *    and NEVER throws an unhandled exception or corrupts the test.
 */
public class FailureAnalysisService {

    private static final Logger LOGGER = LogManager.getLogger(FailureAnalysisService.class);

    private final AiConfig config;
    private final AiClient aiClient;
    private final ObjectMapper mapper;

    public FailureAnalysisService() {
        this(new AiConfig(), new GeminiApiClient());
    }

    public FailureAnalysisService(AiConfig config, AiClient aiClient) {
        this.config = Objects.requireNonNull(config, "AiConfig must not be null");
        this.aiClient = Objects.requireNonNull(aiClient, "AiClient must not be null");
        this.mapper = new ObjectMapper();
    }

    /**
     * Checks whether AI failure analysis is enabled and ready to run.
     */
    public boolean isAnalysisEligible() {
        return config.isAiEnabled() && config.isFailureAnalysisEnabled() && aiClient.isAvailable();
    }

    /**
     * Executes AI failure analysis for the provided FailureContext.
     *
     * @param context the diagnostic failure context
     * @return an AiAnalysisResponse if analysis succeeded, or null if analysis was disabled/failed
     */
    public AiAnalysisResponse analyze(FailureContext context) {
        if (!isAnalysisEligible()) {
            LOGGER.debug("AI failure analysis is not eligible (ai.enabled={}, ai.failure.analysis.enabled={}, available={})",
                    config.isAiEnabled(), config.isFailureAnalysisEnabled(), aiClient.isAvailable());
            return null;
        }

        if (context == null) {
            LOGGER.warn("Cannot analyze null FailureContext");
            return null;
        }

        try {
            LOGGER.info("Starting AI failure analysis for test: {}", context.getTestName());

            String prompt = FailureAnalysisPrompt.buildPrompt(context);

            AiRequest request = AiRequest.builder()
                    .systemInstruction(FailureAnalysisPrompt.SYSTEM_INSTRUCTION)
                    .prompt(prompt)
                    .temperature(0.2)
                    .maxTokens(2048)
                    .build();

            AiResponse response = aiClient.generate(request);

            if (!response.isSuccess()) {
                LOGGER.warn("AI generation failed for test [{}]: {}", context.getTestName(), response.getErrorMessage());
                return null;
            }

            return parseAndValidateResponse(response.getContent());
        } catch (Exception e) {
            // Absolute boundary: never leak an exception out of analysis
            LOGGER.warn("Unexpected error during AI failure analysis: {}", e.getMessage());
            return null;
        }
    }

    private AiAnalysisResponse parseAndValidateResponse(String rawContent) {
        if (rawContent == null || rawContent.isBlank()) {
            LOGGER.warn("AI returned empty content");
            return null;
        }

        try {
            // In case the model wrapped the JSON in markdown fences:
            String cleaned = cleanJsonContent(rawContent);

            JsonNode node = mapper.readTree(cleaned);

            String summary = node.path("summary").asText("");
            String rootCause = node.path("rootCause").asText("");
            String catStr = node.path("category").asText("UNKNOWN");
            FailureCategory category = FailureCategory.fromString(catStr);
            String suggestedFix = node.path("suggestedFix").asText("");
            String jiraBugReport = node.path("jiraBugReport").asText("");
            double confidence = node.path("confidenceScore").asDouble(0.0);

            List<String> locators = new ArrayList<>();
            JsonNode locatorsNode = node.path("suggestedLocators");
            if (locatorsNode.isArray()) {
                for (JsonNode loc : locatorsNode) {
                    locators.add(loc.asText());
                }
            }

            return AiAnalysisResponse.builder()
                    .summary(summary)
                    .rootCause(rootCause)
                    .category(category)
                    .suggestedFix(suggestedFix)
                    .suggestedLocators(locators)
                    .jiraBugReport(jiraBugReport)
                    .confidenceScore(confidence)
                    .addMetadata("rawLength", rawContent.length())
                    .build();
        } catch (Exception e) {
            LOGGER.warn("Failed to parse AI JSON into AiAnalysisResponse: {}", e.getMessage());
            return null;
        }
    }

    private String cleanJsonContent(String content) {
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
