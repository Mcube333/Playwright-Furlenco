package com.framework.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.util.Set;
import org.assertj.core.api.Assertions;

/**
 * Static assertion helpers for API tests. Kept separate from APIResponse so response
 * stays a plain data holder and assertions stay swappable (e.g., soft-assert variants later).
 */
public final class ApiAssertions {

    private static final JsonSchemaFactory SCHEMA_FACTORY =
            JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);

    private ApiAssertions() {
    }

    public static void assertStatusCode(APIResponse response, int expected) {
        Assertions.assertThat(response.statusCode())
                .as("Expected status code %d but got %d. Body: %s",
                        expected, response.statusCode(), response.bodyAsString())
                .isEqualTo(expected);
    }

    public static void assertResponseTimeUnder(APIResponse response, long maxMillis) {
        Assertions.assertThat(response.responseTimeMs())
                .as("Response time SLA breached: %dms > %dms limit", response.responseTimeMs(), maxMillis)
                .isLessThanOrEqualTo(maxMillis);
    }

    public static void assertJsonPathEquals(APIResponse response, String fieldName, String expectedValue) {
        String actual = response.jsonPathValue(fieldName);
        Assertions.assertThat(actual)
                .as("JSON field [%s] mismatch. Full body: %s", fieldName, response.bodyAsString())
                .isEqualTo(expectedValue);
    }

    public static void assertJsonPathExists(APIResponse response, String fieldName) {
        JsonNode node = response.bodyAsJson().get(fieldName);
        Assertions.assertThat(node)
                .as("Expected JSON field [%s] to exist. Full body: %s", fieldName, response.bodyAsString())
                .isNotNull();
    }

    /**
     * Validates the response body against a JSON schema file on the classpath
     * (e.g. "schemas/user-schema.json" under src/test/resources).
     */
    public static void assertMatchesSchema(APIResponse response, String schemaClasspathPath) {
        JsonSchema schema = SCHEMA_FACTORY.getSchema(
                ApiAssertions.class.getClassLoader().getResourceAsStream(schemaClasspathPath));
        Set<ValidationMessage> errors = schema.validate(response.bodyAsJson());
        Assertions.assertThat(errors)
                .as("Schema validation failed for %s. Errors: %s. Body: %s",
                        schemaClasspathPath, errors, response.bodyAsString())
                .isEmpty();
    }
}
