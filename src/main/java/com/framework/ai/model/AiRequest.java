package com.framework.ai.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Provider-agnostic request model for AI LLM interactions.
 */
public final class AiRequest {

    private final String prompt;
    private final String systemInstruction;
    private final String model;
    private final Double temperature;
    private final Integer maxTokens;
    private final Map<String, Object> parameters;

    private AiRequest(Builder builder) {
        this.prompt = builder.prompt != null ? builder.prompt : "";
        this.systemInstruction = builder.systemInstruction != null ? builder.systemInstruction : "";
        this.model = builder.model != null ? builder.model : "";
        this.temperature = builder.temperature;
        this.maxTokens = builder.maxTokens;
        this.parameters = Collections.unmodifiableMap(new HashMap<>(builder.parameters));
    }

    public String getPrompt() {
        return prompt;
    }

    public String getSystemInstruction() {
        return systemInstruction;
    }

    public String getModel() {
        return model;
    }

    public Double getTemperature() {
        return temperature;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String prompt;
        private String systemInstruction;
        private String model;
        private Double temperature;
        private Integer maxTokens;
        private final Map<String, Object> parameters = new HashMap<>();

        public Builder prompt(String prompt) {
            this.prompt = prompt;
            return this;
        }

        public Builder systemInstruction(String systemInstruction) {
            this.systemInstruction = systemInstruction;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder parameter(String key, Object value) {
            if (key != null && value != null) {
                this.parameters.put(key, value);
            }
            return this;
        }

        public Builder parameters(Map<String, Object> parameters) {
            if (parameters != null) {
                this.parameters.putAll(parameters);
            }
            return this;
        }

        public AiRequest build() {
            if (prompt == null || prompt.isBlank()) {
                throw new IllegalArgumentException("AiRequest prompt must not be null or blank");
            }
            return new AiRequest(this);
        }
    }

    @Override
    public String toString() {
        return "AiRequest{" +
                "model='" + model + '\'' +
                ", temperature=" + temperature +
                ", maxTokens=" + maxTokens +
                ", promptLength=" + prompt.length() +
                '}';
    }
}
