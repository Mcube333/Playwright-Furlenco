package com.framework.ai.diagnosis;

import com.framework.ai.config.AiConfig;
import com.framework.ai.extractor.DomContextExtractor;
import com.framework.ai.model.FailureContext;
import com.framework.driver.PlaywrightManager;
import com.microsoft.playwright.Page;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.ITestResult;

/**
 * Phase 7 Step 6: the smallest explicit entry point for a QA engineer to run controlled failure
 * diagnosis during manual investigation of a test failure.
 *
 * EXPLICITLY INVOKED ONLY. This class is never registered as a TestNG {@code ITestListener}, never
 * annotated with {@code @Listeners}, and never called from {@code TestListener}, {@code BaseTest},
 * {@code BaseWebTest}, {@code BaseApiTest}, {@code RetryAnalyzer}, or {@code RetryTransformer}. A
 * caller must construct an instance and call {@link #diagnose(ITestResult)}/{@link #report(FailureDiagnosis)}
 * themselves — nothing here runs automatically for any test, passing or failing.
 *
 * PURE PLUMBING. This class performs no AI orchestration, no locator analysis, and no runtime
 * validation itself. It exists solely to remove the boilerplate (identified in the Phase 7 Step 5
 * review) of building a {@link FailureContext} from an {@link ITestResult} outside of
 * {@code TestListener}, and then delegates entirely to the existing, unmodified
 * {@link FailureDiagnosisService} and {@link FailureDiagnosisReporter}.
 *
 * PAGE SAFETY. This class never creates a {@code Playwright}/{@code Browser}/{@code BrowserContext}/
 * {@code Page}. It only reads the {@code Page} already bound to the current thread via the existing
 * {@link PlaywrightManager#getPage()}, and only calls the same read-only methods
 * ({@code isClosed()}, {@code url()}, {@code title()}) that {@code TestListener}'s own existing
 * Phase 2 hook already calls. DOM extraction is delegated entirely to the existing, bounded
 * {@link DomContextExtractor} — no direct {@code Page.content()} call is made here. Runtime locator
 * validation is never invoked directly from this class; the {@code Page} is simply passed through
 * to {@link FailureDiagnosisService}, which alone decides whether/how to use it.
 *
 * KNOWN, PRESERVED LIMITATION (documented per the Step 5/Step 6 review, not fixed here): like
 * {@code TestListener}'s existing Phase 2 hook, the {@code FailureContext.environment} field
 * populated by this class is the AI provider name (e.g. {@code "gemini"}, from
 * {@link AiConfig#getProvider()}), not the actual runtime/test environment
 * ({@code ConfigManager.getEnvironment()}). This class intentionally preserves that existing
 * semantic rather than silently redefining it — changing it would alter Phase 2 behavior, which is
 * out of scope for this integration step. Phase 6 runtime validation is unaffected by this: it
 * independently reads the real environment via {@code ConfigManager.getEnvironment()} inside
 * {@code RuntimeLocatorValidator}/{@code RuntimeEnvironmentGuard}, not from this field.
 */
public class FailureDiagnosisHelper {

    private static final Logger LOGGER = LogManager.getLogger(FailureDiagnosisHelper.class);

    private final AiConfig aiConfig;
    private final FailureDiagnosisService diagnosisService;
    private final FailureDiagnosisReporter reporter;

    public FailureDiagnosisHelper() {
        this(new AiConfig(), new FailureDiagnosisService(), new FailureDiagnosisReporter());
    }

    public FailureDiagnosisHelper(AiConfig aiConfig,
                                   FailureDiagnosisService diagnosisService,
                                   FailureDiagnosisReporter reporter) {
        this.aiConfig = Objects.requireNonNull(aiConfig, "AiConfig must not be null");
        this.diagnosisService = Objects.requireNonNull(diagnosisService, "FailureDiagnosisService must not be null");
        this.reporter = Objects.requireNonNull(reporter, "FailureDiagnosisReporter must not be null");
    }

    /**
     * Builds a {@link FailureContext} from {@code testResult} — the same fields
     * {@code TestListener}'s own existing Phase 2 hook already captures — and runs it through the
     * existing, unmodified {@link FailureDiagnosisService}. The current Playwright {@code Page}
     * (if any is bound to this thread) is passed through unchanged; runtime validation remains
     * entirely {@code FailureDiagnosisService}'s decision.
     *
     * Never throws: a {@code null} {@code testResult}, or any error while collecting diagnostic
     * context, results in an honest, possibly context-only {@link FailureDiagnosis} rather than a
     * propagated exception — collecting diagnosis context must never alter a QA test's outcome.
     */
    public FailureDiagnosis diagnose(ITestResult testResult) {
        if (testResult == null) {
            LOGGER.debug("FailureDiagnosisHelper.diagnose() called with a null ITestResult; "
                    + "delegating to FailureDiagnosisService with no failure context.");
            return diagnosisService.diagnose(null);
        }

        try {
            FailureContext context = buildFailureContext(testResult);
            Page page = safeGetPage();
            return diagnosisService.diagnose(context, page);
        } catch (Exception e) {
            // Absolute boundary: collecting diagnosis context must never fail the caller's test.
            LOGGER.warn("Unexpected error while preparing failure diagnosis context: {}", e.getMessage());
            return diagnosisService.diagnose(null);
        }
    }

