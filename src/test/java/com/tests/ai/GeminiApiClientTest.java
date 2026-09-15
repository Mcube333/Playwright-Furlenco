package com.tests.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.framework.ai.client.GeminiApiClient;
import com.framework.ai.config.AiConfig;
import com.framework.ai.model.AiRequest;
import com.framework.ai.model.AiResponse;
import com.framework.config.ConfigManager;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import org.testng.annotations.Test;

public class GeminiApiClientTest {

    private static class StubHttpResponse implements HttpResponse<String> {
        private final int statusCode;
        private final String body;

        public StubHttpResponse(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }

        @Override public int statusCode() { return statusCode; }
        @Override public String body() { return body; }
        @Override public HttpRequest request() { return null; }
        @Override public java.util.Optional<HttpResponse<String>> previousResponse() { return java.util.Optional.empty(); }
        @Override public java.net.http.HttpHeaders headers() { return java.net.http.HttpHeaders.of(java.util.Map.of(), (a, b) -> true); }
        @Override public java.net.URI uri() { return java.net.URI.create("https://generativelanguage.googleapis.com"); }
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
        @Override public java.util.Optional<javax.net.ssl.SSLSession> sslSession() { return java.util.Optional.empty(); }
    }

    private static class MockHttpClient extends HttpClient {
        private final HttpResponse<String> stubResponse;
        private final Exception throwException;

        public MockHttpClient(HttpResponse<String> stubResponse) {
            this.stubResponse = stubResponse;
            this.throwException = null;
        }

        public MockHttpClient(Exception throwException) {
            this.stubResponse = null;
            this.throwException = throwException;
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) throws IOException, InterruptedException {
            if (throwException != null) {
                if (throwException instanceof IOException) throw (IOException) throwException;
                if (throwException instanceof InterruptedException) throw (InterruptedException) throwException;
                throw new RuntimeException(throwException);
            }
            @SuppressWarnings("unchecked")
            HttpResponse<T> resp = (HttpResponse<T>) stubResponse;
            return resp;
        }

