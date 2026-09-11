package com.tests.ai.codegeneration;

import static org.assertj.core.api.Assertions.assertThat;

import com.framework.ai.client.AiClient;
import com.framework.ai.codegeneration.CodeValidator;
import com.framework.ai.codegeneration.EvidenceItem;
import com.framework.ai.codegeneration.EvidenceStatus;
import com.framework.ai.codegeneration.GeneratedCodeReporter;
import com.framework.ai.codegeneration.GeneratedTestCodeResponse;
import com.framework.ai.codegeneration.PlaywrightCodeGenerator;
import com.framework.ai.codegeneration.TestCodeGenerationPrompt;
import com.framework.ai.codegeneration.ValidationResult;
import com.framework.ai.config.AiConfig;
import com.framework.ai.model.AiRequest;
import com.framework.ai.model.AiResponse;
import com.framework.ai.testgeneration.GeneratedTestCase;
import com.framework.ai.testgeneration.TestCasePriority;
import com.framework.ai.testgeneration.TestCaseType;
import com.framework.ai.testgeneration.TestDataSuggestion;
import com.framework.config.ConfigManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.testng.annotations.Test;

public class PlaywrightCodeGenerationTest {

    private AiConfig createConfig(boolean enabled) {
        return new AiConfig(ConfigManager.getInstance()) {
            @Override public boolean isAiEnabled() { return enabled; }
            @Override public String getApiKey() { return "fake-api-key"; }
            @Override public String getProvider() { return "gemini"; }
        };
    }

    private static class MockAiClient implements AiClient {
        private final AiResponse stubResponse;
        private final boolean available;
        public String capturedPrompt;

        public MockAiClient(AiResponse stubResponse, boolean available) {
            this.stubResponse = stubResponse;
            this.available = available;
        }

        @Override
        public AiResponse generate(AiRequest request) {
            this.capturedPrompt = request.getPrompt();
            return stubResponse;
        }

        @Override public String getProviderName() { return "mock"; }
        @Override public boolean isAvailable() { return available; }
    }

    private GeneratedTestCase sampleTestCase() {
        return GeneratedTestCase.builder()
                .testCaseId("TC-CART-01")
                .module("Cart")
                .scenario("Increase cart item quantity")
                .description("Verify cart item count increases on plus click")
                .priority(TestCasePriority.P0)
                .testType(TestCaseType.FUNCTIONAL)
                .addPrecondition("User has at least 1 item in cart")
                .addStep("Navigate to cart")
                .addStep("Click '+' icon")
                .addStep("Verify item count updates to 2")
                .expectedResult("Item count is 2 and total amount recalculates")
                .addTestData(new TestDataSuggestion("quantity", "2", "Incremented count"))
                .build();
    }

    // 1. Prompt construction test
    @Test
    public void testPromptConstruction() {
        String prompt = TestCodeGenerationPrompt.buildPrompt(sampleTestCase());
        assertThat(prompt).contains("TC-CART-01");
        assertThat(prompt).contains("Increase cart item quantity");
        assertThat(prompt).contains("User has at least 1 item in cart");
        assertThat(prompt).contains("Item count is 2 and total amount recalculates");
        assertThat(TestCodeGenerationPrompt.SYSTEM_INSTRUCTION).contains("BaseWebTest");
        assertThat(TestCodeGenerationPrompt.SYSTEM_INSTRUCTION).contains("NEVER use Thread.sleep()");
        assertThat(TestCodeGenerationPrompt.SYSTEM_INSTRUCTION).contains("CRITICAL EVIDENCE & ANTI-HALLUCINATION RULES");
    }

