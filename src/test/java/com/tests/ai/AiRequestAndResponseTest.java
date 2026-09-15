package com.tests.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.framework.ai.model.AiRequest;
import com.framework.ai.model.AiResponse;
import org.testng.annotations.Test;

public class AiRequestAndResponseTest {

    @Test(groups = {"unit", "ai"})
    public void shouldBuildAiRequestCorrectly() {
        AiRequest request = AiRequest.builder()
                .systemInstruction("You are a QA automation expert.")
                .prompt("Analyze this failure stack trace.")
                .model("gemini-2.5-flash")
                .temperature(0.2)
                .maxTokens(1024)
                .parameter("topP", 0.95)
                .build();

        assertThat(request.getSystemInstruction()).isEqualTo("You are a QA automation expert.");
        assertThat(request.getPrompt()).isEqualTo("Analyze this failure stack trace.");
        assertThat(request.getModel()).isEqualTo("gemini-2.5-flash");
        assertThat(request.getTemperature()).isEqualTo(0.2);
        assertThat(request.getMaxTokens()).isEqualTo(1024);
        assertThat(request.getParameters()).containsEntry("topP", 0.95);
    }

    @Test(groups = {"unit", "ai"})
    public void shouldThrowExceptionWhenPromptIsBlank() {
        assertThatThrownBy(() -> AiRequest.builder().prompt("").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("prompt must not be null or blank");

        assertThatThrownBy(() -> AiRequest.builder().build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("prompt must not be null or blank");
    }

    @Test(groups = {"unit", "ai"})
    public void shouldBuildSuccessfulAiResponse() {
        AiResponse response = AiResponse.success("Failure analysis content", "gemini-2.5-flash");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getContent()).isEqualTo("Failure analysis content");
        assertThat(response.getModel()).isEqualTo("gemini-2.5-flash");
        assertThat(response.getErrorMessage()).isEmpty();
    }

    @Test(groups = {"unit", "ai"})
    public void shouldBuildFailureAiResponse() {
        AiResponse response = AiResponse.failure("Connection refused: 503");

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getContent()).isEmpty();
        assertThat(response.getErrorMessage()).isEqualTo("Connection refused: 503");
    }
}
