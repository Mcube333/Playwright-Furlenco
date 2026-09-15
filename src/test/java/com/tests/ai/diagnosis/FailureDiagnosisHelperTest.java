package com.tests.ai.diagnosis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.framework.ai.config.AiConfig;
import com.framework.ai.diagnosis.FailureDiagnosis;
import com.framework.ai.diagnosis.FailureDiagnosisHelper;
import com.framework.ai.diagnosis.FailureDiagnosisReporter;
import com.framework.ai.diagnosis.FailureDiagnosisService;
import com.framework.ai.locatoradvisor.runtime.RuntimeLocatorValidator;
import com.framework.ai.model.FailureContext;
import com.framework.ai.service.FailureAnalysisService;
import com.framework.config.ConfigManager;
import com.framework.driver.PlaywrightManager;
import com.microsoft.playwright.Page;
import java.lang.reflect.Field;
import java.util.List;
import org.testng.ITestResult;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

/**
 * Phase 7 Step 6: focused tests for {@link FailureDiagnosisHelper}, the smallest explicit entry
 * point for manually triggering failure diagnosis. Hermetic throughout — no real AI provider, no
 * real browser, no real Allure attachment target: {@link RecordingFailureDiagnosisService} and
 * {@link RecordingFailureDiagnosisReporter} are hand-written subclasses of the real, unmodified
 * {@link FailureDiagnosisService}/{@link FailureDiagnosisReporter} that record their inputs instead
 * of performing real orchestration, following this project's existing no-Mockito convention.
 *
 * PlaywrightManager exposes no public seam for binding a fake Page to the current thread (Phase 6
 * avoided this entirely by taking Page as an explicit method parameter). Since
 * {@link FailureDiagnosisHelper#diagnose(ITestResult)} deliberately calls the existing
 * {@code PlaywrightManager.getPage()} internally (per the Step 6 spec), these tests bind/unbind a
 * fake Page directly on {@code PlaywrightManager}'s private ThreadLocal via reflection — this reads
 * and writes only test-local ThreadLocal state and does not modify {@code PlaywrightManager.java}.
 */
public class FailureDiagnosisHelperTest {

    @AfterMethod(alwaysRun = true)
    public void unbindPageFromCurrentThread() throws Exception {
        setBoundPage(null);
    }

    // ------------------------------------------------------------------------------------------
    // Context construction
    // ------------------------------------------------------------------------------------------

    @Test
    public void testDiagnoseBuildsContextWithTestNameClassAndEnvironment() {
        RecordingFailureDiagnosisService service = new RecordingFailureDiagnosisService();
        AiConfig aiConfig = new AiConfig(ConfigManager.getInstance());
        FailureDiagnosisHelper helper = new FailureDiagnosisHelper(aiConfig, service, new RecordingFailureDiagnosisReporter());

        ITestResult testResult = FailureDiagnosisTestResultTestDouble.builder()
                .methodName("testCheckout")
                .className("com.tests.web.furlenco.FurlencoCartFlowTest")
                .build();

        helper.diagnose(testResult);

        FailureContext context = service.lastContext();
        assertThat(context.getTestName()).isEqualTo("testCheckout");
        assertThat(context.getTestClass()).isEqualTo("com.tests.web.furlenco.FurlencoCartFlowTest");
        assertThat(context.getEnvironment()).isEqualTo(aiConfig.getProvider());
    }

    @Test
    public void testDiagnoseCapturesThrowableMessageAndStackTrace() {
        RecordingFailureDiagnosisService service = new RecordingFailureDiagnosisService();
        FailureDiagnosisHelper helper = newHelper(service, new RecordingFailureDiagnosisReporter());

        ITestResult testResult = FailureDiagnosisTestResultTestDouble.builder()
                .throwable(new IllegalStateException("checkout button not found"))
                .build();

        helper.diagnose(testResult);

        FailureContext context = service.lastContext();
        assertThat(context.getErrorMessage()).isEqualTo("checkout button not found");
        assertThat(context.getStackTrace()).contains("IllegalStateException");
    }

