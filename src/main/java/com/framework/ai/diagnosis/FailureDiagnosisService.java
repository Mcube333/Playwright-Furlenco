package com.framework.ai.diagnosis;

import com.framework.ai.codegeneration.EvidenceItem;
import com.framework.ai.codegeneration.EvidenceStatus;
import com.framework.ai.config.AiConfig;
import com.framework.ai.locatoradvisor.LocatorAnalysisRequest;
import com.framework.ai.locatoradvisor.LocatorAnalysisResponse;
import com.framework.ai.locatoradvisor.LocatorAnalysisService;
import com.framework.ai.locatoradvisor.LocatorCandidate;
import com.framework.ai.locatoradvisor.runtime.RuntimeLocatorValidator;
import com.framework.ai.locatoradvisor.runtime.RuntimeValidationResult;
import com.framework.ai.model.AiAnalysisResponse;
import com.framework.ai.model.FailureCategory;
import com.framework.ai.model.FailureContext;
import com.framework.ai.service.FailureAnalysisService;
import com.microsoft.playwright.Page;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Phase 7 Step 3: standalone orchestrator combining existing Phase 2 ({@link FailureAnalysisService}),
 * Phase 5 ({@link LocatorAnalysisService}), and optional Phase 6 ({@link RuntimeLocatorValidator})
 * into one {@link FailureDiagnosis}.
 *
 * PURE ORCHESTRATION. None of the three collaborators are modified, subclassed to change behavior,
 * or reimplemented — this class only calls their existing public APIs and combines their outputs.
 * It computes NO {@link EvidenceStatus} itself anywhere: every evidence value in the resulting
 * diagnosis is copied verbatim from whichever of the three services produced it.
 *
 * ADDITIVE, OPTIONAL, EXPLICITLY INVOKED. Not referenced from {@code TestListener},
 * {@code BaseTest}/{@code BaseWebTest}, {@code RetryAnalyzer}/{@code RetryTransformer}, or
 * {@code PlaywrightManager} — nothing in the existing TestNG lifecycle calls this class.
 *
 * STATELESS. All fields are final, constructor-injected collaborators; no mutable instance or
 * static state is held anywhere, so a single instance is safe to reuse across parallel callers
 * provided its injected collaborators are themselves safe (which they already are — see Phase 5/6).
 */
public class FailureDiagnosisService {

    private static final Logger LOGGER = LogManager.getLogger(FailureDiagnosisService.class);
    private static final String DOM_UNAVAILABLE = "[DOM context unavailable]";

    /**
     * Conservative category gate: only these two categories plausibly indicate a locator/element/
     * visibility/interaction problem worth spending a second (Phase 5) AI call on. Every other
     * category — including UNCERTAIN/UNKNOWN and clearly backend-shaped categories
     * (API_FAILURE, DATA_ISSUE, AUTHENTICATION_FAILURE, NETWORK_FAILURE, ENVIRONMENT_FAILURE) —
     * skips locator analysis entirely. TIMEOUT is included because, in this Playwright-based
     * framework, a timeout overwhelmingly means "waiting for an element" rather than a generic
     * infrastructure timeout. FailureCategory itself is never modified.
     */
    private static final Set<FailureCategory> LOCATOR_RELEVANT_CATEGORIES =
            Set.of(FailureCategory.LOCATOR_CHANGED, FailureCategory.TIMEOUT);

    private final AiConfig aiConfig;
    private final FailureAnalysisService failureAnalysisService;
    private final LocatorAnalysisService locatorAnalysisService;
    private final RuntimeLocatorValidator runtimeLocatorValidator;

    public FailureDiagnosisService() {
        this(new AiConfig(), new FailureAnalysisService(), new LocatorAnalysisService(), new RuntimeLocatorValidator());
    }

    public FailureDiagnosisService(AiConfig aiConfig,
                                    FailureAnalysisService failureAnalysisService,
                                    LocatorAnalysisService locatorAnalysisService,
                                    RuntimeLocatorValidator runtimeLocatorValidator) {
        this.aiConfig = Objects.requireNonNull(aiConfig, "AiConfig must not be null");
        this.failureAnalysisService = Objects.requireNonNull(failureAnalysisService, "FailureAnalysisService must not be null");
        this.locatorAnalysisService = Objects.requireNonNull(locatorAnalysisService, "LocatorAnalysisService must not be null");
        this.runtimeLocatorValidator = Objects.requireNonNull(runtimeLocatorValidator, "RuntimeLocatorValidator must not be null");
    }

