package com.tests.ai.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.framework.ai.agent.SelfHealingRecommendation;
import com.framework.ai.agent.SelfHealingRecommendationReporter;
import com.framework.ai.codegeneration.EvidenceItem;
import com.framework.ai.codegeneration.EvidenceStatus;
import com.framework.ai.diagnosis.FixType;
import com.framework.ai.locatoradvisor.ValidationType;
import java.util.List;
import org.testng.annotations.Test;

/**
 * Phase 8 Step 6: pure rendering tests for {@link SelfHealingRecommendationReporter}. No AI, no
 * Playwright, no orchestration anywhere in this file.
 */
public class SelfHealingRecommendationReporterTest {

    private final SelfHealingRecommendationReporter reporter = new SelfHealingRecommendationReporter();

    private SelfHealingRecommendation sampleLocatorRecommendation() {
        return SelfHealingRecommendation.builder()
                .fixType(FixType.LOCATOR)
                .description("Consider updating the locator to match the current DOM evidence.")
                .currentLocator("#checkout-button")
                .proposedLocator("[data-testid='checkout-submit']")
                .rationale("The current locator did not resolve reliably.")
                .confidence(0.86)
                .validationType(ValidationType.RUNTIME_VALIDATED)
                .addEvidenceItem(EvidenceItem.builder().item("Locator").value("[data-testid='checkout-submit']")
                        .status(EvidenceStatus.VERIFIED).confidence(1.0).build())
                .build();
    }

    @Test
    public void testReportContainsAllExpectedSections() {
        String report = reporter.buildMarkdownReport(List.of(sampleLocatorRecommendation()));

        assertThat(report).contains("# Self-Healing Recommendation")
                .contains("### Current Behavior").contains("### Proposed Change")
                .contains("### Evidence").contains("### Validation")
                .contains("### Confidence").contains("### Approval").contains("### Execution Status")
                .contains("## Evidence Disclaimer");
    }

    @Test
    public void testEmptyRecommendationListRendersHonestReport() {
        String report = reporter.buildMarkdownReport(List.of());

        assertThat(report).contains("No self-healing recommendation was produced");
    }

    @Test
    public void testNullRecommendationListHandledSafely() {
        assertThatCode(() -> reporter.buildMarkdownReport(null)).doesNotThrowAnyException();
        assertThat(reporter.buildMarkdownReport(null)).contains("No self-healing recommendation was produced");
    }

    @Test
    public void testReportExplicitlyStatesNoChangesApplied() {
        String report = reporter.buildMarkdownReport(List.of(sampleLocatorRecommendation()));

        assertThat(report).contains("Recommendation only").contains("no changes were applied");
    }

    @Test
    public void testReportNeverClaimsFixedOrApplied() {
        String report = reporter.buildMarkdownReport(List.of(sampleLocatorRecommendation()));

        assertThat(report).doesNotContain("Fixed").doesNotContain("Applied").doesNotContain("Healed successfully");
    }

    @Test
    public void testReportShowsCurrentAndProposedLocator() {
        String report = reporter.buildMarkdownReport(List.of(sampleLocatorRecommendation()));

        assertThat(report).contains("#checkout-button").contains("[data-testid='checkout-submit']");
    }

    @Test
    public void testReportShowsValidationType() {
        String report = reporter.buildMarkdownReport(List.of(sampleLocatorRecommendation()));

        assertThat(report).contains("RUNTIME_VALIDATED");
    }

    @Test
    public void testReportShowsEvidenceStatus() {
        String report = reporter.buildMarkdownReport(List.of(sampleLocatorRecommendation()));

        assertThat(report).contains("VERIFIED");
    }

    @Test
    public void testReportAlwaysShowsApprovalRequired() {
        String report = reporter.buildMarkdownReport(List.of(sampleLocatorRecommendation()));

        assertThat(report).contains("REQUIRED");
    }

    @Test
    public void testReportShowsExecutionNotPerformed() {
        String report = reporter.buildMarkdownReport(List.of(sampleLocatorRecommendation()));

        assertThat(report).contains("NOT PERFORMED");
    }

    @Test
    public void testMultipleRecommendationsAllRendered() {
        SelfHealingRecommendation second = SelfHealingRecommendation.builder()
                .fixType(FixType.WAIT).description("Consider an explicit wait.").confidence(0.5).build();

        String report = reporter.buildMarkdownReport(List.of(sampleLocatorRecommendation(), second));

        assertThat(report).contains("Recommendation 1").contains("Recommendation 2");
    }

    @Test
    public void testSensitiveDataSanitizedAtRenderTimeEvenIfNotPreSanitized() {
        SelfHealingRecommendation withSecret = SelfHealingRecommendation.builder()
                .fixType(FixType.LOCATOR)
                .currentLocator("[data-testid='x']?token=TEST_REPORT_SECRET")
                .rationale("password=TEST_REPORT_SECRET was involved")
                .build();

        String report = reporter.buildMarkdownReport(List.of(withSecret));

        assertThat(report).doesNotContain("TEST_REPORT_SECRET");
    }

    @Test
    public void testAttachToAllureDoesNotThrowWithoutLiveAllureContext() {
        assertThatCode(() -> reporter.attachToAllure(List.of(sampleLocatorRecommendation())))
                .doesNotThrowAnyException();
        assertThatCode(() -> reporter.attachToAllure(null)).doesNotThrowAnyException();
    }
}
