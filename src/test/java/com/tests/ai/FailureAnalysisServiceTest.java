package com.tests.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.framework.ai.client.AiClient;
import com.framework.ai.config.AiConfig;
import com.framework.ai.model.AiAnalysisResponse;
import com.framework.ai.model.AiRequest;
import com.framework.ai.model.AiResponse;
import com.framework.ai.model.FailureCategory;
import com.framework.ai.model.FailureContext;
import com.framework.ai.service.FailureAnalysisService;
import com.framework.config.ConfigManager;
import org.testng.annotations.Test;

public class FailureAnalysisServiceTest {

    private AiConfig createConfig(boolean aiEnabled, boolean analysisEnabled) {
        return new AiConfig(ConfigManager.getInstance()) {
            @Override public boolean isAiEnabled() { return aiEnabled; }
            @Override public boolean isFailureAnalysisEnabled() { return analysisEnabled; }
            @Override public String getApiKey() { return "fake-api-key"; }
            @Override public String getProvider() { return "gemini"; }
        };
    }

    private static class MockAiClient implements AiClient {
        private final AiResponse stubResponse;
        private final boolean available;
        public String capturedPrompt;

        public MockAiClient(AiResponse stubResponse, boolean available) {
            this.stubResponse = stubResponse;
            this.available = available;
        }

        @Override
        public AiResponse generate(AiRequest request) {
            this.capturedPrompt = request.getPrompt();
            return stubResponse;
        }

        @Override public String getProviderName() { return "mock"; }
        @Override public boolean isAvailable() { return available; }
    }

    @Test
    public void testSuccessfulAnalysis() {
        String jsonPayload = "{\n"
                + "  \"summary\": \"Element not interactable\",\n"
                + "  \"rootCause\": \"Delivery modal backdrop intercepted pointer events\",\n"
                + "  \"category\": \"APPLICATION_BUG\",\n"
                + "  \"suggestedFix\": \"Add modal dismissal before clicking search\",\n"
                + "  \"suggestedLocators\": [\"[data-slot='location-selector'] svg\"],\n"
                + "  \"jiraBugReport\": \"Modal blocks checkout flow\",\n"
                + "  \"confidenceScore\": 0.92\n"
                + "}";

        MockAiClient client = new MockAiClient(AiResponse.success(jsonPayload, "gemini-2.5-flash"), true);
        FailureAnalysisService service = new FailureAnalysisService(createConfig(true, true), client);

        FailureContext context = FailureContext.builder()
                .testName("testCartFlow")
                .errorMessage("Click intercepted by backdrop")
                .build();

        AiAnalysisResponse analysis = service.analyze(context);

        assertThat(analysis).isNotNull();
        assertThat(analysis.getSummary()).isEqualTo("Element not interactable");
        assertThat(analysis.getCategory()).isEqualTo(FailureCategory.APPLICATION_BUG);
        assertThat(analysis.getConfidenceScore()).isEqualTo(0.92);
        assertThat(analysis.getSuggestedLocators()).contains("[data-slot='location-selector'] svg");
    }

    @Test
    public void testAiDisabledReturnsNull() {
        MockAiClient client = new MockAiClient(AiResponse.success("{}", "mock"), true);
        FailureAnalysisService service = new FailureAnalysisService(createConfig(false, true), client);

        FailureContext context = FailureContext.builder().testName("test").build();
        AiAnalysisResponse analysis = service.analyze(context);

        assertThat(analysis).isNull();
    }

    @Test
    public void testFailureAnalysisDisabledReturnsNull() {
        MockAiClient client = new MockAiClient(AiResponse.success("{}", "mock"), true);
        FailureAnalysisService service = new FailureAnalysisService(createConfig(true, false), client);

        FailureContext context = FailureContext.builder().testName("test").build();
        AiAnalysisResponse analysis = service.analyze(context);

        assertThat(analysis).isNull();
    }

    @Test
    public void testProviderUnavailableReturnsNull() {
        MockAiClient client = new MockAiClient(AiResponse.failure("No connection"), false);
        FailureAnalysisService service = new FailureAnalysisService(createConfig(true, true), client);

        FailureContext context = FailureContext.builder().testName("test").build();
        AiAnalysisResponse analysis = service.analyze(context);

        assertThat(analysis).isNull();
    }

    @Test
    public void testMalformedResponseReturnsNullWithoutException() {
        MockAiClient client = new MockAiClient(AiResponse.success("not a valid json object", "mock"), true);
        FailureAnalysisService service = new FailureAnalysisService(createConfig(true, true), client);

        FailureContext context = FailureContext.builder().testName("test").build();
        AiAnalysisResponse analysis = service.analyze(context);

        assertThat(analysis).isNull();
    }

    @Test
    public void testSanitizationHappensBeforeSendingToAiClient() {
        MockAiClient client = new MockAiClient(AiResponse.success("{}", "mock"), true);
        FailureAnalysisService service = new FailureAnalysisService(createConfig(true, true), client);

        FailureContext context = FailureContext.builder()
                .testName("testAuth")
                .errorMessage("Failed with secret password=TopSecret123 and Authorization: Bearer tokenXYZ")
                .build();

        service.analyze(context);

        assertThat(client.capturedPrompt).isNotNull();
        assertThat(client.capturedPrompt).doesNotContain("TopSecret123");
        assertThat(client.capturedPrompt).doesNotContain("tokenXYZ");
        assertThat(client.capturedPrompt).contains("[REDACTED]");
    }

    @Test
    public void testAiExceptionDoesNotPropagate() {
        AiClient throwingClient = new AiClient() {
            @Override public AiResponse generate(AiRequest request) { throw new RuntimeException("Simulated unexpected crash!"); }
            @Override public String getProviderName() { return "crash"; }
            @Override public boolean isAvailable() { return true; }
        };

        FailureAnalysisService service = new FailureAnalysisService(createConfig(true, true), throwingClient);
        FailureContext context = FailureContext.builder().testName("test").build();

        // Must not throw exception
        AiAnalysisResponse analysis = service.analyze(context);
        assertThat(analysis).isNull();
    }
}