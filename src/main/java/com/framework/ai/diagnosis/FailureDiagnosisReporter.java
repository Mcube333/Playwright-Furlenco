package com.framework.ai.diagnosis;

import com.framework.ai.codegeneration.EvidenceItem;
import com.framework.ai.locatoradvisor.LocatorAnalysisResponse;
import com.framework.ai.locatoradvisor.LocatorCandidate;
import com.framework.ai.locatoradvisor.runtime.RuntimeValidationResult;
import com.framework.ai.model.AiAnalysisResponse;
import com.framework.ai.model.FailureContext;
import com.framework.ai.sanitizer.SensitiveDataSanitizer;
import io.qameta.allure.Allure;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Phase 7 Step 4: pure presentation layer for {@link FailureDiagnosis}.
 *
 * PURE RENDERING — this class calls no AI provider, no Playwright API, no Phase 2/5/6 service,
 * and never touches {@code TestListener}. It only reads getters already present on
 * {@link FailureDiagnosis} and its components and formats them; it computes and upgrades
 * NOTHING. Every {@code EvidenceStatus}/{@code ValidationType} value shown is rendered verbatim
 * from whichever object already carries it (a {@link LocatorCandidate}, an {@link EvidenceItem},
 * or a {@link RuntimeValidationResult}) — static (DOM) and runtime evidence are always shown in
 * separate sections, never merged into one synthetic status.
 *
 * Every string pulled from a supplied object is passed through the existing
 * {@link SensitiveDataSanitizer} at render time — none of {@link FailureContext},
 * {@link RuntimeValidationResult}, or the AI-derived text fields are guaranteed to already be
 * sanitized by the time they reach this class (e.g. {@code RuntimeValidationResult.currentUrl} is
 * captured directly from {@code Page.url()} with no sanitization applied upstream), so this class
 * re-sanitizes defensively rather than trusting any prior step. No new sanitizer is introduced and
 * {@code SensitiveDataSanitizer} itself is not modified.
 *
 * Stateless: no mutable instance or static state. Explicitly invoked only — never registered with
 * Allure's lifecycle and never called from {@code TestListener}.
 */
public class FailureDiagnosisReporter {

    private static final Logger LOGGER = LogManager.getLogger(FailureDiagnosisReporter.class);
    private static final String ATTACHMENT_NAME = "AI Failure Diagnosis";
    private static final String NOT_AVAILABLE = "Not available";

    public FailureDiagnosisReporter() {
    }

    /** Renders {@code diagnosis} as Markdown. Never throws; a null diagnosis renders a minimal, honest report. */
    public String buildMarkdownReport(FailureDiagnosis diagnosis) {
        StringBuilder sb = new StringBuilder();
        sb.append("# AI Failure Diagnosis\n\n");

        if (diagnosis == null) {
            sb.append("No diagnosis was supplied.\n\n");
            appendDisclaimer(sb);
            return sb.toString();
        }

        appendFailureSection(sb, diagnosis);
        appendAiAnalysisSection(sb, diagnosis.getAiAnalysis());
        appendLocatorAnalysisSection(sb, diagnosis.getLocatorAnalysis());
        appendRuntimeValidationSection(sb, diagnosis.getRuntimeValidation());
        appendSuggestedFixesSection(sb, diagnosis.getSuggestedFixes());
        appendDisclaimer(sb);

        return sb.toString();
    }

    /**
     * Attaches the Markdown report to the current Allure test context. Explicitly invoked only —
     * not wired into any TestNG listener. Never throws (mirrors {@code AiAnalysisReporter}'s and
     * {@code LocatorAnalysisReporter}'s existing safe-attachment pattern).
     */
    public void attachToAllure(FailureDiagnosis diagnosis) {
        try {
            String report = buildMarkdownReport(diagnosis);
            Allure.addAttachment(ATTACHMENT_NAME, "text/markdown",
                    new ByteArrayInputStream(report.getBytes(StandardCharsets.UTF_8)), ".md");
        } catch (Exception e) {
            LOGGER.warn("Could not attach failure diagnosis to Allure: {}", e.getMessage());
        }
    }