    @Test
    public void testDiagnoseCapturesPageUrlAndTitleWhenPageOpen() throws Exception {
        Page page = FailureDiagnosisPageTestDouble.builder()
                .closed(false)
                .url("https://www.stag.furlenco.com/cart")
                .title("Cart")
                .build();
        setBoundPage(page);

        RecordingFailureDiagnosisService service = new RecordingFailureDiagnosisService();
        FailureDiagnosisHelper helper = newHelper(service, new RecordingFailureDiagnosisReporter());

        helper.diagnose(FailureDiagnosisTestResultTestDouble.builder().build());

        FailureContext context = service.lastContext();
        assertThat(context.getCurrentUrl()).isEqualTo("https://www.stag.furlenco.com/cart");
        assertThat(context.getPageTitle()).isEqualTo("Cart");
    }

    @Test
    public void testDiagnoseCapturesBoundedDomFromPageContent() throws Exception {
        Page page = FailureDiagnosisPageTestDouble.builder()
                .closed(false)
                .content("<div id='checkout'>sample</div>")
                .build();
        setBoundPage(page);

        RecordingFailureDiagnosisService service = new RecordingFailureDiagnosisService();
        FailureDiagnosisHelper helper = newHelper(service, new RecordingFailureDiagnosisReporter());

        helper.diagnose(FailureDiagnosisTestResultTestDouble.builder().build());

        assertThat(service.lastContext().getDomSnippet()).isEqualTo("<div id='checkout'>sample</div>");
    }

    @Test
    public void testDiagnoseComputesExecutionDuration() {
        RecordingFailureDiagnosisService service = new RecordingFailureDiagnosisService();
        FailureDiagnosisHelper helper = newHelper(service, new RecordingFailureDiagnosisReporter());

        ITestResult testResult = FailureDiagnosisTestResultTestDouble.builder()
                .startMillis(1_000L)
                .endMillis(3_500L)
                .build();

        helper.diagnose(testResult);

        assertThat(service.lastContext().getExecutionDurationMs()).isEqualTo(2_500L);
    }

    // ------------------------------------------------------------------------------------------
    // Safety
    // ------------------------------------------------------------------------------------------

    @Test
    public void testDiagnoseWithNullTestResultDelegatesEmptyDiagnosis() {
        RecordingFailureDiagnosisService service = new RecordingFailureDiagnosisService();
        FailureDiagnosisHelper helper = newHelper(service, new RecordingFailureDiagnosisReporter());

        FailureDiagnosis result = helper.diagnose(null);

        assertThat(result).isNotNull();
        assertThat(service.lastContext()).isNull();
        assertThat(service.lastCallHadPageOverload()).isFalse();
        assertThat(service.callCount()).isEqualTo(1);
    }

    @Test
    public void testDiagnoseWithNullThrowableLeavesErrorFieldsEmpty() {
        RecordingFailureDiagnosisService service = new RecordingFailureDiagnosisService();
        FailureDiagnosisHelper helper = newHelper(service, new RecordingFailureDiagnosisReporter());

        helper.diagnose(FailureDiagnosisTestResultTestDouble.builder().throwable(null).build());

        FailureContext context = service.lastContext();
        assertThat(context.getErrorMessage()).isEmpty();
        assertThat(context.getStackTrace()).isEmpty();
    }

    @Test
    public void testDiagnoseWithNoPageBoundLeavesUrlTitleEmptyAndDomUnavailable() {
        // No setBoundPage(...) call — PlaywrightManager.getPage() throws IllegalStateException,
        // exactly as it would for a real API-only test with no browser initialized.
        RecordingFailureDiagnosisService service = new RecordingFailureDiagnosisService();
        FailureDiagnosisHelper helper = newHelper(service, new RecordingFailureDiagnosisReporter());

        helper.diagnose(FailureDiagnosisTestResultTestDouble.builder().build());

        FailureContext context = service.lastContext();
        assertThat(context.getCurrentUrl()).isEmpty();
        assertThat(context.getPageTitle()).isEmpty();
        assertThat(context.getDomSnippet()).isEqualTo("[DOM context unavailable]");
        assertThat(service.lastPage()).isNull();
    }

