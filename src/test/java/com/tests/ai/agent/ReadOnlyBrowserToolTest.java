package com.tests.ai.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.framework.ai.agent.AgentBrowserObservation;
import com.framework.ai.agent.LocatorObservation;
import com.framework.ai.agent.ReadOnlyBrowserTool;
import com.microsoft.playwright.Page;
import com.tests.ai.locatoradvisor.runtime.RuntimeLocatorTestDouble;
import java.util.List;
import org.testng.annotations.Test;

/**
 * Phase 8 Step 4: focused tests for {@link ReadOnlyBrowserTool}. Hermetic throughout — no real
 * browser, no real network call. {@link ReadOnlyBrowserPageTestDouble} is a JDK {@link java.lang.reflect.Proxy}
 * double combining exactly the read-only Page surface this tool needs; {@link RuntimeLocatorTestDouble}
 * (the existing Phase 6 double) is reused as-is for {@code Locator}.
 */
public class ReadOnlyBrowserToolTest {

    private final ReadOnlyBrowserTool tool = new ReadOnlyBrowserTool();

    // =====================================================================
    // Basic observation
    // =====================================================================

    @Test
    public void testValidPageUrlObserved() {
        Page page = ReadOnlyBrowserPageTestDouble.builder().closed(false).url("https://www.stag.furlenco.com/cart").build();

        AgentBrowserObservation observation = tool.observe(page);

        assertThat(observation.getUrl()).isEqualTo("https://www.stag.furlenco.com/cart");
    }

    @Test
    public void testValidPageTitleObserved() {
        Page page = ReadOnlyBrowserPageTestDouble.builder().closed(false).title("Cart").build();

        assertThat(tool.observe(page).getTitle()).isEqualTo("Cart");
    }

    @Test
    public void testValidPageDomObserved() {
        Page page = ReadOnlyBrowserPageTestDouble.builder().closed(false).content("<div>checkout</div>").build();

        assertThat(tool.observe(page).getDomSnapshot()).isEqualTo("<div>checkout</div>");
    }

    @Test
    public void testValidPageAvailableTrue() {
        Page page = ReadOnlyBrowserPageTestDouble.builder().closed(false).build();

        assertThat(tool.observe(page).isPageAvailable()).isTrue();
    }

    @Test
    public void testLocatorCountObserved() {
        Page page = ReadOnlyBrowserPageTestDouble.builder().closed(false)
                .locatorResult(RuntimeLocatorTestDouble.builder().count(2).visible(true).build())
                .build();

        LocatorObservation locatorObservation = tool.observeLocator(page, "[data-testid='checkout']");

        assertThat(locatorObservation.getCount()).isEqualTo(2);
    }

    @Test
    public void testLocatorVisibilityObserved() {
        Page page = ReadOnlyBrowserPageTestDouble.builder().closed(false)
                .locatorResult(RuntimeLocatorTestDouble.builder().count(1).visible(true).build())
                .build();

        assertThat(tool.observeLocator(page, "x").getVisible()).isTrue();
    }

    @Test
    public void testLocatorEnabledStateObserved() {
        Page page = ReadOnlyBrowserPageTestDouble.builder().closed(false)
                .locatorResult(RuntimeLocatorTestDouble.builder().count(1).visible(true).enabled(false).build())
                .build();

        assertThat(tool.observeLocator(page, "x").getEnabled()).isFalse();
    }

    // =====================================================================
    // Lifecycle safety
    // =====================================================================

    @Test
    public void testNullPage() {
        AgentBrowserObservation observation = tool.observe(null);

        assertThat(observation.isPageAvailable()).isFalse();
        assertThat(observation.getError()).isNotEmpty();
        assertThat(observation.getUrl()).isNull();
    }

    @Test
    public void testClosedPage() {
        Page page = ReadOnlyBrowserPageTestDouble.builder().closed(true).build();

        AgentBrowserObservation observation = tool.observe(page);

        assertThat(observation.isPageAvailable()).isFalse();
        assertThat(observation.getError()).contains("closed");
    }

