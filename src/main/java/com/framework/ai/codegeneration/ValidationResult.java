package com.framework.ai.codegeneration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result of static validation on generated draft code.
 */
public final class ValidationResult {

    private final boolean valid;
    private final List<String> errors;
    private final List<String> warnings;

    public ValidationResult(boolean valid, List<String> errors, List<String> warnings) {
        this.valid = valid;
        this.errors = errors != null ? Collections.unmodifiableList(new ArrayList<>(errors)) : Collections.emptyList();
        this.warnings = warnings != null ? Collections.unmodifiableList(new ArrayList<>(warnings)) : Collections.emptyList();
    }

    public boolean isValid() {
        return valid;
    }

    public List<String> getErrors() {
        return errors;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public static ValidationResult success(List<String> warnings) {
        return new ValidationResult(true, Collections.emptyList(), warnings);
    }

    public static ValidationResult failure(List<String> errors, List<String> warnings) {
        return new ValidationResult(false, errors, warnings);
    }
}