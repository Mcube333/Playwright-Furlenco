package com.framework.ai.testgeneration;

import com.framework.ai.sanitizer.SensitiveDataSanitizer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Encapsulates input requirements (PRD text, user stories, acceptance criteria, or markdown).
 * Automatically applies deterministic sanitization.
 */
public final class RequirementInput {

    private final String requirementId;
    private final String title;
    private final String content;
    private final String module;
    private final Map<String, String> metadata;

    private RequirementInput(Builder builder) {
        this.requirementId = builder.requirementId != null ? builder.requirementId.trim() : "";
        this.title = builder.title != null ? builder.title.trim() : "";
        this.content = builder.content != null ? builder.content.trim() : "";
        this.module = builder.module != null ? builder.module.trim() : "";
        this.metadata = Collections.unmodifiableMap(new HashMap<>(builder.metadata));
    }

    public String getRequirementId() {
        return requirementId;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public String getModule() {
        return module;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    /**
     * Returns a copy of this RequirementInput with all sensitive data sanitized.
     */
    public RequirementInput sanitize() {
        return builder()
                .requirementId(this.requirementId)
                .title(SensitiveDataSanitizer.sanitize(this.title))
                .content(SensitiveDataSanitizer.sanitize(this.content))
                .module(SensitiveDataSanitizer.sanitize(this.module))
                .metadata(SensitiveDataSanitizer.sanitizeMap(this.metadata))
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static RequirementInput of(String content) {
        return builder().content(content).build();
    }

    public static RequirementInput of(String title, String content) {
        return builder().title(title).content(content).build();
    }

    public static final class Builder {
        private String requirementId;
        private String title;
        private String content;
        private String module;
        private Map<String, String> metadata = new HashMap<>();

        public Builder requirementId(String requirementId) {
            this.requirementId = requirementId;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder module(String module) {
            this.module = module;
            return this;
        }

        public Builder metadata(Map<String, String> metadata) {
            if (metadata != null) {
                this.metadata = new HashMap<>(metadata);
            }
            return this;
        }

        public Builder addMetadata(String key, String value) {
            this.metadata.put(key, value);
            return this;
        }

        public RequirementInput build() {
            return new RequirementInput(this);
        }
    }
}