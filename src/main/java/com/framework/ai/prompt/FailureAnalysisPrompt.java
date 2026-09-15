package com.framework.ai.prompt;

import com.framework.ai.model.FailureContext;
import com.framework.ai.sanitizer.SensitiveDataSanitizer;
import java.util.Objects;

/**
 * Dedicated prompt engineering template for AI test failure and root-cause analysis.
 *
 * Enforces:
 * - Senior QA / SDET persona
 * - Strict evidence-based reasoning
 * - Uncertainty rules (do not invent DOM elements, API responses, or selectors)
 * - Structured JSON output format mapping to AiAnalysisResponse
 * - Mandatory pre-prompt sanitization
 */
public final class FailureAnalysisPrompt {

    public static final String SYSTEM_INSTRUCTION =
            "You are a Senior QA Automation Architect and SDET specialized in Playwright, Java, and web automation failure analysis.\n"
            + "Your task is to analyze test failure diagnostics, distinguish automation test issues from real application defects, identify the root cause, and provide actionable fixes.\n\n"
            + "CONSTRAINTS & RULES:\n"
            + "1. Analyze ONLY the evidence provided in the diagnostics. Do NOT invent DOM elements, selectors, or API responses.\n"
            + "2. Never claim an application defect is confirmed unless clear evidence (e.g. HTTP 500, error toast, visible crash) exists in the context.\n"
            + "3. If evidence is ambiguous or incomplete, state uncertainty explicitly and assign an appropriate confidence score (between 0.0 and 1.0).\n"
            + "4. Classify the failure into exactly ONE of the following categories:\n"
            + "   APPLICATION_BUG, LOCATOR_CHANGED, TIMEOUT, NETWORK_FAILURE, API_FAILURE, DATA_ISSUE, AUTHENTICATION_FAILURE, ENVIRONMENT_FAILURE, TEST_FAILURE, UNCERTAIN, UNKNOWN\n"
            + "5. Provide your output strictly as a valid JSON object matching the requested schema. Do not wrap in markdown code blocks like ```json ... ```. Return raw JSON only.";

    private FailureAnalysisPrompt() {
    }

    /**
     * Builds the complete prompt for the LLM given a FailureContext.
     * Sanitizes all fields in FailureContext before embedding them into the prompt.
     *
     * @param context the failure context (must not be null)
     * @return prompt text
     */
    public static String buildPrompt(FailureContext context) {
        Objects.requireNonNull(context, "FailureContext must not be null");

        // Sanitize every component deterministically before embedding in the prompt
        String safeTestName = SensitiveDataSanitizer.sanitize(context.getTestName());
        String safeTestClass = SensitiveDataSanitizer.sanitize(context.getTestClass());
        String safeErrorMessage = SensitiveDataSanitizer.sanitize(context.getErrorMessage());
        String safeStackTrace = SensitiveDataSanitizer.sanitize(context.getStackTrace());
        String safeUrl = SensitiveDataSanitizer.sanitize(context.getCurrentUrl());
        String safeTitle = SensitiveDataSanitizer.sanitize(context.getPageTitle());
        String safeDom = SensitiveDataSanitizer.sanitize(context.getDomSnippet());
        String safeEnv = SensitiveDataSanitizer.sanitize(context.getEnvironment());

        return "=== TEST FAILURE DIAGNOSTIC REPORT ===\n\n"
                + "[CONTEXT]\n"
                + "Environment: " + safeEnv + "\n"
                + "Test Class: " + safeTestClass + "\n"
                + "Test Method: " + safeTestName + "\n"
                + "Execution Duration: " + context.getExecutionDurationMs() + " ms\n"
                + "Page URL at Failure: " + safeUrl + "\n"
                + "Page Title: " + safeTitle + "\n\n"
                + "[ERROR MESSAGE]\n"
                + safeErrorMessage + "\n\n"
                + "[STACK TRACE (RELEVANT FRAMES)]\n"
                + safeStackTrace + "\n\n"
                + "[DOM CONTEXT (TRUNCATED)]\n"
                + safeDom + "\n\n"
                + "=== INSTRUCTIONS ===\n"
                + "Analyze the above failure evidence and return a JSON object with this exact structure:\n"
                + "{\n"
                + "  \"summary\": \"Brief 1-2 sentence summary of what failed\",\n"
                + "  \"rootCause\": \"Detailed technical explanation of why the test failed based on the stack trace, error, and DOM\",\n"
                + "  \"category\": \"ONE of: APPLICATION_BUG, LOCATOR_CHANGED, TIMEOUT, NETWORK_FAILURE, API_FAILURE, DATA_ISSUE, AUTHENTICATION_FAILURE, ENVIRONMENT_FAILURE, TEST_FAILURE, UNCERTAIN, UNKNOWN\",\n"
                + "  \"suggestedFix\": \"Specific, concrete code or configuration change to resolve the failure\",\n"
                + "  \"suggestedLocators\": [\"css-or-xpath-selector-1\", \"selector-2\"],\n"
                + "  \"jiraBugReport\": \"A concise, Jira-ready bug report with Summary, Steps to Reproduce, Expected, and Actual\",\n"
                + "  \"confidenceScore\": 0.85\n"
                + "}\n\n"
                + "Return ONLY the raw JSON object. Do not include markdown code block formatting or explanations outside the JSON.";
    }
}
