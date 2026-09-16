package com.tests.ai.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.framework.ai.agent.SelfHealingRecommendation;
import com.framework.ai.codegeneration.EvidenceItem;
import com.framework.ai.codegeneration.EvidenceStatus;
import com.framework.ai.diagnosis.FixType;
import com.framework.ai.orchestration.AgentApprovalRecord;
import com.framework.ai.orchestration.AgentApprovalStatus;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Arrays;
import org.testng.annotations.Test;

/**
 * Phase 9 Step 6: focused tests for the {@link AgentApprovalRecord} immutable model. Fully
 * hermetic — no AI, no Playwright, no network. Every {@link SelfHealingRecommendation} is
 * hand-built directly, mirroring the existing project fixture convention (e.g.
 * {@code SelfHealingRecommendationServiceTest}).
 */
public class AgentApprovalRecordTest {

    private SelfHealingRecommendation sampleRecommendation() {
        return SelfHealingRecommendation.builder()
                .recommendationId("rec-approval-1")
                .fixType(FixType.LOCATOR)
                .description("Review the proposed locator.")
                .proposedLocator("#new")
                .confidence(0.5)
                .addEvidenceItem(EvidenceItem.builder().item("Locator").value("#new")
                        .status(EvidenceStatus.VERIFIED).source("test").confidence(0.9).build())
                .approvalRequired(true)
                .build();
    }

    // ------------------------------------------------------------------------------------------
    // 1-3. Creation for each status
    // ------------------------------------------------------------------------------------------

    @Test
    public void testPendingCreation() {
        AgentApprovalRecord record = AgentApprovalRecord.builder()
                .recommendation(sampleRecommendation())
                .status(AgentApprovalStatus.PENDING)
                .build();

        assertThat(record.getStatus()).isEqualTo(AgentApprovalStatus.PENDING);
    }

    @Test
    public void testApprovedCreation() {
        AgentApprovalRecord record = AgentApprovalRecord.builder()
                .recommendation(sampleRecommendation())
                .status(AgentApprovalStatus.APPROVED)
                .reason("Locator confirmed manually against staging.")
                .build();

        assertThat(record.getStatus()).isEqualTo(AgentApprovalStatus.APPROVED);
    }

    @Test
    public void testRejectedCreation() {
        AgentApprovalRecord record = AgentApprovalRecord.builder()
                .recommendation(sampleRecommendation())
                .status(AgentApprovalStatus.REJECTED)
                .reason("Not applicable to this release.")
                .build();

        assertThat(record.getStatus()).isEqualTo(AgentApprovalStatus.REJECTED);
    }

    // ------------------------------------------------------------------------------------------
    // 4. Null recommendation rejection — for every status, "invalid" per spec
    // ------------------------------------------------------------------------------------------

