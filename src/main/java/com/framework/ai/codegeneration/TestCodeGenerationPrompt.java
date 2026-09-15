package com.framework.ai.codegeneration;

import com.framework.ai.testgeneration.GeneratedTestCase;
import java.util.Objects;

/**
 * Hardened prompt engineering template enforcing strict evidence-based generation.
 *
 * Rules:
 * - AI MUST NEVER present an invented locator, method, event, or API as verified fact.
 * - If actual DOM evidence is absent, locators MUST be prefixed with '// TODO: VERIFY LOCATOR AGAINST QA/STAGING DOM'
 *   and categorized as UNVERIFIED.
 * - If analytics details are not explicitly provided in the requirement, mark as MISSING_EVIDENCE.
 * - Prefer an incomplete but honest draft over a complete hallucinated draft.
 */
public final class TestCodeGenerationPrompt {

    public static final String SYSTEM_INSTRUCTION =
            "You are a Principal SDET and Playwright Java Automation Architect.\n"
            + "Your task is to generate honest, maintainable, production-ready DRAFT TestNG test code and Page Object methods in Java for Playwright.\n\n"
            + "CRITICAL EVIDENCE & ANTI-HALLUCINATION RULES:\n"
            + "1. NEVER present an invented locator, Page Object method, analytics event, or API as verified fact.\n"
            + "2. If DOM evidence is not supplied, you MUST classify proposed locators as 'UNVERIFIED' and prefix them in code with:\n"
            + "   '// TODO: VERIFY LOCATOR AGAINST QA/STAGING DOM'\n"
            + "3. ANALYTICS RULE: Never invent analytics events (e.g. 'cart_updated') or attributes unless explicitly given in the requirement.\n"
            + "   If missing, classify as 'MISSING' with advice: 'Confirm expected analytics event and attributes'.\n"
            + "4. REUSE EXISTING PAGE OBJECTS: If existing Page Objects (e.g. FurlencoHomePage, FurlencoCartDrawer) offer methods, use them.\n"
            + "   If missing, generate as a separate draft suggestion, never pretending it exists.\n"
            + "5. Prefer an incomplete, honest draft over a complete hallucinated draft.\n\n"
            + "FRAMEWORK ARCHITECTURE & CODING RULES:\n"
            + "- Base class: extends com.tests.base.BaseWebTest\n"
            + "- Assertions: AssertJ (org.assertj.core.api.Assertions.assertThat)\n"
            + "- Annotations: TestNG (@Test, @BeforeMethod) and Allure (@Epic, @Feature, @Severity, @Description, @Step)\n"
            + "- Logging: Log4j2 (org.apache.logging.log4j.LogManager, Logger)\n"
            + "- NEVER use Thread.sleep(). Rely on Playwright auto-waiting.\n"
            + "- Top header: '// AI-GENERATED DRAFT\\n// HUMAN REVIEW REQUIRED\\n// DO NOT MERGE WITHOUT QA REVIEW'\n"
            + "- Return strictly raw JSON matching the requested schema. No markdown code blocks.";

    private TestCodeGenerationPrompt() {
    }

    /**
     * Builds the prompt from a GeneratedTestCase.
     */
    public static String buildPrompt(GeneratedTestCase testCase) {
        Objects.requireNonNull(testCase, "GeneratedTestCase must not be null");

        StringBuilder sb = new StringBuilder();
        sb.append("=== GENERATE PLAYWRIGHT JAVA TEST CODE (EVIDENCE-HARDENED) ===\n\n");
        sb.append("Test Case ID: ").append(testCase.getTestCaseId()).append("\n");
        sb.append("Module: ").append(testCase.getModule()).append("\n");
        sb.append("Scenario: ").append(testCase.getScenario()).append("\n");
        sb.append("Priority: ").append(testCase.getPriority()).append("\n");
        sb.append("Type: ").append(testCase.getTestType()).append("\n");
        sb.append("Description: ").append(testCase.getDescription()).append("\n\n");

        sb.append("[PRECONDITIONS]\n");
        if (testCase.getPreconditions().isEmpty()) {
            sb.append("None specified\n");
        } else {
            for (String pre : testCase.getPreconditions()) {
                sb.append("- ").append(pre).append("\n");
            }
        }
        sb.append("\n");

        sb.append("[TEST STEPS]\n");
        if (testCase.getSteps().isEmpty()) {
            sb.append("None specified\n");
        } else {
            for (int i = 0; i < testCase.getSteps().size(); i++) {
                sb.append(i + 1).append(". ").append(testCase.getSteps().get(i)).append("\n");
            }
        }
        sb.append("\n");

        sb.append("[EXPECTED RESULT]\n");
        sb.append(testCase.getExpectedResult()).append("\n\n");

        sb.append("[SUGGESTED TEST DATA]\n");
        if (testCase.getTestData().isEmpty()) {
            sb.append("None specified\n");
        } else {
            for (var td : testCase.getTestData()) {
                sb.append("- ").append(td.getField()).append(" = ").append(td.getSuggestedValue())
                        .append(" (").append(td.getDescription()).append(")\n");
            }
        }
        sb.append("\n");

        sb.append("=== INSTRUCTIONS ===\n");
        sb.append("Generate a draft @Test class and Page Object suggestions. Every proposed locator or event MUST have an evidence classification.\n");
        sb.append("Return a JSON object with this exact structure:\n");
        sb.append("{\n");
        sb.append("  \"testClassName\": \"Furlenco" + sanitizeClassName(testCase.getModule()) + "DraftTest\",\n");
        sb.append("  \"packageName\": \"com.tests.web.furlenco.draft\",\n");
        sb.append("  \"testClassCode\": \"// AI-GENERATED DRAFT\\n// HUMAN REVIEW REQUIRED\\n... full Java class ...\",\n");
        sb.append("  \"pageObjectSuggestions\": [\"// Suggested method for Page Object...\"],\n");
        sb.append("  \"referencedFrameworkClasses\": [\"BaseWebTest\", \"FurlencoHomePage\"],\n");
        sb.append("  \"locatorsUsed\": [\"button[aria-label='Cart']\"],\n");
        sb.append("  \"testDataUsed\": [\"TEST_USER\"],\n");
        sb.append("  \"warnings\": [\"Locator for checkout confirmation needs QA staging validation\"],\n");
        sb.append("  \"assumptions\": [\"User starts from clean session\"],\n");
        sb.append("  \"analyticsSuggestions\": [\"ANALYTICS VALIDATION DETAILS REQUIRED: Confirm expected event and attributes\"],\n");
        sb.append("  \"evidenceItems\": [\n");
        sb.append("    {\"item\": \"Cart button\", \"value\": \"button[aria-label='Cart']\", \"status\": \"UNVERIFIED\", \"source\": \"AI inference\", \"confidence\": 0.4},\n");
        sb.append("    {\"item\": \"Analytics event\", \"value\": \"None\", \"status\": \"MISSING\", \"source\": \"No requirement evidence\", \"confidence\": 0.0}\n");
        sb.append("  ]\n");
        sb.append("}\n\n");
        sb.append("Return ONLY the raw JSON object. Do not include markdown code block formatting or explanations outside the JSON.");

        return sb.toString();
    }

    private static String sanitizeClassName(String input) {
        if (input == null || input.isBlank()) return "General";
        return input.replaceAll("[^a-zA-Z0-9]", "");
    }
}