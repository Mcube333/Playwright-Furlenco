package com.tests.pages.furlenco;

import com.framework.base.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import io.qameta.allure.Step;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Page Object for the Payment screen, verified live at {@code /my/payment?payment_id=<id>}: a
 * Razorpay test-mode integration with "UPI" and "Credit Card/Debit Card" tabs (no separate
 * Netbanking tab was observed — {@link #isNetbankingOptionDisplayed()} is kept as a best-effort,
 * unverified fallback in case a different vertical/variant offers one).
 * <p>
 * Card field names are verified live: {@code cardNumber}, {@code nameOnCard}, {@code validity}
 * (MM/YY), {@code cvv} (rendered as a password input). Submitting uses Razorpay's own publicly
 * documented generic test card by default (never a real card) — see
 * {@code test.payment.card.*} in the environment properties files.
 */
public class FurlencoPaymentPage extends BasePage {

    private static final Logger LOGGER = LogManager.getLogger(FurlencoPaymentPage.class);

    private static final String PAYMENT_CONTAINER = ":text(\"Payment Options\")";
    private static final String UPI_TAB = "label:has-text('UPI')";
    private static final String CARD_TAB = "label:has-text('Credit Card'), label:has-text('Debit Card')";
    private static final String CARD_NUMBER_INPUT = "input[name='cardNumber']";
    private static final String NAME_ON_CARD_INPUT = "input[name='nameOnCard']";
    private static final String VALIDITY_INPUT = "input[name='validity']";
    private static final String CVV_INPUT = "input[name='cvv']";
    private static final String PAY_NOW_BUTTON = "button:has-text('PAY NOW'), button:has-text('Pay ₹')";
    private static final String NETBANKING_OPTION = "label:has-text('Netbanking'), label:has-text('Net Banking')";

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
        Locator option = page.locator(CARD_TAB);
        return option.count() > 0 && option.first().isVisible();
    }

    @Step("Check if UPI payment option is offered")
    public boolean isUpiOptionDisplayed() {
        Locator option = page.locator(UPI_TAB);
        return option.count() > 0 && option.first().isVisible();
    }

    @Step("Check if Netbanking payment option is offered")
    public boolean isNetbankingOptionDisplayed() {
        Locator option = page.locator(NETBANKING_OPTION);
        return option.count() > 0 && option.first().isVisible();
    }

    @Step("Select Credit/Debit Card as the payment method")
    public FurlencoPaymentPage selectCardPaymentMethod() {
        LOGGER.info("Selecting Card payment method");
        page.locator(CARD_TAB).first().click();
        page.locator(CARD_NUMBER_INPUT).waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        return this;
    }

    /**
     * Fills the Razorpay test-mode card form. Callers should pass Razorpay's own published test
     * card details (see {@code test.payment.card.*} config) — never a real card.
     */
    @Step("Fill card details and submit payment")
    public FurlencoOrderResultPage payWithCard(String cardNumber, String nameOnCard, String validity, String cvv) {
        LOGGER.info("Filling card payment form (values not logged)");
        page.locator(CARD_NUMBER_INPUT).fill(cardNumber);
        page.locator(NAME_ON_CARD_INPUT).fill(nameOnCard);
        page.locator(VALIDITY_INPUT).fill(validity);
        page.locator(CVV_INPUT).fill(cvv);
        page.locator(PAY_NOW_BUTTON).first().click();
        page.waitForLoadState();
        page.waitForTimeout(4000);
        return new FurlencoOrderResultPage(page);
    }
}