    @Test
    public void testNullRecommendationRejectedForPending() {
        assertThatThrownBy(() -> AgentApprovalRecord.builder().status(AgentApprovalStatus.PENDING).build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void testNullRecommendationRejectedForApproved() {
        assertThatThrownBy(() -> AgentApprovalRecord.builder().status(AgentApprovalStatus.APPROVED).build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void testNullRecommendationRejectedForRejected() {
        assertThatThrownBy(() -> AgentApprovalRecord.builder().status(AgentApprovalStatus.REJECTED).build())
                .isInstanceOf(NullPointerException.class);
    }

    // ------------------------------------------------------------------------------------------
    // 5. Null status rejection
    // ------------------------------------------------------------------------------------------

    @Test
    public void testNullStatusRejected() {
        assertThatThrownBy(() -> AgentApprovalRecord.builder().recommendation(sampleRecommendation()).build())
                .isInstanceOf(NullPointerException.class);
    }

    // ------------------------------------------------------------------------------------------
    // 6. Immutability
    // ------------------------------------------------------------------------------------------

    @Test
    public void testAllDeclaredFieldsAreFinal() {
        for (Field field : AgentApprovalRecord.class.getDeclaredFields()) {
            assertThat(Modifier.isFinal(field.getModifiers()))
                    .describedAs("AgentApprovalRecord.%s must be final", field.getName())
                    .isTrue();
        }
    }

    @Test
    public void testNoSetterMethodsExist() {
        for (Method method : AgentApprovalRecord.class.getDeclaredMethods()) {
            assertThat(method.getName())
                    .describedAs("AgentApprovalRecord must not declare a setter")
                    .doesNotMatch("^set[A-Z].*");
        }
    }

    // ------------------------------------------------------------------------------------------
    // 7. Equality/hashCode — deliberately NOT overridden, matching the existing Phase 8 convention
    //    (AgentDecision, SelfHealingRecommendation, etc. rely on identity semantics too).
    // ------------------------------------------------------------------------------------------

    @Test
    public void testEqualsUsesIdentitySemanticsConsistentWithProjectConvention() {
        SelfHealingRecommendation recommendation = sampleRecommendation();
        AgentApprovalRecord first = AgentApprovalRecord.builder()
                .recommendation(recommendation).status(AgentApprovalStatus.APPROVED).build();
        AgentApprovalRecord second = AgentApprovalRecord.builder()
                .recommendation(recommendation).status(AgentApprovalStatus.APPROVED).build();

        assertThat(first).isNotEqualTo(second);
        assertThat(first).isEqualTo(first);
    }

    // ------------------------------------------------------------------------------------------
    // 8. Safe toString — sensitive-looking reason/actor content is sanitized at render time
    // ------------------------------------------------------------------------------------------

    @Test
    public void testToStringSanitizesSensitiveReasonAndActorContent() {
        AgentApprovalRecord record = AgentApprovalRecord.builder()
                .recommendation(sampleRecommendation())
                .status(AgentApprovalStatus.APPROVED)
                .reason("session=deadbeef1234567890; password=hunter2")
                .actor("token=abc123secretvalue")
                .build();

        String rendered = record.toString();

        assertThat(rendered)
                .doesNotContain("deadbeef1234567890")
                .doesNotContain("hunter2")
                .doesNotContain("abc123secretvalue")
                .contains("REDACTED");
    }

    @Test
    public void testToStringIncludesRecommendationIdAndStatus() {
        AgentApprovalRecord record = AgentApprovalRecord.builder()
                .recommendation(sampleRecommendation())
                .status(AgentApprovalStatus.REJECTED)
                .build();

        assertThat(record.toString()).contains("rec-approval-1").contains("REJECTED");
    }

    // ------------------------------------------------------------------------------------------
    // 9-10. Timestamp presence and immutability
    // ------------------------------------------------------------------------------------------

    @Test
    public void testTimestampIsNeverNullAndDefaultsToNow() {
        Instant before = Instant.now();
        AgentApprovalRecord record = AgentApprovalRecord.builder()
                .recommendation(sampleRecommendation()).status(AgentApprovalStatus.PENDING).build();
        Instant after = Instant.now();

        assertThat(record.getTimestamp()).isNotNull();
        assertThat(record.getTimestamp()).isBetween(before, after);
    }

    @Test
    public void testExplicitTimestampIsPreservedExactly() {
        Instant fixed = Instant.parse("2026-01-01T00:00:00Z");
        AgentApprovalRecord record = AgentApprovalRecord.builder()
                .recommendation(sampleRecommendation()).status(AgentApprovalStatus.PENDING)
                .timestamp(fixed).build();

        assertThat(record.getTimestamp()).isEqualTo(fixed);
    }

    @Test
    public void testTimestampTypeIsImmutableInstant() {
        assertThat(Instant.class.getName()).isEqualTo("java.time.Instant");
        // java.time.Instant is itself immutable by JDK design; asserting the field's declared
        // type here documents that this class never uses a mutable java.util.Date.
        boolean usesInstant = Arrays.stream(AgentApprovalRecord.class.getDeclaredFields())
                .anyMatch(f -> f.getType().equals(Instant.class));
        assertThat(usesInstant).isTrue();
    }

    // ------------------------------------------------------------------------------------------
    // 11-12. Optional reason/actor handling
    // ------------------------------------------------------------------------------------------

    @Test
    public void testNullReasonBecomesEmptyString() {
        AgentApprovalRecord record = AgentApprovalRecord.builder()
                .recommendation(sampleRecommendation()).status(AgentApprovalStatus.PENDING).build();

        assertThat(record.getReason()).isNotNull().isEmpty();
    }

    @Test
    public void testNullActorBecomesEmptyString() {
        AgentApprovalRecord record = AgentApprovalRecord.builder()
                .recommendation(sampleRecommendation()).status(AgentApprovalStatus.PENDING).build();

        assertThat(record.getActor()).isNotNull().isEmpty();
    }

    // ------------------------------------------------------------------------------------------
    // Recommendation identity preservation
    // ------------------------------------------------------------------------------------------

    @Test
    public void testGetRecommendationReturnsTheExactSameObjectReference() {
        SelfHealingRecommendation recommendation = sampleRecommendation();
        AgentApprovalRecord record = AgentApprovalRecord.builder()
                .recommendation(recommendation).status(AgentApprovalStatus.APPROVED).build();

        assertThat(record.getRecommendation()).isSameAs(recommendation);
    }

    @Test
    public void testGetRecommendationIdDelegatesToTheUnderlyingRecommendation() {
        SelfHealingRecommendation recommendation = sampleRecommendation();
        AgentApprovalRecord record = AgentApprovalRecord.builder()
                .recommendation(recommendation).status(AgentApprovalStatus.APPROVED).build();

        assertThat(record.getRecommendationId()).isEqualTo(recommendation.getRecommendationId());
    }
}
