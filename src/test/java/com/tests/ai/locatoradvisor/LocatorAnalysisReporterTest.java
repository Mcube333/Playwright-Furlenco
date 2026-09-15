package com.tests.ai.locatoradvisor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.framework.ai.codegeneration.EvidenceStatus;
import com.framework.ai.locatoradvisor.AccessibilityFinding;
import com.framework.ai.locatoradvisor.LocatorAnalysisReporter;
import com.framework.ai.locatoradvisor.LocatorAnalysisResponse;
import com.framework.ai.locatoradvisor.LocatorCandidate;
import com.framework.ai.locatoradvisor.LocatorStrategy;
import com.framework.ai.locatoradvisor.PageObjectMatch;
import com.framework.ai.locatoradvisor.ValidationType;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.testng.annotations.Test;

/**
 * Phase 5.1: direct unit tests for LocatorAnalysisReporter, closing the coverage
 * gap identified in the Phase 5 verification report. No production code is
 * modified by this test class — the private path-traversal guard is exercised
 * via reflection so the security behavior can be verified without changing
 * LocatorAnalysisReporter's public API.
 */
public class LocatorAnalysisReporterTest {

    private LocatorCandidate verifiedCandidate() {
        return LocatorCandidate.builder()
                .locator("[data-testid='cart-plus']")
                .strategy(LocatorStrategy.TEST_ID)
                .evidenceStatus(EvidenceStatus.VERIFIED)
                .validationType(ValidationType.DOM_MATCHED)
                .matchCount(1)
                .confidence(1.0)
                .score(100)
                .addStrength("Unique match in supplied DOM snapshot.")
                .recommendation("Unique, evidence-backed match — safe to adopt after a final human review.")
                .build();
    }

    private LocatorAnalysisResponse fullResponse() {
        return LocatorAnalysisResponse.builder()
                .success(true)
                .targetElement("Cart quantity increase button")
                .addCandidate(verifiedCandidate())
                .recommendedLocator(verifiedCandidate())
                .addAccessibilityFinding(AccessibilityFinding.of("button", "missing accessible name",
                        "Add aria-label='Increase quantity'"))
                .addExistingPageObjectMatch(PageObjectMatch.reuse("incrementQuantity", "CartPage"))
                .addAssumption("User already has an item in cart")
                .addMissingEvidence("No existing Page Object context supplied for a second element")
                .overallConfidence(1.0)
                .humanReviewRequired(true)
                .domTruncated(false)
                .build();
    }

    // --- Markdown report generation ---

    @Test
    public void testMarkdownReportContainsAllRequiredSections() {
        String md = LocatorAnalysisReporter.buildMarkdownReport(fullResponse());

        assertThat(md).contains("# AI Locator Analysis Report");
        assertThat(md).contains("## Target Element");
        assertThat(md).contains("## Recommended Locator");
        assertThat(md).contains("## Confidence");
        assertThat(md).contains("## Candidate Locators");
        assertThat(md).contains("## Why This Locator");
        assertThat(md).contains("## Accessibility Findings");
        assertThat(md).contains("## Existing Page Object Suggestions");
        assertThat(md).contains("## Missing Evidence");
        assertThat(md).contains("## Assumptions");
        assertThat(md).contains("## Human Review");
        assertThat(md).contains("ADVISORY ONLY");
    }

    // --- Candidate locator details / evidence status rendering ---

    @Test
    public void testCandidateLocatorTableRendersEvidenceAndValidationType() {
        String md = LocatorAnalysisReporter.buildMarkdownReport(fullResponse());

        assertThat(md).contains("| Locator | Strategy | Match Count | Score | Evidence |");
        assertThat(md).contains("`[data-testid='cart-plus']`");
        assertThat(md).contains("TEST_ID");
        assertThat(md).contains("100 (Excellent)");
        assertThat(md).contains("VERIFIED / DOM_MATCHED");
    }

