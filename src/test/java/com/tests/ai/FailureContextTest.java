package com.tests.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.framework.ai.model.FailureContext;
import java.util.Map;
import org.testng.annotations.Test;

public class FailureContextTest {

    @Test(groups = {"unit", "ai"})
    public void shouldBuildFailureContextWithAllFields() {
        FailureContext context = FailureContext.builder()
                .testName("testLoginFailure")
                .testClass("com.tests.web.LoginTest")
                .errorMessage("Element not found")
                .stackTrace("TimeoutError: locator timed out at ...")
                .currentUrl("https://example.com/login")
                .pageTitle("Sign In")
                .domSnippet("<form id='login-form'></form>")
                .executionDurationMs(4520)
                .environment("qa")
                .attribute("browser", "chromium")
                .build();

        assertThat(context.getTestName()).isEqualTo("testLoginFailure");
        assertThat(context.getTestClass()).isEqualTo("com.tests.web.LoginTest");
        assertThat(context.getErrorMessage()).isEqualTo("Element not found");
        assertThat(context.getStackTrace()).contains("TimeoutError");
        assertThat(context.getCurrentUrl()).isEqualTo("https://example.com/login");
        assertThat(context.getPageTitle()).isEqualTo("Sign In");
        assertThat(context.getDomSnippet()).isEqualTo("<form id='login-form'></form>");
        assertThat(context.getExecutionDurationMs()).isEqualTo(4520);
        assertThat(context.getEnvironment()).isEqualTo("qa");
        assertThat(context.getAttributes()).containsEntry("browser", "chromium");
    }

    @Test(groups = {"unit", "ai"})
    public void shouldHandleNullFieldsGracefullyWithDefaults() {
        FailureContext context = FailureContext.builder().build();

        assertThat(context.getTestName()).isEmpty();
        assertThat(context.getTestClass()).isEmpty();
        assertThat(context.getErrorMessage()).isEmpty();
        assertThat(context.getStackTrace()).isEmpty();
        assertThat(context.getCurrentUrl()).isEmpty();
        assertThat(context.getPageTitle()).isEmpty();
        assertThat(context.getDomSnippet()).isEmpty();
        assertThat(context.getExecutionDurationMs()).isZero();
        assertThat(context.getEnvironment()).isEmpty();
        assertThat(context.getAttributes()).isEmpty();
    }

    @Test(groups = {"unit", "ai"})
    public void attributesMapShouldBeUnmodifiable() {
        FailureContext context = FailureContext.builder()
                .attribute("key1", "val1")
                .build();

        Map<String, String> attrs = context.getAttributes();
        assertThatThrownBy(() -> attrs.put("key2", "val2"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
