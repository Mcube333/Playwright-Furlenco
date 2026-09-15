package com.tests.ai.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.framework.ai.agent.AgentState;
import java.util.Arrays;
import java.util.List;
import org.testng.annotations.Test;

/**
 * Phase 8 Step 2: pure data-model tests for {@link AgentState}. No transition engine, no
 * execution behavior anywhere in this file.
 */
public class AgentStateTest {

    private static final List<String> INITIAL_STATES = Arrays.asList("OBSERVE", "ANALYZE", "PROPOSE", "BLOCKED");
    private static final List<String> FUTURE_EXECUTION_STATES = Arrays.asList(
            "APPROVED", "EXECUTING", "SUCCEEDED", "FAILED");

    @Test
    public void testAllRequiredInitialStatesExist() {
        List<String> actualNames = Arrays.stream(AgentState.values()).map(Enum::name).toList();
        assertThat(actualNames).containsExactlyInAnyOrderElementsOf(INITIAL_STATES);
    }

    @Test
    public void testFutureExecutionStatesAreNotIncludedYet() {
        List<String> actualNames = Arrays.stream(AgentState.values()).map(Enum::name).toList();
        for (String future : FUTURE_EXECUTION_STATES) {
            assertThat(actualNames).doesNotContain(future);
        }
    }

    @Test
    public void testEnumHasExactlyFourValues() {
        assertThat(AgentState.values()).hasSize(4);
    }
}