    /** Diagnoses a failure with no live Page available (e.g. API-only test). Runtime validation is then simply skipped. */
    public FailureDiagnosis diagnose(FailureContext failureContext) {
        return diagnose(failureContext, null);
    }

    /**
     * Diagnoses a failure, optionally attempting Phase 6 runtime validation against {@code page}.
     * {@code page} may be null — runtime validation then remains unavailable, never an error.
     * Never throws: every failure mode returns a usable (possibly mostly-empty) {@link FailureDiagnosis}.
     */
    public FailureDiagnosis diagnose(FailureContext failureContext, Page page) {
        try {
            if (failureContext == null) {
                // No invented failure information — an empty, valid diagnosis, not an exception.
                return FailureDiagnosis.builder().build();
            }

            FailureDiagnosis.Builder result = FailureDiagnosis.builder().failureContext(failureContext);

            AiAnalysisResponse aiAnalysis = safelyAnalyzeFailure(failureContext);
            result.aiAnalysis(aiAnalysis);

            if (aiAnalysis == null) {
                // AI disabled/unavailable/malformed — Phase 2 already logged why. No locator
                // analysis is possible without it (nothing to conservatively filter on), and no
                // suggested fixes can be derived beyond "insufficient evidence."
                return result.build();
            }

            LocatorAnalysisResponse locatorAnalysis = null;
            RuntimeValidationResult runtimeResult = null;
            boolean locatorFixAdded = false;

            if (shouldAnalyzeLocators(aiAnalysis) && hasUsefulLocatorEvidence(failureContext, aiAnalysis)) {
                locatorAnalysis = safelyAnalyzeLocators(failureContext, aiAnalysis);

                if (locatorAnalysis != null && locatorAnalysis.isSuccess()) {
                    LocatorCandidate topCandidate = pickTopCandidate(locatorAnalysis);
                    if (page != null && topCandidate != null) {
                        runtimeResult = safelyValidateRuntime(page, topCandidate);
                    }
                    result.addSuggestedFix(buildLocatorSuggestedFix(topCandidate, runtimeResult));
                    locatorFixAdded = true;
                }
            }

            if (!locatorFixAdded) {
                result.addSuggestedFix(buildNonLocatorSuggestedFix(aiAnalysis));
            }

            return result.locatorAnalysis(locatorAnalysis).runtimeValidation(runtimeResult).build();

        } catch (Exception e) {
            // Absolute boundary: diagnosis must never throw into the caller/test.
            LOGGER.warn("Unexpected error during failure diagnosis: {}", e.getMessage());
            return FailureDiagnosis.builder().failureContext(failureContext).build();
        }
    }

    // ------------------------------------------------------------------------------------------
    // Collaborator calls, each individually failure-isolated so one optional component failing
    // never prevents returning whatever the others already produced.
    // ------------------------------------------------------------------------------------------

    private AiAnalysisResponse safelyAnalyzeFailure(FailureContext context) {
        try {
            return failureAnalysisService.analyze(context);
        } catch (Exception e) {
            LOGGER.warn("Phase 2 failure analysis threw unexpectedly: {}", e.getMessage());
            return null;
        }
    }

    private LocatorAnalysisResponse safelyAnalyzeLocators(FailureContext context, AiAnalysisResponse aiAnalysis) {
        try {
            return locatorAnalysisService.analyze(buildLocatorRequest(context, aiAnalysis));
        } catch (Exception e) {
            LOGGER.warn("Phase 5 locator analysis threw unexpectedly: {}", e.getMessage());
            return null;
        }
    }

    private RuntimeValidationResult safelyValidateRuntime(Page page, LocatorCandidate candidate) {
        try {
            // RuntimeLocatorValidator already internally enforces ai.locator.runtime.validation.enabled
            // and RuntimeEnvironmentGuard — neither check is duplicated or re-implemented here.
            return runtimeLocatorValidator.validate(page, candidate);
        } catch (Exception e) {
            LOGGER.warn("Phase 6 runtime validation threw unexpectedly: {}", e.getMessage());
            return null;
        }
    }

