package com.framework.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.framework.ai.config.AiConfig;
import com.framework.ai.model.AiRequest;
import com.framework.ai.model.AiResponse;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Gemini REST API implementation of AiClient using java.net.http.HttpClient.
 *
 * Requirements:
 * - Zero new dependencies (uses standard library HttpClient + existing Jackson).
 * - API key obtained ONLY from AiConfig/ConfigManager/env var; never hardcoded, never logged.
 * - HTTP timeouts controlled by ai.timeout.seconds.
 * - Failures (4xx, 5xx, timeouts, malformed JSON) return safe AiResponse failure objects
 *   and NEVER throw unhandled exceptions.
 */
public class GeminiApiClient implements AiClient {

    private static final Logger LOGGER = LogManager.getLogger(GeminiApiClient.class);
    private static final String DEFAULT_GEMINI_MODEL = "gemini-2.5-flash";
    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";

    private final AiConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public GeminiApiClient() {
        this(new AiConfig(), HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build());
    }

    public GeminiApiClient(AiConfig config) {
        this(config, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build());
    }

    public GeminiApiClient(AiConfig config, HttpClient httpClient) {
        this.config = Objects.requireNonNull(config, "AiConfig must not be null");
        this.httpClient = Objects.requireNonNull(httpClient, "HttpClient must not be null");
        this.mapper = new ObjectMapper();
    }

    @Override
    public String getProviderName() {
        return "gemini";
    }

    @Override
    public boolean isAvailable() {
        if (!config.isAiEnabled()) {
            return false;
        }
        String key = config.getApiKey();
        return key != null && !key.isBlank();
    }

    @Override
    public AiResponse generate(AiRequest request) {
        if (!isAvailable()) {
            return AiResponse.failure("Gemini API client is unavailable or AI is disabled");
        }

        String model = (request.getModel() != null && !request.getModel().isBlank())
                ? request.getModel()
                : (config.getModel() != null && !config.getModel().isBlank()
                        ? config.getModel()
                        : DEFAULT_GEMINI_MODEL);

        String apiKey = config.getApiKey();
        int timeoutSeconds = config.getTimeoutSeconds() > 0 ? config.getTimeoutSeconds() : 20;

        String endpoint = BASE_URL + model + ":generateContent?key=" + apiKey;

        try {
            String requestPayload = buildRequestBody(request);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .POST(HttpRequest.BodyPublishers.ofString(requestPayload, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int statusCode = httpResponse.statusCode();

            if (statusCode >= 200 && statusCode < 300) {
                return parseSuccessResponse(httpResponse.body(), model);
            } else {
                LOGGER.warn("Gemini API returned non-success HTTP status: {}", statusCode);
                return AiResponse.builder()
                        .success(false)
                        .model(model)
                        .errorMessage("Gemini API returned HTTP " + statusCode)
                        .addMetadata("statusCode", statusCode)
                        .build();
            }
        } catch (java.net.http.HttpTimeoutException e) {
            LOGGER.warn("Gemini API call timed out after {}s", timeoutSeconds);
            return AiResponse.failure("Gemini API call timed out after " + timeoutSeconds + " seconds");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Gemini API request interrupted: {}", e.getMessage());
            return AiResponse.failure("Gemini API request interrupted");
        } catch (Exception e) {
            LOGGER.warn("Unexpected error calling Gemini API: {}", e.getMessage());
            return AiResponse.failure("Gemini API error: " + e.getMessage());
        }
    }

    private String buildRequestBody(AiRequest request) throws Exception {
        ObjectNode root = mapper.createObjectNode();

        // System instruction if present
        if (request.getSystemInstruction() != null && !request.getSystemInstruction().isBlank()) {
            ObjectNode sysNode = root.putObject("systemInstruction");
            ArrayNode sysParts = sysNode.putArray("parts");
            sysParts.addObject().put("text", request.getSystemInstruction());
        }

        // Contents
        ArrayNode contents = root.putArray("contents");
        ObjectNode userContent = contents.addObject();
        userContent.put("role", "user");
        ArrayNode parts = userContent.putArray("parts");
        parts.addObject().put("text", request.getPrompt());

        // Generation config
        ObjectNode genConfig = root.putObject("generationConfig");
        if (request.getTemperature() != null) {
            genConfig.put("temperature", request.getTemperature());
        }
        if (request.getMaxTokens() != null) {
            genConfig.put("maxOutputTokens", request.getMaxTokens());
        }
        // Force JSON response
        genConfig.put("responseMimeType", "application/json");

        return mapper.writeValueAsString(root);
    }

    private AiResponse parseSuccessResponse(String responseBody, String model) {
        try {
            JsonNode root = mapper.readTree(responseBody);
            JsonNode candidates = root.path("candidates");
            if (!candidates.isArray() || candidates.isEmpty()) {
                return AiResponse.failure("Gemini response contains no candidates");
            }

            JsonNode firstCandidate = candidates.get(0);
            JsonNode textNode = firstCandidate.path("content").path("parts").get(0).path("text");
            String text = textNode.asText("");

            int promptTokens = root.path("usageMetadata").path("promptTokenCount").asInt(0);
            int completionTokens = root.path("usageMetadata").path("candidatesTokenCount").asInt(0);
            int totalTokens = root.path("usageMetadata").path("totalTokenCount").asInt(0);

            return AiResponse.builder()
                    .success(true)
                    .content(text)
                    .model(model)
                    .promptTokens(promptTokens)
                    .completionTokens(completionTokens)
                    .totalTokens(totalTokens)
                    .build();
        } catch (Exception e) {
            LOGGER.warn("Failed to parse Gemini API JSON response: {}", e.getMessage());
            return AiResponse.failure("Malformed Gemini JSON response: " + e.getMessage());
        }
    }
}
