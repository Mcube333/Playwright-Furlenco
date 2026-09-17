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

    // ---- Cart item rendering / quantity / removal (CART-01, CART-02) ----
    // Verified live against a real mixed Rent+Buy cart. The Cart page shows a "Rent Cart"
    // section and a "Buy Cart" section side by side when both have items (each with its own
    // coupon panel and cost breakup), with one combined total and one CHECKOUT button.
    // Item cards have no stable per-card wrapper class, so every per-item accessor below is
    // scoped relative to that item's delete button (`.cart-item-delete-button` — the one
    // reliably distinctive class in the whole card, verified live) via the nearest ancestor
    // that also contains the item-name paragraph. This also naturally excludes "Frequently
    // Bought Together"/"Customers Also Viewed" suggestion cards on the same page, since only
    // real cart items have a delete button.
    private static final String RENT_SECTION_HEADING = ":text(\"Rent Cart\")";
    private static final String BUY_SECTION_HEADING = ":text(\"Buy Cart\")";
    private static final String ITEM_DELETE_BUTTON = "button.cart-item-delete-button";
    private static final String ITEM_NAME_TEXT = "p.MuiTypography-P_Medium";
    private static final String ITEM_QUANTITY_TEXT = "p.MuiTypography-body1";
    private static final String ITEM_QUANTITY_BUTTON = "button.MuiIconButton-root:not(.cart-item-delete-button)";
    private static final String ITEM_ORIGINAL_PRICE_TEXT = "p.MuiTypography-Tiny_Medium";
    private static final String ITEM_DISCOUNT_BADGE_TEXT = "p.MuiTypography-Tiny_SemiBold";
    private static final String ITEM_FINAL_PRICE_TEXT = "p.MuiTypography-Small_SemiBold";

    @Step("Check if the Rent Cart section is displayed")
    public boolean isRentSectionDisplayed() {
        return pollForVisible(RENT_SECTION_HEADING);
    }

    @Step("Check if the Buy Cart section is displayed")
    public boolean isBuySectionDisplayed() {
        return pollForVisible(BUY_SECTION_HEADING);
    }

    /**
     * Polls briefly rather than checking once — verified live that the Cart page's item sections
     * can take longer than a Cart-icon-click's fixed short wait to finish loading, especially
     * right after adding an item.
     */
    private boolean pollForVisible(String selector) {
        for (int i = 0; i < 10; i++) {
            Locator located = page.locator(selector);
            if (located.count() > 0 && located.first().isVisible()) {
                return true;
            }
            page.waitForTimeout(1000);
        }
        return false;
    }

    @Step("Get total number of items across all cart sections")
    public int getItemCount() {
        return page.locator(ITEM_DELETE_BUTTON).count();
    }

    private Locator itemCard(int index) {
        return page.locator(ITEM_DELETE_BUTTON).nth(index)
                .locator("xpath=ancestor::div[.//p[contains(@class,'MuiTypography-P_Medium')]][1]");
    }

    @Step("Get item name at index {index}")
    public String getItemName(int index) {
        return itemCard(index).locator(ITEM_NAME_TEXT).first().textContent().trim();
    }

    @Step("Get item quantity at index {index}")
    public int getItemQuantity(int index) {
        String text = itemCard(index).locator(ITEM_QUANTITY_TEXT).first().textContent().trim();
        return Integer.parseInt(text.replaceAll("[^0-9]", ""));
    }

    @Step("Get original (pre-discount) price text for item at index {index}")
    public String getItemOriginalPriceText(int index) {
        Locator price = itemCard(index).locator(ITEM_ORIGINAL_PRICE_TEXT);
        return price.count() > 0 ? price.first().textContent().trim() : "";
    }

    @Step("Get discount badge text for item at index {index}")
    public String getItemDiscountBadgeText(int index) {
        Locator badge = itemCard(index).locator(ITEM_DISCOUNT_BADGE_TEXT);
        return badge.count() > 0 ? badge.first().textContent().trim() : "";
    }

    @Step("Get final (post-discount) price text for item at index {index}")
    public String getItemFinalPriceText(int index) {
        return itemCard(index).locator(ITEM_FINAL_PRICE_TEXT).first().textContent().trim();
    }

    @Step("Check if a tag/label (e.g. 'REFURBISHED') is displayed for item at index {index}")
    public boolean isItemTagDisplayed(int index, String tagText) {
        Locator tag = itemCard(index).locator(":text(\"" + tagText + "\")");
        return tag.count() > 0 && tag.first().isVisible();
    }

    @Step("Increase quantity for item at index {index}")
    public FurlencoCartDrawer increaseQuantity(int index) {
        LOGGER.info("Increasing quantity for cart item at index {}", index);
        itemCard(index).locator(ITEM_QUANTITY_BUTTON).nth(1).click();
        page.waitForTimeout(800);
        return this;
    }

    @Step("Decrease quantity for item at index {index}")
    public FurlencoCartDrawer decreaseQuantity(int index) {
        LOGGER.info("Decreasing quantity for cart item at index {}", index);
        itemCard(index).locator(ITEM_QUANTITY_BUTTON).nth(0).click();
        page.waitForTimeout(800);
        return this;
    }

    @Step("Remove item at index {index}")
    public FurlencoCartDrawer removeItem(int index) {
        LOGGER.info("Removing cart item at index {}", index);
        page.locator(ITEM_DELETE_BUTTON).nth(index).click();
        page.waitForTimeout(800);
        return this;
    }

    // ---- Rental tenure / payment plan selection (CART-03) ----
    // Verified live: only "Upfront" rental products (e.g. appliances like AC) expose a tenure
    // switcher — standard furniture rentals (chairs, tables, beds) are plain month-to-month with
    // no plan choice at all, so this only applies when a cart contains at least one Upfront-plan
    // eligible item. The switcher is a single global control ("Switch and compare payment plans
    // for all products in one go"), not per-item. Clicking its header opens a "Cart Payment Plan
    // Selector" overlay listing every available plan as a tile with a "SELECT" (or "SELECTED" for
    // the current one) label — plain `<p>` text, not a `<button>`. Selecting a plan applies
    // immediately (no separate confirm step) and closes the overlay; Escape closes it without
    // changing anything.
    private static final String PAYMENT_PLAN_TRIGGER = ":text(\"Choose a payment plan\")";
    private static final String PAYMENT_PLAN_MODAL_HEADING = ":text(\"Cart Payment Plan Selector\")";
    private static final String PAYMENT_PLAN_SELECTED_BADGE = ":text(\"SELECTED\")";

    @Step("Check if a rental tenure/payment plan switcher is available")
    public boolean isPaymentPlanSelectorAvailable() {
        Locator trigger = page.locator(PAYMENT_PLAN_TRIGGER);
        return trigger.count() > 0 && trigger.first().isVisible();
    }

    @Step("Open the payment plan selector")
    public FurlencoCartDrawer openPaymentPlanSelector() {
        LOGGER.info("Opening payment plan selector");
        page.locator(PAYMENT_PLAN_TRIGGER).first().click();
        page.locator(PAYMENT_PLAN_MODAL_HEADING).waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        return this;
    }

    @Step("Check if the payment plan selector overlay is open")
    public boolean isPaymentPlanSelectorOpen() {
        Locator heading = page.locator(PAYMENT_PLAN_MODAL_HEADING);
        return heading.count() > 0 && heading.first().isVisible();
    }

    /**
     * Closes the payment plan selector via Escape without selecting anything — verified live this
     * leaves the previously-applied plan unchanged.
     */
    @Step("Close the payment plan selector without selecting a plan")
    public FurlencoCartDrawer closePaymentPlanSelectorWithoutSelecting() {
        LOGGER.info("Closing payment plan selector via Escape (no selection)");
        page.keyboard().press("Escape");
        page.waitForTimeout(500);
        return this;
    }

    /**
     * Selects a plan by its visible label (e.g. {@code "Upfront 12 Months"}) from an already-open
     * payment plan selector. A no-op if that plan is already the selected one.
     */
    @Step("Select payment plan: {planLabel}")
    public FurlencoCartDrawer selectPaymentPlan(String planLabel) {
        LOGGER.info("Selecting payment plan: {}", planLabel);
        Locator tile = page.locator("xpath=//div[.//p[contains(text(), '" + planLabel + "')]][.//p[text()='SELECT' or text()='SELECTED']][1]");
        Locator alreadySelected = tile.locator(PAYMENT_PLAN_SELECTED_BADGE);
        if (alreadySelected.count() > 0 && alreadySelected.first().isVisible()) {
            LOGGER.info("Plan '{}' is already selected, nothing to do", planLabel);
            return this;
        }
        tile.locator(":text(\"SELECT\")").first().click();
        page.waitForTimeout(2000);
        return this;
    }

    @Step("Get the currently applied plan label for item at index {index} (e.g. '6 Months')")
    public String getItemPlanLabel(int index) {
        Locator label = itemCard(index).locator(":text-matches(\"^\\\\d+ Months?$\")");
        return label.count() > 0 ? label.first().textContent().trim() : "";
    }

    // ---- Value Added Services (CART-05) ----
    // Verified live: a "Value added services" section shows "Furlenco Care" with a price, an
    // "Applied to N Items" count, and an "EDIT" toggle that opens a "Value Added Services" panel
    // with a "Customize VAS" heading. The panel's exact per-item checkbox mapping was NOT fully
    // confirmed (two checkboxes were found on the page when the panel was open, but their exact
    // labels/scope weren't disambiguated from other page checkboxes like the WhatsApp opt-in) —
    // toggleFirstVasOption() is a best-effort action on whatever the first VAS-panel checkbox is;
    // re-verify before relying on it to target a specific item.
    private static final String VAS_SECTION_HEADING = ":text(\"Value added services\")";
    private static final String VAS_APPLIED_COUNT_TEXT = ":text-matches(\"Applied to \\\\d+ Items?\")";
    private static final String VAS_EDIT_TRIGGER = ":text(\"EDIT\")";
    private static final String VAS_PANEL_HEADING = ":text(\"Value Added Services\")";
    private static final String VAS_PANEL_CHECKBOX = "input[type='checkbox']";

    @Step("Check if the Value Added Services section is displayed")
    public boolean isVasSectionDisplayed() {
        return pollForVisible(VAS_SECTION_HEADING);
    }

    @Step("Get the VAS 'Applied to N Items' text")
    public String getVasAppliedCountText() {
        Locator text = page.locator(VAS_APPLIED_COUNT_TEXT);
        return text.count() > 0 ? text.first().textContent().trim() : "";
    }

    @Step("Open the VAS edit panel")
    public FurlencoCartDrawer openVasEditPanel() {
        LOGGER.info("Opening VAS edit panel");
        page.locator(VAS_EDIT_TRIGGER).first().click();
        page.locator(VAS_PANEL_HEADING).waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        return this;
    }

    @Step("Check if the VAS edit panel is open")
    public boolean isVasEditPanelOpen() {
        Locator heading = page.locator(VAS_PANEL_HEADING);
        return heading.count() > 0 && heading.first().isVisible();
    }

    @Step("Close the VAS edit panel")
    public FurlencoCartDrawer closeVasEditPanel() {
        LOGGER.info("Closing VAS edit panel via Escape");
        page.keyboard().press("Escape");
        page.waitForTimeout(500);
        return this;
    }

    /**
     * Toggles the first VAS checkbox found in the (already-open) edit panel — unverified which
     * specific item/VAS combination this targets; see class javadoc note.
     */
    @Step("Toggle the first VAS option in the open edit panel")
    public FurlencoCartDrawer toggleFirstVasOption() {
        LOGGER.info("Toggling first VAS checkbox");
        page.locator(VAS_PANEL_CHECKBOX).first().click();
        page.waitForTimeout(1000);
        return this;
    }

    // ---- Coupons (CART-06) ----
    // Verified live: each vertical section ("Rental Offers & Discounts" / "Buy Offers &
    // Discounts") has its own "Have a Coupon code?" input, an "Apply" button per listed coupon
    // tile, and a "*Terms and Conditions" link. Coupons below the minimum-cart-value threshold
    // show "Add items worth ₹<amount> to unlock offer" instead of being applicable yet.
    private static final String COUPON_INPUT = "input[placeholder*='coupon' i], input[placeholder*='code' i]";
    private static final String COUPON_APPLY_BUTTON = "button:has-text('Apply')";
    private static final String COUPON_LOCKED_MESSAGE = ":text-matches(\"Add items worth.*unlock offer\")";
    private static final String COUPON_ERROR_MESSAGE =
            ":text(\"Invalid coupon\"), :text(\"invalid code\"), :text(\"expired\"), :text(\"not applicable\")";
    private static final String COUPON_REMOVE_BUTTON = "button:has-text('Remove')";
    private static final String COUPON_APPLIED_INDICATOR = ":text(\"Coupon Applied\"), :text(\"applied successfully\")";

    @Step("Enter a coupon code and apply it")
    public FurlencoCartDrawer applyCouponCode(String code) {
        LOGGER.info("Applying coupon code: {}", code);
        Locator input = page.locator(COUPON_INPUT).first();
        input.fill(code);
        page.locator(COUPON_APPLY_BUTTON).first().click();
        page.waitForTimeout(1500);
        return this;
    }

    @Step("Check if a coupon error/invalid message is displayed")
    public boolean isCouponErrorDisplayed() {
        Locator error = page.locator(COUPON_ERROR_MESSAGE);
        return error.count() > 0 && error.first().isVisible();
    }

    @Step("Check if a coupon is shown as successfully applied")
    public boolean isCouponAppliedIndicatorDisplayed() {
        Locator indicator = page.locator(COUPON_APPLIED_INDICATOR);
        return indicator.count() > 0 && indicator.first().isVisible();
    }

    @Step("Remove the currently applied coupon")
    public FurlencoCartDrawer removeCoupon() {
        LOGGER.info("Removing applied coupon");
        page.locator(COUPON_REMOVE_BUTTON).first().click();
        page.waitForTimeout(1000);
        return this;
    }

    @Step("Check if a below-threshold 'unlock offer' message is displayed for any coupon")
    public boolean isCouponLockedMessageDisplayed() {
        Locator locked = page.locator(COUPON_LOCKED_MESSAGE);
        return locked.count() > 0 && locked.first().isVisible();
    }

    // ---- Minimum order value / Empty & error states (CART-07, CART-08) ----
    @Step("Check if a minimum-order-value message is displayed")
    public boolean isMovMessageDisplayed() {
        Locator mov = page.locator(":text-matches(\"[Mm]inimum [Oo]rder\"), :text(\"MOV\")");
        return mov.count() > 0 && mov.first().isVisible();
    }

    @Step("Check if Checkout/Proceed is blocked (disabled) — e.g. below MOV")
    public boolean isCheckoutBlocked() {
        Locator checkoutBtn = page.locator(CHECKOUT_BUTTON).first();
        return checkoutBtn.count() > 0 && !checkoutBtn.isEnabled();
    }
}
