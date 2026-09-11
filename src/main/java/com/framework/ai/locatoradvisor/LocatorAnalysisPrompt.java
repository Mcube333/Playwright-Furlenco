package com.framework.ai.locatoradvisor;

import java.util.Objects;

/**
 * Hardened prompt template for the Locator Advisor.
 *
 * The AI's role here is narrow: propose candidate locators, accessibility
 * observations, and a possible existing-method name, in the preferred
 * strategy order. It is NOT trusted to determine matchCount, evidence status,
 * validation type, or score — {@link LocatorAnalysisService} recomputes all of
 * those deterministically against the supplied DOM/Page-Object text via
 * {@link LocatorDomMatcher} and {@link PageObjectContextMatcher}.
 */
public final class LocatorAnalysisPrompt {

    public static final String SYSTEM_INSTRUCTION =
            "You are a Senior Test Automation Architect specializing in resilient, accessible Playwright locators.\n"
            + "Your task is to analyze the supplied DOM evidence and propose locator candidates, accessibility\n"
            + "observations, and (if relevant) a matching existing Page Object method.\n\n"
            + "CRITICAL EVIDENCE RULES (violating any of these makes your response unusable):\n"
            + "1. Analyze only supplied evidence. Do not reason about elements you were not given.\n"
            + "2. Never invent DOM elements. If the DOM snapshot does not contain something, say so.\n"
            + "3. Never claim runtime validation without runtime execution. You only ever see a static DOM\n"
            + "   snapshot string — you never execute anything in a browser.\n"
            + "4. Never claim uniqueness without evidence. If you are not certain a locator matches exactly one\n"
            + "   element, say so explicitly instead of asserting uniqueness.\n"
            + "5. Prefer resilient accessible locators over fragile ones: getByTestId > getByRole+accessible name >\n"
            + "   getByLabel > getByPlaceholder > getByText > stable CSS attribute > stable CSS > XPath > positional.\n"
            + "   This is a preference, not a rule to follow blindly — let the actual DOM evidence decide.\n"
            + "6. Do not invent Page Object methods. Only reference a method name if it appears verbatim in the\n"
            + "   supplied existing Page Object context.\n"
            + "7. Do not invent test IDs. Only propose a data-testid/data-test/data-qa/data-cy value that is\n"
            + "   actually present in the supplied DOM snapshot.\n"
            + "8. Do not invent analytics events.\n"
            + "9. If evidence is insufficient for a judgment, return \"MISSING\" or \"UNVERIFIED\" for that item\n"
            + "   rather than guessing.\n\n"
            + "Return strictly raw JSON matching the requested schema. No markdown code blocks, no commentary\n"
            + "outside the JSON.";

    private LocatorAnalysisPrompt() {
    }

    public static String buildPrompt(LocatorAnalysisRequest request, String sanitizedDom, boolean domTruncated) {
        Objects.requireNonNull(request, "LocatorAnalysisRequest must not be null");

        StringBuilder sb = new StringBuilder();
        sb.append("=== LOCATOR ADVISORY ANALYSIS (EVIDENCE-HARDENED) ===\n\n");

        sb.append("[TARGET]\n");
        sb.append("Description: ").append(orNone(request.getTargetDescription())).append("\n");
        sb.append("Text: ").append(orNone(request.getTargetText())).append("\n");
        sb.append("Role: ").append(orNone(request.getTargetRole())).append("\n");
        sb.append("Page: ").append(orNone(request.getPageName())).append("\n");
        sb.append("URL: ").append(orNone(request.getCurrentUrl())).append("\n\n");

        if (request.hasFailingLocator()) {
            sb.append("[FAILURE CONTEXT]\n");
            sb.append("Failed locator: ").append(request.getFailingLocator()).append("\n");
            sb.append("Failure message: ").append(orNone(request.getFailureMessage())).append("\n\n");
        }

        sb.append("[DOM SNAPSHOT]\n");
        if (sanitizedDom == null || sanitizedDom.isBlank()) {
            sb.append("No DOM snapshot supplied. Base all locator suggestions on the target description/text only,\n");
            sb.append("and classify them as UNVERIFIED or MISSING as appropriate.\n\n");
        } else {
            sb.append(sanitizedDom).append("\n");
            if (domTruncated) {
                sb.append("\n[NOTE] The DOM snapshot above was truncated to stay within the configured size limit.\n");
                sb.append("Do not assume complete DOM coverage — elements outside the truncated portion are unknown.\n\n");
            } else {
                sb.append("\n");
            }
        }

        if (request.hasExistingPageObjectContext()) {
            sb.append("[EXISTING PAGE OBJECT CONTEXT]\n");
            sb.append(request.getExistingPageObjectContext()).append("\n\n");
        }

        sb.append("=== INSTRUCTIONS ===\n");
        sb.append("Propose up to 5 candidate locators for the target element, in preferred-strategy order where the\n");
        sb.append("evidence supports it. For each, give your best-effort strategy classification, the locator string,\n");
        sb.append("a short rationale, and any strengths/weaknesses you observe. Do NOT compute match counts, scores,\n");
        sb.append("or evidence status yourself — that will be calculated independently against the supplied DOM.\n\n");
        sb.append("Return a JSON object with this exact structure:\n");
        sb.append("{\n");
        sb.append("  \"targetElement\": \"short human description of the element being analyzed\",\n");
        sb.append("  \"candidates\": [\n");
        sb.append("    {\"locator\": \"[data-testid='cart-plus']\", \"strategy\": \"TEST_ID\", \"rationale\": \"...\",\n");
        sb.append("     \"strengths\": [\"...\"], \"weaknesses\": [\"...\"]}\n");
        sb.append("  ],\n");
        sb.append("  \"accessibilityFindings\": [\n");
        sb.append("    {\"element\": \"button.plus\", \"issue\": \"missing accessible name\",\n");
        sb.append("     \"recommendation\": \"Add aria-label='Increase quantity'\"}\n");
        sb.append("  ],\n");
        sb.append("  \"existingMethodSuggestion\": \"incrementQuantity\",\n");
        sb.append("  \"assumptions\": [\"...\"],\n");
        sb.append("  \"missingEvidence\": [\"...\"]\n");
        sb.append("}\n\n");
        sb.append("If the existing Page Object context does not contain a method matching this target, set\n");
        sb.append("\"existingMethodSuggestion\" to an empty string rather than inventing one.\n");
        sb.append("Return ONLY the raw JSON object.");

        return sb.toString();
    }

    private static String orNone(String value) {
        return (value == null || value.isBlank()) ? "(not provided)" : value;
    }
}
