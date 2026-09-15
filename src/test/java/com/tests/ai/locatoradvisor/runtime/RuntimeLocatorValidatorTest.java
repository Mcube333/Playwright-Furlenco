package com.tests.ai.locatoradvisor.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.framework.ai.codegeneration.EvidenceStatus;
import com.framework.ai.config.AiConfig;
import com.framework.ai.locatoradvisor.LocatorAnalysisReporter;
import com.framework.ai.locatoradvisor.LocatorAnalysisResponse;
import com.framework.ai.locatoradvisor.LocatorCandidate;
import com.framework.ai.locatoradvisor.LocatorStrategy;
import com.framework.ai.locatoradvisor.ValidationType;
import com.framework.ai.locatoradvisor.runtime.RuntimeEnvironmentGuard;
import com.framework.ai.locatoradvisor.runtime.RuntimeLocatorValidator;
import com.framework.ai.locatoradvisor.runtime.RuntimeValidationResult;
import com.framework.ai.sanitizer.SensitiveDataSanitizer;
import com.framework.config.ConfigManager;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.testng.annotations.Test;

/**
 * Phase 6: RuntimeLocatorValidator tests. All Page/Locator instances are JDK
 * {@link java.lang.reflect.Proxy}-backed test doubles ({@link RuntimePageTestDouble},
 * {@link RuntimeLocatorTestDouble}) — no real browser, no Mockito, no new dependency.
 *
 * The confirmed-safe staging host ({@code https://www.stag.furlenco.com}) is used only as the
 * URL string returned by the fake Page so tests reach the locator-checking logic — no real
 * navigation, no real network call, nothing is opened.
 */
public class RuntimeLocatorValidatorTest {

    private static final String SAFE_URL = "https://www.stag.furlenco.com";

    private AiConfig enabledConfig() {
        return new AiConfig(ConfigManager.getInstance()) {
            @Override
            public boolean isLocatorRuntimeValidationEnabled() {
                return true;
            }
        };
    }

    private RuntimeLocatorValidator enabledValidator() {
        AiConfig cfg = enabledConfig();
        return new RuntimeLocatorValidator(cfg, new RuntimeEnvironmentGuard(cfg));
    }

    private LocatorCandidate candidate(String locator) {
        return LocatorCandidate.builder().locator(locator).strategy(LocatorStrategy.CSS_STABLE).build();
    }

    private Page pageReturning(Locator locator) {
        return RuntimePageTestDouble.builder().url(SAFE_URL).closed(false).locator(locator).build();
    }

    // =====================================================================
    // RUNTIME
    // =====================================================================

    // 12. null Page
    @Test
    public void testNullPageReturnsNotValidated() {
        RuntimeValidationResult result = enabledValidator().validate(null, candidate("[data-testid='x']"));
        assertThat(result.getValidationType()).isEqualTo(ValidationType.NOT_VALIDATED);
        assertThat(result.getEvidenceStatus()).isEqualTo(EvidenceStatus.UNVERIFIED);
        assertThat(result.getMessage()).contains("No Playwright Page");
    }

    // 13. closed Page
    @Test
    public void testClosedPageReturnsNotValidated() {
        Page page = RuntimePageTestDouble.builder().closed(true).url(SAFE_URL).build();
        RuntimeValidationResult result = enabledValidator().validate(page, candidate("[data-testid='x']"));
        assertThat(result.getValidationType()).isEqualTo(ValidationType.NOT_VALIDATED);
        assertThat(result.getMessage()).contains("closed");
    }

    // 14. null candidate
    @Test
    public void testNullCandidateReturnsNotValidated() {
        Page page = pageReturning(RuntimeLocatorTestDouble.builder().count(1).visible(true).build());
        RuntimeValidationResult result = enabledValidator().validate(page, null);
        assertThat(result.getValidationType()).isEqualTo(ValidationType.NOT_VALIDATED);
        assertThat(result.getMessage()).contains("No locator candidate supplied");
    }

