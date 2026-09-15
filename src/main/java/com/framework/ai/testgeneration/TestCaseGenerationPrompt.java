package com.framework.ai.testgeneration;

import java.util.Objects;

/**
 * Dedicated prompt engineering template for AI test case and test data generation.
 *
 * Enforces:
 * - Senior QA / Lead SDET persona
 * - Strict requirement coverage without inventing requirements
 * - Positive, negative, boundary, validation, error-handling, responsive, and permission scenarios
 * - Scenario deduplication and meaningful coverage over volume
 * - Explicit identification of missing requirements or needed clarifications
 * - Safe dummy test data placeholders (no real PII or secrets)
 * - Raw JSON output matching the TestCaseGenerationResponse schema
 */
public final class TestCaseGenerationPrompt {

    public static final String SYSTEM_INSTRUCTION =
            "You are a Lead QA Automation Architect and Senior SDET specialized in Test Design, Quality Engineering, and Automation Planning.\n"
            + "Your task is to analyze product requirements/acceptance criteria and generate a comprehensive, structured, deduplicated test suite.\n\n"
            + "CORE RULES & GUIDELINES:\n"
            + "1. Map test cases directly to the stated requirements.\n"
            + "2. Generate relevant scenarios: Functional (happy path), Negative (invalid inputs/actions), Boundary (limits, empty, max), Validation, and Error Handling.\n"
            + "3. AVOID DUPLICATION: Do NOT generate multiple tests that assert identical system behavior under minor rewordings.\n"
            + "4. DO NOT INVENT REQUIREMENTS: If requirements are vague, underspecified, or have unaddressed edge cases, record them in the 'missingRequirements' list.\n"
            + "5. Use ONLY safe dummy test data (e.g. 'test@example.com', 'TEST_USER', 'SKU-1001'). NEVER suggest real customer PII, real payment cards, or secrets.\n"
            + "6. Assign realistic priorities: P0 (Smoke/Blocker), P1 (Critical/Core Regression), P2 (Major/Secondary), P3 (Minor/Edge).\n"
            + "7. Categorize each test into ONE valid testType: FUNCTIONAL, NEGATIVE, BOUNDARY, VALIDATION, ERROR_HANDLING, PERMISSION, AUTHENTICATION, DATA_INTEGRITY, UI, NAVIGATION, RESPONSIVE, API, REGRESSION, CROSS_BROWSER.\n"
            + "8. Return your response STRICTLY as a raw valid JSON object. Do NOT wrap in markdown code blocks like ```json ... ```. Return valid JSON only.";

    private TestCaseGenerationPrompt() {
    }

    /**
     * Builds the complete prompt for the LLM given a sanitized RequirementInput.
     */
    public static String buildPrompt(RequirementInput input) {
        Objects.requireNonNull(input, "RequirementInput must not be null");

        String reqId = input.getRequirementId().isBlank() ? "N/A" : input.getRequirementId();
        String title = input.getTitle().isBlank() ? "Feature Specification" : input.getTitle();
        String module = input.getModule().isBlank() ? "General" : input.getModule();

        return "=== REQUIREMENT SPECIFICATION FOR TEST GENERATION ===\n\n"
                + "Requirement ID: " + reqId + "\n"
                + "Module: " + module + "\n"
                + "Title: " + title + "\n\n"
                + "[REQUIREMENTS / ACCEPTANCE CRITERIA]\n"
                + input.getContent() + "\n\n"
                + "=== INSTRUCTIONS ===\n"
                + "Generate structured test cases for the requirement above. Return a JSON object with this exact structure:\n"
                + "{\n"
                + "  \"requirementId\": \"" + reqId + "\",\n"
                + "  \"module\": \"" + module + "\",\n"
                + "  \"testCases\": [\n"
                + "    {\n"
                + "      \"testCaseId\": \"TC-001\",\n"
                + "      \"module\": \"" + module + "\",\n"
                + "      \"scenario\": \"Concise title of scenario\",\n"
                + "      \"description\": \"Detailed explanation of what this test verifies\",\n"
                + "      \"preconditions\": [\"User is logged in\", \"Cart has at least 1 item\"],\n"
                + "      \"testData\": [\n"
                + "        {\"field\": \"quantity\", \"suggestedValue\": \"2\", \"description\": \"Valid positive increment\"}\n"
                + "      ],\n"
                + "      \"steps\": [\"Step 1\", \"Step 2\", \"Step 3\"],\n"
                + "      \"expectedResult\": \"Clear expected outcome\",\n"
                + "      \"priority\": \"P0\",\n"
                + "      \"testType\": \"FUNCTIONAL\",\n"
                + "      \"tags\": [\"cart\", \"smoke\"],\n"
                + "      \"requirementReference\": \"" + reqId + "\"\n"
                + "    }\n"
                + "  ],\n"
                + "  \"missingRequirements\": [\n"
                + "    \"What happens if item is out of stock during increment? (Requirement clarification needed)\"\n"
                + "  ],\n"
                + "  \"assumptions\": [\n"
                + "    \"Assumed standard cart timeout is 30 minutes\"\n"
                + "  ]\n"
                + "}\n\n"
                + "Return ONLY the raw JSON object. Do not include markdown code block formatting or explanations outside the JSON.";
    }
}