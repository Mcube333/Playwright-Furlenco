package com.tests.ai.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.framework.ai.agent.LocatorObservation;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.testng.annotations.Test;

/**
 * Phase 8 Step 4: pure data-model tests for {@link LocatorObservation}. No Playwright call, no
 * execution behavior anywhere in this file.
 */
public class LocatorObservationTest {

    @Test
    public void testValidConstruction() {
        LocatorObservation observation = LocatorObservation.builder()
                .selector("[data-testid='checkout-button']")
                .count(1).visible(true).enabled(true)
                .build();

        assertThat(observation.getSelector()).isEqualTo("[data-testid='checkout-button']");
        assertThat(observation.getCount()).isEqualTo(1);
        assertThat(observation.getVisible()).isTrue();
        assertThat(observation.getEnabled()).isTrue();
        assertThat(observation.getError()).isEmpty();
    }

    @Test
    public void testCountDefaultsToNegativeOneMeaningUndetermined() {
        LocatorObservation observation = LocatorObservation.builder().selector("x").build();

        assertThat(observation.getCount()).isEqualTo(-1);
    }

    @Test
    public void testVisibleAndEnabledDefaultToNullMeaningUndetermined() {
        LocatorObservation observation = LocatorObservation.builder().selector("x").count(1).build();

        assertThat(observation.getVisible()).isNull();
        assertThat(observation.getEnabled()).isNull();
    }

    @Test
    public void testNullSelectorAndErrorDefaultToEmptyStringNotNull() {
        LocatorObservation observation = LocatorObservation.builder().build();

        assertThat(observation.getSelector()).isEmpty();
        assertThat(observation.getError()).isEmpty();
    }

    @Test
    public void testErrorObservationCarriesNoFabricatedCountOrState() {
        LocatorObservation observation = LocatorObservation.builder()
                .selector("bad-selector")
                .error("Invalid or unresolvable selector")
                .build();

        assertThat(observation.getCount()).isEqualTo(-1);
        assertThat(observation.getVisible()).isNull();
        assertThat(observation.getEnabled()).isNull();
        assertThat(observation.getError()).isNotEmpty();
    }

    @Test
    public void testNoSetterOrExecutionMethodsExist() {
        for (Method method : LocatorObservation.class.getDeclaredMethods()) {
            assertThat(method.getName()).doesNotStartWith("set");
            assertThat(Arrays.asList("click", "fill", "navigate", "execute", "apply", "run"))
                    .doesNotContain(method.getName());
        }
    }
}
