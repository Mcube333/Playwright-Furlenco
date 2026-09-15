package com.framework.ai.locatoradvisor;

import com.framework.ai.locatoradvisor.runtime.RuntimeValidationResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Renders a {@link LocatorAnalysisResponse} as a human-readable Markdown report
 * and writes it strictly under target/ai-locator-analysis/ (never src/).
 */
public final class LocatorAnalysisReporter {

    private static final Logger LOGGER = LogManager.getLogger(LocatorAnalysisReporter.class);
    private static final Path DEFAULT_TARGET_DIR = Paths.get("target", "ai-locator-analysis");

    private LocatorAnalysisReporter() {
    }

    public static Path exportReport(LocatorAnalysisResponse response) {
        return exportReport(response, null);
    }

    /**
     * Phase 6 additive overload: same export as {@link #exportReport(LocatorAnalysisResponse)},
     * with an optional Phase 6 runtime validation section appended when {@code runtimeResult} is
     * supplied. Passing {@code null} for {@code runtimeResult} is byte-for-byte identical to the
     * single-argument overload.
     */
    public static Path exportReport(LocatorAnalysisResponse response, RuntimeValidationResult runtimeResult) {
        if (response == null || !response.isSuccess()) {
            return null;
        }
        try {
            Files.createDirectories(DEFAULT_TARGET_DIR);
            Path reportPath = DEFAULT_TARGET_DIR.resolve("locator-analysis-report.md").normalize();
            guardAgainstPathTraversal(reportPath);
            Files.writeString(reportPath, buildMarkdownReport(response, runtimeResult), StandardCharsets.UTF_8);
            LOGGER.info("Exported locator analysis report to: {}", reportPath);
            return reportPath;
        } catch (IOException e) {
            LOGGER.warn("Failed to export locator analysis report: {}", e.getMessage());
            return null;
        }
    }