    // 15. invalid locator
    @Test
    public void testInvalidLocatorReturnsNotValidated() {
        Page page = RuntimePageTestDouble.builder().url(SAFE_URL).closed(false)
                .locatorThrows(new RuntimeException("Unsupported selector syntax"))
                .build();
        RuntimeValidationResult result = enabledValidator().validate(page, candidate(">>>invalid"));
        assertThat(result.getValidationType()).isEqualTo(ValidationType.NOT_VALIDATED);
        assertThat(result.getEvidenceStatus()).isEqualTo(EvidenceStatus.UNVERIFIED);
        assertThat(result.getMessage()).contains("Invalid or unresolvable locator");
    }

    // 16. zero matches
    @Test
    public void testZeroMatchesResultsInUnverified() {
        Page page = pageReturning(RuntimeLocatorTestDouble.builder().count(0).build());
        RuntimeValidationResult result = enabledValidator().validate(page, candidate("[data-testid='missing']"));
        assertThat(result.getMatchCount()).isEqualTo(0);
        assertThat(result.getEvidenceStatus()).isEqualTo(EvidenceStatus.UNVERIFIED);
        assertThat(result.getValidationType()).isEqualTo(ValidationType.RUNTIME_VALIDATED);
    }

    // 17. exactly one match + visible
    @Test
    public void testOneMatchAndVisibleResultsInVerified() {
        Page page = pageReturning(RuntimeLocatorTestDouble.builder().count(1).visible(true).enabled(true).build());
        RuntimeValidationResult result = enabledValidator().validate(page, candidate("[data-testid='cart-plus']"));
        assertThat(result.getMatchCount()).isEqualTo(1);
        assertThat(result.getVisible()).isTrue();
        assertThat(result.getEvidenceStatus()).isEqualTo(EvidenceStatus.VERIFIED);
        assertThat(result.getValidationType()).isEqualTo(ValidationType.RUNTIME_VALIDATED);
    }

    // 18. exactly one match + invisible
    @Test
    public void testOneMatchButInvisibleResultsInUnverified() {
        Page page = pageReturning(RuntimeLocatorTestDouble.builder().count(1).visible(false).build());
        RuntimeValidationResult result = enabledValidator().validate(page, candidate("[data-testid='hidden-el']"));
        assertThat(result.getMatchCount()).isEqualTo(1);
        assertThat(result.getVisible()).isFalse();
        assertThat(result.getEvidenceStatus()).isEqualTo(EvidenceStatus.UNVERIFIED);
        assertThat(result.getValidationType()).isEqualTo(ValidationType.RUNTIME_VALIDATED);
        assertThat(result.getMessage()).contains("not visible");
    }

    // 19. multiple matches
    @Test
    public void testMultipleMatchesResultsInUnverified() {
        Page page = pageReturning(RuntimeLocatorTestDouble.builder().count(3).build());
        RuntimeValidationResult result = enabledValidator().validate(page, candidate(".plus"));
        assertThat(result.getMatchCount()).isEqualTo(3);
        assertThat(result.getEvidenceStatus()).isEqualTo(EvidenceStatus.UNVERIFIED);
        assertThat(result.getValidationType()).isEqualTo(ValidationType.RUNTIME_VALIDATED);
        assertThat(result.getMessage()).contains("not unique");
    }

    // 20. enabled=true
    @Test
    public void testEnabledTrueIsCarriedInResult() {
        Page page = pageReturning(RuntimeLocatorTestDouble.builder().count(1).visible(true).enabled(true).build());
        RuntimeValidationResult result = enabledValidator().validate(page, candidate("button.submit"));
        assertThat(result.getEnabled()).isTrue();
    }

    // 21. enabled=false remains advisory (does not invalidate an otherwise visible unique match)
    @Test
    public void testEnabledFalseDoesNotInvalidateVisibleUniqueMatch() {
        Page page = pageReturning(RuntimeLocatorTestDouble.builder().count(1).visible(true).enabled(false).build());
        RuntimeValidationResult result = enabledValidator().validate(page, candidate("button.submit"));
        assertThat(result.getEnabled()).isFalse();
        assertThat(result.getEvidenceStatus()).isEqualTo(EvidenceStatus.VERIFIED);
        assertThat(result.getValidationType()).isEqualTo(ValidationType.RUNTIME_VALIDATED);
    }

