package com.tests.ai.locatoradvisor;

import static org.assertj.core.api.Assertions.assertThat;

import com.framework.ai.locatoradvisor.LocatorAnalysisPrompt;
import com.framework.ai.locatoradvisor.LocatorAnalysisRequest;
import org.testng.annotations.Test;

/**
 * Phase 5.1: direct unit tests for LocatorAnalysisPrompt, closing the coverage
 * gap identified in the Phase 5 verification report. Pure string-content
 * assertions — no AI client involved, no production code modified.
 */
public class LocatorAnalysisPromptTest {

    // --- Required system instructions present ---

    @Test
    public void testSystemInstructionDefinesTheAdvisorRole() {
        assertThat(LocatorAnalysisPrompt.SYSTEM_INSTRUCTION)
                .contains("Senior Test Automation Architect")
                .contains("resilient, accessible Playwright locators");
    }

    // --- Anti-hallucination instructions present ---

    @Test
    public void testAntiHallucinationRulesArePresent() {
        String instr = LocatorAnalysisPrompt.SYSTEM_INSTRUCTION;
        assertThat(instr).contains("Analyze only supplied evidence.");
        assertThat(instr).contains("Never invent DOM elements.");
        assertThat(instr).contains("Never claim runtime validation without runtime execution.");
        assertThat(instr).contains("Never claim uniqueness without evidence.");
        assertThat(instr).contains("Do not invent Page Object methods.");
        assertThat(instr).contains("Do not invent test IDs.");
        assertThat(instr).contains("Do not invent analytics events.");
    }

    // --- AI cannot mark a locator VERIFIED without DOM evidence ---

    @Test
    public void testPromptNeverDelegatesEvidenceStatusDeterminationToTheAi() {
        // The prompt must explicitly forbid the AI from self-assigning uniqueness/evidence
        // claims — LocatorAnalysisService recomputes all of that deterministically.
        assertThat(LocatorAnalysisPrompt.SYSTEM_INSTRUCTION)
                .contains("Never claim uniqueness without evidence");

        LocatorAnalysisRequest request = LocatorAnalysisRequest.builder()
                .targetDescription("Search field")
                .build();
        String prompt = LocatorAnalysisPrompt.buildPrompt(request, "<input/>", false);
        assertThat(prompt).contains("Do NOT compute match counts, scores,")
                .contains("or evidence status yourself — that will be calculated independently against the supplied DOM.");
    }

    // --- Analytics must not be invented ---

    @Test
    public void testAnalyticsInventionIsExplicitlyForbidden() {
        assertThat(LocatorAnalysisPrompt.SYSTEM_INSTRUCTION).contains("Do not invent analytics events.");
    }

    // --- Evidence statuses are required when evidence is insufficient ---

    @Test
    public void testInsufficientEvidenceMustResolveToMissingOrUnverified() {
        assertThat(LocatorAnalysisPrompt.SYSTEM_INSTRUCTION)
                .contains("If evidence is insufficient for a judgment, return \"MISSING\" or \"UNVERIFIED\"");
    }

    @Test
    public void testNoDomSuppliedInstructsUnverifiedOrMissingClassification() {
        LocatorAnalysisRequest request = LocatorAnalysisRequest.builder()
                .targetDescription("Search field")
                .build();
        String prompt = LocatorAnalysisPrompt.buildPrompt(request, "", false);
        assertThat(prompt).contains("No DOM snapshot supplied");
        assertThat(prompt).contains("classify them as UNVERIFIED or MISSING as appropriate");
    }

    // --- Sanitized DOM is used (not the raw request field) ---

    @Test
    public void testBuildPromptUsesTheSuppliedSanitizedDomNotTheRawRequestField() {
        LocatorAnalysisRequest requestWithRawUnsanitizedDom = LocatorAnalysisRequest.builder()
                .domSnapshot("RAW_UNSANITIZED_SECRET_VALUE")
                .targetDescription("Login form")
                .build();

        // Simulate what LocatorAnalysisService actually does: sanitize/bound the DOM first,
        // then pass the SANITIZED string separately to buildPrompt.
        String prompt = LocatorAnalysisPrompt.buildPrompt(requestWithRawUnsanitizedDom, "SAFE_SANITIZED_DOM_VALUE", false);

        assertThat(prompt).contains("SAFE_SANITIZED_DOM_VALUE");
        assertThat(prompt).doesNotContain("RAW_UNSANITIZED_SECRET_VALUE");
    }

    @Test
    public void testTruncationNoteIsIncludedWhenDomWasTruncated() {
        LocatorAnalysisRequest request = LocatorAnalysisRequest.builder().targetDescription("x").build();
        String prompt = LocatorAnalysisPrompt.buildPrompt(request, "<div>partial</div>", true);
        assertThat(prompt).contains("truncated to stay within the configured size limit");
        assertThat(prompt).contains("Do not assume complete DOM coverage");
    }

