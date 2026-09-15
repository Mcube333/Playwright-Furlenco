package com.framework.ai.config;

import com.framework.config.ConfigManager;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Encapsulates configuration for the AI Foundation layer.
 * Reuses the existing {@link ConfigManager} without creating duplicate config loaders.
 * Resolves properties via JVM system properties (-Dai.enabled=true),
 * environment variables (AI_ENABLED=true), and properties files.
 *
 * All AI features are disabled by default.
 */
public class AiConfig {

    private static final Logger LOGGER = LogManager.getLogger(AiConfig.class);

    private final ConfigManager configManager;

    public AiConfig() {
        this(ConfigManager.getInstance());
    }

    public AiConfig(ConfigManager configManager) {
        this.configManager = Objects.requireNonNull(configManager, "ConfigManager must not be null");
    }

    /**
     * Master switch for the AI layer. Defaults to false.
     */
    public boolean isAiEnabled() {
        return configManager.getBoolean("ai.enabled", false);
    }

    /**
     * Target AI provider name (e.g. "gemini", "openai", "ollama"). Defaults to "gemini".
     */
    public String getProvider() {
        return configManager.get("ai.provider", "gemini");
    }

    /**
     * Model identifier (e.g. "gemini-2.5-flash", "gpt-4o"). Defaults to empty string.
     */
    public String getModel() {
        return configManager.get("ai.model", "");
    }

    /**
     * Controls whether test failure analysis is enabled when AI is active. Defaults to false.
     */
    public boolean isFailureAnalysisEnabled() {
        return configManager.getBoolean("ai.failure.analysis.enabled", false);
    }

    /**
     * HTTP timeout in seconds for AI provider requests. Defaults to 20 seconds.
     */
    public int getTimeoutSeconds() {
        return configManager.getInt("ai.timeout.seconds", 20);
    }

    /**
     * Retrieves the AI API key.
     * Never required when AI is disabled. Never logged.
     * Resolves via system property (-Dai.api.key=), env var (AI_API_KEY=), or properties file.
     */
    public String getApiKey() {
        return configManager.get("ai.api.key", "");
    }

    /**
     * Phase 6 master switch for runtime (live Playwright Page) locator validation. Defaults to false.
     * Independent of {@link #isAiEnabled()} — no AI call is involved in runtime validation itself.
     */
    public boolean isLocatorRuntimeValidationEnabled() {
        return configManager.getBoolean("ai.locator.runtime.validation.enabled", false);
    }

    /**
     * Environment names allowed to run Phase 6 runtime locator validation, lowercased.
     * Defaults to "qa,staging". Read by {@link com.framework.ai.locatoradvisor.runtime.RuntimeEnvironmentGuard},
     * which additionally denies "prod"/"production" unconditionally regardless of this value.
     */
    public java.util.Set<String> getLocatorRuntimeAllowedEnvironments() {
        String raw = configManager.get("ai.locator.runtime.allowed.environments", "qa,staging");
        java.util.Set<String> allowed = new java.util.LinkedHashSet<>();
        for (String part : raw.split(",")) {
            String trimmed = part.trim().toLowerCase();
            if (!trimmed.isEmpty()) {
                allowed.add(trimmed);
            }
        }
        return java.util.Collections.unmodifiableSet(allowed);
    }

    /**
     * Phase 8 Step 5 master switch for agent action execution. Defaults to false. Independent of
     * {@link #isAiEnabled()} — even with AI enabled (so agent reasoning may run), this must be
     * separately enabled before {@code AgentExecutionGuard} will consider any decision for
     * execution. There is no execution capability in the framework yet regardless of this flag —
     * it exists so the policy boundary is already safe-by-default before one is ever built.
     */
    public boolean isAgentExecutionEnabled() {
        return configManager.getBoolean("ai.agent.execution.enabled", false);
    }

    /**
     * Phase 8 Step 5 master switch for allowing an agent-proposed action to eventually mutate
     * browser state. Defaults to false. Read by {@code AgentExecutionGuard} as an additional,
     * independent fail-closed gate — never implied by {@link #isAgentExecutionEnabled()} alone.
     */
    public boolean isAgentBrowserMutationEnabled() {
        return configManager.getBoolean("ai.agent.browser.mutation.enabled", false);
    }

    /**
     * Phase 8 Step 5 maximum number of agent actions permitted. Defaults to 0 (no actions
     * permitted) — this is a policy ceiling for a future executor, not a counter this framework
     * currently enforces at runtime (there is no executor yet to count against it).
     */
    public int getAgentMaxActions() {
        return configManager.getInt("ai.agent.max.actions", 0);
    }

    /**
     * Validates configuration if AI is enabled.
     * When AI is disabled, validation always succeeds without requiring credentials.
     *
     * @throws IllegalStateException if AI is enabled but required configuration is missing
     */
    public void validate() {
        if (!isAiEnabled()) {
            LOGGER.debug("AI layer is disabled (ai.enabled=false). Validation skipped.");
            return;
        }

        String provider = getProvider();
        if (provider == null || provider.isBlank()) {
            throw new IllegalStateException("AI is enabled but 'ai.provider' is not specified.");
        }

        String apiKey = getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "AI is enabled for provider [" + provider + "] but 'ai.api.key' (or AI_API_KEY) is not set.");
        }
    }
}