    // 22. runtime exception during locator()
    @Test
    public void testExceptionDuringLocatorResolutionReturnsNotValidated() {
        Page page = RuntimePageTestDouble.builder().url(SAFE_URL).closed(false)
                .locatorThrows(new RuntimeException("selector engine failure"))
                .build();
        RuntimeValidationResult result = enabledValidator().validate(page, candidate("weird::selector"));
        assertThat(result.getValidationType()).isEqualTo(ValidationType.NOT_VALIDATED);
    }

    // 23. runtime exception during count()
    @Test
    public void testExceptionDuringCountReturnsNotValidated() {
        Page page = pageReturning(RuntimeLocatorTestDouble.builder().countThrows(new RuntimeException("count failed")).build());
        RuntimeValidationResult result = enabledValidator().validate(page, candidate("[data-testid='x']"));
        assertThat(result.getValidationType()).isEqualTo(ValidationType.NOT_VALIDATED);
        assertThat(result.getEvidenceStatus()).isEqualTo(EvidenceStatus.UNVERIFIED);
    }

    // 24. runtime exception during isVisible()
    @Test
    public void testExceptionDuringIsVisibleReturnsNotValidated() {
        Page page = pageReturning(RuntimeLocatorTestDouble.builder().count(1)
                .isVisibleThrows(new RuntimeException("visibility check failed")).build());
        RuntimeValidationResult result = enabledValidator().validate(page, candidate("[data-testid='x']"));
        assertThat(result.getValidationType()).isEqualTo(ValidationType.NOT_VALIDATED);
        assertThat(result.getMessage()).contains("Visibility check failed");
    }

    // 25. runtime exception during isEnabled() — advisory only, must NOT fail the overall result
    @Test
    public void testExceptionDuringIsEnabledIsAdvisoryAndDoesNotFailValidation() {
        Page page = pageReturning(RuntimeLocatorTestDouble.builder().count(1).visible(true)
                .isEnabledThrows(new RuntimeException("enabled check failed")).build());
        RuntimeValidationResult result = enabledValidator().validate(page, candidate("[data-testid='x']"));
        assertThat(result.getEvidenceStatus()).isEqualTo(EvidenceStatus.VERIFIED);
        assertThat(result.getValidationType()).isEqualTo(ValidationType.RUNTIME_VALIDATED);
        assertThat(result.getEnabled()).isNull();
    }

    // 26. runtime result contains URL
    @Test
    public void testResultContainsCurrentUrl() {
        Page page = pageReturning(RuntimeLocatorTestDouble.builder().count(1).visible(true).build());
        RuntimeValidationResult result = enabledValidator().validate(page, candidate("[data-testid='x']"));
        assertThat(result.getCurrentUrl()).isEqualTo(SAFE_URL);
    }

    // 27. runtime result contains environment
    @Test
    public void testResultContainsEnvironment() {
        Page page = pageReturning(RuntimeLocatorTestDouble.builder().count(1).visible(true).build());
        RuntimeValidationResult result = enabledValidator().validate(page, candidate("[data-testid='x']"));
        assertThat(result.getEnvironment()).isEqualTo(ConfigManager.getInstance().getEnvironment());
    }

    // 28. runtime result contains timestamp
    @Test
    public void testResultContainsTimestamp() {
        Page page = pageReturning(RuntimeLocatorTestDouble.builder().count(1).visible(true).build());
        RuntimeValidationResult result = enabledValidator().validate(page, candidate("[data-testid='x']"));
        assertThat(result.getTimestamp()).isNotNull();
    }

    // 29. runtime validation uses RUNTIME_VALIDATED (never NOT_VALIDATED/DOM_MATCHED) whenever it actually ran
    @Test
    public void testSuccessfulRuntimeChecksAlwaysUseRuntimeValidatedType() {
        Page onePage = pageReturning(RuntimeLocatorTestDouble.builder().count(1).visible(true).build());
        Page zeroPage = pageReturning(RuntimeLocatorTestDouble.builder().count(0).build());
        Page manyPage = pageReturning(RuntimeLocatorTestDouble.builder().count(2).build());

        assertThat(enabledValidator().validate(onePage, candidate("a")).getValidationType()).isEqualTo(ValidationType.RUNTIME_VALIDATED);
        assertThat(enabledValidator().validate(zeroPage, candidate("b")).getValidationType()).isEqualTo(ValidationType.RUNTIME_VALIDATED);
        assertThat(enabledValidator().validate(manyPage, candidate("c")).getValidationType()).isEqualTo(ValidationType.RUNTIME_VALIDATED);
    }