    @Test
    public void testUrlObservationException() {
        Page page = ReadOnlyBrowserPageTestDouble.builder().closed(false)
                .urlThrows(new RuntimeException("Simulated url() failure")).build();

        AgentBrowserObservation observation = tool.observe(page);

        assertThat(observation.isPageAvailable()).isTrue();
        assertThat(observation.getUrl()).isNull();
        assertThat(observation.getError()).contains("URL observation failed");
    }

    @Test
    public void testTitleObservationException() {
        Page page = ReadOnlyBrowserPageTestDouble.builder().closed(false)
                .titleThrows(new RuntimeException("Simulated title() failure")).build();

        AgentBrowserObservation observation = tool.observe(page);

        assertThat(observation.getTitle()).isNull();
        assertThat(observation.getError()).contains("Title observation failed");
    }

    @Test
    public void testDomObservationException() {
        Page page = ReadOnlyBrowserPageTestDouble.builder().closed(false)
                .contentThrows(new RuntimeException("Simulated content() failure")).build();

        AgentBrowserObservation observation = tool.observe(page);

        // DomContextExtractor.extractFromPage() itself already catches the exception safely.
        assertThat(observation.getDomSnapshot()).isEqualTo("[DOM context unavailable]");
    }

    @Test
    public void testLocatorObservationException() {
        Page page = ReadOnlyBrowserPageTestDouble.builder().closed(false)
                .locatorThrows(new RuntimeException("Simulated locator() failure")).build();

        LocatorObservation locatorObservation = tool.observeLocator(page, "bad-selector");

        assertThat(locatorObservation.getCount()).isEqualTo(-1);
        assertThat(locatorObservation.getError()).isNotEmpty();
    }

    // =====================================================================
    // Input safety
    // =====================================================================

    @Test
    public void testNullSelector() {
        Page page = ReadOnlyBrowserPageTestDouble.builder().closed(false).build();

        LocatorObservation observation = tool.observeLocator(page, null);

        assertThat(observation.getError()).isNotEmpty();
        assertThat(observation.getCount()).isEqualTo(-1);
    }

    @Test
    public void testBlankSelector() {
        Page page = ReadOnlyBrowserPageTestDouble.builder().closed(false).build();

        LocatorObservation observation = tool.observeLocator(page, "   ");

        assertThat(observation.getError()).isNotEmpty();
    }

    @Test
    public void testInvalidSelectorCaughtSafely() {
        Page page = ReadOnlyBrowserPageTestDouble.builder().closed(false)
                .locatorThrows(new RuntimeException("Unsupported pseudo-class")).build();

        assertThatCode(() -> tool.observeLocator(page, ":::not-a-real-selector")).doesNotThrowAnyException();
        assertThat(tool.observeLocator(page, ":::not-a-real-selector").getError()).isNotEmpty();
    }

    @Test
    public void testEmptyDom() {
        Page page = ReadOnlyBrowserPageTestDouble.builder().closed(false).content("").build();

        // DomContextExtractor treats blank content as unavailable rather than an empty string.
        assertThat(tool.observe(page).getDomSnapshot()).isEqualTo("[DOM context unavailable]");
    }

    // =====================================================================
    // Partial observation
    // =====================================================================

    @Test
    public void testUrlSucceedsWhileDomFails() {
        Page page = ReadOnlyBrowserPageTestDouble.builder().closed(false)
                .url("https://www.stag.furlenco.com/cart")
                .contentThrows(new RuntimeException("Simulated content() failure"))
                .build();

        AgentBrowserObservation observation = tool.observe(page);

        assertThat(observation.getUrl()).isEqualTo("https://www.stag.furlenco.com/cart"); // preserved
        assertThat(observation.getDomSnapshot()).isEqualTo("[DOM context unavailable]");
    }

