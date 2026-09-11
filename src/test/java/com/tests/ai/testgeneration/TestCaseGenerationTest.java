package com.tests.ai.testgeneration;

import static org.assertj.core.api.Assertions.assertThat;

import com.framework.ai.client.AiClient;
import com.framework.ai.config.AiConfig;
import com.framework.ai.model.AiRequest;
import com.framework.ai.model.AiResponse;
import com.framework.ai.testgeneration.GeneratedTestCase;
import com.framework.ai.testgeneration.RequirementInput;
import com.framework.ai.testgeneration.TestCaseGenerationPrompt;
import com.framework.ai.testgeneration.TestCaseGenerationReporter;
import com.framework.ai.testgeneration.TestCaseGenerationResponse;
import com.framework.ai.testgeneration.TestCasePriority;
import com.framework.ai.testgeneration.TestCaseType;
import com.framework.ai.testgeneration.TestCaseGenerator;
import com.framework.ai.testgeneration.TestDataSuggestion;
import com.framework.config.ConfigManager;
import java.util.List;
import org.testng.annotations.Test;

public class TestCaseGenerationTest {

    private AiConfig createConfig(boolean enabled) {
        return new AiConfig(ConfigManager.getInstance()) {
            @Override public boolean isAiEnabled() { return enabled; }
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

    // 1. RequirementInput tests
    @Test
    public void testRequirementInputBuilder() {
        RequirementInput input = RequirementInput.builder()
                .requirementId("REQ-101")
                .title("Cart Checkout")
                .content("User can increase item count in cart")
                .module("Cart")
                .addMetadata("author", "SDET")
                .build();

        assertThat(input.getRequirementId()).isEqualTo("REQ-101");
        assertThat(input.getTitle()).isEqualTo("Cart Checkout");
        assertThat(input.getContent()).isEqualTo("User can increase item count in cart");
        assertThat(input.getModule()).isEqualTo("Cart");
        assertThat(input.getMetadata().get("author")).isEqualTo("SDET");
    }

    // 2. Prompt engineering tests
    @Test
    public void testPromptContainsInstructionsAndGuidelines() {
        RequirementInput input = RequirementInput.builder()
                .requirementId("AC-55")
                .title("Location Drawer")
                .content("Modal should close when user clicks outside")
                .module("Homepage")
                .build();

        String prompt = TestCaseGenerationPrompt.buildPrompt(input);
        assertThat(prompt).contains("AC-55");
        assertThat(prompt).contains("Location Drawer");
        assertThat(prompt).contains("Modal should close when user clicks outside");
        assertThat(prompt).contains("testCases");
        assertThat(prompt).contains("missingRequirements");
        assertThat(prompt).contains("assumptions");
        assertThat(TestCaseGenerationPrompt.SYSTEM_INSTRUCTION).contains("Lead QA Automation Architect");
        assertThat(TestCaseGenerationPrompt.SYSTEM_INSTRUCTION).contains("DO NOT INVENT REQUIREMENTS");
    }

    // 3. Sanitization test on requirement input
    @Test
    public void testRequirementInputSanitization() {
        RequirementInput input = RequirementInput.builder()
                .requirementId("REQ-AUTH")
                .title("Login flow with password=TopSecretPass123!")
                .content("Send Authorization: Bearer eyJhbGciOiJIUzI1NiInR5cCI6IkpXVCJ9")
                .build();

        RequirementInput sanitized = input.sanitize();
        assertThat(sanitized.getTitle()).doesNotContain("TopSecretPass123!");
        assertThat(sanitized.getTitle()).contains("password=[REDACTED]");
        assertThat(sanitized.getContent()).doesNotContain("eyJhbGciOiJIUzI1NiInR5cCI6IkpXVCJ9");
        assertThat(sanitized.getContent()).contains("Authorization: [REDACTED]");
    }

    // 4. JSON parsing and successful generation
    @Test
    public void testSuccessfulTestCaseGeneration() {
        String jsonPayload = "{\n"
                + "  \"requirementId\": \"REQ-01\",\n"
                + "  \"module\": \"Cart\",\n"
                + "  \"testCases\": [\n"
                + "    {\n"
                + "      \"testCaseId\": \"TC-001\",\n"
                + "      \"module\": \"Cart\",\n"
                + "      \"scenario\": \"Increase cart item count\",\n"
                + "      \"description\": \"Verify user can increase item count up to max limit\",\n"
                + "      \"preconditions\": [\"Cart has 1 item\"],\n"
                + "      \"testData\": [{\"field\": \"count\", \"suggestedValue\": \"2\", \"description\": \"Valid increment\"}],\n"
                + "      \"steps\": [\"Click + icon\", \"Verify count updates\"],\n"
                + "      \"expectedResult\": \"Cart counter displays 2\",\n"
                + "      \"priority\": \"P0\",\n"
                + "      \"testType\": \"FUNCTIONAL\",\n"
                + "      \"tags\": [\"cart\", \"smoke\"]\n"
                + "    }\n"
                + "  ],\n"
                + "  \"missingRequirements\": [\"What is maximum item limit?\"],\n"
                + "  \"assumptions\": [\"Standard delivery rules apply\"]\n"
                + "}";

        MockAiClient client = new MockAiClient(AiResponse.success(jsonPayload, "gemini-2.5-flash"), true);
        TestCaseGenerator generator = new TestCaseGenerator(createConfig(true), client);

        RequirementInput input = RequirementInput.of("REQ-01", "Increment item count in cart");
        TestCaseGenerationResponse response = generator.generateTestCases(input);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getTestCases()).hasSize(1);

        GeneratedTestCase tc = response.getTestCases().get(0);
        assertThat(tc.getTestCaseId()).isEqualTo("TC-001");
        assertThat(tc.getScenario()).isEqualTo("Increase cart item count");
        assertThat(tc.getPriority()).isEqualTo(TestCasePriority.P0);
        assertThat(tc.getTestType()).isEqualTo(TestCaseType.FUNCTIONAL);
        assertThat(tc.getTestData()).hasSize(1);
        assertThat(tc.getTestData().get(0).getSuggestedValue()).isEqualTo("2");
        assertThat(response.getMissingRequirements()).contains("What is maximum item limit?");
    }

    // 5. Deduplication and automatic ID resolution
    @Test
    public void testDeduplicationAndIdResolution() {
        String jsonWithDuplicates = "{\n"
                + "  \"testCases\": [\n"
                + "    {\n"
                + "      \"testCaseId\": \"\",\n"
                + "      \"module\": \"Checkout\",\n"
                + "      \"scenario\": \"Apply valid coupon\",\n"
                + "      \"priority\": \"P0\"\n"
                + "    },\n"
                + "    {\n"
                + "      \"testCaseId\": \"TC-SAME\",\n"
                + "      \"module\": \"Checkout\",\n"
                + "      \"scenario\": \"Apply valid coupon\",\n"
                + "      \"priority\": \"P1\"\n"
                + "    },\n"
                + "    {\n"
                + "      \"testCaseId\": \"TC-SAME\",\n"
                + "      \"module\": \"Checkout\",\n"
                + "      \"scenario\": \"Apply expired coupon\",\n"
                + "      \"priority\": \"P2\"\n"
                + "    }\n"
                + "  ]\n"
                + "}";

        MockAiClient client = new MockAiClient(AiResponse.success(jsonWithDuplicates, "mock"), true);
        TestCaseGenerator generator = new TestCaseGenerator(createConfig(true), client);

        TestCaseGenerationResponse response = generator.generateTestCases(RequirementInput.of("Coupons"));

        assertThat(response.isSuccess()).isTrue();
        // Duplicate scenario should be dropped:
        assertThat(response.getTestCases()).hasSize(2);

        GeneratedTestCase tc1 = response.getTestCases().get(0);
        assertThat(tc1.getTestCaseId()).isEqualTo("AI-TC-001"); // Auto-assigned because original was blank

        GeneratedTestCase tc2 = response.getTestCases().get(1);
        // Duplicate ID 'TC-SAME' should be reassigned to avoid collisions:
        assertThat(tc2.getTestCaseId()).isEqualTo("AI-TC-002");
    }

    // 6. Invalid priority and type fallback safely to UNKNOWN
    @Test
    public void testInvalidPriorityAndTypeSafeFallback() {
        TestCasePriority p = TestCasePriority.fromString("NON_EXISTENT");
        assertThat(p).isEqualTo(TestCasePriority.UNKNOWN);

        TestCaseType t = TestCaseType.fromString("SUPER_CUSTOM_TEST");
        assertThat(t).isEqualTo(TestCaseType.UNKNOWN);
    }

    // 7. Malformed AI response returns safe failure
    @Test
    public void testMalformedAiResponseFailsSafely() {
        MockAiClient client = new MockAiClient(AiResponse.success("this is not JSON at all", "mock"), true);
        TestCaseGenerator generator = new TestCaseGenerator(createConfig(true), client);

        TestCaseGenerationResponse response = generator.generateTestCases(RequirementInput.of("Requirements"));
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorMessage()).contains("Failed to parse");
    }

