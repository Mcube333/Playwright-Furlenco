package com.tests.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.framework.ai.model.AiAnalysisResponse;
import com.framework.ai.model.FailureCategory;
import com.framework.utils.JsonUtils;
import java.util.List;
import java.util.Map;
import org.testng.annotations.Test;

public class AiAnalysisResponseTest {

    @Test(groups = {"unit", "ai"})
    public void shouldBuildAiAnalysisResponseCorrectly() {
        AiAnalysisResponse response = AiAnalysisResponse.builder()
                .summary("Login button locator altered in UI")
                .rootCause("The submit button ID changed from #login-button to #submit-btn")
                .category(FailureCategory.LOCATOR_CHANGED)
                .suggestedFix("Update LoginPage.java with the new ID or use button[type='submit']")
                .addSuggestedLocator("button[type='submit']")
                .addSuggestedLocator("#submit-btn")
                .jiraBugReport("h3. Defect Details\n* Summary: Login button broken")
                .confidenceScore(0.95)
                .addMetadata("model", "gemini-2.5-flash")
                .build();

        assertThat(response.getSummary()).isEqualTo("Login button locator altered in UI");
        assertThat(response.getRootCause()).contains("The submit button ID changed");
        assertThat(response.getCategory()).isEqualTo(FailureCategory.LOCATOR_CHANGED);
        assertThat(response.getSuggestedFix()).contains("Update LoginPage.java");
        assertThat(response.getSuggestedLocators()).containsExactly("button[type='submit']", "#submit-btn");
        assertThat(response.getJiraBugReport()).contains("Defect Details");
        assertThat(response.getConfidenceScore()).isEqualTo(0.95);
        assertThat(response.getMetadata()).containsEntry("model", "gemini-2.5-flash");
    }

    @Test(groups = {"unit", "ai"})
    public void suggestedLocatorsListShouldBeUnmodifiable() {
        AiAnalysisResponse response = AiAnalysisResponse.builder()
                .addSuggestedLocator("#btn1")
                .build();

        List<String> locators = response.getSuggestedLocators();
        assertThatThrownBy(() -> locators.add("#btn2"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test(groups = {"unit", "ai"})
    public void shouldSerializeAndDeserializeJsonCleanly() {
        AiAnalysisResponse original = AiAnalysisResponse.builder()
                .summary("Network connection timeout")
                .rootCause("Gateway timeout 504 returned by API")
                .category(FailureCategory.NETWORK_FAILURE)
                .suggestedFix("Increase timeout or check upstream service")
                .addSuggestedLocator("div.status-error")
                .jiraBugReport("h3. Gateway 504")
                .confidenceScore(0.88)
                .addMetadata("tokens", 350)
                .build();

        String json = JsonUtils.toJson(original);
        assertThat(json).contains("\"category\":\"NETWORK_FAILURE\"");

        AiAnalysisResponse deserialized = JsonUtils.fromJson(json, AiAnalysisResponse.class);
        assertThat(deserialized.getSummary()).isEqualTo(original.getSummary());
        assertThat(deserialized.getRootCause()).isEqualTo(original.getRootCause());
        assertThat(deserialized.getCategory()).isEqualTo(FailureCategory.NETWORK_FAILURE);
        assertThat(deserialized.getSuggestedLocators()).containsExactly("div.status-error");
        assertThat(deserialized.getConfidenceScore()).isEqualTo(0.88);
    }
}
