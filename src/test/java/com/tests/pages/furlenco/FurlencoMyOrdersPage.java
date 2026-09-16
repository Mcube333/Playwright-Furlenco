package com.tests.pages.furlenco;

import com.framework.base.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import io.qameta.allure.Step;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Page Object for "My Orders" ({@code /my/orders}) and an individual order's detail page
 * ({@code /my/orders/cart-checkouts/&lt;id&gt;}) — verified live. Each order card shows an
 * order-status badge, a "TRACK BUY ORDER"/"TRACK RENTAL ORDER" link, and "View Details"; the
 * detail page shows a "Cancel" link ({@code /my/orders/cancel/&lt;id&gt;}) next to a still-active
 * order's status.
 * <p>
 * <b>Unverified beyond this point:</b> the actual cancel-confirmation screen (reached by
 * following that "Cancel" link) was not inspected this session — navigating directly to a
 * {@code /cancel/} URL was correctly treated as a potentially-destructive action and blocked.
 * {@link #confirmCancellation()} is a best-effort guess at common confirm-dialog copy
 * ("Confirm"/"Yes, Cancel"); re-verify against a real run and tighten before relying on this in
 * CI.
 */
public class FurlencoMyOrdersPage extends BasePage {

    private static final Logger LOGGER = LogManager.getLogger(FurlencoMyOrdersPage.class);

    private static final String ORDER_CARD = "a[href*='/my/orders/cart-checkouts/']";
    private static final String BUY_ORDER_CARD = "a[href*='/my/orders/cart-checkouts/']:has-text('Buy')";
    private static final String RENT_ORDER_CARD = "a[href*='/my/orders/cart-checkouts/']:has-text('Rent')";
    private static final String CANCEL_LINK = "a[href*='/my/orders/cancel/']";
    private static final String CONFIRM_CANCEL_BUTTON =
            "button:has-text('Confirm'), button:has-text('Yes, Cancel'), button:has-text('Cancel Order')";
    private static final String CANCELLED_INDICATOR = ":text(\"Cancelled\"), :text(\"cancelled\")";

    public FurlencoMyOrdersPage(Page page) {
        super(page);
    }

    @Step("Navigate to My Orders")
    public FurlencoMyOrdersPage open(String baseUrl) {
        navigateTo(baseUrl + "/my/orders");
        page.waitForLoadState();
        page.waitForTimeout(1500);
        return this;
    }

    @Step("Check if My Orders is loaded")
    public boolean isLoaded() {
        return currentUrl().contains("/my/orders");
    }

    @Step("Get count of order cards displayed")
    public int getOrderCount() {
        return page.locator(ORDER_CARD).count();
    }

    @Step("Open the first Buy order's details")
    public FurlencoMyOrdersPage openFirstBuyOrder() {
        LOGGER.info("Opening first Buy order details");
        Locator card = page.locator(BUY_ORDER_CARD).first();
        card.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        card.click();
        page.waitForLoadState();
        page.waitForTimeout(1000);
        return this;
    }

    @Step("Open the first Rent order's details")
    public FurlencoMyOrdersPage openFirstRentOrder() {
        LOGGER.info("Opening first Rent order details");
        Locator card = page.locator(RENT_ORDER_CARD).first();
        card.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        card.click();
        page.waitForLoadState();
        page.waitForTimeout(1000);
        return this;
    }

    @Step("Check if this order's detail page is loaded")
    public boolean isOrderDetailLoaded() {
        return currentUrl().contains("/my/orders/cart-checkouts/");
    }

    @Step("Check if a Cancel action is available on this order")
    public boolean isCancelAvailable() {
        Locator cancelLink = page.locator(CANCEL_LINK);
        return cancelLink.count() > 0 && cancelLink.first().isVisible();
    }

    @Step("Click Cancel on this order")
    public FurlencoMyOrdersPage clickCancel() {
        LOGGER.info("Clicking Cancel on order");
        page.locator(CANCEL_LINK).first().click();
        page.waitForLoadState();
        page.waitForTimeout(1000);
        return this;
    }

    @Step("Confirm the order cancellation")
    public FurlencoMyOrdersPage confirmCancellation() {
        LOGGER.info("Confirming order cancellation");
        Locator confirmBtn = page.locator(CONFIRM_CANCEL_BUTTON).first();
        confirmBtn.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        confirmBtn.click();
        page.waitForTimeout(1500);
        return this;
    }

    @Step("Check if the order now shows as cancelled")
    public boolean isCancelled() {
        Locator indicator = page.locator(CANCELLED_INDICATOR);
        return indicator.count() > 0 && indicator.first().isVisible();
    }
}
