package com.tests.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.framework.ai.sanitizer.SensitiveDataSanitizer;
import java.util.HashMap;
import java.util.Map;
import org.testng.annotations.Test;

public class SensitiveDataSanitizerTest {

    @Test
    public void testBearerTokenRedaction() {
        String input = "Request failed with Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9";
        String sanitized = SensitiveDataSanitizer.sanitize(input);
        assertThat(sanitized).doesNotContain("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9");
        assertThat(sanitized).contains("Authorization: [REDACTED]");
    }

    @Test
    public void testAuthorizationBasicHeaderRedaction() {
        String input = "Authorization: Basic dXNlcjpwYXNzd29yZA==";
        String sanitized = SensitiveDataSanitizer.sanitize(input);
        assertThat(sanitized).doesNotContain("dXNlcjpwYXNzd29yZA==");
        assertThat(sanitized).contains("Authorization: [REDACTED]");
    }

    @Test
    public void testPasswordKeyValueRedaction() {
        String input = "Login failed for user 'john' with password=SuperSecretPassword123! in form";
        String sanitized = SensitiveDataSanitizer.sanitize(input);
        assertThat(sanitized).doesNotContain("SuperSecretPassword123!");
        assertThat(sanitized).contains("password=[REDACTED]");
    }

    @Test
    public void testApiKeyRedaction() {
        String input = "GET /v1/models?api_key=AIzaSyA_99RandomRealLookingKey123 HTTP/1.1";
        String sanitized = SensitiveDataSanitizer.sanitize(input);
        assertThat(sanitized).doesNotContain("AIzaSyA_99RandomRealLookingKey123");
        assertThat(sanitized).contains("api_key=[REDACTED]");
    }

    @Test
    public void testCookieHeaderRedaction() {
        String input = "Cookie: sessionid=xyz123456789; token=abcdef; path=/";
        String sanitized = SensitiveDataSanitizer.sanitize(input);
        assertThat(sanitized).doesNotContain("xyz123456789");
        assertThat(sanitized).contains("Cookie: [REDACTED]");
    }

    @Test
    public void testCreditCardRedaction() {
        String input = "Card payment with 4111111111111111 failed with decline code";
        String sanitized = SensitiveDataSanitizer.sanitize(input);
        assertThat(sanitized).doesNotContain("4111111111111111");
        assertThat(sanitized).contains("[REDACTED]");
    }

    @Test
    public void testCvvAndOtpRedaction() {
        String input = "Submitted cvv: 893 and verification_code: 481920 to payment portal";
        String sanitized = SensitiveDataSanitizer.sanitize(input);
        assertThat(sanitized).doesNotContain("893");
        assertThat(sanitized).doesNotContain("481920");
        assertThat(sanitized).contains("cvv: [REDACTED]");
        assertThat(sanitized).contains("verification_code: [REDACTED]");
    }

    @Test
    public void testMultipleSecretsInSinglePayload() {
        String input = "User login error: password=pass123, apiKey=key456, and Bearer secretToken789";
        String sanitized = SensitiveDataSanitizer.sanitize(input);
        assertThat(sanitized).doesNotContain("pass123");
        assertThat(sanitized).doesNotContain("key456");
        assertThat(sanitized).doesNotContain("secretToken789");
    }

    @Test
    public void testNormalTextRemainsUnchanged() {
        String normal = "Timeout 30000ms exceeded while waiting for locator('button.submit'). Locator did not resolve.";
        String sanitized = SensitiveDataSanitizer.sanitize(normal);
        assertThat(sanitized).isEqualTo(normal);
    }

    @Test
    public void testSanitizeMap() {
        Map<String, String> map = new HashMap<>();
        map.put("normalKey", "normalValue");
        map.put("db_password", "dbSecretPass");
        map.put("authToken", "jwt.token.val");

        Map<String, String> sanitized = SensitiveDataSanitizer.sanitizeMap(map);
        assertThat(sanitized.get("normalKey")).isEqualTo("normalValue");
        assertThat(sanitized.get("db_password")).isEqualTo("[REDACTED]");
        assertThat(sanitized.get("authToken")).isEqualTo("[REDACTED]");
    }

    // ===================================================================================
    // Phase 5.2 hardening: bare "authorization="/"session="/"cookie=" key=value forms.
    // The Phase 5.1 technical spike confirmed these three were not previously redacted.
    // ===================================================================================

    // A. authorization=
    @Test
    public void testAuthorizationKeyValuePlainRedaction() {
        String input = "Request headers included authorization=TEST_AUTHORIZATION_SECRET before the call";
        String sanitized = SensitiveDataSanitizer.sanitize(input);
        assertThat(sanitized).doesNotContain("TEST_AUTHORIZATION_SECRET");
        assertThat(sanitized).contains("authorization=[REDACTED]");
    }

    @Test
    public void testAuthorizationKeyValueQuotedRedaction() {
        String input = "config: authorization=\"TEST_AUTHORIZATION_SECRET\"";
        String sanitized = SensitiveDataSanitizer.sanitize(input);
        assertThat(sanitized).doesNotContain("TEST_AUTHORIZATION_SECRET");
        assertThat(sanitized).contains("authorization=\"[REDACTED]\"");
    }

    @Test
    public void testAuthorizationKeyValueWithWhitespaceAroundEqualsRedaction() {
        String input = "authorization = TEST_AUTHORIZATION_SECRET";
        String sanitized = SensitiveDataSanitizer.sanitize(input);
        assertThat(sanitized).doesNotContain("TEST_AUTHORIZATION_SECRET");
        assertThat(sanitized).contains("[REDACTED]");
    }

