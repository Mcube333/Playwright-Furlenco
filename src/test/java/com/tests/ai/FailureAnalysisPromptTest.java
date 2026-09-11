package com.tests.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.framework.ai.model.FailureContext;
import com.framework.ai.prompt.FailureAnalysisPrompt;
import org.testng.annotations.Test;

public class FailureAnalysisPromptTest {

    @Test
    public void testPromptContainsRequiredSections() {
        FailureContext context = FailureContext.builder()
                .testName("testSearchSofa")
                .testClass("com.tests.web.furlenco.FurlencoSearchAndBrowseTest")
                .errorMessage("Timeout 10000ms waiting for locator('input[placeholder*=Search]')")
                .stackTrace("at com.tests.pages.FurlencoHomePage.search(FurlencoHomePage.java:45)")
                .currentUrl("https://www.furlenco.com")
                .pageTitle("Furlenco Furniture")
                .domSnippet("<div><input placeholder='Search products'/></div>")
                .environment("qa")
                .executionDurationMs(12500)
                .build();

        String prompt = FailureAnalysisPrompt.buildPrompt(context);

        assertThat(prompt).contains("[CONTEXT]");
        assertThat(prompt).contains("[ERROR MESSAGE]");
        assertThat(prompt).contains("[STACK TRACE (RELEVANT FRAMES)]");
        assertThat(prompt).contains("[DOM CONTEXT (TRUNCATED)]");
        assertThat(prompt).contains("=== INSTRUCTIONS ===");
        assertThat(prompt).contains("summary");
        assertThat(prompt).contains("rootCause");
        assertThat(prompt).contains("category");
        assertThat(prompt).contains("suggestedFix");
        assertThat(prompt).contains("suggestedLocators");
        assertThat(prompt).contains("jiraBugReport");
        assertThat(prompt).contains("confidenceScore");
    }

    @Test
    public void testSystemInstructionContainsRulesAndPersona() {
        String sys = FailureAnalysisPrompt.SYSTEM_INSTRUCTION;
        assertThat(sys).contains("Senior QA Automation Architect and SDET");
        assertThat(sys).contains("Do NOT invent DOM elements");
        assertThat(sys).contains("APPLICATION_BUG");
        assertThat(sys).contains("LOCATOR_CHANGED");
        assertThat(sys).contains("TIMEOUT");
    }

    @Test
    public void testSensitiveValuesAreSanitizedInPrompt() {
        FailureContext context = FailureContext.builder()
                .testName("testLogin")
                .testClass("com.tests.web.LoginTest")
                .errorMessage("Failed with password=SuperSecretPassword123!")
                .stackTrace("Authorization: Bearer secretJwtTokenHere")
                .currentUrl("https://qa.example.com?api_key=secretKey123")
                .build();

        String prompt = FailureAnalysisPrompt.buildPrompt(context);

        assertThat(prompt).doesNotContain("SuperSecretPassword123!");
        assertThat(prompt).doesNotContain("secretJwtTokenHere");
        assertThat(prompt).doesNotContain("secretKey123");
        assertThat(prompt).contains("[REDACTED]");
    }
}