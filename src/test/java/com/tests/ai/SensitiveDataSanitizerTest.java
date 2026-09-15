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
}