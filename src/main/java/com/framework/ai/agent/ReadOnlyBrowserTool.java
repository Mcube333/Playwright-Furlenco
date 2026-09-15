package com.framework.ai.agent;

import com.framework.ai.extractor.DomContextExtractor;
import com.framework.ai.sanitizer.SensitiveDataSanitizer;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Phase 8 Step 4: a read-only browser observation tool — the agent's "eyes", never its "hands".
 *
 * READ-ONLY BY DESIGN. The only Playwright calls anywhere in this class are {@code page.isClosed()},
 * {@code page.url()}, {@code page.title()}, {@code page.content()} (via the existing, unmodified
 * {@link DomContextExtractor}), {@code page.locator(String)}, {@code Locator.count()},
 * {@code Locator.isVisible()}, and {@code Locator.isEnabled()}. There is no {@code click}/
 * {@code fill}/{@code type}/{@code press}/{@code check}/{@code selectOption}/{@code navigate}/
 * {@code reload}/{@code evaluate}/{@code setContent}/{@code addCookies}/{@code clearCookies}/
 * {@code grantPermissions} call — or any other state-mutating Playwright API — anywhere in this
 * file. There is also no reflection-based or string-driven dispatch to an arbitrary Playwright
 * method: every operation this class can perform is a fixed, named Java method call, so no
 * AI-generated string can ever become an executable browser action through this class.
 *
 * EXPLICITLY INVOKED ONLY. This class is never referenced by {@code TestListener}, any Base*Test,
 * {@code RetryAnalyzer}/{@code RetryTransformer}, or {@code PlaywrightManager} — a caller must
 * construct it and call one of its methods directly. It performs no loop, no retry, and no
 * observe-then-act cycle: each method is a single, one-shot read.
 *
 * SEPARATE FROM PHASE 6. This is not a replacement for, wrapper of, or dependency on
 * {@code RuntimeLocatorValidator} — that class performs controlled, evidence-producing validation
 * gated by {@code RuntimeEnvironmentGuard}; this class only produces an honest
 * {@link AgentBrowserObservation}/{@link LocatorObservation}, which never carries an
 * {@code EvidenceStatus} or {@code ValidationType}. Neither class is modified by the other.
 *
 * Reuses the existing, unmodified {@link DomContextExtractor} for DOM capture (same size bound,
 * same {@code ai.dom.max.bytes} configuration, same "[DOM context unavailable]" safe fallback) and
 * the existing, unmodified {@link SensitiveDataSanitizer} for AI-boundary preparation via
 * {@link #sanitizeForAi(AgentBrowserObservation)} — no second DOM extractor and no second
 * sanitizer are introduced.
 */
public class ReadOnlyBrowserTool {

    private static final Logger LOGGER = LogManager.getLogger(ReadOnlyBrowserTool.class);

    /**
     * Observes {@code page}'s URL, title, and DOM. Never throws: a {@code null}/closed Page, or any
     * individual read failing, results in a safe, partial {@link AgentBrowserObservation} rather
     * than a propagated exception. Successful fields are preserved even when another field's
     * observation fails (e.g. URL/title succeed while DOM extraction fails).
     */
    public AgentBrowserObservation observe(Page page) {
        return observe(page, List.of());
    }

    /**
     * Same as {@link #observe(Page)}, additionally observing each selector in {@code selectors}
     * via {@link #observeLocator(Page, String)}. {@code selectors} may be null or empty.
     */
    public AgentBrowserObservation observe(Page page, List<String> selectors) {
        if (page == null) {
            return unavailable("No Playwright Page supplied.");
        }

        boolean closed;
        try {
            closed = page.isClosed();
        } catch (Exception e) {
            LOGGER.warn("Could not determine Page state: {}", e.getMessage());
            return unavailable("Unable to determine Page state: " + e.getMessage());
        }
        if (closed) {
            return unavailable("Playwright Page is closed.");
        }

        List<String> partialErrors = new ArrayList<>();
        String url = safeUrl(page, partialErrors);
        String title = safeTitle(page, partialErrors);
        String dom = safeDom(page, partialErrors);

        AgentBrowserObservation.Builder builder = AgentBrowserObservation.builder()
                .pageAvailable(true)
                .url(url)
                .title(title)
                .domSnapshot(dom);

        if (selectors != null) {
            for (String selector : selectors) {
                builder.addObservedLocatorState(observeLocator(page, selector));
            }
        }

        if (!partialErrors.isEmpty()) {
            builder.error(String.join("; ", partialErrors));
        }

        return builder.build();
    }

    /** Safe, single-field observation. Returns {@code null} (never throws) if the URL cannot be read. */
    public String observeUrl(Page page) {
        if (!isPageUsable(page)) {
            return null;
        }
        return safeUrl(page, new ArrayList<>());
    }

    /** Safe, single-field observation. Returns {@code null} (never throws) if the title cannot be read. */
    public String observeTitle(Page page) {
        if (!isPageUsable(page)) {
            return null;
        }
        return safeTitle(page, new ArrayList<>());
    }

    /**
     * Safe, single-field observation. Delegates entirely to the existing, unmodified
     * {@link DomContextExtractor} (same bounded size, same truncation behavior) — no new DOM
     * extraction logic is introduced here.
     */
    public String observeDom(Page page) {
        if (!isPageUsable(page)) {
            return DomContextExtractor.extractFromPage(page);
        }
        return safeDom(page, new ArrayList<>());
    }

    /**
     * Observes a single locator's read-only state: match count, visibility, enabled state. Never
     * throws. A {@code null}/blank selector, a {@code null}/closed Page, or an unresolvable
     * selector all produce a safe {@link LocatorObservation} carrying an {@code error} rather than
     * an exception. This method never clicks, fills, or otherwise interacts with the located
     * element — it only calls {@code count()}, {@code isVisible()}, and {@code isEnabled()}.
     */
    public LocatorObservation observeLocator(Page page, String selector) {
        if (page == null) {
            return LocatorObservation.builder().selector(selector).error("No Playwright Page supplied.").build();
        }
        if (selector == null || selector.isBlank()) {
            return LocatorObservation.builder().selector(selector).error("No selector supplied.").build();
        }

        try {
            if (page.isClosed()) {
                return LocatorObservation.builder().selector(selector).error("Playwright Page is closed.").build();
            }
        } catch (Exception e) {
            return LocatorObservation.builder().selector(selector)
                    .error("Unable to determine Page state: " + e.getMessage()).build();
        }

        Locator locator;
        int count;
        try {
            locator = page.locator(selector);
            count = locator.count();
        } catch (Exception e) {
            LOGGER.warn("Could not resolve locator [{}]: {}", selector, e.getMessage());
            return LocatorObservation.builder().selector(selector)
                    .error("Invalid or unresolvable selector: " + e.getMessage()).build();
        }

        Boolean visible = null;
        try {
            visible = locator.isVisible();
        } catch (Exception e) {
            LOGGER.debug("Visibility observation failed for locator [{}]: {}", selector, e.getMessage());
        }

        Boolean enabled = null;
        try {
            enabled = locator.isEnabled();
        } catch (Exception e) {
            LOGGER.debug("Enabled-state observation failed for locator [{}]: {}", selector, e.getMessage());
        }

        return LocatorObservation.builder()
                .selector(selector)
                .count(count)
                .visible(visible)
                .enabled(enabled)
                .build();
    }

    /**
     * Returns a new {@link AgentBrowserObservation} with every string field passed through the
     * existing, unmodified {@link SensitiveDataSanitizer} — the explicit boundary where raw
     * observed browser content (which may contain an authorization header, cookie, session id,
     * token, or password echoed back in the DOM/URL/title/error text) is made safe before it could
     * ever be prepared for an AI prompt. This method is never called automatically; a caller must
     * invoke it explicitly at the point data is about to cross into an AI-facing boundary.
     */
    public AgentBrowserObservation sanitizeForAi(AgentBrowserObservation observation) {
        if (observation == null) {
            return null;
        }

        AgentBrowserObservation.Builder builder = AgentBrowserObservation.builder()
                .pageAvailable(observation.isPageAvailable())
                .url(sanitize(observation.getUrl()))
                .title(sanitize(observation.getTitle()))
                .domSnapshot(sanitize(observation.getDomSnapshot()))
                .error(sanitize(observation.getError()));

        for (LocatorObservation locatorObservation : observation.getObservedLocatorStates()) {
            builder.addObservedLocatorState(LocatorObservation.builder()
                    .selector(sanitize(locatorObservation.getSelector()))
                    .count(locatorObservation.getCount())
                    .visible(locatorObservation.getVisible())
                    .enabled(locatorObservation.getEnabled())
                    .error(sanitize(locatorObservation.getError()))
                    .build());
        }

        return builder.build();
    }

    // ------------------------------------------------------------------------------------------
    // Internal helpers — each individually failure-isolated so one field failing never discards
    // whatever else was already successfully observed.
    // ------------------------------------------------------------------------------------------

    private AgentBrowserObservation unavailable(String reason) {
        return AgentBrowserObservation.builder().pageAvailable(false).error(reason).build();
    }

    private boolean isPageUsable(Page page) {
        if (page == null) {
            return false;
        }
        try {
            return !page.isClosed();
        } catch (Exception e) {
            LOGGER.warn("Could not determine Page state: {}", e.getMessage());
            return false;
        }
    }

    private String safeUrl(Page page, List<String> errors) {
        try {
            return page.url();
        } catch (Exception e) {
            LOGGER.warn("URL observation failed: {}", e.getMessage());
            errors.add("URL observation failed: " + e.getMessage());
            return null;
        }
    }

    private String safeTitle(Page page, List<String> errors) {
        try {
            return page.title();
        } catch (Exception e) {
            LOGGER.warn("Title observation failed: {}", e.getMessage());
            errors.add("Title observation failed: " + e.getMessage());
            return null;
        }
    }

    private String safeDom(Page page, List<String> errors) {
        try {
            return DomContextExtractor.extractFromPage(page);
        } catch (Exception e) {
            LOGGER.warn("DOM observation failed: {}", e.getMessage());
            errors.add("DOM observation failed: " + e.getMessage());
            return null;
        }
    }

    private String sanitize(String value) {
        return value == null ? null : SensitiveDataSanitizer.sanitize(value);
    }
}
