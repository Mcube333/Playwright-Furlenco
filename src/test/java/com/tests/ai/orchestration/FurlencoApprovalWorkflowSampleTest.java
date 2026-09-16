package com.tests.ai.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import com.framework.ai.agent.AgentAction;
import com.framework.ai.agent.AgentContext;
import com.framework.ai.agent.AgentDecision;
import com.framework.ai.agent.AgentExecutionGuard;
import com.framework.ai.agent.AgentReasoningService;
import com.framework.ai.agent.AgentState;
import com.framework.ai.agent.SelfHealingRecommendation;
import com.framework.ai.agent.SelfHealingRecommendationService;
import com.framework.ai.codegeneration.EvidenceStatus;
import com.framework.ai.diagnosis.FailureDiagnosis;
import com.framework.ai.diagnosis.FailureDiagnosisHelper;
import com.framework.ai.locatoradvisor.LocatorAnalysisResponse;
import com.framework.ai.locatoradvisor.LocatorCandidate;
import com.framework.ai.locatoradvisor.ValidationType;
import com.framework.ai.model.AiAnalysisResponse;
import com.framework.ai.model.FailureCategory;
import com.framework.ai.model.FailureContext;
import com.framework.ai.orchestration.AgentApprovalRecord;
import com.framework.ai.orchestration.AgentApprovalRecordStore;
import com.framework.ai.orchestration.AgentApprovalService;
import com.framework.ai.orchestration.AgentApprovalStatus;
import com.framework.ai.orchestration.AgentApprovalSummaryReporter;
import com.framework.ai.orchestration.AgentOrchestrationService;
import com.framework.ai.orchestration.AgentRecommendationConsumer;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.List;
import org.testng.IClass;
import org.testng.ITestNGMethod;
import org.testng.ITestResult;
import org.testng.annotations.Test;

/**
 * Phase 9 — living usage documentation, not a boundary/unit test: a single, fully-runnable,
 * end-to-end walkthrough of the explicit Phase 9 workflow against a realistic Furlenco failure,
 * for a QA engineer to read top-to-bottom and copy from.
 *
 * <pre>
 * FailureDiagnosisHelper -&gt; AgentRecommendationConsumer -&gt; AgentApprovalService
 *         -&gt; AgentApprovalRecordStore -&gt; AgentApprovalSummaryReporter
 * </pre>
 *
 * THE SCENARIO. {@code FurlencoCartFlowTest#verifyCartDrawerOpenAndClose} (see
 * {@code src/test/java/com/tests/web/furlenco/FurlencoCartFlowTest.java}) drives
 * {@code FurlencoHomePage#openCart()}, which clicks the header cart button located by
 * {@code button[aria-label='Cart']} (see {@code FurlencoHomePage.CART_BUTTON}). This test imagines
 * that selector going stale after a Furlenco header redesign — a completely ordinary,
 * realistic locator-drift failure — and walks through what a QA engineer explicitly does about it.
 *
 * TWO HALVES, BOTH REAL CODE, NEITHER USING A LIVE AI CALL (this environment has {@code ai.enabled=false}
 * by default, exactly as the framework ships):
 *
 * <ol>
 *   <li>{@link #step1_diagnoseTheFailureWithFailureDiagnosisHelper()} calls the real, unmodified
 *       {@link FailureDiagnosisHelper#diagnose(ITestResult)} against a hand-built {@link ITestResult}
 *       representing the failing Furlenco test — exactly what a QA engineer runs immediately after
 *       a failure, from a debugger, a triage script, or a REPL. With AI disabled this documents the
 *       framework's own safe fallback: no fabricated root cause, just an honest, mostly-empty
 *       diagnosis.</li>
 *   <li>{@link #step2to5_consumeApproveStoreAndSummarizeARealisticRecommendation()} picks up from
 *       there with a diagnosis shaped exactly like what {@code FailureDiagnosisHelper} would have
 *       produced <em>had</em> {@code ai.enabled=true} and the Locator Advisor already found a
 *       DOM-verified replacement for the stale cart button selector — then runs that diagnosis
 *       through the real, unmodified {@link AgentRecommendationConsumer},
 *       {@link AgentApprovalService}, {@link AgentApprovalRecordStore}, and
 *       {@link AgentApprovalSummaryReporter}. The only stand-in is
 *       {@link AgentReasoningService} itself (a local subclass, standing in for a live AI response
 *       proposing the locator fix) — everything downstream of it is real, unmodified Phase 8/9
 *       code.</li>
 * </ol>
 *
 * At every step the recommendation stays a human-review artifact: {@code approvalRequired} is
 * always {@code true}, the guard never grants execution, and nothing here clicks, fills, navigates,
 * or otherwise touches a real Furlenco page.
 */
public class FurlencoApprovalWorkflowSampleTest {

