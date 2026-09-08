package com.framework.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.options.RequestOptions;
import io.qameta.allure.Allure;
import io.qameta.allure.Step;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Fluent-ish API client built on Playwright's APIRequestContext.
 * Every call logs method/URL/headers/body/status/duration to Log4j2, and attaches
 * request + response payloads to the Allure report for traceability during release sign-off.
 */
public class APIClient {

    private static final Logger LOGGER = LogManager.getLogger(APIClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final APIRequestContext requestContext;
    private final Map<String, String> extraHeaders = new HashMap<>();

    public APIClient() {
        this.requestContext = APIClientManager.getContext();
    }

    public APIClient withHeader(String key, String value) {
        extraHeaders.put(key, value);
        return this;
    }

    @Step("GET {path}")
    public APIResponse get(String path) {
        return execute("GET", path, RequestOptions.create(), null);
    }

    @Step("GET {path} with query params")
    public APIResponse get(String path, Map<String, String> queryParams) {
        RequestOptions options = RequestOptions.create();
        queryParams.forEach(options::setQueryParam);
        return execute("GET", path, options, null);
    }

    @Step("POST {path}")
    public APIResponse post(String path, Object body) {
        return execute("POST", path, RequestOptions.create(), body);
    }

    @Step("PUT {path}")
    public APIResponse put(String path, Object body) {
        return execute("PUT", path, RequestOptions.create(), body);
    }

    @Step("PATCH {path}")
    public APIResponse patch(String path, Object body) {
        return execute("PATCH", path, RequestOptions.create(), body);
    }

    @Step("DELETE {path}")
    public APIResponse delete(String path) {
        return execute("DELETE", path, RequestOptions.create(), null);
    }

    private APIResponse execute(String method, String path, RequestOptions options, Object body) {
        extraHeaders.forEach(options::setHeader);

        String requestBodyJson = null;
        if (body != null) {
            requestBodyJson = toJson(body);
            options.setData(requestBodyJson);
        }

        logRequest(method, path, requestBodyJson);
        long start = System.currentTimeMillis();

        com.microsoft.playwright.APIResponse rawResponse = switch (method) {
            case "GET" -> requestContext.get(path, options);
            case "POST" -> requestContext.post(path, options);
            case "PUT" -> requestContext.put(path, options);
            case "PATCH" -> requestContext.patch(path, options);
            case "DELETE" -> requestContext.delete(path, options);
            default -> throw new IllegalArgumentException("Unsupported HTTP method: " + method);
        };

        long durationMs = System.currentTimeMillis() - start;
        String responseBody = rawResponse.text();

        APIResponse response = new APIResponse(
                rawResponse.status(),
                rawResponse.headers(),
                responseBody,
                durationMs);

        logResponse(method, path, response);
        extraHeaders.clear();
        return response;
    }

    private void logRequest(String method, String path, String body) {
        LOGGER.info("--> {} {} | body={}", method, path, body == null ? "-" : body);
        if (body != null) {
            Allure.addAttachment(method + " " + path + " - Request Body", "application/json", body);
        }
    }

    private void logResponse(String method, String path, APIResponse response) {
        LOGGER.info("<-- {} {} | status={} | durationMs={} | body={}",
                method, path, response.statusCode(), response.responseTimeMs(), response.bodyAsString());
        Allure.addAttachment(
                method + " " + path + " - Response (" + response.statusCode() + ")",
                "application/json",
                new ByteArrayInputStream(response.bodyAsString().getBytes()),
                "json");
    }

    private String toJson(Object body) {
        if (body instanceof String s) {
            return s;
        }
        try {
            return MAPPER.writeValueAsString(body);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to serialize request body to JSON", e);
        }
    }
}
