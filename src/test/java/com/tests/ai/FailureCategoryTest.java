package com.tests.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.framework.ai.model.FailureCategory;
import org.testng.annotations.Test;

public class FailureCategoryTest {

    @Test(groups = {"unit", "ai"})
    public void shouldSupportAllExpectedCategories() {
        assertThat(FailureCategory.values()).containsExactlyInAnyOrder(
                FailureCategory.APPLICATION_BUG,
                FailureCategory.LOCATOR_CHANGED,
                FailureCategory.TIMEOUT,
                FailureCategory.NETWORK_FAILURE,
                FailureCategory.API_FAILURE,
                FailureCategory.DATA_ISSUE,
                FailureCategory.AUTHENTICATION_FAILURE,
                FailureCategory.ENVIRONMENT_FAILURE,
                FailureCategory.TEST_FAILURE,
                FailureCategory.UNCERTAIN,
                FailureCategory.UNKNOWN);
    }

    @Test(groups = {"unit", "ai"})
    public void shouldParseValidStringsCaseInsensitively() {
        assertThat(FailureCategory.fromString("APPLICATION_BUG")).isEqualTo(FailureCategory.APPLICATION_BUG);
        assertThat(FailureCategory.fromString("locator_changed")).isEqualTo(FailureCategory.LOCATOR_CHANGED);
        assertThat(FailureCategory.fromString("  timeout  ")).isEqualTo(FailureCategory.TIMEOUT);
        assertThat(FailureCategory.fromString("Uncertain")).isEqualTo(FailureCategory.UNCERTAIN);
    }

    @Test(groups = {"unit", "ai"})
    public void shouldFallbackToUnknownForInvalidOrNullInput() {
        assertThat(FailureCategory.fromString(null)).isEqualTo(FailureCategory.UNKNOWN);
        assertThat(FailureCategory.fromString("")).isEqualTo(FailureCategory.UNKNOWN);
        assertThat(FailureCategory.fromString("   ")).isEqualTo(FailureCategory.UNKNOWN);
        assertThat(FailureCategory.fromString("NON_EXISTENT_CATEGORY")).isEqualTo(FailureCategory.UNKNOWN);
    }
}
