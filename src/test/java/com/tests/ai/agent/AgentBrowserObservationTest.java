package com.tests.ai.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.framework.ai.agent.AgentBrowserObservation;
import com.framework.ai.agent.LocatorObservation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.testng.annotations.Test;

/**
 * Phase 8 Step 4: pure data-model tests for {@link AgentBrowserObservation}. No Playwright call
 * anywhere in this file — every {@link LocatorObservation} is hand-built.
 */
public class AgentBrowserObservationTest {

    @Test
    public void testValidConstruction() {
        AgentBrowserObservation observation = AgentBrowserObservation.builder()
                .pageAvailable(true)
                .url("https://www.stag.furlenco.com/cart")
                .title("Cart")
                .domSnapshot("<div>ok</div>")
                .build();

        assertThat(observation.isPageAvailable()).isTrue();
        assertThat(observation.getUrl()).isEqualTo("https://www.stag.furlenco.com/cart");
        assertThat(observation.getTitle()).isEqualTo("Cart");
        assertThat(observation.getDomSnapshot()).isEqualTo("<div>ok</div>");
        assertThat(observation.getObservedLocatorStates()).isEmpty();
        assertThat(observation.getError()).isEmpty();
    }

    @Test
    public void testPageUnavailableDefaultsFieldsToNullNotFabricated() {
        AgentBrowserObservation observation = AgentBrowserObservation.builder()
                .pageAvailable(false)
                .error("No Playwright Page supplied.")
                .build();

        assertThat(observation.isPageAvailable()).isFalse();
        assertThat(observation.getUrl()).isNull();
        assertThat(observation.getTitle()).isNull();
        assertThat(observation.getDomSnapshot()).isNull();
        assertThat(observation.getError()).isNotEmpty();
    }

    @Test
    public void testDefensiveCopyOfLocatorStates() {
        List<LocatorObservation> source = new ArrayList<>();
        source.add(LocatorObservation.builder().selector("a").count(1).build());

        AgentBrowserObservation observation = AgentBrowserObservation.builder()
                .pageAvailable(true).observedLocatorStates(source).build();
        source.add(LocatorObservation.builder().selector("b").count(2).build()); // mutate after construction

        assertThat(observation.getObservedLocatorStates()).hasSize(1);
    }

    @Test
    public void testReturnedLocatorStatesListIsUnmodifiable() {
        AgentBrowserObservation observation = AgentBrowserObservation.builder()
                .pageAvailable(true)
                .addObservedLocatorState(LocatorObservation.builder().selector("a").build())
                .build();

        assertThatThrownBy(() -> observation.getObservedLocatorStates()
                        .add(LocatorObservation.builder().selector("b").build()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void testNullLocatorStatesListDefaultsToEmptyNotAnError() {
        AgentBrowserObservation observation = AgentBrowserObservation.builder()
                .pageAvailable(true).observedLocatorStates(null).build();

        assertThat(observation.getObservedLocatorStates()).isEmpty();
    }

    @Test
    public void testListContainingNullLocatorObservationIsFiltered() {
        List<LocatorObservation> withNull = Arrays.asList(
                LocatorObservation.builder().selector("a").build(), null);

        AgentBrowserObservation observation = AgentBrowserObservation.builder()
                .pageAvailable(true).observedLocatorStates(withNull).build();

        assertThat(observation.getObservedLocatorStates()).hasSize(1);
    }

    @Test
    public void testNoSetterOrExecutionMethodsExist() {
        for (Method method : AgentBrowserObservation.class.getDeclaredMethods()) {
            assertThat(method.getName()).doesNotStartWith("set");
            assertThat(Arrays.asList("click", "fill", "navigate", "execute", "apply", "run"))
                    .doesNotContain(method.getName());
        }
    }
}
