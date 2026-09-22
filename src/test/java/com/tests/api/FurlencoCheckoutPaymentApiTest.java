package com.tests.api;

import static com.framework.api.ApiAssertions.assertMatchesSchema;
import static com.framework.api.ApiAssertions.assertStatusCode;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.framework.api.APIResponse;
import com.tests.base.FurlencoApiTestBase;
import com.tests.models.CartItemAddRequest;
import com.tests.models.CheckoutOrderRequest;
import com.tests.models.CreatePaymentRequest;
import com.tests.models.ProcessRazorpayPaymentRequest;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import java.util.Map;
import org.testng.SkipException;
import org.testng.annotations.Test;

/**
 * API coverage for Furlenco's Rent checkout + payment flow: GET /carts (fetch the account's cart)
 * -&gt; POST /carts/{cartId}/cartItems (add a product) -&gt; POST /carts/{cartId}/create-checkout-order
 * -&gt; POST /carts/{cartId}/createPayment -&gt; POST /payments/config.
 * <p>
 * Captured from a real Furlenco iOS app network session on 2026-09-21, for the exact account
 * already configured as {@code test.user.username} in this repo (see
 * {@code com.tests.base.FurlencoApiTestBase} for the shared login/header plumbing and its VPN
 * caveat — none of this has been verified reachable from a sandboxed, non-VPN environment).
 * <p>
 * <b>These tests create a real order and a real (unpaid) payment record against the configured
 * staging account every time they run</b> — the same kind of side effect the existing
 * {@code FurlencoCheckoutFlowTest}/{@code FurlencoSuccessfulOrderTest} UI tests already accept.
 * The checkout+payment steps are deliberately kept as ONE test rather than split into independent
 * ones, so a run only ever creates one order, not one per assertion.
 * <p>
 * <b>Deliberately out of scope:</b> actually completing the Razorpay payment
 * (POST /payments/processRazorpayPayment) needs a real {@code razorpay_payment_id}, which only
 * exists once Razorpay has processed an actual charge — obtainable only via Razorpay API keys
 * this project doesn't have, or by driving the Razorpay checkout UI (already covered by
 * {@code FurlencoCheckoutFlowTest}). {@link #verifyProcessRazorpayPaymentRejectsInvalidReference()}
 * only exercises the negative path (a bogus reference is rejected), never a real payment.
 */
@Epic("API")
@Feature("Checkout & Payment Flow")
public class FurlencoCheckoutPaymentApiTest extends FurlencoApiTestBase {

    @Test(groups = {"smoke", "api", "furlenco"}, priority = 1)
    @Severity(SeverityLevel.CRITICAL)
    @Description("POST /carts/{cartId}/cartItems should accept an available rent product")
    public void verifyAddingAvailableRentProductToCartSucceeds() {
        long cartId = fetchCartId();
        long productId = fetchFirstAvailableRentProductId();

        APIResponse response = furlencoClient().post("carts/" + cartId + "/cartItems",
                CartItemAddRequest.builder().id(productId).tenure(1).build());

        assertStatusCode(response, 200);
        assertThat(response.bodyAsJson().path("success").asBoolean())
                .as("Adding an available rent product to the cart should report success=true")
                .isTrue();
    }