    /**
     * Explicitly attaches {@code diagnosis} to the current Allure test context by delegating to
     * the existing {@link FailureDiagnosisReporter#attachToAllure(FailureDiagnosis)}. Never called
     * automatically — the caller decides if/when a diagnosis is worth attaching. Never throws.
     */
    public void report(FailureDiagnosis diagnosis) {
        try {
            reporter.attachToAllure(diagnosis);
        } catch (Exception e) {
            // Defense in depth: attachToAllure() already never throws today, but reporting must
            // never affect a test's outcome even if that guarantee changes underneath us.
            LOGGER.warn("Failed to report failure diagnosis: {}", e.getMessage());
        }
    }

    /**
     * Convenience only: {@link #diagnose(ITestResult)} followed immediately by
     * {@link #report(FailureDiagnosis)}. Still entirely opt-in — this method itself is never
     * invoked automatically by any lifecycle class; a caller must call it explicitly.
     */
    public FailureDiagnosis diagnoseAndReport(ITestResult testResult) {
        FailureDiagnosis diagnosis = diagnose(testResult);
        report(diagnosis);
        return diagnosis;
    }

    // ------------------------------------------------------------------------------------------
    // FailureContext construction — mirrors TestListener's existing, unmodified Phase 2 hook
    // field-for-field. Reuses the existing FailureContext.Builder; no new context semantics.
    // ------------------------------------------------------------------------------------------

    private FailureContext buildFailureContext(ITestResult testResult) {
        String testName = safeMethodName(testResult);
        String testClass = safeClassName(testResult);

        String errorMessage = "";
        String stackTrace = "";
        Throwable throwable = safeThrowable(testResult);
        if (throwable != null) {
            errorMessage = throwable.getMessage() != null ? throwable.getMessage() : throwable.toString();
            StringWriter sw = new StringWriter();
            throwable.printStackTrace(new PrintWriter(sw));
            stackTrace = sw.toString();
        }

        Page page = safeGetPage();
        boolean pageUsable = isPageUsable(page);

        String currentUrl = pageUsable ? safeUrl(page) : "";
        String pageTitle = pageUsable ? safeTitle(page) : "";
        // DomContextExtractor is safe regardless of null/closed Page — mirrors TestListener,
        // which calls it unconditionally rather than gating it on the same isClosed() check.
        String domSnippet = safeDom(page);

        long duration = safeDuration(testResult);

        return FailureContext.builder()
                .testName(testName)
                .testClass(testClass)
                .errorMessage(errorMessage)
                .stackTrace(stackTrace)
                .currentUrl(currentUrl)
                .pageTitle(pageTitle)
                .domSnippet(domSnippet)
                .executionDurationMs(duration)
                .environment(aiConfig.getProvider())
                .build();
    }

    private boolean isPageUsable(Page page) {
        if (page == null) {
            return false;
        }
        try {
            return !page.isClosed();
        } catch (Exception e) {
            LOGGER.warn("Failed to determine Page state while building failure context: {}", e.getMessage());
            return false;
        }
    }

    private Page safeGetPage() {
        try {
            return PlaywrightManager.getPage();
        } catch (IllegalStateException e) {
            // No page bound to this thread (e.g. an API-only test) — expected, not an error.
            LOGGER.debug("No Playwright page bound to current thread: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            LOGGER.warn("Unexpected error obtaining the current Playwright page: {}", e.getMessage());
            return null;
        }
    }

    private String safeUrl(Page page) {
        try {
            return page.url();
        } catch (Exception e) {
            LOGGER.warn("Failed to read the current page URL while building failure context: {}", e.getMessage());
            return "";
        }
    }

    private String safeTitle(Page page) {
        try {
            return page.title();
        } catch (Exception e) {
            LOGGER.warn("Failed to read the current page title while building failure context: {}", e.getMessage());
            return "";
        }
    }

    private String safeDom(Page page) {
        try {
            return DomContextExtractor.extractFromPage(page);
        } catch (Exception e) {
            LOGGER.warn("Failed to extract bounded DOM context while building failure context: {}", e.getMessage());
            return "";
        }
    }

    private Throwable safeThrowable(ITestResult testResult) {
        try {
            return testResult.getThrowable();
        } catch (Exception e) {
            LOGGER.warn("Failed to read the throwable from ITestResult: {}", e.getMessage());
            return null;
        }
    }

    private String safeMethodName(ITestResult testResult) {
        try {
            return testResult.getMethod().getMethodName();
        } catch (Exception e) {
            LOGGER.warn("Failed to read the test method name from ITestResult: {}", e.getMessage());
            return "";
        }
    }

    private String safeClassName(ITestResult testResult) {
        try {
            return testResult.getTestClass().getName();
        } catch (Exception e) {
            LOGGER.warn("Failed to read the test class name from ITestResult: {}", e.getMessage());
            return "";
        }
    }

    private long safeDuration(ITestResult testResult) {
        try {
            return testResult.getEndMillis() - testResult.getStartMillis();
        } catch (Exception e) {
            LOGGER.warn("Failed to compute test execution duration from ITestResult: {}", e.getMessage());
            return 0L;
        }
    }
}