    // --- Target description/text are represented ---

    @Test
    public void testTargetDescriptionAndTextAreIncludedInPrompt() {
        LocatorAnalysisRequest request = LocatorAnalysisRequest.builder()
                .targetDescription("Increase quantity button")
                .targetText("Increase quantity")
                .targetRole("button")
                .pageName("CartPage")
                .currentUrl("https://example.test/cart")
                .build();

        String prompt = LocatorAnalysisPrompt.buildPrompt(request, "<button/>", false);

        assertThat(prompt).contains("Description: Increase quantity button");
        assertThat(prompt).contains("Text: Increase quantity");
        assertThat(prompt).contains("Role: button");
        assertThat(prompt).contains("Page: CartPage");
        assertThat(prompt).contains("URL: https://example.test/cart");
    }

    @Test
    public void testUnprovidedTargetFieldsRenderAsNotProvided() {
        LocatorAnalysisRequest request = LocatorAnalysisRequest.builder().build();
        String prompt = LocatorAnalysisPrompt.buildPrompt(request, "", false);
        assertThat(prompt).contains("Description: (not provided)");
        assertThat(prompt).contains("Text: (not provided)");
    }

    // --- Failure context represented when supplied ---

    @Test
    public void testFailureContextIncludedWhenFailingLocatorSupplied() {
        LocatorAnalysisRequest request = LocatorAnalysisRequest.builder()
                .targetDescription("Increase quantity button")
                .failingLocator("button.plus")
                .failureMessage("Timeout 5000ms exceeded")
                .build();
        String prompt = LocatorAnalysisPrompt.buildPrompt(request, "<button/>", false);
        assertThat(prompt).contains("[FAILURE CONTEXT]");
        assertThat(prompt).contains("Failed locator: button.plus");
        assertThat(prompt).contains("Failure message: Timeout 5000ms exceeded");
    }

    @Test
    public void testFailureContextOmittedWhenNoFailingLocatorSupplied() {
        LocatorAnalysisRequest request = LocatorAnalysisRequest.builder().targetDescription("x").build();
        String prompt = LocatorAnalysisPrompt.buildPrompt(request, "<div/>", false);
        assertThat(prompt).doesNotContain("[FAILURE CONTEXT]");
    }

    // --- Page Object context represented when supplied ---

    @Test
    public void testExistingPageObjectContextIncludedWhenSupplied() {
        String poContext = "public class CartPage { public void incrementQuantity() {} }";
        LocatorAnalysisRequest request = LocatorAnalysisRequest.builder()
                .targetDescription("Increase quantity button")
                .existingPageObjectContext(poContext)
                .build();
        String prompt = LocatorAnalysisPrompt.buildPrompt(request, "<button/>", false);
        assertThat(prompt).contains("[EXISTING PAGE OBJECT CONTEXT]");
        assertThat(prompt).contains(poContext);
    }

    @Test
    public void testExistingPageObjectContextSectionOmittedWhenNotSupplied() {
        LocatorAnalysisRequest request = LocatorAnalysisRequest.builder().targetDescription("x").build();
        String prompt = LocatorAnalysisPrompt.buildPrompt(request, "<div/>", false);
        assertThat(prompt).doesNotContain("[EXISTING PAGE OBJECT CONTEXT]");
    }

    @Test
    public void testPromptInstructsAgainstInventingPageObjectMethodWhenNoneMatches() {
        String prompt = LocatorAnalysisPrompt.buildPrompt(
                LocatorAnalysisRequest.builder().targetDescription("x").build(), "<div/>", false);
        assertThat(prompt).contains("\"existingMethodSuggestion\" to an empty string rather than inventing one.");
    }

    // --- Locator strategy guidance present ---

    @Test
    public void testLocatorStrategyPriorityOrderIsPresent() {
        String instr = LocatorAnalysisPrompt.SYSTEM_INSTRUCTION;
        assertThat(instr).contains("getByTestId > getByRole+accessible name >");
        assertThat(instr).contains("getByLabel > getByPlaceholder > getByText > stable CSS attribute > stable CSS > XPath > positional");
        assertThat(instr).contains("This is a preference, not a rule to follow blindly — let the actual DOM evidence decide.");
    }

    @Test
    public void testResponseSchemaInstructionsAreWellFormed() {
        LocatorAnalysisRequest request = LocatorAnalysisRequest.builder().targetDescription("x").build();
        String prompt = LocatorAnalysisPrompt.buildPrompt(request, "<div/>", false);
        assertThat(prompt).contains("\"candidates\": [");
        assertThat(prompt).contains("\"accessibilityFindings\": [");
        assertThat(prompt).contains("\"existingMethodSuggestion\"");
        assertThat(prompt).contains("\"assumptions\"");
        assertThat(prompt).contains("\"missingEvidence\"");
        assertThat(prompt).contains("Return ONLY the raw JSON object.");
    }
}