    // 30. runtime evidence is independent from Phase 5 evidence — the original candidate is never mutated
    @Test
    public void testRuntimeEvidenceIsIndependentFromPhase5EvidenceAndCandidateIsNeverMutated() {
        LocatorCandidate phase5Candidate = LocatorCandidate.builder()
                .locator("[data-testid='cart-plus']")
                .strategy(LocatorStrategy.TEST_ID)
                .evidenceStatus(EvidenceStatus.UNVERIFIED)
                .validationType(ValidationType.DOM_MATCHED)
                .matchCount(0)
                .build();

        Page page = pageReturning(RuntimeLocatorTestDouble.builder().count(1).visible(true).build());
        RuntimeValidationResult runtimeResult = enabledValidator().validate(page, phase5Candidate);

        // Runtime result reflects the live page (VERIFIED)...
        assertThat(runtimeResult.getEvidenceStatus()).isEqualTo(EvidenceStatus.VERIFIED);
        assertThat(runtimeResult.getValidationType()).isEqualTo(ValidationType.RUNTIME_VALIDATED);

        // ...while the original Phase 5 LocatorCandidate object is completely untouched.
        assertThat(phase5Candidate.getEvidenceStatus()).isEqualTo(EvidenceStatus.UNVERIFIED);
        assertThat(phase5Candidate.getValidationType()).isEqualTo(ValidationType.DOM_MATCHED);
        assertThat(phase5Candidate.getMatchCount()).isEqualTo(0);
    }

    // 31. AI cannot override runtime evidence: a candidate the AI/Phase 5 pipeline marked VERIFIED
    //     must NOT make the runtime check report VERIFIED if the live page disagrees.
    @Test
    public void testAiClaimedVerifiedCandidateCannotForceRuntimeVerified() {
        LocatorCandidate aiClaimedVerified = LocatorCandidate.builder()
                .locator("[data-testid='cart-plus']")
                .strategy(LocatorStrategy.TEST_ID)
                .evidenceStatus(EvidenceStatus.VERIFIED) // Phase 5 already said VERIFIED
                .validationType(ValidationType.DOM_MATCHED)
                .matchCount(1)
                .build();

        Page page = pageReturning(RuntimeLocatorTestDouble.builder().count(0).build()); // but live page finds nothing
        RuntimeValidationResult result = enabledValidator().validate(page, aiClaimedVerified);

        assertThat(result.getEvidenceStatus()).isEqualTo(EvidenceStatus.UNVERIFIED);
        assertThat(result.getMatchCount()).isEqualTo(0);
    }

    // 32. runtime validation failure does not throw into caller
    @Test
    public void testUnexpectedExceptionNeverPropagatesToCaller() {
        Page page = RuntimePageTestDouble.builder()
                .isClosedThrows(new RuntimeException("simulated catastrophic failure"))
                .build();
        RuntimeValidationResult result = enabledValidator().validate(page, candidate("[data-testid='x']"));
        assertThat(result.getValidationType()).isEqualTo(ValidationType.NOT_VALIDATED);
    }

    // 33. runtime validation disabled by default (real, unstubbed AiConfig — confirms the shipped
    //     qa.properties default, not just a test stub)
    @Test
    public void testRuntimeValidationDisabledByDefault() {
        RuntimeLocatorValidator defaultValidator = new RuntimeLocatorValidator();
        assertThat(defaultValidator.isRuntimeValidationEnabled()).isFalse();

        Page page = pageReturning(RuntimeLocatorTestDouble.builder().count(1).visible(true).build());
        RuntimeValidationResult result = defaultValidator.validate(page, candidate("[data-testid='x']"));
        assertThat(result.getValidationType()).isEqualTo(ValidationType.NOT_VALIDATED);
        assertThat(result.getMessage()).contains("disabled");
    }

