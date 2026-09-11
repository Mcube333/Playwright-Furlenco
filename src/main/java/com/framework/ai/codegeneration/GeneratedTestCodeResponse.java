package com.framework.ai.codegeneration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Structured LLM response for Java test code generation,
 * hardened with evidence-tracking and hallucination classification.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class GeneratedTestCodeResponse {

    private final boolean success;
    private final String errorMessage;
    private final String testClassName;
    private final String packageName;
    private final String testClassCode;
    private final List<String> pageObjectMethods;
    private final List<String> referencedFrameworkClasses;
    private final List<String> locatorsUsed;
    private final List<String> testDataUsed;
    private final List<String> warnings;
    private final List<String> assumptions;
    private final List<String> analyticsSuggestions;
    private final List<EvidenceItem> evidenceItems;
    private final boolean reviewRequired;

    @JsonCreator
    public GeneratedTestCodeResponse(
            @JsonProperty("success") Boolean success,
            @JsonProperty("errorMessage") String errorMessage,
            @JsonProperty("testClassName") String testClassName,
            @JsonProperty("packageName") String packageName,
            @JsonProperty("testClassCode") String testClassCode,
            @JsonProperty("pageObjectMethods") List<String> pageObjectMethods,
            @JsonProperty("referencedFrameworkClasses") List<String> referencedFrameworkClasses,
            @JsonProperty("locatorsUsed") List<String> locatorsUsed,
            @JsonProperty("testDataUsed") List<String> testDataUsed,
            @JsonProperty("warnings") List<String> warnings,
            @JsonProperty("assumptions") List<String> assumptions,
            @JsonProperty("analyticsSuggestions") List<String> analyticsSuggestions,
            @JsonProperty("evidenceItems") List<EvidenceItem> evidenceItems,
            @JsonProperty("reviewRequired") Boolean reviewRequired) {
        this.success = success != null ? success : true;
        this.errorMessage = errorMessage != null ? errorMessage : "";
        this.testClassName = testClassName != null ? testClassName.trim() : "";
        this.packageName = packageName != null ? packageName.trim() : "com.tests.web";
        this.testClassCode = testClassCode != null ? testClassCode : "";
        this.pageObjectMethods = pageObjectMethods != null ? Collections.unmodifiableList(new ArrayList<>(pageObjectMethods)) : Collections.emptyList();
        this.referencedFrameworkClasses = referencedFrameworkClasses != null ? Collections.unmodifiableList(new ArrayList<>(referencedFrameworkClasses)) : Collections.emptyList();
        this.locatorsUsed = locatorsUsed != null ? Collections.unmodifiableList(new ArrayList<>(locatorsUsed)) : Collections.emptyList();
        this.testDataUsed = testDataUsed != null ? Collections.unmodifiableList(new ArrayList<>(testDataUsed)) : Collections.emptyList();
        this.warnings = warnings != null ? Collections.unmodifiableList(new ArrayList<>(warnings)) : Collections.emptyList();
        this.assumptions = assumptions != null ? Collections.unmodifiableList(new ArrayList<>(assumptions)) : Collections.emptyList();
        this.analyticsSuggestions = analyticsSuggestions != null ? Collections.unmodifiableList(new ArrayList<>(analyticsSuggestions)) : Collections.emptyList();
        this.evidenceItems = evidenceItems != null ? Collections.unmodifiableList(new ArrayList<>(evidenceItems)) : Collections.emptyList();
        this.reviewRequired = reviewRequired != null ? reviewRequired : true;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getTestClassName() {
        return testClassName;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getTestClassCode() {
        return testClassCode;
    }

    public List<String> getPageObjectMethods() {
        return pageObjectMethods;
    }

    public List<String> getReferencedFrameworkClasses() {
        return referencedFrameworkClasses;
    }

    public List<String> getLocatorsUsed() {
        return locatorsUsed;
    }

    public List<String> getTestDataUsed() {
        return testDataUsed;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public List<String> getAssumptions() {
        return assumptions;
    }

    public List<String> getAnalyticsSuggestions() {
        return analyticsSuggestions;
    }

    public List<EvidenceItem> getEvidenceItems() {
        return evidenceItems;
    }

    public boolean isReviewRequired() {
        return reviewRequired;
    }

    public static GeneratedTestCodeResponse failure(String errorMessage) {
        return builder().success(false).errorMessage(errorMessage).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private boolean success = true;
        private String errorMessage = "";
        private String testClassName = "";
        private String packageName = "com.tests.web";
        private String testClassCode = "";
        private List<String> pageObjectMethods = new ArrayList<>();
        private List<String> referencedFrameworkClasses = new ArrayList<>();
        private List<String> locatorsUsed = new ArrayList<>();
        private List<String> testDataUsed = new ArrayList<>();
        private List<String> warnings = new ArrayList<>();
        private List<String> assumptions = new ArrayList<>();
        private List<String> analyticsSuggestions = new ArrayList<>();
        private List<EvidenceItem> evidenceItems = new ArrayList<>();
        private boolean reviewRequired = true;

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder testClassName(String testClassName) {
            this.testClassName = testClassName;
            return this;
        }

        public Builder packageName(String packageName) {
            this.packageName = packageName;
            return this;
        }

        public Builder testClassCode(String testClassCode) {
            this.testClassCode = testClassCode;
            return this;
        }

        public Builder pageObjectMethods(List<String> methods) {
            if (methods != null) this.pageObjectMethods = new ArrayList<>(methods);
            return this;
        }

        public Builder addPageObjectMethod(String method) {
            this.pageObjectMethods.add(method);
            return this;
        }

        public Builder referencedFrameworkClasses(List<String> classes) {
            if (classes != null) this.referencedFrameworkClasses = new ArrayList<>(classes);
            return this;
        }

        public Builder addReferencedFrameworkClass(String clazz) {
            this.referencedFrameworkClasses.add(clazz);
            return this;
        }

        public Builder locatorsUsed(List<String> locators) {
            if (locators != null) this.locatorsUsed = new ArrayList<>(locators);
            return this;
        }

        public Builder addLocator(String locator) {
            this.locatorsUsed.add(locator);
            return this;
        }

        public Builder testDataUsed(List<String> testData) {
            if (testData != null) this.testDataUsed = new ArrayList<>(testData);
            return this;
        }

        public Builder addTestData(String data) {
            this.testDataUsed.add(data);
            return this;
        }

        public Builder warnings(List<String> warnings) {
            if (warnings != null) this.warnings = new ArrayList<>(warnings);
            return this;
        }

        public Builder addWarning(String warning) {
            this.warnings.add(warning);
            return this;
        }

        public Builder assumptions(List<String> assumptions) {
            if (assumptions != null) this.assumptions = new ArrayList<>(assumptions);
            return this;
        }

        public Builder addAssumption(String assumption) {
            this.assumptions.add(assumption);
            return this;
        }

        public Builder analyticsSuggestions(List<String> analytics) {
            if (analytics != null) this.analyticsSuggestions = new ArrayList<>(analytics);
            return this;
        }

        public Builder addAnalyticsSuggestion(String suggestion) {
            this.analyticsSuggestions.add(suggestion);
            return this;
        }

        public Builder evidenceItems(List<EvidenceItem> items) {
            if (items != null) this.evidenceItems = new ArrayList<>(items);
            return this;
        }

        public Builder addEvidenceItem(EvidenceItem item) {
            this.evidenceItems.add(item);
            return this;
        }

        public Builder reviewRequired(boolean reviewRequired) {
            this.reviewRequired = reviewRequired;
            return this;
        }

        public GeneratedTestCodeResponse build() {
            return new GeneratedTestCodeResponse(
                    success, errorMessage, testClassName, packageName,
                    testClassCode, pageObjectMethods, referencedFrameworkClasses,
                    locatorsUsed, testDataUsed, warnings, assumptions,
                    analyticsSuggestions, evidenceItems, reviewRequired
            );
        }
    }
}