    private static final String FAILING_TEST_CLASS = "com.tests.web.furlenco.FurlencoCartFlowTest";
    private static final String FAILING_TEST_METHOD = "verifyCartDrawerOpenAndClose";
    private static final String STALE_LOCATOR = "button[aria-label='Cart']";
    private static final String VERIFIED_REPLACEMENT_LOCATOR = "header button[data-testid='cart-icon-button']";

    // ------------------------------------------------------------------------------------------
    // Step 1 — a QA engineer's very first move after the failure: ask FailureDiagnosisHelper.
    // ------------------------------------------------------------------------------------------

    @Test
    public void step1_diagnoseTheFailureWithFailureDiagnosisHelper() {
        ITestResult failingCartTest = buildFailingFurlencoCartTestResult();

        FailureDiagnosisHelper helper = new FailureDiagnosisHelper();
        FailureDiagnosis diagnosis = helper.diagnose(failingCartTest);

        // Real behavior, AI disabled (this environment's actual default): FailureDiagnosisHelper
        // never fabricates a root cause or a locator suggestion it can't back up. The diagnosis is
        // honest and safe, not empty-because-broken.
        assertThat(diagnosis).isNotNull();
        assertThat(diagnosis.getAiAnalysis())
                .describedAs("With ai.enabled=false, Phase 2 correctly performs no analysis rather than guessing")
                .isNull();

        // helper.report(diagnosis) would attach this to Allure here in a real triage run; omitted
        // in this sample so it doesn't require a live Allure test context to execute.
    }

    // ------------------------------------------------------------------------------------------
    // Steps 2-5 — once a diagnosis carries real, DOM-verified locator evidence (exactly what
    // ai.enabled=true + a completed Locator Advisor pass would have produced for this same
    // failure), this is the full explicit chain a QA engineer runs to reach a recorded decision.
    // ------------------------------------------------------------------------------------------

    @Test
    public void step2to5_consumeApproveStoreAndSummarizeARealisticRecommendation() {
        FailureDiagnosis diagnosisWithLocatorEvidence = buildDiagnosisAsIfAiAndLocatorAdvisorHadRun();

        // ---- Step 2: AgentRecommendationConsumer -----------------------------------------------
        // The real, unmodified AgentOrchestrationService/AgentRecommendationConsumer. The only
        // stand-in is the reasoning service itself (see StubAiProposingLocatorFix below) — a
        // stand-in for what ai.enabled=true would have returned; AgentExecutionGuard and
        // SelfHealingRecommendationService below it are the real, unmodified Phase 8 classes.
        AgentOrchestrationService orchestrationService = new AgentOrchestrationService(
                new StubAiProposingLocatorFix(), new AgentExecutionGuard(), new SelfHealingRecommendationService());
        AgentRecommendationConsumer consumer = new AgentRecommendationConsumer(orchestrationService);

        List<SelfHealingRecommendation> recommendations = consumer.consume(diagnosisWithLocatorEvidence);

        assertThat(recommendations).hasSize(1);
        SelfHealingRecommendation recommendation = recommendations.get(0);
        assertThat(recommendation.getCurrentLocator()).isEqualTo(STALE_LOCATOR);
        assertThat(recommendation.getProposedLocator()).isEqualTo(VERIFIED_REPLACEMENT_LOCATOR);
        assertThat(recommendation.isApprovalRequired())
                .describedAs("A recommendation is always a human-review artifact, never pre-approved")
                .isTrue();

        // ---- Step 3: AgentApprovalService — a human (here, a QA engineer named in the sample
        // reviewer field) explicitly decides, after checking the replacement selector on staging. --
        AgentApprovalService approvalService = new AgentApprovalService();
        AgentApprovalRecord decision = approvalService.approve(recommendation,
                "Checked header button[data-testid='cart-icon-button'] on staging after the Q3 header "
                        + "redesign — resolves to exactly one visible, enabled element. Safe to adopt in "
                        + "FurlencoHomePage.CART_BUTTON.",
                "qa.jane");

        assertThat(decision.getStatus()).isEqualTo(AgentApprovalStatus.APPROVED);
        assertThat(decision.getRecommendation()).isSameAs(recommendation);

        // ---- Step 4: AgentApprovalRecordStore — record the decision for the team to see. --------
        AgentApprovalRecordStore store = new AgentApprovalRecordStore();
        store.add(decision);

        assertThat(store.findByRecommendationId(recommendation.getRecommendationId())).contains(decision);

        // ---- Step 5: AgentApprovalSummaryReporter — a plain-text status a human can read at a
        // glance (a standup, a PR description, a triage channel message). -------------------------
        AgentApprovalSummaryReporter summaryReporter = new AgentApprovalSummaryReporter(store);
        String summary = summaryReporter.summarize();

        assertThat(summary).contains("Total: 1").contains("Approved: 1").contains("Pending: 0").contains("Rejected: 0");

        // STOP. Nothing below this line exists in the framework: there is no step 6. Approving this
        // record is a decision for a human to act on themselves (e.g. editing
        // FurlencoHomePage.CART_BUTTON by hand) — this workflow never edits source, never touches a
        // real Page, and never applies the recommendation on anyone's behalf.
    }

