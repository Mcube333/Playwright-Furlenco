package com.tests.api;

import static com.framework.api.ApiAssertions.assertStatusCode;
import static org.assertj.core.api.Assertions.assertThat;

import com.framework.api.APIClient;
import com.framework.api.APIResponse;
import com.tests.base.FurlencoApiTestBase;
import com.tests.models.KycVerifyUserRequest;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.Test;

/**
 * API coverage for the read-only/safe slice of Furlenco's post-checkout KYC flow, captured from a
 * real Furlenco iOS app network session on 2026-09-21 (triggered right after a Rent order's
 * payment completes — the payment summary flags {@code isKycRequired:true}).
 * <p>
 * KYC lives on a <b>different host</b> than every other Furlenco API test in this repo:
 * {@code st-cia.furlenco.com} (no "go"), not {@code st-ciago.furlenco.com}. Confirm this isn't a
 * typo in the capture before relying on it — it wasn't independently verified live (see
 * {@code com.tests.base.FurlencoApiTestBase} for the general VPN caveat, which applies here too).
 * Login/session headers ({@code x-panem-token} etc.) are the same as every other Furlenco API
 * test; KYC calls additionally send {@code x-tenant-name: Furlenco Rental}.
 * <p>
 * <b>Deliberately NOT automated here — reference only:</b>
 * <ul>
 *   <li>{@code POST /kyc/pan/submit} {@code {"pan":"...","occupationType":"SALARIED"}} — submits
 *       the account holder's real PAN.</li>
 *   <li>{@code POST /kyc/credit-check/initiate} {@code {"consent":true,"panName":"...","ipAddress":"..."}}
 *       — triggers a real credit-bureau (CIBIL) pull via Hyperverge for whoever owns the
 *       configured staging account. The captured response for this exact call included a real
 *       CIBIL score.</li>
 *   <li>{@code POST /kyc/sign-e-contract} — signs a real, binding e-contract.</li>
 * </ul>
 * These three are consequential, hard-to-reverse actions against a real person's real financial/
 * legal data, not something to fire repeatedly and unattended from an automated suite. If this
 * coverage is ever wanted, it needs an explicit decision (and probably a dedicated, disclosed test
 * account) rather than being added quietly here.
 */
@Epic("API")
@Feature("KYC Flow")
public class FurlencoKycApiTest extends FurlencoApiTestBase {

    private static final String TENANT_NAME_HEADER = "Furlenco Rental";

    @Test(groups = {"smoke", "api", "furlenco"}, priority = 1)
    @Severity(SeverityLevel.NORMAL)
    @Description("GET /kyc should return a screen definition for the logged-in user's current KYC step")
    public void verifyKycScreenDefinitionIsFetchable() {
        APIResponse response = kycClient().get(kycUrl("kyc"));

        assertStatusCode(response, 200);
        assertThat(response.bodyAsJson().path("screens").isObject())
                .as("GET /kyc should return a non-empty 'screens' object describing the current step")
                .isTrue();
    }

    @Test(groups = {"regression", "api", "furlenco"}, priority = 2)
    @Severity(SeverityLevel.NORMAL)
    @Description("GET /kyc/hyperverge/initiate should hand back a bearer token for the Hyperverge SDK session")
    public void verifyHypervergeInitiateReturnsAccessToken() {
        // Read-only: this only starts a Hyperverge SDK session token, it doesn't submit any
        // document or trigger a check by itself.
        APIResponse response = kycClient().get(kycUrl("kyc/hyperverge/initiate"));

        assertStatusCode(response, 200);
        assertThat(response.bodyAsJson().path("accessToken").asText(""))
                .as("Hyperverge initiate should return a non-blank bearer access token")
                .startsWith("Bearer ");
        assertThat(response.bodyAsJson().path("transactionId").asText(""))
                .as("Hyperverge initiate should return a non-blank transaction id")
                .isNotBlank();
    }

    @Test(groups = {"regression", "api", "furlenco"}, priority = 3)
    @Severity(SeverityLevel.NORMAL)
    @Description("POST /kyc/verify-user should (re)send a phone OTP for KYC contact verification")
    public void verifyKycVerifyUserAcceptsPhoneOtpRequest() {
        // Mirrors the already-accepted login-OTP pattern (FurlencoLoginTest): this only asks for
        // an OTP to be (re)sent, it doesn't itself verify or change anything.
        APIResponse response = kycClient()
                .post(kycUrl("kyc/verify-user"), KycVerifyUserRequest.builder().contactNo(account).build());

        assertStatusCode(response, 200);
        assertThat(response.bodyAsJson().path("success").asBoolean())
                .as("kyc/verify-user should report success=true for a valid phone number")
                .isTrue();
    }

    private APIClient kycClient() {
        return furlencoClient().withHeader("x-tenant-name", TENANT_NAME_HEADER);
    }

    private String kycUrl(String path) {
        return config.get("api.kyc.base.url", "https://st-cia.furlenco.com/api/v1") + "/" + path;
    }
}
