package com.framework.ai.codegeneration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.framework.ai.client.AiClient;
import com.framework.ai.client.GeminiApiClient;
import com.framework.ai.config.AiConfig;
import com.framework.ai.model.AiRequest;
import com.framework.ai.model.AiResponse;
import com.framework.ai.testgeneration.GeneratedTestCase;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Hardened orchestration service for generating draft Playwright Java code.
 * Enforces evidence verification, strict classification of unverified elements,
 * and isolated file output in target/ai-generated/.
 */
public class PlaywrightCodeGenerator {

    private static final Logger LOGGER = LogManager.getLogger(PlaywrightCodeGenerator.class);

    private final AiConfig config;
    private final AiClient aiClient;
    private final ObjectMapper mapper;

    public PlaywrightCodeGenerator() {
        this(new AiConfig(), new GeminiApiClient());
    }

    public PlaywrightCodeGenerator(AiConfig config, AiClient aiClient) {
        this.config = Objects.requireNonNull(config, "AiConfig must not be null");
        this.aiClient = Objects.requireNonNull(aiClient, "AiClient must not be null");
        this.mapper = new ObjectMapper();
    }

    public boolean isGenerationAvailable() {
        return config.isAiEnabled() && aiClient.isAvailable();
    }

    public GeneratedTestCodeResponse generateDraftCode(GeneratedTestCase testCase, boolean exportFiles) {
        if (testCase == null || testCase.getScenario() == null || testCase.getScenario().isBlank()) {
            return GeneratedTestCodeResponse.failure("GeneratedTestCase scenario cannot be null or empty");
        }

        if (!config.isAiEnabled()) {
            return GeneratedTestCodeResponse.failure("AI is disabled (ai.enabled=false)");
        }

        if (!aiClient.isAvailable()) {
            return GeneratedTestCodeResponse.failure("AI client is unavailable or credentials missing");
        }

        try {
            String prompt = TestCodeGenerationPrompt.buildPrompt(testCase);

            AiRequest request = AiRequest.builder()
                    .systemInstruction(TestCodeGenerationPrompt.SYSTEM_INSTRUCTION)
                    .prompt(prompt)
                    .temperature(0.2)
                    .maxTokens(4096)
                    .build();

            AiResponse response = aiClient.generate(request);

            if (!response.isSuccess()) {
                LOGGER.warn("AI code generation failed for scenario [{}]: {}", testCase.getScenario(), response.getErrorMessage());
                return GeneratedTestCodeResponse.failure("AI generation failed: " + response.getErrorMessage());
            }

            GeneratedTestCodeResponse parsed = parseAndValidate(response.getContent(), testCase);

            if (exportFiles && parsed.isSuccess()) {
                GeneratedCodeReporter.exportDraftFiles(parsed, testCase.getScenario());
            }

            return parsed;
        } catch (Exception e) {
            LOGGER.warn("Unexpected error during Playwright code generation: {}", e.getMessage());
            return GeneratedTestCodeResponse.failure("Code generation error: " + e.getMessage());
        }
    }

