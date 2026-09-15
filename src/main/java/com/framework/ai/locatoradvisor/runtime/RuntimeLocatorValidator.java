package com.framework.ai.locatoradvisor.runtime;

import com.framework.ai.codegeneration.EvidenceStatus;
import com.framework.ai.config.AiConfig;
import com.framework.ai.locatoradvisor.LocatorCandidate;
import com.framework.ai.locatoradvisor.ValidationType;
import com.framework.config.ConfigManager;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Phase 6: validates a Phase 5 {@link LocatorCandidate} against the CURRENT live Playwright
 * {@code Page} — reusing the existing {@code PlaywrightManager}-managed Page, never creating a
 * new Playwright instance, Browser, BrowserContext, or Page.
 *
 * ADVISORY, EXPLICITLY-INVOKED ONLY. Nothing in this class is wired into {@code TestListener} or
 * any suite; nothing here is called automatically for every candidate or every test.
 *
 * Read-only by design: the only Playwright calls made are {@code Page.isClosed()}, {@code Page.url()},
 * {@code Page.locator(String)}, {@code Locator.count()}, {@code Locator.isVisible()}, and
 * {@code Locator.isEnabled()}. No click/fill/press/check/hover/navigation/evaluate/state-mutation of
 * any kind is ever performed.
 *
 * Runtime evidence comes exclusively from these Playwright API calls. The AI is never consulted here
 * and cannot influence the outcome — this class does not read {@link LocatorCandidate#getEvidenceStatus()},
 * {@link LocatorCandidate#getValidationType()}, {@link LocatorCandidate#getConfidence()}, or
 * {@link LocatorCandidate#getScore()} at all; only {@link LocatorCandidate#getLocator()} (the raw
 * selector string) is used as input. The Phase 5 {@code LocatorCandidate} passed in is never mutated —
 * this method always returns a separate {@link RuntimeValidationResult}.
 */
public class RuntimeLocatorValidator {

    private static final Logger LOGGER = LogManager.getLogger(RuntimeLocatorValidator.class);

    private final AiConfig aiConfig;
    private final RuntimeEnvironmentGuard environmentGuard;

    public RuntimeLocatorValidator() {
        this(new AiConfig(), new RuntimeEnvironmentGuard());
    }

    public RuntimeLocatorValidator(AiConfig aiConfig, RuntimeEnvironmentGuard environmentGuard) {
        this.aiConfig = Objects.requireNonNull(aiConfig, "AiConfig must not be null");
        this.environmentGuard = Objects.requireNonNull(environmentGuard, "RuntimeEnvironmentGuard must not be null");
    }

    /** True only when the master switch is on. Does NOT check environment/host safety — see {@link RuntimeEnvironmentGuard}. */
    public boolean isRuntimeValidationEnabled() {
        return aiConfig.isLocatorRuntimeValidationEnabled();
    }

    /**
     * Validates {@code candidate} against {@code page}. Never throws — every failure mode
     * (disabled, null/closed Page, guard denial, invalid locator, or any Playwright exception)
     * returns a safe {@code NOT_VALIDATED} result instead of propagating, so a caller invoking
     * this from within a test can never have it alter that test's outcome.
     */
    public RuntimeValidationResult validate(Page page, LocatorCandidate candidate) {
        try {
            if (!aiConfig.isLocatorRuntimeValidationEnabled()) {
                return RuntimeValidationResult.notValidated(safeLocator(candidate),
                        "Runtime locator validation is disabled (ai.locator.runtime.validation.enabled=false).");
            }

            if (page == null) {
                return RuntimeValidationResult.notValidated(safeLocator(candidate), "No Playwright Page supplied.");
            }

            boolean closed;
            try {
                closed = page.isClosed();
            } catch (Exception e) {
                LOGGER.warn("Runtime locator validation could not determine Page state: {}", e.getMessage());
                return RuntimeValidationResult.notValidated(safeLocator(candidate),
                        "Unable to determine Page state: " + e.getMessage());
            }
            if (closed) {
                return RuntimeValidationResult.notValidated(safeLocator(candidate), "Playwright Page is closed.");
            }

            String environment = ConfigManager.getInstance().getEnvironment();
            String currentUrl;
            try {
                currentUrl = page.url();
            } catch (Exception e) {
                LOGGER.warn("Runtime locator validation could not read the current page URL: {}", e.getMessage());
                return RuntimeValidationResult.notValidated(safeLocator(candidate),
                        "Unable to read current page URL: " + e.getMessage());
            }

            RuntimeEnvironmentGuard.GuardDecision decision = environmentGuard.evaluate(environment, currentUrl);
            if (!decision.isAllowed()) {
                LOGGER.warn("Runtime locator validation blocked by environment guard: {}", decision.getReason());
                return RuntimeValidationResult.builder()
                        .locator(safeLocator(candidate))
                        .currentUrl(currentUrl)
                        .environment(environment)
                        .validationType(ValidationType.NOT_VALIDATED)
                        .evidenceStatus(EvidenceStatus.UNVERIFIED)
                        .message("Blocked by environment guard: " + decision.getReason())
                        .build();
            }

            if (candidate == null || candidate.getLocator() == null || candidate.getLocator().isBlank()) {
                return RuntimeValidationResult.builder()
                        .locator("")
                        .currentUrl(currentUrl)
                        .environment(environment)
                        .validationType(ValidationType.NOT_VALIDATED)
                        .evidenceStatus(EvidenceStatus.UNVERIFIED)
                        .message("No locator candidate supplied.")
                        .build();
            }

            String locatorString = candidate.getLocator();
            Locator locator;
            int matchCount;
            try {
                locator = page.locator(locatorString);
                matchCount = locator.count();
            } catch (Exception e) {
                LOGGER.warn("Runtime locator validation could not resolve locator [{}]: {}", locatorString, e.getMessage());
                return RuntimeValidationResult.builder()
                        .locator(locatorString)
                        .currentUrl(currentUrl)
                        .environment(environment)
                        .validationType(ValidationType.NOT_VALIDATED)
                        .evidenceStatus(EvidenceStatus.UNVERIFIED)
                        .message("Invalid or unresolvable locator: " + e.getMessage())
                        .build();
            }

            if (matchCount == 0) {
                return RuntimeValidationResult.builder()
                        .locator(locatorString)
                        .matchCount(0)
                        .currentUrl(currentUrl)
                        .environment(environment)
                        .validationType(ValidationType.RUNTIME_VALIDATED)
                        .evidenceStatus(EvidenceStatus.UNVERIFIED)
                        .message("No matching element found at runtime.")
                        .build();
            }

            if (matchCount > 1) {
                return RuntimeValidationResult.builder()
                        .locator(locatorString)
                        .matchCount(matchCount)
                        .currentUrl(currentUrl)
                        .environment(environment)
                        .validationType(ValidationType.RUNTIME_VALIDATED)
                        .evidenceStatus(EvidenceStatus.UNVERIFIED)
                        .message("Matches " + matchCount + " elements at runtime — not unique.")
                        .build();
            }

            // Exactly one match.
            Boolean visible;
            try {
                visible = locator.isVisible();
            } catch (Exception e) {
                LOGGER.warn("Runtime visibility check failed for locator [{}]: {}", locatorString, e.getMessage());
                return RuntimeValidationResult.builder()
                        .locator(locatorString)
                        .matchCount(1)
                        .currentUrl(currentUrl)
                        .environment(environment)
                        .validationType(ValidationType.NOT_VALIDATED)
                        .evidenceStatus(EvidenceStatus.UNVERIFIED)
                        .message("Visibility check failed: " + e.getMessage())
                        .build();
            }

            // isEnabled() is advisory only (per spec): a failure here must never affect the
            // VERIFIED/UNVERIFIED outcome, and enabled=false must never invalidate an otherwise
            // visible unique match.
            Boolean enabled;
            try {
                enabled = locator.isEnabled();
            } catch (Exception e) {
                LOGGER.debug("Runtime enabled-state check failed for locator [{}] (advisory only, ignored): {}",
                        locatorString, e.getMessage());
                enabled = null;
            }

            if (Boolean.TRUE.equals(visible)) {
                return RuntimeValidationResult.builder()
                        .locator(locatorString)
                        .matchCount(1)
                        .visible(true)
                        .enabled(enabled)
                        .currentUrl(currentUrl)
                        .environment(environment)
                        .validationType(ValidationType.RUNTIME_VALIDATED)
                        .evidenceStatus(EvidenceStatus.VERIFIED)
                        .message("Unique, visible match confirmed at runtime.")
                        .build();
            }

            return RuntimeValidationResult.builder()
                    .locator(locatorString)
                    .matchCount(1)
                    .visible(false)
                    .enabled(enabled)
                    .currentUrl(currentUrl)
                    .environment(environment)
                    .validationType(ValidationType.RUNTIME_VALIDATED)
                    .evidenceStatus(EvidenceStatus.UNVERIFIED)
                    .message("Unique match found but element is not visible.")
                    .build();

        } catch (Exception e) {
            // Absolute boundary: runtime validation must never throw into the caller/test.
            LOGGER.warn("Unexpected error during runtime locator validation: {}", e.getMessage());
            return RuntimeValidationResult.notValidated(safeLocator(candidate),
                    "Unexpected runtime validation error: " + e.getMessage());
        }
    }

    private static String safeLocator(LocatorCandidate candidate) {
        return candidate != null && candidate.getLocator() != null ? candidate.getLocator() : "";
    }
}
