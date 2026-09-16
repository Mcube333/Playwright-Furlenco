package com.tests.pages.furlenco;

import com.framework.base.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import io.qameta.allure.Step;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Page Object for the "Order Summary" checkout step, verified live — reached after confirming a
 * delivery address ({@link FurlencoCheckoutAddressPage}). Shows the item cart, value-added
 * services, coupons and price breakup ("&lt;Vertical&gt; Cost Breakup" / "Total Cost"), with a
 * {@code PROCEED} button that moves to {@link FurlencoPaymentPage}.
 */
public class FurlencoOrderSummaryPage extends BasePage {

    private static final Logger LOGGER = LogManager.getLogger(FurlencoOrderSummaryPage.class);

    private static final String PRICE_BREAKDOWN = ":text(\"Cost Breakup\"), :text(\"Total Cost\")";
    private static final String PROCEED_BUTTON = "button:has-text('PROCEED'), button:has-text('Pay ₹'), button:has-text('Proceed')";

    public FurlencoOrderSummaryPage(Page page) {
        super(page);
    }

    @Step("Check if Order Summary is loaded")
    public boolean isLoaded() {
        boolean urlMatches = currentUrl().contains("/checkout") || currentUrl().contains("/cart");
        boolean breakdownVisible = isPriceBreakdownDisplayed();
        LOGGER.info("Order Summary loaded check: urlMatches={}, breakdownVisible={}", urlMatches, breakdownVisible);
        return urlMatches && breakdownVisible;
    }

    @Step("Check if price breakdown is displayed")
    public boolean isPriceBreakdownDisplayed() {
        Locator breakdown = page.locator(PRICE_BREAKDOWN);
        return breakdown.count() > 0 && breakdown.first().isVisible();
    }

    @Step("Click Proceed to move to Payment")
    public FurlencoPaymentPage clickProceed() {
        LOGGER.info("Clicking Proceed on Order Summary");
        Locator proceedBtn = page.locator(PROCEED_BUTTON).first();
        proceedBtn.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        proceedBtn.click();
        page.waitForLoadState();
        page.waitForTimeout(2000);
        return new FurlencoPaymentPage(page);
    }
}
