package com.tests.ai.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.framework.ai.agent.AgentContext;
import com.framework.ai.diagnosis.FailureDiagnosis;
import com.framework.ai.model.FailureContext;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import org.testng.annotations.Test;

/**
 * Phase 8 Step 2: pure data-model tests for {@link AgentContext}. No AI, no Playwright, no
 * execution behavior anywhere in this file.
 */
public class AgentContextTest {

    private FailureDiagnosis sampleDiagnosis() {
        return FailureDiagnosis.builder()
                .failureContext(FailureContext.builder().testName("testCheckout").build())
                .build();
    }

    @Test
    public void testValidConstruction() {
        FailureDiagnosis diagnosis = sampleDiagnosis();
        AgentContext context = AgentContext.builder().failureDiagnosis(diagnosis).build();

        assertThat(context.getFailureDiagnosis()).isSameAs(diagnosis);
    }

    @Test
    public void testNullFailureDiagnosisFailsFast() {
        assertThatThrownBy(() -> AgentContext.builder().failureDiagnosis(null).build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void testMissingFailureDiagnosisFailsFast() {
        assertThatThrownBy(() -> AgentContext.builder().build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void testFailureDiagnosisRetainedByReferenceNotCopied() {
        FailureDiagnosis diagnosis = sampleDiagnosis();
        AgentContext context = AgentContext.builder().failureDiagnosis(diagnosis).build();

        // AgentContext does not clone existing, already-immutable Phase 7 objects.
        assertThat(context.getFailureDiagnosis()).isSameAs(diagnosis);
    }

    @Test
    public void testNoSetterMethodsExist() {
        for (Method method : AgentContext.class.getDeclaredMethods()) {
            assertThat(method.getName()).doesNotStartWith("set");
        }
    }

    @Test
    public void testNoExecutionOrLifecycleMethodsExist() {
        for (Method method : AgentContext.class.getDeclaredMethods()) {
            String name = method.getName();
            assertThat(Arrays.asList("execute", "apply", "heal", "run", "approve", "transition"))
                    .doesNotContain(name);
        }
    }

    @Test
    public void testClassIsFinalAndFieldsArePrivateFinal() {
        assertThat(Modifier.isFinal(AgentContext.class.getModifiers())).isTrue();
        for (var field : AgentContext.class.getDeclaredFields()) {
            assertThat(Modifier.isPrivate(field.getModifiers())).isTrue();
            assertThat(Modifier.isFinal(field.getModifiers())).isTrue();
        }
    }
}
