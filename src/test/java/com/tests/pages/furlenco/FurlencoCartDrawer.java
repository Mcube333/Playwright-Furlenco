package com.tests.pages.furlenco;

import com.framework.base.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import io.qameta.allure.Step;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Page Object for the Furlenco Cart screen — a real page at {@code /cart}, verified against a
 * live session, not a drawer overlay (the class name is kept for backward compatibility with
 * existing callers/tests).
 * <p>
 * Verified checkout flow (authenticated session, Rent): Cart -&gt; click {@code CHECKOUT} -&gt; a
 * "Rental Terms Reminder" dialog with a {@code PROCEED} button -&gt; navigates to
 * {@code /checkout/address?vertical=...&cartId=...} ({@link FurlencoCheckoutAddressPage}) -&gt;
 * {@link FurlencoOrderSummaryPage} -&gt; {@link FurlencoPaymentPage}. For an unauthenticated
 * session, clicking Pay/Checkout opens the Login dialog directly instead.
 */
public class FurlencoCartDrawer extends BasePage {

    private static final Logger LOGGER = LogManager.getLogger(FurlencoCartDrawer.class);

    // Note: the bare `text=` engine prefix cannot be mixed with plain CSS clauses in the same
    // comma-separated selector list (throws a parse error) — use the composable `:text()` pseudo
    // instead, verified live.
    private static final String CART_CONTAINER = ":text(\"Cost Breakup\"), button:has-text('Pay ₹'), button:has-text('CHECKOUT'), #empty-cart";
    private static final String CLOSE_BUTTON = "button[aria-label='Close'], button:has-text('✕')";
    private static final String EMPTY_CART_MSG = "#empty-cart, :text(\"Your Cart Looks\"), :text(\"empty\"), :text(\"No items\")";
    // Verified button text for an authenticated session is "CHECKOUT"; "Pay ₹<amount>" is what an
    // unauthenticated session shows instead (opens Login rather than navigating).
    private static final String CHECKOUT_BUTTON =
            "button:has-text('CHECKOUT'), button:has-text('Pay ₹'), button:has-text('Proceed')";
    // Verified live: a "Rental Terms Reminder" dialog with a PROCEED button (Rent only, minimum
    // tenure disclosure) — not literally worded "tenure", so match on the dialog's own PROCEED CTA.
    private static final String MIN_TENURE_DIALOG = "[data-slot='dialog-content']:has-text('Rental Terms')";
    private static final String MIN_TENURE_CONFIRM_BUTTON =
            "[data-slot='dialog-content'] button:has-text('PROCEED'), [data-slot='dialog-content'] button:has-text('Continue')";

    public FurlencoCartDrawer(Page page) {
        super(page);
    }

    @Step("Verify if cart drawer or cart page is displayed")
    public boolean isCartOpen() {
        boolean urlIsCart = currentUrl().contains("/cart");
        boolean containerVisible = page.locator(CART_CONTAINER).count() > 0 && page.locator(CART_CONTAINER).first().isVisible();
        LOGGER.info("Cart open check: urlContainsCart={}, containerVisible={}", urlIsCart, containerVisible);
        return urlIsCart || containerVisible;
    }

    @Step("Check if empty cart state or message is displayed")
    public boolean isEmptyStateDisplayed() {
        return page.locator(EMPTY_CART_MSG).count() > 0 && page.locator(EMPTY_CART_MSG).first().isVisible();
    }

    @Step("Close Cart Drawer if close button is present")
    public void closeCart() {
        try {
            Locator closeBtn = page.locator(CLOSE_BUTTON).first();
            if (closeBtn.isVisible()) {
                LOGGER.info("Closing Cart drawer via close button");
                closeBtn.click();
            } else {
                LOGGER.info("Pressing Escape or navigating back if on cart page");
                page.keyboard().press("Escape");
            }
            page.waitForTimeout(500);
        } catch (Exception e) {
            LOGGER.debug("Close cart exception handled: {}", e.getMessage());
        }
    }

    @Step("Check if a minimum-tenure confirmation popup is shown (Rent checkout)")
    public boolean isMinTenurePopupDisplayed() {
        Locator dialog = page.locator(MIN_TENURE_DIALOG);
        return dialog.count() > 0 && dialog.first().isVisible();
    }

    @Step("Confirm the minimum-tenure popup if present")
    public FurlencoCartDrawer confirmMinTenurePopupIfPresent() {
        if (isMinTenurePopupDisplayed()) {
            LOGGER.info("Min tenure popup displayed, confirming");
            page.locator(MIN_TENURE_CONFIRM_BUTTON).first().click();
            page.waitForTimeout(800);
        }
        return this;
    }

    /**
     * Clicks Pay/Checkout. For an authenticated session this navigates towards
     * {@link FurlencoCheckoutAddressPage} (handling the Rent min-tenure dialog along the way); for
     * an unauthenticated session it opens the Login dialog instead — check
     * {@link #isLoginPromptedOnCheckout()} first.
     */
    @Step("Click Checkout to proceed to Delivery Address")
    public FurlencoCheckoutAddressPage clickCheckout() {
        LOGGER.info("Clicking Checkout");
        Locator checkoutBtn = page.locator(CHECKOUT_BUTTON).first();
        checkoutBtn.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        checkoutBtn.click();
        page.waitForTimeout(800);
        confirmMinTenurePopupIfPresent();
        page.waitForLoadState();
        return new FurlencoCheckoutAddressPage(page);
    }

    @Step("Check if clicking Checkout opened the Login dialog (unauthenticated session)")
    public boolean isLoginPromptedOnCheckout() {
        return new FurlencoLoginPage(page).isOpen();
    }
}