    // ------------------------------------------------------------------------------------------
    // Section builders — each handles its own "component unavailable" case explicitly rather than
    // inventing a value; absence is always rendered as absence, never as failure.
    // ------------------------------------------------------------------------------------------

    private void appendFailureSection(StringBuilder sb, FailureDiagnosis diagnosis) {
        sb.append("## Failure\n\n");
        FailureContext context = diagnosis.getFailureContext();

        sb.append("- Test: ").append(context == null ? NOT_AVAILABLE : testIdentifier(context)).append("\n");

        AiAnalysisResponse ai = diagnosis.getAiAnalysis();
        String category = (ai != null && ai.getCategory() != null) ? ai.getCategory().toString() : NOT_AVAILABLE;
        sb.append("- Category: ").append(category).append("\n");

        String error = context == null ? null : context.getErrorMessage();
        sb.append("- Error: ").append(displayOrNotAvailable(error)).append("\n\n");
    }

    private void appendAiAnalysisSection(StringBuilder sb, AiAnalysisResponse ai) {
        sb.append("## AI Analysis\n\n");
        if (ai == null) {
            sb.append(NOT_AVAILABLE).append("\n\n");
            return;
        }
        sb.append("- Summary: ").append(displayOrNotAvailable(ai.getSummary())).append("\n");
        sb.append("- Root Cause: ").append(displayOrNotAvailable(ai.getRootCause())).append("\n");
        sb.append(String.format("- Confidence: %.0f%%\n", ai.getConfidenceScore() * 100));
        sb.append("- Suggested Fix (Phase 2): ").append(displayOrNotAvailable(ai.getSuggestedFix())).append("\n\n");
    }

    private void appendLocatorAnalysisSection(StringBuilder sb, LocatorAnalysisResponse locatorAnalysis) {
        sb.append("## Locator Analysis\n\n");
        if (locatorAnalysis == null || locatorAnalysis.getCandidates().isEmpty()) {
            sb.append(NOT_AVAILABLE).append("\n\n");
            return;
        }

        sb.append("| Locator | Strategy | Match Count | Confidence | Evidence | Validation |\n");
        sb.append("|---|---|---:|---:|---|---|\n");
        for (LocatorCandidate c : locatorAnalysis.getCandidates()) {
            sb.append(String.format("| `%s` | %s | %s | %.0f%% | %s | %s |\n",
                    escapePipes(sanitize(c.getLocator())),
                    c.getStrategy(),
                    c.getMatchCount() < 0 ? "n/a" : String.valueOf(c.getMatchCount()),
                    c.getConfidence() * 100,
                    c.getEvidenceStatus(),
                    c.getValidationType()));
        }
        sb.append("\n");

        for (LocatorCandidate c : locatorAnalysis.getCandidates()) {
            String recommendation = sanitize(c.getRecommendation());
            if (!recommendation.isBlank()) {
                sb.append("- Recommendation (`").append(escapePipes(sanitize(c.getLocator()))).append("`): ")
                        .append(escapePipes(recommendation)).append("\n");
            }
            if (!c.getStrengths().isEmpty()) {
                sb.append("  - Strengths: ").append(String.join("; ", sanitizeList(c.getStrengths()))).append("\n");
            }
            if (!c.getWeaknesses().isEmpty()) {
                sb.append("  - Weaknesses: ").append(String.join("; ", sanitizeList(c.getWeaknesses()))).append("\n");
            }
        }
        sb.append("\n");
    }

