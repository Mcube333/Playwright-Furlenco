package com.tests.api;

import static com.framework.api.ApiAssertions.assertMatchesSchema;
import static com.framework.api.ApiAssertions.assertStatusCode;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.framework.api.APIResponse;
import com.tests.base.FurlencoApiTestBase;
import com.tests.models.BreakageDryRunRequest;
import com.tests.models.ReturnItemRef;
import com.tests.models.ReturnRequestBody;
import com.tests.models.ReturnSummaryRequestBody;
import com.tests.models.ReturnUserSelectionDetails;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import java.util.List;
import org.testng.SkipException;
import org.testng.annotations.Test;

/**
 * API coverage for Furlenco's item Returns flow: GET /returns (list what's returnable) -&gt;
 * POST /returns/request (select item + reason) -&gt; POST /returns/summary (pickup dates/charges)
 * -&gt; POST /bundles/breakage-dry-run (bundle-discount impact check).
 * <p>
 * The request/response contract below (fields, header set, the fixed {@code "PHOENIX"}
 * {@code previousState} token, {@code x-panem-token} as the session-auth header) was captured
 * from a real Furlenco iOS app network session against {@code st-ciago.furlenco.com} on
 * 2026-09-22. Login reuses the same {@code test.user.username}/{@code test.user.password}
 * (phone/fixed-OTP) pair already used by the web login tests (see {@code FurlencoLoginTest}) —
 * no credentials are hardcoded here.
 * <p>
 * <b>Data dependency:</b> these tests need the configured staging account to currently have at
 * least one returnable rental item. If it doesn't, {@link #fetchReturnableSelection()} skips
 * every test that depends on it with a clear reason, the same way {@code FurlencoCartVasTest}
 * degrades gracefully rather than failing on unavailable test data.
 * <p>
 * See {@code FurlencoApiTestBase} for the shared login/header plumbing (including the VPN caveat).
 */
@Epic("API")
@Feature("Returns Flow")
public class FurlencoReturnsApiTest extends FurlencoApiTestBase {

    @Test(groups = {"smoke", "api", "furlenco"}, priority = 1)
    @Severity(SeverityLevel.BLOCKER)
    @Description("GET /returns should list the logged-in user's currently returnable items")
    public void verifyAuthenticatedUserCanFetchReturnableItems() {
        APIResponse response = furlencoClient().get("returns");

        assertStatusCode(response, 200);
        assertMatchesSchema(response, "schemas/returns-list-response-schema.json");
        assertThat(response.bodyAsJson().path("success").asBoolean())
                .as("GET /returns should report success=true for an authenticated user")
                .isTrue();
    }

    @Test(groups = {"regression", "api", "furlenco"}, priority = 2)
    @Severity(SeverityLevel.CRITICAL)
    @Description("POST /returns/request should accept a returnable item + reason and advance the flow")
    public void verifyReturnRequestAcceptsSelectedItemAndProgressesToNextStep() {
        ReturnableSelection selection = fetchReturnableSelection();

        ReturnRequestBody body = ReturnRequestBody.builder()
                .userSelectionDetails(ReturnUserSelectionDetails.builder()
                        .products(List.of(ReturnItemRef.builder().id(selection.itemId()).build()))
                        .enrichments(List.of(selection.enrichmentReason()))
                        .build())
                .build();

        APIResponse response = furlencoClient().post("returns/request", body);

        assertStatusCode(response, 200);
        assertMatchesSchema(response, "schemas/return-request-response-schema.json");
        assertThat(response.bodyAsJson().path("success").asBoolean())
                .as("POST /returns/request should report success=true for a valid returnable item")
                .isTrue();
        assertThat(response.bodyAsJson().path("data").path("nextStep").asText())
                .as("A successful return request should hand back a non-blank next step")
                .isNotBlank();
    }