    @Test(groups = {"regression", "api", "furlenco", "payment-critical"}, priority = 2)
    @Severity(SeverityLevel.BLOCKER)
    @Description("Checkout (create-checkout-order + createPayment + payments/config) should succeed once the cart is checkout-ready")
    public void verifyCheckoutAndPaymentSessionCreation() {
        long cartId = fetchCartId();
        boolean readyToCheckout = isCartCheckoutReady(cartId);
        if (!readyToCheckout) {
            long productId = fetchFirstAvailableRentProductId();
            APIResponse addResponse = furlencoClient().post("carts/" + cartId + "/cartItems",
                    CartItemAddRequest.builder().id(productId).tenure(1).build());
            assertStatusCode(addResponse, 200);
            readyToCheckout = addResponse.bodyAsJson()
                    .path("data").path("cart").path("canCheckout").asBoolean(false);
        }
        if (!readyToCheckout) {
            throw new SkipException("Configured staging account [" + account
                    + "]'s cart is not checkout-ready (canCheckout=false) even after adding an"
                    + " available product — likely needs a delivery address or has failed a"
                    + " minimum-order-value/serviceability check this test doesn't set up.");
        }

        APIResponse checkoutResponse = furlencoClient().post("carts/" + cartId + "/create-checkout-order",
                CheckoutOrderRequest.builder().isAutopaySelected(false).build());
        assertStatusCode(checkoutResponse, 200);
        assertMatchesSchema(checkoutResponse, "schemas/checkout-order-response-schema.json");
        assertThat(checkoutResponse.bodyAsJson().path("success").asBoolean())
                .as("Checkout should report success=true once the cart is checkout-ready")
                .isTrue();

        APIResponse createPaymentResponse = furlencoClient().post("carts/" + cartId + "/createPayment",
                CreatePaymentRequest.builder().build());
        assertStatusCode(createPaymentResponse, 200);
        assertMatchesSchema(createPaymentResponse, "schemas/create-payment-response-schema.json");
        long paymentId = createPaymentResponse.bodyAsJson().path("data").path("payment").path("id").asLong();
        assertThat(paymentId).as("createPayment should hand back a positive payment id").isPositive();

        // payments/config takes identifier/includeDraftOrder as query params with an empty body
        // (confirmed from the capture) — APIClient.post(path, body) has no query-param overload,
        // so the query string is appended to the path itself instead.
        APIResponse paymentConfigResponse = furlencoClient()
                .post("payments/config?identifier=" + paymentId + "&includeDraftOrder=true", null);

        assertStatusCode(paymentConfigResponse, 200);
        assertMatchesSchema(paymentConfigResponse, "schemas/payments-config-response-schema.json");
        assertThat(paymentConfigResponse.bodyAsJson().path("data").path("payment").path("id").asLong())
                .as("payments/config should describe the same payment id just created")
                .isEqualTo(paymentId);
    }

    @Test(groups = {"regression", "api", "furlenco", "negative"}, priority = 3)
    @Severity(SeverityLevel.MINOR)
    @Description("POST /payments/processRazorpayPayment should reject a payment reference that doesn't exist")
    public void verifyProcessRazorpayPaymentRejectsInvalidReference() {
        ProcessRazorpayPaymentRequest body = ProcessRazorpayPaymentRequest.builder()
                .paymentId(1L)
                .razorpayPaymentId("pay_INVALID_TEST_REFERENCE")
                .build();

        APIResponse response = furlencoClient().post("payments/processRazorpayPayment", body);

        boolean rejected = response.statusCode() >= 400
                || !response.bodyAsJson().path("data").path("paymentSuccess").asBoolean(true);
        assertThat(rejected)
                .as("A bogus Razorpay payment reference should never be accepted as a successful payment. Status=%d, body=%s",
                        response.statusCode(), response.bodyAsString())
                .isTrue();
    }

    private long fetchCartId() {
        APIResponse response = furlencoClient().get("carts",
                Map.of("cityId", cityId, "pincode", pincode, "withDefaultAddress", "true"));
        assertStatusCode(response, 200);
        return response.bodyAsJson().path("data").path("cart").path("id").asLong();
    }

    private boolean isCartCheckoutReady(long cartId) {
        APIResponse response = furlencoClient().get("carts",
                Map.of("cityId", cityId, "pincode", pincode, "withDefaultAddress", "true"));
        assertStatusCode(response, 200);
        return response.bodyAsJson().path("data").path("cart").path("canCheckout").asBoolean(false);
    }

    private long fetchFirstAvailableRentProductId() {
        APIResponse response = furlencoClient().get("catalogue/products",
                Map.of("collection", "bedroom-furniture-on-rent", "collectionType", "CATEGORY_RENT"));
        assertStatusCode(response, 200);

        JsonNode products = response.bodyAsJson().path("data").path("products");
        for (JsonNode product : products) {
            if (product.path("available").asBoolean(false)) {
                return product.path("id").asLong();
            }
        }
        throw new SkipException("No available (in-stock) product found under the"
                + " bedroom-furniture-on-rent collection for cityId=" + cityId + "/pincode=" + pincode
                + " — needed to exercise the checkout flow.");
    }
}
