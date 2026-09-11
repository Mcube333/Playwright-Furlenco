package com.framework.listeners;

import com.framework.ai.config.AiConfig;
import com.framework.ai.extractor.DomContextExtractor;
import com.framework.ai.model.AiAnalysisResponse;
import com.framework.ai.model.FailureContext;
import com.framework.ai.report.AiAnalysisReporter;
import com.framework.ai.sanitizer.SensitiveDataSanitizer;
import com.framework.ai.service.FailureAnalysisService;
import com.framework.driver.PlaywrightManager;
import com.microsoft.playwright.Page;
import io.qameta.allure.Allure;
import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;

/**
 * Registered in testng.xml under <listeners>. Handles:
 *  - Screenshot + Playwright trace capture ONLY on failure (kept out of PlaywrightManager
 *    itself so non-Web/API-only test runs never pay the tracing cost).
 *  - Structured start/end/pass/fail/skip logging with timestamps.
 *  - Flagging retried-then-passed tests as "flaky" in Allure instead of a silent pass.
 *  - Safe AI failure and root-cause analysis hook (additive, runs only when AI enabled).
 */
public class TestListener implements ITestListener {

    private static final Logger LOGGER = LogManager.getLogger(TestListener.class);
    private final FailureAnalysisService aiAnalysisService;
    private final AiConfig aiConfig;

    public TestListener() {
        this.aiConfig = new AiConfig();
        this.aiAnalysisService = new FailureAnalysisService(aiConfig, new com.framework.ai.client.GeminiApiClient(aiConfig));
    }

    public TestListener(AiConfig aiConfig, FailureAnalysisService aiAnalysisService) {
        this.aiConfig = aiConfig;
        this.aiAnalysisService = aiAnalysisService;
    }

    @Override
    public void onTestStart(ITestResult result) {
        LOGGER.info("[START] {} at {}", testId(result), LocalDateTime.now());
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        LOGGER.info("[PASS] {} ({} ms)", testId(result), result.getEndMillis() - result.getStartMillis());
        if (result.getMethod().getCurrentInvocationCount() > 1) {
            Allure.label("flaky", "true");
            LOGGER.warn("[FLAKY] {} passed only after retry", testId(result));
        }
        safeDiscardTrace();
    }

    @Override
    public void onTestFailure(ITestResult result) {
        LOGGER.error("[FAIL] {} — {}", testId(result), result.getThrowable() != null
                ? result.getThrowable().getMessage() : "no exception captured");
        attachScreenshotIfAvailable(result);
        safeSaveTrace(result);
        safeAiFailureAnalysis(result);
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        LOGGER.warn("[SKIP] {} — {}", testId(result), result.getThrowable() != null
                ? result.getThrowable().getMessage() : "dependency failure or skip condition");
        safeDiscardTrace();
    }

    @Override
    public void onStart(ITestContext context) {
        LOGGER.info("=== Suite started: {} ===", context.getName());
    }

    @Override
    public void onFinish(ITestContext context) {
        LOGGER.info("=== Suite finished: {} | Passed={} Failed={} Skipped={} ===",
                context.getName(),
                context.getPassedTests().size(),
                context.getFailedTests().size(),
                context.getSkippedTests().size());
    }

    private void attachScreenshotIfAvailable(ITestResult result) {
        try {
            byte[] screenshot = PlaywrightManager.captureScreenshot();
            if (screenshot.length > 0) {
                Allure.addAttachment(testId(result) + " - Screenshot", new ByteArrayInputStream(screenshot));
            }
        } catch (IllegalStateException e) {
            // API-only test with no Page bound — expected, not an error.
            LOGGER.debug("No page bound for screenshot capture on {} (likely an API test)", testId(result));
        }
    }

    private void safeSaveTrace(ITestResult result) {
        try {
            PlaywrightManager.saveTraceOnFailure(testId(result).replaceAll("[^a-zA-Z0-9._-]", "_"));
        } catch (IllegalStateException ignored) {
            // No browser context for this test (API-only) — nothing to trace.
        }
    }

    private void safeDiscardTrace() {
        try {
            PlaywrightManager.discardTrace();
        } catch (IllegalStateException ignored) {
            // No browser context for this test (API-only) — nothing to discard.
        }
    }

    private void safeAiFailureAnalysis(ITestResult result) {
        try {
            if (!aiConfig.isAiEnabled() || !aiConfig.isFailureAnalysisEnabled()) {
                return;
            }

            // Extract safe diagnostic details
            String currentUrl = "";
            String pageTitle = "";
            try {
                Page page = PlaywrightManager.getPage();
                if (page != null && !page.isClosed()) {
                    currentUrl = page.url();
                    pageTitle = page.title();
                }
            } catch (Exception ignored) {
                // Not a browser test or page closed
            }

            String domSnippet = DomContextExtractor.extractSafeDom();

            String errorMessage = "";
            String stackTrace = "";
            if (result.getThrowable() != null) {
                errorMessage = result.getThrowable().getMessage() != null
                        ? result.getThrowable().getMessage() : result.getThrowable().toString();
                StringWriter sw = new StringWriter();
                result.getThrowable().printStackTrace(new PrintWriter(sw));
                stackTrace = sw.toString();
            }

            long duration = result.getEndMillis() - result.getStartMillis();

            FailureContext context = FailureContext.builder()
                    .testName(result.getMethod().getMethodName())
                    .testClass(result.getTestClass().getName())
                    .errorMessage(errorMessage)
                    .stackTrace(stackTrace)
                    .currentUrl(currentUrl)
                    .pageTitle(pageTitle)
                    .domSnippet(domSnippet)
                    .executionDurationMs(duration)
                    .environment(aiConfig.getProvider())
                    .build();

            AiAnalysisResponse analysis = aiAnalysisService.analyze(context);
            if (analysis != null) {
                AiAnalysisReporter.report(testId(result), analysis);
            }
        } catch (Exception e) {
            // Absolute boundary: AI failure must never become test failure or alter test outcome
            LOGGER.warn("AI failure analysis hook encountered an error: {}", e.getMessage());
        }
    }

    private String testId(ITestResult result) {
        return result.getTestClass().getName() + "#" + result.getMethod().getMethodName();
    }
}
