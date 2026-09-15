package com.framework.ai.report;

import com.framework.ai.model.AiAnalysisResponse;
import com.framework.config.ConfigManager;
import io.qameta.allure.Allure;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Handles reporting of AI failure analysis results.
 *
 * 1. Attaches formatted text to Allure with the attachment name:
 *    "AI Failure & Root Cause Analysis"
 * 2. Optionally writes report to target/ai-analysis/ when
 *    ai.failure.analysis.save.file=true (with safe path-traversal prevention).
 * 3. Never throws an unhandled exception or interrupts the test lifecycle.
 */
public final class AiAnalysisReporter {

    private static final Logger LOGGER = LogManager.getLogger(AiAnalysisReporter.class);
    private static final String ATTACHMENT_NAME = "AI Failure & Root Cause Analysis";
    private static final Path OUTPUT_DIR = Paths.get("target", "ai-analysis");

    private AiAnalysisReporter() {
    }

    /**
     * Reports AI analysis result to Allure and optional file destination.
     *
     * @param testName the test identifier
     * @param response the structured AI analysis response
     */
    public static void report(String testName, AiAnalysisResponse response) {
        if (response == null) {
            return;
        }

        try {
            String formattedReport = formatReport(testName, response);

            // 1. Attach to Allure
            attachToAllure(formattedReport);

            // 2. Save to file if configured
            boolean saveFile = ConfigManager.getInstance().getBoolean("ai.failure.analysis.save.file", false);
            if (saveFile) {
                saveReportToFile(testName, formattedReport);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to report AI analysis for {}: {}", testName, e.getMessage());
        }
    }

    public static String formatReport(String testName, AiAnalysisResponse response) {
        StringBuilder sb = new StringBuilder();
        sb.append("====================================================\n");
        sb.append("      AI FAILURE & ROOT CAUSE ANALYSIS\n");
        sb.append("====================================================\n\n");
        sb.append("Test: ").append(testName != null ? testName : "Unknown").append("\n");
        sb.append("Category: ").append(response.getCategory() != null ? response.getCategory().name() : "UNKNOWN").append("\n");
        sb.append(String.format("Confidence: %.0f%%\n\n", response.getConfidenceScore() * 100));

        sb.append("Summary:\n");
        sb.append(response.getSummary()).append("\n\n");

        sb.append("Root Cause:\n");
        sb.append(response.getRootCause()).append("\n\n");

        sb.append("Suggested Fix:\n");
        sb.append(response.getSuggestedFix()).append("\n\n");

        sb.append("Suggested Locators:\n");
        if (response.getSuggestedLocators() != null && !response.getSuggestedLocators().isEmpty()) {
            for (String loc : response.getSuggestedLocators()) {
                sb.append(" - ").append(loc).append("\n");
            }
        } else {
            sb.append(" (None suggested)\n");
        }
        sb.append("\n");

        sb.append("Jira-ready Bug Report:\n");
        sb.append(response.getJiraBugReport()).append("\n\n");

        sb.append("----------------------------------------------------\n");
        sb.append("Human Review Required: YES\n");
        sb.append("====================================================\n");

        return sb.toString();
    }

    private static void attachToAllure(String reportContent) {
        try {
            Allure.addAttachment(ATTACHMENT_NAME, "text/plain",
                    new ByteArrayInputStream(reportContent.getBytes(StandardCharsets.UTF_8)), ".txt");
        } catch (Exception e) {
            LOGGER.warn("Could not attach AI analysis to Allure: {}", e.getMessage());
        }
    }

    private static void saveReportToFile(String testName, String reportContent) {
        try {
            Files.createDirectories(OUTPUT_DIR);

            String safeName = sanitizeFileName(testName);
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String fileName = safeName + "_" + timestamp + ".txt";

            Path targetPath = OUTPUT_DIR.resolve(fileName).normalize();

            // Guard against path traversal: must stay within OUTPUT_DIR
            if (!targetPath.startsWith(OUTPUT_DIR.toAbsolutePath().normalize())
                    && !targetPath.startsWith(OUTPUT_DIR.normalize())) {
                LOGGER.warn("Path traversal detected in test name: {}. File write skipped.", testName);
                return;
            }

            Files.writeString(targetPath, reportContent, StandardCharsets.UTF_8);
            LOGGER.info("AI analysis report written to: {}", targetPath);
        } catch (Exception e) {
            LOGGER.warn("Could not write AI report file: {}", e.getMessage());
        }
    }

    private static String sanitizeFileName(String name) {
        if (name == null) {
            return "unknown_test";
        }
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
