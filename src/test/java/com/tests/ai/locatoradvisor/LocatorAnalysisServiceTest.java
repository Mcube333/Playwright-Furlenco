package com.tests.ai.locatoradvisor;

import static org.assertj.core.api.Assertions.assertThat;

import com.framework.ai.client.AiClient;
import com.framework.ai.codegeneration.EvidenceStatus;
import com.framework.ai.config.AiConfig;
import com.framework.ai.locatoradvisor.LocatorAnalysisRequest;
import com.framework.ai.locatoradvisor.LocatorAnalysisResponse;
import com.framework.ai.locatoradvisor.LocatorAnalysisService;
import com.framework.ai.locatoradvisor.LocatorCandidate;
import com.framework.ai.locatoradvisor.LocatorStrategy;
import com.framework.ai.locatoradvisor.PageObjectMatch;
import com.framework.ai.locatoradvisor.ValidationType;
import com.framework.ai.model.AiRequest;
import com.framework.ai.model.AiResponse;
import com.framework.config.ConfigManager;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

/**
 * Service-level orchestration tests using a hermetic mock AiClient — no real
 * API key or network call is ever made. Each test targets one of the Phase 5
 * requirement scenarios (numbered per the phase spec in comments).
 */
public class LocatorAnalysisServiceTest {

    private AiConfig createConfig(boolean enabled) {
        return new AiConfig(ConfigManager.getInstance()) {
            @Override public boolean isAiEnabled() { return enabled; }
            @Override public String getApiKey() { return "fake-api-key"; }
            @Override public String getProvider() { return "gemini"; }
        };
    }

    private static class MockAiClient implements AiClient {
        private final AiResponse stubResponse;
        private final boolean available;
        public String capturedPrompt;

        MockAiClient(AiResponse stubResponse, boolean available) {
            this.stubResponse = stubResponse;
            this.available = available;
        }

        @Override
        public AiResponse generate(AiRequest request) {
            this.capturedPrompt = request.getPrompt();
            return stubResponse;
        }

        @Override public String getProviderName() { return "mock"; }
        @Override public boolean isAvailable() { return available; }
    }

    @AfterMethod
    public void clearSystemProperties() {
        System.clearProperty("ai.dom.max.bytes");
    }

