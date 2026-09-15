package com.tests.ai.diagnosis;

import com.framework.ai.diagnosis.FailureDiagnosis;
import com.framework.ai.diagnosis.FailureDiagnosisReporter;

/**
 * Phase 7 Step 6 test double: a hand-written subclass of the real, unmodified
 * {@link FailureDiagnosisReporter} that records {@code attachToAllure(...)} invocations instead of
 * (or in addition to, when {@link #alsoDelegateToReal} is set) calling the real Allure API. Used to
 * assert precisely that {@code FailureDiagnosisHelper.report(...)} delegates to the reporter, and to
 * simulate a reporting failure without needing a live Allure test context.
 */
final class RecordingFailureDiagnosisReporter extends FailureDiagnosisReporter {

    private FailureDiagnosis lastDiagnosis;
    private int callCount;
    private RuntimeException throwOnAttach;

    void throwOnAttach(RuntimeException exception) {
        this.throwOnAttach = exception;
    }

    FailureDiagnosis lastDiagnosis() {
        return lastDiagnosis;
    }

    int callCount() {
        return callCount;
    }

    @Override
    public void attachToAllure(FailureDiagnosis diagnosis) {
        this.lastDiagnosis = diagnosis;
        this.callCount++;
        if (throwOnAttach != null) {
            throw throwOnAttach;
        }
    }
}
