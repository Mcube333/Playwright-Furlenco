package com.framework.ai.testgeneration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable domain model representing a single AI-generated QA test case.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class GeneratedTestCase {

    private final String testCaseId;
    private final String module;
    private final String scenario;
    private final String description;
    private final List<String> preconditions;
    private final List<TestDataSuggestion> testData;
    private final List<String> steps;
    private final String expectedResult;
    private final TestCasePriority priority;
    private final TestCaseType testType;
    private final List<String> tags;
    private final String requirementReference;

    @JsonCreator
    public GeneratedTestCase(
            @JsonProperty("testCaseId") String testCaseId,
            @JsonProperty("module") String module,
            @JsonProperty("scenario") String scenario,
            @JsonProperty("description") String description,
            @JsonProperty("preconditions") List<String> preconditions,
            @JsonProperty("testData") List<TestDataSuggestion> testData,
            @JsonProperty("steps") List<String> steps,
            @JsonProperty("expectedResult") String expectedResult,
            @JsonProperty("priority") Object priority,
            @JsonProperty("testType") Object testType,
            @JsonProperty("tags") List<String> tags,
            @JsonProperty("requirementReference") String requirementReference) {
        this.testCaseId = testCaseId != null ? testCaseId.trim() : "";
        this.module = module != null ? module.trim() : "";
        this.scenario = scenario != null ? scenario.trim() : "";
        this.description = description != null ? description.trim() : "";
        this.preconditions = preconditions != null
                ? Collections.unmodifiableList(new ArrayList<>(preconditions))
                : Collections.emptyList();
        this.testData = testData != null
                ? Collections.unmodifiableList(new ArrayList<>(testData))
                : Collections.emptyList();
        this.steps = steps != null
                ? Collections.unmodifiableList(new ArrayList<>(steps))
                : Collections.emptyList();
        this.expectedResult = expectedResult != null ? expectedResult.trim() : "";
        this.priority = priority instanceof TestCasePriority
                ? (TestCasePriority) priority
                : TestCasePriority.fromString(String.valueOf(priority));
        this.testType = testType instanceof TestCaseType
                ? (TestCaseType) testType
                : TestCaseType.fromString(String.valueOf(testType));
        this.tags = tags != null
                ? Collections.unmodifiableList(new ArrayList<>(tags))
                : Collections.emptyList();
        this.requirementReference = requirementReference != null ? requirementReference.trim() : "";
    }

    public String getTestCaseId() {
        return testCaseId;
    }

    public String getModule() {
        return module;
    }

    public String getScenario() {
        return scenario;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getPreconditions() {
        return preconditions;
    }

    public List<TestDataSuggestion> getTestData() {
        return testData;
    }

    public List<String> getSteps() {
        return steps;
    }

    public String getExpectedResult() {
        return expectedResult;
    }

    public TestCasePriority getPriority() {
        return priority;
    }

    public TestCaseType getTestType() {
        return testType;
    }

    public List<String> getTags() {
        return tags;
    }

    public String getRequirementReference() {
        return requirementReference;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String testCaseId;
        private String module;
        private String scenario;
        private String description;
        private List<String> preconditions = new ArrayList<>();
        private List<TestDataSuggestion> testData = new ArrayList<>();
        private List<String> steps = new ArrayList<>();
        private String expectedResult;
        private TestCasePriority priority = TestCasePriority.P1;
        private TestCaseType testType = TestCaseType.FUNCTIONAL;
        private List<String> tags = new ArrayList<>();
        private String requirementReference;

        public Builder testCaseId(String testCaseId) {
            this.testCaseId = testCaseId;
            return this;
        }

        public Builder module(String module) {
            this.module = module;
            return this;
        }

        public Builder scenario(String scenario) {
            this.scenario = scenario;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder preconditions(List<String> preconditions) {
            if (preconditions != null) {
                this.preconditions = new ArrayList<>(preconditions);
            }
            return this;
        }

        public Builder addPrecondition(String precondition) {
            this.preconditions.add(precondition);
            return this;
        }

        public Builder testData(List<TestDataSuggestion> testData) {
            if (testData != null) {
                this.testData = new ArrayList<>(testData);
            }
            return this;
        }

        public Builder addTestData(TestDataSuggestion data) {
            this.testData.add(data);
            return this;
        }

        public Builder steps(List<String> steps) {
            if (steps != null) {
                this.steps = new ArrayList<>(steps);
            }
            return this;
        }

        public Builder addStep(String step) {
            this.steps.add(step);
            return this;
        }

        public Builder expectedResult(String expectedResult) {
            this.expectedResult = expectedResult;
            return this;
        }

        public Builder priority(TestCasePriority priority) {
            this.priority = priority;
            return this;
        }

        public Builder testType(TestCaseType testType) {
            this.testType = testType;
            return this;
        }

        public Builder tags(List<String> tags) {
            if (tags != null) {
                this.tags = new ArrayList<>(tags);
            }
            return this;
        }

        public Builder addTag(String tag) {
            this.tags.add(tag);
            return this;
        }

        public Builder requirementReference(String requirementReference) {
            this.requirementReference = requirementReference;
            return this;
        }

        public GeneratedTestCase build() {
            return new GeneratedTestCase(
                    testCaseId, module, scenario, description,
                    preconditions, testData, steps, expectedResult,
                    priority, testType, tags, requirementReference);
        }
    }
}