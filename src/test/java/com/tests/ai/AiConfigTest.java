package com.tests.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.framework.ai.config.AiConfig;
import com.framework.config.ConfigManager;
import org.testng.annotations.Test;

public class AiConfigTest {

    @Test(groups = {"unit", "ai"})
    public void shouldHaveSafeDefaultsWhenAiIsDisabled() {
        AiConfig aiConfig = new AiConfig();

        assertThat(aiConfig.isAiEnabled())
                .as("AI must be disabled by default")
                .isFalse();

        assertThat(aiConfig.getProvider())
                .as("Default provider should be gemini")
                .isEqualTo("gemini");

        assertThat(aiConfig.getModel())
                .as("Default model should be empty")
                .isEmpty();

        assertThat(aiConfig.isFailureAnalysisEnabled())
                .as("Failure analysis must be disabled by default")
                .isFalse();

        assertThat(aiConfig.getTimeoutSeconds())
                .as("Default timeout should be 20 seconds")
                .isEqualTo(20);
    }

    @Test(groups = {"unit", "ai"})
    public void validateShouldPassWhenAiIsDisabledWithoutApiKey() {
        AiConfig aiConfig = new AiConfig();

        assertThat(aiConfig.isAiEnabled()).isFalse();

        // Validation must not throw an exception when AI is disabled
        assertThatCode(aiConfig::validate)
                .as("Validation must succeed when AI is disabled, even with no API key set")
                .doesNotThrowAnyException();
    }

    @Test(groups = {"unit", "ai"})
    public void validateShouldThrowWhenAiEnabledWithoutApiKey() {
        // Temporarily set system property to simulate enabled AI
        System.setProperty("ai.enabled", "true");
        try {
            AiConfig aiConfig = new AiConfig(ConfigManager.getInstance());

            assertThat(aiConfig.isAiEnabled()).isTrue();

            assertThatThrownBy(aiConfig::validate)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("ai.api.key");
        } finally {
            System.clearProperty("ai.enabled");
        }
    }
}
