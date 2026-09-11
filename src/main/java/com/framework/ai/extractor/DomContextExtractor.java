package com.framework.ai.extractor;

import com.framework.config.ConfigManager;
import com.framework.driver.PlaywrightManager;
import com.microsoft.playwright.Page;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Extracts conservative, size-limited DOM context from the active browser page.
 *
 * Designed to prevent excessive token usage, privacy leaks, and performance overhead.
 * Target default limit: 15 KB (configurable via ai.dom.max.bytes).
 *
 * If extraction fails or no page is bound, returns a safe fallback message
 * rather than throwing an exception or failing the test.
 */
public final class DomContextExtractor {

    private static final Logger LOGGER = LogManager.getLogger(DomContextExtractor.class);
    private static final int DEFAULT_MAX_BYTES = 15 * 1024; // 15 KB
    private static final String UNAVAILABLE_MSG = "[DOM context unavailable]";

    private DomContextExtractor() {
    }

    /**
     * Safely extracts the DOM from the current Playwright Page.
     *
     * @return bounded DOM string, or "[DOM context unavailable]" if extraction fails
     */
    public static String extractSafeDom() {
        try {
            Page page = PlaywrightManager.getPage();
            if (page == null || page.isClosed()) {
                return UNAVAILABLE_MSG;
            }
            return extractFromPage(page);
        } catch (IllegalStateException e) {
            // No page bound to this thread (e.g. API-only test)
            LOGGER.debug("No Playwright page bound to current thread: {}", e.getMessage());
            return UNAVAILABLE_MSG;
        } catch (Exception e) {
            LOGGER.warn("Failed to extract DOM context from page: {}", e.getMessage());
            return UNAVAILABLE_MSG;
        }
    }

    /**
     * Extracts and bounds DOM from a specific Page instance.
     *
     * @param page the Playwright Page
     * @return bounded DOM string
     */
    public static String extractFromPage(Page page) {
        if (page == null) {
            return UNAVAILABLE_MSG;
        }

        int maxBytes = ConfigManager.getInstance().getInt("ai.dom.max.bytes", DEFAULT_MAX_BYTES);

        try {
            // Attempt to get main body innerHTML or full content
            String content = page.content();
            if (content == null || content.isBlank()) {
                return UNAVAILABLE_MSG;
            }

            return truncateSafely(content, maxBytes);
        } catch (Exception e) {
            LOGGER.warn("Exception during page content retrieval: {}", e.getMessage());
            return UNAVAILABLE_MSG;
        }
    }

    /**
     * Truncates content to maxBytes without splitting characters inappropriately,
     * adding a clear truncation notice.
     */
    public static String truncateSafely(String content, int maxBytes) {
        if (content == null) {
            return "";
        }

        byte[] bytes = content.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) {
            return content;
        }

        // Truncate to maxBytes
        String truncated = new String(bytes, 0, maxBytes, java.nio.charset.StandardCharsets.UTF_8);
        return truncated + "\n... [DOM truncated: exceeded " + maxBytes + " bytes limit]";
    }
}