    @Test
    public void testCandidateTableRendersEachEvidenceStatusDistinctly() {
        LocatorCandidate unverified = LocatorCandidate.builder()
                .locator(".plus")
                .strategy(LocatorStrategy.CSS_STABLE)
                .evidenceStatus(EvidenceStatus.UNVERIFIED)
                .validationType(ValidationType.DOM_MATCHED)
                .matchCount(2)
                .score(30)
                .build();
        LocatorCandidate missing = LocatorCandidate.builder()
                .locator("")
                .strategy(LocatorStrategy.UNKNOWN)
                .evidenceStatus(EvidenceStatus.MISSING)
                .validationType(ValidationType.NOT_VALIDATED)
                .matchCount(-1)
                .score(10)
                .build();

        LocatorAnalysisResponse response = LocatorAnalysisResponse.builder()
                .success(true)
                .targetElement("Search field")
                .addCandidate(verifiedCandidate())
                .addCandidate(unverified)
                .addCandidate(missing)
                .build();

        String md = LocatorAnalysisReporter.buildMarkdownReport(response);
        assertThat(md).contains("VERIFIED / DOM_MATCHED");
        assertThat(md).contains("UNVERIFIED / DOM_MATCHED");
        assertThat(md).contains("MISSING / NOT_VALIDATED");
    }

    // --- Confidence rendering ---

    @Test
    public void testConfidenceRendersPercentageAndTruncationNote() {
        LocatorAnalysisResponse truncated = LocatorAnalysisResponse.builder()
                .success(true)
                .targetElement("x")
                .overallConfidence(0.42)
                .domTruncated(true)
                .build();

        String md = LocatorAnalysisReporter.buildMarkdownReport(truncated);
        assertThat(md).contains("Overall confidence: **42%**");
        assertThat(md).contains("reduced — DOM snapshot was truncated");
    }

    // --- Accessibility findings ---

    @Test
    public void testAccessibilityFindingsRenderElementIssueRecommendationAndStatus() {
        String md = LocatorAnalysisReporter.buildMarkdownReport(fullResponse());
        assertThat(md).contains("**button**: missing accessible name");
        assertThat(md).contains("Add aria-label='Increase quantity'");
        assertThat(md).contains("(INFERRED)");
    }

    @Test
    public void testAccessibilityFindingsEmptyStateRendersPlaceholder() {
        LocatorAnalysisResponse response = LocatorAnalysisResponse.builder()
                .success(true).targetElement("x").build();
        String md = LocatorAnalysisReporter.buildMarkdownReport(response);
        assertThat(md).contains("## Accessibility Findings\n\n*(none observed)*");
    }

    // --- Missing evidence rendering ---

    @Test
    public void testMissingEvidenceRendersBulletList() {
        String md = LocatorAnalysisReporter.buildMarkdownReport(fullResponse());
        assertThat(md).contains("## Missing Evidence\n\n- No existing Page Object context supplied for a second element");
    }

    @Test
    public void testMissingEvidenceEmptyStateRendersPlaceholder() {
        LocatorAnalysisResponse response = LocatorAnalysisResponse.builder()
                .success(true).targetElement("x").build();
        String md = LocatorAnalysisReporter.buildMarkdownReport(response);
        assertThat(md).contains("## Missing Evidence\n\n*(none)*");
    }

    // --- Empty / null response handling ---

    @Test
    public void testExportReportReturnsNullForNullResponse() {
        assertThat(LocatorAnalysisReporter.exportReport(null)).isNull();
    }

    @Test
    public void testExportReportReturnsNullForFailureResponse() {
        LocatorAnalysisResponse failure = LocatorAnalysisResponse.failure("AI is disabled (ai.enabled=false)");
        assertThat(LocatorAnalysisReporter.exportReport(failure)).isNull();
    }

    @Test
    public void testBuildMarkdownReportHandlesEmptyCandidatesWithoutThrowing() {
        LocatorAnalysisResponse response = LocatorAnalysisResponse.builder()
                .success(true)
                .targetElement("Unresolvable element")
                .build();
        String md = LocatorAnalysisReporter.buildMarkdownReport(response);
        assertThat(md).contains("*No candidate could be confirmed against the supplied evidence. Manual verification required.*");
        assertThat(md).contains("*(no recommended locator — see Candidate Locators table above)*");
        assertThat(md).contains("*(no existing Page Object context supplied)*");
    }

    // --- Report generation to target/ai-locator-analysis/ ---