    // B. session=
    @Test
    public void testSessionKeyValuePlainRedaction() {
        String input = "DOM snippet contained session=TEST_SESSION_SECRET in a hidden field";
        String sanitized = SensitiveDataSanitizer.sanitize(input);
        assertThat(sanitized).doesNotContain("TEST_SESSION_SECRET");
        assertThat(sanitized).contains("session=[REDACTED]");
    }

    @Test
    public void testSessionKeyValueQuotedRedaction() {
        String input = "window.__state = { session=\"TEST_SESSION_SECRET\" }";
        String sanitized = SensitiveDataSanitizer.sanitize(input);
        assertThat(sanitized).doesNotContain("TEST_SESSION_SECRET");
        assertThat(sanitized).contains("session=\"[REDACTED]\"");
    }

    @Test
    public void testSessionKeyValueWithWhitespaceAroundEqualsRedaction() {
        String input = "session   =   TEST_SESSION_SECRET";
        String sanitized = SensitiveDataSanitizer.sanitize(input);
        assertThat(sanitized).doesNotContain("TEST_SESSION_SECRET");
        assertThat(sanitized).contains("[REDACTED]");
    }

    // C. cookie=
    @Test
    public void testCookieKeyValuePlainRedaction() {
        String input = "document.cookie=TEST_COOKIE_SECRET;path=/";
        String sanitized = SensitiveDataSanitizer.sanitize(input);
        assertThat(sanitized).doesNotContain("TEST_COOKIE_SECRET");
        assertThat(sanitized).contains("cookie=[REDACTED]");
    }

    @Test
    public void testCookieKeyValueQuotedRedaction() {
        String input = "attrs: cookie=\"TEST_COOKIE_SECRET\"";
        String sanitized = SensitiveDataSanitizer.sanitize(input);
        assertThat(sanitized).doesNotContain("TEST_COOKIE_SECRET");
        assertThat(sanitized).contains("cookie=\"[REDACTED]\"");
    }

    @Test
    public void testCookieKeyValueWithWhitespaceAroundEqualsRedaction() {
        String input = "cookie = TEST_COOKIE_SECRET";
        String sanitized = SensitiveDataSanitizer.sanitize(input);
        assertThat(sanitized).doesNotContain("TEST_COOKIE_SECRET");
        assertThat(sanitized).contains("[REDACTED]");
    }

    // Value followed by another key/value pair in the same string, for all three new keys
    @Test
    public void testAuthorizationSessionCookieFollowedByAnotherKeyValuePair() {
        String input = "authorization=TEST_AUTHORIZATION_SECRET session=TEST_SESSION_SECRET cookie=TEST_COOKIE_SECRET nextField=normalValue";
        String sanitized = SensitiveDataSanitizer.sanitize(input);

        assertThat(sanitized).doesNotContain("TEST_AUTHORIZATION_SECRET");
        assertThat(sanitized).doesNotContain("TEST_SESSION_SECRET");
        assertThat(sanitized).doesNotContain("TEST_COOKIE_SECRET");
        assertThat(sanitized).contains("nextField=normalValue");
    }

    // Regression: previously-supported bare key=value forms must remain redacted
    @Test
    public void testRegressionExistingKeyValueFormsStillRedacted() {
        assertThat(SensitiveDataSanitizer.sanitize("token=TEST_TOKEN_SECRET"))
                .doesNotContain("TEST_TOKEN_SECRET");
        assertThat(SensitiveDataSanitizer.sanitize("secret=TEST_SECRET_VALUE"))
                .doesNotContain("TEST_SECRET_VALUE")
                .contains("secret=[REDACTED]");
        assertThat(SensitiveDataSanitizer.sanitize("api_key=TEST_API_KEY_SECRET"))
                .doesNotContain("TEST_API_KEY_SECRET")
                .contains("api_key=[REDACTED]");
        assertThat(SensitiveDataSanitizer.sanitize("apikey=TEST_API_KEY_SECRET"))
                .doesNotContain("TEST_API_KEY_SECRET")
                .contains("apikey=[REDACTED]");
        assertThat(SensitiveDataSanitizer.sanitize("credential=TEST_CREDENTIAL_SECRET"))
                .doesNotContain("TEST_CREDENTIAL_SECRET")
                .contains("credential=[REDACTED]");
        assertThat(SensitiveDataSanitizer.sanitize("password=TEST_PASSWORD_SECRET"))
                .doesNotContain("TEST_PASSWORD_SECRET")
                .contains("password=[REDACTED]");
    }

    // Regression: existing HTTP/header-style forms must remain fully redacted, unweakened
    @Test
    public void testRegressionHttpHeaderStyleFormsStillRedacted() {
        String authHeader = "Authorization: Bearer TEST_AUTHORIZATION_SECRET";
        String sanitizedAuth = SensitiveDataSanitizer.sanitize(authHeader);
        assertThat(sanitizedAuth).doesNotContain("TEST_AUTHORIZATION_SECRET");
        assertThat(sanitizedAuth).contains("Authorization: [REDACTED]");

        String cookieHeader = "Cookie: sessionid=TEST_SESSION_SECRET; path=/";
        String sanitizedCookie = SensitiveDataSanitizer.sanitize(cookieHeader);
        assertThat(sanitizedCookie).doesNotContain("TEST_SESSION_SECRET");
        assertThat(sanitizedCookie).contains("Cookie: [REDACTED]");
    }

    // Null/empty input handling (matches SensitiveDataSanitizer.sanitize's documented null-safe contract)
    @Test
    public void testNullAndEmptyInputReturnEmptyString() {
        assertThat(SensitiveDataSanitizer.sanitize(null)).isEqualTo("");
        assertThat(SensitiveDataSanitizer.sanitize("")).isEqualTo("");
    }
}