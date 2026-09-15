package com.framework.ai.locatoradvisor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.framework.ai.client.AiClient;
import com.framework.ai.client.GeminiApiClient;
import com.framework.ai.codegeneration.EvidenceStatus;
import com.framework.ai.config.AiConfig;
import com.framework.ai.extractor.DomContextExtractor;
import com.framework.ai.model.AiRequest;
import com.framework.ai.model.AiResponse;
import com.framework.ai.sanitizer.SensitiveDataSanitizer;
import com.framework.config.ConfigManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Orchestrates the Phase 5 Locator Advisor: sanitizes and bounds the DOM,
 * asks the AI for candidate proposals, and then independently re-validates
 * every claim (match count, uniqueness, Page Object reuse) against the
 * supplied evidence before it is ever surfaced.
 *
 * ADVISORY ONLY. Never invoked from normal `mvn test` execution, never
 * modifies source, never executes anything in a live browser.
 */
public class LocatorAnalysisService {

    private static final Logger LOGGER = LogManager.getLogger(LocatorAnalysisService.class);
    private static final int DEFAULT_DOM_MAX_BYTES = 15 * 1024;

    private final AiConfig config;
    private final AiClient aiClient;
    private final ObjectMapper mapper;

    public LocatorAnalysisService() {
        this(new AiConfig(), new GeminiApiClient());
    }

    public LocatorAnalysisService(AiConfig config, AiClient aiClient) {
        this.config = Objects.requireNonNull(config, "AiConfig must not be null");
        this.aiClient = Objects.requireNonNull(aiClient, "AiClient must not be null");
        this.mapper = new ObjectMapper();
    }

    public boolean isAnalysisAvailable() {
        return config.isAiEnabled() && aiClient.isAvailable();
    }

    public LocatorAnalysisResponse analyze(LocatorAnalysisRequest request) {
        if (request == null) {
            return LocatorAnalysisResponse.failure("LocatorAnalysisRequest must not be null");
        }
        if (!config.isAiEnabled()) {
            return LocatorAnalysisResponse.failure("AI is disabled (ai.enabled=false)");
        }
        if (!aiClient.isAvailable()) {
            return LocatorAnalysisResponse.failure("AI client is unavailable or credentials missing");
        }

        try {
            String sanitizedRawDom = SensitiveDataSanitizer.sanitize(request.getDomSnapshot());
            int maxBytes = ConfigManager.getInstance().getInt("ai.dom.max.bytes", DEFAULT_DOM_MAX_BYTES);
            String boundedDom = DomContextExtractor.truncateSafely(sanitizedRawDom, maxBytes);
            boolean domTruncated = boundedDom.contains("[DOM truncated:");

            LocatorAnalysisRequest sanitized = LocatorAnalysisRequest.builder()
                    .domSnapshot(boundedDom)
                    .targetDescription(SensitiveDataSanitizer.sanitize(request.getTargetDescription()))
                    .targetText(SensitiveDataSanitizer.sanitize(request.getTargetText()))
                    .targetRole(request.getTargetRole())
                    .currentUrl(SensitiveDataSanitizer.sanitize(request.getCurrentUrl()))
                    .pageName(request.getPageName())
                    .existingPageObjectContext(SensitiveDataSanitizer.sanitize(request.getExistingPageObjectContext()))
                    .failingLocator(request.getFailingLocator())
                    .failureMessage(SensitiveDataSanitizer.sanitize(request.getFailureMessage()))
                    .build();

            String prompt = LocatorAnalysisPrompt.buildPrompt(sanitized, boundedDom, domTruncated);

            AiRequest aiRequest = AiRequest.builder()
                    .systemInstruction(LocatorAnalysisPrompt.SYSTEM_INSTRUCTION)
                    .prompt(prompt)
                    .temperature(0.2)
                    .maxTokens(2048)
                    .build();

            AiResponse aiResponse = aiClient.generate(aiRequest);
            if (!aiResponse.isSuccess()) {
                LOGGER.warn("AI locator analysis failed: {}", aiResponse.getErrorMessage());
                return LocatorAnalysisResponse.failure("AI generation failed: " + aiResponse.getErrorMessage());
            }

            return parseAndValidate(aiResponse.getContent(), request, sanitized, domTruncated);
        } catch (Exception e) {
            LOGGER.warn("Unexpected error during locator analysis: {}", e.getMessage());
            return LocatorAnalysisResponse.failure("Locator analysis error: " + e.getMessage());
        }
    }

