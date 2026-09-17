package com.tests.ai.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.framework.ai.agent.AgentAction;
import com.framework.ai.codegeneration.EvidenceItem;
import com.framework.ai.codegeneration.EvidenceStatus;
import com.framework.ai.orchestration.AgentActionResult;
import com.framework.ai.orchestration.AgentActionResultStatus;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import org.testng.annotations.Test;

/**
 * Phase 10 Step 2: focused tests for the {@link AgentActionResult} immutable model. Fully
 * hermetic — no AI, no Playwright, no network.
 */
public class AgentActionResultTest {

    private EvidenceItem sampleEvidence() {
        return EvidenceItem.builder().item("Locator").value("#new")
                .status(EvidenceStatus.VERIFIED).source("test").confidence(0.9).build();
    }

    // 1. builder creates valid NOT_EXECUTED result
    @Test
    public void testBuilderCreatesValidNotExecutedResult() {
        AgentActionResult result = AgentActionResult.builder()
                .action(AgentAction.LOCATOR_RECOMMENDATION)
                .status(AgentActionResultStatus.NOT_EXECUTED)
                .build();

        assertThat(result.getStatus()).isEqualTo(AgentActionResultStatus.NOT_EXECUTED);
        assertThat(result.getAction()).isEqualTo(AgentAction.LOCATOR_RECOMMENDATION);
        assertThat(result.getStartedAt()).isNull();
        assertThat(result.getCompletedAt()).isNull();
    }

    // 2. EXECUTED status can be represented as data
    @Test
    public void testExecutedStatusCanBeRepresentedAsData() {
        AgentActionResult result = AgentActionResult.builder()
                .action(AgentAction.LOCATOR_RECOMMENDATION)
                .status(AgentActionResultStatus.EXECUTED)
                .build();

        assertThat(result.getStatus()).isEqualTo(AgentActionResultStatus.EXECUTED);
    }

    // 3. FAILED status can be represented as data
    @Test
    public void testFailedStatusCanBeRepresentedAsData() {
        AgentActionResult result = AgentActionResult.builder()
                .action(AgentAction.LOCATOR_RECOMMENDATION)
                .status(AgentActionResultStatus.FAILED)
                .errorType("TimeoutError")
                .build();

        assertThat(result.getStatus()).isEqualTo(AgentActionResultStatus.FAILED);
        assertThat(result.getErrorType()).isEqualTo("TimeoutError");
    }

    // 4. BLOCKED status can be represented as data
    @Test
    public void testBlockedStatusCanBeRepresentedAsData() {
        AgentActionResult result = AgentActionResult.builder()
                .action(AgentAction.NONE)
                .status(AgentActionResultStatus.BLOCKED)
                .message("Withheld by AgentExecutionGuard")
                .build();

        assertThat(result.getStatus()).isEqualTo(AgentActionResultStatus.BLOCKED);
    }

    // 5. null status handling
    @Test
    public void testNullStatusRejected() {
        assertThatThrownBy(() -> AgentActionResult.builder().action(AgentAction.NONE).build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void testNullActionRejected() {
        assertThatThrownBy(() -> AgentActionResult.builder().status(AgentActionResultStatus.NOT_EXECUTED).build())
                .isInstanceOf(NullPointerException.class);
    }

    // 6. defensive evidence list
    @Test
    public void testEvidenceListIsDefensivelyCopied() {
        List<EvidenceItem> source = new java.util.ArrayList<>();
        source.add(sampleEvidence());

        AgentActionResult result = AgentActionResult.builder()
                .action(AgentAction.LOCATOR_RECOMMENDATION)
                .status(AgentActionResultStatus.NOT_EXECUTED)
                .evidenceItems(source)
                .build();

        source.add(sampleEvidence());

        assertThat(result.getEvidenceItems()).hasSize(1);
    }

    // 7. immutable evidence list
    @Test
    public void testEvidenceListIsUnmodifiable() {
        AgentActionResult result = AgentActionResult.builder()
                .action(AgentAction.LOCATOR_RECOMMENDATION)
                .status(AgentActionResultStatus.NOT_EXECUTED)
                .addEvidenceItem(sampleEvidence())
                .build();

        assertThatThrownBy(() -> result.getEvidenceItems().add(sampleEvidence()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // 8. null evidence list
    @Test
    public void testNullEvidenceListBecomesEmptyList() {
        AgentActionResult result = AgentActionResult.builder()
                .action(AgentAction.LOCATOR_RECOMMENDATION)
                .status(AgentActionResultStatus.NOT_EXECUTED)
                .evidenceItems(null)
                .build();

        assertThat(result.getEvidenceItems()).isNotNull().isEmpty();
    }

    // 9. message retention
    @Test
    public void testMessageIsRetainedExactly() {
        AgentActionResult result = AgentActionResult.builder()
                .action(AgentAction.LOCATOR_RECOMMENDATION)
                .status(AgentActionResultStatus.NOT_EXECUTED)
                .message("No executor exists; recorded for audit purposes only.")
                .build();

        assertThat(result.getMessage()).isEqualTo("No executor exists; recorded for audit purposes only.");
    }

    @Test
    public void testNullMessageBecomesEmptyString() {
        AgentActionResult result = AgentActionResult.builder()
                .action(AgentAction.LOCATOR_RECOMMENDATION)
                .status(AgentActionResultStatus.NOT_EXECUTED)
                .build();

        assertThat(result.getMessage()).isNotNull().isEmpty();
    }

    // 10. error type retention
    @Test
    public void testErrorTypeIsRetainedExactly() {
        AgentActionResult result = AgentActionResult.builder()
                .action(AgentAction.LOCATOR_RECOMMENDATION)
                .status(AgentActionResultStatus.FAILED)
                .errorType("ElementNotFoundError")
                .build();

        assertThat(result.getErrorType()).isEqualTo("ElementNotFoundError");
    }

    // 11. timestamp retention
    @Test
    public void testTimestampsAreRetainedExactlyWhenSupplied() {
        Instant started = Instant.parse("2026-01-01T00:00:00Z");
        Instant completed = Instant.parse("2026-01-01T00:00:05Z");

        AgentActionResult result = AgentActionResult.builder()
                .action(AgentAction.LOCATOR_RECOMMENDATION)
                .status(AgentActionResultStatus.EXECUTED)
                .startedAt(started)
                .completedAt(completed)
                .build();

        assertThat(result.getStartedAt()).isEqualTo(started);
        assertThat(result.getCompletedAt()).isEqualTo(completed);
    }

    // No confidence field exists on this model at all (structural proof).
    @Test
    public void testNoConfidenceFieldExistsOnThisModel() {
        for (Method method : AgentActionResult.class.getDeclaredMethods()) {
            assertThat(method.getName()).doesNotContain("Confidence");
        }
    }
}
