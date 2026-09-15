package com.framework.ai.locatoradvisor.runtime;

import com.framework.ai.config.AiConfig;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Fail-closed gate that decides whether Phase 6 runtime locator validation may proceed
 * against the CURRENT live page.
 *
 * Deliberately checks two independent signals — neither is trusted alone:
 * 1. The configured environment name ({@code ai.locator.runtime.allowed.environments},
 *    default "qa,staging") must allow it, and "prod"/"production" is unconditionally denied
 *    regardless of that configuration.
 * 2. The actual live {@code Page.url()} host must exactly match a small, hardcoded allowlist —
 *    NOT a substring/"contains" check — over HTTPS.
 *
 * This second check exists specifically because the repository's own {@code qa.properties} /
 * {@code staging.properties} / {@code prod.properties} do not reliably tie environment name to a
 * safe URL (a pre-existing, out-of-scope condition identified during the Phase 6 feasibility spike:
 * {@code furlenco.base.url} resolves to the production domain even when {@code env=qa}). Trusting
 * the environment name alone would not have caught that. This class does not fix that configuration
 * issue — it exists so runtime validation stays safe despite it.
 */
public final class RuntimeEnvironmentGuard {

    /** Environment names that are NEVER allowed, regardless of {@code ai.locator.runtime.allowed.environments}. */
    private static final Set<String> HARD_DENIED_ENVIRONMENTS = Set.of("prod", "production");

    /**
     * Explicit, exact-match host allowlist. Intentionally small: only hosts that have been
     * explicitly confirmed safe for runtime validation belong here. A host is never added by
     * inference (e.g. "contains 'stag'") — only by exact, case-insensitive match.
     *
     * Currently contains only the confirmed Furlenco staging host. There is deliberately no
     * QA-environment host here yet — none has been explicitly confirmed as safe, so an
     * {@code env=qa} run will pass {@link #isEnvironmentAllowed(String)} but still fail
     * {@link #isHostAllowed(String)} until a safe QA host is confirmed and added. That is
     * intended fail-closed behavior, not a bug.
     */
    private static final Set<String> ALLOWED_HOSTS = Set.of("www.stag.furlenco.com");

    private static final String REQUIRED_SCHEME = "https";

    private final AiConfig aiConfig;

    public RuntimeEnvironmentGuard() {
        this(new AiConfig());
    }

    public RuntimeEnvironmentGuard(AiConfig aiConfig) {
        this.aiConfig = Objects.requireNonNull(aiConfig, "AiConfig must not be null");
    }

    /**
     * Checks the environment name alone (e.g. from {@code ConfigManager.getEnvironment()}).
     * Fails closed on null/blank/unknown values. Does NOT inspect any URL.
     */
    public boolean isEnvironmentAllowed(String environment) {
        if (environment == null || environment.isBlank()) {
            return false;
        }
        String normalized = environment.trim().toLowerCase();
        if (HARD_DENIED_ENVIRONMENTS.contains(normalized)) {
            return false;
        }
        Set<String> allowed = aiConfig.getLocatorRuntimeAllowedEnvironments();
        return allowed.contains(normalized);
    }

    /**
     * Checks the actual live URL independently of environment name, via exact-host + HTTPS matching.
     * Fails closed on null/blank/malformed URLs, non-HTTPS schemes, and any host not in the
     * explicit allowlist — including hosts that merely resemble an allowed host.
     */
    public boolean isHostAllowed(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            URI uri = new URI(url.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null) {
                return false;
            }
            if (!REQUIRED_SCHEME.equalsIgnoreCase(scheme)) {
                return false;
            }
            return ALLOWED_HOSTS.contains(host.toLowerCase());
        } catch (URISyntaxException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Combined decision: both the environment name AND the live URL must independently pass.
     * This is the method runtime validation should actually call.
     */
    public GuardDecision evaluate(String environment, String currentUrl) {
        if (!isEnvironmentAllowed(environment)) {
            return GuardDecision.deny(
                    "Environment '" + safe(environment) + "' is not an allowed runtime-validation environment.");
        }
        if (!isHostAllowed(currentUrl)) {
            return GuardDecision.deny(
                    "URL '" + safe(currentUrl) + "' is not an explicitly allowed safe host for runtime validation.");
        }
        return GuardDecision.allow();
    }

    /** Read-only view of the currently allowed exact hosts, for diagnostics/tests — never mutable. */
    public static Set<String> allowedHosts() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(ALLOWED_HOSTS));
    }

    private static String safe(String value) {
        return value == null ? "(none)" : value;
    }

    /** Simple immutable allow/deny outcome with a human-readable reason for denial. */
    public static final class GuardDecision {
        private final boolean allowed;
        private final String reason;

        private GuardDecision(boolean allowed, String reason) {
            this.allowed = allowed;
            this.reason = reason != null ? reason : "";
        }

        public static GuardDecision allow() {
            return new GuardDecision(true, "");
        }

        public static GuardDecision deny(String reason) {
            return new GuardDecision(false, reason);
        }

        public boolean isAllowed() {
            return allowed;
        }

        public String getReason() {
            return reason;
        }
    }
}
