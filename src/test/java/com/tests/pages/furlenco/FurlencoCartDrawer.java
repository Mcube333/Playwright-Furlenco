package com.tests.pages.furlenco;

import com.framework.base.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import io.qameta.allure.Step;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Page Object for Furlenco Cart Drawer / Cart Page.
 */
public class FurlencoCartDrawer extends BasePage {

    private static final Logger LOGGER = LogManager.getLogger(FurlencoCartDrawer.class);

    private static final String CART_CONTAINER = ".CART_PAGE, #empty-cart, .MuiDrawer-root, [data-slot*='cart']";
    private static final String CLOSE_BUTTON = "button[aria-label='Close'], button:has-text('✕'), svg[data-icon='close'], [data-slot*='drawer'] button";
    private static final String EMPTY_CART_MSG = "#empty-cart, text=Your Cart Looks, text=empty, text=No items";

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
}