    // ------------------------------------------------------------------------------------------
    // Conservative locator-relevance filtering
    // ------------------------------------------------------------------------------------------

    private boolean shouldAnalyzeLocators(AiAnalysisResponse aiAnalysis) {
        if (aiAnalysis == null || aiAnalysis.getCategory() == null) {
            return false; // uncertain/unavailable category -> do not assume locator failure
        }
        return LOCATOR_RELEVANT_CATEGORIES.contains(aiAnalysis.getCategory());
    }

    /** "No useful evidence -> do not invent a locator": skip the Phase 5 AI call entirely unless
     *  there is at least a DOM snapshot or an AI-suggested locator string to actually evaluate. */
    private boolean hasUsefulLocatorEvidence(FailureContext context, AiAnalysisResponse aiAnalysis) {
        boolean hasDom = context.getDomSnippet() != null
                && !context.getDomSnippet().isBlank()
                && !DOM_UNAVAILABLE.equals(context.getDomSnippet());
        boolean hasSuggestedLocator = aiAnalysis.getSuggestedLocators() != null
                && !aiAnalysis.getSuggestedLocators().isEmpty();
        return hasDom || hasSuggestedLocator;
    }

    // ------------------------------------------------------------------------------------------
    // LocatorAnalysisRequest construction — reuses FailureContext's already-captured, existing
    // DOM snapshot as-is. No new DOM extraction, no Page.content() call, no bypass of Phase 5's
    // own sanitization/bounding (LocatorAnalysisService sanitizes/truncates every field itself).
    // ------------------------------------------------------------------------------------------

    private LocatorAnalysisRequest buildLocatorRequest(FailureContext context, AiAnalysisResponse aiAnalysis) {
        LocatorAnalysisRequest.Builder builder = LocatorAnalysisRequest.builder()
                .domSnapshot(context.getDomSnippet())
                .currentUrl(context.getCurrentUrl())
                .pageName(context.getTestClass())
                .targetDescription(aiAnalysis.getSummary())
                .failureMessage(context.getErrorMessage());

        // AI-suggested locators are proposals only — fed through Phase 5's existing failure-mode
        // mechanism so they are independently, deterministically re-evaluated against the DOM.
        // The AI itself never determines VERIFIED; LocatorDomMatcher does.
        List<String> suggested = aiAnalysis.getSuggestedLocators();
        if (suggested != null && !suggested.isEmpty()) {
            builder.failingLocator(suggested.get(0));
        }

        return builder.build();
    }

