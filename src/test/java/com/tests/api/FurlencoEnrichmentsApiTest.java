package com.tests.api;

import static com.framework.api.ApiAssertions.assertMatchesSchema;
import static com.framework.api.ApiAssertions.assertStatusCode;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.framework.api.APIResponse;
import com.tests.base.FurlencoApiTestBase;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import java.util.Map;
import org.testng.annotations.Test;

/**
 * API coverage for {@code GET /enrichments?eventName=...} — a generic, read-only endpoint that
 * returns the reason-selection question(s) shown before a given customer action, the same shape
 * as the Returns flow's own baked-in enrichments (see {@code FurlencoReturnsApiTest}). Captured
 * from a real Furlenco iOS app network session on 2026-09-21 for {@code PLAN_CANCELLATION_REQUESTED}
 * — the reasons shown before cancelling a UNLMTD subscription plan.
 */
@Epic("API")
@Feature("Plan Cancellation Enrichments")
public class FurlencoEnrichmentsApiTest extends FurlencoApiTestBase {

    @Test(groups = {"regression", "api", "furlenco"}, priority = 1)
    @Severity(SeverityLevel.MINOR)
    @Description("GET /enrichments?eventName=PLAN_CANCELLATION_REQUESTED should list plan cancellation reasons")
    public void verifyPlanCancellationEnrichmentsAreFetchable() {
        APIResponse response = furlencoClient()
                .get("enrichments", Map.of("eventName", "PLAN_CANCELLATION_REQUESTED"));

        assertStatusCode(response, 200);
        assertMatchesSchema(response, "schemas/enrichments-response-schema.json");
        assertThat(response.bodyAsJson().path("success").asBoolean())
                .as("GET /enrichments should report success=true")
                .isTrue();

        JsonNode options = response.bodyAsJson().path("data").path("questions").path(0).path("options");
        assertThat(options.isArray() && !options.isEmpty())
                .as("Plan cancellation reasons list should not be empty")
                .isTrue();
    }
}
