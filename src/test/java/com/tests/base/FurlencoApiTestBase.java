package com.tests.base;

import static com.framework.api.ApiAssertions.assertMatchesSchema;
import static com.framework.api.ApiAssertions.assertStatusCode;
import static org.assertj.core.api.Assertions.assertThat;

import com.framework.api.APIClient;
import com.framework.api.APIResponse;
import com.tests.models.VerifyAccountRequest;
import com.tests.models.VerifyOtpRequest;
import java.util.UUID;
import org.testng.annotations.BeforeMethod;

/**
 * Shared login + header plumbing for every Furlenco app-backend API test class
 * ({@code st-ciago.furlenco.com}). Logs in via the same phone+OTP pair the web login tests use
 * ({@code test.user.username}/{@code test.user.password} — see {@code FurlencoLoginTest}), then
 * exposes {@link #furlencoClient()}, pre-populated with every header the real iOS app sends on
 * every request beyond auth.
 * <p>
 * The request/response contract and header set below were captured from a real Furlenco iOS app
 * network session against {@code st-ciago.furlenco.com} on 2026-09-21/22. See individual subclass
 * javadocs for the specific flow each one covers.
 * <p>
 * <b>Unverified assumption:</b> the {@code x-rcs} header seen on every captured request (an
 * opaque, seemingly remote-config-related blob) is intentionally omitted below since it wasn't
 * confirmed to be required.
 * <p>
 * <b>Not yet verified live:</b> a run from a sandboxed, non-VPN environment got back a bare
 * "404 page not found" from {@code st-ciago.furlenco.com} for every call — a real server
 * responded, just not (as far as could be confirmed there) the right backend. This most likely
 * means the host needs Furlenco's internal VPN, the same way {@code stag.furlenco.com} does (see
 * {@code staging.properties}) — but that has not been confirmed.
 */
public abstract class FurlencoApiTestBase extends BaseApiTest {

    private static final String MORIARTY_HEADER = "iPhone-18.2.400";

    protected String account;
    protected String otp;
    protected String cityId;
    protected String pincode;
    protected String deviceSessionId;
    protected String sessionToken;

    @BeforeMethod(alwaysRun = true)
    public void authenticateFurlencoSession() {
        account = config.get("test.user.username");
        otp = config.get("test.user.password");
        cityId = String.valueOf(config.getInt("api.city.id", 7));
        pincode = config.get("api.pincode", "500046");
        deviceSessionId = UUID.randomUUID().toString();

        APIResponse verifyAccountResponse = furlencoClient()
                .post("users/verify-account", VerifyAccountRequest.builder().account(account).build());
        assertStatusCode(verifyAccountResponse, 200);

        APIResponse verifyOtpResponse = furlencoClient()
                .post("users/verify-otp", VerifyOtpRequest.builder().account(account).otp(otp).build());
        assertStatusCode(verifyOtpResponse, 200);
        assertMatchesSchema(verifyOtpResponse, "schemas/otp-verify-response-schema.json");

        sessionToken = verifyOtpResponse.bodyAsJson()
                .path("data").path("user").path("session_token").path("token").asText("");
        assertThat(sessionToken).as("Login should yield a non-blank session token").isNotBlank();
    }

    protected APIClient furlencoClient() {
        APIClient client = apiClient
                .withHeader("x-city-id", cityId)
                .withHeader("x-pincode", pincode)
                .withHeader("environment", "staging")
                .withHeader("moriarty", MORIARTY_HEADER)
                .withHeader("X-API-TOKEN", "application/json")
                .withHeader("x-session-id", deviceSessionId);
        if (sessionToken != null && !sessionToken.isBlank()) {
            client = client.withHeader("x-panem-token", sessionToken);
        }
        return client;
    }
}
