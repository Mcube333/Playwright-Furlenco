package com.framework.ai.orchestration;

import com.framework.ai.codegeneration.EvidenceItem;
import com.framework.ai.sanitizer.SensitiveDataSanitizer;
import java.util.List;
import java.util.Objects;

/**
 * Phase 10 Step 2: a pure, read-only, in-memory human summary of the current contents of an
 * {@link AgentActionAuditStore}.
 *
 * PURE PRESENTATION — this class computes nothing beyond string formatting and holds no state of
 * its own beyond the injected {@link AgentActionAuditStore} reference. Every value rendered is
 * read verbatim from {@link AgentActionAuditStore#findAll()} — the store remains the single source
 * of truth, and this class never caches an entry, never keeps its own collection, and never
 * re-derives one field from another.
 *
 * NEVER CLAIMS EXECUTION THAT WASN'T SUPPLIED. This is the central safety property of the whole
 * audit layer: {@link #summarize()} prints {@link AgentActionResult#getStatus()} exactly as given
 * — it never prints "executed" unless that {@link AgentActionResultStatus} literally is
 * {@link AgentActionResultStatus#EXECUTED}, and it never derives that conclusion from
 * {@link AgentApprovalRecord#getStatus()} being {@code APPROVED}, from an {@code EvidenceStatus} of
 * {@code VERIFIED}, or from any confidence value — there is no confidence field anywhere in
 * {@link AgentActionResult} for this class to even read.
 *
 * DETERMINISTIC ORDER. Entries are rendered in exactly the order
 * {@link AgentActionAuditStore#findAll()} returns them (insertion order) — no sorting, no
 * reordering, no filtering.
 *
 * SANITIZED AT RENDER TIME. Every free-text field (approval reason/actor, this audit entry's own
 * actor, the result's message/error type, and each evidence item's item/value) is passed through
 * the existing, unmodified {@link SensitiveDataSanitizer} before being written into the returned
 * string — the same "sanitize at the point of display" convention already used by
 * {@code FailureDiagnosisReporter}/{@code SelfHealingRecommendationReporter}/
 * {@code AgentApprovalRecord.toString()}.
 *
 * NO OUTPUT CHANNEL. {@link #summarize()} returns an in-memory {@link String} only — there is no
 * file write, no network call, no database call, and no console print anywhere in this class.
 */
public class AgentActionAuditReporter {

    private static final String NOT_AVAILABLE = "Not available";

    private final AgentActionAuditStore store;

    /**
     * @throws NullPointerException if {@code store} is {@code null} — matching the same
     *                               fail-fast-on-a-required-dependency convention already used by
     *                               {@link AgentApprovalSummaryReporter}'s own constructor.
     */
    public AgentActionAuditReporter(AgentActionAuditStore store) {
        this.store = Objects.requireNonNull(store, "AgentActionAuditStore must not be null");
    }

    /**
     * Builds a fresh, human-readable summary of every audit entry currently in the store. Never
     * throws, never executes anything, and never changes any stored value.
     */
    public String summarize() {
        List<AgentActionAuditRecord> entries = store.findAll();

        StringBuilder sb = new StringBuilder();
        sb.append("Agent Action Audit\n");
        sb.append("-------------------\n");
        sb.append("Total: ").append(entries.size()).append("\n\n");

        if (entries.isEmpty()) {
            sb.append("No audit records.\n");
            return sb.toString();
        }

        int index = 1;
        for (AgentActionAuditRecord entry : entries) {
            appendEntry(sb, entry, index++);
        }
        return sb.toString();
    }

    private void appendEntry(StringBuilder sb, AgentActionAuditRecord entry, int index) {
        AgentApprovalRecord approval = entry.getApprovalRecord();
        AgentActionResult result = entry.getActionResult();

        sb.append("Entry ").append(index).append("\n");
        sb.append("  Approval ID: ").append(sanitize(approval.getRecommendationId())).append("\n");
        sb.append("  Action: ").append(result.getAction()).append("\n");
        sb.append("  Approval Status: ").append(approval.getStatus()).append("\n");
        sb.append("  Result Status: ").append(result.getStatus()).append("\n");
        sb.append("  Actor: ").append(displayOrNotAvailable(entry.getActor())).append("\n");
        sb.append("  Recorded At: ").append(entry.getRecordedAt()).append("\n");
        sb.append("  Message: ").append(displayOrNotAvailable(result.getMessage())).append("\n");
        sb.append("  Error Type: ").append(displayOrNotAvailable(result.getErrorType())).append("\n");
        sb.append("  Evidence: ");
        appendEvidence(sb, result.getEvidenceItems());
        sb.append("\n");
    }

    private void appendEvidence(StringBuilder sb, List<EvidenceItem> evidenceItems) {
        if (evidenceItems.isEmpty()) {
            sb.append(NOT_AVAILABLE).append("\n");
            return;
        }
        sb.append("\n");
        for (EvidenceItem item : evidenceItems) {
            sb.append("    - ").append(sanitize(item.getItem())).append(": `")
                    .append(sanitize(item.getValue())).append("` -- ").append(item.getStatus()).append("\n");
        }
    }

    private String sanitize(String value) {
        return value == null ? "" : SensitiveDataSanitizer.sanitize(value);
    }

    private String displayOrNotAvailable(String value) {
        String sanitized = sanitize(value);
        return sanitized.isBlank() ? NOT_AVAILABLE : sanitized;
    }
}
