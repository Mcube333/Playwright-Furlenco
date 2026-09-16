package com.tests.pages.furlenco;

import com.framework.base.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import io.qameta.allure.Step;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Page Object for the "Order Summary" step of checkout. Verified against a live session: this is
 * NOT a separate route — the Furlenco cart at {@code /cart} already shows the price breakup
 * ("Rent Cost Breakup" / "View Breakup" / "Total Cost") and the {@code Pay ₹<amount>} button
 * inline, so this class models that same page's summary/proceed state after login rather than a
 * distinct screen. {@code clickProceed()} re-clicks Pay now that the session is authenticated.
 */
public class FurlencoOrderSummaryPage extends BasePage {

    private static final Logger LOGGER = LogManager.getLogger(FurlencoOrderSummaryPage.class);

    private static final String PRICE_BREAKDOWN = ":text(\"Rent Cost Breakup\"), :text(\"Total Cost\")";
    private static final String PROCEED_BUTTON = "button:has-text('Pay ₹'), button:has-text('Proceed')";

    public FurlencoOrderSummaryPage(Page page) {
        super(page);
    }

    @Step("Check if Order Summary (price breakup on Cart) is loaded")
    public boolean isLoaded() {
        boolean urlMatches = currentUrl().contains("/cart") || currentUrl().contains("/checkout")
                || currentUrl().contains("/order-summary");
        boolean breakdownVisible = isPriceBreakdownDisplayed();
        LOGGER.info("Order Summary loaded check: urlMatches={}, breakdownVisible={}", urlMatches, breakdownVisible);
        return urlMatches && breakdownVisible;
    }

    @Step("Check if price breakdown is displayed")
    public boolean isPriceBreakdownDisplayed() {
        Locator breakdown = page.locator(PRICE_BREAKDOWN);
        return breakdown.count() > 0 && breakdown.first().isVisible();
    }

    @Step("Click Pay/Proceed to move to Payment")
    public FurlencoPaymentPage clickProceed() {
        LOGGER.info("Clicking Pay/Proceed on Order Summary");
        Locator proceedBtn = page.locator(PROCEED_BUTTON).first();
        proceedBtn.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        proceedBtn.click();
        page.waitForLoadState();
        page.waitForTimeout(800);
        return new FurlencoPaymentPage(page);
    }
}