    // Additional: environment guard denial surfaces as NOT_VALIDATED, not an exception
    @Test
    public void testEnvironmentGuardDenialReturnsNotValidated() {
        AiConfig cfg = enabledConfig();
        RuntimeLocatorValidator validator = new RuntimeLocatorValidator(cfg, new RuntimeEnvironmentGuard(cfg));
        Page productionLookingPage = RuntimePageTestDouble.builder().url("https://www.furlenco.com").closed(false).build();

        RuntimeValidationResult result = validator.validate(productionLookingPage, candidate("[data-testid='x']"));
        assertThat(result.getValidationType()).isEqualTo(ValidationType.NOT_VALIDATED);
        assertThat(result.getEvidenceStatus()).isEqualTo(EvidenceStatus.UNVERIFIED);
        assertThat(result.getMessage()).contains("environment guard");
    }

    // =====================================================================
    // REPORTING
    // =====================================================================

    // 34. existing reporter output remains unchanged without runtime result
    @Test
    public void testReporterOutputUnchangedWithoutRuntimeResult() {
        LocatorAnalysisResponse response = LocatorAnalysisResponse.builder()
                .success(true).targetElement("Cart button").build();

        String withoutOverload = LocatorAnalysisReporter.buildMarkdownReport(response);
        String withNullRuntime = LocatorAnalysisReporter.buildMarkdownReport(response, null);

        assertThat(withNullRuntime).isEqualTo(withoutOverload);
        assertThat(withNullRuntime).doesNotContain("## Runtime Validation");
    }

    // 35. runtime section appears when runtime result is supplied
    @Test
    public void testRuntimeSectionAppearsWhenRuntimeResultSupplied() {
        LocatorAnalysisResponse response = LocatorAnalysisResponse.builder()
                .success(true).targetElement("Cart button").build();
        RuntimeValidationResult runtimeResult = RuntimeValidationResult.builder()
                .locator("[data-testid='cart-plus']").matchCount(1).visible(true).enabled(true)
                .currentUrl(SAFE_URL).environment("staging")
                .validationType(ValidationType.RUNTIME_VALIDATED).evidenceStatus(EvidenceStatus.VERIFIED)
                .message("Unique, visible match confirmed at runtime.")
                .build();

        String md = LocatorAnalysisReporter.buildMarkdownReport(response, runtimeResult);
        assertThat(md).contains("## Runtime Validation");
    }

    // 36. runtime fields render correctly
    @Test
    public void testRuntimeFieldsRenderCorrectlyInReport() {
        RuntimeValidationResult runtimeResult = RuntimeValidationResult.builder()
                .locator("[data-testid='cart-plus']").matchCount(1).visible(true).enabled(false)
                .currentUrl(SAFE_URL).environment("staging")
                .validationType(ValidationType.RUNTIME_VALIDATED).evidenceStatus(EvidenceStatus.VERIFIED)
                .message("Unique, visible match confirmed at runtime.")
                .build();
        LocatorAnalysisResponse response = LocatorAnalysisResponse.builder().success(true).targetElement("x").build();

        String md = LocatorAnalysisReporter.buildMarkdownReport(response, runtimeResult);

        assertThat(md).contains("[data-testid='cart-plus']");
        assertThat(md).contains("| Match Count | 1 |");
        assertThat(md).contains("| Visible | true |");
        assertThat(md).contains("| Enabled | false |");
        assertThat(md).contains(SAFE_URL);
        assertThat(md).contains("| Environment | staging |");
        assertThat(md).contains("VERIFIED");
        assertThat(md).contains("RUNTIME_VALIDATED");
        assertThat(md).contains("Unique, visible match confirmed at runtime.");
    }