    // 2. Successful JSON parsing and validation
    @Test
    public void testSuccessfulCodeGenerationAndValidation() {
        String jsonPayload = "{\n"
                + "  \"testClassName\": \"FurlencoCartDraftTest\",\n"
                + "  \"packageName\": \"com.tests.web.furlenco.draft\",\n"
                + "  \"testClassCode\": \"package com.tests.web.furlenco.draft;\\n\\npublic class FurlencoCartDraftTest extends BaseWebTest {\\n    public void testIncreaseQuantity() {\\n        assertThat(true).isTrue();\\n    }\\n}\",\n"
                + "  \"pageObjectSuggestions\": [\"public void clickPlusIcon() { page.locator(\\\"button.plus\\\").click(); }\"],\n"
                + "  \"referencedFrameworkClasses\": [\"BaseWebTest\", \"FurlencoHomePage\"],\n"
                + "  \"locatorsUsed\": [\"button.plus\"],\n"
                + "  \"testDataUsed\": [\"quantity=2\"],\n"
                + "  \"warnings\": [\"Check locator on staging\"],\n"
                + "  \"assumptions\": [\"User is logged in\"],\n"
                + "  \"analyticsSuggestions\": [\"ANALYTICS VALIDATION DETAILS REQUIRED: Confirm expected event\"],\n"
                + "  \"evidenceItems\": [\n"
                + "    {\"item\": \"Quantity plus button\", \"value\": \"button.plus\", \"status\": \"UNVERIFIED\", \"source\": \"AI inference\", \"confidence\": 0.35},\n"
                + "    {\"item\": \"Analytics event\", \"value\": \"None\", \"status\": \"MISSING\", \"source\": \"No requirement evidence\", \"confidence\": 0.0}\n"
                + "  ]\n"
                + "}";

        MockAiClient client = new MockAiClient(AiResponse.success(jsonPayload, "mock"), true);
        PlaywrightCodeGenerator generator = new PlaywrightCodeGenerator(createConfig(true), client);

        GeneratedTestCodeResponse response = generator.generateDraftCode(sampleTestCase(), true);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getTestClassName()).isEqualTo("FurlencoCartDraftTest");
        assertThat(response.getTestClassCode()).contains("// AI-GENERATED DRAFT");
        assertThat(response.getTestClassCode()).contains("class FurlencoCartDraftTest");
        assertThat(response.getPageObjectMethods()).hasSize(1);
        assertThat(response.getLocatorsUsed()).contains("button.plus");
        assertThat(response.getEvidenceItems()).hasSize(2);