    @Test
    public void testDiagnoseWithClosedPageLeavesUrlAndTitleEmpty() throws Exception {
        Page page = FailureDiagnosisPageTestDouble.builder().closed(true).url("https://ignored").title("Ignored").build();
        setBoundPage(page);

        RecordingFailureDiagnosisService service = new RecordingFailureDiagnosisService();
        FailureDiagnosisHelper helper = newHelper(service, new RecordingFailureDiagnosisReporter());

        helper.diagnose(FailureDiagnosisTestResultTestDouble.builder().build());

        FailureContext context = service.lastContext();
        assertThat(context.getCurrentUrl()).isEmpty();
        assertThat(context.getPageTitle()).isEmpty();
    }

    @Test
    public void testDiagnoseWhenUrlThrowsLeavesUrlEmptyAndDoesNotPropagate() throws Exception {
        Page page = FailureDiagnosisPageTestDouble.builder()
                .closed(false)
                .urlThrows(new RuntimeException("Simulated url() failure"))
                .title("Cart")
                .build();
        setBoundPage(page);

        RecordingFailureDiagnosisService service = new RecordingFailureDiagnosisService();
        FailureDiagnosisHelper helper = newHelper(service, new RecordingFailureDiagnosisReporter());

        assertThatCode(() -> helper.diagnose(FailureDiagnosisTestResultTestDouble.builder().build()))
                .doesNotThrowAnyException();
        assertThat(service.lastContext().getCurrentUrl()).isEmpty();
        assertThat(service.lastContext().getPageTitle()).isEqualTo("Cart");
    }

    @Test
    public void testDiagnoseWhenTitleThrowsLeavesTitleEmptyAndDoesNotPropagate() throws Exception {
        Page page = FailureDiagnosisPageTestDouble.builder()
                .closed(false)
                .url("https://www.stag.furlenco.com/cart")
                .titleThrows(new RuntimeException("Simulated title() failure"))
                .build();
        setBoundPage(page);

        RecordingFailureDiagnosisService service = new RecordingFailureDiagnosisService();
        FailureDiagnosisHelper helper = newHelper(service, new RecordingFailureDiagnosisReporter());

        helper.diagnose(FailureDiagnosisTestResultTestDouble.builder().build());

        assertThat(service.lastContext().getPageTitle()).isEmpty();
        assertThat(service.lastContext().getCurrentUrl()).isEqualTo("https://www.stag.furlenco.com/cart");
    }

    @Test
    public void testDiagnoseWhenDomExtractionThrowsFallsBackToUnavailableMarker() throws Exception {
        Page page = FailureDiagnosisPageTestDouble.builder()
                .closed(false)
                .contentThrows(new RuntimeException("Simulated content() failure"))
                .build();
        setBoundPage(page);

        RecordingFailureDiagnosisService service = new RecordingFailureDiagnosisService();
        FailureDiagnosisHelper helper = newHelper(service, new RecordingFailureDiagnosisReporter());

        helper.diagnose(FailureDiagnosisTestResultTestDouble.builder().build());

        // DomContextExtractor.extractFromPage() itself already catches the exception.
        assertThat(service.lastContext().getDomSnippet()).isEqualTo("[DOM context unavailable]");
    }

    @Test
    public void testDiagnoseWhenTestResultMethodAccessThrowsFallsBackSafely() {
        RecordingFailureDiagnosisService service = new RecordingFailureDiagnosisService();
        FailureDiagnosisHelper helper = newHelper(service, new RecordingFailureDiagnosisReporter());

        ITestResult testResult = FailureDiagnosisTestResultTestDouble.builder().methodThrows().build();

        assertThatCode(() -> helper.diagnose(testResult)).doesNotThrowAnyException();
        assertThat(service.lastContext().getTestName()).isEmpty();
    }

