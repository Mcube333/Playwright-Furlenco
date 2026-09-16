package com.tests.pages.furlenco;

import com.framework.base.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import io.qameta.allure.Step;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Page Object for the post-payment result screen (Order Processing -&gt; Order Success / Order
 * Failure in the manual test suite).
 * <p>
 * <b>Unverified:</b> this class was written without actually submitting a payment (this session
 * stopped short of entering card details into the live Razorpay form itself — see
 * {@link FurlencoPaymentPage} javadoc). Selectors here are best-effort guesses at common
 * success/failure copy; re-verify against a real payment run (e.g. via
 * {@code FurlencoPaymentPage.payWithCard(...)} executed by the test suite) and tighten before
 * relying on this in CI.
 */
public class FurlencoOrderResultPage extends BasePage {

    private static final Logger LOGGER = LogManager.getLogger(FurlencoOrderResultPage.class);

    private static final String SUCCESS_INDICATOR =
            ":text(\"Order Placed\"), :text(\"Order placed\"), :text(\"Payment Successful\"), :text(\"Thank you\")";
    private static final String FAILURE_INDICATOR =
            ":text(\"Payment Failed\"), :text(\"payment failed\"), :text(\"Try Again\"), :text(\"unsuccessful\")";
    private static final String ORDER_ID_TEXT = ":text(\"Order ID\"), :text(\"Order #\")";

    public FurlencoOrderResultPage(Page page) {
        super(page);
    }

    @Step("Check if the order was placed successfully")
    public boolean isSuccessDisplayed() {
        boolean urlMatches = currentUrl().contains("/order-success") || currentUrl().contains("/success")
                || currentUrl().contains("/my/orders");
        Locator indicator = page.locator(SUCCESS_INDICATOR);
        boolean indicatorVisible = indicator.count() > 0 && indicator.first().isVisible();
        LOGGER.info("Order success check: urlMatches={}, indicatorVisible={}", urlMatches, indicatorVisible);
        return urlMatches || indicatorVisible;
    }

    @Step("Check if the order/payment failed")
    public boolean isFailureDisplayed() {
        Locator indicator = page.locator(FAILURE_INDICATOR);
        return indicator.count() > 0 && indicator.first().isVisible();
    }

    @Step("Get the order ID text if displayed")
    public String getOrderIdText() {
        Locator idText = page.locator(ORDER_ID_TEXT).first();
        return idText.count() > 0 ? idText.textContent().trim() : "";
    }
}