    private LocatorAnalysisResponse parseAndValidate(String rawContent, LocatorAnalysisRequest original,
                                                       LocatorAnalysisRequest sanitized, boolean domTruncated) {
        if (rawContent == null || rawContent.isBlank()) {
            return LocatorAnalysisResponse.failure("AI returned empty content");
        }

        JsonNode root;
        try {
            root = mapper.readTree(cleanJsonContent(rawContent));
        } catch (Exception e) {
            LOGGER.warn("Failed to parse AI locator analysis JSON: {}", e.getMessage());
            return LocatorAnalysisResponse.failure("Failed to parse AI response: " + e.getMessage());
        }

        String targetElement = root.path("targetElement").asText(
                sanitized.getTargetDescription().isBlank() ? "(unnamed target)" : sanitized.getTargetDescription());

        List<String> assumptions = new ArrayList<>();
        root.path("assumptions").forEach(n -> assumptions.add(SensitiveDataSanitizer.sanitize(n.asText())));

        List<String> missingEvidence = new ArrayList<>();
        root.path("missingEvidence").forEach(n -> missingEvidence.add(SensitiveDataSanitizer.sanitize(n.asText())));

        boolean hasDom = sanitized.hasDomEvidence();
        if (!hasDom) {
            missingEvidence.add("No DOM snapshot supplied — locator candidates are UNVERIFIED against actual markup.");
        }
        if (domTruncated) {
            missingEvidence.add("DOM snapshot exceeded the configured size limit and was truncated — full DOM coverage is not guaranteed.");
        }
        if (!sanitized.hasExistingPageObjectContext()) {
            missingEvidence.add("No existing Page Object context supplied — reuse opportunities could not be checked.");
        }

        List<LocatorCandidate> candidates = new ArrayList<>();

        // Failure-mode: always surface the failing locator itself as an explicitly evaluated (likely broken) candidate.
        if (original.hasFailingLocator()) {
            candidates.add(buildFailedLocatorCandidate(original, sanitized));
        }

        for (JsonNode c : root.path("candidates")) {
            String locator = c.path("locator").asText("");
            if (locator.isBlank()) {
                continue;
            }
            LocatorStrategy strategy = LocatorStrategy.fromString(c.path("strategy").asText(""));
            List<String> aiStrengths = toSanitizedList(c.path("strengths"));
            List<String> aiWeaknesses = toSanitizedList(c.path("weaknesses"));
            String rationale = SensitiveDataSanitizer.sanitize(c.path("rationale").asText(""));

            candidates.add(buildCandidate(locator, strategy, sanitized, hasDom, aiStrengths, aiWeaknesses, rationale));
        }

        candidates.sort(candidateOrdering());

        LocatorCandidate recommended = candidates.stream()
                .filter(cand -> cand.getEvidenceStatus() == EvidenceStatus.VERIFIED)
                .findFirst()
                .orElse(null);
        if (recommended == null) {
            missingEvidence.add("No candidate locator could be confirmed unique against the supplied evidence — manual verification required.");
        }

        List<AccessibilityFinding> accessibilityFindings = new ArrayList<>();
        accessibilityFindings.addAll(deterministicAccessibilityScan(sanitized.getDomSnapshot()));
        for (JsonNode a : root.path("accessibilityFindings")) {
            accessibilityFindings.add(AccessibilityFinding.of(
                    SensitiveDataSanitizer.sanitize(a.path("element").asText("")),
                    SensitiveDataSanitizer.sanitize(a.path("issue").asText("")),
                    SensitiveDataSanitizer.sanitize(a.path("recommendation").asText(""))));
        }

        List<PageObjectMatch> pageObjectMatches = new ArrayList<>();
        if (sanitized.hasExistingPageObjectContext()) {
            String poContext = sanitized.getExistingPageObjectContext();
            String claimedMethod = SensitiveDataSanitizer.sanitize(root.path("existingMethodSuggestion").asText(""));
            String className = PageObjectContextMatcher.extractClassName(poContext);

            if (!claimedMethod.isBlank() && PageObjectContextMatcher.containsMethod(poContext, claimedMethod)) {
                pageObjectMatches.add(PageObjectMatch.reuse(claimedMethod, className));
            } else {
                if (!claimedMethod.isBlank()) {
                    assumptions.add("AI suggested Page Object method '" + claimedMethod
                            + "' but it was not found in the supplied Page Object context — discarded to avoid inventing a method.");
                }
                Optional<String> best = PageObjectContextMatcher.findBestMatchingMethod(
                        poContext, sanitized.getTargetDescription(), sanitized.getTargetText());
                if (best.isPresent()) {
                    pageObjectMatches.add(PageObjectMatch.reuse(best.get(), className));
                } else {
                    pageObjectMatches.add(PageObjectMatch.recommendNew(deriveMethodNameStub(sanitized)));
                }
            }
        }

        double overallConfidence = computeOverallConfidence(candidates, hasDom, domTruncated);

        return LocatorAnalysisResponse.builder()
                .success(true)
                .targetElement(targetElement)
                .candidates(candidates)
                .recommendedLocator(recommended)
                .accessibilityFindings(accessibilityFindings)
                .existingPageObjectMatches(pageObjectMatches)
                .assumptions(assumptions)
                .missingEvidence(missingEvidence)
                .overallConfidence(overallConfidence)
                .humanReviewRequired(true)
                .domTruncated(domTruncated)
                .build();
    }

