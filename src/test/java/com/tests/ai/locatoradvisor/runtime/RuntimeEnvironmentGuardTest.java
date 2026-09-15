package com.tests.ai.locatoradvisor.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.framework.ai.config.AiConfig;
import com.framework.ai.locatoradvisor.runtime.RuntimeEnvironmentGuard;
import com.framework.ai.locatoradvisor.runtime.RuntimeEnvironmentGuard.GuardDecision;
import com.framework.config.ConfigManager;
import java.util.Set;
import org.testng.annotations.Test;

/**
 * Phase 6: RuntimeEnvironmentGuard tests. Every case constructs the guard with explicit
 * environment/URL string arguments — no real ConfigManager environment or live Page is needed,
 * fully hermetic.
 */
public class RuntimeEnvironmentGuardTest {

    private static final String STAGING_URL = "https://www.stag.furlenco.com";

    private RuntimeEnvironmentGuard defaultGuard() {
        return new RuntimeEnvironmentGuard(new AiConfig(ConfigManager.getInstance()));
    }

    // 1. qa + allowed safe URL -> allowed (the environment-name check and the host check are
    //    independent; "qa" passes the default allowed-environments list, and the confirmed staging
    //    host independently passes the host check, regardless of which environment "normally" serves it)
    @Test
    public void testQaEnvironmentWithAllowedHostIsAllowed() {
        GuardDecision decision = defaultGuard().evaluate("qa", STAGING_URL);
        assertThat(decision.isAllowed()).isTrue();
    }

    // 2. staging + https://www.stag.furlenco.com -> allowed
    @Test
    public void testStagingEnvironmentWithConfirmedStagingHostIsAllowed() {
        GuardDecision decision = defaultGuard().evaluate("staging", STAGING_URL);
        assertThat(decision.isAllowed()).isTrue();
    }

    // 3. prod -> rejected
    @Test
    public void testProductionEnvironmentIsRejected() {
        GuardDecision decision = defaultGuard().evaluate("prod", STAGING_URL);
        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.getReason()).isNotBlank();
    }

    @Test
    public void testProductionEnvironmentIsRejectedEvenIfMisconfiguredIntoAllowedList() {
        // Defense-in-depth: "prod"/"production" must be denied unconditionally, even if
        // ai.locator.runtime.allowed.environments were mistakenly set to include it.
        AiConfig misconfigured = new AiConfig(ConfigManager.getInstance()) {
            @Override
            public Set<String> getLocatorRuntimeAllowedEnvironments() {
                return Set.of("qa", "staging", "prod");
            }
        };
        RuntimeEnvironmentGuard guard = new RuntimeEnvironmentGuard(misconfigured);
        assertThat(guard.isEnvironmentAllowed("prod")).isFalse();
        assertThat(guard.isEnvironmentAllowed("production")).isFalse();
    }

    // 4. unknown environment -> rejected
    @Test
    public void testUnknownEnvironmentIsRejected() {
        GuardDecision decision = defaultGuard().evaluate("sandbox", STAGING_URL);
        assertThat(decision.isAllowed()).isFalse();
    }

    // 5. missing environment -> rejected
    @Test
    public void testMissingEnvironmentIsRejected() {
        RuntimeEnvironmentGuard guard = defaultGuard();
        assertThat(guard.evaluate(null, STAGING_URL).isAllowed()).isFalse();
        assertThat(guard.evaluate("", STAGING_URL).isAllowed()).isFalse();
        assertThat(guard.evaluate("   ", STAGING_URL).isAllowed()).isFalse();
    }

    // 6. production Furlenco URL even when env=qa -> rejected
    @Test
    public void testProductionFurlencoUrlRejectedEvenWhenEnvIsQa() {
        GuardDecision decision = defaultGuard().evaluate("qa", "https://www.furlenco.com");
        assertThat(decision.isAllowed()).isFalse();
    }

    // 7. arbitrary external host -> rejected
    @Test
    public void testArbitraryExternalHostRejected() {
        GuardDecision decision = defaultGuard().evaluate("staging", "https://evil.com");
        assertThat(decision.isAllowed()).isFalse();
    }

    // 8. malformed URL -> rejected
    @Test
    public void testMalformedUrlRejected() {
        RuntimeEnvironmentGuard guard = defaultGuard();
        assertThat(guard.isHostAllowed("not a valid uri ### with spaces")).isFalse();
        assertThat(guard.evaluate("staging", "not a valid uri ### with spaces").isAllowed()).isFalse();
    }

    // 9. HTTP staging URL -> rejected
    @Test
    public void testHttpStagingUrlRejected() {
        GuardDecision decision = defaultGuard().evaluate("staging", "http://www.stag.furlenco.com");
        assertThat(decision.isAllowed()).isFalse();
    }

    // 10. exact staging hostname accepted
    @Test
    public void testExactStagingHostnameAccepted() {
        assertThat(defaultGuard().isHostAllowed(STAGING_URL)).isTrue();
        assertThat(RuntimeEnvironmentGuard.allowedHosts()).contains("www.stag.furlenco.com");
    }

    // 11. similar/malicious hostname rejected
    @Test
    public void testSimilarOrMaliciousHostnameRejected() {
        RuntimeEnvironmentGuard guard = defaultGuard();
        assertThat(guard.isHostAllowed("https://www.stag.furlenco.com.evil.com")).isFalse();
        assertThat(guard.isHostAllowed("https://evilwww.stag.furlenco.com")).isFalse();
        assertThat(guard.isHostAllowed("https://www-stag-furlenco.com")).isFalse();
        assertThat(guard.isHostAllowed("https://stag.furlenco.com")).isFalse(); // missing "www." — not an exact match
    }

    // Additional: null/blank URL handling
    @Test
    public void testNullOrBlankUrlIsRejected() {
        RuntimeEnvironmentGuard guard = defaultGuard();
        assertThat(guard.isHostAllowed(null)).isFalse();
        assertThat(guard.isHostAllowed("")).isFalse();
        assertThat(guard.isHostAllowed("   ")).isFalse();
    }

    // Additional: environment name is matched case-insensitively and trims whitespace
    @Test
    public void testEnvironmentNameIsCaseInsensitiveAndTrimmed() {
        RuntimeEnvironmentGuard guard = defaultGuard();
        assertThat(guard.isEnvironmentAllowed("QA")).isTrue();
        assertThat(guard.isEnvironmentAllowed("  staging  ")).isTrue();
        assertThat(guard.isEnvironmentAllowed("PROD")).isFalse();
    }
}