    private GeneratedTestCodeResponse parseAndValidate(String rawContent, GeneratedTestCase testCase) {
        if (rawContent == null || rawContent.isBlank()) {
            return GeneratedTestCodeResponse.failure("AI returned empty content");
        }

        try {
            String cleaned = cleanJsonContent(rawContent);
            JsonNode root = mapper.readTree(cleaned);

            String testClassName = root.path("testClassName").asText("FurlencoDraftTest");
            String packageName = root.path("packageName").asText("com.tests.web.furlenco.draft");
            String testClassCode = root.path("testClassCode").asText("");

            List<String> pageObjectMethods = new ArrayList<>();
            JsonNode poArray = root.path("pageObjectSuggestions");
            if (poArray.isArray()) {
                for (JsonNode po : poArray) pageObjectMethods.add(po.asText());
            }

            List<String> frameworkClasses = new ArrayList<>();
            JsonNode fcArray = root.path("referencedFrameworkClasses");
            if (fcArray.isArray()) {
                for (JsonNode fc : fcArray) frameworkClasses.add(fc.asText());
            }

            List<String> locators = new ArrayList<>();
            JsonNode locArray = root.path("locatorsUsed");
            if (locArray.isArray()) {
                for (JsonNode l : locArray) locators.add(l.asText());
            }

            List<String> testData = new ArrayList<>();
            JsonNode tdArray = root.path("testDataUsed");
            if (tdArray.isArray()) {
                for (JsonNode td : tdArray) testData.add(td.asText());
            }

            List<String> warnings = new ArrayList<>();
            JsonNode warnArray = root.path("warnings");
            if (warnArray.isArray()) {
                for (JsonNode w : warnArray) warnings.add(w.asText());
            }

            List<String> assumptions = new ArrayList<>();
            JsonNode assArray = root.path("assumptions");
            if (assArray.isArray()) {
                for (JsonNode a : assArray) assumptions.add(a.asText());
            }

            List<String> analytics = new ArrayList<>();
            JsonNode anArray = root.path("analyticsSuggestions");
            if (anArray.isArray()) {
                for (JsonNode an : anArray) analytics.add(an.asText());
            }

            // Parse or synthesize evidenceItems
            List<EvidenceItem> evidenceItems = new ArrayList<>();
            JsonNode eviArray = root.path("evidenceItems");
            if (eviArray.isArray() && !eviArray.isEmpty()) {
                for (JsonNode ev : eviArray) {
                    evidenceItems.add(new EvidenceItem(
                            ev.path("item").asText(""),
                            ev.path("value").asText(""),
                            ev.path("status").asText("UNVERIFIED"),
                            ev.path("source").asText("AI inference"),
                            ev.path("confidence").asDouble(0.5)
                    ));
                }
            } else {
                // Synthesize from raw locators/analytics
                for (String loc : locators) {
                    evidenceItems.add(EvidenceItem.builder()
                            .item("Locator")
                            .value(loc)
                            .status(EvidenceStatus.UNVERIFIED)
                            .source("AI inference")
                            .confidence(0.3)
                            .build());
                }
                if (analytics.isEmpty()) {
                    evidenceItems.add(EvidenceItem.builder()
                            .item("Analytics event")
                            .value("None")
                            .status(EvidenceStatus.MISSING)
                            .source("No requirement evidence")
                            .confidence(0.0)
                            .build());
                }
            }

            // Ensure disclaimer header in Java code
            String finalClassCode = ensureDisclaimerHeader(testClassCode);

            // Run static code validation
            ValidationResult validation = CodeValidator.validateCode(finalClassCode, testClassName);
            if (!validation.isValid()) {
                warnings.addAll(validation.getErrors());
            }
            warnings.addAll(validation.getWarnings());

            return GeneratedTestCodeResponse.builder()
                    .success(true)
                    .testClassName(testClassName)
                    .packageName(packageName)
                    .testClassCode(finalClassCode)
                    .pageObjectMethods(pageObjectMethods)
                    .referencedFrameworkClasses(frameworkClasses)
                    .locatorsUsed(locators)
                    .testDataUsed(testData)
                    .warnings(warnings)
                    .assumptions(assumptions)
                    .analyticsSuggestions(analytics)
                    .evidenceItems(evidenceItems)
                    .reviewRequired(true)
                    .build();

        } catch (Exception e) {
            LOGGER.warn("Failed to parse AI code generation JSON: {}", e.getMessage());
            return GeneratedTestCodeResponse.failure("Failed to parse AI response: " + e.getMessage());
        }
    }

    private String ensureDisclaimerHeader(String code) {
        String header = "// AI-GENERATED DRAFT\n// HUMAN REVIEW REQUIRED\n// DO NOT MERGE WITHOUT QA REVIEW\n\n";
        if (code == null) {
            return header;
        }
        if (!code.contains("AI-GENERATED DRAFT")) {
            return header + code;
        }
        return code;
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