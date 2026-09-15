package com.tests.ai.locatoradvisor;

import static org.assertj.core.api.Assertions.assertThat;

import com.framework.ai.locatoradvisor.LocatorDomMatcher;
import com.framework.ai.locatoradvisor.LocatorDomMatcher.MatchResult;
import com.framework.ai.locatoradvisor.LocatorStrategy;
import org.testng.annotations.Test;

/**
 * Deterministic, offline DOM-matching tests. No AI client involved — these
 * verify the anti-hallucination gate that independently counts matches
 * before any locator can be classified VERIFIED.
 */
public class LocatorDomMatcherTest {

    // 1. Unique test ID locator
    @Test
    public void testUniqueTestIdLocatorMatchesExactlyOnce() {
        String dom = "<div><button data-testid=\"cart-plus\">+</button></div>";
        MatchResult result = LocatorDomMatcher.evaluate(dom, LocatorStrategy.TEST_ID, "[data-testid='cart-plus']", null);
        assertThat(result.getMatchCount()).isEqualTo(1);
    }

    // 2. Unique role + accessible name
    @Test
    public void testUniqueRoleWithAccessibleNameMatchesExactlyOnce() {
        String dom = "<div><button aria-label=\"Increase quantity\">+</button></div>";
        MatchResult result = LocatorDomMatcher.evaluate(dom, LocatorStrategy.ROLE, "button", "Increase quantity");
        assertThat(result.getMatchCount()).isEqualTo(1);
    }

    // 3. Duplicate matching locator
    @Test
    public void testDuplicateClassSelectorMatchesMultipleElements() {
        String dom = "<button class=\"plus\">A</button><button class=\"plus\">B</button>";
        MatchResult result = LocatorDomMatcher.evaluate(dom, LocatorStrategy.CSS_STABLE, ".plus", null);
        assertThat(result.getMatchCount()).isEqualTo(2);
    }

    // 6. Fragile generated CSS class
    @Test
    public void testGeneratedLookingCssClassIsFlagged() {
        String dom = "<button class=\"css-a1b2c3d4\">+</button>";
        MatchResult result = LocatorDomMatcher.evaluate(dom, LocatorStrategy.CSS_STABLE, ".css-a1b2c3d4", null);
        assertThat(result.getMatchCount()).isEqualTo(1);
        assertThat(result.getNotes()).anyMatch(n -> n.toLowerCase().contains("generated"));
    }

    // 7. XPath fallback — cannot be validated offline
    @Test
    public void testXPathCannotBeValidatedOffline() {
        String dom = "<div><span>x</span></div>";
        MatchResult result = LocatorDomMatcher.evaluate(dom, LocatorStrategy.XPATH, "/html/body/div[1]/span", null);
        assertThat(result.isEvaluable()).isFalse();
        assertThat(result.getMatchCount()).isEqualTo(-1);
        assertThat(result.getNotes()).anyMatch(n -> n.contains("cannot be structurally validated"));
    }

    // 8. Positional selector warning
    @Test
    public void testPositionalSelectorIsFlaggedAsFragile() {
        String dom = "<div><span>x</span></div>";
        MatchResult result = LocatorDomMatcher.evaluate(dom, LocatorStrategy.POSITIONAL, "div span:nth-child(2)", null);
        assertThat(result.isEvaluable()).isFalse();
        assertThat(result.getNotes()).anyMatch(n -> n.toLowerCase().contains("fragile"));
        assertThat(LocatorDomMatcher.isFragileSelectorText("div span:nth-child(2)")).isTrue();
    }

    // 10. Missing DOM evidence
    @Test
    public void testBlankDomIsNotEvaluable() {
        MatchResult result = LocatorDomMatcher.evaluate("", LocatorStrategy.TEST_ID, "[data-testid='cart-plus']", null);
        assertThat(result.isEvaluable()).isFalse();
        assertThat(result.getMatchCount()).isEqualTo(-1);
    }