    @Test
    public void testTitleSucceedsWhileLocatorObservationFails() {
        Page page = ReadOnlyBrowserPageTestDouble.builder().closed(false)
                .title("Cart")
                .locatorThrows(new RuntimeException("Simulated locator() failure"))
                .build();

        AgentBrowserObservation observation = tool.observe(page, List.of("[data-testid='x']"));

        assertThat(observation.getTitle()).isEqualTo("Cart"); // preserved
        assertThat(observation.getObservedLocatorStates()).hasSize(1);
        assertThat(observation.getObservedLocatorStates().get(0).getError()).isNotEmpty();
    }

    @Test
    public void testPartialResultDoesNotFabricateMissingValues() {
        Page page = ReadOnlyBrowserPageTestDouble.builder().closed(false)
                .urlThrows(new RuntimeException("Simulated url() failure"))
                .title("Cart")
                .build();

        AgentBrowserObservation observation = tool.observe(page);

        assertThat(observation.getUrl()).isNull(); // never a fabricated placeholder like ""
        assertThat(observation.getTitle()).isEqualTo("Cart");
    }

    // =====================================================================
    // Security — sanitization at the explicit AI-boundary preparation method
    // =====================================================================

    @Test
    public void testAuthorizationIsSanitizedBeforeAiBoundary() {
        assertSanitized("Authorization: Bearer TEST_BROWSER_AUTH_SECRET", "TEST_BROWSER_AUTH_SECRET");
    }

    @Test
    public void testCookieIsSanitizedBeforeAiBoundary() {
        assertSanitized("Cookie: sessionid=TEST_BROWSER_COOKIE_SECRET", "TEST_BROWSER_COOKIE_SECRET");
    }

    @Test
    public void testSessionIsSanitizedBeforeAiBoundary() {
        assertSanitized("session=TEST_BROWSER_SESSION_SECRET", "TEST_BROWSER_SESSION_SECRET");
    }

    @Test
    public void testTokenIsSanitizedBeforeAiBoundary() {
        assertSanitized("token=TEST_BROWSER_TOKEN_SECRET", "TEST_BROWSER_TOKEN_SECRET");
    }

    @Test
    public void testPasswordIsSanitizedBeforeAiBoundary() {
        assertSanitized("password=TEST_BROWSER_PASSWORD_SECRET", "TEST_BROWSER_PASSWORD_SECRET");
    }

    private void assertSanitized(String rawContentContainingSecret, String secretValue) {
        Page page = ReadOnlyBrowserPageTestDouble.builder().closed(false)
                .url("https://www.stag.furlenco.com/cart?" + rawContentContainingSecret)
                .content("<div>" + rawContentContainingSecret + "</div>")
                .build();

        AgentBrowserObservation raw = tool.observe(page);
        assertThat(raw.getUrl()).contains(secretValue); // raw observation is honest/unsanitized

        AgentBrowserObservation sanitized = tool.sanitizeForAi(raw);
        assertThat(sanitized.getUrl()).doesNotContain(secretValue);
        assertThat(sanitized.getDomSnapshot()).doesNotContain(secretValue);
    }

    @Test
    public void testSanitizeForAiHandlesNullObservationSafely() {
        assertThat(tool.sanitizeForAi(null)).isNull();
    }

    // =====================================================================
    // Mutation boundary (empirical — complements the static architecture tests)
    // =====================================================================

    @Test
    public void testObserveOnlyInvokesReadOnlyPageMethods() {
        ReadOnlyBrowserPageTestDouble.Builder pageBuilder = ReadOnlyBrowserPageTestDouble.builder()
                .closed(false)
                .locatorResult(RuntimeLocatorTestDouble.builder().count(1).visible(true).build());
        Page page = pageBuilder.build();

        tool.observe(page, List.of("[data-testid='checkout']"));

        assertThat(pageBuilder.invokedMethods()).containsOnly("isClosed", "url", "title", "content", "locator");
    }
}
