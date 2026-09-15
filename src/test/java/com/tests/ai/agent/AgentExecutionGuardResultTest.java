package com.tests.ai.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.framework.ai.agent.AgentAction;
import com.framework.ai.agent.AgentExecutionGuardResult;
import com.framework.ai.codegeneration.EvidenceItem;
import com.framework.ai.codegeneration.EvidenceStatus;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.testng.annotations.Test;

/**
 * Phase 8 Step 5: pure data-model tests for {@link AgentExecutionGuardResult}. No Playwright, no
 * AI, no execution behavior anywhere in this file.
 */
public class AgentExecutionGuardResultTest {

    private EvidenceItem sampleEvidence(EvidenceStatus status) {
        return EvidenceItem.builder().item("Locator").value("x").status(status).confidence(0.5).build();
    }

    @Test
    public void testAllowedDefaultsToFalse() {
        AgentExecutionGuardResult result = AgentExecutionGuardResult.builder().build();

        assertThat(result.isAllowed()).isFalse();
    }

    @Test
    public void testActionDefaultsToNoneWhenNotSupplied() {
        AgentExecutionGuardResult result = AgentExecutionGuardResult.builder().build();

        assertThat(result.getAction()).isEqualTo(AgentAction.NONE);
    }

    @Test
    public void testReasonDefaultsToEmptyStringNotNull() {
        AgentExecutionGuardResult result = AgentExecutionGuardResult.builder().build();

        assertThat(result.getReason()).isNotNull().isEmpty();
    }

    @Test
    public void testValidBlockedResult() {
        AgentExecutionGuardResult result = AgentExecutionGuardResult.builder()
                .allowed(false)
                .action(AgentAction.LOCATOR_RECOMMENDATION)
                .reason("Agent execution is disabled.")
                .confidence(0.9)
                .requiresApproval(true)
                .build();

        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getAction()).isEqualTo(AgentAction.LOCATOR_RECOMMENDATION);
        assertThat(result.getReason()).isEqualTo("Agent execution is disabled.");
    }

    @Test
    public void testHighConfidenceDoesNotImplyAllowed() {
        AgentExecutionGuardResult result = AgentExecutionGuardResult.builder()
                .allowed(false).confidence(1.0).build();

        assertThat(result.getConfidence()).isEqualTo(1.0);
        assertThat(result.isAllowed()).isFalse();
    }

    @Test
    public void testVerifiedEvidenceDoesNotImplyAllowed() {
        AgentExecutionGuardResult result = AgentExecutionGuardResult.builder()
                .allowed(false)
                .addEvidenceItem(sampleEvidence(EvidenceStatus.VERIFIED))
                .build();

        assertThat(result.getEvidenceItems().get(0).getStatus()).isEqualTo(EvidenceStatus.VERIFIED);
        assertThat(result.isAllowed()).isFalse();
    }

    @Test
    public void testRequiresApprovalTrueDoesNotImplyApprovalGranted() {
        AgentExecutionGuardResult result = AgentExecutionGuardResult.builder()
                .allowed(false).requiresApproval(true).build();

        assertThat(result.isRequiresApproval()).isTrue();
        assertThat(result.isAllowed()).isFalse();
    }

    @Test
    public void testDefensiveCopyOfEvidenceList() {
        List<EvidenceItem> source = new ArrayList<>();
        source.add(sampleEvidence(EvidenceStatus.UNVERIFIED));

        AgentExecutionGuardResult result = AgentExecutionGuardResult.builder().evidenceItems(source).build();
        source.add(sampleEvidence(EvidenceStatus.VERIFIED));

        assertThat(result.getEvidenceItems()).hasSize(1);
    }

    @Test
    public void testReturnedEvidenceListIsUnmodifiable() {
        AgentExecutionGuardResult result = AgentExecutionGuardResult.builder()
                .addEvidenceItem(sampleEvidence(EvidenceStatus.MISSING)).build();

        assertThatThrownBy(() -> result.getEvidenceItems().add(sampleEvidence(EvidenceStatus.VERIFIED)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void testListContainingNullIsFiltered() {
        List<EvidenceItem> withNull = Arrays.asList(sampleEvidence(EvidenceStatus.UNVERIFIED), null);

        AgentExecutionGuardResult result = AgentExecutionGuardResult.builder().evidenceItems(withNull).build();

        assertThat(result.getEvidenceItems()).hasSize(1);
    }

    @Test
    public void testNullEvidenceListDefaultsToEmptyNotAnError() {
        AgentExecutionGuardResult result = AgentExecutionGuardResult.builder().evidenceItems(null).build();

        assertThat(result.getEvidenceItems()).isEmpty();
    }

    @Test
    public void testNoSetterOrExecutionMethodsExist() {
        for (Method method : AgentExecutionGuardResult.class.getDeclaredMethods()) {
            assertThat(method.getName()).doesNotStartWith("set");
            assertThat(Arrays.asList("execute", "apply", "run", "runAction", "selfHeal", "approve"))
                    .doesNotContain(method.getName());
        }
    }
}