    // 37. Phase 5 evidence remains visible separately (not overwritten by runtime evidence)
    @Test
    public void testPhase5EvidenceRemainsVisibleSeparatelyFromRuntimeEvidence() {
        LocatorCandidate phase5Candidate = LocatorCandidate.builder()
                .locator("[data-testid='cart-plus']")
                .strategy(LocatorStrategy.TEST_ID)
                .evidenceStatus(EvidenceStatus.UNVERIFIED)
                .validationType(ValidationType.DOM_MATCHED)
                .matchCount(0)
                .build();
        LocatorAnalysisResponse response = LocatorAnalysisResponse.builder()
                .success(true).targetElement("Cart button").addCandidate(phase5Candidate).build();

        RuntimeValidationResult runtimeResult = RuntimeValidationResult.builder()
                .locator("[data-testid='cart-plus']").matchCount(1).visible(true)
                .currentUrl(SAFE_URL).environment("staging")
                .validationType(ValidationType.RUNTIME_VALIDATED).evidenceStatus(EvidenceStatus.VERIFIED)
                .message("Unique, visible match confirmed at runtime.")
                .build();

        String md = LocatorAnalysisReporter.buildMarkdownReport(response, runtimeResult);

        // Phase 5 (DOM) evidence still shown in the Candidate Locators table...
        assertThat(md).contains("UNVERIFIED / DOM_MATCHED");
        // ...and Phase 6 (runtime) evidence shown separately in its own section — neither overwrites the other.
        assertThat(md).contains("VERIFIED");
        assertThat(md).contains("RUNTIME_VALIDATED");
    }

    // =====================================================================
    // SECURITY
    // =====================================================================

    // 38. sanitized DOM does not expose test secret (reaffirms Phase 5.2 hardening in this context;
    //     RuntimeLocatorValidator itself never captures DOM — see class Javadoc — this proves the
    //     reused SensitiveDataSanitizer contract still holds if a caller ever combines the two)
    @Test
    public void testSanitizedDomDoesNotExposeTestSecret() {
        String hypotheticalRuntimeDom = "<input authorization=\"TEST_RUNTIME_AUTH_SECRET\" />";
        String sanitized = SensitiveDataSanitizer.sanitize(hypotheticalRuntimeDom);
        assertThat(sanitized).doesNotContain("TEST_RUNTIME_AUTH_SECRET");
    }

    // 39. runtime validator does not log/echo raw DOM — structural: RuntimeLocatorValidator never
    //     calls DomContextExtractor or Page.content(), so no result message can ever contain markup
    @Test
    public void testRuntimeResultMessagesNeverContainDomMarkup() {
        Page zeroPage = pageReturning(RuntimeLocatorTestDouble.builder().count(0).build());
        Page invalidPage = RuntimePageTestDouble.builder().url(SAFE_URL).closed(false)
                .locatorThrows(new RuntimeException("bad selector")).build();

        RuntimeValidationResult r1 = enabledValidator().validate(zeroPage, candidate("[data-testid='x']"));
        RuntimeValidationResult r2 = enabledValidator().validate(invalidPage, candidate(">>bad"));
        RuntimeValidationResult r3 = enabledValidator().validate(null, candidate("[data-testid='x']"));

        assertThat(r1.getMessage()).doesNotContain("<");
        assertThat(r2.getMessage()).doesNotContain("<");
        assertThat(r3.getMessage()).doesNotContain("<");
    }

    // 40. no secret/token/cookie values appear in the runtime report section for a normal result
    @Test
    public void testNoSecretTokenOrCookieValuesAppearInRuntimeReport() {
        RuntimeValidationResult runtimeResult = RuntimeValidationResult.builder()
                .locator("[data-testid='cart-plus']").matchCount(1).visible(true).enabled(true)
                .currentUrl(SAFE_URL).environment("staging")
                .validationType(ValidationType.RUNTIME_VALIDATED).evidenceStatus(EvidenceStatus.VERIFIED)
                .message("Unique, visible match confirmed at runtime.")
                .build();
        LocatorAnalysisResponse response = LocatorAnalysisResponse.builder().success(true).targetElement("x").build();

        String md = LocatorAnalysisReporter.buildMarkdownReport(response, runtimeResult);

        assertThat(md.toLowerCase()).doesNotContain("password");
        assertThat(md.toLowerCase()).doesNotContain("authorization:");
        assertThat(md.toLowerCase()).doesNotContain("cookie:");
        assertThat(md).doesNotContain("[REDACTED]"); // nothing sensitive was ever present to redact in the first place
    }
}
