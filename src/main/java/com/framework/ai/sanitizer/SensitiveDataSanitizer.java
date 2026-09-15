package com.framework.ai.sanitizer;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Deterministic sanitizer that redacts credentials, secrets, tokens, PII,
 * and sensitive values before any diagnostic data is sent to an LLM.
 *
 * Sanitization occurs BEFORE the HTTP request is constructed and does NOT
 * rely on prompt instructions for data protection.
 */
public final class SensitiveDataSanitizer {

    private static final String REDACTED = "[REDACTED]";

    // Authorization headers: redact entirely from "Authorization: Bearer ..." to "Authorization: [REDACTED]"
    private static final Pattern AUTH_HEADER_PATTERN = Pattern.compile(
            "(?i)(authorization\\s*:\\s*(?:bearer|basic|digest|token)\\s+)[^\\r\\n,;\"'\\s]+");

    private static final Pattern AUTH_HEADER_FULL_PATTERN = Pattern.compile(
            "(?i)(authorization\\s*:\\s*)(?:bearer|basic|digest|token)\\s+[^\\r\\n,;\"'\\s]+");

    // Generic Bearer tokens
    private static final Pattern BEARER_TOKEN_PATTERN = Pattern.compile(
            "(?i)(bearer\\s+)[a-zA-Z0-9._~+/-]{8,}");

    // Key-value pairs for sensitive fields: password, secret, token, apiKey, authorization, session, cookie, etc.
    // Bare "authorization="/"session="/"cookie=" forms are covered here (in addition to the dedicated
    // header-style AUTH_HEADER_*/COOKIE_PATTERN below) since this is the only pattern in the file with
    // quote-aware value capture (group 2/3), so a quoted value like authorization="secret" redacts cleanly
    // to authorization="[REDACTED]" instead of leaving the quotes stranded or the match failing outright.
    private static final Pattern KV_SENSITIVE_PATTERN = Pattern.compile(
            "(?i)(['\"]?(?:password|passwd|pwd|secret|api[_-]?key|access[_-]?token|refresh[_-]?token|auth[_-]?token|authorization|session[_-]?id|session[_-]?token|session|cookie|jwt|credential|private[_-]?key)['\"]?\\s*[:=]\\s*['\"]?)([^'\"&,\\r\\n\\s]+)(['\"]?)");

    // Cookie headers and set-cookie values
    private static final Pattern COOKIE_PATTERN = Pattern.compile(
            "(?i)(cookie\\s*:\\s*)[^\\r\\n]+");

    private static final Pattern COOKIE_KV_PATTERN = Pattern.compile(
            "(?i)(['\"]?(?:sessionid|jsessionid|phpsessid|token|auth)['\"]?\\s*=\\s*)[^;\\r\\n\\s\"']+");

    // Credit card patterns: 13-19 digits with optional dashes or spaces
    private static final Pattern CREDIT_CARD_PATTERN = Pattern.compile(
            "\\b(?:4[0-9]{12}(?:[0-9]{3})?|5[1-5][0-9]{14}|3[47][0-9]{13}|3(?:0[0-5]|[68][0-9])[0-9]{11}|6(?:011|5[0-9]{2})[0-9]{12}|(?:2131|1800|35\\d{3})\\d{11})\\b");

    private static final Pattern GENERIC_CARD_DASHED_PATTERN = Pattern.compile(
            "\\b(?:\\d{4}[- ]){3}\\d{4}\\b");

    // CVV / CVC (3-4 digits preceded by cvv/cvc label)
    private static final Pattern CVV_PATTERN = Pattern.compile(
            "(?i)(['\"]?(?:cvv|cvc|security[_-]?code)['\"]?\\s*[:=]\\s*['\"]?)\\d{3,4}\\b");

    // OTP / verification code (4-8 digits preceded by otp/code label)
    private static final Pattern OTP_PATTERN = Pattern.compile(
            "(?i)(['\"]?(?:otp|one[_-]?time[_-]?password|verification[_-]?code)['\"]?\\s*[:=]\\s*['\"]?)\\d{4,8}\\b");

    // Database connection strings containing user:password@host
    private static final Pattern DB_URI_PATTERN = Pattern.compile(
            "(?i)(://[^:/@\\s]+:)([^@/\\s]+)(@[^/\\s]+)");

    // Private key blocks (PEM)
    private static final Pattern PEM_KEY_PATTERN = Pattern.compile(
            "-----BEGIN (?:RSA )?PRIVATE KEY-----[\\s\\S]*?-----END (?:RSA )?PRIVATE KEY-----");

    private SensitiveDataSanitizer() {
    }

    /**
     * Sanitizes a text string by redacting all known sensitive patterns.
     *
     * @param input the raw input text (may be null)
     * @return the sanitized string with sensitive data redacted, or empty string if input is null
     */
    public static String sanitize(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }

        String result = input;

        // PEM private keys
        result = PEM_KEY_PATTERN.matcher(result).replaceAll("-----BEGIN PRIVATE KEY-----\n" + REDACTED + "\n-----END PRIVATE KEY-----");

        // Database credentials in URI
        result = DB_URI_PATTERN.matcher(result).replaceAll("$1" + REDACTED + "$3");

        // Full Authorization header redaction: "Authorization: Bearer xyz" -> "Authorization: [REDACTED]"
        result = AUTH_HEADER_FULL_PATTERN.matcher(result).replaceAll("$1" + REDACTED);

        // Bearer tokens in standalone form
        result = BEARER_TOKEN_PATTERN.matcher(result).replaceAll("$1" + REDACTED);

        // Key-value sensitive credentials (passwords, tokens, api keys, etc.)
        result = KV_SENSITIVE_PATTERN.matcher(result).replaceAll("$1" + REDACTED + "$3");

        // Cookie headers
        result = COOKIE_PATTERN.matcher(result).replaceAll("$1" + REDACTED);
        result = COOKIE_KV_PATTERN.matcher(result).replaceAll("$1" + REDACTED);

        // CVV and OTP
        result = CVV_PATTERN.matcher(result).replaceAll("$1" + REDACTED);
        result = OTP_PATTERN.matcher(result).replaceAll("$1" + REDACTED);

        // Credit cards
        result = CREDIT_CARD_PATTERN.matcher(result).replaceAll(REDACTED);
        result = GENERIC_CARD_DASHED_PATTERN.matcher(result).replaceAll(REDACTED);

        return result;
    }

    /**
     * Sanitizes a map of string key-values (e.g. metadata or failure attributes).
     *
     * @param inputMap map to sanitize
     * @return unmodifiable map with sanitized keys and values
     */
    public static Map<String, String> sanitizeMap(Map<String, String> inputMap) {
        if (inputMap == null || inputMap.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, String> sanitized = new HashMap<>();
        for (Map.Entry<String, String> entry : inputMap.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            if (isSensitiveKey(key)) {
                sanitized.put(key, REDACTED);
            } else {
                sanitized.put(key, sanitize(value));
            }
        }
        return Collections.unmodifiableMap(sanitized);
    }

    private static boolean isSensitiveKey(String key) {
        if (key == null) {
            return false;
        }
        String lower = key.toLowerCase();
        return lower.contains("password")
                || lower.contains("passwd")
                || lower.contains("secret")
                || lower.contains("token")
                || lower.contains("api_key")
                || lower.contains("apikey")
                || lower.contains("auth")
                || lower.contains("cookie")
                || lower.contains("credential")
                || lower.contains("cvv")
                || lower.contains("otp");
    }
}