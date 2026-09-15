package com.tests.ai.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.framework.ai.agent.AgentObservation;
import com.framework.ai.codegeneration.EvidenceItem;
import com.framework.ai.codegeneration.EvidenceStatus;
import com.framework.ai.diagnosis.FailureDiagnosis;
import com.framework.ai.model.FailureContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.testng.annotations.Test;

/**
 * Phase 8 Step 2: pure data-model tests for {@link AgentObservation}. No Playwright, no AI, no
 * evidence-generation logic anywhere in this file — every {@link EvidenceItem} used here is
 * hand-constructed exactly as an already-established piece of evidence would be.
 */
public class AgentObservationTest {

    private FailureDiagnosis sampleDiagnosis() {
        return FailureDiagnosis.builder()
                .failureContext(FailureContext.builder().testName("testCheckout").build())
                .build();
    }

    private EvidenceItem sampleEvidence(EvidenceStatus status) {
        return EvidenceItem.builder()
                .item("Locator").value("[data-testid='checkout-button']")
                .status(status).source("LocatorAnalysisService").confidence(0.5)
                .build();
    }

    @Test
    public void testValidConstruction() {
        FailureDiagnosis diagnosis = sampleDiagnosis();
        EvidenceItem evidence = sampleEvidence(EvidenceStatus.UNVERIFIED);

        AgentObservation observation = AgentObservation.builder()
                .failureDiagnosis(diagnosis)
                .addEvidenceItem(evidence)
                .build();

        assertThat(observation.getFailureDiagnosis()).isSameAs(diagnosis);
        assertThat(observation.getEvidenceItems()).containsExactly(evidence);
    }

    @Test
    public void testEvidenceListDefaultsToEmptyNeverNull() {
        AgentObservation observation = AgentObservation.builder()
                .failureDiagnosis(sampleDiagnosis())
                .build();

        assertThat(observation.getEvidenceItems()).isNotNull().isEmpty();
    }

    @Test
    public void testDefensiveCopyOfEvidenceList() {
        List<EvidenceItem> source = new ArrayList<>();
        source.add(sampleEvidence(EvidenceStatus.VERIFIED));

        AgentObservation observation = AgentObservation.builder()
                .failureDiagnosis(sampleDiagnosis())
                .evidenceItems(source)
                .build();
        source.add(sampleEvidence(EvidenceStatus.MISSING)); // mutate original AFTER construction

        assertThat(observation.getEvidenceItems()).hasSize(1); // unaffected
    }

    @Test
    public void testReturnedEvidenceListIsUnmodifiable() {
        AgentObservation observation = AgentObservation.builder()
                .failureDiagnosis(sampleDiagnosis())
                .addEvidenceItem(sampleEvidence(EvidenceStatus.INFERRED))
                .build();

        assertThatThrownBy(() -> observation.getEvidenceItems().add(sampleEvidence(EvidenceStatus.VERIFIED)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void testNullEvidenceListIsTreatedAsNoneSuppliedNotAnError() {
        AgentObservation observation = AgentObservation.builder()
                .failureDiagnosis(sampleDiagnosis())
                .evidenceItems(null)
                .build();

        assertThat(observation.getEvidenceItems()).isEmpty();
    }

    @Test
    public void testListContainingNullIsFilteredNotRejected() {
        List<EvidenceItem> withNull = Arrays.asList(sampleEvidence(EvidenceStatus.UNVERIFIED), null);

        AgentObservation observation = AgentObservation.builder()
                .failureDiagnosis(sampleDiagnosis())
                .evidenceItems(withNull)
                .build();

        assertThat(observation.getEvidenceItems()).hasSize(1);
        assertThat(observation.getEvidenceItems().get(0)).isNotNull();
    }

    @Test
    public void testNullFailureDiagnosisFailsFast() {
        assertThatThrownBy(() -> AgentObservation.builder().failureDiagnosis(null).build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void testEvidenceStatusRemainsExactlyAsSupplied() {
        EvidenceItem verified = sampleEvidence(EvidenceStatus.VERIFIED);
        EvidenceItem missing = sampleEvidence(EvidenceStatus.MISSING);

        AgentObservation observation = AgentObservation.builder()
                .failureDiagnosis(sampleDiagnosis())
                .addEvidenceItem(verified)
                .addEvidenceItem(missing)
                .build();

        // AgentObservation must never compute or upgrade status — it only stores what was given.
        assertThat(observation.getEvidenceItems().get(0).getStatus()).isEqualTo(EvidenceStatus.VERIFIED);
        assertThat(observation.getEvidenceItems().get(1).getStatus()).isEqualTo(EvidenceStatus.MISSING);
    }

    @Test
    public void testNoSetterOrExecutionMethodsExist() {
        for (var method : AgentObservation.class.getDeclaredMethods()) {
            assertThat(method.getName()).doesNotStartWith("set");
            assertThat(Arrays.asList("execute", "apply", "heal", "run", "approve", "transition"))
                    .doesNotContain(method.getName());
        }
    }
}