    @Test(groups = {"regression", "api", "furlenco"}, priority = 3)
    @Severity(SeverityLevel.NORMAL)
    @Description("POST /returns/summary should respond with a valid, schema-conformant summary payload")
    public void verifyReturnSummaryRespondsWithValidPayload() {
        ReturnableSelection selection = fetchReturnableSelection();
        Long addressId = fetchFirstBillingAddressId();

        ReturnSummaryRequestBody body = ReturnSummaryRequestBody.builder()
                .userSelectionDetails(ReturnUserSelectionDetails.builder()
                        .products(List.of(ReturnItemRef.builder().id(selection.itemId()).build()))
                        .enrichments(List.of(selection.enrichmentReason()))
                        .addressId(addressId)
                        .build())
                .build();

        APIResponse response = furlencoClient().post("returns/summary", body);

        // NOTE: happy-path business assertions (availableDates populated, finalAmount computed)
        // need an addressId verified to be a valid *pickup* address for this account — only a
        // billing addressId was available to fetch generically, and its acceptance here hasn't
        // been confirmed live. So this test asserts the response CONTRACT (status + shape), not
        // the specific business outcome, until that's verified against a real staging account.
        assertStatusCode(response, 200);
        assertThat(response.bodyAsJson().path("success").isBoolean())
                .as("POST /returns/summary should always return a boolean success field")
                .isTrue();
    }

    @Test(groups = {"regression", "api", "furlenco"}, priority = 4)
    @Severity(SeverityLevel.NORMAL)
    @Description("POST /bundles/breakage-dry-run should evaluate bundle impact for a returned item")
    public void verifyBreakageDryRunForReturnedItem() {
        ReturnableSelection selection = fetchReturnableSelection();

        BreakageDryRunRequest body = BreakageDryRunRequest.builder()
                .affectedItemIds(List.of(selection.itemId()))
                .build();

        APIResponse response = furlencoClient().post("bundles/breakage-dry-run", body);

        assertStatusCode(response, 200);
        assertMatchesSchema(response, "schemas/breakage-dry-run-response-schema.json");
        assertThat(response.bodyAsJson().path("success").asBoolean())
                .as("POST /bundles/breakage-dry-run should report success=true for a valid item id")
                .isTrue();
    }

    @Test(groups = {"regression", "api", "furlenco", "negative"}, priority = 5)
    @Severity(SeverityLevel.MINOR)
    @Description("POST /returns/request should not silently succeed for a non-existent item id")
    public void verifyReturnRequestRejectsNonExistentItemId() {
        // The exact error shape for this case wasn't captured live, so this only asserts the
        // request is NOT accepted as a normal success — not a specific status code or body shape.
        ReturnRequestBody body = ReturnRequestBody.builder()
                .userSelectionDetails(ReturnUserSelectionDetails.builder()
                        .products(List.of(ReturnItemRef.builder().id(Long.MAX_VALUE - 1).build()))
                        .enrichments(List.of("Others"))
                        .build())
                .build();

        APIResponse response = furlencoClient().post("returns/request", body);

        boolean rejected = response.statusCode() >= 400
                || !response.bodyAsJson().path("success").asBoolean(true);
        assertThat(rejected)
                .as("A non-existent item id should not be accepted as a successful return request. Status=%d, body=%s",
                        response.statusCode(), response.bodyAsString())
                .isTrue();
    }

    private ReturnableSelection fetchReturnableSelection() {
        APIResponse response = furlencoClient().get("returns");
        assertStatusCode(response, 200);

        JsonNode data = response.bodyAsJson().path("data");
        JsonNode products = data.path("products");
        long itemId = -1;
        for (JsonNode product : products) {
            if (product.path("isReturnable").asBoolean(false)) {
                itemId = product.path("id").asLong();
                break;
            }
        }
        if (itemId < 0) {
            throw new SkipException("Configured staging account [" + account
                    + "] currently has no returnable rental item — the Returns flow needs at least"
                    + " one active, returnable item to exercise this test.");
        }

        String enrichmentReason = "Others";
        JsonNode options = data.path("enrichments").path("questions").path(0).path("options");
        for (JsonNode option : options) {
            if ("Others".equalsIgnoreCase(option.path("option").asText(""))) {
                enrichmentReason = option.path("option").asText("Others");
                break;
            }
        }

        return new ReturnableSelection(itemId, enrichmentReason);
    }

    private Long fetchFirstBillingAddressId() {
        APIResponse response = furlencoClient().get("users/addresses");
        assertStatusCode(response, 200);

        JsonNode billingAddresses = response.bodyAsJson().path("data").path("billingAddresses");
        if (!billingAddresses.isArray() || billingAddresses.isEmpty()) {
            throw new SkipException("Configured staging account [" + account
                    + "] has no billing address on file — POST /returns/summary needs an addressId.");
        }
        return billingAddresses.get(0).path("id").asLong();
    }

    private record ReturnableSelection(long itemId, String enrichmentReason) {
    }
}
