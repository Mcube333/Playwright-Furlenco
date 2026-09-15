package com.tests.ai.diagnosis;

import com.framework.ai.diagnosis.FailureDiagnosis;
import com.framework.ai.diagnosis.FailureDiagnosisService;
import com.framework.ai.model.FailureContext;
import com.microsoft.playwright.Page;

/**
 * Phase 7 Step 6 test double: a hand-written subclass of the real, unmodified
 * {@link FailureDiagnosisService} that records what it was called with instead of performing any
 * real AI/locator/runtime orchestration. This lets {@code FailureDiagnosisHelperTest} assert
 * exactly what {@link com.framework.ai.diagnosis.FailureDiagnosisHelper} passes downstream —
 * the constructed {@link FailureContext} and the {@link Page} reference — without needing a
 * hermetic AI client stub or a live Playwright Page, and without modifying
 * {@code FailureDiagnosisService} itself.
 *
 * The default (super()) constructor is used only because it is the cheapest way to obtain a valid
 * instance; since every {@code diagnose(...)} overload is overridden below, none of the real
 * collaborators it constructs are ever invoked.
 */
final class RecordingFailureDiagnosisService extends FailureDiagnosisService {

    private FailureContext lastContext;
    private Page lastPage;
    private boolean lastCallHadPageOverload;
    private int callCount;
    private FailureDiagnosis canned = FailureDiagnosis.builder().build();

    void returnCanned(FailureDiagnosis diagnosis) {
        this.canned = diagnosis;
    }

    FailureContext lastContext() {
        return lastContext;
    }

    Page lastPage() {
        return lastPage;
    }

    boolean lastCallHadPageOverload() {
        return lastCallHadPageOverload;
    }

    int callCount() {
        return callCount;
    }

    @Override
    public FailureDiagnosis diagnose(FailureContext failureContext) {
        this.lastContext = failureContext;
        this.lastPage = null;
        this.lastCallHadPageOverload = false;
        this.callCount++;
        return canned;
    }

    @Override
    public FailureDiagnosis diagnose(FailureContext failureContext, Page page) {
        this.lastContext = failureContext;
        this.lastPage = page;
        this.lastCallHadPageOverload = true;
        this.callCount++;
        return canned;
    }
}