    @Test
    public void testDiagnoseWhenTestResultTestClassAccessThrowsFallsBackSafely() {
        RecordingFailureDiagnosisService service = new RecordingFailureDiagnosisService();
        FailureDiagnosisHelper helper = newHelper(service, new RecordingFailureDiagnosisReporter());

        ITestResult testResult = FailureDiagnosisTestResultTestDouble.builder().testClassThrows().build();

        assertThatCode(() -> helper.diagnose(testResult)).doesNotThrowAnyException();
        assertThat(service.lastContext().getTestClass()).isEmpty();
    }

    // ------------------------------------------------------------------------------------------
    // Diagnosis delegation
    // ------------------------------------------------------------------------------------------

    @Test
    public void testDiagnosePassesConstructedContextToService() {
        RecordingFailureDiagnosisService service = new RecordingFailureDiagnosisService();
        FailureDiagnosis canned = FailureDiagnosis.builder().build();
        service.returnCanned(canned);
        FailureDiagnosisHelper helper = newHelper(service, new RecordingFailureDiagnosisReporter());

        FailureDiagnosis result = helper.diagnose(FailureDiagnosisTestResultTestDouble.builder().build());

        assertThat(result).isSameAs(canned);
        assertThat(service.lastContext()).isNotNull();
        assertThat(service.lastCallHadPageOverload()).isTrue();
    }

    @Test
    public void testDiagnosePassesPageThroughToServicePageOverload() throws Exception {
        Page page = FailureDiagnosisPageTestDouble.builder().closed(false).build();
        setBoundPage(page);

        RecordingFailureDiagnosisService service = new RecordingFailureDiagnosisService();
        FailureDiagnosisHelper helper = newHelper(service, new RecordingFailureDiagnosisReporter());

        helper.diagnose(FailureDiagnosisTestResultTestDouble.builder().build());

        assertThat(service.lastPage()).isSameAs(page);
    }

    @Test
    public void testHelperDoesNotReferenceRuntimeLocatorValidatorOrAiClasses() {
        Field[] fields = FailureDiagnosisHelper.class.getDeclaredFields();
        for (Field field : fields) {
            Class<?> type = field.getType();
            assertThat(type).isNotEqualTo(RuntimeLocatorValidator.class);
            assertThat(type).isNotEqualTo(FailureAnalysisService.class);
        }
    }

    @Test
    public void testHelperOnlyHoldsItsThreeExplicitCollaborators() {
        Field[] fields = FailureDiagnosisHelper.class.getDeclaredFields();
        List<Class<?>> instanceFieldTypes = java.util.Arrays.stream(fields)
                .filter(f -> !java.lang.reflect.Modifier.isStatic(f.getModifiers()))
                .map(Field::getType)
                .collect(java.util.stream.Collectors.toList());

        assertThat(instanceFieldTypes).containsExactlyInAnyOrder(
                AiConfig.class, FailureDiagnosisService.class, FailureDiagnosisReporter.class);
    }

    // ------------------------------------------------------------------------------------------
    // Reporting
    // ------------------------------------------------------------------------------------------

    @Test
    public void testReportDelegatesToReporterAttachToAllure() {
        RecordingFailureDiagnosisReporter reporter = new RecordingFailureDiagnosisReporter();
        FailureDiagnosisHelper helper = newHelper(new RecordingFailureDiagnosisService(), reporter);

        FailureDiagnosis diagnosis = FailureDiagnosis.builder().build();
        helper.report(diagnosis);

        assertThat(reporter.callCount()).isEqualTo(1);
        assertThat(reporter.lastDiagnosis()).isSameAs(diagnosis);
    }

    @Test
    public void testReportWithNullDiagnosisDoesNotThrow() {
        RecordingFailureDiagnosisReporter reporter = new RecordingFailureDiagnosisReporter();
        FailureDiagnosisHelper helper = newHelper(new RecordingFailureDiagnosisService(), reporter);

        assertThatCode(() -> helper.report(null)).doesNotThrowAnyException();
        assertThat(reporter.callCount()).isEqualTo(1);
    }

