package com.framework.ai.locatoradvisor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic keyword-overlap matcher between a target element description
 * and the supplied existing Page Object source/context string.
 *
 * Exists specifically to enforce "do not invent Page Object methods": any
 * method name surfaced to the user MUST have been extracted verbatim from the
 * supplied context text, never proposed from AI memory alone.
 */
public final class PageObjectContextMatcher {

    private static final Pattern CLASS_NAME_PATTERN = Pattern.compile("\\bclass\\s+([A-Za-z_$][A-Za-z0-9_$]*)");
    private static final Pattern METHOD_DECLARATION_PATTERN =
            Pattern.compile("(?:public|private|protected)\\s+[\\w<>\\[\\],\\s]+?\\s+([a-zA-Z_$][a-zA-Z0-9_$]*)\\s*\\(");
    private static final Set<String> IGNORED_METHOD_NAMES = Set.of(
            "if", "for", "while", "switch", "catch", "synchronized");

    private PageObjectContextMatcher() {
    }

    public static List<String> extractMethodNames(String context) {
        List<String> methods = new ArrayList<>();
        if (context == null || context.isBlank()) {
            return methods;
        }
        Matcher m = METHOD_DECLARATION_PATTERN.matcher(context);
        Set<String> seen = new LinkedHashSet<>();
        while (m.find()) {
            String name = m.group(1);
            if (!IGNORED_METHOD_NAMES.contains(name)) {
                seen.add(name);
            }
        }
        methods.addAll(seen);
        return methods;
    }

    public static String extractClassName(String context) {
        if (context == null || context.isBlank()) {
            return "";
        }
        Matcher m = CLASS_NAME_PATTERN.matcher(context);
        return m.find() ? m.group(1) : "";
    }

    /**
     * Returns true only if {@code methodName} appears verbatim as a declared method
     * in {@code context}. Used to reject AI-claimed method names that were not
     * actually present in the supplied evidence.
     */
    public static boolean containsMethod(String context, String methodName) {
        if (methodName == null || methodName.isBlank()) {
            return false;
        }
        return extractMethodNames(context).stream().anyMatch(m -> m.equalsIgnoreCase(methodName));
    }

    /**
     * Finds the existing method whose name most overlaps (by word) with the target
     * description/text, using simple camelCase tokenization. Returns empty when no
     * method shares any keyword with the target.
     */
    public static Optional<String> findBestMatchingMethod(String context, String targetDescription, String targetText) {
        List<String> methods = extractMethodNames(context);
        if (methods.isEmpty()) {
            return Optional.empty();
        }

        Set<String> targetWords = tokenize(targetDescription);
        targetWords.addAll(tokenize(targetText));
        if (targetWords.isEmpty()) {
            return Optional.empty();
        }

        String best = null;
        int bestScore = 0;
        for (String method : methods) {
            Set<String> methodWords = tokenize(splitCamelCase(method));
            int overlap = 0;
            for (String w : methodWords) {
                if (targetWords.contains(w)) {
                    overlap++;
                }
            }
            if (overlap > bestScore) {
                bestScore = overlap;
                best = method;
            }
        }
        return bestScore > 0 ? Optional.of(best) : Optional.empty();
    }

    private static String splitCamelCase(String s) {
        return s.replaceAll("([a-z])([A-Z])", "$1 $2");
    }

    private static Set<String> tokenize(String text) {
        Set<String> words = new LinkedHashSet<>();
        if (text == null || text.isBlank()) {
            return words;
        }
        for (String w : text.toLowerCase().split("[^a-zA-Z0-9]+")) {
            if (w.length() > 2) {
                words.add(w);
            }
        }
        return words;
    }
}