    private LocatorCandidate buildCandidate(String locator, LocatorStrategy strategy, LocatorAnalysisRequest sanitized,
                                             boolean hasDom, List<String> aiStrengths, List<String> aiWeaknesses, String rationale) {
        List<String> strengths = new ArrayList<>(aiStrengths);
        List<String> weaknesses = new ArrayList<>(aiWeaknesses);

        LocatorDomMatcher.MatchResult match = hasDom
                ? LocatorDomMatcher.evaluate(sanitized.getDomSnapshot(), strategy, locator, sanitized.getTargetText())
                : new LocatorDomMatcher.MatchResult(-1, List.of());
        weaknesses.addAll(match.getNotes());

        EvidenceStatus status;
        ValidationType validationType;
        int matchCount = match.getMatchCount();

        if (!hasDom) {
            status = EvidenceStatus.UNVERIFIED;
            validationType = ValidationType.NOT_VALIDATED;
            weaknesses.add("No DOM evidence supplied — cannot confirm this locator exists.");
        } else if (matchCount < 0) {
            status = EvidenceStatus.UNVERIFIED;
            validationType = ValidationType.NOT_VALIDATED;
        } else if (matchCount == 0) {
            status = EvidenceStatus.UNVERIFIED;
            validationType = ValidationType.DOM_MATCHED;
            weaknesses.add("No matching element found in the supplied DOM snapshot.");
        } else if (matchCount == 1) {
            status = EvidenceStatus.VERIFIED;
            validationType = ValidationType.DOM_MATCHED;
            strengths.add("Unique match in supplied DOM snapshot.");
        } else {
            status = EvidenceStatus.UNVERIFIED;
            validationType = ValidationType.DOM_MATCHED;
            weaknesses.add("Matches " + matchCount + " elements in the supplied DOM — not unique.");
        }

        if (strategy.isFragile()) {
            weaknesses.add("Fragile strategy (" + strategy + ") — prefer a resilient accessible locator when evidence allows.");
        }

        int score = computeScore(strategy, status, matchCount, weaknesses);
        double confidence = score / 100.0;

        String recommendation = !rationale.isBlank() ? rationale : defaultRecommendation(status, matchCount);

        return LocatorCandidate.builder()
                .locator(locator)
                .strategy(strategy)
                .evidenceStatus(status)
                .validationType(validationType)
                .matchCount(matchCount)
                .confidence(confidence)
                .score(score)
                .strengths(strengths)
                .weaknesses(weaknesses)
                .recommendation(recommendation)
                .build();
    }