    private void appendRuntimeValidationSection(StringBuilder sb, RuntimeValidationResult runtime) {
        sb.append("## Runtime Validation\n\n");
        if (runtime == null) {
            sb.append("Runtime validation: ").append(NOT_AVAILABLE).append("\n\n");
            return;
        }

        sb.append("| Field | Value |\n");
        sb.append("|---|---|\n");
        sb.append("| Locator | `").append(escapePipes(sanitize(runtime.getLocator()))).append("` |\n");
        sb.append("| Match count | ").append(runtime.getMatchCount() < 0 ? "n/a" : String.valueOf(runtime.getMatchCount())).append(" |\n");
        sb.append("| Visible | ").append(runtime.getVisible() == null ? NOT_AVAILABLE : runtime.getVisible().toString()).append(" |\n");
        sb.append("| Enabled | ").append(runtime.getEnabled() == null ? NOT_AVAILABLE : runtime.getEnabled().toString()).append(" |\n");
        sb.append("| URL | ").append(displayOrNotAvailable(runtime.getCurrentUrl())).append(" |\n");
        sb.append("| Environment | ").append(displayOrNotAvailable(runtime.getEnvironment())).append(" |\n");
        sb.append("| Validation type | ").append(runtime.getValidationType()).append(" |\n");
        sb.append("| Evidence status | ").append(runtime.getEvidenceStatus()).append(" |\n");
        sb.append("| Message | ").append(displayOrNotAvailable(runtime.getMessage())).append(" |\n");
        sb.append("| Timestamp | ").append(runtime.getTimestamp() == null ? NOT_AVAILABLE : runtime.getTimestamp().toString()).append(" |\n\n");
    }

    private void appendSuggestedFixesSection(StringBuilder sb, List<SuggestedFix> fixes) {
        sb.append("## Suggested Fixes\n\n");
        if (fixes == null || fixes.isEmpty()) {
            sb.append(NOT_AVAILABLE).append("\n\n");
            return;
        }

        sb.append("| Type | Description | Confidence | Evidence |\n");
        sb.append("|---|---|---:|---|\n");
        for (SuggestedFix fix : fixes) {
            String evidenceSummary = summarizeEvidence(fix.getEvidenceItems());
            sb.append(String.format("| %s | %s | %.0f%% | %s |\n",
                    fix.getFixType(),
                    escapePipes(displayOrNotAvailable(fix.getDescription())),
                    fix.getConfidence() * 100,
                    escapePipes(evidenceSummary)));

            LocatorCandidate related = fix.getRelatedLocatorCandidate();
            if (related != null) {
                sb.append("  - Related locator: `").append(escapePipes(sanitize(related.getLocator())))
                        .append("` (").append(related.getEvidenceStatus()).append(" / ")
                        .append(related.getValidationType()).append(")\n");
            }
        }
        sb.append("\n");
    }

    private void appendDisclaimer(StringBuilder sb) {
        sb.append("## Evidence Disclaimer\n\n");
        sb.append("AI-generated recommendations are advisory. Evidence status is based on deterministic ")
                .append("framework evidence and runtime validation where available. No automatic test or ")
                .append("code changes were performed.\n");
    }

    // ------------------------------------------------------------------------------------------
    // Rendering helpers
    // ------------------------------------------------------------------------------------------

    private String summarizeEvidence(List<EvidenceItem> items) {
        if (items == null || items.isEmpty()) {
            return NOT_AVAILABLE;
        }
        return items.stream()
                .map(e -> {
                    String value = sanitize(e.getValue());
                    return e.getStatus() + (value.isBlank() ? "" : " (`" + value + "`)");
                })
                .collect(Collectors.joining("; "));
    }

    private String testIdentifier(FailureContext context) {
        String testClass = sanitize(context.getTestClass());
        String testName = sanitize(context.getTestName());
        if (testClass.isBlank() && testName.isBlank()) {
            return NOT_AVAILABLE;
        }
        if (testClass.isBlank()) {
            return testName;
        }
        if (testName.isBlank()) {
            return testClass;
        }
        return testClass + "#" + testName;
    }

    /** Sanitizes defensively; never trusts that a supplied string was already sanitized upstream. */
    private String sanitize(String value) {
        return value == null ? "" : SensitiveDataSanitizer.sanitize(value);
    }

    private List<String> sanitizeList(List<String> values) {
        return values.stream().map(this::sanitize).collect(Collectors.toList());
    }

    private String displayOrNotAvailable(String value) {
        String sanitized = sanitize(value);
        return sanitized.isBlank() ? NOT_AVAILABLE : sanitized;
    }

    private String escapePipes(String text) {
        return text == null ? "" : text.replace("|", "\\|");
    }
}