    /** Prefer the DOM-verified recommended candidate; otherwise the first evaluated candidate
     *  (its true, independently-determined evidence status — never hidden just because it isn't
     *  "recommended"). Returns null only when there is truly no candidate at all — never fabricated. */
    private LocatorCandidate pickTopCandidate(LocatorAnalysisResponse response) {
        if (response == null) {
            return null;
        }
        if (response.getRecommendedLocator() != null) {
            return response.getRecommendedLocator();
        }
        List<LocatorCandidate> candidates = response.getCandidates();
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    // ------------------------------------------------------------------------------------------
    // SuggestedFix construction — pure aggregation, no evidence computed here.
    // ------------------------------------------------------------------------------------------

    private SuggestedFix buildLocatorSuggestedFix(LocatorCandidate candidate, RuntimeValidationResult runtimeResult) {
        if (candidate == null) {
            return SuggestedFix.builder()
                    .fixType(FixType.LOCATOR)
                    .description("No locator candidate could be derived from the available evidence.")
                    .confidence(0.0)
                    .addEvidenceItem(EvidenceItem.builder()
                            .item("Locator")
                            .value("")
                            .status(EvidenceStatus.MISSING)
                            .source("FailureDiagnosisService")
                            .confidence(0.0)
                            .build())
                    .build();
        }

        SuggestedFix.Builder builder = SuggestedFix.builder()
                .fixType(FixType.LOCATOR)
                .description(honestLocatorDescription(candidate))
                .relatedLocatorCandidate(candidate) // the candidate object itself is never mutated
                .confidence(candidate.getConfidence())
                .addEvidenceItem(EvidenceItem.builder()
                        .item("Locator (static DOM evidence)")
                        .value(candidate.getLocator())
                        .status(candidate.getEvidenceStatus()) // verbatim from Phase 5 — never recomputed
                        .source("LocatorAnalysisService (" + candidate.getValidationType() + ")")
                        .confidence(candidate.getConfidence())
                        .build());

        if (runtimeResult != null) {
            // Runtime evidence sits ALONGSIDE static evidence — never merged into or overwriting it.
            builder.addEvidenceItem(EvidenceItem.builder()
                    .item("Locator (runtime evidence)")
                    .value(runtimeResult.getLocator())
                    .status(runtimeResult.getEvidenceStatus()) // verbatim from Phase 6 — never recomputed
                    .source("RuntimeLocatorValidator (" + runtimeResult.getValidationType() + ")")
                    .confidence(runtimeResult.getEvidenceStatus() == EvidenceStatus.VERIFIED ? 1.0 : 0.0)
                    .build());
        }

        return builder.build();
    }

    /** Prefer an incomplete but honest description over a complete but hallucinated one — never
     *  claims a specific Page Object method/locator replacement unless the candidate's own
     *  evidence actually supports it. */
    private String honestLocatorDescription(LocatorCandidate candidate) {
        if (candidate.getEvidenceStatus() == EvidenceStatus.VERIFIED) {
            return "Review the DOM-verified locator candidate before adopting it: " + candidate.getLocator();
        }
        return "Review the proposed locator candidate against the actual DOM/QA environment before adopting it "
                + "— it is not yet confirmed unique/visible: " + candidate.getLocator();
    }

    /** Non-locator (or no-useful-evidence) fix: the AI's own free-text reasoning is surfaced, but
     *  only ever as INFERRED evidence — unverified reasoning, never elevated to VERIFIED. */
    private SuggestedFix buildNonLocatorSuggestedFix(AiAnalysisResponse aiAnalysis) {
        return SuggestedFix.builder()
                .fixType(classifyFixType(aiAnalysis))
                .description(aiAnalysis.getSuggestedFix())
                .confidence(aiAnalysis.getConfidenceScore())
                .addEvidenceItem(EvidenceItem.builder()
                        .item("Suggested fix")
                        .value(aiAnalysis.getSuggestedFix())
                        .status(EvidenceStatus.INFERRED) // AI reasoning is never more than INFERRED here
                        .source("FailureAnalysisService (AI reasoning, not independently verified)")
                        .confidence(aiAnalysis.getConfidenceScore())
                        .build())
                .build();
    }

    /**
     * Classifies the KIND of a non-locator fix suggestion from the AI's own already-produced text
     * (summary/rootCause/suggestedFix — no new AI call), falling back to a conservative
     * per-category default. This only labels what TYPE of suggestion it is; it never marks the
     * suggestion's evidence as more than INFERRED (see {@link #buildNonLocatorSuggestedFix}) — a
     * text-based classification is not new evidence, and no category is forced when the signal
     * is too weak (defaults to {@link FixType#UNKNOWN} rather than guessing further).
     */
    private FixType classifyFixType(AiAnalysisResponse aiAnalysis) {
        String text = (nullToEmpty(aiAnalysis.getSummary()) + " "
                + nullToEmpty(aiAnalysis.getRootCause()) + " "
                + nullToEmpty(aiAnalysis.getSuggestedFix())).toLowerCase(Locale.ROOT);

        if (containsAny(text, "analytics", "tracking event", "ga4", "segment event")) {
            return FixType.ANALYTICS;
        }
        if (containsAny(text, "assert", "assertion", "expected value", "expected result")) {
            return FixType.ASSERTION;
        }
        if (containsAny(text, "wait", "timing", "race condition")) {
            return FixType.WAIT;
        }
        if (containsAny(text, "test data", "dataset", "seed data", "fixture data")) {
            return FixType.TEST_DATA;
        }

        FailureCategory category = aiAnalysis.getCategory();
        if (category == null) {
            return FixType.UNKNOWN;
        }
        return switch (category) {
            case APPLICATION_BUG -> FixType.APPLICATION_BEHAVIOR;
            case DATA_ISSUE -> FixType.TEST_DATA;
            case API_FAILURE -> FixType.API;
            case TIMEOUT -> FixType.WAIT;
            default -> FixType.UNKNOWN;
        };
    }

    private static boolean containsAny(String haystack, String... needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }
}