        @Override public java.util.Optional<java.net.CookieHandler> cookieHandler() { return java.util.Optional.empty(); }
        @Override public java.util.Optional<java.time.Duration> connectTimeout() { return java.util.Optional.empty(); }
        @Override public HttpClient.Redirect followRedirects() { return HttpClient.Redirect.NEVER; }
        @Override public java.util.Optional<java.net.ProxySelector> proxy() { return java.util.Optional.empty(); }
        @Override public javax.net.ssl.SSLContext sslContext() { return null; }
        @Override public javax.net.ssl.SSLParameters sslParameters() { return null; }
        @Override public java.util.Optional<java.net.Authenticator> authenticator() { return java.util.Optional.empty(); }
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
        @Override public java.util.Optional<java.util.concurrent.Executor> executor() { return java.util.Optional.empty(); }
        @Override public <T> java.util.concurrent.CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) { return null; }
        @Override public <T> java.util.concurrent.CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler, HttpResponse.PushPromiseHandler<T> pushPromiseHandler) { return null; }
    }

    private AiConfig createMockConfig(boolean enabled, String apiKey) {
        return new AiConfig(ConfigManager.getInstance()) {
            @Override public boolean isAiEnabled() { return enabled; }
            @Override public String getApiKey() { return apiKey; }
            @Override public String getProvider() { return "gemini"; }
            @Override public String getModel() { return "gemini-2.5-flash"; }
            @Override public int getTimeoutSeconds() { return 5; }
        };
    }

    @Test
    public void testSuccessfulResponse() {
        String jsonSuccess = "{\n"
                + "  \"candidates\": [{\n"
                + "    \"content\": {\n"
                + "      \"parts\": [{\"text\": \"{\\\"summary\\\":\\\"Button not found\\\",\\\"category\\\":\\\"LOCATOR_CHANGED\\\"}\"}]\n"
                + "    }\n"
                + "  }],\n"
                + "  \"usageMetadata\": {\n"
                + "    \"promptTokenCount\": 120,\n"
                + "    \"candidatesTokenCount\": 45,\n"
                + "    \"totalTokenCount\": 165\n"
                + "  }\n"
                + "}";

        MockHttpClient client = new MockHttpClient(new StubHttpResponse(200, jsonSuccess));
        GeminiApiClient gemini = new GeminiApiClient(createMockConfig(true, "mock-fake-key"), client);

        AiRequest request = AiRequest.builder().prompt("Analyze this test failure").build();
        AiResponse response = gemini.generate(request);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getContent()).contains("Button not found");
        assertThat(response.getPromptTokens()).isEqualTo(120);
        assertThat(response.getCompletionTokens()).isEqualTo(45);
        assertThat(response.getTotalTokens()).isEqualTo(165);
    }

    @Test
    public void testHttp400BadRequest() {
        MockHttpClient client = new MockHttpClient(new StubHttpResponse(400, "{\"error\": \"Bad Request\"}"));
        GeminiApiClient gemini = new GeminiApiClient(createMockConfig(true, "mock-fake-key"), client);

        AiResponse response = gemini.generate(AiRequest.builder().prompt("Prompt").build());
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorMessage()).contains("HTTP 400");
    }

    @Test
    public void testHttp401Unauthorized() {
        MockHttpClient client = new MockHttpClient(new StubHttpResponse(401, "{\"error\": \"API key invalid\"}"));
        GeminiApiClient gemini = new GeminiApiClient(createMockConfig(true, "mock-fake-key"), client);

        AiResponse response = gemini.generate(AiRequest.builder().prompt("Prompt").build());
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorMessage()).contains("HTTP 401");
    }

    @Test
    public void testHttp429RateLimit() {
        MockHttpClient client = new MockHttpClient(new StubHttpResponse(429, "{\"error\": \"Resource exhausted\"}"));
        GeminiApiClient gemini = new GeminiApiClient(createMockConfig(true, "mock-fake-key"), client);

        AiResponse response = gemini.generate(AiRequest.builder().prompt("Prompt").build());
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorMessage()).contains("HTTP 429");
    }

    @Test
    public void testHttp500ServerError() {
        MockHttpClient client = new MockHttpClient(new StubHttpResponse(500, "{\"error\": \"Internal server error\"}"));
        GeminiApiClient gemini = new GeminiApiClient(createMockConfig(true, "mock-fake-key"), client);

        AiResponse response = gemini.generate(AiRequest.builder().prompt("Prompt").build());
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorMessage()).contains("HTTP 500");
    }

    @Test
    public void testTimeoutHandling() {
        MockHttpClient client = new MockHttpClient(new HttpTimeoutException("Request timed out"));
        GeminiApiClient gemini = new GeminiApiClient(createMockConfig(true, "mock-fake-key"), client);

        AiResponse response = gemini.generate(AiRequest.builder().prompt("Prompt").build());
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorMessage()).contains("timed out");
    }

    @Test
    public void testMalformedJsonResponse() {
        MockHttpClient client = new MockHttpClient(new StubHttpResponse(200, "{malformed-json..."));
        GeminiApiClient gemini = new GeminiApiClient(createMockConfig(true, "mock-fake-key"), client);

        AiResponse response = gemini.generate(AiRequest.builder().prompt("Prompt").build());
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorMessage()).contains("Malformed");
    }

    @Test
    public void testAiDisabledReturnsUnavailable() {
        MockHttpClient client = new MockHttpClient(new StubHttpResponse(200, "{}"));
        GeminiApiClient gemini = new GeminiApiClient(createMockConfig(false, ""), client);

        assertThat(gemini.isAvailable()).isFalse();
        AiResponse response = gemini.generate(AiRequest.builder().prompt("Prompt").build());
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorMessage()).contains("unavailable");
    }
}