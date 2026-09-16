package com.tests.pages.furlenco;

import com.framework.base.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import io.qameta.allure.Step;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Page Object for the "Select Delivery Address" checkout step, verified live at
 * {@code /checkout/address?vertical=<rent|buy>&cartId=<id>}. A test account with at least one
 * saved address will have one pre-selected; {@link #confirmSelectedAddress()} proceeds with
 * whichever is currently selected rather than picking a specific one.
 */
public class FurlencoCheckoutAddressPage extends BasePage {

    private static final Logger LOGGER = LogManager.getLogger(FurlencoCheckoutAddressPage.class);

    private static final String ADDRESS_LIST_CONTAINER = ":text(\"Select Delivery Address\")";
    private static final String SAVED_ADDRESS_RADIO = "input[type='radio']";
    private static final String CONFIRM_LOCATION_BUTTON = "button:has-text('Confirm Location')";
    private static final String ADD_NEW_ADDRESS_BUTTON = "button:has-text('Add New Address')";

    public FurlencoCheckoutAddressPage(Page page) {
        super(page);
    }

    @Step("Check if the Delivery Address step is loaded")
    public boolean isLoaded() {
        boolean urlMatches = currentUrl().contains("/checkout/address");
        Locator container = page.locator(ADDRESS_LIST_CONTAINER);
        boolean containerVisible = container.count() > 0 && container.first().isVisible();
        LOGGER.info("Checkout address step loaded check: urlMatches={}, containerVisible={}", urlMatches, containerVisible);
        return urlMatches || containerVisible;
    }

    @Step("Check if at least one saved address is available")
    public boolean hasSavedAddress() {
        return page.locator(SAVED_ADDRESS_RADIO).count() > 0;
    }

    @Step("Check if the Add New Address action is available")
    public boolean isAddNewAddressAvailable() {
        Locator btn = page.locator(ADD_NEW_ADDRESS_BUTTON);
        return btn.count() > 0 && btn.first().isVisible();
    }

    /**
     * Proceeds with whichever address is already selected (a test account is expected to have a
     * default/pre-selected saved address) — this class deliberately doesn't pick a specific
     * address, since which one is "correct" is test-data-dependent.
     */
    @Step("Confirm the currently selected delivery address")
    public FurlencoOrderSummaryPage confirmSelectedAddress() {
        LOGGER.info("Confirming selected delivery address");
        Locator confirmBtn = page.locator(CONFIRM_LOCATION_BUTTON).first();
        confirmBtn.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        confirmBtn.click();
        page.waitForLoadState();
        page.waitForTimeout(1500);
        return new FurlencoOrderSummaryPage(page);
    }
}
