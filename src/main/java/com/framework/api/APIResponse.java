package com.framework.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

/**
 * Immutable wrapper around a Playwright API response, decoupled from the Playwright type
 * so test code and assertion helpers don't depend directly on com.microsoft.playwright.APIResponse.
 */
public final class APIResponse {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final int statusCode;
    private final Map<String, String> headers;
    private final String bodyAsString;
    private final long responseTimeMs;

    public APIResponse(int statusCode, Map<String, String> headers, String bodyAsString, long responseTimeMs) {
        this.statusCode = statusCode;
        this.headers = headers;
        this.bodyAsString = bodyAsString;
        this.responseTimeMs = responseTimeMs;
    }

    public int statusCode() {
        return statusCode;
    }

    public Map<String, String> headers() {
        return headers;
    }

    public String bodyAsString() {
        return bodyAsString;
    }

    public long responseTimeMs() {
        return responseTimeMs;
    }

    public JsonNode bodyAsJson() {
        try {
            return MAPPER.readTree(bodyAsString);
        } catch (Exception e) {
            throw new IllegalStateException("Response body is not valid JSON: " + bodyAsString, e);
        }
    }

    public <T> T bodyAs(Class<T> clazz) {
        try {
            return MAPPER.readValue(bodyAsString, clazz);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize response body into " + clazz.getSimpleName(), e);
        }
    }

    public String jsonPathValue(String fieldName) {
        JsonNode node = bodyAsJson().get(fieldName);
        return node == null ? null : node.asText();
    }
}