    private LocatorCandidate buildFailedLocatorCandidate(LocatorAnalysisRequest original, LocatorAnalysisRequest sanitized) {
        String locator = original.getFailingLocator();
        LocatorStrategy strategy = guessStrategy(locator);
        boolean hasDom = sanitized.hasDomEvidence();

        LocatorDomMatcher.MatchResult match = hasDom
                ? LocatorDomMatcher.evaluate(sanitized.getDomSnapshot(), strategy, locator, sanitized.getTargetText())
                : new LocatorDomMatcher.MatchResult(-1, List.of());

        List<String> weaknesses = new ArrayList<>(match.getNotes());
        weaknesses.add("This is the locator that FAILED in the test run.");
        if (!sanitized.getFailureMessage().isBlank()) {
            weaknesses.add("Failure message: " + sanitized.getFailureMessage());
        }

        int matchCount = match.getMatchCount();
        EvidenceStatus status = EvidenceStatus.UNVERIFIED;
        ValidationType validationType = (hasDom && matchCount >= 0) ? ValidationType.DOM_MATCHED : ValidationType.NOT_VALIDATED;
        if (hasDom && matchCount == 0) {
            weaknesses.add("No matching element found in the supplied DOM snapshot — consistent with the reported failure.");
        }

        int score = computeScore(strategy, status, matchCount, weaknesses);

        return LocatorCandidate.builder()
                .locator(locator)
                .strategy(strategy)
                .evidenceStatus(status)
                .validationType(validationType)
                .matchCount(matchCount)
                .confidence(score / 100.0)
                .score(score)
                .weaknesses(weaknesses)
                .recommendation("Failed locator — do not reuse as-is. See recommended alternative candidates below.")
                .build();
    }

    private static final Pattern XPATH_HINT = Pattern.compile("^\\s*(xpath=|//|/html)");
    private static final Pattern NTH_HINT = Pattern.compile("\\.nth\\s*\\(|:nth-child|:nth-of-type");

    private LocatorStrategy guessStrategy(String locator) {
        if (locator == null) {
            return LocatorStrategy.UNKNOWN;
        }
        String l = locator.toLowerCase();
        if (l.contains("data-testid") || l.contains("data-test-id") || l.contains("data-test") || l.contains("gettestid")) {
            return LocatorStrategy.TEST_ID;
        }
        if (NTH_HINT.matcher(l).find()) {
            return LocatorStrategy.POSITIONAL;
        }
        if (XPATH_HINT.matcher(l).find()) {
            return LocatorStrategy.XPATH;
        }
        if (l.contains("getbyrole")) {
            return LocatorStrategy.ROLE;
        }
        if (l.contains("getbylabel")) {
            return LocatorStrategy.LABEL;
        }
        if (l.contains("getbyplaceholder")) {
            return LocatorStrategy.PLACEHOLDER;
        }
        if (l.contains("getbytext")) {
            return LocatorStrategy.TEXT;
        }
        if (l.startsWith(".") || l.startsWith("#") || l.contains("[") || l.matches("^[a-z][a-z0-9]*\\.[\\w-]+.*")) {
            return LocatorStrategy.CSS_STABLE;
        }
        return LocatorStrategy.UNKNOWN;
    }

    private int computeScore(LocatorStrategy strategy, EvidenceStatus status, int matchCount, List<String> notes) {
        int base = 100 - (Math.min(strategy.getPriorityRank(), 10) - 1) * 8;

        switch (status) {
            case VERIFIED:
                break;
            case INFERRED:
                base -= 15;
                break;
            case UNVERIFIED:
                base -= 35;
                break;
            case MISSING:
                base = 10;
                break;
        }

        if (matchCount > 1) {
            base -= 30;
        }
        if (strategy.isFragile()) {
            base = Math.min(base, 39);
        }
        boolean generatedFlag = notes.stream().anyMatch(n -> n.toLowerCase().contains("generated") || n.toLowerCase().contains("hashed"));
        if (generatedFlag) {
            base -= 15;
        }

        return Math.max(0, Math.min(100, base));
    }

