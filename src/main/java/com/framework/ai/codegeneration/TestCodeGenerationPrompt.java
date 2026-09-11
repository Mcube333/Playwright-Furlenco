package com.framework.ai.codegeneration;

import com.framework.ai.testgeneration.GeneratedTestCase;
import java.util.Objects;

/**
 * Dedicated prompt template for generating Playwright Java TestNG test code.
 */
public final class TestCodeGenerationPrompt {

    public static final String SYSTEM_INSTRUCTION =
            "You are a Principal SDET and Playwright Java Automation Architect.\n"
            + "Your task is to generate clean, maintainable, production-ready DRAFT TestNG test code and Page Object methods in Java for Playwright.\n\n"
            + "FRAMEWORK ARCHITECTURE & CONVENTIONS:\n"
            + "- Base class: extends com.tests.base.BaseWebTest (provides protected Page page, setup and teardown)\n"
            + "- Assertions: AssertJ (org.assertj.core.api.Assertions.assertThat)\n"
            + "- Annotations: TestNG (@Test, @BeforeMethod) and Allure (@Epic, @Feature, @Severity, @Description, @Step)\n"
            + "- Logging: Log4j2 (org.apache.logging.log4j.LogManager, Logger)\n"
            + "- Config: com.framework.config.ConfigManager.getInstance()\n\n"
            + "STRICT CODING RULES:\n"
            + "1. Header: Include '// AI-GENERATED DRAFT\\n// HUMAN REVIEW REQUIRED\\n// DO NOT MERGE WITHOUT QA REVIEW' at the top of every generated file.\n"
            + "2. NEVER use Thread.sleep(). Rely on Playwright auto-waiting, locator assertions, or page.waitForLoadState().\n"
            + "3. LOCATOR HIERARCHY: getByTestId > getByRole > accessible name > stable CSS. Avoid positional (.nth()) or fragile XPath.\n"
            + "4. If a reliable locator cannot be deduced from evidence, output: '// TODO: LOCATOR REQUIRED - explain missing info'. DO NOT invent selectors.\n"
            + "5. REUSE existing Page Objects if applicable (e.g. FurlencoHomePage, FurlencoCartDrawer, FurlencoSearchResultsPage). If a new method is required, generate it as a draft suggestion separately.\n"
            + "6. TEST DATA: Use safe dummy placeholders (e.g. 'TEST_USER', 'test@example.com'). Never use real secrets or PII. If unknown, output '// TODO: TEST DATA REQUIRED'.\n"
            + "7. ASSERTIONS: Use meaningful, high-value assertions checking expected behavior from the test case. Do not use trivial assertTrue(page != null).\n"
            + "8. Return your output STRICTLY as a valid JSON object matching the requested schema. No markdown code blocks like ```json ... ```.";

    private TestCodeGenerationPrompt() {
    }

    /**
     * Builds the prompt from a GeneratedTestCase.
     */
    public static String buildPrompt(GeneratedTestCase testCase) {
        Objects.requireNonNull(testCase, "GeneratedTestCase must not be null");

        StringBuilder sb = new StringBuilder();
        sb.append("=== GENERATE PLAYWRIGHT JAVA TEST CODE FOR THE FOLLOWING TEST CASE ===\n\n");
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
        sb.append("Generate a draft @Test class and any required Page Object methods as a JSON object with this exact structure:\n");
        sb.append("{\n");
        sb.append("  \"testClassName\": \"Furlenco" + sanitizeClassName(testCase.getModule()) + "DraftTest\",\n");
        sb.append("  \"packageName\": \"com.tests.web.furlenco.draft\",\n");
        sb.append("  \"testClassCode\": \"// AI-GENERATED DRAFT\\n// HUMAN REVIEW REQUIRED\\n... full Java class ...\",\n");
        sb.append("  \"pageObjectSuggestions\": [\"// Method suggestion for Page Object...\"],\n");
        sb.append("  \"referencedFrameworkClasses\": [\"BaseWebTest\", \"FurlencoHomePage\"],\n");
        sb.append("  \"locatorsUsed\": [\"button[aria-label='Cart']\"],\n");
        sb.append("  \"testDataUsed\": [\"TEST_USER\"],\n");
        sb.append("  \"warnings\": [\"Locator for checkout confirmation needs QA staging validation\"],\n");
        sb.append("  \"assumptions\": [\"User starts from clean session\"],\n");
        sb.append("  \"analyticsSuggestions\": [\"Verify 'cart_updated' event fired with quantity=2\"]\n");
        sb.append("}\n\n");
        sb.append("Return ONLY the raw JSON object. Do not include markdown code block formatting or explanations outside the JSON.");

        return sb.toString();
    }

    private static String sanitizeClassName(String input) {
        if (input == null || input.isBlank()) return "General";
        return input.replaceAll("[^a-zA-Z0-9]", "");
    }
}