    @Test
    public void testReportSwallowsReporterException() {
        RecordingFailureDiagnosisReporter reporter = new RecordingFailureDiagnosisReporter();
        reporter.throwOnAttach(new RuntimeException("Simulated Allure attachment failure"));
        FailureDiagnosisHelper helper = newHelper(new RecordingFailureDiagnosisService(), reporter);

        assertThatCode(() -> helper.report(FailureDiagnosis.builder().build())).doesNotThrowAnyException();
    }

    // ------------------------------------------------------------------------------------------
    // Architecture / explicitness
    // ------------------------------------------------------------------------------------------

    @Test
    public void testHelperReadsOnlyExpectedTestResultMethods() {
        FailureDiagnosisTestResultTestDouble.Builder builder = FailureDiagnosisTestResultTestDouble.builder()
                .throwable(new RuntimeException("boom"));
        ITestResult testResult = builder.build();

        FailureDiagnosisHelper helper = newHelper(new RecordingFailureDiagnosisService(), new RecordingFailureDiagnosisReporter());
        helper.diagnose(testResult);

        assertThat(builder.invokedMethods()).containsOnly(
                "getMethod", "getTestClass", "getThrowable", "getStartMillis", "getEndMillis");
    }

    @Test
    public void testHelperReadsOnlyExpectedPageMethods() throws Exception {
        FailureDiagnosisPageTestDouble.Builder pageBuilder = FailureDiagnosisPageTestDouble.builder().closed(false);
        Page page = pageBuilder.build();
        setBoundPage(page);

        FailureDiagnosisHelper helper = newHelper(new RecordingFailureDiagnosisService(), new RecordingFailureDiagnosisReporter());
        helper.diagnose(FailureDiagnosisTestResultTestDouble.builder().build());

        assertThat(pageBuilder.invokedMethods()).containsOnly("isClosed", "url", "title", "content");
    }

    @Test
    public void testDiagnoseAndReportInvokesBothStepsExplicitly() {
        RecordingFailureDiagnosisService service = new RecordingFailureDiagnosisService();
        FailureDiagnosis canned = FailureDiagnosis.builder().build();
        service.returnCanned(canned);
        RecordingFailureDiagnosisReporter reporter = new RecordingFailureDiagnosisReporter();
        FailureDiagnosisHelper helper = newHelper(service, reporter);

        FailureDiagnosis result = helper.diagnoseAndReport(FailureDiagnosisTestResultTestDouble.builder().build());

        assertThat(result).isSameAs(canned);
        assertThat(service.callCount()).isEqualTo(1);
        assertThat(reporter.callCount()).isEqualTo(1);
        assertThat(reporter.lastDiagnosis()).isSameAs(canned);
    }

    @Test
    public void testDiagnoseNeverThrowsEvenWithFullyBrokenTestResult() {
        ITestResult testResult = FailureDiagnosisTestResultTestDouble.builder()
                .methodThrows()
                .testClassThrows()
                .build();

        FailureDiagnosisHelper helper = newHelper(new RecordingFailureDiagnosisService(), new RecordingFailureDiagnosisReporter());

        assertThatCode(() -> helper.diagnose(testResult)).doesNotThrowAnyException();
    }

    // ------------------------------------------------------------------------------------------
    // Test scaffolding
    // ------------------------------------------------------------------------------------------

    private static FailureDiagnosisHelper newHelper(FailureDiagnosisService service, FailureDiagnosisReporter reporter) {
        return new FailureDiagnosisHelper(new AiConfig(ConfigManager.getInstance()), service, reporter);
    }

    @SuppressWarnings("unchecked")
    private static void setBoundPage(Page page) throws Exception {
        Field field = PlaywrightManager.class.getDeclaredField("PAGE");
        field.setAccessible(true);
        ThreadLocal<Page> threadLocal = (ThreadLocal<Page>) field.get(null);
        if (page == null) {
            threadLocal.remove();
        } else {
            threadLocal.set(page);
        }
    }
}