    // 14. DOM_MATCHED vs RUNTIME_VALIDATED distinction (matcher never fabricates runtime execution)
    @Test
    public void testMatcherNeverReturnsRuntimeSignal() {
        String dom = "<input placeholder=\"Email\" />";
        MatchResult result = LocatorDomMatcher.evaluate(dom, LocatorStrategy.PLACEHOLDER, "Email", null);
        assertThat(result.getMatchCount()).isEqualTo(1);
        // The matcher only ever returns a raw count — classification into DOM_MATCHED/RUNTIME_VALIDATED
        // is the caller's (LocatorAnalysisService's) responsibility, and it must never assign RUNTIME_VALIDATED.
    }

    @Test
    public void testRoleWithNoMatchingAccessibleNameReturnsZero() {
        String dom = "<button aria-label=\"Remove item\">-</button>";
        MatchResult result = LocatorDomMatcher.evaluate(dom, LocatorStrategy.ROLE, "button", "Increase quantity");
        assertThat(result.getMatchCount()).isEqualTo(0);
    }

    @Test
    public void testDataTestAttributeFallbackIsSupported() {
        String dom = "<button data-test=\"add-to-cart-sauce-labs-backpack\">Add to cart</button>";
        MatchResult result = LocatorDomMatcher.evaluate(dom, LocatorStrategy.TEST_ID, "add-to-cart-sauce-labs-backpack", null);
        assertThat(result.getMatchCount()).isEqualTo(1);
    }

    // ===================================================================================
    // Phase 5.1 hardening: explicit re-assertions of the matcher-level anti-hallucination
    // invariants — the matcher is the sole source of truth for match evidence.
    // ===================================================================================

    @Test
    public void testTestIdWithNoMatchInDomReturnsZeroNotNegativeOne() {
        String dom = "<div><button data-testid=\"unrelated-button\">x</button></div>";
        MatchResult result = LocatorDomMatcher.evaluate(dom, LocatorStrategy.TEST_ID, "[data-testid='cart-plus']", null);
        // Zero is a real, evaluated result (element absent) — distinct from -1 (strategy not evaluable at all).
        assertThat(result.isEvaluable()).isTrue();
        assertThat(result.getMatchCount()).isEqualTo(0);
    }

    @Test
    public void testUnknownStrategyIsNeverEvaluable() {
        String dom = "<div><button>x</button></div>";
        MatchResult result = LocatorDomMatcher.evaluate(dom, LocatorStrategy.UNKNOWN, "mystery-locator", null);
        assertThat(result.isEvaluable()).isFalse();
        assertThat(result.getMatchCount()).isEqualTo(-1);
    }

    @Test
    public void testUnparseableCssSelectorIsNotEvaluableRatherThanFalselyZero() {
        String dom = "<div><button>x</button></div>";
        // No attribute pattern and no leading '.'/tag.class form — cannot be parsed into a checkable token.
        MatchResult result = LocatorDomMatcher.evaluate(dom, LocatorStrategy.CSS_STABLE, ">>weird::selector", null);
        assertThat(result.isEvaluable()).isFalse();
        assertThat(result.getNotes()).anyMatch(n -> n.contains("Could not parse selector"));
    }

    @Test
    public void testGetByRoleExpressionWithAccessibleNameIsParsedAndMatched() {
        String dom = "<button aria-label=\"Increase quantity\">+</button>";
        MatchResult result = LocatorDomMatcher.evaluate(
                dom, LocatorStrategy.ROLE, "getByRole('button', { name: 'Increase quantity' })", null);
        assertThat(result.getMatchCount()).isEqualTo(1);
    }

    @Test
    public void testTagDotClassSelectorMatchesRealisticFailedLocatorExample() {
        // Mirrors the Phase 5 spec's own failure-mode example: "button.plus" against a DOM
        // that only exposes an accessible name, not that class — must resolve to zero, not -1.
        String dom = "<button aria-label=\"Increase quantity\">+</button>";
        MatchResult result = LocatorDomMatcher.evaluate(dom, LocatorStrategy.CSS_STABLE, "button.plus", null);
        assertThat(result.isEvaluable()).isTrue();
        assertThat(result.getMatchCount()).isEqualTo(0);
    }
}
