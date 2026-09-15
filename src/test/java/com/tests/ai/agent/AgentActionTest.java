package com.tests.ai.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.framework.ai.agent.AgentAction;
import java.util.Arrays;
import java.util.List;
import org.testng.annotations.Test;

/**
 * Phase 8 Step 2: pure data-model tests for {@link AgentAction}. No AI, no Playwright, no
 * orchestration anywhere in this file.
 */
public class AgentActionTest {

    private static final List<String> APPROVED_ACTIONS = Arrays.asList(
            "NONE", "LOCATOR_RECOMMENDATION", "WAIT_RECOMMENDATION",
            "ASSERTION_RECOMMENDATION", "SCREENSHOT", "DOM_CAPTURE");

    private static final List<String> PROHIBITED_ACTIONS = Arrays.asList(
            "EXECUTE_JAVASCRIPT", "RUN_SHELL_COMMAND", "MODIFY_SOURCE", "MODIFY_TEST",
            "DELETE_FILE", "CHANGE_CONFIG", "GIT_COMMIT", "GIT_PUSH", "CREATE_PR",
            "DISABLE_ASSERTION", "DISABLE_TEST", "BYPASS_AUTHENTICATION",
            "CLICK", "FILL", "PRESS", "NAVIGATE", "EVALUATE", "SELECT_OPTION");

    private static final List<String> NOT_YET_APPROVED_FUTURE_ACTIONS = Arrays.asList(
            "RETRY_OBSERVATION", "NAVIGATION_RECOMMENDATION");

    @Test
    public void testAllApprovedActionValuesExist() {
        List<String> actualNames = Arrays.stream(AgentAction.values()).map(Enum::name).toList();
        assertThat(actualNames).containsExactlyInAnyOrderElementsOf(APPROVED_ACTIONS);
    }

    @Test
    public void testProhibitedActionsDoNotExistInTheEnum() {
        List<String> actualNames = Arrays.stream(AgentAction.values()).map(Enum::name).toList();
        for (String prohibited : PROHIBITED_ACTIONS) {
            assertThat(actualNames).doesNotContain(prohibited);
        }
    }

    @Test
    public void testFutureNotYetApprovedActionsDoNotExistYet() {
        List<String> actualNames = Arrays.stream(AgentAction.values()).map(Enum::name).toList();
        for (String future : NOT_YET_APPROVED_FUTURE_ACTIONS) {
            assertThat(actualNames).doesNotContain(future);
        }
    }

    @Test
    public void testNoFreeFormExecutionActionExists() {
        // AgentAction is a closed enum, not a String — this is itself the structural guarantee that
        // no free-form/AI-generated action value can ever exist at runtime. Confirmed by type.
        assertThat(AgentAction.class.isEnum()).isTrue();
    }

    @Test
    public void testEnumHasExactlySixValues() {
        assertThat(AgentAction.values()).hasSize(6);
    }
}
