package com.framework.ai.testgeneration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * Encapsulates suggested test data for a generated test case.
 * Separated strictly from executable data fixtures.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class TestDataSuggestion {

    private final String field;
    private final String suggestedValue;
    private final String description;

    @JsonCreator
    public TestDataSuggestion(
            @JsonProperty("field") String field,
            @JsonProperty("suggestedValue") String suggestedValue,
            @JsonProperty("description") String description) {
        this.field = field != null ? field : "";
        this.suggestedValue = suggestedValue != null ? suggestedValue : "";
        this.description = description != null ? description : "";
    }

    public String getField() {
        return field;
    }

    public String getSuggestedValue() {
        return suggestedValue;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TestDataSuggestion that = (TestDataSuggestion) o;
        return Objects.equals(field, that.field) &&
                Objects.equals(suggestedValue, that.suggestedValue) &&
                Objects.equals(description, that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(field, suggestedValue, description);
    }

    @Override
    public String toString() {
        return "TestDataSuggestion{" +
                "field='" + field + '\'' +
                ", suggestedValue='" + suggestedValue + '\'' +
                ", description='" + description + '\'' +
                '}';
    }
}