package com.tests.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.framework.ai.model.AiAnalysisResponse;
import com.framework.ai.model.FailureCategory;
import com.framework.ai.report.AiAnalysisReporter;
import org.testng.annotations.Test;

public class AiAnalysisReporterTest {

    @Test
    public void testFormatReportContainsAllSections() {
        AiAnalysisResponse response = AiAnalysisResponse.builder()
                .summary("Locator not found on page")
                .rootCause("DOM element was not loaded before click timed out")
                .category(FailureCategory.LOCATOR_CHANGED)
                .suggestedFix("Use waitForSelector or update CSS selector")
                .addSuggestedLocator("button[data-test='submit']")
                .jiraBugReport("Steps to reproduce: ...")
                .confidenceScore(0.88)
                .build();

        String report = AiAnalysisReporter.formatReport("com.tests.web.LoginTest#testLogin", response);

        assertThat(report).contains("AI FAILURE & ROOT CAUSE ANALYSIS");
        assertThat(report).contains("Test: com.tests.web.LoginTest#testLogin");
        assertThat(report).contains("Category: LOCATOR_CHANGED");
        assertThat(report).contains("Confidence: 88%");
        assertThat(report).contains("Summary:\nLocator not found on page");
        assertThat(report).contains("Root Cause:\nDOM element was not loaded");
        assertThat(report).contains("Suggested Fix:\nUse waitForSelector");
        assertThat(report).contains("button[data-test='submit']");
        assertThat(report).contains("Jira-ready Bug Report:\nSteps to reproduce: ...");
        assertThat(report).contains("Human Review Required: YES");
    }

    @Test
    public void testNullResponseDoesNotThrow() {
        // Should safely return without throwing
        AiAnalysisReporter.report("test", null);
    }
}