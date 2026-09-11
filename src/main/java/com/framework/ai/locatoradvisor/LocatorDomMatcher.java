package com.framework.ai.locatoradvisor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic, offline structural analysis of a locator candidate against a
 * static DOM snapshot string.
 *
 * This is the anti-hallucination gate for Phase 5: it never trusts an AI claim
 * of "unique match" or "verified" — it independently counts occurrences in the
 * supplied DOM text using regex-based heuristics (no HTML parser dependency is
 * introduced, per the zero-new-dependency rule).
 *
 * Limitations are intentional and disclosed rather than papered over: this is
 * text-pattern matching, not a real DOM tree walk, so nested/malformed markup
 * or non-standard attribute ordering can produce approximate counts. Callers
 * must treat a {@link ValidationType#DOM_MATCHED} result as "matched against
 * the supplied snapshot text", never as a live browser execution.
 */
public final class LocatorDomMatcher {

    private static final Pattern ABSOLUTE_XPATH_PATTERN =
            Pattern.compile("(?i)(^\\s*/html/body|//div\\[\\d+\\]|/html/body)");
    private static final Pattern POSITIONAL_PATTERN =
            Pattern.compile("(?i)\\.nth\\s*\\(|:nth-child|:nth-of-type|\\[\\d+\\]\\s*$");
    private static final Pattern GENERATED_CLASS_PATTERN =
            Pattern.compile("(?i)^(css|sc|jsx|styled|makeStyles)-[a-z0-9]{4,}$|^[a-z]{1,3}-[a-f0-9]{5,}$|[a-f0-9]{8,}");
    private static final Pattern ATTR_VALUE_PAIR_PATTERN =
            Pattern.compile("\\[?([\\w:-]+)\\s*=\\s*['\"]([^'\"]+)['\"]\\]?");

    private LocatorDomMatcher() {
    }

    public static final class MatchResult {
        private final int matchCount;
        private final List<String> notes;

        MatchResult(int matchCount, List<String> notes) {
            this.matchCount = matchCount;
            this.notes = notes;
        }

        /** Number of elements matched in the supplied DOM text, or -1 if this strategy cannot be evaluated offline. */
        public int getMatchCount() {
            return matchCount;
        }

        public List<String> getNotes() {
            return notes;
        }

        public boolean isEvaluable() {
            return matchCount >= 0;
        }
    }

    /**
     * Evaluates a candidate locator against the supplied DOM snapshot.
     *
     * @param dom          sanitized DOM snapshot text (may be blank/unavailable)
     * @param strategy     the candidate's locator strategy
     * @param locatorValue the locator string itself (e.g. "[data-testid='cart-plus']" or "Add to cart")
     * @param targetText   optional accessible name / text hint, used for ROLE and TEXT strategies
     * @return a MatchResult; matchCount == -1 means "not evaluable offline", never "zero matches"
     */
    public static MatchResult evaluate(String dom, LocatorStrategy strategy, String locatorValue, String targetText) {
        List<String> notes = new ArrayList<>();
        if (dom == null || dom.isBlank()) {
            return new MatchResult(-1, notes);
        }
        if (locatorValue == null || locatorValue.isBlank()) {
            return new MatchResult(-1, notes);
        }

        switch (strategy) {
            case TEST_ID:
                return evaluateTestId(dom, locatorValue, notes);
            case LABEL:
                return evaluateAttributeOrTag(dom, locatorValue, "aria-label", notes);
            case PLACEHOLDER:
                return evaluateAttributeOrTag(dom, locatorValue, "placeholder", notes);
            case CSS_ATTRIBUTE:
            case CSS_STABLE:
                return evaluateCss(dom, locatorValue, notes);
            case ROLE:
                return evaluateRole(dom, locatorValue, targetText, notes);
            case TEXT:
                return evaluateText(dom, locatorValue, notes);
            case XPATH:
                if (ABSOLUTE_XPATH_PATTERN.matcher(locatorValue).find()) {
                    notes.add("Absolute/positional XPath detected — brittle across markup changes.");
                }
                notes.add("XPath cannot be structurally validated offline without a DOM parser.");
                return new MatchResult(-1, notes);
            case POSITIONAL:
                notes.add("Positional selector — fragile to reordering or insertion of sibling elements.");
                return new MatchResult(-1, notes);
            default:
                notes.add("Unknown locator strategy — cannot be validated offline.");
                return new MatchResult(-1, notes);
        }
    }

    private static MatchResult evaluateTestId(String dom, String locatorValue, List<String> notes) {
        AttrValue pair = extractAttrValuePair(locatorValue);
        String value = pair != null ? pair.value : stripToRawValue(locatorValue);

        int count = countAttributeOccurrences(dom, "data-testid", value);
        if (count == 0) {
            count = countAttributeOccurrences(dom, "data-test-id", value);
        }
        if (count == 0) {
            count = countAttributeOccurrences(dom, "data-test", value);
        }
        if (count == 0) {
            count = countAttributeOccurrences(dom, "data-qa", value);
        }
        if (count == 0) {
            count = countAttributeOccurrences(dom, "data-cy", value);
        }
        return new MatchResult(count, notes);
    }

    private static MatchResult evaluateAttributeOrTag(String dom, String locatorValue, String attrName, List<String> notes) {
        String value = extractPrimaryArgument(locatorValue);
        int count = countAttributeOccurrences(dom, attrName, value);
        if (count == 0 && attrName.equals("aria-label")) {
            // Fall back to associated <label> text for form-field labeling.
            Pattern labelTag = Pattern.compile("(?i)<label\\b[^>]*>\\s*" + Pattern.quote(value) + "\\s*<");
            Matcher m = labelTag.matcher(dom);
            int labelCount = 0;
            while (m.find()) labelCount++;
            if (labelCount > 0) {
                count = labelCount;
                notes.add("Matched via associated <label> text, not aria-label.");
            }
        }
        return new MatchResult(count, notes);
    }

    private static MatchResult evaluateCss(String dom, String locatorValue, List<String> notes) {
        AttrValue pair = extractAttrValuePair(locatorValue);
        if (pair != null) {
            if (GENERATED_CLASS_PATTERN.matcher(pair.value).find()
                    && (pair.attr.equalsIgnoreCase("class") || pair.attr.equalsIgnoreCase("id"))) {
                notes.add("Value '" + pair.value + "' looks like a generated/hashed " + pair.attr + " — likely to change on rebuild.");
            }
            int count = countAttributeOccurrences(dom, pair.attr, pair.value);
            return new MatchResult(count, notes);
        }

        // Plain class selector (".inventory_list") or tag.class selector ("button.plus")
        String className = extractClassToken(locatorValue);
        if (className != null) {
            if (GENERATED_CLASS_PATTERN.matcher(className).find()) {
                notes.add("Class '" + className + "' looks like a generated/hashed class name — likely to change on rebuild.");
            }
            Pattern classPattern = Pattern.compile(
                    "(?i)class\\s*=\\s*['\"][^'\"]*\\b" + Pattern.quote(className) + "\\b[^'\"]*['\"]");
            Matcher m = classPattern.matcher(dom);
            int count = 0;
            while (m.find()) count++;
            return new MatchResult(count, notes);
        }

        notes.add("Could not parse selector into an attribute/value pair for structural validation.");
        return new MatchResult(-1, notes);
    }

    private static final Pattern TAG_DOT_CLASS_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9]*\\.([\\w-]+)");

    private static String extractClassToken(String locatorValue) {
        if (locatorValue.startsWith(".")) {
            return locatorValue.substring(1).split("[.\\s>#\\[]")[0];
        }
        Matcher m = TAG_DOT_CLASS_PATTERN.matcher(locatorValue.trim());
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private static final Pattern GET_BY_ROLE_PATTERN = Pattern.compile(
            "(?i)getbyrole\\(\\s*['\"]([a-zA-Z]+)['\"]\\s*(?:,\\s*\\{[^}]*name\\s*:\\s*['\"]([^'\"]+)['\"])?");

    private static MatchResult evaluateRole(String dom, String roleValue, String targetText, List<String> notes) {
        String role = roleValue == null ? "" : roleValue.trim().toLowerCase();
        String accessibleName = targetText;

        Matcher grbm = GET_BY_ROLE_PATTERN.matcher(roleValue == null ? "" : roleValue);
        if (grbm.find()) {
            role = grbm.group(1).toLowerCase();
            if (grbm.group(2) != null && !grbm.group(2).isBlank()) {
                accessibleName = grbm.group(2);
            }
        }
        targetText = accessibleName;

        String tagPattern = roleTagPattern(role);
        if (tagPattern == null) {
            notes.add("Role '" + role + "' has no known tag heuristic — cannot be validated offline.");
            return new MatchResult(-1, notes);
        }

        Pattern elementPattern = Pattern.compile("(?is)<(" + tagPattern + ")\\b([^>]*)>(.*?)</\\1>");
        Matcher m = elementPattern.matcher(dom);
        int count = 0;
        int totalTagMatches = 0;
        String needle = targetText == null ? "" : targetText.trim().toLowerCase();

        while (m.find()) {
            totalTagMatches++;
            if (needle.isEmpty()) {
                count++;
                continue;
            }
            String attrs = m.group(2) == null ? "" : m.group(2).toLowerCase();
            String inner = m.group(3) == null ? "" : m.group(3).replaceAll("<[^>]+>", " ").toLowerCase();
            if (attrs.contains("aria-label=\"" + needle + "\"") || attrs.contains("aria-label='" + needle + "'")
                    || inner.contains(needle)) {
                count++;
            }
        }

        if (!needle.isEmpty() && count == 0 && totalTagMatches > 0) {
            notes.add("Found " + totalTagMatches + " element(s) with role '" + role + "' but none matched the accessible name '"
                    + targetText + "'.");
        }
        return new MatchResult(count, notes);
    }

    private static MatchResult evaluateText(String dom, String locatorValue, List<String> notes) {
        String plainText = dom.replaceAll("(?is)<script.*?</script>", " ")
                .replaceAll("(?is)<style.*?</style>", " ")
                .replaceAll("<[^>]+>", " ");
        String needle = extractPrimaryArgument(locatorValue);
        if (needle.isEmpty()) {
            return new MatchResult(-1, notes);
        }
        Pattern p = Pattern.compile(Pattern.quote(needle), Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(plainText);
        int count = 0;
        while (m.find()) count++;
        notes.add("Text-based matching is sensitive to copy/localization changes.");
        return new MatchResult(count, notes);
    }

    private static String roleTagPattern(String role) {
        switch (role) {
            case "button": return "button";
            case "link": return "a";
            case "checkbox": return "input";
            case "radio": return "input";
            case "textbox": return "input|textarea";
            case "combobox": return "select";
            case "heading": return "h1|h2|h3|h4|h5|h6";
            case "list": return "ul|ol";
            case "listitem": return "li";
            case "img": case "image": return "img";
            default: return null;
        }
    }

    private static int countAttributeOccurrences(String dom, String attrName, String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        Pattern p = Pattern.compile(
                "(?i)" + Pattern.quote(attrName) + "\\s*=\\s*['\"]" + Pattern.quote(value) + "['\"]");
        Matcher m = p.matcher(dom);
        int count = 0;
        while (m.find()) count++;
        return count;
    }

    private static final Pattern QUOTED_ARG_PATTERN = Pattern.compile("\\(\\s*['\"]([^'\"]+)['\"]");

    /**
     * Extracts the primary string argument from either a full Playwright expression
     * (e.g. getByLabel('Email'), getByText("Add to cart")) or falls back to treating
     * the whole value as a raw string.
     */
    private static String extractPrimaryArgument(String locatorValue) {
        if (locatorValue == null) {
            return "";
        }
        Matcher m = QUOTED_ARG_PATTERN.matcher(locatorValue);
        if (m.find()) {
            return m.group(1).trim();
        }
        return stripToRawValue(locatorValue);
    }

    private static String stripToRawValue(String locatorValue) {
        String v = locatorValue.trim();
        v = v.replaceAll("^\\[|\\]$", "");
        v = v.replaceAll("^['\"]|['\"]$", "");
        int eq = v.indexOf('=');
        if (eq >= 0) {
            v = v.substring(eq + 1).replaceAll("^['\"]|['\"]$", "");
        }
        return v.trim();
    }

    private static final class AttrValue {
        final String attr;
        final String value;

        AttrValue(String attr, String value) {
            this.attr = attr;
            this.value = value;
        }
    }

    private static AttrValue extractAttrValuePair(String locatorValue) {
        Matcher m = ATTR_VALUE_PAIR_PATTERN.matcher(locatorValue);
        if (m.find()) {
            return new AttrValue(m.group(1), m.group(2));
        }
        return null;
    }

    public static boolean isFragileSelectorText(String locatorValue) {
        if (locatorValue == null) {
            return false;
        }
        return ABSOLUTE_XPATH_PATTERN.matcher(locatorValue).find()
                || POSITIONAL_PATTERN.matcher(locatorValue).find();
    }
}