    // ------------------------------------------------------------------------------------------
    // Fixture: a diagnosis shaped exactly like what ai.enabled=true would have produced for this
    // failure — an AI-classified category plus a Locator Advisor pass that already DOM-verified a
    // replacement selector. Built directly (no live AI/Playwright call), matching the same fixture
    // convention already used by SelfHealingRecommendationServiceTest.
    // ------------------------------------------------------------------------------------------

    private FailureDiagnosis buildDiagnosisAsIfAiAndLocatorAdvisorHadRun() {
        FailureContext failureContext = FailureContext.builder()
                .testName(FAILING_TEST_METHOD)
                .testClass(FAILING_TEST_CLASS)
                .errorMessage("Timeout 30000ms exceeded waiting for locator('" + STALE_LOCATOR + "')")
                .currentUrl("https://www.furlenco.com/")
                .build();

        AiAnalysisResponse aiAnalysis = AiAnalysisResponse.builder()
                .category(FailureCategory.LOCATOR_CHANGED)
                .summary("The header cart button locator no longer matches any element after a header redesign.")
                .addSuggestedLocator(STALE_LOCATOR)
                .confidenceScore(0.72)
                .build();

        LocatorCandidate verifiedCandidate = LocatorCandidate.builder()
                .locator(VERIFIED_REPLACEMENT_LOCATOR)
                .evidenceStatus(EvidenceStatus.VERIFIED)
                .validationType(ValidationType.RUNTIME_VALIDATED)
                .score(9)
                .confidence(0.93)
                .build();

        LocatorAnalysisResponse locatorAnalysis = LocatorAnalysisResponse.builder()
                .success(true)
                .addCandidate(verifiedCandidate)
                .build();

        return FailureDiagnosis.builder()
                .failureContext(failureContext)
                .aiAnalysis(aiAnalysis)
                .locatorAnalysis(locatorAnalysis)
                .build();
    }

    /**
     * Stands in for a live AI response under {@code ai.enabled=true}, proposing exactly the
     * locator fix the diagnosis's own DOM-verified evidence already supports. Everything this
     * class returns is still independently re-evaluated by the real, unmodified
     * {@link AgentExecutionGuard} and {@link SelfHealingRecommendationService} — this stub cannot
     * grant itself anything.
     */
    private static class StubAiProposingLocatorFix extends AgentReasoningService {
        @Override
        public AgentDecision analyze(AgentContext context) {
            return AgentDecision.builder()
                    .state(AgentState.PROPOSE)
                    .action(AgentAction.LOCATOR_RECOMMENDATION)
                    .rationale("Static DOM evidence already identifies a single, verified replacement candidate.")
                    .confidence(0.8)
                    .requiresApproval(true)
                    .build();
        }
    }

    // ------------------------------------------------------------------------------------------
    // Fixture: a minimal, JDK-Proxy-backed ITestResult double representing the failing Furlenco
    // test, following the same no-Mockito convention already established by
    // com.tests.ai.diagnosis.FailureDiagnosisTestResultTestDouble. Only the handful of methods
    // FailureDiagnosisHelper actually reads are implemented.
    // ------------------------------------------------------------------------------------------

    private static ITestResult buildFailingFurlencoCartTestResult() {
        Throwable timeout = new RuntimeException(
                "Timeout 30000ms exceeded waiting for locator('" + STALE_LOCATOR + "')");

        ITestNGMethod method = (ITestNGMethod) Proxy.newProxyInstance(
                ITestNGMethod.class.getClassLoader(), new Class<?>[] {ITestNGMethod.class},
                (proxy, m, args) -> "getMethodName".equals(m.getName()) ? FAILING_TEST_METHOD : unsupported(m));

        IClass testClass = (IClass) Proxy.newProxyInstance(
                IClass.class.getClassLoader(), new Class<?>[] {IClass.class},
                (proxy, m, args) -> "getName".equals(m.getName()) ? FAILING_TEST_CLASS : unsupported(m));

        InvocationHandler handler = (proxy, m, args) -> {
            switch (m.getName()) {
                case "getMethod": return method;
                case "getTestClass": return testClass;
                case "getThrowable": return timeout;
                case "getStartMillis": return 0L;
                case "getEndMillis": return 30_000L;
                default: return unsupported(m);
            }
        };
        return (ITestResult) Proxy.newProxyInstance(
                ITestResult.class.getClassLoader(), new Class<?>[] {ITestResult.class}, handler);
    }

    private static Object unsupported(java.lang.reflect.Method m) {
        throw new UnsupportedOperationException("Sample test double does not support " + m.getName() + "()");
    }
}
