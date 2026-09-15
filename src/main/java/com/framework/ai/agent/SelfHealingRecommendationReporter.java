package com.framework.ai.agent;

import com.framework.ai.codegeneration.EvidenceItem;
import com.framework.ai.sanitizer.SensitiveDataSanitizer;
import io.qameta.allure.Allure;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Phase 8 Step 6: pure presentation layer for {@link SelfHealingRecommendation}.
 *
 * PURE RENDERING — this class calls no AI provider, no Playwright API, and no Phase 2/5/6/7/Step
 * 3-5 service; it only reads getters already present on {@link SelfHealingRecommendation} and
 * formats them. Every string is re-sanitized via {@link SensitiveDataSanitizer} at render time,
 * exactly as {@code FailureDiagnosisReporter} already does — defense in depth, since the service
 * that builds these recommendations already sanitizes once.
 *
 * Every rendered report explicitly states that nothing was applied — this class never uses words
 * like "Fixed", "Applied", or "Healed successfully", because nothing here ever changes anything.
 *
 * Explicitly invoked only: never registered with Allure's lifecycle, never called from
 * {@code TestListener}.
 */
public class SelfHealingRecommendationReporter {

    private static final Logger LOGGER = LogManager.getLogger(SelfHealingRecommendationReporter.class);
    private static final String ATTACHMENT_NAME = "Self-Healing Recommendation";
    private static final String NOT_AVAILABLE = "Not available";
    private static final String DISCLAIMER =
            "Recommendation only — no changes were applied. A human must review and apply any change manually.";

    public SelfHealingRecommendationReporter() {
    }

    /** Renders {@code recommendations} as Markdown. Never throws; an empty/null list renders an honest "no recommendation" report. */
    public String buildMarkdownReport(List<SelfHealingRecommendation> recommendations) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Self-Healing Recommendation\n\n");

        if (recommendations == null || recommendations.isEmpty()) {
            sb.append("No self-healing recommendation was produced for this failure.\n\n");
            appendDisclaimer(sb);
            return sb.toString();
        }

        for (int i = 0; i < recommendations.size(); i++) {
            appendRecommendation(sb, recommendations.get(i), i + 1);
        }
        appendDisclaimer(sb);
        return sb.toString();
    }

    /** Explicitly attaches the Markdown report to the current Allure test context. Never throws. */
    public void attachToAllure(List<SelfHealingRecommendation> recommendations) {
        try {
            String report = buildMarkdownReport(recommendations);
            Allure.addAttachment(ATTACHMENT_NAME, "text/markdown",
                    new ByteArrayInputStream(report.getBytes(StandardCharsets.UTF_8)), ".md");
        } catch (Exception e) {
            LOGGER.warn("Could not attach self-healing recommendation to Allure: {}", e.getMessage());
        }
    }

    private void appendRecommendation(StringBuilder sb, SelfHealingRecommendation recommendation, int index) {
        sb.append("## Recommendation ").append(index).append(" — ").append(recommendation.getFixType()).append("\n\n");

        sb.append("### Current Behavior\n");
        if (!recommendation.getCurrentLocator().isBlank()) {
            sb.append("- Current locator: `").append(sanitize(recommendation.getCurrentLocator())).append("`\n");
        }
        if (!recommendation.getCurrentAction().isBlank()) {
            sb.append("- Current: ").append(sanitize(recommendation.getCurrentAction())).append("\n");
        }
        if (recommendation.getCurrentLocator().isBlank() && recommendation.getCurrentAction().isBlank()) {
            sb.append(NOT_AVAILABLE).append("\n");
        }
        sb.append("\n");

        sb.append("### Proposed Change\n");
        sb.append("- ").append(sanitize(displayOrNotAvailable(recommendation.getDescription()))).append("\n");
        if (!recommendation.getProposedLocator().isBlank()) {
            sb.append("- Proposed locator: `").append(sanitize(recommendation.getProposedLocator())).append("`\n");
        }
        if (!recommendation.getProposedAction().isBlank()) {
            sb.append("- Proposed: ").append(sanitize(recommendation.getProposedAction())).append("\n");
        }
        sb.append("\n");

        sb.append("### Evidence\n");
        List<EvidenceItem> evidenceItems = recommendation.getEvidenceItems();
        if (evidenceItems.isEmpty()) {
            sb.append(NOT_AVAILABLE).append("\n");
        } else {
            for (EvidenceItem item : evidenceItems) {
                sb.append("- ").append(sanitize(item.getItem())).append(": `")
                        .append(sanitize(item.getValue())).append("` — ").append(item.getStatus()).append("\n");
            }
        }
        sb.append("\n");

        sb.append("### Validation\n");
        sb.append(recommendation.getValidationType()).append("\n\n");

        sb.append("### Confidence\n");
        sb.append(String.format("%.0f%% (agent reasoning confidence — not a verification or approval)\n\n",
                recommendation.getConfidence() * 100));

        sb.append("### Approval\n");
        sb.append(recommendation.isApprovalRequired() ? "REQUIRED — awaiting human review.\n\n" : "REQUIRED\n\n");

        sb.append("### Execution Status\n");
        sb.append("NOT PERFORMED. ").append(DISCLAIMER).append("\n\n");

        if (!recommendation.getRationale().isBlank()) {
            sb.append("_Rationale: ").append(sanitize(recommendation.getRationale())).append("_\n\n");
        }
    }

    private void appendDisclaimer(StringBuilder sb) {
        sb.append("## Evidence Disclaimer\n\n");
        sb.append(DISCLAIMER).append(" AI-generated reasoning is advisory only and is never treated as verified evidence.\n");
    }

    private String sanitize(String value) {
        return value == null ? "" : SensitiveDataSanitizer.sanitize(value);
    }

    private String displayOrNotAvailable(String value) {
        String sanitized = sanitize(value);
        return sanitized.isBlank() ? NOT_AVAILABLE : sanitized;
    }
}
