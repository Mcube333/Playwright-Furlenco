package com.framework.listeners;

import com.framework.driver.PlaywrightManager;
import io.qameta.allure.Allure;
import java.io.ByteArrayInputStream;
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
 */
public class TestListener implements ITestListener {

    private static final Logger LOGGER = LogManager.getLogger(TestListener.class);

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

    private String testId(ITestResult result) {
        return result.getTestClass().getName() + "#" + result.getMethod().getMethodName();
    }
}
