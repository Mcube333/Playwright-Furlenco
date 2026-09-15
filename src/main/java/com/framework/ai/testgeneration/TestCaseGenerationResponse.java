package com.framework.ai.testgeneration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Encapsulates the complete response of the test case generation workflow.
 * Includes parsed test cases, missing requirement notices, validation warnings, and metadata.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class TestCaseGenerationResponse {

    private final boolean success;
    private final String errorMessage;
    private final String requirementId;
    private final String module;
    private final List<GeneratedTestCase> testCases;
    private final List<String> missingRequirements;
    private final List<String> assumptions;
    private final Map<String, Object> metadata;

    @JsonCreator
    public TestCaseGenerationResponse(
            @JsonProperty("success") Boolean success,
            @JsonProperty("errorMessage") String errorMessage,
            @JsonProperty("requirementId") String requirementId,
            @JsonProperty("module") String module,
            @JsonProperty("testCases") List<GeneratedTestCase> testCases,
            @JsonProperty("missingRequirements") List<String> missingRequirements,
            @JsonProperty("assumptions") List<String> assumptions,
            @JsonProperty("metadata") Map<String, Object> metadata) {
        this.success = success != null ? success : true;
        this.errorMessage = errorMessage != null ? errorMessage : "";
        this.requirementId = requirementId != null ? requirementId : "";
        this.module = module != null ? module : "";
        this.testCases = testCases != null
                ? Collections.unmodifiableList(new ArrayList<>(testCases))
                : Collections.emptyList();
        this.missingRequirements = missingRequirements != null
                ? Collections.unmodifiableList(new ArrayList<>(missingRequirements))
                : Collections.emptyList();
        this.assumptions = assumptions != null
                ? Collections.unmodifiableList(new ArrayList<>(assumptions))
                : Collections.emptyList();
        this.metadata = metadata != null
                ? Collections.unmodifiableMap(new HashMap<>(metadata))
                : Collections.emptyMap();
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getRequirementId() {
        return requirementId;
    }

    public String getModule() {
        return module;
    }

    public List<GeneratedTestCase> getTestCases() {
        return testCases;
    }

    public List<String> getMissingRequirements() {
        return missingRequirements;
    }

    public List<String> getAssumptions() {
        return assumptions;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
     * Performs strict validation and deduplication on test cases.
     * Returns a new validated response with deduplicated test case IDs.
     */
    public TestCaseGenerationResponse validateAndDeduplicate() {
        if (!success || testCases.isEmpty()) {
            return this;
        }

        List<GeneratedTestCase> validatedList = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        Set<String> seenScenarios = new HashSet<>();
        List<String> warnings = new ArrayList<>();

        int autoIdCounter = 1;
        for (GeneratedTestCase tc : testCases) {
            String id = tc.getTestCaseId();
            if (id == null || id.isBlank() || seenIds.contains(id)) {
                String newId = "AI-TC-" + String.format("%03d", autoIdCounter++);
                warnings.add("Replaced missing or duplicate ID [" + id + "] with [" + newId + "]");
                id = newId;
            }
            seenIds.add(id);

            String scenarioKey = (tc.getModule() + "::" + tc.getScenario()).toLowerCase();
            if (seenScenarios.contains(scenarioKey)) {
                warnings.add("Skipped duplicate scenario: " + tc.getScenario());
                continue; // Deduplicate
            }
            seenScenarios.add(scenarioKey);

            GeneratedTestCase sanitizedTc = GeneratedTestCase.builder()
                    .testCaseId(id)
                    .module(tc.getModule().isBlank() ? this.module : tc.getModule())
                    .scenario(tc.getScenario())
                    .description(tc.getDescription())
                    .preconditions(tc.getPreconditions())
                    .testData(tc.getTestData())
                    .steps(tc.getSteps())
                    .expectedResult(tc.getExpectedResult())
                    .priority(tc.getPriority())
                    .testType(tc.getTestType())
                    .tags(tc.getTags())
                    .requirementReference(tc.getRequirementReference().isBlank() ? this.requirementId : tc.getRequirementReference())
                    .build();

            validatedList.add(sanitizedTc);
        }

        Map<String, Object> updatedMeta = new HashMap<>(this.metadata);
        if (!warnings.isEmpty()) {
            updatedMeta.put("validationWarnings", warnings);
        }

        return builder()
                .success(this.success)
                .errorMessage(this.errorMessage)
                .requirementId(this.requirementId)
                .module(this.module)
                .testCases(validatedList)
                .missingRequirements(this.missingRequirements)
                .assumptions(this.assumptions)
                .metadata(updatedMeta)
                .build();
    }

    public static TestCaseGenerationResponse failure(String errorMessage) {
        return builder()
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private boolean success = true;
        private String errorMessage = "";
        private String requirementId = "";
        private String module = "";
        private List<GeneratedTestCase> testCases = new ArrayList<>();
        private List<String> missingRequirements = new ArrayList<>();
        private List<String> assumptions = new ArrayList<>();
        private Map<String, Object> metadata = new HashMap<>();

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder requirementId(String requirementId) {
            this.requirementId = requirementId;
            return this;
        }

        public Builder module(String module) {
            this.module = module;
            return this;
        }

        public Builder testCases(List<GeneratedTestCase> testCases) {
            if (testCases != null) {
                this.testCases = new ArrayList<>(testCases);
            }
            return this;
        }

        public Builder addTestCase(GeneratedTestCase testCase) {
            this.testCases.add(testCase);
            return this;
        }

        public Builder missingRequirements(List<String> missingRequirements) {
            if (missingRequirements != null) {
                this.missingRequirements = new ArrayList<>(missingRequirements);
            }
            return this;
        }

        public Builder addMissingRequirement(String missing) {
            this.missingRequirements.add(missing);
            return this;
        }

        public Builder assumptions(List<String> assumptions) {
            if (assumptions != null) {
                this.assumptions = new ArrayList<>(assumptions);
            }
            return this;
        }

        public Builder addAssumption(String assumption) {
            this.assumptions.add(assumption);
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            if (metadata != null) {
                this.metadata = new HashMap<>(metadata);
            }
            return this;
        }

        public Builder addMetadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        public TestCaseGenerationResponse build() {
            return new TestCaseGenerationResponse(
                    success, errorMessage, requirementId, module,
                    testCases, missingRequirements, assumptions, metadata);
        }
    }
}