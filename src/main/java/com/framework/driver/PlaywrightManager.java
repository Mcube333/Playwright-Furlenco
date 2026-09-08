package com.framework.driver;

import com.framework.config.ConfigManager;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Tracing;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Manages Playwright / Browser / BrowserContext / Page lifecycles per test thread.
 * ThreadLocal-scoped so parallel TestNG execution (methods or classes level) does not
 * leak browser state across tests. Every ThreadLocal MUST be explicitly removed in tearDown()
 * — leaving it set causes cross-test pollution and hard-to-reproduce flakiness in parallel runs.
 */
public final class PlaywrightManager {

    private static final Logger LOGGER = LogManager.getLogger(PlaywrightManager.class);

    private static final ThreadLocal<Playwright> PLAYWRIGHT = new ThreadLocal<>();
    private static final ThreadLocal<Browser> BROWSER = new ThreadLocal<>();
    private static final ThreadLocal<BrowserContext> CONTEXT = new ThreadLocal<>();
    private static final ThreadLocal<Page> PAGE = new ThreadLocal<>();

    private PlaywrightManager() {
    }

    public static void initBrowser() {
        ConfigManager config = ConfigManager.getInstance();

        Playwright playwright = Playwright.create();
        PLAYWRIGHT.set(playwright);

        BrowserType browserType = resolveBrowserType(playwright, config.browser());

        Browser browser = browserType.launch(new BrowserType.LaunchOptions()
                .setHeadless(config.headless())
                .setSlowMo(config.getInt("slowmo.ms", 0)));
        BROWSER.set(browser);

        BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                .setViewportSize(1920, 1080)
                .setRecordVideoDir(Paths.get("target/videos")));
        context.setDefaultTimeout(config.defaultTimeoutMs());
        CONTEXT.set(context);

        // Start tracing unconditionally; we only PERSIST the trace file on failure (see stopTracing).
        // This keeps CI artifact storage lean for large regression suites.
        context.tracing().start(new Tracing.StartOptions()
                .setScreenshots(true)
                .setSnapshots(true)
                .setSources(true));

        Page page = context.newPage();
        PAGE.set(page);

        LOGGER.info("Initialized {} browser (headless={}) for thread {}",
                config.browser(), config.headless(), Thread.currentThread().getId());
    }

    private static BrowserType resolveBrowserType(Playwright playwright, String browserName) {
        return switch (browserName.toLowerCase()) {
            case "firefox" -> playwright.firefox();
            case "webkit" -> playwright.webkit();
            default -> playwright.chromium();
        };
    }

    public static Page getPage() {
        Page page = PAGE.get();
        if (page == null) {
            throw new IllegalStateException(
                    "No Page bound to this thread. Call PlaywrightManager.initBrowser() in @BeforeMethod first.");
        }
        return page;
    }

    public static BrowserContext getContext() {
        return CONTEXT.get();
    }

    /**
     * Saves the trace .zip only when called — wire this into the TestNG listener's onTestFailure.
     */
    public static void saveTraceOnFailure(String testName) {
        BrowserContext context = CONTEXT.get();
        if (context == null) {
            return;
        }
        Path tracePath = Paths.get("target/traces", testName + "-trace.zip");
        context.tracing().stop(new Tracing.StopOptions().setPath(tracePath));
        LOGGER.warn("Saved Playwright trace for failed test [{}] to {}", testName, tracePath);
    }

    /**
     * Discards trace data for passing tests (no artifact written) — call from onTestSuccess/onTestSkipped.
     */
    public static void discardTrace() {
        BrowserContext context = CONTEXT.get();
        if (context == null) {
            return;
        }
        context.tracing().stop();
    }

    public static byte[] captureScreenshot() {
        Page page = PAGE.get();
        if (page == null) {
            return new byte[0];
        }
        return page.screenshot(new Page.ScreenshotOptions().setFullPage(true));
    }

    public static void tearDown() {
        try {
            if (CONTEXT.get() != null) {
                CONTEXT.get().close();
            }
            if (BROWSER.get() != null) {
                BROWSER.get().close();
            }
            if (PLAYWRIGHT.get() != null) {
                PLAYWRIGHT.get().close();
            }
        } finally {
            // Critical: remove ThreadLocal references even if close() throws,
            // otherwise pooled/reused threads (common with TestNG parallel="methods")
            // silently carry over stale Page/Context references into the next test.
            PAGE.remove();
            CONTEXT.remove();
            BROWSER.remove();
            PLAYWRIGHT.remove();
        }
    }
}
