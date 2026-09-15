package com.framework.ai.testgeneration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Exports generated test cases into a human-readable Markdown report and file.
 */
public final class TestCaseGenerationReporter {

    private static final Logger LOGGER = LogManager.getLogger(TestCaseGenerationReporter.class);
    private static final Path DEFAULT_OUTPUT_DIR = Paths.get("target", "ai-test-generation");

    private TestCaseGenerationReporter() {
    }

    /**
     * Converts a TestCaseGenerationResponse into a comprehensive Markdown document.
     */
    public static String toMarkdown(TestCaseGenerationResponse response) {
        if (response == null) {
            return "# AI Test Case Generation Report\n\nNo test case data provided.\n";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("# AI Test Case Generation Report\n\n");
        sb.append("**Requirement ID:** ").append(response.getRequirementId().isBlank() ? "N/A" : response.getRequirementId()).append("\n");
        sb.append("**Module:** ").append(response.getModule().isBlank() ? "General" : response.getModule()).append("\n");
        sb.append("**Total Test Cases:** ").append(response.getTestCases().size()).append("\n");
        sb.append("**Generated At:** ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())).append("\n\n");

        if (!response.isSuccess()) {
            sb.append("> [!WARNING]\n");
            sb.append("> Generation encountered an error: ").append(response.getErrorMessage()).append("\n\n");
            return sb.toString();
        }

        // Summary Table
        sb.append("## Test Case Summary Table\n\n");
        sb.append("| ID | Module | Scenario | Type | Priority | Expected Result |\n");
        sb.append("|---|---|---|---|---|---|\n");

        for (GeneratedTestCase tc : response.getTestCases()) {
            sb.append(String.format("| %s | %s | %s | %s | %s | %s |\n",
                    tc.getTestCaseId(),
                    tc.getModule(),
                    escapePipes(tc.getScenario()),
                    tc.getTestType(),
                    tc.getPriority(),
                    escapePipes(tc.getExpectedResult())
            ));
        }
        sb.append("\n");

        // Detailed Scenarios
        sb.append("## Detailed Test Scenarios\n\n");
        for (GeneratedTestCase tc : response.getTestCases()) {
            sb.append("### ").append(tc.getTestCaseId()).append(": ").append(tc.getScenario()).append("\n\n");
            sb.append("- **Description:** ").append(tc.getDescription()).append("\n");
            sb.append("- **Priority:** ").append(tc.getPriority()).append(" | **Type:** ").append(tc.getTestType()).append("\n");

            if (!tc.getTags().isEmpty()) {
                sb.append("- **Tags:** `").append(String.join("`, `", tc.getTags())).append("`\n");
            }

            if (!tc.getPreconditions().isEmpty()) {
                sb.append("- **Preconditions:**\n");
                for (String pre : tc.getPreconditions()) {
                    sb.append("  - ").append(pre).append("\n");
                }
            }

            if (!tc.getSteps().isEmpty()) {
                sb.append("- **Test Steps:**\n");
                for (int i = 0; i < tc.getSteps().size(); i++) {
                    sb.append("  ").append(i + 1).append(". ").append(tc.getSteps().get(i)).append("\n");
                }
            }

            sb.append("- **Expected Result:** ").append(tc.getExpectedResult()).append("\n");

            if (!tc.getTestData().isEmpty()) {
                sb.append("- **Suggested Test Data:**\n");
                for (TestDataSuggestion td : tc.getTestData()) {
                    sb.append("  - `").append(td.getField()).append("` = `").append(td.getSuggestedValue())
                            .append("` (").append(td.getDescription()).append(")\n");
                }
            }
            sb.append("\n");
        }

        // Missing Requirements / Clarifications
        if (!response.getMissingRequirements().isEmpty()) {
            sb.append("## Requirement Clarifications & Missing Details\n\n");
            sb.append("> [!NOTE]\n");
            sb.append("> The AI identified the following ambiguities or gaps in the provided requirements:\n\n");
            for (String missing : response.getMissingRequirements()) {
                sb.append("- ").append(missing).append("\n");
            }
            sb.append("\n");
        }

        // Assumptions
        if (!response.getAssumptions().isEmpty()) {
            sb.append("## Assumptions Made\n\n");
            for (String assumption : response.getAssumptions()) {
                sb.append("- ").append(assumption).append("\n");
            }
            sb.append("\n");
        }

        sb.append("---\n*Human Review Required before converting to automated test scripts: YES*\n");

        return sb.toString();
    }

    /**
     * Writes the Markdown report to target/ai-test-generation/
     */
    public static Path saveReport(TestCaseGenerationResponse response) {
        try {
            Files.createDirectories(DEFAULT_OUTPUT_DIR);
            String safeReqId = (response.getRequirementId() != null && !response.getRequirementId().isBlank())
                    ? response.getRequirementId().replaceAll("[^a-zA-Z0-9._-]", "_")
                    : "test_suite";
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String fileName = safeReqId + "_" + timestamp + ".md";

            Path targetFile = DEFAULT_OUTPUT_DIR.resolve(fileName).normalize();
            Files.writeString(targetFile, toMarkdown(response), StandardCharsets.UTF_8);
            LOGGER.info("Test case generation report written to: {}", targetFile);
            return targetFile;
        } catch (IOException e) {
            LOGGER.warn("Failed to save test generation markdown report: {}", e.getMessage());
            return null;
        }
    }

    private static String escapePipes(String text) {
        if (text == null) return "";
        return text.replace("|", "\\|");
    }
}