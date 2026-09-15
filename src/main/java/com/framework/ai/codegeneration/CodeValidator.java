package com.framework.ai.codegeneration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Performs static security, syntax, and framework-convention validation
 * on AI-generated Java draft code.
 */
public final class CodeValidator {

    // Forbidden Thread.sleep
    private static final Pattern THREAD_SLEEP_PATTERN = Pattern.compile("Thread\\.sleep\\s*\\(");

    // Unsafe absolute or fragile locators
    private static final Pattern UNTRUSTED_XPATH_PATTERN = Pattern.compile("(?i)(xpath\\s*=\\s*['\"]?/html/body|//div\\[\\d+\\]|/html/body)");
    private static final Pattern POSITION_NTH_PATTERN = Pattern.compile("\\.nth\\s*\\(");

    // Hardcoded credentials / secrets
    private static final Pattern SECRET_PATTERN = Pattern.compile(
            "(?i)(?:password|passwd|apiKey|api_key|token|jwt|secret|cvv)\\s*=\\s*['\"][^'\"]{5,}['\"]");

    // Valid Java identifier
    private static final Pattern JAVA_CLASS_NAME_PATTERN = Pattern.compile("^[A-Z][a-zA-Z0-9_]*$");
    private static final Pattern METHOD_NAME_PATTERN = Pattern.compile("public\\s+void\\s+([a-zA-Z0-9_]+)\\s*\\(");

    private CodeValidator() {
    }

    /**
     * Validates class name syntax.
     */
    public static boolean isValidClassName(String className) {
        return className != null && JAVA_CLASS_NAME_PATTERN.matcher(className).matches();
    }

    /**
     * Validates full Java source code string for safety and conventions.
     */
    public static ValidationResult validateCode(String sourceCode, String expectedClassName) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (sourceCode == null || sourceCode.isBlank()) {
            errors.add("Source code is null or empty");
            return ValidationResult.failure(errors, warnings);
        }

        // 1. Check class name presence and declaration
        if (expectedClassName != null && !expectedClassName.isBlank()) {
            if (!sourceCode.contains("class " + expectedClassName)) {
                errors.add("Source does not declare expected class: " + expectedClassName);
            }
        }

        // 2. Thread.sleep() detection
        if (THREAD_SLEEP_PATTERN.matcher(sourceCode).find()) {
            errors.add("Forbidden 'Thread.sleep()' detected in generated code. Use Playwright auto-waiting instead.");
        }

        // 3. Hardcoded secrets detection
        if (SECRET_PATTERN.matcher(sourceCode).find()) {
            errors.add("Potential hardcoded credentials/secrets detected in generated code.");
        }

        // 4. Fragile / positional selector warnings
        if (UNTRUSTED_XPATH_PATTERN.matcher(sourceCode).find()) {
            warnings.add("Unsafe absolute XPath detected. Prefer getByRole, getByTestId, or stable CSS.");
        }
        if (POSITION_NTH_PATTERN.matcher(sourceCode).find()) {
            warnings.add("Positional '.nth()' locator detected. Prefer specific accessible attributes.");
        }

        // 5. Duplicate method name detection
        var matcher = METHOD_NAME_PATTERN.matcher(sourceCode);
        Set<String> seenMethods = new HashSet<>();
        while (matcher.find()) {
            String method = matcher.group(1);
            if (!seenMethods.add(method)) {
                errors.add("Duplicate test method declared in same class: " + method);
            }
        }

        // 6. Mandatory AI draft header
        if (!sourceCode.contains("AI-GENERATED DRAFT") && !sourceCode.contains("HUMAN REVIEW REQUIRED")) {
            warnings.add("Missing standard AI draft disclaimer header.");
        }

        if (!errors.isEmpty()) {
            return ValidationResult.failure(errors, warnings);
        }
        return ValidationResult.success(warnings);
    }
}