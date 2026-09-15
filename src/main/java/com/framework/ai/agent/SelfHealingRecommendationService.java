package com.framework.ai.agent;

import com.framework.ai.codegeneration.EvidenceItem;
import com.framework.ai.codegeneration.EvidenceStatus;
import com.framework.ai.diagnosis.FailureDiagnosis;
import com.framework.ai.diagnosis.FixType;
import com.framework.ai.diagnosis.SuggestedFix;
import com.framework.ai.locatoradvisor.LocatorAnalysisResponse;
import com.framework.ai.locatoradvisor.LocatorCandidate;
import com.framework.ai.locatoradvisor.ValidationType;
import com.framework.ai.locatoradvisor.runtime.RuntimeValidationResult;
import com.framework.ai.model.AiAnalysisResponse;
import com.framework.ai.sanitizer.SensitiveDataSanitizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Phase 8 Step 6: transforms an existing, already-produced diagnosis into zero or more
 * {@link SelfHealingRecommendation}s — proposals for a human to review, never anything this class
 * (or anything downstream of it in this codebase) applies automatically.
 *
 * DETERMINISTIC, NO NEW AI CALL. This class makes no call to {@code AiClient} or
 * {@code AgentReasoningService} of its own — it only reads already-produced Phase 2/5/6/7 output
 * ({@link FailureDiagnosis}, {@link LocatorAnalysisResponse}, {@link RuntimeValidationResult},
 * {@link SuggestedFix}) and applies fixed, deterministic templates and evidence-ranking rules.
 * Reusing an existing AI diagnosis rather than calling AI a second time to "rewrite" it avoids
 * exactly the unnecessary AI aggregation this step's own instructions warn against.
 *
 * GUARD-INDEPENDENT. {@link AgentExecutionGuardResult#isAllowed()} is never read anywhere in this
 * class — a recommendation is produced (or not) purely from the diagnosis evidence, regardless of
 * whether the guard says {@code ALLOWED} or {@code BLOCKED}; this class has no way to reinterpret
 * either outcome as permission, because it never inspects the guard result's allow/block value at
 * all. The parameter exists only so a caller may correlate the two in its own reporting.
 *
 * NO EXECUTION, NO SOURCE MODIFICATION. There is no method here capable of clicking, filling,
 * navigating, writing a file, or invoking Git — only {@code recommend(...)} returning in-memory,
 * immutable {@link SelfHealingRecommendation} values.
 */
public class SelfHealingRecommendationService {

    private static final Logger LOGGER = LogManager.getLogger(SelfHealingRecommendationService.class);

    private static final int MAX_LOCATOR_RECOMMENDATIONS = 3;

    /**
     * Produces recommendations for {@code diagnosis}. Never throws: any malformed input or
     * unexpected exception results in an empty list rather than a propagated exception.
     * {@code decision} (if supplied) narrows recommendation generation to the fix type implied by
     * its {@link AgentAction} (e.g. only locator recommendations for
     * {@link AgentAction#LOCATOR_RECOMMENDATION}); a {@code null} decision considers every
     * supported fix type. {@code guardResult} is accepted for a caller's own correlation/reporting
     * purposes only — its allow/block value is never read here.
     */
    public List<SelfHealingRecommendation> recommend(FailureDiagnosis diagnosis, AgentDecision decision,
                                                       AgentExecutionGuardResult guardResult) {
        try {
            if (diagnosis == null) {
                return List.of();
            }

            AgentAction requestedAction = decision != null ? decision.getAction() : null;
            List<SelfHealingRecommendation> recommendations = new ArrayList<>();

            if (requestedAction == null || requestedAction == AgentAction.LOCATOR_RECOMMENDATION) {
                recommendations.addAll(recommendLocatorFixes(diagnosis));
            }
            if (requestedAction == null || requestedAction == AgentAction.WAIT_RECOMMENDATION) {
                recommendations.addAll(recommendFromSuggestedFixes(diagnosis, FixType.WAIT));
            }
            if (requestedAction == null || requestedAction == AgentAction.ASSERTION_RECOMMENDATION) {
                recommendations.addAll(recommendFromSuggestedFixes(diagnosis, FixType.TEST_DATA));
                recommendations.addAll(recommendAssertionFixes(diagnosis));
            }

            return recommendations;
        } catch (Exception e) {
            LOGGER.warn("Unexpected error while producing self-healing recommendations: {}", e.getMessage());
            return List.of();
        }
    }

    // ------------------------------------------------------------------------------------------
    // Locator healing — the primary Step 6 use case.
    // ------------------------------------------------------------------------------------------

    private List<SelfHealingRecommendation> recommendLocatorFixes(FailureDiagnosis diagnosis) {
        LocatorAnalysisResponse locatorAnalysis = diagnosis.getLocatorAnalysis();
        if (locatorAnalysis == null || locatorAnalysis.getCandidates().isEmpty()) {
            return List.of();
        }

        String currentLocator = failingLocator(diagnosis.getAiAnalysis());

        List<LocatorCandidate> proposedCandidates = new ArrayList<>();
        for (LocatorCandidate candidate : locatorAnalysis.getCandidates()) {
            if (currentLocator == null || !currentLocator.equals(candidate.getLocator())) {
                proposedCandidates.add(candidate);
            }
        }
        if (proposedCandidates.isEmpty()) {
            // Only the already-known-failing locator was ever evaluated — nothing new to propose.
            return List.of();
        }

        proposedCandidates.sort(Comparator
                .comparingInt((LocatorCandidate c) -> statusRank(c.getEvidenceStatus()))
                .thenComparing(Comparator.comparingInt(LocatorCandidate::getScore).reversed()));

        LocatorCandidate best = proposedCandidates.get(0);
        List<LocatorCandidate> tiedForBest = new ArrayList<>();
        for (LocatorCandidate candidate : proposedCandidates) {
            if (statusRank(candidate.getEvidenceStatus()) == statusRank(best.getEvidenceStatus())
                    && candidate.getScore() == best.getScore()) {
                tiedForBest.add(candidate);
            }
        }

        boolean ambiguous = tiedForBest.size() > 1;
        List<LocatorCandidate> toRecommend = ambiguous
                ? tiedForBest.subList(0, Math.min(tiedForBest.size(), MAX_LOCATOR_RECOMMENDATIONS))
                : List.of(best);

        List<SelfHealingRecommendation> recommendations = new ArrayList<>();
        for (LocatorCandidate candidate : toRecommend) {
            recommendations.add(buildLocatorRecommendation(currentLocator, candidate,
                    diagnosis.getRuntimeValidation(), ambiguous));
        }
        return recommendations;
    }

    private SelfHealingRecommendation buildLocatorRecommendation(String currentLocator, LocatorCandidate candidate,
                                                                    RuntimeValidationResult runtimeValidation,
                                                                    boolean ambiguous) {
        EvidenceStatus evidenceStatus = candidate.getEvidenceStatus();
        ValidationType validationType = candidate.getValidationType();

        List<EvidenceItem> evidenceItems = new ArrayList<>();
        evidenceItems.add(EvidenceItem.builder()
                .item("Locator (static DOM evidence)")
                .value(candidate.getLocator())
                .status(evidenceStatus)
                .source("LocatorAnalysisService (" + validationType + ")")
                .confidence(candidate.getConfidence())
                .build());

        boolean runtimeMatches = runtimeValidation != null
                && candidate.getLocator().equals(runtimeValidation.getLocator());
        if (runtimeMatches) {
            // Runtime evidence is strictly stronger when it exists for this exact candidate —
            // reused verbatim from the existing, unmodified RuntimeValidationResult, never invented.
            evidenceStatus = runtimeValidation.getEvidenceStatus();
            validationType = runtimeValidation.getValidationType();
            evidenceItems.add(EvidenceItem.builder()
                    .item("Locator (runtime evidence)")
                    .value(runtimeValidation.getLocator())
                    .status(runtimeValidation.getEvidenceStatus())
                    .source("RuntimeLocatorValidator (" + runtimeValidation.getValidationType() + ")")
                    .confidence(runtimeValidation.getEvidenceStatus() == EvidenceStatus.VERIFIED ? 1.0 : 0.0)
                    .build());
        }

        String description = ambiguous
                ? "Ambiguous: multiple candidate selectors are equally supported by the observed evidence — review individually before adopting any."
                : "Consider updating the locator to match the current DOM evidence.";

        String rationale = sanitize(String.format(
                "The current locator '%s' did not reliably resolve. Candidate '%s' is supported by %s evidence (%s).",
                emptyIfBlank(currentLocator), candidate.getLocator(), evidenceStatus, validationType));

        return SelfHealingRecommendation.builder()
                .fixType(FixType.LOCATOR)
                .description(sanitize(description))
                .currentLocator(sanitize(currentLocator))
                .proposedLocator(sanitize(candidate.getLocator()))
                .rationale(rationale)
                .confidence(candidate.getConfidence())
                .evidenceItems(evidenceItems)
                .validationType(validationType)
                .approvalRequired(true)
                .build();
    }

    private String failingLocator(AiAnalysisResponse aiAnalysis) {
        if (aiAnalysis == null || aiAnalysis.getSuggestedLocators() == null || aiAnalysis.getSuggestedLocators().isEmpty()) {
            return null;
        }
        return aiAnalysis.getSuggestedLocators().get(0);
    }

    private int statusRank(EvidenceStatus status) {
        if (status == null) {
            return 3;
        }
        return switch (status) {
            case VERIFIED -> 0;
            case INFERRED -> 1;
            case UNVERIFIED -> 2;
            case MISSING -> 3;
        };
    }

    // ------------------------------------------------------------------------------------------
    // Wait / test-data healing — derived from Phase 7's own already-classified SuggestedFixes.
    // ------------------------------------------------------------------------------------------

    private List<SelfHealingRecommendation> recommendFromSuggestedFixes(FailureDiagnosis diagnosis, FixType fixType) {
        List<SelfHealingRecommendation> recommendations = new ArrayList<>();
        for (SuggestedFix fix : diagnosis.getSuggestedFixes()) {
            if (fix.getFixType() != fixType) {
                continue;
            }
            if (fixType == FixType.TEST_DATA && noUsableEvidence(fix)) {
                // Only recommend test-data changes when existing evidence actually supports it.
                continue;
            }

            String description = fixType == FixType.WAIT
                    ? "Element was not immediately available; consider an explicit visibility/state wait rather than a fixed delay."
                    : "Test data appears mismatched with the current environment/application state; review the test dataset for this scenario.";
            String currentAction = fixType == FixType.WAIT ? "Implicit or no explicit wait" : "";
            String proposedAction = fixType == FixType.WAIT ? "Explicit visibility/state wait" : "";

            recommendations.add(SelfHealingRecommendation.builder()
                    .fixType(fixType)
                    .description(sanitize(description))
                    .currentAction(sanitize(currentAction))
                    .proposedAction(sanitize(proposedAction))
                    .rationale(sanitize(fix.getDescription()))
                    .confidence(fix.getConfidence())
                    .evidenceItems(fix.getEvidenceItems())
                    .validationType(ValidationType.NOT_VALIDATED)
                    .approvalRequired(true)
                    .build());
        }
        return recommendations;
    }

    // ------------------------------------------------------------------------------------------
    // Assertion healing — never weakens or removes an assertion; only suggests reviewing expected values.
    // ------------------------------------------------------------------------------------------

    private List<SelfHealingRecommendation> recommendAssertionFixes(FailureDiagnosis diagnosis) {
        List<SelfHealingRecommendation> recommendations = new ArrayList<>();
        for (SuggestedFix fix : diagnosis.getSuggestedFixes()) {
            if (fix.getFixType() != FixType.ASSERTION) {
                continue;
            }
            recommendations.add(SelfHealingRecommendation.builder()
                    .fixType(FixType.ASSERTION)
                    .description(sanitize("Observed application behavior differs from the expected assertion; "
                            + "review the expected value rather than weakening or removing the assertion."))
                    .rationale(sanitize(fix.getDescription()))
                    .confidence(fix.getConfidence())
                    .evidenceItems(fix.getEvidenceItems())
                    .validationType(ValidationType.NOT_VALIDATED)
                    .approvalRequired(true)
                    .build());
        }
        return recommendations;
    }

    private boolean noUsableEvidence(SuggestedFix fix) {
        return fix.getEvidenceItems().isEmpty()
                || fix.getEvidenceItems().stream().allMatch(e -> e.getStatus() == EvidenceStatus.MISSING);
    }

    private String emptyIfBlank(String value) {
        return value == null || value.isBlank() ? "(unknown)" : value;
    }

    private String sanitize(String value) {
        return value == null ? "" : SensitiveDataSanitizer.sanitize(value);
    }
}