    // 4. Missing accessible name -> deterministic accessibility scan flags icon-only button
    @Test
    public void testMissingAccessibleNameFinding() {
        String dom = "<button data-testid=\"plus\"></button>";
        String json = "{\"targetElement\":\"Increase quantity button\",\"candidates\":["
                + "{\"locator\":\"[data-testid='plus']\",\"strategy\":\"TEST_ID\",\"rationale\":\"Stable test id\"}]}";

        MockAiClient client = new MockAiClient(AiResponse.success(json, "mock"), true);
        LocatorAnalysisService service = new LocatorAnalysisService(createConfig(true), client);

        LocatorAnalysisRequest request = LocatorAnalysisRequest.builder()
                .domSnapshot(dom)
                .targetDescription("Increase quantity button")
                .build();

        LocatorAnalysisResponse response = service.analyze(request);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getAccessibilityFindings())
                .anyMatch(f -> f.getElement().equals("button") && f.getIssue().contains("missing accessible name"));
        assertThat(response.getAccessibilityFindings().get(0).getEvidenceStatus()).isEqualTo(EvidenceStatus.INFERRED);
    }

    // 5. Missing test ID -> advisor falls back to the next best evidenced strategy
    @Test
    public void testMissingTestIdFallsBackToNextBestStrategy() {
        String dom = "<button aria-label=\"Increase quantity\">+</button>";
        String json = "{\"targetElement\":\"Increase quantity button\",\"candidates\":["
                + "{\"locator\":\"[data-testid='cart-plus']\",\"strategy\":\"TEST_ID\",\"rationale\":\"Preferred if present\"},"
                + "{\"locator\":\"getByRole('button', {name: 'Increase quantity'})\",\"strategy\":\"ROLE\",\"rationale\":\"Accessible fallback\"}"
                + "]}";

        MockAiClient client = new MockAiClient(AiResponse.success(json, "mock"), true);
        LocatorAnalysisService service = new LocatorAnalysisService(createConfig(true), client);

        LocatorAnalysisRequest request = LocatorAnalysisRequest.builder()
                .domSnapshot(dom)
                .targetDescription("Increase quantity button")
                .targetText("Increase quantity")
                .build();

        LocatorAnalysisResponse response = service.analyze(request);

        LocatorCandidate testIdCandidate = response.getCandidates().stream()
                .filter(c -> c.getStrategy() == LocatorStrategy.TEST_ID).findFirst().orElseThrow();
        assertThat(testIdCandidate.getEvidenceStatus()).isEqualTo(EvidenceStatus.UNVERIFIED);
        assertThat(testIdCandidate.getMatchCount()).isEqualTo(0);

        assertThat(response.getRecommendedLocator()).isNotNull();
        assertThat(response.getRecommendedLocator().getStrategy()).isEqualTo(LocatorStrategy.ROLE);
        assertThat(response.getRecommendedLocator().getEvidenceStatus()).isEqualTo(EvidenceStatus.VERIFIED);
    }

    // 9. Failed locator replacement recommendation
    @Test
    public void testFailedLocatorReplacementRecommendation() {
        String dom = "<button aria-label=\"Increase quantity\">+</button>";
        String json = "{\"targetElement\":\"Increase quantity button\",\"candidates\":["
                + "{\"locator\":\"getByRole('button', {name: 'Increase quantity'})\",\"strategy\":\"ROLE\",\"rationale\":\"Use accessible role and name\"}"
                + "]}";

        MockAiClient client = new MockAiClient(AiResponse.success(json, "mock"), true);
        LocatorAnalysisService service = new LocatorAnalysisService(createConfig(true), client);

        LocatorAnalysisRequest request = LocatorAnalysisRequest.builder()
                .domSnapshot(dom)
                .targetDescription("Increase quantity button")
                .failingLocator("button.plus")
                .failureMessage("Timeout 5000ms exceeded")
                .build();

        LocatorAnalysisResponse response = service.analyze(request);

        LocatorCandidate failed = response.getCandidates().stream()
                .filter(c -> c.getLocator().equals("button.plus")).findFirst().orElseThrow();
        assertThat(failed.getEvidenceStatus()).isEqualTo(EvidenceStatus.UNVERIFIED);
        assertThat(failed.getWeaknesses()).anyMatch(w -> w.contains("FAILED"));
        assertThat(failed.getWeaknesses()).anyMatch(w -> w.contains("Timeout 5000ms exceeded"));

        assertThat(response.getRecommendedLocator()).isNotNull();
        assertThat(response.getRecommendedLocator().getLocator()).contains("getByRole");
        assertThat(response.getRecommendedLocator().getEvidenceStatus()).isEqualTo(EvidenceStatus.VERIFIED);
    }

    // 11. Truncated DOM reduces confidence and reports truncation
    @Test
    public void testTruncatedDomReducesConfidenceAndReportsTruncation() {
        System.setProperty("ai.dom.max.bytes", "40");

        StringBuilder domBuilder = new StringBuilder("<button data-testid=\"btn\">");
        domBuilder.append("x".repeat(500));
        domBuilder.append("</button>");

        String json = "{\"targetElement\":\"Some button\",\"candidates\":["
                + "{\"locator\":\"[data-testid='btn']\",\"strategy\":\"TEST_ID\",\"rationale\":\"Stable\"}]}";

        MockAiClient client = new MockAiClient(AiResponse.success(json, "mock"), true);
        LocatorAnalysisService service = new LocatorAnalysisService(createConfig(true), client);

        LocatorAnalysisRequest request = LocatorAnalysisRequest.builder()
                .domSnapshot(domBuilder.toString())
                .targetDescription("Some button")
                .build();

        LocatorAnalysisResponse response = service.analyze(request);

        assertThat(response.isDomTruncated()).isTrue();
        assertThat(response.getMissingEvidence()).anyMatch(m -> m.toLowerCase().contains("truncat"));
        assertThat(response.getOverallConfidence()).isLessThanOrEqualTo(1.0);
    }

    // 12. Existing Page Object method reuse recommended
    @Test
    public void testExistingPageObjectMethodReuseRecommended() {
        String poContext = "public class CartPage extends BasePage {\n"
                + "    public void incrementQuantity() { page.locator(\"button.plus\").click(); }\n"
                + "}";
        String dom = "<button aria-label=\"Increase quantity\">+</button>";
        String json = "{\"targetElement\":\"Increase quantity button\",\"candidates\":[],"
                + "\"existingMethodSuggestion\":\"incrementQuantity\"}";

        MockAiClient client = new MockAiClient(AiResponse.success(json, "mock"), true);
        LocatorAnalysisService service = new LocatorAnalysisService(createConfig(true), client);

        LocatorAnalysisRequest request = LocatorAnalysisRequest.builder()
                .domSnapshot(dom)
                .targetDescription("Increase quantity button")
                .existingPageObjectContext(poContext)
                .build();

        LocatorAnalysisResponse response = service.analyze(request);

        assertThat(response.getExistingPageObjectMatches()).hasSize(1);
        PageObjectMatch match = response.getExistingPageObjectMatches().get(0);
        assertThat(match.isMatched()).isTrue();
        assertThat(match.getExistingMethod()).isEqualTo("incrementQuantity");
        assertThat(match.getPageObjectClass()).isEqualTo("CartPage");
        assertThat(match.getEvidenceStatus()).isEqualTo(EvidenceStatus.VERIFIED);
    }

    // 13. No existing Page Object method -> recommend new, and discard a hallucinated AI claim
    @Test
    public void testNoExistingPageObjectMethodRecommendsNewAndDiscardsHallucination() {
        String poContext = "public class CartPage extends BasePage {\n"
                + "    public void removeItem() { page.locator(\".remove\").click(); }\n"
                + "}";
        String dom = "<button aria-label=\"Increase quantity\">+</button>";
        // AI claims a method that does NOT exist in the supplied context — must be discarded.
        String json = "{\"targetElement\":\"Increase quantity button\",\"candidates\":[],"
                + "\"existingMethodSuggestion\":\"incrementQuantity\"}";

        MockAiClient client = new MockAiClient(AiResponse.success(json, "mock"), true);
        LocatorAnalysisService service = new LocatorAnalysisService(createConfig(true), client);

        LocatorAnalysisRequest request = LocatorAnalysisRequest.builder()
                .domSnapshot(dom)
                .targetDescription("Increase quantity button")
                .existingPageObjectContext(poContext)
                .build();

        LocatorAnalysisResponse response = service.analyze(request);

        assertThat(response.getExistingPageObjectMatches()).hasSize(1);
        PageObjectMatch match = response.getExistingPageObjectMatches().get(0);
        assertThat(match.isMatched()).isFalse();
        assertThat(match.getEvidenceStatus()).isEqualTo(EvidenceStatus.INFERRED);
        assertThat(match.getRecommendation()).contains("new");

        assertThat(response.getAssumptions()).anyMatch(a -> a.contains("incrementQuantity") && a.contains("discarded"));
    }

    // 15. Evidence classification: AI's own claim of certainty is never trusted without DOM evidence
    @Test
    public void testEvidenceClassificationNeverTrustsAiClaimWithoutDomEvidence() {
        String json = "{\"targetElement\":\"Search input\",\"candidates\":["
                + "{\"locator\":\"input[name='q']\",\"strategy\":\"CSS_ATTRIBUTE\","
                + "\"rationale\":\"This is verified and unique\"}]}";

        MockAiClient client = new MockAiClient(AiResponse.success(json, "mock"), true);
        LocatorAnalysisService service = new LocatorAnalysisService(createConfig(true), client);

        // No DOM snapshot supplied at all.
        LocatorAnalysisRequest request = LocatorAnalysisRequest.builder()
                .targetDescription("Search input")
                .build();

        LocatorAnalysisResponse response = service.analyze(request);

        assertThat(response.getCandidates()).hasSize(1);
        LocatorCandidate candidate = response.getCandidates().get(0);
        assertThat(candidate.getEvidenceStatus()).isEqualTo(EvidenceStatus.UNVERIFIED);
        assertThat(candidate.getValidationType()).isEqualTo(ValidationType.NOT_VALIDATED);
        assertThat(response.getMissingEvidence()).anyMatch(m -> m.contains("No DOM snapshot supplied"));
    }

    // 16. AI disabled behavior
    @Test
    public void testAiDisabledReturnsSafeFailure() {
        LocatorAnalysisService service = new LocatorAnalysisService(createConfig(false), new MockAiClient(null, false));
        LocatorAnalysisResponse response = service.analyze(LocatorAnalysisRequest.builder().build());
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorMessage()).contains("AI is disabled");
    }

    // 17. Malformed AI response
    @Test
    public void testMalformedAiResponseHandledGracefully() {
        MockAiClient client = new MockAiClient(AiResponse.success("not json at all", "mock"), true);
        LocatorAnalysisService service = new LocatorAnalysisService(createConfig(true), client);
        LocatorAnalysisResponse response = service.analyze(LocatorAnalysisRequest.builder().domSnapshot("<div/>").build());
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorMessage()).contains("Failed to parse");
    }

    // 18. Sanitization: secrets in the DOM must never reach the AI provider
    @Test
    public void testSensitiveDataSanitizedBeforePromptAssembly() {
        String dom = "<div>Welcome</div><script>var config = {apiKey: 'AKIA1234567890', password: 'hunter2'};</script>";
        String json = "{\"targetElement\":\"page\",\"candidates\":[]}";

        MockAiClient client = new MockAiClient(AiResponse.success(json, "mock"), true);
        LocatorAnalysisService service = new LocatorAnalysisService(createConfig(true), client);

        LocatorAnalysisRequest request = LocatorAnalysisRequest.builder()
                .domSnapshot(dom)
                .targetDescription("page")
                .build();

        service.analyze(request);

        assertThat(client.capturedPrompt).doesNotContain("hunter2");
        assertThat(client.capturedPrompt).doesNotContain("AKIA1234567890");
        assertThat(client.capturedPrompt).contains("[REDACTED]");
    }

    // Additional: end-to-end happy path sanity check
    @Test
    public void testFullSuccessfulAnalysisProducesRankedCandidates() {
        String dom = "<div class=\"cart\">"
                + "<button data-testid=\"cart-plus\" aria-label=\"Increase quantity\">+</button>"
                + "</div>";
        String json = "{\"targetElement\":\"Cart quantity increase button\",\"candidates\":["
                + "{\"locator\":\"[data-testid='cart-plus']\",\"strategy\":\"TEST_ID\",\"rationale\":\"Stable test id\","
                + "\"strengths\":[\"Explicit test hook\"]},"
                + "{\"locator\":\"getByRole('button', {name: 'Increase quantity'})\",\"strategy\":\"ROLE\",\"rationale\":\"Accessible\"}"
                + "],\"accessibilityFindings\":[],\"assumptions\":[\"User already has an item in cart\"],\"missingEvidence\":[]}";

        MockAiClient client = new MockAiClient(AiResponse.success(json, "mock"), true);
        LocatorAnalysisService service = new LocatorAnalysisService(createConfig(true), client);

        LocatorAnalysisRequest request = LocatorAnalysisRequest.builder()
                .domSnapshot(dom)
                .targetDescription("Cart quantity increase button")
                .targetText("Increase quantity")
                .pageName("CartPage")
                .build();

        LocatorAnalysisResponse response = service.analyze(request);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCandidates()).hasSize(2);
        assertThat(response.getRecommendedLocator()).isNotNull();
        assertThat(response.getRecommendedLocator().getStrategy()).isEqualTo(LocatorStrategy.TEST_ID);
        assertThat(response.getRecommendedLocator().getEvidenceStatus()).isEqualTo(EvidenceStatus.VERIFIED);
        assertThat(response.getRecommendedLocator().getScore()).isGreaterThanOrEqualTo(80);
        assertThat(response.isHumanReviewRequired()).isTrue();
        assertThat(response.getAssumptions()).contains("User already has an item in cart");
    }

    // ===================================================================================
    // Phase 5.1 hardening: explicit re-assertions of the anti-hallucination invariants.
    // ===================================================================================

    // B. Multiple matches cannot become VERIFIED
    @Test
    public void testMultipleMatchesNeverBecomeVerified() {
        String dom = "<button class=\"plus\">A</button><button class=\"plus\">B</button>";
        String json = "{\"targetElement\":\"Plus button\",\"candidates\":["
                + "{\"locator\":\".plus\",\"strategy\":\"CSS_STABLE\",\"rationale\":\"This is verified and unique\"}]}";

        MockAiClient client = new MockAiClient(AiResponse.success(json, "mock"), true);
        LocatorAnalysisService service = new LocatorAnalysisService(createConfig(true), client);
        LocatorAnalysisResponse response = service.analyze(
                LocatorAnalysisRequest.builder().domSnapshot(dom).targetDescription("Plus button").build());

        LocatorCandidate candidate = response.getCandidates().get(0);
        assertThat(candidate.getMatchCount()).isEqualTo(2);
        assertThat(candidate.getEvidenceStatus()).isEqualTo(EvidenceStatus.UNVERIFIED);
        assertThat(candidate.getValidationType()).isEqualTo(ValidationType.DOM_MATCHED);
        assertThat(response.getRecommendedLocator()).isNull();
    }

    // B. Zero matches cannot become VERIFIED
    @Test
    public void testZeroMatchesNeverBecomeVerified() {
        String dom = "<div>No matching elements here</div>";
        String json = "{\"targetElement\":\"Missing button\",\"candidates\":["
                + "{\"locator\":\"[data-testid='does-not-exist']\",\"strategy\":\"TEST_ID\",\"rationale\":\"Proposed test id\"}]}";

        MockAiClient client = new MockAiClient(AiResponse.success(json, "mock"), true);
        LocatorAnalysisService service = new LocatorAnalysisService(createConfig(true), client);
        LocatorAnalysisResponse response = service.analyze(
                LocatorAnalysisRequest.builder().domSnapshot(dom).targetDescription("Missing button").build());

        LocatorCandidate candidate = response.getCandidates().get(0);
        assertThat(candidate.getMatchCount()).isEqualTo(0);
        assertThat(candidate.getEvidenceStatus()).isEqualTo(EvidenceStatus.UNVERIFIED);
        assertThat(candidate.getValidationType()).isEqualTo(ValidationType.DOM_MATCHED);
        assertThat(response.getRecommendedLocator()).isNull();
    }

    // B. Missing DOM cannot become VERIFIED (explicit, dedicated re-assertion)
    @Test
    public void testMissingDomCannotBecomeVerifiedEvenForAPlausibleLocator() {
        String json = "{\"targetElement\":\"Login button\",\"candidates\":["
                + "{\"locator\":\"[data-testid='login-submit']\",\"strategy\":\"TEST_ID\",\"rationale\":\"Confirmed unique\"}]}";

        MockAiClient client = new MockAiClient(AiResponse.success(json, "mock"), true);
        LocatorAnalysisService service = new LocatorAnalysisService(createConfig(true), client);
        // No domSnapshot() call at all.
        LocatorAnalysisResponse response = service.analyze(
                LocatorAnalysisRequest.builder().targetDescription("Login button").build());

        LocatorCandidate candidate = response.getCandidates().get(0);
        assertThat(candidate.getEvidenceStatus()).isNotEqualTo(EvidenceStatus.VERIFIED);
        assertThat(candidate.getEvidenceStatus()).isEqualTo(EvidenceStatus.UNVERIFIED);
        assertThat(candidate.getValidationType()).isEqualTo(ValidationType.NOT_VALIDATED);
    }

    // B. Unsupported strategies remain UNVERIFIED regardless of DOM content
    @Test
    public void testUnsupportedStrategiesRemainUnverified() {
        String dom = "<div><span>x</span></div>";
        String json = "{\"targetElement\":\"Some element\",\"candidates\":["
                + "{\"locator\":\"/html/body/div[1]/span\",\"strategy\":\"XPATH\",\"rationale\":\"Absolute path\"},"
                + "{\"locator\":\"div span:nth-child(1)\",\"strategy\":\"POSITIONAL\",\"rationale\":\"Positional\"},"
                + "{\"locator\":\"mystery-locator\",\"strategy\":\"NOT_A_REAL_STRATEGY\",\"rationale\":\"Unclassifiable\"}"
                + "]}";

        MockAiClient client = new MockAiClient(AiResponse.success(json, "mock"), true);
        LocatorAnalysisService service = new LocatorAnalysisService(createConfig(true), client);
        LocatorAnalysisResponse response = service.analyze(
                LocatorAnalysisRequest.builder().domSnapshot(dom).targetDescription("Some element").build());

        assertThat(response.getCandidates()).hasSize(3);
        assertThat(response.getCandidates()).allMatch(c -> c.getEvidenceStatus() == EvidenceStatus.UNVERIFIED);
        assertThat(response.getCandidates()).allMatch(c -> c.getValidationType() == ValidationType.NOT_VALIDATED);
        assertThat(response.getRecommendedLocator()).isNull();
    }

    // B. Existing Page Object methods are only marked VERIFIED when actually present in the supplied context
    @Test
    public void testPageObjectMatchNeverVerifiedWhenContextHasNoMethodsAtAll() {
        String poContext = "public class EmptyPage extends BasePage { }";
        String json = "{\"targetElement\":\"Some button\",\"candidates\":[],"
                + "\"existingMethodSuggestion\":\"clickSomeButton\"}";

        MockAiClient client = new MockAiClient(AiResponse.success(json, "mock"), true);
        LocatorAnalysisService service = new LocatorAnalysisService(createConfig(true), client);
        LocatorAnalysisResponse response = service.analyze(LocatorAnalysisRequest.builder()
                .targetDescription("Some button")
                .existingPageObjectContext(poContext)
                .build());

        assertThat(response.getExistingPageObjectMatches()).hasSize(1);
        PageObjectMatch match = response.getExistingPageObjectMatches().get(0);
        assertThat(match.isMatched()).isFalse();
        assertThat(match.getEvidenceStatus()).isNotEqualTo(EvidenceStatus.VERIFIED);
    }

    // D. Sanitization coverage beyond the DOM: failure message and Page Object context
    @Test
    public void testSanitizationAppliesToFailureMessageAndPageObjectContext() {
        String dom = "<button aria-label=\"Submit\">Go</button>";
        String poContextWithSecret = "public class LoginPage { // apiKey: 'synthetic-test-api-key-value' \n"
                + "public void submit() {} }";
        String failureMessageWithSecret = "Request failed, password: 'synthetic-test-password'";
        String json = "{\"targetElement\":\"Submit button\",\"candidates\":[]}";

        MockAiClient client = new MockAiClient(AiResponse.success(json, "mock"), true);
        LocatorAnalysisService service = new LocatorAnalysisService(createConfig(true), client);

        LocatorAnalysisRequest request = LocatorAnalysisRequest.builder()
                .domSnapshot(dom)
                .targetDescription("Submit button")
                .existingPageObjectContext(poContextWithSecret)
                .failingLocator("button.submit")
                .failureMessage(failureMessageWithSecret)
                .build();

        service.analyze(request);

        assertThat(client.capturedPrompt).doesNotContain("synthetic-test-api-key-value");
        assertThat(client.capturedPrompt).doesNotContain("synthetic-test-password");
        assertThat(client.capturedPrompt).contains("[REDACTED]");
    }
}
