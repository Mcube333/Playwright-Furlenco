package com.framework.listeners;

import com.framework.config.ConfigManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;

/**
 * Retries a failed test up to retry.max.count times (default 2, configurable per env).
 * Applied globally via RetryTransformer (IAnnotationTransformer) — NOT via @Test(retryAnalyzer=...)
 * on individual methods, so no test author can forget to wire it in.
 */
public class RetryAnalyzer implements IRetryAnalyzer {

    private static final Logger LOGGER = LogManager.getLogger(RetryAnalyzer.class);
    private int retryCount = 0;

    @Override
    public boolean retry(ITestResult result) {
        int maxRetries = ConfigManager.getInstance().getInt("retry.max.count", 2);
        if (retryCount < maxRetries) {
            retryCount++;
            LOGGER.warn("Retrying [{}] — attempt {}/{}", result.getMethod().getMethodName(), retryCount, maxRetries);
            // Marking the eventual pass as "flaky" is handled in TestListener via Allure's flaky label
            // so a retried-then-passed test is still visible for triage, not silently green.
            return true;
        }
        return false;
    }
}