    @Test
    public void testExportReportWritesFileUnderConfiguredTargetDirectory() throws Exception {
        Path reportPath = LocatorAnalysisReporter.exportReport(fullResponse());

        assertThat(reportPath).isNotNull();
        assertThat(Files.exists(reportPath)).isTrue();

        Path expectedDir = Paths.get("target", "ai-locator-analysis").normalize().toAbsolutePath();
        assertThat(reportPath.normalize().toAbsolutePath().getParent()).isEqualTo(expectedDir);
        assertThat(reportPath.getFileName().toString()).isEqualTo("locator-analysis-report.md");

        String written = Files.readString(reportPath);
        assertThat(written).contains("# AI Locator Analysis Report");
        assertThat(written).contains("[data-testid='cart-plus']");
    }

    // --- Path traversal rejection (private guard, exercised via reflection) ---

    private void invokeGuard(Path path) throws Exception {
        Method guard = LocatorAnalysisReporter.class.getDeclaredMethod("guardAgainstPathTraversal", Path.class);
        guard.setAccessible(true);
        try {
            guard.invoke(null, path);
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof SecurityException) {
                throw (SecurityException) e.getCause();
            }
            throw e;
        }
    }

    @Test
    public void testPathTraversalWithRelativeDotDotIsRejected() {
        Path malicious = Paths.get("target", "ai-locator-analysis", "..", "..", "outside.md");
        assertThatThrownBy(() -> invokeGuard(malicious)).isInstanceOf(SecurityException.class);
    }

    @Test
    public void testPathTraversalIntoSourceTreeIsRejected() {
        Path malicious = Paths.get("target", "ai-locator-analysis", "..", "..", "src", "test", "java", "Evil.java");
        assertThatThrownBy(() -> invokeGuard(malicious)).isInstanceOf(SecurityException.class);
    }

    @Test
    public void testAbsolutePathOutsideTargetDirIsRejected() {
        Path malicious = Paths.get(System.getProperty("java.io.tmpdir"), "evil-locator-report.md");
        assertThatThrownBy(() -> invokeGuard(malicious)).isInstanceOf(SecurityException.class);
    }

    @Test
    public void testNestedTraversalIsRejected() {
        Path malicious = Paths.get("target", "ai-locator-analysis", "sub", "..", "..", "..", "..", "outside.md");
        assertThatThrownBy(() -> invokeGuard(malicious)).isInstanceOf(SecurityException.class);
    }

    @Test
    public void testFilenameContainingDotDotSegmentIsRejected() {
        Path malicious = Paths.get("target", "ai-locator-analysis", "..", "outside.md");
        assertThatThrownBy(() -> invokeGuard(malicious)).isInstanceOf(SecurityException.class);
    }

    @Test
    public void testLegitimatePathWithinTargetDirIsAccepted() throws Exception {
        Path legitimate = Paths.get("target", "ai-locator-analysis", "locator-analysis-report.md");
        invokeGuard(legitimate); // must not throw
    }

    // --- Safe filename handling ---

    @Test
    public void testReportFilenameIsFixedAndDoesNotIncorporateUnsanitizedInput() {
        // The public exportReport() API never accepts a caller-supplied filename or path —
        // the output filename is a fixed literal, so untrusted input can never influence it.
        LocatorAnalysisResponse response = LocatorAnalysisResponse.builder()
                .success(true)
                .targetElement("../../etc/passwd\" ; rm -rf /")
                .build();
        Path reportPath = LocatorAnalysisReporter.exportReport(response);
        assertThat(reportPath).isNotNull();
        assertThat(reportPath.getFileName().toString()).isEqualTo("locator-analysis-report.md");
    }

    @Test
    public void testPipeCharactersInContentAreEscapedInMarkdownTable() {
        LocatorCandidate candidateWithPipe = LocatorCandidate.builder()
                .locator("a[href='x|y']")
                .strategy(LocatorStrategy.CSS_ATTRIBUTE)
                .evidenceStatus(EvidenceStatus.UNVERIFIED)
                .validationType(ValidationType.NOT_VALIDATED)
                .matchCount(-1)
                .score(20)
                .build();
        LocatorAnalysisResponse response = LocatorAnalysisResponse.builder()
                .success(true).targetElement("x").addCandidate(candidateWithPipe).build();

        String md = LocatorAnalysisReporter.buildMarkdownReport(response);
        assertThat(md).contains("x\\|y");
    }
}