        // Verify files were exported strictly to target/ai-generated/
        Path reportPath = Paths.get("target", "ai-generated", "generation-report.md");
        assertThat(Files.exists(reportPath)).isTrue();
        Path javaPath = Paths.get("target", "ai-generated", "FurlencoCartDraftTest.java");
        assertThat(Files.exists(javaPath)).isTrue();
    }

    // 3. Thread.sleep detection
    @Test
    public void testThreadSleepDetection() {
        String codeWithSleep = "public class BadTest { public void test() throws Exception { Thread.sleep(5000); } }";
        ValidationResult result = CodeValidator.validateCode(codeWithSleep, "BadTest");
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("Thread.sleep"));
    }

    // 4. Secret detection
    @Test
    public void testSecretDetectionInGeneratedCode() {
        String codeWithSecret = "public class SecretTest { String token = \"eyJhbGciOiJIUzI1NiJ9\"; String password = \"HardcodedPass123!\"; }";
        ValidationResult result = CodeValidator.validateCode(codeWithSecret, "SecretTest");
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("credentials/secrets"));
    }

    // 5. Unsafe selector warning
    @Test
    public void testUnsafeSelectorDetection() {
        String codeWithXpath = "public class XpathTest { public void test() { page.locator(\"xpath=/html/body/div[1]\").click(); } }";
        ValidationResult result = CodeValidator.validateCode(codeWithXpath, "XpathTest");
        assertThat(result.getWarnings()).anyMatch(w -> w.contains("Unsafe absolute XPath"));
    }

    // 6. Duplicate method name detection
    @Test
    public void testDuplicateMethodNameDetection() {
        String codeWithDuplicates = "public class DupTest {\n"
                + "    public void verifyCart() {}\n"
                + "    public void verifyCart() {}\n"
                + "}";
        ValidationResult result = CodeValidator.validateCode(codeWithDuplicates, "DupTest");
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("Duplicate test method declared"));
    }

    // 7. Invalid Java class name
    @Test
    public void testInvalidJavaClassName() {
        assertThat(CodeValidator.isValidClassName("ValidClass123")).isTrue();
        assertThat(CodeValidator.isValidClassName("123Invalid")).isFalse();
        assertThat(CodeValidator.isValidClassName("invalid-class")).isFalse();
        assertThat(CodeValidator.isValidClassName("")).isFalse();
    }

    // 8. AI disabled returns safe failure
    @Test
    public void testAiDisabledReturnsSafeFailure() {
        PlaywrightCodeGenerator generator = new PlaywrightCodeGenerator(createConfig(false), new MockAiClient(null, false));
        GeneratedTestCodeResponse response = generator.generateDraftCode(sampleTestCase(), false);
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorMessage()).contains("AI is disabled");
    }

    // 9. Malformed response handling
    @Test
    public void testMalformedResponseHandling() {
        MockAiClient client = new MockAiClient(AiResponse.success("not json", "mock"), true);
        PlaywrightCodeGenerator generator = new PlaywrightCodeGenerator(createConfig(true), client);
        GeneratedTestCodeResponse response = generator.generateDraftCode(sampleTestCase(), false);
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorMessage()).contains("Failed to parse");
    }

    // 10. Human review report formatting with Evidence Classification
    @Test
    public void testHumanReviewReportFormattingWithEvidence() {
        GeneratedTestCodeResponse response = GeneratedTestCodeResponse.builder()
                .testClassName("FurlencoCheckoutTest")
                .packageName("com.tests.web.furlenco.draft")
                .addReferencedFrameworkClass("BaseWebTest")
                .addReferencedFrameworkClass("FurlencoCartDrawer")
                .addLocator("button[aria-label='Checkout']")
                .addTestData("coupon=DISCOUNT10")
                .addWarning("Payment gateway iframe not accessible in mock")
                .addAssumption("User has verified phone number")
                .addAnalyticsSuggestion("ANALYTICS VALIDATION DETAILS REQUIRED: Confirm expected event and attributes")
                .addEvidenceItem(EvidenceItem.builder()
                        .item("Checkout button")
                        .value("button[aria-label='Checkout']")
                        .status(EvidenceStatus.UNVERIFIED)
                        .source("AI inference")
                        .confidence(0.4)
                        .build())
                .addEvidenceItem(EvidenceItem.builder()
                        .item("Analytics event")
                        .value("None")
                        .status(EvidenceStatus.MISSING)
                        .source("No requirement evidence")
                        .confidence(0.0)
                        .build())
                .build();

        String md = GeneratedCodeReporter.buildMarkdownReport(response, "Cart Checkout Flow");
        assertThat(md).contains("# AI Test Code Generation & Human Review Report");
        assertThat(md).contains("## Evidence Classification");
        assertThat(md).contains("| Checkout button | `button[aria-label='Checkout']` | **UNVERIFIED** | AI inference | 40% |");
        assertThat(md).contains("| Analytics event | `None` | **MISSING** | No requirement evidence | 0% |");
        assertThat(md).contains("FurlencoCheckoutTest.java");
        assertThat(md).contains("FurlencoCartDrawer");
        assertThat(md).contains("DISCOUNT10");
        assertThat(md).contains("Human Review Checklist");
    }

    // 11. Phase 4.1 Hardening: Evidence status verification
    @Test
    public void testEvidenceStatusParsingAndSafety() {
        assertThat(EvidenceStatus.fromString("VERIFIED")).isEqualTo(EvidenceStatus.VERIFIED);
        assertThat(EvidenceStatus.fromString("INFERRED")).isEqualTo(EvidenceStatus.INFERRED);
        assertThat(EvidenceStatus.fromString("UNVERIFIED")).isEqualTo(EvidenceStatus.UNVERIFIED);
        assertThat(EvidenceStatus.fromString("MISSING")).isEqualTo(EvidenceStatus.MISSING);
        assertThat(EvidenceStatus.fromString("UNKNOWN_VALUE")).isEqualTo(EvidenceStatus.UNVERIFIED);
    }
}