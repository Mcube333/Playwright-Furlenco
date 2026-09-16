package com.tests.pages.furlenco;

import com.framework.base.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import io.qameta.allure.Step;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Page Object for the Payment screen.
 * <p>
 * Deliberately observation-only: this class exposes checks for which payment methods are
 * presented (Card / UPI / Netbanking) but never fills card/UPI details or submits a real payment.
 * Automating an actual charge against a live payment gateway is out of scope for this framework
 * layer — if/when Furlenco provides sandbox payment gateway credentials and a documented test-card
 * flow, that belongs in a dedicated, explicitly-reviewed module (see
 * {@code com.tests.api.payment} suggestion in the project README), not here.
 */
public class FurlencoPaymentPage extends BasePage {

    private static final Logger LOGGER = LogManager.getLogger(FurlencoPaymentPage.class);

    private static final String PAYMENT_CONTAINER =
            "[data-slot='payment'], main:has-text('Payment'), main:has-text('Choose Payment')";
    // Note: the bare `text=` engine prefix cannot be mixed with plain CSS clauses in the same
    // comma-separated selector list (throws a parse error) — use the composable `:text()` pseudo
    // instead, verified live.
    private static final String CARD_OPTION = ":text(\"Card\"), [data-slot='payment-card'], button:has-text('Card')";
    private static final String UPI_OPTION = ":text(\"UPI\"), [data-slot='payment-upi'], button:has-text('UPI')";
    private static final String NETBANKING_OPTION =
            ":text(\"Netbanking\"), :text(\"Net Banking\"), [data-slot='payment-netbanking'], button:has-text('Netbanking')";

    public FurlencoPaymentPage(Page page) {
        super(page);
    }

    @Step("Check if Payment screen is loaded")
    public boolean isLoaded() {
        boolean urlMatches = currentUrl().contains("/payment");
        Locator container = page.locator(PAYMENT_CONTAINER);
        boolean containerVisible = container.count() > 0 && container.first().isVisible();
        LOGGER.info("Payment screen loaded check: urlMatches={}, containerVisible={}", urlMatches, containerVisible);
        return urlMatches || containerVisible;
    }

    @Step("Check if Card payment option is offered")
    public boolean isCardOptionDisplayed() {
        Locator option = page.locator(CARD_OPTION);
        return option.count() > 0 && option.first().isVisible();
    }

    @Step("Check if UPI payment option is offered")
    public boolean isUpiOptionDisplayed() {
        Locator option = page.locator(UPI_OPTION);
        return option.count() > 0 && option.first().isVisible();
    }

    @Step("Check if Netbanking payment option is offered")
    public boolean isNetbankingOptionDisplayed() {
        Locator option = page.locator(NETBANKING_OPTION);
        return option.count() > 0 && option.first().isVisible();
    }
}