    // 8. AI disabled returns safe failure without making network call
    @Test
    public void testAiDisabledReturnsControlledFailure() {
        MockAiClient client = new MockAiClient(AiResponse.success("{}", "mock"), true);
        TestCaseGenerator generator = new TestCaseGenerator(createConfig(false), client);

        TestCaseGenerationResponse response = generator.generateTestCases(RequirementInput.of("Requirements"));
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorMessage()).contains("AI is disabled");
    }

    // 9. Empty or null requirement content fails early
    @Test
    public void testEmptyRequirementFailsEarly() {
        TestCaseGenerator generator = new TestCaseGenerator(createConfig(true), new MockAiClient(null, true));

        TestCaseGenerationResponse response1 = generator.generateTestCases(null);
        assertThat(response1.isSuccess()).isFalse();

        TestCaseGenerationResponse response2 = generator.generateTestCases(RequirementInput.of("   "));
        assertThat(response2.isSuccess()).isFalse();
    }

    // 10. Markdown reporter formatting
    @Test
    public void testReporterGeneratesMarkdown() {
        GeneratedTestCase tc = GeneratedTestCase.builder()
                .testCaseId("TC-DEMO")
                .module("Search")
                .scenario("Search with special characters")
                .description("Verifies query sanitizer handles apostrophe")
                .priority(TestCasePriority.P2)
                .testType(TestCaseType.NEGATIVE)
                .expectedResult("Search returns no crash and friendly empty state")
                .addPrecondition("Search input is visible")
                .addStep("Type query")
                .addStep("Click search")
                .addTestData(new TestDataSuggestion("query", "sofa's", "Escaped punctuation"))
                .build();

        TestCaseGenerationResponse response = TestCaseGenerationResponse.builder()
                .requirementId("REQ-SEARCH")
                .module("Search")
                .addTestCase(tc)
                .addMissingRequirement("Special character rate-limiting unmentioned")
                .build();

        String md = TestCaseGenerationReporter.toMarkdown(response);

        assertThat(md).contains("# AI Test Case Generation Report");
        assertThat(md).contains("TC-DEMO");
        assertThat(md).contains("Search with special characters");
        assertThat(md).contains("Escaped punctuation");
        assertThat(md).contains("Special character rate-limiting unmentioned");
        assertThat(md).contains("Human Review Required before converting to automated test scripts: YES");
    }
}