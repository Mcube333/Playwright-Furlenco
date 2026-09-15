package com.tests.ai.diagnosis;

import static org.assertj.core.api.Assertions.assertThat;

import com.framework.ai.diagnosis.FixType;
import org.testng.annotations.Test;

public class FixTypeTest {

    // 1. All expected enum values exist, with exactly matching names
    @Test
    public void testAllExpectedValuesExist() {
        assertThat(FixType.values()).extracting(Enum::name).containsExactlyInAnyOrder(
                "LOCATOR", "ASSERTION", "WAIT", "TEST_DATA",
                "APPLICATION_BEHAVIOR", "ANALYTICS", "API", "UNKNOWN");
    }

    // 2. No accidental duplicates are possible (enum values are a set by construction; assert the count is exact)
    @Test
    public void testNoDuplicateValues() {
        assertThat(FixType.values()).hasSize(8);
    }

    // 3. Enum names match exactly (case-sensitive), individually
    @Test
    public void testIndividualValueNames() {
        assertThat(FixType.LOCATOR.name()).isEqualTo("LOCATOR");
        assertThat(FixType.ASSERTION.name()).isEqualTo("ASSERTION");
        assertThat(FixType.WAIT.name()).isEqualTo("WAIT");
        assertThat(FixType.TEST_DATA.name()).isEqualTo("TEST_DATA");
        assertThat(FixType.APPLICATION_BEHAVIOR.name()).isEqualTo("APPLICATION_BEHAVIOR");
        assertThat(FixType.ANALYTICS.name()).isEqualTo("ANALYTICS");
        assertThat(FixType.API.name()).isEqualTo("API");
        assertThat(FixType.UNKNOWN.name()).isEqualTo("UNKNOWN");
    }

    @Test
    public void testFromStringParsesKnownValuesCaseInsensitively() {
        assertThat(FixType.fromString("locator")).isEqualTo(FixType.LOCATOR);
        assertThat(FixType.fromString("Test_Data")).isEqualTo(FixType.TEST_DATA);
    }

    @Test
    public void testFromStringDefaultsToUnknownForInvalidOrMissingInput() {
        assertThat(FixType.fromString(null)).isEqualTo(FixType.UNKNOWN);
        assertThat(FixType.fromString("")).isEqualTo(FixType.UNKNOWN);
        assertThat(FixType.fromString("SELF_HEAL")).isEqualTo(FixType.UNKNOWN);
        assertThat(FixType.fromString("nonsense")).isEqualTo(FixType.UNKNOWN);
    }
}
