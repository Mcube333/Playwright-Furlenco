package com.framework.ai.testgeneration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.framework.ai.client.AiClient;
import com.framework.ai.client.GeminiApiClient;
import com.framework.ai.config.AiConfig;
import com.framework.ai.model.AiRequest;
import com.framework.ai.model.AiResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * On-demand AI Test Case & Test Data Generator.
 *
 * Converts requirements / acceptance criteria into structured QA test cases.
 * Offline capability: never triggered by normal automated test execution.
 * Fails safely and gracefully if AI is unavailable or produces invalid JSON.
 */
public class TestCaseGenerator {

    private static final Logger LOGGER = LogManager.getLogger(TestCaseGenerator.class);

    private final AiConfig config;
    private final AiClient aiClient;
    private final ObjectMapper mapper;

    public TestCaseGenerator() {
        this(new AiConfig(), new GeminiApiClient());
    }

    public TestCaseGenerator(AiConfig config, AiClient aiClient) {
        this.config = Objects.requireNonNull(config, "AiConfig must not be null");
        this.aiClient = Objects.requireNonNull(aiClient, "AiClient must not be null");
        this.mapper = new ObjectMapper();
    }

    /**
     * Checks whether AI generation can be executed.
     */
    public boolean isGenerationAvailable() {
        return config.isAiEnabled() && aiClient.isAvailable();
    }

    /**
     * Generates structured test cases from a requirement specification.
     *
     * @param input the requirement input (content must not be null or empty)
     * @return structured TestCaseGenerationResponse (never throws an exception)
     */
    public TestCaseGenerationResponse generateTestCases(RequirementInput input) {
        if (input == null || input.getContent() == null || input.getContent().isBlank()) {
            return TestCaseGenerationResponse.failure("Requirement input content cannot be null or empty");
        }

        if (!config.isAiEnabled()) {
            return TestCaseGenerationResponse.failure("AI is disabled (ai.enabled=false)");
        }

        if (!aiClient.isAvailable()) {
            return TestCaseGenerationResponse.failure("AI client is not available or credentials missing");
        }

        try {
            // 1. Sanitize the input deterministically before building prompt
            RequirementInput safeInput = input.sanitize();

            // 2. Build the prompt
            String prompt = TestCaseGenerationPrompt.buildPrompt(safeInput);

            // 3. Construct AI request
            AiRequest request = AiRequest.builder()
                    .systemInstruction(TestCaseGenerationPrompt.SYSTEM_INSTRUCTION)
                    .prompt(prompt)
                    .temperature(0.3)
                    .maxTokens(4096)
                    .build();

            // 4. Send request to AI client
            AiResponse response = aiClient.generate(request);

            if (!response.isSuccess()) {
                LOGGER.warn("AI generation failed for requirement [{}]: {}", input.getRequirementId(), response.getErrorMessage());
                return TestCaseGenerationResponse.failure("AI generation failed: " + response.getErrorMessage());
            }

            // 5. Parse and validate JSON response
            return parseAndValidate(response.getContent(), safeInput);

        } catch (Exception e) {
            LOGGER.warn("Unexpected error during test case generation: {}", e.getMessage());
            return TestCaseGenerationResponse.failure("Generation error: " + e.getMessage());
        }
    }

    private TestCaseGenerationResponse parseAndValidate(String rawContent, RequirementInput input) {
        if (rawContent == null || rawContent.isBlank()) {
            return TestCaseGenerationResponse.failure("AI returned empty content");
        }

        try {
            String cleaned = cleanJsonContent(rawContent);
            JsonNode root = mapper.readTree(cleaned);

            String reqId = root.path("requirementId").asText(input.getRequirementId());
            String module = root.path("module").asText(input.getModule());

            List<GeneratedTestCase> testCases = new ArrayList<>();
            JsonNode tcArray = root.path("testCases");
            if (tcArray.isArray()) {
                for (JsonNode tcNode : tcArray) {
                    testCases.add(parseTestCaseNode(tcNode, module, reqId));
                }
            }

            List<String> missing = new ArrayList<>();
            JsonNode missingNode = root.path("missingRequirements");
            if (missingNode.isArray()) {
                for (JsonNode m : missingNode) {
                    missing.add(m.asText());
                }
            }

            List<String> assumptions = new ArrayList<>();
            JsonNode assumptionsNode = root.path("assumptions");
            if (assumptionsNode.isArray()) {
                for (JsonNode a : assumptionsNode) {
                    assumptions.add(a.asText());
                }
            }

            TestCaseGenerationResponse rawResponse = TestCaseGenerationResponse.builder()
                    .success(true)
                    .requirementId(reqId)
                    .module(module)
                    .testCases(testCases)
                    .missingRequirements(missing)
                    .assumptions(assumptions)
                    .addMetadata("rawContentLength", rawContent.length())
                    .build();

            // Enforce ID validation and scenario deduplication
            return rawResponse.validateAndDeduplicate();

        } catch (Exception e) {
            LOGGER.warn("Failed to parse AI test generation JSON: {}", e.getMessage());
            return TestCaseGenerationResponse.failure("Failed to parse AI response: " + e.getMessage());
        }
    }

    private GeneratedTestCase parseTestCaseNode(JsonNode node, String defaultModule, String defaultReqId) {
        String id = node.path("testCaseId").asText("");
        String mod = node.path("module").asText(defaultModule);
        String scenario = node.path("scenario").asText("");
        String desc = node.path("description").asText("");
        String expected = node.path("expectedResult").asText("");
        String prioStr = node.path("priority").asText("P1");
        String typeStr = node.path("testType").asText("FUNCTIONAL");
        String reqRef = node.path("requirementReference").asText(defaultReqId);

        List<String> pre = new ArrayList<>();
        JsonNode preNode = node.path("preconditions");
        if (preNode.isArray()) {
            for (JsonNode p : preNode) pre.add(p.asText());
        }

        List<String> steps = new ArrayList<>();
        JsonNode stepsNode = node.path("steps");
        if (stepsNode.isArray()) {
            for (JsonNode s : stepsNode) steps.add(s.asText());
        }

        List<TestDataSuggestion> dataList = new ArrayList<>();
        JsonNode dataNode = node.path("testData");
        if (dataNode.isArray()) {
            for (JsonNode d : dataNode) {
                dataList.add(new TestDataSuggestion(
                        d.path("field").asText(""),
                        d.path("suggestedValue").asText(""),
                        d.path("description").asText("")
                ));
            }
        }

        List<String> tags = new ArrayList<>();
        JsonNode tagsNode = node.path("tags");
        if (tagsNode.isArray()) {
            for (JsonNode t : tagsNode) tags.add(t.asText());
        }

        return new GeneratedTestCase(
                id, mod, scenario, desc, pre, dataList, steps, expected,
                TestCasePriority.fromString(prioStr),
                TestCaseType.fromString(typeStr),
                tags, reqRef
        );
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