    private String defaultRecommendation(EvidenceStatus status, int matchCount) {
        return switch (status) {
            case VERIFIED -> "Unique, evidence-backed match — safe to adopt after a final human review.";
            case UNVERIFIED -> matchCount > 1
                    ? "Matches multiple elements — add scoping before use."
                    : "Could not be confirmed against supplied evidence — verify manually before use.";
            case INFERRED -> "Logically reasonable but not directly confirmed by DOM evidence.";
            case MISSING -> "Insufficient evidence to propose this locator with confidence.";
        };
    }

    private Comparator<LocatorCandidate> candidateOrdering() {
        Comparator<LocatorCandidate> byStatus = Comparator.comparingInt(c -> statusRank(c.getEvidenceStatus()));
        Comparator<LocatorCandidate> byScore = Comparator.comparingInt(LocatorCandidate::getScore).reversed();
        Comparator<LocatorCandidate> byStrategy = Comparator.comparingInt(c -> c.getStrategy().getPriorityRank());
        return byStatus.thenComparing(byScore).thenComparing(byStrategy);
    }

    private int statusRank(EvidenceStatus status) {
        return switch (status) {
            case VERIFIED -> 0;
            case INFERRED -> 1;
            case UNVERIFIED -> 2;
            case MISSING -> 3;
        };
    }

    private double computeOverallConfidence(List<LocatorCandidate> candidates, boolean hasDom, boolean domTruncated) {
        if (candidates.isEmpty()) {
            return 0.0;
        }
        double max = candidates.stream().mapToDouble(LocatorCandidate::getConfidence).max().orElse(0.0);
        if (!hasDom) {
            max = Math.min(max, 0.4);
        }
        if (domTruncated) {
            max *= 0.85;
        }
        return Math.round(max * 100.0) / 100.0;
    }

    private String deriveMethodNameStub(LocatorAnalysisRequest sanitized) {
        String basis = !sanitized.getTargetDescription().isBlank() ? sanitized.getTargetDescription() : sanitized.getTargetText();
        if (basis == null || basis.isBlank()) {
            return "interactWithTargetElement";
        }
        String[] words = basis.trim().toLowerCase().split("[^a-zA-Z0-9]+");
        StringBuilder name = new StringBuilder("interactWith");
        for (String w : words) {
            if (w.isBlank()) continue;
            name.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return name.toString();
    }

    private List<AccessibilityFinding> deterministicAccessibilityScan(String dom) {
        List<AccessibilityFinding> findings = new ArrayList<>();
        if (dom == null || dom.isBlank()) {
            return findings;
        }

        Matcher buttons = Pattern.compile("(?is)<button\\b([^>]*)>(.*?)</button>").matcher(dom);
        while (buttons.find()) {
            String attrs = buttons.group(1) == null ? "" : buttons.group(1).toLowerCase();
            String inner = buttons.group(2) == null ? "" : buttons.group(2).replaceAll("<[^>]+>", " ").trim();
            if (!attrs.contains("aria-label") && inner.isEmpty()) {
                findings.add(AccessibilityFinding.of("button", "missing accessible name",
                        "Add an accessible name, e.g. aria-label='<describe the action>', or visible text content."));
            }
        }

        Matcher inputs = Pattern.compile("(?is)<input\\b([^>]*)/?>").matcher(dom);
        while (inputs.find()) {
            String attrs = inputs.group(1) == null ? "" : inputs.group(1).toLowerCase();
            if (attrs.contains("type=\"hidden\"") || attrs.contains("type='hidden'")) {
                continue;
            }
            if (!attrs.contains("aria-label") && !attrs.contains("placeholder") && !attrs.contains("id=")) {
                findings.add(AccessibilityFinding.of("input", "missing label association",
                        "Add a <label for=...>, aria-label, or aria-labelledby so the field has an accessible name."));
            }
        }

        return findings;
    }

    private List<String> toSanitizedList(JsonNode arrayNode) {
        List<String> out = new ArrayList<>();
        if (arrayNode != null && arrayNode.isArray()) {
            arrayNode.forEach(n -> out.add(SensitiveDataSanitizer.sanitize(n.asText())));
        }
        return out;
    }

    private String cleanJsonContent(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.trim();
    }
}
