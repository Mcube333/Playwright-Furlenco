package com.framework.ai.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Provider-agnostic response model from AI LLM interactions.
 */
public final class AiResponse {

    private final String content;
    private final String model;
    private final Integer promptTokens;
    private final Integer completionTokens;
    private final Integer totalTokens;
    private final boolean success;
    private final String errorMessage;
    private final Map<String, Object> metadata;

    private AiResponse(Builder builder) {
        this.content = builder.content != null ? builder.content : "";
        this.model = builder.model != null ? builder.model : "";
        this.promptTokens = builder.promptTokens;
        this.completionTokens = builder.completionTokens;
        this.totalTokens = builder.totalTokens;
        this.success = builder.success;
        this.errorMessage = builder.errorMessage != null ? builder.errorMessage : "";
        this.metadata = Collections.unmodifiableMap(new HashMap<>(builder.metadata));
    }

    public String getContent() {
        return content;
    }

    public String getModel() {
        return model;
    }

    public Integer getPromptTokens() {
        return promptTokens;
    }

    public Integer getCompletionTokens() {
        return completionTokens;
    }

    public Integer getTotalTokens() {
        return totalTokens;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static AiResponse success(String content, String model) {
        return builder()
                .content(content)
                .model(model)
                .success(true)
                .build();
    }

    public static AiResponse failure(String errorMessage) {
        return builder()
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }

    public static final class Builder {
        private String content;
        private String model;
        private Integer promptTokens;
        private Integer completionTokens;
        private Integer totalTokens;
        private boolean success = true;
        private String errorMessage;
        private final Map<String, Object> metadata = new HashMap<>();

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder promptTokens(Integer promptTokens) {
            this.promptTokens = promptTokens;
            return this;
        }

        public Builder completionTokens(Integer completionTokens) {
            this.completionTokens = completionTokens;
            return this;
        }

        public Builder totalTokens(Integer totalTokens) {
            this.totalTokens = totalTokens;
            return this;
        }

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            if (metadata != null) {
                this.metadata.putAll(metadata);
            }
            return this;
        }

        public Builder addMetadata(String key, Object value) {
            if (key != null && value != null) {
                this.metadata.put(key, value);
            }
            return this;
        }

        public AiResponse build() {
            return new AiResponse(this);
        }
    }

    @Override
    public String toString() {
        return "AiResponse{" +
                "success=" + success +
                ", model='" + model + '\'' +
                ", totalTokens=" + totalTokens +
                ", contentLength=" + content.length() +
                (errorMessage.isEmpty() ? "" : ", errorMessage='" + errorMessage + '\'') +
                '}';
    }
}