    public static String buildMarkdownReport(LocatorAnalysisResponse response) {
        StringBuilder sb = new StringBuilder();
        sb.append("# AI Locator Analysis Report\n\n");
        sb.append("**Generated At:** ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())).append("\n\n");

        sb.append("> [!IMPORTANT]\n");
        sb.append("> **ADVISORY ONLY.** This report is a recommendation, not an applied change. Locators were\n");
        sb.append("> validated against a static DOM snapshot text (`DOM_MATCHED`), never against a live browser\n");
        sb.append("> (`RUNTIME_VALIDATED`). Nothing in this report has been written to source automatically.\n\n");

        sb.append("## Target Element\n\n");
        sb.append(response.getTargetElement()).append("\n\n");

        sb.append("## Recommended Locator\n\n");
        LocatorCandidate rec = response.getRecommendedLocator();
        if (rec != null) {
            sb.append("`").append(rec.getLocator()).append("` (").append(rec.getStrategy()).append(")\n\n");
            sb.append(rec.getRecommendation()).append("\n\n");
        } else {
            sb.append("*No candidate could be confirmed against the supplied evidence. Manual verification required.*\n\n");
        }

        sb.append("## Confidence\n\n");
        sb.append(String.format("Overall confidence: **%.0f%%**", response.getOverallConfidence() * 100));
        if (response.isDomTruncated()) {
            sb.append(" _(reduced — DOM snapshot was truncated)_");
        }
        sb.append("\n\n");

        sb.append("## Candidate Locators\n\n");
        sb.append("| Locator | Strategy | Match Count | Score | Evidence |\n");
        sb.append("|---|---|---:|---:|---|\n");
        for (LocatorCandidate c : response.getCandidates()) {
            sb.append(String.format("| `%s` | %s | %s | %d (%s) | %s / %s |\n",
                    escapePipes(c.getLocator()),
                    c.getStrategy(),
                    c.getMatchCount() < 0 ? "n/a" : String.valueOf(c.getMatchCount()),
                    c.getScore(),
                    c.getScoreTier(),
                    c.getEvidenceStatus(),
                    c.getValidationType()));
        }
        sb.append("\n");

        sb.append("## Why This Locator\n\n");
        if (rec != null) {
            if (!rec.getStrengths().isEmpty()) {
                sb.append("**Strengths:**\n");
                for (String s : rec.getStrengths()) sb.append("- ").append(s).append("\n");
                sb.append("\n");
            }
            if (!rec.getWeaknesses().isEmpty()) {
                sb.append("**Weaknesses:**\n");
                for (String w : rec.getWeaknesses()) sb.append("- ").append(w).append("\n");
                sb.append("\n");
            }
        } else {
            sb.append("*(no recommended locator — see Candidate Locators table above)*\n\n");
        }

        sb.append("## Accessibility Findings\n\n");
        if (response.getAccessibilityFindings().isEmpty()) {
            sb.append("*(none observed)*\n\n");
        } else {
            for (AccessibilityFinding f : response.getAccessibilityFindings()) {
                sb.append("- **").append(f.getElement()).append("**: ").append(f.getIssue())
                        .append(" — ").append(f.getRecommendation())
                        .append(" _(").append(f.getEvidenceStatus()).append(")_\n");
            }
            sb.append("\n");
        }

        sb.append("## Existing Page Object Suggestions\n\n");
        if (response.getExistingPageObjectMatches().isEmpty()) {
            sb.append("*(no existing Page Object context supplied)*\n\n");
        } else {
            for (PageObjectMatch m : response.getExistingPageObjectMatches()) {
                sb.append("- ").append(m.getRecommendation()).append(" _(").append(m.getEvidenceStatus()).append(")_\n");
            }
            sb.append("\n");
        }

        sb.append("## Missing Evidence\n\n");
        if (response.getMissingEvidence().isEmpty()) {
            sb.append("*(none)*\n\n");
        } else {
            for (String m : response.getMissingEvidence()) {
                sb.append("- ").append(m).append("\n");
            }
            sb.append("\n");
        }

        if (!response.getAssumptions().isEmpty()) {
            sb.append("## Assumptions\n\n");
            for (String a : response.getAssumptions()) {
                sb.append("- ").append(a).append("\n");
            }
            sb.append("\n");
        }

        sb.append("## Human Review\n\n");
        sb.append("- [ ] Verify locator on QA/staging\n");
        sb.append("- [ ] Verify element behavior\n");
        sb.append("- [ ] Verify Page Object reuse\n");
        sb.append("- [ ] Verify accessibility\n");
        sb.append("- [ ] Verify cross-browser behavior\n");
        sb.append("- [ ] Approve before implementation\n");

        return sb.toString();
    }

    /**
     * Phase 6 additive overload: renders the exact same report as
     * {@link #buildMarkdownReport(LocatorAnalysisResponse)}, with one appended section when a
     * {@link RuntimeValidationResult} is supplied. Passing {@code null} is byte-for-byte identical
     * to the single-argument overload — the existing "Candidate Locators" table (Phase 5 DOM
     * evidence) is never changed or overwritten by runtime data; the two are always shown as
     * clearly separate sections.
     */
    public static String buildMarkdownReport(LocatorAnalysisResponse response, RuntimeValidationResult runtimeResult) {
        String base = buildMarkdownReport(response);
        if (runtimeResult == null) {
            return base;
        }

        StringBuilder sb = new StringBuilder(base);
        sb.append("## Runtime Validation\n\n");
        sb.append("> [!NOTE]\n");
        sb.append("> Live Playwright check against the CURRENT page, performed separately from the static\n");
        sb.append("> DOM-snapshot evidence above. This does not overwrite or replace the Phase 5 evidence in\n");
        sb.append("> \"Candidate Locators\" — both are shown independently.\n\n");
        sb.append("| Field | Value |\n");
        sb.append("|---|---|\n");
        sb.append("| Locator | `").append(escapePipes(runtimeResult.getLocator())).append("` |\n");
        sb.append("| Match Count | ").append(runtimeResult.getMatchCount() < 0 ? "n/a" : String.valueOf(runtimeResult.getMatchCount())).append(" |\n");
        sb.append("| Visible | ").append(runtimeResult.getVisible() == null ? "n/a" : runtimeResult.getVisible().toString()).append(" |\n");
        sb.append("| Enabled | ").append(runtimeResult.getEnabled() == null ? "n/a" : runtimeResult.getEnabled().toString()).append(" |\n");
        sb.append("| Current URL | ").append(escapePipes(runtimeResult.getCurrentUrl())).append(" |\n");
        sb.append("| Environment | ").append(escapePipes(runtimeResult.getEnvironment())).append(" |\n");
        sb.append("| Timestamp | ").append(runtimeResult.getTimestamp()).append(" |\n");
        sb.append("| Evidence Status | ").append(runtimeResult.getEvidenceStatus()).append(" |\n");
        sb.append("| Validation Type | ").append(runtimeResult.getValidationType()).append(" |\n");
        sb.append("| Validation Message | ").append(escapePipes(runtimeResult.getMessage())).append(" |\n");

        return sb.toString();
    }

    private static String escapePipes(String text) {
        return text == null ? "" : text.replace("|", "\\|");
    }

    private static void guardAgainstPathTraversal(Path path) {
        Path normalized = path.normalize().toAbsolutePath();
        Path base = DEFAULT_TARGET_DIR.normalize().toAbsolutePath();
        if (!normalized.startsWith(base)) {
            throw new SecurityException("Path traversal attempt detected: " + path);
        }
    }
}
